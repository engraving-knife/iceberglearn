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
import org.apache.flink.annotation.Experimental;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.iceberg.SnapshotRef;

/**
 * 文件级说明：Iceberg Flink sink 写入选项的集中定义。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块根包）。
 *
 * <p>职责：以 {@link ConfigOption} 形式声明所有与写入相关的可配置项， 包括文件格式、目标文件大小、压缩编解码、upsert 模式、覆盖模式、
 * 分布式模式、分支、写入并行度与表刷新间隔等。
 *
 * <p>设计意图：将所有写入相关选项集中到一处，便于使用者统一查阅， 同时作为 {@link FlinkWriteConf} 解析配置的依据。
 *
 * <p>上下游关系：上游为 Flink SQL Hint 与全局配置，下游为 {@link FlinkWriteConf} 与 {@link
 * org.apache.iceberg.flink.sink.FlinkSink}。
 */
public class FlinkWriteOptions {

  /** 私有构造，配置项类禁止实例化。 */
  private FlinkWriteOptions() {}

  // File format for write operations(default: Table write.format.default )
  public static final ConfigOption<String> WRITE_FORMAT =
      ConfigOptions.key("write-format").stringType().noDefaultValue();

  // Overrides this table's write.target-file-size-bytes
  public static final ConfigOption<Long> TARGET_FILE_SIZE_BYTES =
      ConfigOptions.key("target-file-size-bytes").longType().noDefaultValue();

  // Overrides this table's write.<FILE_FORMAT>.compression-codec
  public static final ConfigOption<String> COMPRESSION_CODEC =
      ConfigOptions.key("compression-codec").stringType().noDefaultValue();

  // Overrides this table's write.<FILE_FORMAT>.compression-level
  public static final ConfigOption<String> COMPRESSION_LEVEL =
      ConfigOptions.key("compression-level").stringType().noDefaultValue();

  // Overrides this table's write.<FILE_FORMAT>.compression-strategy
  public static final ConfigOption<String> COMPRESSION_STRATEGY =
      ConfigOptions.key("compression-strategy").stringType().noDefaultValue();

  // Overrides this table's write.upsert.enabled
  public static final ConfigOption<Boolean> WRITE_UPSERT_ENABLED =
      ConfigOptions.key("upsert-enabled").booleanType().noDefaultValue();

  public static final ConfigOption<Boolean> OVERWRITE_MODE =
      ConfigOptions.key("overwrite-enabled").booleanType().defaultValue(false);

  // Overrides the table's write.distribution-mode
  public static final ConfigOption<String> DISTRIBUTION_MODE =
      ConfigOptions.key("distribution-mode").stringType().noDefaultValue();

  // Branch to write to
  public static final ConfigOption<String> BRANCH =
      ConfigOptions.key("branch").stringType().defaultValue(SnapshotRef.MAIN_BRANCH);

  public static final ConfigOption<Integer> WRITE_PARALLELISM =
      ConfigOptions.key("write-parallelism").intType().noDefaultValue();

  @Experimental
  public static final ConfigOption<Duration> TABLE_REFRESH_INTERVAL =
      ConfigOptions.key("table-refresh-interval").durationType().noDefaultValue();
}
