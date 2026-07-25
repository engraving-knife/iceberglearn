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
 * Flink Iceberg 相关的全局配置选项定义。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：声明可在 Flink {@link Configuration} 或 {@link TableEnvironment} 中设置的
 * Iceberg 表执行选项，例如是否推断 source 并行度、 worker 线程池大小、是否使用 FLIP-27 source 实现等。
 *
 * <p>设计意图：以 {@link ConfigOption} 形式声明选项，既支持 Java API 设置， 也支持 SQL/Table API 通过 TableEnvironment 配置。
 *
 * <p>Java API 用法：
 *
 * <pre>
 *   configuration.setBoolean(FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM, true);
 *   FlinkSource.forRowData()
 *       .flinkConf(configuration)
 *       ...
 * </pre>
 *
 * <p>SQL/Table API 用法：
 *
 * <pre>
 *   TableEnvironment tEnv = createTableEnv();
 *   tEnv.getConfig()
 *        .getConfiguration()
 *        .setBoolean(FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM, true);
 * </pre>
 */
public class FlinkConfigOptions {

  private FlinkConfigOptions() {}

  /** 是否按 split 数推断 source 算子并行度，默认开启。 */
  public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM =
      ConfigOptions.key("table.exec.iceberg.infer-source-parallelism")
          .booleanType()
          .defaultValue(true)
          .withDescription(
              "If is false, parallelism of source are set by config.\n"
                  + "If is true, source parallelism is inferred according to splits number.\n");

  /** 推断 source 并行度的上限，默认 100。 */
  public static final ConfigOption<Integer> TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM_MAX =
      ConfigOptions.key("table.exec.iceberg.infer-source-parallelism.max")
          .intType()
          .defaultValue(100)
          .withDescription("Sets max infer parallelism for source operator.");

  /** 是否暴露 split 所在 host 信息，便于 Flink 本地化 split assigner 使用。 */
  public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_EXPOSE_SPLIT_LOCALITY_INFO =
      ConfigOptions.key("table.exec.iceberg.expose-split-locality-info")
          .booleanType()
          .noDefaultValue()
          .withDescription(
              "Expose split host information to use Flink's locality aware split assigner.");

  /** Iceberg reader 单次 fetch 的目标记录数，默认 2048。 */
  public static final ConfigOption<Integer> SOURCE_READER_FETCH_BATCH_RECORD_COUNT =
      ConfigOptions.key("table.exec.iceberg.fetch-batch-record-count")
          .intType()
          .defaultValue(2048)
          .withDescription("The target number of records for Iceberg reader fetch batch.");

  /** Iceberg 用于规划/扫描 manifest 的 worker 线程池大小。 */
  public static final ConfigOption<Integer> TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE =
      ConfigOptions.key("table.exec.iceberg.worker-pool-size")
          .intType()
          .defaultValue(ThreadPools.WORKER_THREAD_POOL_SIZE)
          .withDescription("The size of workers pool used to plan or scan manifests.");

  /** 是否使用基于 FLIP-27 的 Iceberg source 实现，默认关闭。 */
  public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE =
      ConfigOptions.key("table.exec.iceberg.use-flip27-source")
          .booleanType()
          .defaultValue(false)
          .withDescription("Use the FLIP-27 based Iceberg source implementation.");

  /** Split 分配器类型，决定 splits 如何分配给 reader，默认 SIMPLE。 */
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
