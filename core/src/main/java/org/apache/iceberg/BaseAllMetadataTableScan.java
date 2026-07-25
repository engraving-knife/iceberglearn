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

import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.iceberg.events.Listeners;
import org.apache.iceberg.events.ScanEvent;
import org.apache.iceberg.expressions.ExpressionUtil;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.base.Function;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.ParallelIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 元数据表扫描的抽象基类：扫描覆盖表中所有快照（而非单个快照）的元数据表。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为 "all_*" 类元数据表（如 all_data_files、all_manifests 等）提供统一的扫描骨架。
 *   <li>禁止按快照/ref/时间点筛选，因为这类表本身就跨越全部快照。
 *   <li>提供 {@link #reachableManifests} 工具方法：并行遍历所有快照收集可达 manifest 并去重。
 * </ul>
 *
 * <p>设计意图：把"跨快照扫描"的共性逻辑（manifest 收集、事件通知、过滤日志）抽到此处， 子类只需实现 {@link #doPlanFiles()} 关注具体行结构。使用 {@link
 * ParallelIterable} 利用 planExecutor 并行遍历快照，避免大表扫描时单线程瓶颈。
 *
 * <p>上下游关系：继承自 {@link BaseMetadataTableScan}；被 {@link AllDataFilesTable} 等子类使用。
 */
abstract class BaseAllMetadataTableScan extends BaseMetadataTableScan {
  private static final Logger LOG = LoggerFactory.getLogger(BaseAllMetadataTableScan.class);

  /**
   * 构造扫描器。
   *
   * @param table 底层物理表
   * @param schema 输出 schema
   * @param tableType 元数据表类型
   */
  BaseAllMetadataTableScan(Table table, Schema schema, MetadataTableType tableType) {
    super(table, schema, tableType);
  }

  /**
   * 带扫描上下文的构造器。
   *
   * @param table 底层物理表
   * @param schema 输出 schema
   * @param tableType 元数据表类型
   * @param context 扫描上下文
   */
  BaseAllMetadataTableScan(
      Table table, Schema schema, MetadataTableType tableType, TableScanContext context) {
    super(table, schema, tableType, context);
  }

  /**
   * 不支持选择快照：跨快照元数据表无法限定到单个快照。
   *
   * @param scanSnapshotId 快照 id
   * @return 不返回，始终抛异常
   * @throws UnsupportedOperationException 调用即抛
   */
  @Override
  public TableScan useSnapshot(long scanSnapshotId) {
    throw new UnsupportedOperationException("Cannot select snapshot in table: " + tableType());
  }

  /**
   * 不支持按 ref 选择：跨快照元数据表无法限定到单个 ref。
   *
   * @param ref 快照引用名
   * @return 不返回，始终抛异常
   * @throws UnsupportedOperationException 调用即抛
   */
  @Override
  public TableScan useRef(String ref) {
    throw new UnsupportedOperationException("Cannot select ref in table: " + tableType());
  }

  /**
   * 不支持按时间点选择：跨快照元数据表无法限定到单个快照。
   *
   * @param timestampMillis 时间戳（毫秒）
   * @return 不返回，始终抛异常
   * @throws UnsupportedOperationException 调用即抛
   */
  @Override
  public TableScan asOfTime(long timestampMillis) {
    throw new UnsupportedOperationException("Cannot select snapshot in table: " + tableType());
  }

  /**
   * 执行扫描并产出文件扫描任务。
   *
   * <p>逻辑：先记录扫描日志（表名+脱敏过滤表达式），再通过 {@link Listeners} 通知扫描事件监听器， 最后委托子类 {@link #doPlanFiles()}
   * 完成具体计划。
   *
   * @return 文件扫描任务的可关闭迭代器
   */
  @Override
  public CloseableIterable<FileScanTask> planFiles() {
    LOG.info(
        "Scanning metadata table {} with filter {}.",
        table(),
        ExpressionUtil.toSanitizedString(filter()));
    Listeners.notifyAll(new ScanEvent(table().name(), 0L, filter(), schema()));

    return doPlanFiles();
  }

  /**
   * 并行收集所有快照中可达的 manifest 并去重。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>取表全部快照；
   *   <li>用 {@code toManifests} 函数把每个快照映射为其 manifest 迭代器；
   *   <li>用 {@link ParallelIterable} 在 planExecutor 上并行遍历这些迭代器；
   *   <li>把结果塞入 HashSet 去重，并包成 noop-close 的可关闭迭代器返回。
   * </ol>
   *
   * <p>设计意图：跨快照扫描时同一 manifest 可能被多个快照引用，去重避免重复读取； 并行化提升大表扫描性能。
   *
   * @param toManifests 从快照映射到其 manifest 集合的函数
   * @return 去重后的 manifest 可关闭迭代器
   */
  protected CloseableIterable<ManifestFile> reachableManifests(
      Function<Snapshot, Iterable<ManifestFile>> toManifests) {
    Iterable<Snapshot> snapshots = table().snapshots();
    Iterable<Iterable<ManifestFile>> manifestIterables =
        Iterables.transform(snapshots, toManifests);

    try (CloseableIterable<ManifestFile> iterable =
        new ParallelIterable<>(manifestIterables, planExecutor())) {
      return CloseableIterable.withNoopClose(Sets.newHashSet(iterable));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to close parallel iterable", e);
    }
  }
}
