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
package org.apache.iceberg.flink.actions;

import java.util.List;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.BaseRewriteDataFilesAction;
import org.apache.iceberg.flink.source.RowDataRewriter;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：基于 Flink 流执行环境重写 Iceberg 数据文件的动作实现。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 actions 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link BaseRewriteDataFilesAction}，复用其文件合并策略与扫描逻辑。
 *   <li>利用 Flink {@link StreamExecutionEnvironment} 并行执行数据文件重写， 将小文件合并为大文件以提升查询性能。
 *   <li>支持用户配置最大并行度。
 * </ul>
 *
 * <p>设计意图：通过 Flink DataStream 把 CombinedScanTask 列表分发到并行度合适的算子， 由 {@link RowDataRewriter}
 * 完成实际的读取-写出-提交流程。
 *
 * <p>上下游关系：上游为 {@link Actions} 入口与用户配置，下游为 {@link RowDataRewriter} 与 Iceberg 表的文件 IO。
 */
public class RewriteDataFilesAction extends BaseRewriteDataFilesAction<RewriteDataFilesAction> {

  private StreamExecutionEnvironment env;
  private int maxParallelism;

  /** 构造重写动作，绑定执行环境与目标表，默认使用环境并行度。 */
  public RewriteDataFilesAction(StreamExecutionEnvironment env, Table table) {
    super(table);
    this.env = env;
    this.maxParallelism = env.getParallelism();
  }

  /** 返回当前表的 {@link FileIO}。 */
  @Override
  protected FileIO fileIO() {
    return table().io();
  }

  /**
   * 在 Flink 集群中并行重写给定扫描任务对应的数据文件。
   *
   * <p>逻辑：取扫描任务数与最大并行度的较小值作为实际并行度， 把任务列表转为 DataStream 后由 {@link RowDataRewriter} 完成重写。
   *
   * @param combinedScanTasks 合并后的扫描任务列表
   * @return 重写后产生的数据文件列表
   */
  @Override
  protected List<DataFile> rewriteDataForTasks(List<CombinedScanTask> combinedScanTasks) {
    int size = combinedScanTasks.size();
    int parallelism = Math.min(size, maxParallelism);
    DataStream<CombinedScanTask> dataStream = env.fromCollection(combinedScanTasks);
    RowDataRewriter rowDataRewriter =
        new RowDataRewriter(table(), caseSensitive(), fileIO(), encryptionManager());
    try {
      return rowDataRewriter.rewriteDataForTasks(dataStream, parallelism);
    } catch (Exception e) {
      throw new RuntimeException("Rewrite data file error.", e);
    }
  }

  /** 返回自身，用于链式 API。 */
  @Override
  protected RewriteDataFilesAction self() {
    return this;
  }

  /** 设置最大并行度，必须为正整数。 */
  public RewriteDataFilesAction maxParallelism(int parallelism) {
    Preconditions.checkArgument(parallelism > 0, "Invalid max parallelism %s", parallelism);
    this.maxParallelism = parallelism;
    return this;
  }
}
