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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
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
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.DeleteSchemaUtil;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.data.SparkAvroWriter;
import org.apache.iceberg.spark.data.SparkOrcWriter;
import org.apache.iceberg.spark.data.SparkParquetWriters;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的工厂，负责创建实例。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkAppenderFactory。
 *
 * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkAppenderFactory implements FileAppenderFactory<InternalRow> {
  private final Map<String, String> properties;
  private final Schema writeSchema;
  private final StructType dsSchema;
  private final PartitionSpec spec;
  private final int[] equalityFieldIds;
  private final Schema eqDeleteRowSchema;
  private final Schema posDeleteRowSchema;

  private StructType eqDeleteSparkType = null;
  private StructType posDeleteSparkType = null;

  SparkAppenderFactory(
      Map<String, String> properties,
      Schema writeSchema,
      StructType dsSchema,
      PartitionSpec spec,
      int[] equalityFieldIds,
      Schema eqDeleteRowSchema,
      Schema posDeleteRowSchema) {
    this.properties = properties;
    this.writeSchema = writeSchema;
    this.dsSchema = dsSchema;
    this.spec = spec;
    this.equalityFieldIds = equalityFieldIds;
    this.eqDeleteRowSchema = eqDeleteRowSchema;
    this.posDeleteRowSchema = posDeleteRowSchema;
  }

  /** 构造并返回目标对象。 */
  static Builder builderFor(Table table, Schema writeSchema, StructType dsSchema) {
    /** 构造并返回目标对象。 */
    return new Builder(table, writeSchema, dsSchema);
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 Builder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  static class Builder {
    private final Table table;
    private final Schema writeSchema;
    private final StructType dsSchema;
    private PartitionSpec spec;
    private int[] equalityFieldIds;
    private Schema eqDeleteRowSchema;
    private Schema posDeleteRowSchema;

    Builder(Table table, Schema writeSchema, StructType dsSchema) {
      this.table = table;
      this.spec = table.spec();
      this.writeSchema = writeSchema;
      this.dsSchema = dsSchema;
    }

    /** 执行该方法的具体逻辑。 */
    Builder spec(PartitionSpec newSpec) {
      this.spec = newSpec;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder equalityFieldIds(int[] newEqualityFieldIds) {
      this.equalityFieldIds = newEqualityFieldIds;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder eqDeleteRowSchema(Schema newEqDeleteRowSchema) {
      this.eqDeleteRowSchema = newEqDeleteRowSchema;
      return this;
    }

    /** 执行该方法的具体逻辑。 */
    Builder posDelRowSchema(Schema newPosDelRowSchema) {
      this.posDeleteRowSchema = newPosDelRowSchema;
      return this;
    }

    /** 构造并返回目标对象。 */
    SparkAppenderFactory build() {
      Preconditions.checkNotNull(table, "Table must not be null");
      Preconditions.checkNotNull(writeSchema, "Write Schema must not be null");
      Preconditions.checkNotNull(dsSchema, "DS Schema must not be null");
      if (equalityFieldIds != null) {
        Preconditions.checkNotNull(
            eqDeleteRowSchema,
            "Equality Field Ids and Equality Delete Row Schema" + " must be set together");
      }
      if (eqDeleteRowSchema != null) {
        Preconditions.checkNotNull(
            equalityFieldIds,
            "Equality Field Ids and Equality Delete Row Schema" + " must be set together");
      }

      /** 执行该方法的具体逻辑。 */
      return new SparkAppenderFactory(
          table.properties(),
          writeSchema,
          dsSchema,
          spec,
          equalityFieldIds,
          eqDeleteRowSchema,
          posDeleteRowSchema);
    }
  }

  /** 执行该方法的具体逻辑。 */
  private StructType lazyEqDeleteSparkType() {
    if (eqDeleteSparkType == null) {
      Preconditions.checkNotNull(eqDeleteRowSchema, "Equality delete row schema shouldn't be null");
      this.eqDeleteSparkType = SparkSchemaUtil.convert(eqDeleteRowSchema);
    }
    return eqDeleteSparkType;
  }

  /** 执行该方法的具体逻辑。 */
  private StructType lazyPosDeleteSparkType() {
    if (posDeleteSparkType == null) {
      Preconditions.checkNotNull(
          posDeleteRowSchema, "Position delete row schema shouldn't be null");
      this.posDeleteSparkType = SparkSchemaUtil.convert(posDeleteRowSchema);
    }
    return posDeleteSparkType;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param file 参数
   * @param fileFormat 参数
   * @return 结果对象
   */
  @Override
  public FileAppender<InternalRow> newAppender(OutputFile file, FileFormat fileFormat) {
    MetricsConfig metricsConfig = MetricsConfig.fromProperties(properties);
    try {
      switch (fileFormat) {
        case PARQUET:
          return Parquet.write(file)
              .createWriterFunc(msgType -> SparkParquetWriters.buildWriter(dsSchema, msgType))
              .setAll(properties)
              .metricsConfig(metricsConfig)
              .schema(writeSchema)
              .overwrite()
              .build();

        case AVRO:
          return Avro.write(file)
              .createWriterFunc(ignored -> new SparkAvroWriter(dsSchema))
              .setAll(properties)
              .schema(writeSchema)
              .overwrite()
              .build();

        case ORC:
          return ORC.write(file)
              .createWriterFunc(SparkOrcWriter::new)
              .setAll(properties)
              .metricsConfig(metricsConfig)
              .schema(writeSchema)
              .overwrite()
              .build();

        default:
          throw new UnsupportedOperationException("Cannot write unknown format: " + fileFormat);
      }
    } catch (IOException e) {
      /** 执行该方法的具体逻辑。 */
      throw new RuntimeIOException(e);
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param file 参数
   * @param format 参数
   * @param partition 参数
   * @return 结果对象
   */
  @Override
  public DataWriter<InternalRow> newDataWriter(
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
   * 执行该方法的具体逻辑。
   *
   * @param file 参数
   * @param format 参数
   * @param partition 参数
   * @return 结果对象
   */
  @Override
  public EqualityDeleteWriter<InternalRow> newEqDeleteWriter(
      EncryptedOutputFile file, FileFormat format, StructLike partition) {
    Preconditions.checkState(
        equalityFieldIds != null && equalityFieldIds.length > 0,
        "Equality field ids shouldn't be null or empty when creating equality-delete writer");
    Preconditions.checkNotNull(
        eqDeleteRowSchema,
        "Equality delete row schema shouldn't be null when creating equality-delete writer");

    try {
      switch (format) {
        case PARQUET:
          return Parquet.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(
                  msgType -> SparkParquetWriters.buildWriter(lazyEqDeleteSparkType(), msgType))
              .overwrite()
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withPartition(partition)
              .equalityFieldIds(equalityFieldIds)
              .withKeyMetadata(file.keyMetadata())
              .buildEqualityWriter();

        case AVRO:
          return Avro.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(ignored -> new SparkAvroWriter(lazyEqDeleteSparkType()))
              .overwrite()
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withPartition(partition)
              .equalityFieldIds(equalityFieldIds)
              .withKeyMetadata(file.keyMetadata())
              .buildEqualityWriter();

        case ORC:
          return ORC.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(SparkOrcWriter::new)
              .overwrite()
              .rowSchema(eqDeleteRowSchema)
              .withSpec(spec)
              .withPartition(partition)
              .equalityFieldIds(equalityFieldIds)
              .withKeyMetadata(file.keyMetadata())
              .buildEqualityWriter();

        default:
          throw new UnsupportedOperationException(
              "Cannot write equality-deletes for unsupported file format: " + format);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create new equality delete writer", e);
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param file 参数
   * @param format 参数
   * @param partition 参数
   * @return 结果对象
   */
  @Override
  public PositionDeleteWriter<InternalRow> newPosDeleteWriter(
      EncryptedOutputFile file, FileFormat format, StructLike partition) {
    try {
      switch (format) {
        case PARQUET:
          StructType sparkPosDeleteSchema =
              SparkSchemaUtil.convert(DeleteSchemaUtil.posDeleteSchema(posDeleteRowSchema));
          return Parquet.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(
                  msgType -> SparkParquetWriters.buildWriter(sparkPosDeleteSchema, msgType))
              .overwrite()
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withPartition(partition)
              .withKeyMetadata(file.keyMetadata())
              .transformPaths(path -> UTF8String.fromString(path.toString()))
              .buildPositionWriter();

        case AVRO:
          return Avro.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(ignored -> new SparkAvroWriter(lazyPosDeleteSparkType()))
              .overwrite()
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withPartition(partition)
              .withKeyMetadata(file.keyMetadata())
              .buildPositionWriter();

        case ORC:
          return ORC.writeDeletes(file.encryptingOutputFile())
              .createWriterFunc(SparkOrcWriter::new)
              .overwrite()
              .rowSchema(posDeleteRowSchema)
              .withSpec(spec)
              .withPartition(partition)
              .withKeyMetadata(file.keyMetadata())
              .transformPaths(path -> UTF8String.fromString(path.toString()))
              .buildPositionWriter();

        default:
          throw new UnsupportedOperationException(
              "Cannot write pos-deletes for unsupported file format: " + format);
      }

    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create new equality delete writer", e);
    }
  }
}
