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

/**
 * 文件级说明：表扫描配置接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义表扫描的配置契约，产出 {@link FileScanTask}（组合为 {@link CombinedScanTask}）。
 *   <li>支持基于快照 ID、引用（branch/tag）或时间戳的时间旅行读取。
 *   <li>提供（已废弃的）增量 append 扫描便捷方法，建议改用 {@link Table#newIncrementalAppendScan()}。
 * </ul>
 *
 * <p>设计意图：扫描对象是不可变的配置载体，每次配置变更返回新的 {@code TableScan} 实例， 便于链式调用与并发共享。将配置与执行分离，扫描规划延迟到 {@code
 * planFiles()} 时执行。
 *
 * <p>上下游关系：由 {@link Table#newScan()} 创建；被引擎批式读取器消费。
 */
public interface TableScan extends Scan<TableScan, FileScanTask, CombinedScanTask> {
  /**
   * 返回本次扫描所读取数据的 {@link Table}。
   *
   * @return 扫描所属的表
   */
  Table table();

  /**
   * 基于当前扫描配置，创建一个使用指定快照 ID 的新 {@link TableScan}（时间旅行读取）。
   *
   * @param snapshotId 目标快照 ID
   * @return 基于该快照 ID 的新扫描对象
   * @throws IllegalArgumentException 若快照不存在
   */
  TableScan useSnapshot(long snapshotId);

  /**
   * 基于当前扫描配置，创建一个使用指定引用（branch 或 tag）的新 {@link TableScan}。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，由具体实现覆写。
   *
   * @param ref 引用名称
   * @return 基于该引用的新扫描对象
   * @throws IllegalArgumentException 若指定名称的引用不存在
   */
  default TableScan useRef(String ref) {
    throw new UnsupportedOperationException("Using a reference is not supported");
  }

  /**
   * 基于当前扫描配置，创建一个"时间旅行"到指定时间点的新 {@link TableScan}：使用扫描分支上 （未设置分支时为 main 分支）不超过给定时间的最新快照。
   *
   * @param timestampMillis 时间戳（毫秒）
   * @return 基于该时间点快照的新扫描对象
   * @throws IllegalArgumentException 若找不到对应快照，或对 tag 引用尝试时间旅行
   */
  TableScan asOfTime(long timestampMillis);

  /**
   * 创建一个新的 {@link TableScan}，读取从 {@code fromSnapshotId}（不含）到 {@code toSnapshotId} （含）之间新增的 append
   * 数据。
   *
   * @param fromSnapshotId 用户上次读取的最后一个快照 ID（不含）
   * @param toSnapshotId 读取 append 数据直到该快照 ID（含）
   * @return 可读取指定区间 append 数据的表扫描
   * @deprecated 自 1.0.0 起，将在 2.0.0 移除；请改用 {@link Table#newIncrementalAppendScan()}
   */
  @Deprecated
  default TableScan appendsBetween(long fromSnapshotId, long toSnapshotId) {
    throw new UnsupportedOperationException("Incremental scan is not supported");
  }

  /**
   * 创建一个新的 {@link TableScan}，读取从 {@code fromSnapshotId}（不含）到当前快照（含）之间 新增的 append 数据。
   *
   * @param fromSnapshotId 用户上次读取的最后一个快照 ID（不含）
   * @return 可读取指定区间 append 数据的表扫描
   * @deprecated 自 1.0.0 起，将在 2.0.0 移除；请改用 {@link Table#newIncrementalAppendScan()}
   */
  @Deprecated
  default TableScan appendsAfter(long fromSnapshotId) {
    throw new UnsupportedOperationException("Incremental scan is not supported");
  }

  /**
   * 返回本次扫描将使用的 {@link Snapshot}。
   *
   * <p>若未通过 {@link #asOfTime(long)} 或 {@link #useSnapshot(long)} 指定，则使用表的当前快照。
   *
   * @return 本次扫描使用的快照
   */
  Snapshot snapshot();
}
