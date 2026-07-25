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
package org.apache.iceberg.flink.source;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.apache.flink.api.common.io.DefaultInputSplitAssigner;
import org.apache.flink.api.common.io.InputFormat;
import org.apache.flink.api.common.io.LocatableInputSplitAssigner;
import org.apache.flink.api.common.io.RichInputFormat;
import org.apache.flink.api.common.io.statistics.BaseStatistics;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.InputSplitAssigner;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.BaseMetadataTable;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.util.ThreadPools;

/**
 * 文件级说明：Iceberg 表的 Flink {@link InputFormat} 实现（旧版 Source API）。
 *
 * <p>所属模块：iceberg-flink（source 子包），用于 Flink 批处理模式读取 Iceberg 表。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 Iceberg 表扫描数据文件，生成 {@link FlinkInputSplit}。
 *   <li>通过 {@link DataIterator} 逐条读取 RowData。
 *   <li>支持本地性感知的 split 分配（LocatableInputSplitAssigner）。
 * </ul>
 *
 * <p>设计意图：兼容 Flink 旧版 Source API（InputFormat），与 FLIP-27 新版 Source API 并存。 对于 BaseMetadataTable 使用
 * DataTaskReader，普通表使用 RowDataFileScanTaskReader。
 *
 * <p>上下游关系：被 Flink Table API 通过 InputFormatSourceFunction 调用； 内部委托 {@link
 * RowDataFileScanTaskReader} 读取数据。
 */
public class FlinkInputFormat extends RichInputFormat<RowData, FlinkInputSplit> {

  private static final long serialVersionUID = 1L;

  private final TableLoader tableLoader;
  private final FileIO io;
  private final EncryptionManager encryption;
  private final ScanContext context;
  private final FileScanTaskReader rowDataReader;

  private transient DataIterator<RowData> iterator;
  private transient long currentReadCount = 0L;

  /**
   * 构造方法。
   *
   * @param tableLoader 表加载器
   * @param tableSchema 表 schema
   * @param io 文件 IO
   * @param encryption 加密管理器
   * @param context 扫描上下文
   */
  FlinkInputFormat(
      TableLoader tableLoader,
      Schema tableSchema,
      FileIO io,
      EncryptionManager encryption,
      ScanContext context) {
    this.tableLoader = tableLoader;
    this.io = io;
    this.encryption = encryption;
    this.context = context;

    tableLoader.open();
    Table table = tableLoader.loadTable();
    if (table instanceof BaseMetadataTable) {
      this.rowDataReader = new DataTaskReader(context.project());
    } else {
      this.rowDataReader =
          new RowDataFileScanTaskReader(
              tableSchema,
              context.project(),
              context.nameMapping(),
              context.caseSensitive(),
              context.filters());
    }
  }

  @VisibleForTesting
  Schema projectedSchema() {
    return context.project();
  }

  @Override
  public BaseStatistics getStatistics(BaseStatistics cachedStatistics) {
    // Legacy method, not be used.
    return null;
  }

  @Override
  public FlinkInputSplit[] createInputSplits(int minNumSplits) throws IOException {
    // Called in Job manager, so it is OK to load table from catalog.
    tableLoader.open();
    final ExecutorService workerPool =
        ThreadPools.newWorkerPool("iceberg-plan-worker-pool", context.planParallelism());
    try (TableLoader loader = tableLoader) {
      Table table = loader.loadTable();
      return FlinkSplitPlanner.planInputSplits(table, context, workerPool);
    } finally {
      workerPool.shutdown();
    }
  }

  @Override
  public InputSplitAssigner getInputSplitAssigner(FlinkInputSplit[] inputSplits) {
    return context.exposeLocality()
        ? new LocatableInputSplitAssigner(inputSplits)
        : new DefaultInputSplitAssigner(inputSplits);
  }

  @Override
  public void configure(Configuration parameters) {}

  @Override
  public void open(FlinkInputSplit split) {
    this.iterator = new DataIterator<>(rowDataReader, split.getTask(), io, encryption);
  }

  @Override
  public boolean reachedEnd() {
    if (context.limit() > 0 && currentReadCount >= context.limit()) {
      return true;
    } else {
      return !iterator.hasNext();
    }
  }

  @Override
  public RowData nextRecord(RowData reuse) {
    currentReadCount++;
    return iterator.next();
  }

  @Override
  public void close() throws IOException {
    if (iterator != null) {
      iterator.close();
    }
  }
}
