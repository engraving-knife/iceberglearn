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
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.FileWriterFactory;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;

/**
 * 文件写入器工厂抽象基类，供查询引擎集成模块继承使用。
 *
 * <p>所属模块：iceberg-data（位于 iceberg-api/iceberg-core 之上，向 JVM 应用提供基于 {@link Record} 等通用数据模型的 Iceberg
 * 表读写支持；本类是写路径上的工厂基类）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link FileWriterFactory} 接口，集中构造数据文件、等值删除文件、位置删除文件 三类写入器（{@link DataWriter} / {@link
 *       EqualityDeleteWriter} / {@link PositionDeleteWriter}）。
 *   <li>屏蔽 Avro / Parquet / ORC 三种格式之间构造过程的差异：根据格式走对应 Builder， 统一注入表属性、{@link
 *       MetricsConfig}、分区、加密元数据、排序顺序等公共参数。
 *   <li>暴露一组 {@code configure*} 钩子，让子类在不重写整体流程的前提下注入引擎特定配置。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法模式：把所有写入器共有的“装配”逻辑（取加密输出文件、读 key metadata、 取表属性与 MetricsConfig、设置
 *       spec/partition/sortOrder/overwrite 等）下沉到基类， 子类只需针对各格式的 Builder 做差异化配置（例如 Spark
 *       的向量化写入、批写参数等）。
 *   <li>统一异常处理：底层 Builder 的 {@link IOException} 在基类统一包装为 {@link UncheckedIOException}，简化引擎侧调用。
 *   <li>位置删除采用 {@link MetricsConfig#forPositionDelete(Table)} 单独取配置， 与数据/等值删除的指标采集语义区分开来。
 * </ul>
 *
 * <p>上下游关系：依赖 iceberg-core 的 io/encryption/deletes API 以及 avro/parquet/orc 格式模块；被各引擎集成模块（如
 * iceberg-spark）的具体工厂继承使用。
 *
 * @param <T> 写入记录的 Java 类型
 */
public abstract class BaseFileWriterFactory<T> implements FileWriterFactory<T> {
  private final Table table;
  private final FileFormat dataFileFormat;
  private final Schema dataSchema;
  private final SortOrder dataSortOrder;
  private final FileFormat deleteFileFormat;
  private final int[] equalityFieldIds;
  private final Schema equalityDeleteRowSchema;
  private final SortOrder equalityDeleteSortOrder;
  private final Schema positionDeleteRowSchema;

  /**
   * 构造工厂实例，绑定写数据/删除文件所需的全部静态配置。
   *
   * @param table 目标 Iceberg 表（用于读取属性与 MetricsConfig）
   * @param dataFileFormat 数据文件格式
   * @param dataSchema 数据文件写入 Schema
   * @param dataSortOrder 数据文件排序顺序（可为 null）
   * @param deleteFileFormat 删除文件格式
   * @param equalityFieldIds 等值删除所依赖的字段 ID 数组（仅创建等值删除写入器时使用）
   * @param equalityDeleteRowSchema 等值删除行 Schema
   * @param equalityDeleteSortOrder 等值删除排序顺序
   * @param positionDeleteRowSchema 位置删除行 Schema
   */
  protected BaseFileWriterFactory(
      Table table,
      FileFormat dataFileFormat,
      Schema dataSchema,
      SortOrder dataSortOrder,
      FileFormat deleteFileFormat,
      int[] equalityFieldIds,
      Schema equalityDeleteRowSchema,
      SortOrder equalityDeleteSortOrder,
      Schema positionDeleteRowSchema) {
    this.table = table;
    this.dataFileFormat = dataFileFormat;
    this.dataSchema = dataSchema;
    this.dataSortOrder = dataSortOrder;
    this.deleteFileFormat = deleteFileFormat;
    this.equalityFieldIds = equalityFieldIds;
    this.equalityDeleteRowSchema = equalityDeleteRowSchema;
    this.equalityDeleteSortOrder = equalityDeleteSortOrder;
    this.positionDeleteRowSchema = positionDeleteRowSchema;
  }

  /** 子类钩子：在 Avro 数据写入 Builder 上注入引擎特定配置。 */
  protected abstract void configureDataWrite(Avro.DataWriteBuilder builder);

