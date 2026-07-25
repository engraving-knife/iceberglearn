/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.spark.source;

import static org.apache.iceberg.MetadataColumns.DELETE_FILE_ROW_FIELD_NAME;
import static org.apache.iceberg.TableProperties.DEFAULT_FILE_FORMAT;
import static org.apache.iceberg.TableProperties.DEFAULT_FILE_FORMAT_DEFAULT;
import static org.apache.iceberg.TableProperties.DELETE_DEFAULT_FILE_FORMAT;

import java.util.Map;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.BaseFileWriterFactory;
import org.apache.iceberg.io.DeleteSchemaUtil;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.data.SparkAvroWriter;
import org.apache.iceberg.spark.data.SparkOrcWriter;
import org.apache.iceberg.spark.data.SparkParquetWriters;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark {@link InternalRow} 的文件写入器工厂。
 *
 * <p>所属模块：iceberg-spark（source 子包）。继承 {@link BaseFileWriterFactory}，为数据文件、 equality 删除文件、position
 * 删除文件按 Avro/Parquet/ORC 三种格式配置对应的 Spark 写入器 （{@link SparkAvroWriter}/{@link
 * SparkParquetWriters}/{@link SparkOrcWriter}）。
 *
 * <p>设计意图：把"按格式 + 文件类型创建写入器函数"的模板逻辑放在父类，本类只负责 Spark 特定 的写入器构造与 Spark 类型转换；Spark 类型按需懒转换并缓存。
 *
 * <p>上下游关系：由 {@link SparkWrite} 等通过 {@link #builderFor(Table)} 构建后交给 Iceberg IO 层创建具体写入器。
 */
class SparkFileWriterFactory extends BaseFileWriterFactory<InternalRow> {
  private StructType dataSparkType;
  private StructType equalityDeleteSparkType;
  private StructType positionDeleteSparkType;
  private Map<String, String> writeProperties;

  /** 全参数构造，初始化各文件类型对应的 Spark 类型与写入属性。 */
  SparkFileWriterFactory(
      Table table,
      FileFormat dataFileFormat,
      Schema dataSchema,
      StructType dataSparkType,
      SortOrder dataSortOrder,
      FileFormat deleteFileFormat,
      int[] equalityFieldIds,
      Schema equalityDeleteRowSchema,
      StructType equalityDeleteSparkType,
      SortOrder equalityDeleteSortOrder,
      Schema positionDeleteRowSchema,
      StructType positionDeleteSparkType,
      Map<String, String> writeProperties) {

    super(
        table,
        dataFileFormat,
        dataSchema,
        dataSortOrder,
        deleteFileFormat,
        equalityFieldIds,
        equalityDeleteRowSchema,
        equalityDeleteSortOrder,
        positionDeleteRowSchema);

    this.dataSparkType = dataSparkType;
    this.equalityDeleteSparkType = equalityDeleteSparkType;
    this.positionDeleteSparkType = positionDeleteSparkType;
    this.writeProperties = writeProperties != null ? writeProperties : ImmutableMap.of();
  }

  /** 返回构建器，默认数据/删除格式取自表属性。 */
  static Builder builderFor(Table table) {
    return new Builder(table);
  }

  /** 配置 Avro 数据写入：使用 {@link SparkAvroWriter} 并设置写入属性。 */
  @Override
  protected void configureDataWrite(Avro.DataWriteBuilder builder) {
    builder.createWriterFunc(ignored -> new SparkAvroWriter(dataSparkType()));
    builder.setAll(writeProperties);
  }

  /** 配置 Avro equality 删除写入：使用 {@link SparkAvroWriter}。 */
  @Override
  protected void configureEqualityDelete(Avro.DeleteWriteBuilder builder) {
    builder.createWriterFunc(ignored -> new SparkAvroWriter(equalityDeleteSparkType()));
    builder.setAll(writeProperties);
  }

  /** 配置 Avro position 删除写入：含行时按行 Spark 类型构造写入器。 */
  @Override
  protected void configurePositionDelete(Avro.DeleteWriteBuilder builder) {
    boolean withRow =
        positionDeleteSparkType().getFieldIndex(DELETE_FILE_ROW_FIELD_NAME).isDefined();
    if (withRow) {
      // SparkAvroWriter accepts just the Spark type of the row ignoring the path and pos
      StructField rowField = positionDeleteSparkType().apply(DELETE_FILE_ROW_FIELD_NAME);
      StructType positionDeleteRowSparkType = (StructType) rowField.dataType();
      builder.createWriterFunc(ignored -> new SparkAvroWriter(positionDeleteRowSparkType));
    }

    builder.setAll(writeProperties);
  }

  /** 配置 Parquet 数据写入：使用 {@link SparkParquetWriters#buildWriter}。 */
  @Override
  protected void configureDataWrite(Parquet.DataWriteBuilder builder) {
    builder.createWriterFunc(msgType -> SparkParquetWriters.buildWriter(dataSparkType(), msgType));
    builder.setAll(writeProperties);
  }

  /** 配置 Parquet equality 删除写入。 */
  @Override
  protected void configureEqualityDelete(Parquet.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        msgType -> SparkParquetWriters.buildWriter(equalityDeleteSparkType(), msgType));
    builder.setAll(writeProperties);
  }

  /** 配置 Parquet position 删除写入，并将路径转为 UTF8String。 */
  @Override
  protected void configurePositionDelete(Parquet.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        msgType -> SparkParquetWriters.buildWriter(positionDeleteSparkType(), msgType));
    builder.transformPaths(path -> UTF8String.fromString(path.toString()));
    builder.setAll(writeProperties);
  }

  /** 配置 ORC 数据写入：使用 {@link SparkOrcWriter}。 */
  @Override
  protected void configureDataWrite(ORC.DataWriteBuilder builder) {
    builder.createWriterFunc(SparkOrcWriter::new);
    builder.setAll(writeProperties);
  }

  /** 配置 ORC equality 删除写入。 */
  @Override
  protected void configureEqualityDelete(ORC.DeleteWriteBuilder builder) {
    builder.createWriterFunc(SparkOrcWriter::new);
    builder.setAll(writeProperties);
  }

  /** 配置 ORC position 删除写入，并将路径转为 UTF8String。 */
  @Override
  protected void configurePositionDelete(ORC.DeleteWriteBuilder builder) {
    builder.createWriterFunc(SparkOrcWriter::new);
    builder.transformPaths(path -> UTF8String.fromString(path.toString()));
    builder.setAll(writeProperties);
  }

  /** 懒转换并返回数据 Spark 类型。 */
  private StructType dataSparkType() {
    if (dataSparkType == null) {
      Preconditions.checkNotNull(dataSchema(), "Data schema must not be null");
      this.dataSparkType = SparkSchemaUtil.convert(dataSchema());
    }

    return dataSparkType;
  }

  /** 懒转换并返回 equality 删除 Spark 类型。 */
  private StructType equalityDeleteSparkType() {
    if (equalityDeleteSparkType == null) {
      Preconditions.checkNotNull(
          equalityDeleteRowSchema(), "Equality delete schema must not be null");
      this.equalityDeleteSparkType = SparkSchemaUtil.convert(equalityDeleteRowSchema());
    }

    return equalityDeleteSparkType;
  }

  /** 懒构造并返回 position 删除 Spark 类型（含 path/position 与可选行）。 */
  private StructType positionDeleteSparkType() {
    if (positionDeleteSparkType == null) {
      // wrap the optional row schema into the position delete schema containing path and position
      Schema positionDeleteSchema = DeleteSchemaUtil.posDeleteSchema(positionDeleteRowSchema());
      this.positionDeleteSparkType = SparkSchemaUtil.convert(positionDeleteSchema);
    }

    return positionDeleteSparkType;
  }

  /** {@link SparkFileWriterFactory} 的构建器，收集各文件类型配置。 */
  static class Builder {
    private final Table table;
    private FileFormat dataFileFormat;
    private Schema dataSchema;
    private StructType dataSparkType;
    private SortOrder dataSortOrder;
    private FileFormat deleteFileFormat;
    private int[] equalityFieldIds;
    private Schema equalityDeleteRowSchema;
    private StructType equalityDeleteSparkType;
    private SortOrder equalityDeleteSortOrder;
    private Schema positionDeleteRowSchema;
    private StructType positionDeleteSparkType;
    private Map<String, String> writeProperties;

    Builder(Table table) {
      this.table = table;

      Map<String, String> properties = table.properties();

      String dataFileFormatName =
          properties.getOrDefault(DEFAULT_FILE_FORMAT, DEFAULT_FILE_FORMAT_DEFAULT);
      this.dataFileFormat = FileFormat.fromString(dataFileFormatName);

      String deleteFileFormatName =
          properties.getOrDefault(DELETE_DEFAULT_FILE_FORMAT, dataFileFormatName);
      this.deleteFileFormat = FileFormat.fromString(deleteFileFormatName);
    }

    Builder dataFileFormat(FileFormat newDataFileFormat) {
      this.dataFileFormat = newDataFileFormat;
      return this;
    }

    Builder dataSchema(Schema newDataSchema) {
      this.dataSchema = newDataSchema;
      return this;
    }

    Builder dataSparkType(StructType newDataSparkType) {
      this.dataSparkType = newDataSparkType;
      return this;
    }

    Builder dataSortOrder(SortOrder newDataSortOrder) {
      this.dataSortOrder = newDataSortOrder;
      return this;
    }

    Builder deleteFileFormat(FileFormat newDeleteFileFormat) {
      this.deleteFileFormat = newDeleteFileFormat;
      return this;
    }

    Builder equalityFieldIds(int[] newEqualityFieldIds) {
      this.equalityFieldIds = newEqualityFieldIds;
      return this;
    }

    Builder equalityDeleteRowSchema(Schema newEqualityDeleteRowSchema) {
      this.equalityDeleteRowSchema = newEqualityDeleteRowSchema;
      return this;
    }

    Builder equalityDeleteSparkType(StructType newEqualityDeleteSparkType) {
      this.equalityDeleteSparkType = newEqualityDeleteSparkType;
      return this;
    }

    Builder equalityDeleteSortOrder(SortOrder newEqualityDeleteSortOrder) {
      this.equalityDeleteSortOrder = newEqualityDeleteSortOrder;
      return this;
    }

    Builder positionDeleteRowSchema(Schema newPositionDeleteRowSchema) {
      this.positionDeleteRowSchema = newPositionDeleteRowSchema;
      return this;
    }

    Builder positionDeleteSparkType(StructType newPositionDeleteSparkType) {
      this.positionDeleteSparkType = newPositionDeleteSparkType;
      return this;
    }

    Builder writeProperties(Map<String, String> properties) {
      this.writeProperties = properties;
      return this;
    }

    /** 构建工厂：校验 equality 字段 ID 与 schema 必须同时设置或同时不设置。 */
    SparkFileWriterFactory build() {
      boolean noEqualityDeleteConf = equalityFieldIds == null && equalityDeleteRowSchema == null;
      boolean fullEqualityDeleteConf = equalityFieldIds != null && equalityDeleteRowSchema != null;
      Preconditions.checkArgument(
          noEqualityDeleteConf || fullEqualityDeleteConf,
          "Equality field IDs and equality delete row schema must be set together");

      return new SparkFileWriterFactory(
          table,
          dataFileFormat,
          dataSchema,
          dataSparkType,
          dataSortOrder,
          deleteFileFormat,
          equalityFieldIds,
          equalityDeleteRowSchema,
          equalityDeleteSparkType,
          equalityDeleteSortOrder,
          positionDeleteRowSchema,
          positionDeleteSparkType,
          writeProperties);
    }
  }
}
