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
package org.apache.iceberg.spark;

import static org.apache.iceberg.DistributionMode.HASH;
import static org.apache.iceberg.DistributionMode.NONE;
import static org.apache.iceberg.DistributionMode.RANGE;
import static org.apache.iceberg.TableProperties.AVRO_COMPRESSION;
import static org.apache.iceberg.TableProperties.AVRO_COMPRESSION_LEVEL;
import static org.apache.iceberg.TableProperties.DELETE_AVRO_COMPRESSION;
import static org.apache.iceberg.TableProperties.DELETE_AVRO_COMPRESSION_LEVEL;
import static org.apache.iceberg.TableProperties.DELETE_ORC_COMPRESSION;
import static org.apache.iceberg.TableProperties.DELETE_ORC_COMPRESSION_STRATEGY;
import static org.apache.iceberg.TableProperties.DELETE_PARQUET_COMPRESSION;
import static org.apache.iceberg.TableProperties.DELETE_PARQUET_COMPRESSION_LEVEL;
import static org.apache.iceberg.TableProperties.ORC_COMPRESSION;
import static org.apache.iceberg.TableProperties.ORC_COMPRESSION_STRATEGY;
import static org.apache.iceberg.TableProperties.PARQUET_COMPRESSION;
import static org.apache.iceberg.TableProperties.PARQUET_COMPRESSION_LEVEL;

import java.util.Locale;
import java.util.Map;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.IsolationLevel;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.spark.sql.RuntimeConfig;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.write.RowLevelOperation.Command;
import org.apache.spark.sql.internal.SQLConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 写入配置，聚合 SparkSession 配置、表属性、写选项，解析出写入时使用的最终写参数。
 *
 * <p>设计意图：采用建造者模式，统一管理写入相关的可调参数（如文件格式、目标大小、分布方式）。
 *
 * <p>上下游关系：由 SparkWriteBuilder / SparkWrite / SparkAppenderFactory 等写入链路使用。
 */
public class SparkWriteConf {

  private static final Logger LOG = LoggerFactory.getLogger(SparkWriteConf.class);

  private final Table table;
  private final String branch;
  private final RuntimeConfig sessionConf;
  private final Map<String, String> writeOptions;
  private final SparkConfParser confParser;

  public SparkWriteConf(SparkSession spark, Table table, Map<String, String> writeOptions) {
    this(spark, table, null, writeOptions);
  }

