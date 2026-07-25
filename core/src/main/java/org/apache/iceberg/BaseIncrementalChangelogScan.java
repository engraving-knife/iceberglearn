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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.ManifestGroup.CreateTasksFunction;
import org.apache.iceberg.ManifestGroup.TaskContext;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.FluentIterable;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.TableScanUtil;

/**
 * 增量变更日志扫描：在指定快照区间内产出数据行级别的变更（新增/删除）任务。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link IncrementalChangelogScan}，按 (fromSnapshotIdExclusive, toSnapshotIdInclusive]
 *       区间扫描产生 {@link ChangelogScanTask}。
 *   <li>把区间内多个快照的提交按时间顺序组织，并为每个变更打上 ordinal 序号。
 *   <li>通过 manifest entry 状态（ADDED/DELETED）区分行级新增与删除。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>变更序号（change ordinal）：区间内按从旧到新依次 0,1,2...，便于下游按提交顺序消费。
 *   <li>忽略 REPLACE 操作：REPLACE 是整表替换，不属于增量变更语义，故跳过。
 *   <li>不支持 delete files：当前变更日志扫描尚不支持等值/位置删除，遇 delete manifest 直接报错。
 *   <li>忽略 EXISTING entry：只关心真正发生变更的 ADDED/DELETED entry。
 * </ul>
 *
 * <p>上下游关系：继承 {@link BaseIncrementalScan}；产出 {@link ChangelogScanTask} 供引擎增量消费。
 */
