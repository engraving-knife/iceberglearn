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
package org.apache.iceberg.flink;

import java.time.Duration;
import java.util.Map;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.util.TimeUtils;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.flink.source.StreamingStartingStrategy;

/**
 * Iceberg Flink source 的读取配置类，按优先级解析各项读取参数。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：基于 {@link FlinkConfParser} 从 SQL 选项、 Flink 全局配置和表属性中读取 source
 * 相关配置（快照、流式策略、split 大小、监控间隔等）。
 *
 * <p>设计意图：配置类，方法自描述单个配置项；优先级由 {@link FlinkConfParser} 决定。 上下游：由 {@link
 * FlinkDynamicTableFactory}、source builder 创建；向下使用 FlinkConfParser。
 */
public class FlinkReadConf {

  private final FlinkConfParser confParser;

  /** 构造读取配置，绑定 Iceberg 表、用户读取选项与 Flink 可读配置。 */
  public FlinkReadConf(
      Table table, Map<String, String> readOptions, ReadableConfig readableConfig) {
    this.confParser = new FlinkConfParser(table, readOptions, readableConfig);
  }

  /** 读取指定扫描的快照 id（可选）。 */
  public Long snapshotId() {
    return confParser.longConf().option(FlinkReadOptions.SNAPSHOT_ID.key()).parseOptional();
  }

  /** 读取扫描的 tag 名称（可选）。 */
  public String tag() {
    return confParser.stringConf().option(FlinkReadOptions.TAG.key()).parseOptional();
  }

  /** 读取流式起始 tag（可选）。 */
  public String startTag() {
    return confParser.stringConf().option(FlinkReadOptions.START_TAG.key()).parseOptional();
  }

  /** 读取流式结束 tag（可选）。 */
  public String endTag() {
    return confParser.stringConf().option(FlinkReadOptions.END_TAG.key()).parseOptional();
  }

  /** 读取扫描的 branch（可选）。 */
  public String branch() {
    return confParser.stringConf().option(FlinkReadOptions.BRANCH.key()).parseOptional();
  }

  /** 是否大小写敏感过滤。 */
  public boolean caseSensitive() {
    return confParser
        .booleanConf()
        .option(FlinkReadOptions.CASE_SENSITIVE)
        .flinkConfig(FlinkReadOptions.CASE_SENSITIVE_OPTION)
        .defaultValue(FlinkReadOptions.CASE_SENSITIVE_OPTION.defaultValue())
        .parse();
  }

  /** 读取按时间戳扫描的快照时间戳（可选）。 */
  public Long asOfTimestamp() {
    return confParser.longConf().option(FlinkReadOptions.AS_OF_TIMESTAMP.key()).parseOptional();
  }

  /** 读取流式启动策略，默认从最新快照增量消费。 */
  public StreamingStartingStrategy startingStrategy() {
    return confParser
        .enumConfParser(StreamingStartingStrategy.class)
        .option(FlinkReadOptions.STARTING_STRATEGY)
        .flinkConfig(FlinkReadOptions.STARTING_STRATEGY_OPTION)
        .defaultValue(StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT)
        .parse();
  }