  public SparkWriteConf(
      SparkSession spark, Table table, String branch, Map<String, String> writeOptions) {
    this.table = table;
    this.branch = branch;
    this.sessionConf = spark.conf();
    this.writeOptions = writeOptions;
    this.confParser = new SparkConfParser(spark, table, writeOptions);

    SparkUtil.validateTimestampWithoutTimezoneConfig(spark.conf(), writeOptions);
  }
  /** 执行 checkNullability 相关操作。 */
  public boolean checkNullability() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.CHECK_NULLABILITY)
        .sessionConf(SparkSQLProperties.CHECK_NULLABILITY)
        .defaultValue(SparkSQLProperties.CHECK_NULLABILITY_DEFAULT)
        .parse();
  }
  /** 执行 checkOrdering 相关操作。 */
  public boolean checkOrdering() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.CHECK_ORDERING)
        .sessionConf(SparkSQLProperties.CHECK_ORDERING)
        .defaultValue(SparkSQLProperties.CHECK_ORDERING_DEFAULT)
        .parse();
  }
  /** 执行 overwriteMode 相关操作。 */
  public String overwriteMode() {
    String overwriteMode = writeOptions.get(SparkWriteOptions.OVERWRITE_MODE);
    return overwriteMode != null ? overwriteMode.toLowerCase(Locale.ROOT) : null;
  }
  /** 执行 wapEnabled 相关操作。 */
  public boolean wapEnabled() {
    return confParser
        .booleanConf()
        .tableProperty(TableProperties.WRITE_AUDIT_PUBLISH_ENABLED)
        .defaultValue(TableProperties.WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT)
        .parse();
  }
  /** 执行 wapId 相关操作。 */
  public String wapId() {
    return sessionConf.get(SparkSQLProperties.WAP_ID, null);
  }
  /** 执行 mergeSchema 相关操作。 */
  public boolean mergeSchema() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.MERGE_SCHEMA)
        .option(SparkWriteOptions.SPARK_MERGE_SCHEMA)
        .defaultValue(SparkWriteOptions.MERGE_SCHEMA_DEFAULT)
        .parse();
  }
  /** 执行 outputSpecId 相关操作。 */
  public int outputSpecId() {
    int outputSpecId =
        confParser
            .intConf()
            .option(SparkWriteOptions.OUTPUT_SPEC_ID)
            .defaultValue(table.spec().specId())
            .parse();
    Preconditions.checkArgument(
        table.specs().containsKey(outputSpecId),
        "Output spec id %s is not a valid spec id for table",
        outputSpecId);
    return outputSpecId;
  }
  /** 执行 dataFileFormat 相关操作。 */
  public FileFormat dataFileFormat() {
    String valueAsString =
        confParser
            .stringConf()
            .option(SparkWriteOptions.WRITE_FORMAT)
            .tableProperty(TableProperties.DEFAULT_FILE_FORMAT)
            .defaultValue(TableProperties.DEFAULT_FILE_FORMAT_DEFAULT)
            .parse();
    return FileFormat.fromString(valueAsString);
  }
  /** 执行 targetDataFileSize 相关操作。 */
  public long targetDataFileSize() {
    return confParser
        .longConf()
        .option(SparkWriteOptions.TARGET_FILE_SIZE_BYTES)
        .tableProperty(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES)
        .defaultValue(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT)
        .parse();
  }
  /** 执行 fanoutWriterEnabled 相关操作。 */
  public boolean fanoutWriterEnabled() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.FANOUT_ENABLED)
        .tableProperty(TableProperties.SPARK_WRITE_PARTITIONED_FANOUT_ENABLED)
        .defaultValue(TableProperties.SPARK_WRITE_PARTITIONED_FANOUT_ENABLED_DEFAULT)
        .parse();
  }
  /** 执行 deleteFileFormat 相关操作。 */
  public FileFormat deleteFileFormat() {
    String valueAsString =
        confParser
            .stringConf()
            .option(SparkWriteOptions.DELETE_FORMAT)
            .tableProperty(TableProperties.DELETE_DEFAULT_FILE_FORMAT)
            .parseOptional();
    return valueAsString != null ? FileFormat.fromString(valueAsString) : dataFileFormat();
  }
  /** 执行 targetDeleteFileSize 相关操作。 */
  public long targetDeleteFileSize() {
    return confParser
        .longConf()
        .option(SparkWriteOptions.TARGET_DELETE_FILE_SIZE_BYTES)
        .tableProperty(TableProperties.DELETE_TARGET_FILE_SIZE_BYTES)
        .defaultValue(TableProperties.DELETE_TARGET_FILE_SIZE_BYTES_DEFAULT)
        .parse();
  }
  /** 执行 extraSnapshotMetadata 相关操作。 */
  public Map<String, String> extraSnapshotMetadata() {
    Map<String, String> extraSnapshotMetadata = Maps.newHashMap();

    writeOptions.forEach(
        (key, value) -> {
          if (key.startsWith(SnapshotSummary.EXTRA_METADATA_PREFIX)) {
            extraSnapshotMetadata.put(
                key.substring(SnapshotSummary.EXTRA_METADATA_PREFIX.length()), value);
          }
        });

    return extraSnapshotMetadata;
  }
  /** 执行 rewrittenFileSetId 相关操作。 */
  public String rewrittenFileSetId() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID)
        .parseOptional();
  }
  /** 执行 writeRequirements 相关操作。 */
  public SparkWriteRequirements writeRequirements() {
    if (ignoreTableDistributionAndOrdering()) {
      LOG.info("Skipping distribution/ordering: disabled per job configuration");
      return SparkWriteRequirements.EMPTY;
    }

    return SparkWriteUtil.writeRequirements(table, distributionMode(), fanoutWriterEnabled());
  }

  @VisibleForTesting
  DistributionMode distributionMode() {
    String modeName =
        confParser
            .stringConf()
            .option(SparkWriteOptions.DISTRIBUTION_MODE)
            .sessionConf(SparkSQLProperties.DISTRIBUTION_MODE)
            .tableProperty(TableProperties.WRITE_DISTRIBUTION_MODE)
            .parseOptional();

    if (modeName != null) {
      DistributionMode mode = DistributionMode.fromName(modeName);
      return adjustWriteDistributionMode(mode);
    } else {
      return defaultWriteDistributionMode();
    }
  }
  /** 执行 adjustWriteDistributionMode 相关操作。 */
  private DistributionMode adjustWriteDistributionMode(DistributionMode mode) {
    if (mode == RANGE && table.spec().isUnpartitioned() && table.sortOrder().isUnsorted()) {
      return NONE;
    } else if (mode == HASH && table.spec().isUnpartitioned()) {
      return NONE;
    } else {
      return mode;
    }
  }
  /** 执行 defaultWriteDistributionMode 相关操作。 */
  private DistributionMode defaultWriteDistributionMode() {
    if (table.sortOrder().isSorted()) {
      return RANGE;
    } else if (table.spec().isPartitioned()) {
      return HASH;
    } else {
      return NONE;
    }
  }
  /** 执行 copyOnWriteRequirements 相关操作。 */
  public SparkWriteRequirements copyOnWriteRequirements(Command command) {
    if (ignoreTableDistributionAndOrdering()) {
      LOG.info("Skipping distribution/ordering: disabled per job configuration");
      return SparkWriteRequirements.EMPTY;
    }

    return SparkWriteUtil.copyOnWriteRequirements(
        table, command, copyOnWriteDistributionMode(command), fanoutWriterEnabled());
  }

  @VisibleForTesting
  DistributionMode copyOnWriteDistributionMode(Command command) {
    switch (command) {
      case DELETE:
        return deleteDistributionMode();
      case UPDATE:
        return updateDistributionMode();
      case MERGE:
        return copyOnWriteMergeDistributionMode();
      default:
        throw new IllegalArgumentException("Unexpected command: " + command);
    }
  }
  /** 执行 positionDeltaRequirements 相关操作。 */
  public SparkWriteRequirements positionDeltaRequirements(Command command) {
    if (ignoreTableDistributionAndOrdering()) {
      LOG.info("Skipping distribution/ordering: disabled per job configuration");
      return SparkWriteRequirements.EMPTY;
    }

    return SparkWriteUtil.positionDeltaRequirements(
        table, command, positionDeltaDistributionMode(command), fanoutWriterEnabled());
  }

  @VisibleForTesting
  DistributionMode positionDeltaDistributionMode(Command command) {
    switch (command) {
      case DELETE:
        return deleteDistributionMode();
      case UPDATE:
        return updateDistributionMode();
      case MERGE:
        return positionDeltaMergeDistributionMode();
      default:
        throw new IllegalArgumentException("Unexpected command: " + command);
    }
  }
  /** 执行 deleteDistributionMode 相关操作。 */
  private DistributionMode deleteDistributionMode() {
    String deleteModeName =
        confParser
            .stringConf()
            .option(SparkWriteOptions.DISTRIBUTION_MODE)
            .sessionConf(SparkSQLProperties.DISTRIBUTION_MODE)
            .tableProperty(TableProperties.DELETE_DISTRIBUTION_MODE)
            .defaultValue(TableProperties.WRITE_DISTRIBUTION_MODE_HASH)
            .parse();
    return DistributionMode.fromName(deleteModeName);
  }
  /** 执行 updateDistributionMode 相关操作。 */
  private DistributionMode updateDistributionMode() {
    String updateModeName =
        confParser
            .stringConf()
            .option(SparkWriteOptions.DISTRIBUTION_MODE)
            .sessionConf(SparkSQLProperties.DISTRIBUTION_MODE)
            .tableProperty(TableProperties.UPDATE_DISTRIBUTION_MODE)
            .defaultValue(TableProperties.WRITE_DISTRIBUTION_MODE_HASH)
            .parse();
    return DistributionMode.fromName(updateModeName);
  }
  /** 执行 copyOnWriteMergeDistributionMode 相关操作。 */
  private DistributionMode copyOnWriteMergeDistributionMode() {
    String mergeModeName =
        confParser
            .stringConf()
            .option(SparkWriteOptions.DISTRIBUTION_MODE)
            .sessionConf(SparkSQLProperties.DISTRIBUTION_MODE)
            .tableProperty(TableProperties.MERGE_DISTRIBUTION_MODE)
            .parseOptional();

    if (mergeModeName != null) {
      DistributionMode mergeMode = DistributionMode.fromName(mergeModeName);
      return adjustWriteDistributionMode(mergeMode);

    } else if (table.spec().isPartitioned()) {
      return HASH;

    } else {
      return distributionMode();
    }
  }
  /** 执行 positionDeltaMergeDistributionMode 相关操作。 */
  private DistributionMode positionDeltaMergeDistributionMode() {
    String mergeModeName =
        confParser
            .stringConf()
            .option(SparkWriteOptions.DISTRIBUTION_MODE)
            .sessionConf(SparkSQLProperties.DISTRIBUTION_MODE)
            .tableProperty(TableProperties.MERGE_DISTRIBUTION_MODE)
            .defaultValue(TableProperties.WRITE_DISTRIBUTION_MODE_HASH)
            .parse();
    return DistributionMode.fromName(mergeModeName);
  }
  /** 执行 ignoreTableDistributionAndOrdering 相关操作。 */
  private boolean ignoreTableDistributionAndOrdering() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.USE_TABLE_DISTRIBUTION_AND_ORDERING)
        .defaultValue(SparkWriteOptions.USE_TABLE_DISTRIBUTION_AND_ORDERING_DEFAULT)
        .negate()
        .parse();
  }
  /** 执行 validateFromSnapshotId 相关操作。 */
  public Long validateFromSnapshotId() {
    return confParser
        .longConf()
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID)
        .parseOptional();
  }
  /** 执行 isolationLevel 相关操作。 */
  public IsolationLevel isolationLevel() {
    String isolationLevelName =
        confParser.stringConf().option(SparkWriteOptions.ISOLATION_LEVEL).parseOptional();
    return isolationLevelName != null ? IsolationLevel.fromName(isolationLevelName) : null;
  }
  /** 执行 caseSensitive 相关操作。 */
  public boolean caseSensitive() {
    return confParser
        .booleanConf()
        .sessionConf(SQLConf.CASE_SENSITIVE().key())
        .defaultValue(SQLConf.CASE_SENSITIVE().defaultValueString())
        .parse();
  }
  /** 执行 branch 相关操作。 */
  public String branch() {
    if (wapEnabled()) {
      String wapId = wapId();
      String wapBranch =
          confParser.stringConf().sessionConf(SparkSQLProperties.WAP_BRANCH).parseOptional();

      ValidationException.check(
          wapId == null || wapBranch == null,
          "Cannot set both WAP ID and branch, but got ID [%s] and branch [%s]",
          wapId,
          wapBranch);

      if (wapBranch != null) {
        ValidationException.check(
            branch == null,
            "Cannot write to both branch and WAP branch, but got branch [%s] and WAP branch [%s]",
            branch,
            wapBranch);

        return wapBranch;
      }
    }

    return branch;
  }
  /** 执行 writeProperties 相关操作。 */
  public Map<String, String> writeProperties() {
    Map<String, String> writeProperties = Maps.newHashMap();
    writeProperties.putAll(dataWriteProperties());
    writeProperties.putAll(deleteWriteProperties());
    return writeProperties;
  }
  /** 执行 dataWriteProperties 相关操作。 */
  private Map<String, String> dataWriteProperties() {
    Map<String, String> writeProperties = Maps.newHashMap();
    FileFormat dataFormat = dataFileFormat();

    switch (dataFormat) {
      case PARQUET:
        writeProperties.put(PARQUET_COMPRESSION, parquetCompressionCodec());
        String parquetCompressionLevel = parquetCompressionLevel();
        if (parquetCompressionLevel != null) {
          writeProperties.put(PARQUET_COMPRESSION_LEVEL, parquetCompressionLevel);
        }
        break;

      case AVRO:
        writeProperties.put(AVRO_COMPRESSION, avroCompressionCodec());
        String avroCompressionLevel = avroCompressionLevel();
        if (avroCompressionLevel != null) {
          writeProperties.put(AVRO_COMPRESSION_LEVEL, avroCompressionLevel);
        }
        break;

      case ORC:
        writeProperties.put(ORC_COMPRESSION, orcCompressionCodec());
        writeProperties.put(ORC_COMPRESSION_STRATEGY, orcCompressionStrategy());
        break;

      default:
        // skip
    }

    return writeProperties;
  }
  /** 执行 deleteWriteProperties 相关操作。 */
  private Map<String, String> deleteWriteProperties() {
    Map<String, String> writeProperties = Maps.newHashMap();
    FileFormat deleteFormat = deleteFileFormat();

    switch (deleteFormat) {
      case PARQUET:
        setWritePropertyWithFallback(
            writeProperties,
            DELETE_PARQUET_COMPRESSION,
            deleteParquetCompressionCodec(),
            parquetCompressionCodec());
        setWritePropertyWithFallback(
            writeProperties,
            DELETE_PARQUET_COMPRESSION_LEVEL,
            deleteParquetCompressionLevel(),
            parquetCompressionLevel());
        break;

      case AVRO:
        setWritePropertyWithFallback(
            writeProperties,
            DELETE_AVRO_COMPRESSION,
            deleteAvroCompressionCodec(),
            avroCompressionCodec());
        setWritePropertyWithFallback(
            writeProperties,
            DELETE_AVRO_COMPRESSION_LEVEL,
            deleteAvroCompressionLevel(),
            avroCompressionLevel());
        break;

      case ORC:
        setWritePropertyWithFallback(
            writeProperties,
            DELETE_ORC_COMPRESSION,
            deleteOrcCompressionCodec(),
            orcCompressionCodec());
        setWritePropertyWithFallback(
            writeProperties,
            DELETE_ORC_COMPRESSION_STRATEGY,
            deleteOrcCompressionStrategy(),
            orcCompressionStrategy());
        break;

      default:
        // skip
    }

    return writeProperties;
  }
  /** 设置 WritePropertyWithFallback 属性。 */
  private void setWritePropertyWithFallback(
      Map<String, String> writeProperties, String key, String value, String fallbackValue) {
    if (value != null) {
      writeProperties.put(key, value);
    } else if (fallbackValue != null) {
      writeProperties.put(key, fallbackValue);
    }
  }
  /** 执行 parquetCompressionCodec 相关操作。 */
  private String parquetCompressionCodec() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_CODEC)
        .sessionConf(SparkSQLProperties.COMPRESSION_CODEC)
        .tableProperty(TableProperties.PARQUET_COMPRESSION)
        .defaultValue(TableProperties.PARQUET_COMPRESSION_DEFAULT)
        .parse();
  }
  /** 执行 deleteParquetCompressionCodec 相关操作。 */
  private String deleteParquetCompressionCodec() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_CODEC)
        .sessionConf(SparkSQLProperties.COMPRESSION_CODEC)
        .tableProperty(DELETE_PARQUET_COMPRESSION)
        .parseOptional();
  }
  /** 执行 parquetCompressionLevel 相关操作。 */
  private String parquetCompressionLevel() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_LEVEL)
        .sessionConf(SparkSQLProperties.COMPRESSION_LEVEL)
        .tableProperty(TableProperties.PARQUET_COMPRESSION_LEVEL)
        .defaultValue(TableProperties.PARQUET_COMPRESSION_LEVEL_DEFAULT)
        .parseOptional();
  }
  /** 执行 deleteParquetCompressionLevel 相关操作。 */
  private String deleteParquetCompressionLevel() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_LEVEL)
        .sessionConf(SparkSQLProperties.COMPRESSION_LEVEL)
        .tableProperty(DELETE_PARQUET_COMPRESSION_LEVEL)
        .parseOptional();
  }
  /** 执行 avroCompressionCodec 相关操作。 */
  private String avroCompressionCodec() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_CODEC)
        .sessionConf(SparkSQLProperties.COMPRESSION_CODEC)
        .tableProperty(TableProperties.AVRO_COMPRESSION)
        .defaultValue(TableProperties.AVRO_COMPRESSION_DEFAULT)
        .parse();
  }
  /** 执行 deleteAvroCompressionCodec 相关操作。 */
  private String deleteAvroCompressionCodec() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_CODEC)
        .sessionConf(SparkSQLProperties.COMPRESSION_CODEC)
        .tableProperty(DELETE_AVRO_COMPRESSION)
        .parseOptional();
  }
  /** 执行 avroCompressionLevel 相关操作。 */
  private String avroCompressionLevel() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_LEVEL)
        .sessionConf(SparkSQLProperties.COMPRESSION_LEVEL)
        .tableProperty(TableProperties.AVRO_COMPRESSION_LEVEL)
        .defaultValue(TableProperties.AVRO_COMPRESSION_LEVEL_DEFAULT)
        .parseOptional();
  }
  /** 执行 deleteAvroCompressionLevel 相关操作。 */
  private String deleteAvroCompressionLevel() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_LEVEL)
        .sessionConf(SparkSQLProperties.COMPRESSION_LEVEL)
        .tableProperty(DELETE_AVRO_COMPRESSION_LEVEL)
        .parseOptional();
  }
  /** 执行 orcCompressionCodec 相关操作。 */
  private String orcCompressionCodec() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_CODEC)
        .sessionConf(SparkSQLProperties.COMPRESSION_CODEC)
        .tableProperty(TableProperties.ORC_COMPRESSION)
        .defaultValue(TableProperties.ORC_COMPRESSION_DEFAULT)
        .parse();
  }
  /** 执行 deleteOrcCompressionCodec 相关操作。 */
  private String deleteOrcCompressionCodec() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_CODEC)
        .sessionConf(SparkSQLProperties.COMPRESSION_CODEC)
        .tableProperty(DELETE_ORC_COMPRESSION)
        .parseOptional();
  }
  /** 执行 orcCompressionStrategy 相关操作。 */
  private String orcCompressionStrategy() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_STRATEGY)
        .sessionConf(SparkSQLProperties.COMPRESSION_STRATEGY)
        .tableProperty(TableProperties.ORC_COMPRESSION_STRATEGY)
        .defaultValue(TableProperties.ORC_COMPRESSION_STRATEGY_DEFAULT)
        .parse();
  }
  /** 执行 deleteOrcCompressionStrategy 相关操作。 */
  private String deleteOrcCompressionStrategy() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.COMPRESSION_STRATEGY)
        .sessionConf(SparkSQLProperties.COMPRESSION_STRATEGY)
        .tableProperty(DELETE_ORC_COMPRESSION_STRATEGY)
        .parseOptional();
  }
}
