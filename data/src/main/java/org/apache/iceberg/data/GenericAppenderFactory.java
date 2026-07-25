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
package org.apache.iceberg.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.avro.DataWriter;
import org.apache.iceberg.data.orc.GenericOrcWriter;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 面向 {@link Record} 的通用文件追加器工厂，实现 {@link FileAppenderFactory}。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持； 本类是写路径上“通用
 * Record”场景的工厂实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>创建 Avro/Parquet/ORC 三种格式的 {@link FileAppender}，用于向数据文件追加 {@link Record}。
 *   <li>创建 {@link org.apache.iceberg.io.DataWriter}（数据写入器）、 {@link EqualityDeleteWriter}（等值删除写入器）、
 *       {@link PositionDeleteWriter}（位置删除写入器），覆盖 Iceberg 写路径所需的全部写入器类型。
 *   <li>持有 schema、分区 spec、等值字段 ID、删除行 Schema 等配置，并提供 {@code set/setAll} 注入表属性（用于 MetricsConfig 等）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@code BaseFileWriterFactory} 区分：后者面向引擎集成（暴露 configure* 钩子）； 本类面向“通用
 *       Record”直接使用，构造简单、无需继承。
 *   <li>统一通过 {@code createWriterFunc} 把格式层与 {@code GenericParquetWriter} / {@code GenericOrcWriter}
 *       / {@code DataWriter}（Avro）这些 Record 适配器对接起来。
 *   <li>等值删除写入器要求 equalityFieldIds 与 eqDeleteRowSchema 非空，构造时校验避免运行期错误。
 * </ul>
 *
 * <p>上下游关系：依赖 iceberg-core 的 io 接口与 avro/parquet/orc 写入 Builder；被 {@code IcebergGenerics}
 * 之外的工具/测试/迁移流程直接调用以写入 Record 数据。
 */
public class GenericAppenderFactory implements FileAppenderFactory<Record> {

  private final Schema schema;
  private final PartitionSpec spec;
  private final int[] equalityFieldIds;
  private final Schema eqDeleteRowSchema;
  private final Schema posDeleteRowSchema;
  private final Map<String, String> config = Maps.newHashMap();

  /** 构造不分区的通用追加器工厂（无删除写入支持）。 */
  public GenericAppenderFactory(Schema schema) {
    this(schema, PartitionSpec.unpartitioned(), null, null, null);
  }

  /** 构造指定分区的通用追加器工厂（无删除写入支持）。 */
  public GenericAppenderFactory(Schema schema, PartitionSpec spec) {
    this(schema, spec, null, null, null);
  }

  /**
   * 完整构造函数：同时支持数据写入与等值/位置删除写入。
   *
   * @param schema 写入 Schema
   * @param spec 分区 spec
   * @param equalityFieldIds 等值删除所依赖的字段 ID 数组（仅等值删除需要）
   * @param eqDeleteRowSchema 等值删除行 Schema
   * @param posDeleteRowSchema 位置删除行 Schema
   */
  public GenericAppenderFactory(
      Schema schema,
      PartitionSpec spec,
      int[] equalityFieldIds,
      Schema eqDeleteRowSchema,
      Schema posDeleteRowSchema) {
    this.schema = schema;
    this.spec = spec;
    this.equalityFieldIds = equalityFieldIds;
    this.eqDeleteRowSchema = eqDeleteRowSchema;
    this.posDeleteRowSchema = posDeleteRowSchema;
  }

  /** 追加单个写入属性（链式）。 */
  public GenericAppenderFactory set(String property, String value) {
    config.put(property, value);
    return this;
  }

  /** 批量追加写入属性（链式）。 */
  public GenericAppenderFactory setAll(Map<String, String> properties) {
    config.putAll(properties);
    return this;
  }