  /** 读取流式启动时按时间戳定位的起始快照（可选）。 */
  public Long startSnapshotTimestamp() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.START_SNAPSHOT_TIMESTAMP.key())
        .parseOptional();
  }

  /** 读取流式起始快照 id（可选）。 */
  public Long startSnapshotId() {
    return confParser.longConf().option(FlinkReadOptions.START_SNAPSHOT_ID.key()).parseOptional();
  }

  /** 读取流式结束快照 id（可选）。 */
  public Long endSnapshotId() {
    return confParser.longConf().option(FlinkReadOptions.END_SNAPSHOT_ID.key()).parseOptional();
  }

  /** 读取单个 split 的目标数据大小。 */
  public long splitSize() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.SPLIT_SIZE)
        .flinkConfig(FlinkReadOptions.SPLIT_SIZE_OPTION)
        .tableProperty(TableProperties.SPLIT_SIZE)
        .defaultValue(TableProperties.SPLIT_SIZE_DEFAULT)
        .parse();
  }

  /** 读取 split 计算时回溯读取的文件数。 */
  public int splitLookback() {
    return confParser
        .intConf()
        .option(FlinkReadOptions.SPLIT_LOOKBACK)
        .flinkConfig(FlinkReadOptions.SPLIT_LOOKBACK_OPTION)
        .tableProperty(TableProperties.SPLIT_LOOKBACK)
        .defaultValue(TableProperties.SPLIT_LOOKBACK_DEFAULT)
        .parse();
  }

  /** 读取打开文件的预估开销，用于 split 大小估算。 */
  public long splitFileOpenCost() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.SPLIT_FILE_OPEN_COST)
        .flinkConfig(FlinkReadOptions.SPLIT_FILE_OPEN_COST_OPTION)
        .tableProperty(TableProperties.SPLIT_OPEN_FILE_COST)
        .defaultValue(TableProperties.SPLIT_OPEN_FILE_COST_DEFAULT)
        .parse();
  }

  /** 是否流式读取。 */
  public boolean streaming() {
    return confParser
        .booleanConf()
        .option(FlinkReadOptions.STREAMING)
        .flinkConfig(FlinkReadOptions.STREAMING_OPTION)
        .defaultValue(FlinkReadOptions.STREAMING_OPTION.defaultValue())
        .parse();
  }

  /** 读取流式监控新快照的间隔。 */
  public Duration monitorInterval() {
    String duration =
        confParser
            .stringConf()
            .option(FlinkReadOptions.MONITOR_INTERVAL)
            .flinkConfig(FlinkReadOptions.MONITOR_INTERVAL_OPTION)
            .defaultValue(FlinkReadOptions.MONITOR_INTERVAL_OPTION.defaultValue())
            .parse();

    return TimeUtils.parseDuration(duration);
  }

  /** 是否包含列统计信息。 */
  public boolean includeColumnStats() {
    return confParser
        .booleanConf()
        .option(FlinkReadOptions.INCLUDE_COLUMN_STATS)
        .flinkConfig(FlinkReadOptions.INCLUDE_COLUMN_STATS_OPTION)
        .defaultValue(FlinkReadOptions.INCLUDE_COLUMN_STATS_OPTION.defaultValue())
        .parse();
  }

  /** 读取单次规划最多扫描的快照数。 */
  public int maxPlanningSnapshotCount() {
    return confParser
        .intConf()
        .option(FlinkReadOptions.MAX_PLANNING_SNAPSHOT_COUNT)
        .flinkConfig(FlinkReadOptions.MAX_PLANNING_SNAPSHOT_COUNT_OPTION)
        .defaultValue(FlinkReadOptions.MAX_PLANNING_SNAPSHOT_COUNT_OPTION.defaultValue())
        .parse();
  }

  /** 读取表属性中的默认 name mapping（可选）。 */
  public String nameMapping() {
    return confParser.stringConf().option(TableProperties.DEFAULT_NAME_MAPPING).parseOptional();
  }

  /** 读取输出记录数限制，-1 表示不限制。 */
  public long limit() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.LIMIT)
        .flinkConfig(FlinkReadOptions.LIMIT_OPTION)
        .defaultValue(FlinkReadOptions.LIMIT_OPTION.defaultValue())
        .parse();
  }

  /** 读取用于 manifest 规划/扫描的 worker 线程池大小。 */
  public int workerPoolSize() {
    return confParser
        .intConf()
        .flinkConfig(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE)
        .defaultValue(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.defaultValue())
        .parse();
  }

  /** 读取最大允许的连续规划失败次数。 */
  public int maxAllowedPlanningFailures() {
    return confParser
        .intConf()
        .option(FlinkReadOptions.MAX_ALLOWED_PLANNING_FAILURES)
        .flinkConfig(FlinkReadOptions.MAX_ALLOWED_PLANNING_FAILURES_OPTION)
        .defaultValue(FlinkReadOptions.MAX_ALLOWED_PLANNING_FAILURES_OPTION.defaultValue())
        .parse();
  }
}
