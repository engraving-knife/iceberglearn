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
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.data.SparkAvroWriter;
import org.apache.iceberg.spark.data.SparkOrcWriter;
import org.apache.iceberg.spark.data.SparkParquetWriters;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的工厂，负责创建实例。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkFileWriterFactory。
 *
 * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkFileWriterFactory extends BaseFileWriterFactory<InternalRow> {
  private StructType dataSparkType;
  private StructType equalityDeleteSparkType;
  private StructType positionDeleteSparkType;

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
      StructType positionDeleteSparkType) {

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
  }

  /** 构造并返回目标对象。 */
  static Builder builderFor(Table table) {
    /** 构造并返回目标对象。 */
    return new Builder(table);
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configureDataWrite(Avro.DataWriteBuilder builder) {
    builder.createWriterFunc(ignored -> new SparkAvroWriter(dataSparkType()));
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configureEqualityDelete(Avro.DeleteWriteBuilder builder) {
    builder.createWriterFunc(ignored -> new SparkAvroWriter(equalityDeleteSparkType()));
  }

  /** 执行该方法的具体逻辑。 */
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
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configureDataWrite(Parquet.DataWriteBuilder builder) {
    builder.createWriterFunc(msgType -> SparkParquetWriters.buildWriter(dataSparkType(), msgType));
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configureEqualityDelete(Parquet.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        msgType -> SparkParquetWriters.buildWriter(equalityDeleteSparkType(), msgType));
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configurePositionDelete(Parquet.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        msgType -> SparkParquetWriters.buildWriter(positionDeleteSparkType(), msgType));
    builder.transformPaths(path -> UTF8String.fromString(path.toString()));
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configureDataWrite(ORC.DataWriteBuilder builder) {
    builder.createWriterFunc(SparkOrcWriter::new);
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configureEqualityDelete(ORC.DeleteWriteBuilder builder) {
    builder.createWriterFunc(SparkOrcWriter::new);
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected void configurePositionDelete(ORC.DeleteWriteBuilder builder) {
    builder.createWriterFunc(SparkOrcWriter::new);
    builder.transformPaths(path -> UTF8String.fromString(path.toString()));
  }

  /** 执行该方法的具体逻辑。 */
  private StructType dataSparkType() {
    if (dataSparkType == null) {
      Preconditions.checkNotNull(dataSchema(), "Data schema must not be null");
      this.dataSparkType = SparkSchemaUtil.convert(dataSchema());
    }

    return dataSparkType;
  }

  /** 执行该方法的具体逻辑。 */
  private StructType equalityDeleteSparkType() {
    if (equalityDeleteSparkType == null) {
      Preconditions.checkNotNull(
          equalityDeleteRowSchema(), "Equality delete schema must not be null");
      this.equalityDeleteSparkType = SparkSchemaUtil.convert(equalityDeleteRowSchema());
    }

    return equalityDeleteSparkType;
  }

  /** 执行该方法的具体逻辑。 */
  private StructType positionDeleteSparkType() {
    if (positionDeleteSparkType == null) {
      // wrap the optional row schema into the position delete schema containing path and position
      Schema positionDeleteSchema = DeleteSchemaUtil.posDeleteSchema(positionDeleteRowSchema());
      this.positionDeleteSparkType = SparkSchemaUtil.convert(positionDeleteSchema);
    }

    return positionDeleteSparkType;
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 Builder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
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

    /** 执行该方法的具体逻辑。 */
    Builder dataFileFormat(FileFormat newDataFileFormat) {
      this.dataFileFormat = newDataFileFormat;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder dataSchema(Schema newDataSchema) {
      this.dataSchema = newDataSchema;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder dataSparkType(StructType newDataSparkType) {
      this.dataSparkType = newDataSparkType;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder dataSortOrder(SortOrder newDataSortOrder) {
      this.dataSortOrder = newDataSortOrder;
      return this;
    }

    /** 删除数据或文件。 */
    Builder deleteFileFormat(FileFormat newDeleteFileFormat) {
      this.deleteFileFormat = newDeleteFileFormat;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder equalityFieldIds(int[] newEqualityFieldIds) {
      this.equalityFieldIds = newEqualityFieldIds;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder equalityDeleteRowSchema(Schema newEqualityDeleteRowSchema) {
      this.equalityDeleteRowSchema = newEqualityDeleteRowSchema;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder equalityDeleteSparkType(StructType newEqualityDeleteSparkType) {
      this.equalityDeleteSparkType = newEqualityDeleteSparkType;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder equalityDeleteSortOrder(SortOrder newEqualityDeleteSortOrder) {
      this.equalityDeleteSortOrder = newEqualityDeleteSortOrder;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder positionDeleteRowSchema(Schema newPositionDeleteRowSchema) {
      this.positionDeleteRowSchema = newPositionDeleteRowSchema;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder positionDeleteSparkType(StructType newPositionDeleteSparkType) {
      this.positionDeleteSparkType = newPositionDeleteSparkType;
      return this;
    }

    /** 构造并返回目标对象。 */
    SparkFileWriterFactory build() {
      boolean noEqualityDeleteConf = equalityFieldIds == null && equalityDeleteRowSchema == null;
      boolean fullEqualityDeleteConf = equalityFieldIds != null && equalityDeleteRowSchema != null;
      Preconditions.checkArgument(
          noEqualityDeleteConf || fullEqualityDeleteConf,
          "Equality field IDs and equality delete row schema must be set together");

      /** 执行该方法的具体逻辑。 */
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
          positionDeleteSparkType);
    }
  }
}
