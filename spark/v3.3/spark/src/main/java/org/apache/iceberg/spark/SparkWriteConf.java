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

import java.util.Locale;
import java.util.Map;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.IsolationLevel;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.spark.sql.RuntimeConfig;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;

/**
 * Iceberg Spark 集成相关组件的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkWriteConf。
 */
public class SparkWriteConf {

  private final Table table;
  private final String branch;
  private final RuntimeConfig sessionConf;
  private final Map<String, String> writeOptions;
  private final SparkConfParser confParser;

  /** 构造 SparkWriteConf 实例。 */
  public SparkWriteConf(SparkSession spark, Table table, Map<String, String> writeOptions) {
    this(spark, table, null, writeOptions);
  }

  /** 构造 SparkWriteConf 实例。 */
  public SparkWriteConf(
      SparkSession spark, Table table, String branch, Map<String, String> writeOptions) {
    this.table = table;
    this.branch = branch;
    this.sessionConf = spark.conf();
    this.writeOptions = writeOptions;
    this.confParser = new SparkConfParser(spark, table, writeOptions);
  }

  /**
   * 校验前置条件或参数。
   *
   * @return 结果对象
   */
  public boolean checkNullability() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.CHECK_NULLABILITY)
        .sessionConf(SparkSQLProperties.CHECK_NULLABILITY)
        .defaultValue(SparkSQLProperties.CHECK_NULLABILITY_DEFAULT)
        .parse();
  }

  /**
   * 校验前置条件或参数。
   *
   * @return 结果对象
   */
  public boolean checkOrdering() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.CHECK_ORDERING)
        .sessionConf(SparkSQLProperties.CHECK_ORDERING)
        .defaultValue(SparkSQLProperties.CHECK_ORDERING_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean handleTimestampWithoutZone() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE)
        .sessionConf(SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE)
        .defaultValue(SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public String overwriteMode() {
    String overwriteMode = writeOptions.get(SparkWriteOptions.OVERWRITE_MODE);
    return overwriteMode != null ? overwriteMode.toLowerCase(Locale.ROOT) : null;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean wapEnabled() {
    return confParser
        .booleanConf()
        .tableProperty(TableProperties.WRITE_AUDIT_PUBLISH_ENABLED)
        .defaultValue(TableProperties.WRITE_AUDIT_PUBLISH_ENABLED_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public String wapId() {
    return sessionConf.get(SparkSQLProperties.WAP_ID, null);
  }

  /**
   * 合并数据。
   *
   * @return 结果对象
   */
  public boolean mergeSchema() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.MERGE_SCHEMA)
        .option(SparkWriteOptions.SPARK_MERGE_SCHEMA)
        .defaultValue(SparkWriteOptions.MERGE_SCHEMA_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public long targetDataFileSize() {
    return confParser
        .longConf()
        .option(SparkWriteOptions.TARGET_FILE_SIZE_BYTES)
        .tableProperty(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES)
        .defaultValue(TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean fanoutWriterEnabled() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.FANOUT_ENABLED)
        .tableProperty(TableProperties.SPARK_WRITE_PARTITIONED_FANOUT_ENABLED)
        .defaultValue(TableProperties.SPARK_WRITE_PARTITIONED_FANOUT_ENABLED_DEFAULT)
        .parse();
  }

  /**
   * 删除数据或文件。
   *
   * @return 结果对象
   */
  public FileFormat deleteFileFormat() {
    String valueAsString =
        confParser
            .stringConf()
            .option(SparkWriteOptions.DELETE_FORMAT)
            .tableProperty(TableProperties.DELETE_DEFAULT_FILE_FORMAT)
            .parseOptional();
    return valueAsString != null ? FileFormat.fromString(valueAsString) : dataFileFormat();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public long targetDeleteFileSize() {
    return confParser
        .longConf()
        .option(SparkWriteOptions.TARGET_DELETE_FILE_SIZE_BYTES)
        .tableProperty(TableProperties.DELETE_TARGET_FILE_SIZE_BYTES)
        .defaultValue(TableProperties.DELETE_TARGET_FILE_SIZE_BYTES_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public String rewrittenFileSetId() {
    return confParser
        .stringConf()
        .option(SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID)
        .parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public DistributionMode distributionMode() {
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

  /** 执行该方法的具体逻辑。 */
  private DistributionMode adjustWriteDistributionMode(DistributionMode mode) {
    if (mode == RANGE && table.spec().isUnpartitioned() && table.sortOrder().isUnsorted()) {
      return NONE;
    } else if (mode == HASH && table.spec().isUnpartitioned()) {
      return NONE;
    } else {
      return mode;
    }
  }

  /** 执行该方法的具体逻辑。 */
  private DistributionMode defaultWriteDistributionMode() {
    if (table.sortOrder().isSorted()) {
      return RANGE;
    } else if (table.spec().isPartitioned()) {
      return HASH;
    } else {
      return NONE;
    }
  }

  /**
   * 删除数据或文件。
   *
   * @return 结果对象
   */
  public DistributionMode deleteDistributionMode() {
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

  /**
   * 更新数据或状态。
   *
   * @return 结果对象
   */
  public DistributionMode updateDistributionMode() {
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public DistributionMode copyOnWriteMergeDistributionMode() {
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public DistributionMode positionDeltaMergeDistributionMode() {
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean useTableDistributionAndOrdering() {
    return confParser
        .booleanConf()
        .option(SparkWriteOptions.USE_TABLE_DISTRIBUTION_AND_ORDERING)
        .defaultValue(SparkWriteOptions.USE_TABLE_DISTRIBUTION_AND_ORDERING_DEFAULT)
        .parse();
  }

  /**
   * 校验前置条件或参数。
   *
   * @return 结果对象
   */
  public Long validateFromSnapshotId() {
    return confParser
        .longConf()
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID)
        .parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 布尔结果
   */
  public IsolationLevel isolationLevel() {
    String isolationLevelName =
        confParser.stringConf().option(SparkWriteOptions.ISOLATION_LEVEL).parseOptional();
    return isolationLevelName != null ? IsolationLevel.fromName(isolationLevelName) : null;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean caseSensitive() {
    return confParser
        .booleanConf()
        .sessionConf(SQLConf.CASE_SENSITIVE().key())
        .defaultValue(SQLConf.CASE_SENSITIVE().defaultValueString())
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
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
}
