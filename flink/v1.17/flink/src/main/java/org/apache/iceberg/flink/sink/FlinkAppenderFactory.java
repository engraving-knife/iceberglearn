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

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Map;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.data.FlinkAvroWriter;
import org.apache.iceberg.flink.data.FlinkOrcWriter;
import org.apache.iceberg.flink.data.FlinkParquetWriters;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.DeleteSchemaUtil;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：Flink RowData 的文件 Appender 工厂实现。
 *
 * <p>所属模块：iceberg-flink（sink 子包），实现 Iceberg 的 {@link FileAppenderFactory} 接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>创建数据文件 writer（支持 Avro/ORC/Parquet 三种格式）。
 *   <li>创建 equality delete writer（用于 UPSERT/CDC 场景的等值删除）。
 *   <li>创建 position delete writer（用于按位置删除）。
 * </ul>
 *
 * <p>设计意图：通过 switch(format) 分发到不同格式的 writer 构建逻辑， 每种格式委托对应的 Flink writer（{@link
 * FlinkAvroWriter}/{@link FlinkOrcWriter}/{@link FlinkParquetWriters}）。 delete schema 的 Flink
 * 类型懒加载转换，避免不必要的转换开销。
 *
 * <p>上下游关系：被 {@link TaskWriterFactory} 用于创建底层文件写入器； 内部委托 {@link FlinkSchemaUtil} 做 schema 转换。
 */
public class FlinkAppenderFactory implements FileAppenderFactory<RowData>, Serializable {
  private final Schema schema;
  private final RowType flinkSchema;
  private final Map<String, String> props;
  private final PartitionSpec spec;
  private final int[] equalityFieldIds;
  private final Schema eqDeleteRowSchema;
  private final Schema posDeleteRowSchema;
  private final Table table;

  private RowType eqDeleteFlinkSchema = null;
  private RowType posDeleteFlinkSchema = null;

  /**
   * 构造方法。
   *
   * @param table Iceberg 表
   * @param schema 表 schema
   * @param flinkSchema Flink 行类型
   * @param props 表属性
   * @param spec 分区规范
   * @param equalityFieldIds equality 字段 id 数组
   * @param eqDeleteRowSchema equality delete 行 schema
   * @param posDeleteRowSchema position delete 行 schema
   */
  public FlinkAppenderFactory(
      Table table,
      Schema schema,
      RowType flinkSchema,
      Map<String, String> props,
      PartitionSpec spec,
      int[] equalityFieldIds,
      Schema eqDeleteRowSchema,
      Schema posDeleteRowSchema) {
    Preconditions.checkNotNull(table, "Table shouldn't be null");
    this.table = table;
    this.schema = schema;
    this.flinkSchema = flinkSchema;
    this.props = props;
    this.spec = spec;
    this.equalityFieldIds = equalityFieldIds;
    this.eqDeleteRowSchema = eqDeleteRowSchema;
    this.posDeleteRowSchema = posDeleteRowSchema;
  }

  /** 懒加载 equality delete 的 Flink RowType。 */
  private RowType lazyEqDeleteFlinkSchema() {
    if (eqDeleteFlinkSchema == null) {
      Preconditions.checkNotNull(eqDeleteRowSchema, "Equality delete row schema shouldn't be null");
      this.eqDeleteFlinkSchema = FlinkSchemaUtil.convert(eqDeleteRowSchema);
    }
    return eqDeleteFlinkSchema;
  }

  /** 懒加载 position delete 的 Flink RowType。 */
  private RowType lazyPosDeleteFlinkSchema() {
    if (posDeleteFlinkSchema == null) {
      Preconditions.checkNotNull(posDeleteRowSchema, "Pos-delete row schema shouldn't be null");
      this.posDeleteFlinkSchema = FlinkSchemaUtil.convert(posDeleteRowSchema);
    }
    return this.posDeleteFlinkSchema;
  }