  /** 子类钩子：在 Avro 等值删除写入 Builder 上注入引擎特定配置。 */
  protected abstract void configureEqualityDelete(Avro.DeleteWriteBuilder builder);

  /** 子类钩子：在 Avro 位置删除写入 Builder 上注入引擎特定配置。 */
  protected abstract void configurePositionDelete(Avro.DeleteWriteBuilder builder);

  /** 子类钩子：在 Parquet 数据写入 Builder 上注入引擎特定配置。 */
  protected abstract void configureDataWrite(Parquet.DataWriteBuilder builder);

  /** 子类钩子：在 Parquet 等值删除写入 Builder 上注入引擎特定配置。 */
  protected abstract void configureEqualityDelete(Parquet.DeleteWriteBuilder builder);

  /** 子类钩子：在 Parquet 位置删除写入 Builder 上注入引擎特定配置。 */
  protected abstract void configurePositionDelete(Parquet.DeleteWriteBuilder builder);

  /** 子类钩子：在 ORC 数据写入 Builder 上注入引擎特定配置。 */
  protected abstract void configureDataWrite(ORC.DataWriteBuilder builder);

  /** 子类钩子：在 ORC 等值删除写入 Builder 上注入引擎特定配置。 */
  protected abstract void configureEqualityDelete(ORC.DeleteWriteBuilder builder);

  /** 子类钩子：在 ORC 位置删除写入 Builder 上注入引擎特定配置。 */
  protected abstract void configurePositionDelete(ORC.DeleteWriteBuilder builder);