class BaseIncrementalChangelogScan
    extends BaseIncrementalScan<
        IncrementalChangelogScan, ChangelogScanTask, ScanTaskGroup<ChangelogScanTask>>
    implements IncrementalChangelogScan {

  /**
   * 构造增量变更日志扫描，使用表自身 schema 与空上下文。
   *
   * @param table 底层物理表
   */
  BaseIncrementalChangelogScan(Table table) {
    this(table, table.schema(), TableScanContext.empty());
  }

  /**
   * 私有构造，用于 refine 时复制扫描器。
   *
   * @param table 底层物理表
   * @param schema 输出 schema
   * @param context 扫描上下文
   */
  private BaseIncrementalChangelogScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
  }

  /**
   * 基于新上下文创建细化扫描器（不可变复制模式）。
   *
   * @param newTable 新的表
   * @param newSchema 新的 schema
   * @param newContext 新的扫描上下文
   * @return 新的 {@link BaseIncrementalChangelogScan}
   */
  @Override
  protected IncrementalChangelogScan newRefinedScan(
      Table newTable, Schema newSchema, TableScanContext newContext) {
    return new BaseIncrementalChangelogScan(newTable, newSchema, newContext);
  }

  /**
   * 在指定快照区间内规划变更日志扫描任务。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>用 {@link #orderedChangelogSnapshots} 取区间内有序快照（旧→新），空则返回空迭代器；
   *   <li>收集这些快照的 id 集合，并从各快照取数据 manifest，按 manifest 的 snapshotId 过滤只保留属于本区间的 manifest；
   *   <li>构造 {@link ManifestGroup}，配置 specs、列选择、数据过滤、entry 过滤（仅本区间快照）、 忽略 EXISTING；按需忽略残留、按需用
   *       executor 并行；
   *   <li>用 {@link CreateDataFileChangeTasks} 把 manifest entry 转为 {@link ChangelogScanTask}。
   * </ol>
   *
   * @param fromSnapshotIdExclusive 起始快照（不含），可为 null
   * @param toSnapshotIdInclusive 结束快照（含）
   * @return 变更日志扫描任务的可关闭迭代器
   */
  @Override
  protected CloseableIterable<ChangelogScanTask> doPlanFiles(
      Long fromSnapshotIdExclusive, long toSnapshotIdInclusive) {

    Deque<Snapshot> changelogSnapshots =
        orderedChangelogSnapshots(fromSnapshotIdExclusive, toSnapshotIdInclusive);

    if (changelogSnapshots.isEmpty()) {
      return CloseableIterable.empty();
    }

    Set<Long> changelogSnapshotIds = toSnapshotIds(changelogSnapshots);

    Set<ManifestFile> newDataManifests =
        FluentIterable.from(changelogSnapshots)
            .transformAndConcat(snapshot -> snapshot.dataManifests(table().io()))
            .filter(manifest -> changelogSnapshotIds.contains(manifest.snapshotId()))
            .toSet();

    ManifestGroup manifestGroup =
        new ManifestGroup(table().io(), newDataManifests, ImmutableList.of())
            .specsById(table().specs())
            .caseSensitive(isCaseSensitive())
            .select(scanColumns())
            .filterData(filter())
            .filterManifestEntries(entry -> changelogSnapshotIds.contains(entry.snapshotId()))
            .ignoreExisting();

    if (shouldIgnoreResiduals()) {
      manifestGroup = manifestGroup.ignoreResiduals();
    }

    if (newDataManifests.size() > 1 && shouldPlanWithExecutor()) {
      manifestGroup = manifestGroup.planWith(planExecutor());
    }

    return manifestGroup.plan(new CreateDataFileChangeTasks(changelogSnapshots));
  }

  /**
   * 将文件级任务聚合为任务组（split）。
   *
   * <p>逻辑：先 {@link #planFiles()} 得到任务，再按 split 大小、回看窗口、打开文件成本 委托 {@link
   * TableScanUtil#planTaskGroups} 聚合。
   *
   * @return 扫描任务组的可关闭迭代器
   */
  @Override
  public CloseableIterable<ScanTaskGroup<ChangelogScanTask>> planTasks() {
    return TableScanUtil.planTaskGroups(
        planFiles(), targetSplitSize(), splitLookback(), splitOpenFileCost());
  }

  /**
   * 构造区间内有序的变更快照集合（从旧到新）。
   *
   * <p>逻辑：用 {@link SnapshotUtil#ancestorsBetween} 取区间祖先链，跳过 REPLACE 操作； 若快照含删除 manifest
   * 则抛异常（当前变更日志不支持 delete files）；通过 addFirst 把祖先链（新→旧）反转为旧→新顺序，序号由此顺序决定。
   *
   * @param fromIdExcl 起始快照（不含），可为 null
   * @param toIdIncl 结束快照（含）
   * @return 从旧到新排列的快照双端队列
   * @throws UnsupportedOperationException 若区间内快照含删除文件
   */
  // builds a collection of changelog snapshots (oldest to newest)
  // the order of the snapshots is important as it is used to determine change ordinals
  private Deque<Snapshot> orderedChangelogSnapshots(Long fromIdExcl, long toIdIncl) {
    Deque<Snapshot> changelogSnapshots = new ArrayDeque<>();

    for (Snapshot snapshot : SnapshotUtil.ancestorsBetween(table(), toIdIncl, fromIdExcl)) {
      if (!snapshot.operation().equals(DataOperations.REPLACE)) {
        if (snapshot.deleteManifests(table().io()).size() > 0) {
          throw new UnsupportedOperationException(
              "Delete files are currently not supported in changelog scans");
        }

        changelogSnapshots.addFirst(snapshot);
      }
    }

    return changelogSnapshots;
  }

  /**
   * 把快照集合转换为快照 id 集合。
   *
   * @param snapshots 快照集合
   * @return 快照 id 集合
   */
  private Set<Long> toSnapshotIds(Collection<Snapshot> snapshots) {
    return snapshots.stream().map(Snapshot::snapshotId).collect(Collectors.toSet());
  }

  /**
   * 为有序快照集合计算每个快照的变更序号（ordinal）。
   *
   * <p>逻辑：按从旧到新顺序依次赋 0,1,2...，建立 snapshotId → ordinal 映射。
   *
   * @param snapshots 从旧到新排列的快照双端队列
   * @return snapshotId 到 ordinal 的映射
   */
  private static Map<Long, Integer> computeSnapshotOrdinals(Deque<Snapshot> snapshots) {
    Map<Long, Integer> snapshotOrdinals = Maps.newHashMap();

    int ordinal = 0;

    for (Snapshot snapshot : snapshots) {
      snapshotOrdinals.put(snapshot.snapshotId(), ordinal++);
    }

    return snapshotOrdinals;
  }

  /**
   * 把 manifest entry 转换为变更日志扫描任务的函数。
   *
   * <p>设计意图：作为 {@link ManifestGroup#plan} 的回调，在每个 manifest entry 上构造 对应的 {@link
   * ChangelogScanTask}（ADDED→新增行任务，DELETED→删除数据文件任务）。
   */
  private static class CreateDataFileChangeTasks implements CreateTasksFunction<ChangelogScanTask> {
    private static final DeleteFile[] NO_DELETES = new DeleteFile[0];

    private final Map<Long, Integer> snapshotOrdinals;

    /**
     * 构造函数，预计算各快照的变更序号。
     *
     * @param snapshots 有序快照集合
     */
    CreateDataFileChangeTasks(Deque<Snapshot> snapshots) {
      this.snapshotOrdinals = computeSnapshotOrdinals(snapshots);
    }

    /**
     * 把 manifest entry 流转换为变更日志任务流。
     *
     * <p>逻辑：对每个 entry 取其提交快照 id 与对应 ordinal，按 entry 状态分支： ADDED 构造 {@link
     * BaseAddedRowsScanTask}，DELETED 构造 {@link BaseDeletedDataFileScanTask}， 其它状态抛 {@link
     * IllegalArgumentException}。删除文件数组固定为空（当前不支持）。
     *
     * @param entries manifest entry 可关闭迭代器
     * @param context 任务上下文（schema/spec/残留等）
     * @return 变更日志任务的可关闭迭代器
     */
    @Override
    public CloseableIterable<ChangelogScanTask> apply(
        CloseableIterable<ManifestEntry<DataFile>> entries, TaskContext context) {

      return CloseableIterable.transform(
          entries,
          entry -> {
            long commitSnapshotId = entry.snapshotId();
            int changeOrdinal = snapshotOrdinals.get(commitSnapshotId);
            DataFile dataFile = entry.file().copy(context.shouldKeepStats());

            switch (entry.status()) {
              case ADDED:
                return new BaseAddedRowsScanTask(
                    changeOrdinal,
                    commitSnapshotId,
                    dataFile,
                    NO_DELETES,
                    context.schemaAsString(),
                    context.specAsString(),
                    context.residuals());

              case DELETED:
                return new BaseDeletedDataFileScanTask(
                    changeOrdinal,
                    commitSnapshotId,
                    dataFile,
                    NO_DELETES,
                    context.schemaAsString(),
                    context.specAsString(),
                    context.residuals());

              default:
                throw new IllegalArgumentException("Unexpected entry status: " + entry.status());
            }
          });
    }
  }
}
