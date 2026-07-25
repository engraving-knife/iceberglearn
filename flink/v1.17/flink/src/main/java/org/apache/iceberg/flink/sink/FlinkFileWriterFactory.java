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
package org.apache.iceberg.flink.sink;

import static org.apache.iceberg.MetadataColumns.DELETE_FILE_ROW_FIELD_NAME;
import static org.apache.iceberg.TableProperties.DEFAULT_FILE_FORMAT;
import static org.apache.iceberg.TableProperties.DEFAULT_FILE_FORMAT_DEFAULT;
import static org.apache.iceberg.TableProperties.DELETE_DEFAULT_FILE_FORMAT;

import java.io.Serializable;
import java.util.Map;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.BaseFileWriterFactory;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.data.FlinkAvroWriter;
import org.apache.iceberg.flink.data.FlinkOrcWriter;
import org.apache.iceberg.flink.data.FlinkParquetWriters;
import org.apache.iceberg.io.DeleteSchemaUtil;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Flink 版本的文件写入器工厂，负责为数据/删除文件创建对应格式（Avro/Parquet/ORC）的写入器。
 *
 * <p>所属模块：iceberg-flink（sink 侧），继承 Iceberg core 的 {@link BaseFileWriterFactory}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有数据、equality delete、position delete 三类 RowType（Flink 逻辑类型）。
 *   <li>为每种文件格式配置 createWriterFunc，桥接 Flink {@link RowData} 与 Iceberg 写入器。
 *   <li>对 position delete 的路径字段做 StringData 转换（Parquet/ORC）。
 * </ul>
 *
 * <p>设计意图：Flink 类型在需要时才从 Iceberg schema 懒转换（{@code dataFlinkType()} 等）， 避免不必要的转换开销；通过 Builder
 * 收集众多参数保证构造清晰。
 *
 * <p>上下游关系：被 {@link FlinkSink} 写入流程调用，产出具体文件写入器。
 */
class FlinkFileWriterFactory extends BaseFileWriterFactory<RowData> implements Serializable {
  private RowType dataFlinkType;
  private RowType equalityDeleteFlinkType;
  private RowType positionDeleteFlinkType;

  /**
   * 构造工厂实例，参数较多，通常通过 {@link #builderFor(Table)} 构建。
   *
   * @param table Iceberg 表
   * @param dataFileFormat 数据文件格式
   * @param dataSchema 数据 schema
   * @param dataFlinkType 数据 Flink RowType（可为 null，懒推导）
   * @param dataSortOrder 数据排序规则
   * @param deleteFileFormat 删除文件格式
   * @param equalityFieldIds equality 字段 id 数组
   * @param equalityDeleteRowSchema equality delete 行 schema
   * @param equalityDeleteFlinkType equality delete Flink RowType
   * @param equalityDeleteSortOrder equality delete 排序规则
   * @param positionDeleteRowSchema position delete 行 schema
   * @param positionDeleteFlinkType position delete Flink RowType
   */
  FlinkFileWriterFactory(
      Table table,
      FileFormat dataFileFormat,
      Schema dataSchema,
      RowType dataFlinkType,
      SortOrder dataSortOrder,
      FileFormat deleteFileFormat,
      int[] equalityFieldIds,
      Schema equalityDeleteRowSchema,
      RowType equalityDeleteFlinkType,
      SortOrder equalityDeleteSortOrder,
      Schema positionDeleteRowSchema,
      RowType positionDeleteFlinkType) {

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

    this.dataFlinkType = dataFlinkType;
    this.equalityDeleteFlinkType = equalityDeleteFlinkType;
    this.positionDeleteFlinkType = positionDeleteFlinkType;
  }

  /**
   * 创建 {@link Builder} 构建工厂实例。
   *
   * @param table Iceberg 表
   * @return Builder
   */
  static Builder builderFor(Table table) {
    return new Builder(table);
  }

  /** 为 Avro 数据写入配置 Flink Avro writer。 */
  @Override
  protected void configureDataWrite(Avro.DataWriteBuilder builder) {
    builder.createWriterFunc(ignore -> new FlinkAvroWriter(dataFlinkType()));
  }

  /** 为 Avro equality delete 写入配置 Flink Avro writer。 */
  @Override
  protected void configureEqualityDelete(Avro.DeleteWriteBuilder builder) {
    builder.createWriterFunc(ignored -> new FlinkAvroWriter(equalityDeleteFlinkType()));
  }

  /** 为 Avro position delete 写入配置 Flink Avro writer（仅写 row 字段，忽略 path/pos）。 */
  @Override
  protected void configurePositionDelete(Avro.DeleteWriteBuilder builder) {
    int rowFieldIndex = positionDeleteFlinkType().getFieldIndex(DELETE_FILE_ROW_FIELD_NAME);
    if (rowFieldIndex >= 0) {
      // FlinkAvroWriter accepts just the Flink type of the row ignoring the path and pos
      RowType positionDeleteRowFlinkType =
          (RowType) positionDeleteFlinkType().getTypeAt(rowFieldIndex);
      builder.createWriterFunc(ignored -> new FlinkAvroWriter(positionDeleteRowFlinkType));
    }
  }

  /** 为 Parquet 数据写入配置 Flink Parquet writer。 */
  @Override
  protected void configureDataWrite(Parquet.DataWriteBuilder builder) {
    builder.createWriterFunc(msgType -> FlinkParquetWriters.buildWriter(dataFlinkType(), msgType));
  }