  /**
   * 创建数据文件写入器。
   *
   * <p>逻辑：根据 {@link #dataFileFormat} 选择 Avro/Parquet/ORC 之一的 DataWriteBuilder， 依次注入
   * schema、表属性、{@link MetricsConfig}（基于整表）、分区 spec、分区值、 加密 key metadata、排序顺序，并设置 {@code
   * overwrite()}；随后调用子类 {@link #configureDataWrite} 钩子完成引擎特定配置；最终构建 {@link DataWriter}。 任何 {@link
   * IOException} 包装为 {@link UncheckedIOException}。
   *
   * @param file 加密输出文件
   * @param spec 分区 spec
   * @param partition 当前分区值
   * @return 已配置好的数据写入器
   */
  @Override
  public DataWriter<T> newDataWriter(
      EncryptedOutputFile file, PartitionSpec spec, StructLike partition) {
    OutputFile outputFile = file.encryptingOutputFile();
    EncryptionKeyMetadata keyMetadata = file.keyMetadata();
    Map<String, String> properties = table.properties();
    MetricsConfig metricsConfig = MetricsConfig.forTable(table);

    try {
      switch (dataFileFormat) {
        case AVRO:
          Avro.DataWriteBuilder avroBuilder =
              Avro.writeData(outputFile)
                  .schema(dataSchema)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .withSortOrder(dataSortOrder)
                  .overwrite();

          configureDataWrite(avroBuilder);

          return avroBuilder.build();

        case PARQUET:
          Parquet.DataWriteBuilder parquetBuilder =
              Parquet.writeData(outputFile)
                  .schema(dataSchema)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .withSortOrder(dataSortOrder)
                  .overwrite();

          configureDataWrite(parquetBuilder);

          return parquetBuilder.build();

        case ORC:
          ORC.DataWriteBuilder orcBuilder =
              ORC.writeData(outputFile)
                  .schema(dataSchema)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .withSortOrder(dataSortOrder)
                  .overwrite();

          configureDataWrite(orcBuilder);

          return orcBuilder.build();

        default:
          throw new UnsupportedOperationException(
              "Unsupported data file format: " + dataFileFormat);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 创建等值删除文件写入器。
   *
   * <p>逻辑：与 {@link #newDataWriter} 类似，但调用各格式的 writeDeletes Builder 并最终 构造 {@link
   * EqualityDeleteWriter}；额外注入等值删除行 Schema、等值字段 ID 与等值删除 排序顺序；MetricsConfig 仍按整表获取。
   *
   * @param file 加密输出文件
   * @param spec 分区 spec
   * @param partition 当前分区值
   * @return 已配置好的等值删除写入器
   */
  @Override
  public EqualityDeleteWriter<T> newEqualityDeleteWriter(
      EncryptedOutputFile file, PartitionSpec spec, StructLike partition) {
    OutputFile outputFile = file.encryptingOutputFile();
    EncryptionKeyMetadata keyMetadata = file.keyMetadata();
    Map<String, String> properties = table.properties();
    MetricsConfig metricsConfig = MetricsConfig.forTable(table);

    try {
      switch (deleteFileFormat) {
        case AVRO:
          Avro.DeleteWriteBuilder avroBuilder =
              Avro.writeDeletes(outputFile)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .rowSchema(equalityDeleteRowSchema)
                  .equalityFieldIds(equalityFieldIds)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .withSortOrder(equalityDeleteSortOrder)
                  .overwrite();

          configureEqualityDelete(avroBuilder);

          return avroBuilder.buildEqualityWriter();

        case PARQUET:
          Parquet.DeleteWriteBuilder parquetBuilder =
              Parquet.writeDeletes(outputFile)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .rowSchema(equalityDeleteRowSchema)
                  .equalityFieldIds(equalityFieldIds)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .withSortOrder(equalityDeleteSortOrder)
                  .overwrite();

          configureEqualityDelete(parquetBuilder);

          return parquetBuilder.buildEqualityWriter();

        case ORC:
          ORC.DeleteWriteBuilder orcBuilder =
              ORC.writeDeletes(outputFile)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .rowSchema(equalityDeleteRowSchema)
                  .equalityFieldIds(equalityFieldIds)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .withSortOrder(equalityDeleteSortOrder)
                  .overwrite();

          configureEqualityDelete(orcBuilder);

          return orcBuilder.buildEqualityWriter();

        default:
          throw new UnsupportedOperationException(
              "Unsupported format for equality deletes: " + deleteFileFormat);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create new equality delete writer", e);
    }
  }

  /**
   * 创建位置删除文件写入器。
   *
   * <p>逻辑：与 {@link #newEqualityDeleteWriter} 类似，但仅注入位置删除行 Schema， 不需要等值字段 ID 与排序顺序；MetricsConfig 改用
   * {@link MetricsConfig#forPositionDelete(Table)}， 以匹配位置删除的指标采集语义。
   *
   * @param file 加密输出文件
   * @param spec 分区 spec
   * @param partition 当前分区值
   * @return 已配置好的位置删除写入器
   */
  @Override
  public PositionDeleteWriter<T> newPositionDeleteWriter(
      EncryptedOutputFile file, PartitionSpec spec, StructLike partition) {
    OutputFile outputFile = file.encryptingOutputFile();
    EncryptionKeyMetadata keyMetadata = file.keyMetadata();
    Map<String, String> properties = table.properties();
    MetricsConfig metricsConfig = MetricsConfig.forPositionDelete(table);

    try {
      switch (deleteFileFormat) {
        case AVRO:
          Avro.DeleteWriteBuilder avroBuilder =
              Avro.writeDeletes(outputFile)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .rowSchema(positionDeleteRowSchema)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .overwrite();

          configurePositionDelete(avroBuilder);

          return avroBuilder.buildPositionWriter();

        case PARQUET:
          Parquet.DeleteWriteBuilder parquetBuilder =
              Parquet.writeDeletes(outputFile)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .rowSchema(positionDeleteRowSchema)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .overwrite();

          configurePositionDelete(parquetBuilder);

          return parquetBuilder.buildPositionWriter();

        case ORC:
          ORC.DeleteWriteBuilder orcBuilder =
              ORC.writeDeletes(outputFile)
                  .setAll(properties)
                  .metricsConfig(metricsConfig)
                  .rowSchema(positionDeleteRowSchema)
                  .withSpec(spec)
                  .withPartition(partition)
                  .withKeyMetadata(keyMetadata)
                  .overwrite();

          configurePositionDelete(orcBuilder);

          return orcBuilder.buildPositionWriter();

        default:
          throw new UnsupportedOperationException(
              "Unsupported format for position deletes: " + deleteFileFormat);
      }

    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create new position delete writer", e);
    }
  }

  /** 返回数据文件写入 Schema。 */
  protected Schema dataSchema() {
    return dataSchema;
  }

  /** 返回等值删除行 Schema。 */
  protected Schema equalityDeleteRowSchema() {
    return equalityDeleteRowSchema;
  }

  /** 返回位置删除行 Schema。 */
  protected Schema positionDeleteRowSchema() {
    return positionDeleteRowSchema;
  }
}
