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
package org.apache.iceberg;

import java.util.function.Function;
import org.apache.iceberg.io.CloseableIterable;

/**
 * 静态元数据表扫描（扫描固定内存数据）。
 *
 * <p>所属模块：iceberg-core。职责：为那些不需要从 manifest 读取文件、而是直接由内存数据生成的元数据表 （如
 * history、snapshots、metadata_log_entries）提供 {@link TableScan} 实现，{@link #doPlanFiles()} 返回一个 {@link
 * DataTask}。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>函数式构建任务：通过 {@code Function<StaticTableScan, DataTask>} 注入"如何把扫描变成数据任务"， 复用 {@link
 *       BaseMetadataTableScan} 的上下文/精简逻辑。
 *   <li>不可变快照上下文：{@link #newRefinedScan} 在 schema 投影时保留 buildTask。
 * </ul>
 *
 * <p>上下游关系：被各内存型元数据表（{@link HistoryTable} 等）作为 scan 返回值； 继承 {@link BaseMetadataTableScan}。
 */
class StaticTableScan extends BaseMetadataTableScan {
  private final Function<StaticTableScan, DataTask> buildTask;

  StaticTableScan(
      Table table,
      Schema schema,
      MetadataTableType tableType,
      Function<StaticTableScan, DataTask> buildTask) {
    super(table, schema, tableType);
    this.buildTask = buildTask;
  }

  StaticTableScan(
      Table table,
      Schema schema,
      MetadataTableType tableType,
      Function<StaticTableScan, DataTask> buildTask,
      TableScanContext context) {
    super(table, schema, tableType, context);
    this.buildTask = buildTask;
  }

  /**
   * 在 schema 投影后创建新的精简扫描，保留 buildTask 函数。
   *
   * @param table 主表
   * @param schema 投影后的 schema
   * @param context 扫描上下文
   * @return 新的 StaticTableScan 实例
   */
  @Override
  protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
    return new StaticTableScan(table, schema, tableType(), buildTask, context);
  }

  /**
   * 执行文件规划：调用 buildTask 函数生成 {@link DataTask}，包装为无操作关闭的迭代器返回。
   *
   * @return 包含单个 DataTask 的可关闭迭代器
   */
  @Override
  protected CloseableIterable<FileScanTask> doPlanFiles() {
    return CloseableIterable.withNoopClose(buildTask.apply(this));
  }
}