  /**
   * 创建数据文件 appender。
   *
   * <p>逻辑：按 format 分发到 Avro/ORC/Parquet 的 write 链，设置 schema、metrics、属性等， 委托对应的 Flink writer 构建函数。
   *
   * @param outputFile 输出文件
   * @param format 文件格式
   * @return 文件 appender
   */
  @Override
  public FileAppender<RowData> newAppender(OutputFile outputFile, FileFormat format) {
    MetricsConfig metricsConfig = MetricsConfig.forTable(table);
    try {
      switch (format) {
        case AVRO:
          return Avro.write(outputFile)
              .createWriterFunc(ignore -> new FlinkAvroWriter(flinkSchema))
              .setAll(props)
              .schema(schema)
              .metricsConfig(metricsConfig)
              .overwrite()
              .build();

        case ORC:
          return ORC.write(outputFile)
              .createWriterFunc(
                  (iSchema, typDesc) -> FlinkOrcWriter.buildWriter(flinkSchema, iSchema))
              .setAll(props)
              .metricsConfig(metricsConfig)
              .schema(schema)
              .overwrite()
              .build();

        case PARQUET:
          return Parquet.write(outputFile)
              .createWriterFunc(msgType -> FlinkParquetWriters.buildWriter(flinkSchema, msgType))
              .setAll(props)
              .metricsConfig(metricsConfig)
              .schema(schema)
              .overwrite()
              .build();

        default:
          throw new UnsupportedOperationException("Cannot write unknown file format: " + format);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 创建数据 writer（包装 appender + 分区信息 + 加密元数据）。
   *
   * @param file 加密输出文件
   * @param format 文件格式
   * @param partition 分区值
   * @return DataWriter 实例
   */
  @Override
  public DataWriter<RowData> newDataWriter(
      EncryptedOutputFile file, FileFormat format, StructLike partition) {
    return new DataWriter<>(
        newAppender(file.encryptingOutputFile(), format),
        format,
        file.encryptingOutputFile().location(),
        spec,
        partition,
        file.keyMetadata());
  }

  /**
   * 创建 equality delete writer。
   *
   * <p>逻辑：校验 equalityFieldIds 非空 → 按 format 分发到 Avro/ORC/Parquet 的 writeDeletes 链， 设置 equality
   * field ids、delete schema、分区等，构建 equality delete writer。
   *
   * @param outputFile 加密输出文件
   * @param format 文件格式
   * @param partition 分区值
   * @return EqualityDeleteWriter 实例
   */
  @Override
  public EqualityDeleteWriter<RowData> newEqDeleteWriter(
      EncryptedOutputFile outputFile, FileFormat format, StructLike partition) {
    Preconditions.checkState(
        equalityFieldIds != null && equalityFieldIds.length > 0,
        "Equality field ids shouldn't be null or empty when creating equality-delete writer");
    Preconditions.checkNotNull(
        eqDeleteRowSchema,
        "Equality delete row schema shouldn't be null when creating equality-delete writer");

    MetricsConfig metricsConfig = MetricsConfig.forTable(table);
    try {
      switch (format) {
        case AVRO:
          return Avro.writeDeletes(outputFile.encryptingOutputFile())
              .createWriterFunc(ignore -> new FlinkAvroWriter(lazyEqDeleteFlinkSchema()))
              .withPartition(partition)
              .overwrite()
              .setAll(props)
              .metricsConfig(metricsConfig)
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(outputFile.keyMetadata())
              .equalityFieldIds(equalityFieldIds)
              .buildEqualityWriter();

        case ORC:
          return ORC.writeDeletes(outputFile.encryptingOutputFile())
              .createWriterFunc(
                  (iSchema, typDesc) -> FlinkOrcWriter.buildWriter(flinkSchema, iSchema))
              .withPartition(partition)
              .overwrite()
              .setAll(props)
              .metricsConfig(metricsConfig)
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(outputFile.keyMetadata())
              .equalityFieldIds(equalityFieldIds)
              .buildEqualityWriter();

        case PARQUET:
          return Parquet.writeDeletes(outputFile.encryptingOutputFile())
              .createWriterFunc(
                  msgType -> FlinkParquetWriters.buildWriter(lazyEqDeleteFlinkSchema(), msgType))
              .withPartition(partition)
              .overwrite()
              .setAll(props)
              .metricsConfig(metricsConfig)
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(outputFile.keyMetadata())
              .equalityFieldIds(equalityFieldIds)
              .buildEqualityWriter();

        default:
          throw new UnsupportedOperationException(
              "Cannot write equality-deletes for unsupported file format: " + format);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 创建 position delete writer。
   *
   * <p>逻辑：按 format 分发到 Avro/ORC/Parquet 的 writeDeletes 链，设置 position delete schema、 分区等。ORC 和
   * Parquet 额外处理 path 转换（转为 StringData）。
   *
   * @param outputFile 加密输出文件
   * @param format 文件格式
   * @param partition 分区值
   * @return PositionDeleteWriter 实例
   */
  @Override
  public PositionDeleteWriter<RowData> newPosDeleteWriter(
      EncryptedOutputFile outputFile, FileFormat format, StructLike partition) {
    MetricsConfig metricsConfig = MetricsConfig.forPositionDelete(table);
    try {
      switch (format) {
        case AVRO:
          return Avro.writeDeletes(outputFile.encryptingOutputFile())
              .createWriterFunc(ignore -> new FlinkAvroWriter(lazyPosDeleteFlinkSchema()))
              .withPartition(partition)
              .overwrite()
              .setAll(props)
              .metricsConfig(metricsConfig)
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(outputFile.keyMetadata())
              .buildPositionWriter();

        case ORC:
          RowType orcPosDeleteSchema =
              FlinkSchemaUtil.convert(DeleteSchemaUtil.posDeleteSchema(posDeleteRowSchema));
          return ORC.writeDeletes(outputFile.encryptingOutputFile())
              .createWriterFunc(
                  (iSchema, typDesc) -> FlinkOrcWriter.buildWriter(orcPosDeleteSchema, iSchema))
              .withPartition(partition)
              .overwrite()
              .setAll(props)
              .metricsConfig(metricsConfig)
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(outputFile.keyMetadata())
              .transformPaths(path -> StringData.fromString(path.toString()))
              .buildPositionWriter();

        case PARQUET:
          RowType flinkPosDeleteSchema =
              FlinkSchemaUtil.convert(DeleteSchemaUtil.posDeleteSchema(posDeleteRowSchema));
          return Parquet.writeDeletes(outputFile.encryptingOutputFile())
              .createWriterFunc(
                  msgType -> FlinkParquetWriters.buildWriter(flinkPosDeleteSchema, msgType))
              .withPartition(partition)
              .overwrite()
              .setAll(props)
              .metricsConfig(metricsConfig)
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(outputFile.keyMetadata())
              .transformPaths(path -> StringData.fromString(path.toString()))
              .buildPositionWriter();

        default:
          throw new UnsupportedOperationException(
              "Cannot write pos-deletes for unsupported file format: " + format);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
