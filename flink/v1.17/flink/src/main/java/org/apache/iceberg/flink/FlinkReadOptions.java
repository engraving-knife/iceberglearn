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
 * 文件级说明：Iceberg Flink source 读取选项的集中定义。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块根包）。
 *
 * <p>职责：以 {@link ConfigOption} 形式声明所有与读取相关的可配置项， 包括快照选择、流式起始策略、分片大小、监控间隔、列统计、限制等。
 *
 * <p>设计意图：将所有读取相关选项集中到一处，便于使用者统一查阅， 同时作为 {@link FlinkReadConf} 解析配置的依据。
 *
 * <p>上下游关系：上游为 Flink SQL Hint 与全局配置，下游为 {@link FlinkReadConf} 与 {@link
 * org.apache.iceberg.flink.source.ScanContext}。
 */
public class FlinkReadOptions {
  private static final String PREFIX = "connector.iceberg.";

  /** 私有构造，配置项类禁止实例化。 */
  private FlinkReadOptions() {}

  public static final ConfigOption<Long> SNAPSHOT_ID =
      ConfigOptions.key("snapshot-id").longType().defaultValue(null);

  public static final ConfigOption<String> TAG =
      ConfigOptions.key("tag").stringType().defaultValue(null);

  public static final ConfigOption<String> BRANCH =
      ConfigOptions.key("branch").stringType().defaultValue(null);

  public static final ConfigOption<String> START_TAG =
      ConfigOptions.key("start-tag").stringType().defaultValue(null);

  public static final ConfigOption<String> END_TAG =
      ConfigOptions.key("end-tag").stringType().defaultValue(null);

  public static final String CASE_SENSITIVE = "case-sensitive";
  public static final ConfigOption<Boolean> CASE_SENSITIVE_OPTION =
      ConfigOptions.key(PREFIX + CASE_SENSITIVE).booleanType().defaultValue(false);

  public static final ConfigOption<Long> AS_OF_TIMESTAMP =
      ConfigOptions.key("as-of-timestamp").longType().defaultValue(null);

  public static final String STARTING_STRATEGY = "starting-strategy";
  public static final ConfigOption<StreamingStartingStrategy> STARTING_STRATEGY_OPTION =
      ConfigOptions.key(PREFIX + STARTING_STRATEGY)
          .enumType(StreamingStartingStrategy.class)
          .defaultValue(StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT);

  public static final ConfigOption<Long> START_SNAPSHOT_TIMESTAMP =
      ConfigOptions.key("start-snapshot-timestamp").longType().defaultValue(null);

  public static final ConfigOption<Long> START_SNAPSHOT_ID =
      ConfigOptions.key("start-snapshot-id").longType().defaultValue(null);

  public static final ConfigOption<Long> END_SNAPSHOT_ID =
      ConfigOptions.key("end-snapshot-id").longType().defaultValue(null);

  public static final String SPLIT_SIZE = "split-size";
  public static final ConfigOption<Long> SPLIT_SIZE_OPTION =
      ConfigOptions.key(PREFIX + SPLIT_SIZE)
          .longType()
          .defaultValue(TableProperties.SPLIT_SIZE_DEFAULT);

  public static final String SPLIT_LOOKBACK = "split-lookback";
  public static final ConfigOption<Integer> SPLIT_LOOKBACK_OPTION =
      ConfigOptions.key(PREFIX + SPLIT_LOOKBACK)
          .intType()
          .defaultValue(TableProperties.SPLIT_LOOKBACK_DEFAULT);

  public static final String SPLIT_FILE_OPEN_COST = "split-file-open-cost";
  public static final ConfigOption<Long> SPLIT_FILE_OPEN_COST_OPTION =
      ConfigOptions.key(PREFIX + SPLIT_FILE_OPEN_COST)
          .longType()
          .defaultValue(TableProperties.SPLIT_OPEN_FILE_COST_DEFAULT);

  public static final String STREAMING = "streaming";
  public static final ConfigOption<Boolean> STREAMING_OPTION =
      ConfigOptions.key(PREFIX + STREAMING).booleanType().defaultValue(false);

  public static final String MONITOR_INTERVAL = "monitor-interval";
  public static final ConfigOption<String> MONITOR_INTERVAL_OPTION =
      ConfigOptions.key(PREFIX + MONITOR_INTERVAL).stringType().defaultValue("60s");

  public static final String INCLUDE_COLUMN_STATS = "include-column-stats";
  public static final ConfigOption<Boolean> INCLUDE_COLUMN_STATS_OPTION =
      ConfigOptions.key(PREFIX + INCLUDE_COLUMN_STATS).booleanType().defaultValue(false);

  public static final String MAX_PLANNING_SNAPSHOT_COUNT = "max-planning-snapshot-count";
  public static final ConfigOption<Integer> MAX_PLANNING_SNAPSHOT_COUNT_OPTION =
      ConfigOptions.key(PREFIX + MAX_PLANNING_SNAPSHOT_COUNT)
          .intType()
          .defaultValue(Integer.MAX_VALUE);

  public static final String LIMIT = "limit";
  public static final ConfigOption<Long> LIMIT_OPTION =
      ConfigOptions.key(PREFIX + LIMIT).longType().defaultValue(-1L);

  public static final String MAX_ALLOWED_PLANNING_FAILURES = "max-allowed-planning-failures";
  public static final ConfigOption<Integer> MAX_ALLOWED_PLANNING_FAILURES_OPTION =
      ConfigOptions.key(PREFIX + MAX_ALLOWED_PLANNING_FAILURES).intType().defaultValue(3);
}
