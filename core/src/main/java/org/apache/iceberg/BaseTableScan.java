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

import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.util.TableScanUtil;

/**
 * {@link TableScan} 实现的抽象基类，提供基于快照的扫描通用骨架。
 *
 * <p>所属模块：iceberg-core（扫描计划核心实现层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link SnapshotScan}，复用快照上下文、列裁剪、过滤等通用扫描能力。
 *   <li>实现 {@link TableScan} 接口的默认行为：增量扫描接口直接抛出 {@link UnsupportedOperationException}，强制子类按需覆盖。
 *   <li>提供 {@link #planTasks()} 的标准实现：先 planFiles 再切分再 bin-pack。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把"按快照扫描全表"和"增量扫描"分离：全表扫描不需要实现 appendsBetween/appendsAfter， 因此基类直接抛异常，避免子类被强制实现无用方法。
 *   <li>planTasks 流程统一为 splitFiles + planTasks 两步，便于所有子类复用。
 * </ul>
 *
 * <p>上下游关系：继承 {@link SnapshotScan}；被 {@code DataTableScan} 等具体扫描类继承； 上层通过 {@link TableScan} 接口调用。
 */
abstract class BaseTableScan extends SnapshotScan<TableScan, FileScanTask, CombinedScanTask>
    implements TableScan {

  /**
   * 构造表扫描器。
   *
   * @param table 目标表
   * @param schema 读取使用的 schema
   * @param context 扫描上下文
   */
  protected BaseTableScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
  }

  /**
   * 增量扫描接口（区间）：基类不支持，子类按需覆盖。
   *
   * @param fromSnapshotId 起始快照 id
   * @param toSnapshotId 结束快照 id
   * @return 增量扫描器
   * @throws UnsupportedOperationException 基类始终抛出
   */
  @Override
  public TableScan appendsBetween(long fromSnapshotId, long toSnapshotId) {
    throw new UnsupportedOperationException("Incremental scan is not supported");
  }

  /**
   * 增量扫描接口（单点起）：基类不支持，子类按需覆盖。
   *
   * @param fromSnapshotId 起始快照 id
   * @return 增量扫描器
   * @throws UnsupportedOperationException 基类始终抛出
   */
  @Override
  public TableScan appendsAfter(long fromSnapshotId) {
    throw new UnsupportedOperationException("Incremental scan is not supported");
  }

  /**
   * 计划可下发的合并扫描任务。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>调用 {@link #planFiles()} 获取文件扫描任务。
   *   <li>用 {@link TableScanUtil#splitFiles} 按目标分片大小拆分文件。
   *   <li>用 {@link TableScanUtil#planTasks} 把拆分后的文件按 bin-packing 合并为多个 {@link CombinedScanTask}。
   * </ol>
   *
   * @return 合并扫描任务迭代器
   */
  @Override
  public CloseableIterable<CombinedScanTask> planTasks() {
    CloseableIterable<FileScanTask> fileScanTasks = planFiles();
    CloseableIterable<FileScanTask> splitFiles =
        TableScanUtil.splitFiles(fileScanTasks, targetSplitSize());
    return TableScanUtil.planTasks(
        splitFiles, targetSplitSize(), splitLookback(), splitOpenFileCost());
  }
}
