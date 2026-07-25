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

import org.apache.iceberg.events.IncrementalScanEvent;
import org.apache.iceberg.events.Listeners;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.SnapshotUtil;

/**
 * 增量扫描的抽象基类：基于快照区间（from, to] 产出新增内容的扫描任务。
 *
 * <p>所属模块：iceberg-core（扫描实现层，扩展 api 中的 {@link IncrementalScan} 接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供设置增量扫描区间（起始快照含/不含、结束快照、分支）的通用实现。
 *   <li>在 {@link #planFiles()} 中校验快照区间合法性、解析最终的 (fromExclusive, toInclusive]， 并通知 {@link
 *       IncrementalScanEvent} 监听器。
 *   <li>把实际的文件计划逻辑委托给子类实现的 {@link #doPlanFiles}。
 * </ul>
 *
 * <p>设计意图：把区间校验、引用解析（tag/branch）、事件通知等通用逻辑集中到基类， 子类只需关注"如何把一个快照区间转成具体扫描任务"。
 *
 * <p>上下游关系：被 {@code IncrementalDataTableScan}、增量 changelog 扫描等继承；底层调用 {@link SnapshotUtil} 做祖先关系校验。
 *
 * @param <ThisT> 实际扫描类型
 * @param <T> 扫描任务类型
 * @param <G> 扫描任务分组类型
 */
