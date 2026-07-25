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
 * Iceberg Flink 读取侧的配置视图。
 *
 * <p>所属模块：iceberg-flink，封装 source 读取相关的全部可配置项的解析结果。
 *
 * <p>职责：通过 {@link FlinkConfParser} 按"读取选项 > Flink 配置 > 表属性 > 默认值"的优先级解析 快照、tag、branch、split
 * 大小、流式策略、监控间隔、限制条数等读取参数。
 *
 * <p>设计意图：将配置解析集中收敛，对外暴露类型安全的 getter，避免各使用点重复处理优先级与类型转换； 大多数字段为可选（parseOptional），缺失时返回 null
 * 由上层决定默认行为。
 *
 * <p>上下游关系：由 Flink source 构建/枚举器/读取器读取；上游依赖 {@link FlinkReadOptions}、 {@link FlinkConfigOptions} 与
 * {@link TableProperties}。
 */
public class FlinkReadConf {

  private final FlinkConfParser confParser;

  /**
   * 构造读取配置视图。
   *
   * @param table Iceberg 表
   * @param readOptions 读取选项（最高优先级）
   * @param readableConfig Flink 配置
   */
  public FlinkReadConf(
      Table table, Map<String, String> readOptions, ReadableConfig readableConfig) {
    this.confParser = new FlinkConfParser(table, readOptions, readableConfig);
  }

  /** 读取的目标快照 id（可选）。 */
  public Long snapshotId() {
    return confParser.longConf().option(FlinkReadOptions.SNAPSHOT_ID.key()).parseOptional();
  }

  /** 读取的目标 tag 名（可选）。 */
  public String tag() {
    return confParser.stringConf().option(FlinkReadOptions.TAG.key()).parseOptional();
  }

  /** 流式读取起始 tag（可选）。 */
  public String startTag() {
    return confParser.stringConf().option(FlinkReadOptions.START_TAG.key()).parseOptional();
  }

  /** 流式读取结束 tag（可选）。 */
  public String endTag() {
    return confParser.stringConf().option(FlinkReadOptions.END_TAG.key()).parseOptional();
  }

  /** 读取的目标 branch 名（可选）。 */
  public String branch() {
    return confParser.stringConf().option(FlinkReadOptions.BRANCH.key()).parseOptional();
  }

  /** 表达式求值是否大小写敏感。 */
  public boolean caseSensitive() {
    return confParser
        .booleanConf()
        .option(FlinkReadOptions.CASE_SENSITIVE)
        .flinkConfig(FlinkReadOptions.CASE_SENSITIVE_OPTION)
        .defaultValue(FlinkReadOptions.CASE_SENSITIVE_OPTION.defaultValue())
        .parse();
  }

  /** 按时间戳读取对应快照（可选）。 */
  public Long asOfTimestamp() {
    return confParser.longConf().option(FlinkReadOptions.AS_OF_TIMESTAMP.key()).parseOptional();
  }

  /** 流式读取的起始策略。 */
  public StreamingStartingStrategy startingStrategy() {
    return confParser
        .enumConfParser(StreamingStartingStrategy.class)
        .option(FlinkReadOptions.STARTING_STRATEGY)
        .flinkConfig(FlinkReadOptions.STARTING_STRATEGY_OPTION)
        .defaultValue(StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT)
        .parse();
  }

