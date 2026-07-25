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

import java.util.Map;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.spark.sql.SparkSession;

/**
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkReadConf。
 */
public class SparkReadConf {

  private final SparkSession spark;
  private final Table table;
  private final Map<String, String> readOptions;
  private final SparkConfParser confParser;

  /** 构造 SparkReadConf 实例。 */
  public SparkReadConf(SparkSession spark, Table table, Map<String, String> readOptions) {
    this.spark = spark;
    this.table = table;
    this.readOptions = readOptions;
    this.confParser = new SparkConfParser(spark, table, readOptions);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean caseSensitive() {
    return SparkUtil.caseSensitive(spark);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean localityEnabled() {
    boolean defaultValue = Util.mayHaveBlockLocations(table.io(), table.location());
    return PropertyUtil.propertyAsBoolean(readOptions, SparkReadOptions.LOCALITY, defaultValue);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long snapshotId() {
    return confParser.longConf().option(SparkReadOptions.SNAPSHOT_ID).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long asOfTimestamp() {
    return confParser.longConf().option(SparkReadOptions.AS_OF_TIMESTAMP).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long startSnapshotId() {
    return confParser.longConf().option(SparkReadOptions.START_SNAPSHOT_ID).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long endSnapshotId() {
    return confParser.longConf().option(SparkReadOptions.END_SNAPSHOT_ID).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public String fileScanTaskSetId() {
    return confParser.stringConf().option(SparkReadOptions.FILE_SCAN_TASK_SET_ID).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean streamingSkipDeleteSnapshots() {
    return confParser
        .booleanConf()
        .option(SparkReadOptions.STREAMING_SKIP_DELETE_SNAPSHOTS)
        .defaultValue(SparkReadOptions.STREAMING_SKIP_DELETE_SNAPSHOTS_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean streamingSkipOverwriteSnapshots() {
    return confParser
        .booleanConf()
        .option(SparkReadOptions.STREAMING_SKIP_OVERWRITE_SNAPSHOTS)
        .defaultValue(SparkReadOptions.STREAMING_SKIP_OVERWRITE_SNAPSHOTS_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean parquetVectorizationEnabled() {
    return confParser
        .booleanConf()
        .option(SparkReadOptions.VECTORIZATION_ENABLED)
        .sessionConf(SparkSQLProperties.VECTORIZATION_ENABLED)
        .tableProperty(TableProperties.PARQUET_VECTORIZATION_ENABLED)
        .defaultValue(TableProperties.PARQUET_VECTORIZATION_ENABLED_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public int parquetBatchSize() {
    return confParser
        .intConf()
        .option(SparkReadOptions.VECTORIZATION_BATCH_SIZE)
        .tableProperty(TableProperties.PARQUET_BATCH_SIZE)
        .defaultValue(TableProperties.PARQUET_BATCH_SIZE_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean orcVectorizationEnabled() {
    return confParser
        .booleanConf()
        .option(SparkReadOptions.VECTORIZATION_ENABLED)
        .sessionConf(SparkSQLProperties.VECTORIZATION_ENABLED)
        .tableProperty(TableProperties.ORC_VECTORIZATION_ENABLED)
        .defaultValue(TableProperties.ORC_VECTORIZATION_ENABLED_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public int orcBatchSize() {
    return confParser
        .intConf()
        .option(SparkReadOptions.VECTORIZATION_BATCH_SIZE)
        .tableProperty(TableProperties.ORC_BATCH_SIZE)
        .defaultValue(TableProperties.ORC_BATCH_SIZE_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long splitSizeOption() {
    return confParser.longConf().option(SparkReadOptions.SPLIT_SIZE).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public long splitSize() {
    return confParser
        .longConf()
        .option(SparkReadOptions.SPLIT_SIZE)
        .tableProperty(TableProperties.SPLIT_SIZE)
        .defaultValue(TableProperties.SPLIT_SIZE_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Integer splitLookbackOption() {
    return confParser.intConf().option(SparkReadOptions.LOOKBACK).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public int splitLookback() {
    return confParser
        .intConf()
        .option(SparkReadOptions.LOOKBACK)
        .tableProperty(TableProperties.SPLIT_LOOKBACK)
        .defaultValue(TableProperties.SPLIT_LOOKBACK_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long splitOpenFileCostOption() {
    return confParser.longConf().option(SparkReadOptions.FILE_OPEN_COST).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public long splitOpenFileCost() {
    return confParser
        .longConf()
        .option(SparkReadOptions.FILE_OPEN_COST)
        .tableProperty(TableProperties.SPLIT_OPEN_FILE_COST)
        .defaultValue(TableProperties.SPLIT_OPEN_FILE_COST_DEFAULT)
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
        .option(SparkReadOptions.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE)
        .sessionConf(SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE)
        .defaultValue(SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE_DEFAULT)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public long streamFromTimestamp() {
    return confParser
        .longConf()
        .option(SparkReadOptions.STREAM_FROM_TIMESTAMP)
        .defaultValue(Long.MIN_VALUE)
        .parse();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long startTimestamp() {
    return confParser.longConf().option(SparkReadOptions.START_TIMESTAMP).parseOptional();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Long endTimestamp() {
    return confParser.longConf().option(SparkReadOptions.END_TIMESTAMP).parseOptional();
  }
}
