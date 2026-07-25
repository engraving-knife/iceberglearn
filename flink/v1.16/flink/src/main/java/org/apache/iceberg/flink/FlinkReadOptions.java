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

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.flink.source.StreamingStartingStrategy;

/**
 * Flink Iceberg source 的读取选项（SQL hint 与 connector 选项）。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：声明 Iceberg source 可用的读取选项， 包括快照、tag/branch、流式策略、split 大小、监控间隔等。
 *
 * <p>设计意图：以 {@link ConfigOption} 形式声明，便于在 SQL hints 与 connector 选项中使用。
 */
public class FlinkReadOptions {
  private static final String PREFIX = "connector.iceberg.";

  private FlinkReadOptions() {}

  /** 指定扫描的快照 id。 */
  public static final ConfigOption<Long> SNAPSHOT_ID =
      ConfigOptions.key("snapshot-id").longType().defaultValue(null);

  /** 指定扫描的 tag 名称。 */
  public static final ConfigOption<String> TAG =
      ConfigOptions.key("tag").stringType().defaultValue(null);

  /** 指定扫描的 branch 名称。 */
  public static final ConfigOption<String> BRANCH =
      ConfigOptions.key("branch").stringType().defaultValue(null);

  /** 流式读取的起始 tag。 */
  public static final ConfigOption<String> START_TAG =
      ConfigOptions.key("start-tag").stringType().defaultValue(null);

  /** 流式读取的结束 tag。 */
  public static final ConfigOption<String> END_TAG =
      ConfigOptions.key("end-tag").stringType().defaultValue(null);

  /** 是否大小写敏感过滤的 key。 */
  public static final String CASE_SENSITIVE = "case-sensitive";
  /** 大小写敏感选项，默认 false。 */
  public static final ConfigOption<Boolean> CASE_SENSITIVE_OPTION =
      ConfigOptions.key(PREFIX + CASE_SENSITIVE).booleanType().defaultValue(false);

  /** 按时间戳扫描的快照时间戳。 */
  public static final ConfigOption<Long> AS_OF_TIMESTAMP =
      ConfigOptions.key("as-of-timestamp").longType().defaultValue(null);

  /** 流式启动策略的 key。 */
  public static final String STARTING_STRATEGY = "starting-strategy";
  /** 流式启动策略选项，默认从最新快照增量消费。 */
  public static final ConfigOption<StreamingStartingStrategy> STARTING_STRATEGY_OPTION =
      ConfigOptions.key(PREFIX + STARTING_STRATEGY)
          .enumType(StreamingStartingStrategy.class)
          .defaultValue(StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT);

  /** 流式启动时按时间戳定位的起始快照。 */
  public static final ConfigOption<Long> START_SNAPSHOT_TIMESTAMP =
      ConfigOptions.key("start-snapshot-timestamp").longType().defaultValue(null);

  /** 流式读取的起始快照 id。 */
  public static final ConfigOption<Long> START_SNAPSHOT_ID =
      ConfigOptions.key("start-snapshot-id").longType().defaultValue(null);

  /** 流式读取的结束快照 id。 */
  public static final ConfigOption<Long> END_SNAPSHOT_ID =
      ConfigOptions.key("end-snapshot-id").longType().defaultValue(null);

  /** 单个 split 目标数据大小的 key。 */
  public static final String SPLIT_SIZE = "split-size";
  /** 单个 split 目标数据大小。 */
  public static final ConfigOption<Long> SPLIT_SIZE_OPTION =
      ConfigOptions.key(PREFIX + SPLIT_SIZE)
          .longType()
          .defaultValue(TableProperties.SPLIT_SIZE_DEFAULT);

  /** split 计算时回溯读取的文件数的 key。 */
  public static final String SPLIT_LOOKBACK = "split-lookback";
  /** split 计算时回溯读取的文件数。 */
  public static final ConfigOption<Integer> SPLIT_LOOKBACK_OPTION =
      ConfigOptions.key(PREFIX + SPLIT_LOOKBACK)
          .intType()
          .defaultValue(TableProperties.SPLIT_LOOKBACK_DEFAULT);

  /** 打开文件预估开销的 key。 */
  public static final String SPLIT_FILE_OPEN_COST = "split-file-open-cost";
  /** 打开文件的预估开销，用于 split 大小估算。 */
  public static final ConfigOption<Long> SPLIT_FILE_OPEN_COST_OPTION =
      ConfigOptions.key(PREFIX + SPLIT_FILE_OPEN_COST)
          .longType()
          .defaultValue(TableProperties.SPLIT_OPEN_FILE_COST_DEFAULT);

  /** 是否流式读取的 key。 */
  public static final String STREAMING = "streaming";
  /** 是否流式读取，默认 false。 */
  public static final ConfigOption<Boolean> STREAMING_OPTION =
      ConfigOptions.key(PREFIX + STREAMING).booleanType().defaultValue(false);

  /** 监控新快照的间隔的 key。 */
  public static final String MONITOR_INTERVAL = "monitor-interval";
  /** 流式监控新快照的间隔，默认 60s。 */
  public static final ConfigOption<String> MONITOR_INTERVAL_OPTION =
      ConfigOptions.key(PREFIX + MONITOR_INTERVAL).stringType().defaultValue("60s");

  /** 是否包含列统计的 key。 */
  public static final String INCLUDE_COLUMN_STATS = "include-column-stats";
  /** 是否包含列统计信息，默认 false。 */
  public static final ConfigOption<Boolean> INCLUDE_COLUMN_STATS_OPTION =
      ConfigOptions.key(PREFIX + INCLUDE_COLUMN_STATS).booleanType().defaultValue(false);

  /** 单次规划最多扫描的快照数的 key。 */
  public static final String MAX_PLANNING_SNAPSHOT_COUNT = "max-planning-snapshot-count";
  /** 单次规划最多扫描的快照数，默认 Integer.MAX_VALUE。 */
  public static final ConfigOption<Integer> MAX_PLANNING_SNAPSHOT_COUNT_OPTION =
      ConfigOptions.key(PREFIX + MAX_PLANNING_SNAPSHOT_COUNT)
          .intType()
          .defaultValue(Integer.MAX_VALUE);

  /** 输出记录数限制的 key。 */
  public static final String LIMIT = "limit";
  /** 输出记录数限制，-1 表示不限制。 */
  public static final ConfigOption<Long> LIMIT_OPTION =
      ConfigOptions.key(PREFIX + LIMIT).longType().defaultValue(-1L);

  /** 最大允许规划失败次数的 key。 */
  public static final String MAX_ALLOWED_PLANNING_FAILURES = "max-allowed-planning-failures";
  /** 最大允许的连续规划失败次数，默认 3。 */
  public static final ConfigOption<Integer> MAX_ALLOWED_PLANNING_FAILURES_OPTION =
      ConfigOptions.key(PREFIX + MAX_ALLOWED_PLANNING_FAILURES).intType().defaultValue(3);
}