abstract class BaseIncrementalScan<ThisT, T extends ScanTask, G extends ScanTaskGroup<T>>
    extends BaseScan<ThisT, T, G> implements IncrementalScan<ThisT, T, G> {

  /**
   * 构造增量扫描实例。
   *
   * @param table 表
   * @param schema 扫描 schema
   * @param context 扫描上下文
   */
  protected BaseIncrementalScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
  }

  /**
   * 子类实现：基于解析后的 (fromExclusive, toInclusive] 快照区间产出扫描任务。
   *
   * @param fromSnapshotIdExclusive 起始快照（不含），可为 null 表示从最老祖先起
   * @param toSnapshotIdInclusive 结束快照（含）
   * @return 扫描任务集合
   */
  protected abstract CloseableIterable<T> doPlanFiles(
      Long fromSnapshotIdExclusive, long toSnapshotIdInclusive);

  /**
   * 按引用名设置起始快照（含），引用必须是 tag。
   *
   * @param ref tag 引用名
   * @return 当前扫描（用于链式调用）
   */
  @Override
  public ThisT fromSnapshotInclusive(String ref) {
    SnapshotRef snapshotRef = table().refs().get(ref);
    Preconditions.checkArgument(snapshotRef != null, "Cannot find ref: %s", ref);
    Preconditions.checkArgument(snapshotRef.isTag(), "Ref %s is not a tag", ref);
    return fromSnapshotInclusive(snapshotRef.snapshotId());
  }

  /**
   * 设置起始快照（含），即扫描包含该快照在内的变更。
   *
   * @param fromSnapshotId 起始快照 id
   * @return 当前扫描
   */
  @Override
  public ThisT fromSnapshotInclusive(long fromSnapshotId) {
    Preconditions.checkArgument(
        table().snapshot(fromSnapshotId) != null,
        "Cannot find the starting snapshot: %s",
        fromSnapshotId);
    TableScanContext newContext = context().fromSnapshotIdInclusive(fromSnapshotId);
    return newRefinedScan(table(), schema(), newContext);
  }

  /**
   * 按引用名设置起始快照（不含），引用必须是 tag。
   *
   * @param ref tag 引用名
   * @return 当前扫描
   */
  @Override
  public ThisT fromSnapshotExclusive(String ref) {
    SnapshotRef snapshotRef = table().refs().get(ref);
    Preconditions.checkArgument(snapshotRef != null, "Cannot find ref: %s", ref);
    Preconditions.checkArgument(snapshotRef.isTag(), "Ref %s is not a tag", ref);
    return fromSnapshotExclusive(snapshotRef.snapshotId());
  }

  /**
   * 设置起始快照（不含），即扫描该快照之后的变更。
   *
   * <p>设计要点：不校验该快照当前是否存在，因为它可能指向一个已被过期的父快照（仅作为边界）。
   *
   * @param fromSnapshotId 起始快照 id（不含）
   * @return 当前扫描
   */
  @Override
  public ThisT fromSnapshotExclusive(long fromSnapshotId) {
    // for exclusive behavior, table().snapshot(fromSnapshotId) check can't be applied
    // as fromSnapshotId could be matched to a parent snapshot that is already expired
    TableScanContext newContext = context().fromSnapshotIdExclusive(fromSnapshotId);
    return newRefinedScan(table(), schema(), newContext);
  }

  /**
   * 设置结束快照（含）。
   *
   * @param toSnapshotId 结束快照 id
   * @return 当前扫描
   */
  @Override
  public ThisT toSnapshot(long toSnapshotId) {
    Preconditions.checkArgument(
        table().snapshot(toSnapshotId) != null, "Cannot find the end snapshot: %s", toSnapshotId);
    TableScanContext newContext = context().toSnapshotId(toSnapshotId);
    return newRefinedScan(table(), schema(), newContext);
  }

  /**
   * 按引用名设置结束快照，引用必须是 tag。
   *
   * @param ref tag 引用名
   * @return 当前扫描
   */
  @Override
  public ThisT toSnapshot(String ref) {
    SnapshotRef snapshotRef = table().refs().get(ref);
    Preconditions.checkArgument(snapshotRef != null, "Cannot find ref: %s", ref);
    Preconditions.checkArgument(snapshotRef.isTag(), "Ref %s is not a tag", ref);
    return toSnapshot(snapshotRef.snapshotId());
  }

  /**
   * 指定在某个分支上进行增量扫描。
   *
   * @param branch 分支名
   * @return 当前扫描
   */
  @Override
  public ThisT useBranch(String branch) {
    SnapshotRef snapshotRef = table().refs().get(branch);
    Preconditions.checkArgument(snapshotRef != null, "Cannot find ref: %s", branch);
    Preconditions.checkArgument(snapshotRef.isBranch(), "Ref %s is not a branch", branch);
    return newRefinedScan(table(), schema(), context().useBranch(branch));
  }

  /**
   * 执行增量扫描文件计划。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若既未设置 from 也未设置 to，且表当前无快照，则返回空（并跳过事件通知）；
   *   <li>解析结束快照 id（toSnapshotIdInclusive）：优先用上下文中的 toSnapshotId，否则取 当前分支/当前快照；
   *   <li>解析起始快照 id（fromSnapshotIdExclusive）：根据 inclusive/exclusive 设置转换为 不含的边界；
   *   <li>通知 {@link IncrementalScanEvent} 监听器；
   *   <li>调用 {@link #doPlanFiles} 由子类产出实际任务。
   * </ol>
   *
   * @return 扫描任务集合
   */
  @Override
  public CloseableIterable<T> planFiles() {
    if (scanCurrentLineage() && table().currentSnapshot() == null) {
      // If the table is empty (no current snapshot) and both from and to snapshots aren't set,
      // simply return an empty iterable. In this case, the listener notification is also skipped.
      return CloseableIterable.empty();
    }

    long toSnapshotIdInclusive = toSnapshotIdInclusive();
    Long fromSnapshotIdExclusive = fromSnapshotIdExclusive(toSnapshotIdInclusive);

    if (fromSnapshotIdExclusive != null) {
      Listeners.notifyAll(
          new IncrementalScanEvent(
              table().name(),
              fromSnapshotIdExclusive,
              toSnapshotIdInclusive,
              filter(),
              schema(),
              false /* from snapshot ID inclusive */));
    } else {
      Listeners.notifyAll(
          new IncrementalScanEvent(
              table().name(),
              SnapshotUtil.oldestAncestorOf(table(), toSnapshotIdInclusive).snapshotId(),
              toSnapshotIdInclusive,
              filter(),
              schema(),
              true /* from snapshot ID inclusive */));
    }

    return doPlanFiles(fromSnapshotIdExclusive, toSnapshotIdInclusive);
  }

  /** 判断是否为"扫描当前血统"：from 与 to 都未设置。 */
  private boolean scanCurrentLineage() {
    return context().fromSnapshotId() == null && context().toSnapshotId() == null;
  }

  /**
   * 解析结束快照 id（含）。
   *
   * <p>逻辑：若上下文中设置了 toSnapshotId，则在指定分支时校验该快照是分支祖先； 否则取当前分支/当前快照作为结束快照。
   *
   * @return 结束快照 id
   */
  private long toSnapshotIdInclusive() {
    if (context().toSnapshotId() != null) {
      if (context().branch() != null) {
        Snapshot currentSnapshot = table().snapshot(context().branch());
        Preconditions.checkArgument(
            SnapshotUtil.isAncestorOf(
                table(), currentSnapshot.snapshotId(), context().toSnapshotId()),
            "End snapshot is not a valid snapshot on the current branch: %s",
            context().branch());
      }

      return context().toSnapshotId();
    } else {
      Snapshot currentSnapshot;
      if (context().branch() != null) {
        currentSnapshot = table().snapshot(context().branch());
      } else {
        currentSnapshot = table().currentSnapshot();
      }

      Preconditions.checkArgument(
          currentSnapshot != null, "End snapshot is not set and table has no current snapshot");
      return currentSnapshot.snapshotId();
    }
  }

  /**
   * 解析起始快照 id（不含）。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>未设置 from 则返回 null（表示从最老祖先起）；
   *   <li>设置 inclusive 时，校验 from 是 to 的祖先，返回 from 的父快照 id（可能为 null）；
   *   <li>设置 exclusive 时，校验 from 是 to 的父祖先，直接返回 from。
   * </ul>
   *
   * @param toSnapshotIdInclusive 结束快照 id（含）
   * @return 起始快照 id（不含），或 null
   */
  private Long fromSnapshotIdExclusive(long toSnapshotIdInclusive) {
    Long fromSnapshotId = context().fromSnapshotId();
    boolean fromSnapshotInclusive = context().fromSnapshotInclusive();

    if (fromSnapshotId == null) {
      return null;
    } else {
      if (fromSnapshotInclusive) {
        // validate fromSnapshotId is an ancestor of toSnapshotIdInclusive
        Preconditions.checkArgument(
            SnapshotUtil.isAncestorOf(table(), toSnapshotIdInclusive, fromSnapshotId),
            "Starting snapshot (inclusive) %s is not an ancestor of end snapshot %s",
            fromSnapshotId,
            toSnapshotIdInclusive);
        // for inclusive behavior fromSnapshotIdExclusive is set to the parent snapshot ID,
        // which can be null
        return table().snapshot(fromSnapshotId).parentId();

      } else {
        // validate there is an ancestor of toSnapshotIdInclusive where parent is fromSnapshotId
        Preconditions.checkArgument(
            SnapshotUtil.isParentAncestorOf(table(), toSnapshotIdInclusive, fromSnapshotId),
            "Starting snapshot (exclusive) %s is not a parent ancestor of end snapshot %s",
            fromSnapshotId,
            toSnapshotIdInclusive);
        return fromSnapshotId;
      }
    }
  }
}
