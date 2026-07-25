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
 * 通过 Flink 流式作业对 Iceberg 表的数据文件进行重写压缩的动作实现。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：继承 Iceberg 的 {@link BaseRewriteDataFilesAction}，利用 Flink 的 {@link
 * StreamExecutionEnvironment} 并行扫描并重写多个 {@link CombinedScanTask}，将小文件合并为大文件。
 *
 * <p>设计意图：模板方法模式——基类定义流程，本类提供 Flink 相关的具体实现。 上下游：由 {@link Actions#rewriteDataFiles()} 创建；向下使用
 * {@link RowDataRewriter} 执行真正的数据重写。
 */
public class RewriteDataFilesAction extends BaseRewriteDataFilesAction<RewriteDataFilesAction> {

  private StreamExecutionEnvironment env;
  private int maxParallelism;

  /** 构造动作，绑定 Flink 执行环境与目标表，最大并行度默认取环境并行度。 */
  public RewriteDataFilesAction(StreamExecutionEnvironment env, Table table) {
    super(table);
    this.env = env;
    this.maxParallelism = env.getParallelism();
  }

  /** 返回表对应的 FileIO，用于读写数据文件。 */
  @Override
  protected FileIO fileIO() {
    return table().io();
  }

  /**
   * 将一组组合扫描任务通过 Flink DataStream 并行重写为新的数据文件。
   *
   * <p>逻辑：把 combinedScanTasks 作为 source，按 maxParallelism 与任务数取较小值作为并行度， 通过 {@link RowDataRewriter}
   * 在 Flink 算子中读取并写出新的数据文件。
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

  /** 返回自身引用，用于基类链式调用。 */
  @Override
  protected RewriteDataFilesAction self() {
    return this;
  }

  /** 设置重写作业的最大并行度，必须为正。 */
  public RewriteDataFilesAction maxParallelism(int parallelism) {
    Preconditions.checkArgument(parallelism > 0, "Invalid max parallelism %s", parallelism);
    this.maxParallelism = parallelism;
    return this;
  }
}