  /** 流式起始快照时间戳（可选）。 */
  public Long startSnapshotTimestamp() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.START_SNAPSHOT_TIMESTAMP.key())
        .parseOptional();
  }

  /** 流式起始快照 id（可选）。 */
  public Long startSnapshotId() {
    return confParser.longConf().option(FlinkReadOptions.START_SNAPSHOT_ID.key()).parseOptional();
  }

  /** 流式结束快照 id（可选）。 */
  public Long endSnapshotId() {
    return confParser.longConf().option(FlinkReadOptions.END_SNAPSHOT_ID.key()).parseOptional();
  }

  /** 单个 split 的目标数据大小（字节）。 */
  public long splitSize() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.SPLIT_SIZE)
        .flinkConfig(FlinkReadOptions.SPLIT_SIZE_OPTION)
        .tableProperty(TableProperties.SPLIT_SIZE)
        .defaultValue(TableProperties.SPLIT_SIZE_DEFAULT)
        .parse();
  }

  /** 合并小文件为 split 时的回看文件数。 */
  public int splitLookback() {
    return confParser
        .intConf()
        .option(FlinkReadOptions.SPLIT_LOOKBACK)
        .flinkConfig(FlinkReadOptions.SPLIT_LOOKBACK_OPTION)
        .tableProperty(TableProperties.SPLIT_LOOKBACK)
        .defaultValue(TableProperties.SPLIT_LOOKBACK_DEFAULT)
        .parse();
  }

  /** 单个文件打开成本（字节），用于 split 大小估算。 */
  public long splitFileOpenCost() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.SPLIT_FILE_OPEN_COST)
        .flinkConfig(FlinkReadOptions.SPLIT_FILE_OPEN_COST_OPTION)
        .tableProperty(TableProperties.SPLIT_OPEN_FILE_COST)
        .defaultValue(TableProperties.SPLIT_OPEN_FILE_COST_DEFAULT)
        .parse();
  }

  /** 是否为流式读取模式。 */
  public boolean streaming() {
    return confParser
        .booleanConf()
        .option(FlinkReadOptions.STREAMING)
        .flinkConfig(FlinkReadOptions.STREAMING_OPTION)
        .defaultValue(FlinkReadOptions.STREAMING_OPTION.defaultValue())
        .parse();
  }

  /** 流式模式下监控新快照的间隔。 */
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

  /** 读取时是否包含列统计信息。 */
  public boolean includeColumnStats() {
    return confParser
        .booleanConf()
        .option(FlinkReadOptions.INCLUDE_COLUMN_STATS)
        .flinkConfig(FlinkReadOptions.INCLUDE_COLUMN_STATS_OPTION)
        .defaultValue(FlinkReadOptions.INCLUDE_COLUMN_STATS_OPTION.defaultValue())
        .parse();
  }

  /** 单次规划最多处理的快照数。 */
  public int maxPlanningSnapshotCount() {
    return confParser
        .intConf()
        .option(FlinkReadOptions.MAX_PLANNING_SNAPSHOT_COUNT)
        .flinkConfig(FlinkReadOptions.MAX_PLANNING_SNAPSHOT_COUNT_OPTION)
        .defaultValue(FlinkReadOptions.MAX_PLANNING_SNAPSHOT_COUNT_OPTION.defaultValue())
        .parse();
  }

  /** 表的默认字段名映射（可选，用于 schema 演进兼容）。 */
  public String nameMapping() {
    return confParser.stringConf().option(TableProperties.DEFAULT_NAME_MAPPING).parseOptional();
  }

  /** 读取记录数上限。 */
  public long limit() {
    return confParser
        .longConf()
        .option(FlinkReadOptions.LIMIT)
        .flinkConfig(FlinkReadOptions.LIMIT_OPTION)
        .defaultValue(FlinkReadOptions.LIMIT_OPTION.defaultValue())
        .parse();
  }

  /** manifest 规划/扫描用工作线程池大小。 */
  public int workerPoolSize() {
    return confParser
        .intConf()
        .flinkConfig(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE)
        .defaultValue(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.defaultValue())
        .parse();
  }

  /** 规划阶段允许的最大失败次数。 */
  public int maxAllowedPlanningFailures() {
    return confParser
        .intConf()
        .option(FlinkReadOptions.MAX_ALLOWED_PLANNING_FAILURES)
        .flinkConfig(FlinkReadOptions.MAX_ALLOWED_PLANNING_FAILURES_OPTION)
        .defaultValue(FlinkReadOptions.MAX_ALLOWED_PLANNING_FAILURES_OPTION.defaultValue())
        .parse();
  }
}