  /** 为 Parquet equality delete 写入配置 Flink Parquet writer。 */
  @Override
  protected void configureEqualityDelete(Parquet.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        msgType -> FlinkParquetWriters.buildWriter(equalityDeleteFlinkType(), msgType));
  }

  /** 为 Parquet position delete 写入配置 Flink Parquet writer，并将路径转为 StringData。 */
  @Override
  protected void configurePositionDelete(Parquet.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        msgType -> FlinkParquetWriters.buildWriter(positionDeleteFlinkType(), msgType));
    builder.transformPaths(path -> StringData.fromString(path.toString()));
  }

  /** 为 ORC 数据写入配置 Flink ORC writer。 */
  @Override
  protected void configureDataWrite(ORC.DataWriteBuilder builder) {
    builder.createWriterFunc(
        (iSchema, typDesc) -> FlinkOrcWriter.buildWriter(dataFlinkType(), iSchema));
  }

  /** 为 ORC equality delete 写入配置 Flink ORC writer。 */
  @Override
  protected void configureEqualityDelete(ORC.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        (iSchema, typDesc) -> FlinkOrcWriter.buildWriter(equalityDeleteFlinkType(), iSchema));
  }

  /** 为 ORC position delete 写入配置 Flink ORC writer，并将路径转为 StringData。 */
  @Override
  protected void configurePositionDelete(ORC.DeleteWriteBuilder builder) {
    builder.createWriterFunc(
        (iSchema, typDesc) -> FlinkOrcWriter.buildWriter(positionDeleteFlinkType(), iSchema));
    builder.transformPaths(path -> StringData.fromString(path.toString()));
  }

  /** 懒推导并返回数据 Flink RowType；未设置时由 dataSchema 转换得到。 */
  private RowType dataFlinkType() {
    if (dataFlinkType == null) {
      Preconditions.checkNotNull(dataSchema(), "Data schema must not be null");
      this.dataFlinkType = FlinkSchemaUtil.convert(dataSchema());
    }

    return dataFlinkType;
  }

  /** 懒推导并返回 equality delete Flink RowType。 */
  private RowType equalityDeleteFlinkType() {
    if (equalityDeleteFlinkType == null) {
      Preconditions.checkNotNull(
          equalityDeleteRowSchema(), "Equality delete schema must not be null");
      this.equalityDeleteFlinkType = FlinkSchemaUtil.convert(equalityDeleteRowSchema());
    }

    return equalityDeleteFlinkType;
  }

  /** 懒推导并返回 position delete Flink RowType（包装 path/pos 与 row）。 */
  private RowType positionDeleteFlinkType() {
    if (positionDeleteFlinkType == null) {
      // wrap the optional row schema into the position delete schema that contains path and
      // position
      Schema positionDeleteSchema = DeleteSchemaUtil.posDeleteSchema(positionDeleteRowSchema());
      this.positionDeleteFlinkType = FlinkSchemaUtil.convert(positionDeleteSchema);
    }

    return positionDeleteFlinkType;
  }

  /** {@link FlinkFileWriterFactory} 的构建器，从表属性推导默认文件格式，并收集各类 schema/类型/排序参数。 */
  static class Builder {
    private final Table table;
    private FileFormat dataFileFormat;
    private Schema dataSchema;
    private RowType dataFlinkType;
    private SortOrder dataSortOrder;
    private FileFormat deleteFileFormat;
    private int[] equalityFieldIds;
    private Schema equalityDeleteRowSchema;
    private RowType equalityDeleteFlinkType;
    private SortOrder equalityDeleteSortOrder;
    private Schema positionDeleteRowSchema;
    private RowType positionDeleteFlinkType;

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

    /** 设置数据文件的 Flink RowType；未设置时由 Iceberg schema 推导。 */
    Builder dataFlinkType(RowType newDataFlinkType) {
      this.dataFlinkType = newDataFlinkType;
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

    /** 设置 equality delete 文件的 Flink RowType；未设置时由 Iceberg schema 推导。 */
    Builder equalityDeleteFlinkType(RowType newEqualityDeleteFlinkType) {
      this.equalityDeleteFlinkType = newEqualityDeleteFlinkType;
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

    /** 设置 position delete 文件的 Flink RowType；未设置时由 Iceberg schema 推导。 */
    Builder positionDeleteFlinkType(RowType newPositionDeleteFlinkType) {
      this.positionDeleteFlinkType = newPositionDeleteFlinkType;
      return this;
    }

    /**
     * 构建工厂实例。
     *
     * <p>逻辑：校验 equality 字段 id 与 equality delete schema 必须同时设置或同时缺省，随后构造 FlinkFileWriterFactory。
     *
     * @return 工厂实例
     */
    FlinkFileWriterFactory build() {
      boolean noEqualityDeleteConf = equalityFieldIds == null && equalityDeleteRowSchema == null;
      boolean fullEqualityDeleteConf = equalityFieldIds != null && equalityDeleteRowSchema != null;
      Preconditions.checkArgument(
          noEqualityDeleteConf || fullEqualityDeleteConf,
          "Equality field IDs and equality delete row schema must be set together");

      return new FlinkFileWriterFactory(
          table,
          dataFileFormat,
          dataSchema,
          dataFlinkType,
          dataSortOrder,
          deleteFileFormat,
          equalityFieldIds,
          equalityDeleteRowSchema,
          equalityDeleteFlinkType,
          equalityDeleteSortOrder,
          positionDeleteRowSchema,
          positionDeleteFlinkType);
    }
  }
}
