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
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.description.Description;
import org.apache.flink.configuration.description.TextElement;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.iceberg.flink.source.assigner.SplitAssignerType;
import org.apache.iceberg.util.ThreadPools;

/**
 * Iceberg Flink 集成的全局配置项定义。
 *
 * <p>所属模块：iceberg-flink，集中声明可通过 Flink 配置体系设置的 Iceberg source/sink 行为开关。
 *
 * <p>职责：以 {@link ConfigOption} 形式定义 source 并行度推断、split 本地性、读取批大小、 工作线程池大小、是否使用 FLIP-27 source、split
 * assigner 类型等配置项及其默认值。
 *
 * <p>设计意图：统一通过 Flink 的 {@link Configuration} / {@link TableEnvironment} 体系管理配置， 既支持 Java API（{@code
 * FlinkSource.forRowData().flinkConf(configuration)}）也支持 SQL/Table API （{@code
 * tEnv.getConfig().getConfiguration()}），保证两种入口的配置语义一致。
 *
 * <p>上下游关系：被 {@link FlinkReadConf}/{@link FlinkWriteConf} 解析使用，由用户在作业或 SQL 环境中设置。
 */
public class FlinkConfigOptions {

  /** 配置项工具类，私有构造禁止实例化。 */
  private FlinkConfigOptions() {}

  /** 是否根据 split 数量自动推断 source 并行度；为 false 时由配置显式指定。 */
  public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM =
      ConfigOptions.key("table.exec.iceberg.infer-source-parallelism")
          .booleanType()
          .defaultValue(true)
          .withDescription(
              "If is false, parallelism of source are set by config.\n"
                  + "If is true, source parallelism is inferred according to splits number.\n");

  /** 推断 source 并行度时的上限。 */
  public static final ConfigOption<Integer> TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM_MAX =
      ConfigOptions.key("table.exec.iceberg.infer-source-parallelism.max")
          .intType()
          .defaultValue(100)
          .withDescription("Sets max infer parallelism for source operator.");

  /** 是否向 Flink 暴露 split 所在主机信息，以启用 Flink 的本地性感知 split 分配。 */
  public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_EXPOSE_SPLIT_LOCALITY_INFO =
      ConfigOptions.key("table.exec.iceberg.expose-split-locality-info")
          .booleanType()
          .noDefaultValue()
          .withDescription(
              "Expose split host information to use Flink's locality aware split assigner.");

  /** Iceberg reader 每次拉取批次的目标记录数。 */
  public static final ConfigOption<Integer> SOURCE_READER_FETCH_BATCH_RECORD_COUNT =
      ConfigOptions.key("table.exec.iceberg.fetch-batch-record-count")
          .intType()
          .defaultValue(2048)
          .withDescription("The target number of records for Iceberg reader fetch batch.");

  /** 用于 manifest 规划/扫描的工作线程池大小。 */
  public static final ConfigOption<Integer> TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE =
      ConfigOptions.key("table.exec.iceberg.worker-pool-size")
          .intType()
          .defaultValue(ThreadPools.WORKER_THREAD_POOL_SIZE)
          .withDescription("The size of workers pool used to plan or scan manifests.");

  /** 是否使用基于 FLIP-27 的 Iceberg source 实现。 */
  public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE =
      ConfigOptions.key("table.exec.iceberg.use-flip27-source")
          .booleanType()
          .defaultValue(false)
          .withDescription("Use the FLIP-27 based Iceberg source implementation.");

  /** split assigner 类型，决定 split 如何分配给 reader（如 SIMPLE 不保证顺序与本地性）。 */
  public static final ConfigOption<SplitAssignerType> TABLE_EXEC_SPLIT_ASSIGNER_TYPE =
      ConfigOptions.key("table.exec.iceberg.split-assigner-type")
          .enumType(SplitAssignerType.class)
          .defaultValue(SplitAssignerType.SIMPLE)
          .withDescription(
              Description.builder()
                  .text("Split assigner type that determine how splits are assigned to readers.")
                  .linebreak()
                  .list(
                      TextElement.text(
                          SplitAssignerType.SIMPLE
                              + ": simple assigner that doesn't provide any guarantee on order or locality."))
                  .build());
}