  /**
   * 创建通用 {@link FileAppender}，按指定格式写入数据文件。
   *
   * <p>逻辑：从 config 解析 {@link MetricsConfig}，按格式走对应 Builder：Avro 用 {@link DataWriter#create}，Parquet
   * 用 {@code GenericParquetWriter.buildWriter}， ORC 用 {@code GenericOrcWriter.buildWriter}；统一注入
   * schema、metrics、属性并 {@code overwrite}。 不支持的格式抛 {@link UnsupportedOperationException}，IO 异常包装为
   * {@link UncheckedIOException}。
   *
   * @param outputFile 输出文件
   * @param fileFormat 目标格式
   * @return 已配置的追加器
   */
  @Override
  public FileAppender<Record> newAppender(OutputFile outputFile, FileFormat fileFormat) {
    MetricsConfig metricsConfig = MetricsConfig.fromProperties(config);
    try {
      switch (fileFormat) {
        case AVRO:
          return Avro.write(outputFile)
              .schema(schema)
              .createWriterFunc(DataWriter::create)
              .metricsConfig(metricsConfig)
              .setAll(config)
              .overwrite()
              .build();

        case PARQUET:
          return Parquet.write(outputFile)
              .schema(schema)
              .createWriterFunc(GenericParquetWriter::buildWriter)
              .setAll(config)
              .metricsConfig(metricsConfig)
              .overwrite()
              .build();

        case ORC:
          return ORC.write(outputFile)
              .schema(schema)
              .createWriterFunc(GenericOrcWriter::buildWriter)
              .setAll(config)
              .metricsConfig(metricsConfig)
              .overwrite()
              .build();

        default:
          throw new UnsupportedOperationException(
              "Cannot write unknown file format: " + fileFormat);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 创建数据写入器 {@link org.apache.iceberg.io.DataWriter}。
   *
   * <p>逻辑：用 {@link #newAppender} 构造追加器，再连同格式、文件路径、分区 spec、分区值、 加密 key metadata 包装成 {@link
   * org.apache.iceberg.io.DataWriter}。
   *
   * @param file 加密输出文件
   * @param format 文件格式
   * @param partition 当前分区值
   * @return 数据写入器
   */
  @Override
  public org.apache.iceberg.io.DataWriter<Record> newDataWriter(
      EncryptedOutputFile file, FileFormat format, StructLike partition) {
    return new org.apache.iceberg.io.DataWriter<>(
        newAppender(file.encryptingOutputFile(), format),
        format,
        file.encryptingOutputFile().location(),
        spec,
        partition,
        file.keyMetadata());
  }

  /**
   * 创建等值删除写入器。
   *
   * <p>逻辑：先校验 equalityFieldIds 非空且 eqDeleteRowSchema 非空；再按格式走 writeDeletes Builder，注入等值字段 ID、等值删除行
   * Schema、分区、key metadata 等， 最终 {@code buildEqualityWriter}。
   *
   * @param file 加密输出文件
   * @param format 文件格式
   * @param partition 当前分区值
   * @return 等值删除写入器
   * @throws IllegalStateException 若 equalityFieldIds 为 null 或空
   * @throws NullPointerException 若 eqDeleteRowSchema 为 null
   */
  @Override
  public EqualityDeleteWriter<Record> newEqDeleteWriter(
      EncryptedOutputFile file, FileFormat format, StructLike partition) {
    Preconditions.checkState(
        equalityFieldIds != null && equalityFieldIds.length > 0,
        "Equality field ids shouldn't be null or empty when creating equality-delete writer");
    Preconditions.checkNotNull(
        eqDeleteRowSchema,
        "Equality delete row schema shouldn't be null when creating equality-delete writer");

    MetricsConfig metricsConfig = MetricsConfig.fromProperties(config);
    try {
      switch (format) {
        case AVRO:
          return Avro.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(DataWriter::create)
              .withPartition(partition)
              .overwrite()
              .setAll(config)
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(file.keyMetadata())
              .equalityFieldIds(equalityFieldIds)
              .buildEqualityWriter();

        case ORC:
          return ORC.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(GenericOrcWriter::buildWriter)
              .withPartition(partition)
              .overwrite()
              .setAll(config)
              .metricsConfig(metricsConfig)
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(file.keyMetadata())
              .equalityFieldIds(equalityFieldIds)
              .buildEqualityWriter();

        case PARQUET:
          return Parquet.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(GenericParquetWriter::buildWriter)
              .withPartition(partition)
              .overwrite()
              .setAll(config)
              .metricsConfig(metricsConfig)
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(file.keyMetadata())
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
   * 创建位置删除写入器。
   *
   * <p>逻辑：按格式走 writeDeletes Builder，注入位置删除行 Schema、分区、key metadata 等， 最终 {@code
   * buildPositionWriter}。与等值删除不同，不需要等值字段 ID 与排序顺序。
   *
   * @param file 加密输出文件
   * @param format 文件格式
   * @param partition 当前分区值
   * @return 位置删除写入器
   */
  @Override
  public PositionDeleteWriter<Record> newPosDeleteWriter(
      EncryptedOutputFile file, FileFormat format, StructLike partition) {
    MetricsConfig metricsConfig = MetricsConfig.fromProperties(config);
    try {
      switch (format) {
        case AVRO:
          return Avro.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(DataWriter::create)
              .withPartition(partition)
              .overwrite()
              .setAll(config)
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(file.keyMetadata())
              .buildPositionWriter();

        case ORC:
          return ORC.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(GenericOrcWriter::buildWriter)
              .withPartition(partition)
              .overwrite()
              .setAll(config)
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(file.keyMetadata())
              .buildPositionWriter();

        case PARQUET:
          return Parquet.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(GenericParquetWriter::buildWriter)
              .withPartition(partition)
              .overwrite()
              .setAll(config)
              .metricsConfig(metricsConfig)
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withKeyMetadata(file.keyMetadata())
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
