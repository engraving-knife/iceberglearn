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

import java.util.List;
import java.util.Set;
import org.apache.iceberg.events.IncrementalScanEvent;
import org.apache.iceberg.events.Listeners;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.FluentIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.SnapshotUtil;

/**
 * 增量数据表扫描：在快照区间（fromSnapshotId, toSnapshotId] 内扫描新增的数据文件。
 *
 * <p>所属模块：iceberg-core（扫描实现层，扩展 {@link DataTableScan}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>基于快照区间产出增量数据扫描任务（仅含 ADDED 状态的数据文件）；
 *   <li>禁止使用 asOfTime/useRef/useSnapshot（因已配置为增量模式）；
 *   <li>支持通过 appendsBetween/appendsAfter 缩小区间。
 * </ul>
 *
 * <p>设计意图：增量扫描只支持 APPEND 操作的快照；若区间内含 OVERWRITE 操作则抛异常。 通过过滤 manifest entry 的 snapshotId 与
 * status==ADDED 来精确定位增量文件。
 *
 * <p>上下游关系：由 {@link TableScan#appendsBetween} 或 {@link TableScan#appendsAfter} 构造； 底层使用 {@link
 * ManifestGroup} 做实际扫描。
 */
class IncrementalDataTableScan extends DataTableScan {
  /**
   * 构造增量数据表扫描。
   *
   * <p>逻辑：调用父类构造（清除 useSnapshotId），并校验 from/to 快照 id 合法性。
   *
   * @param table 表
   * @param schema 扫描 schema
   * @param context 扫描上下文
   */
  IncrementalDataTableScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context.useSnapshotId(null));
    validateSnapshotIds(table, context.fromSnapshotId(), context.toSnapshotId());
  }

  /** 增量扫描不支持 asOfTime。 */
  @Override
  public TableScan asOfTime(long timestampMillis) {
    throw new UnsupportedOperationException(
        String.format(
            "Cannot scan table as of time %s: configured for incremental data in snapshots (%s, %s]",
            timestampMillis, context().fromSnapshotId(), context().toSnapshotId()));
  }

  /** 增量扫描不支持 useRef。 */
  @Override
  public TableScan useRef(String ref) {
    throw new UnsupportedOperationException(
        String.format(
            "Cannot scan table using ref %s: configured for incremental data in snapshots (%s, %s]",
            ref, context().fromSnapshotId(), context().toSnapshotId()));
  }

  /** 增量扫描不支持 useSnapshot。 */
  @Override
  public TableScan useSnapshot(long scanSnapshotId) {
    throw new UnsupportedOperationException(
        String.format(
            "Cannot scan table using scan snapshot id %s: configured for incremental data in snapshots (%s, %s]",
            scanSnapshotId, context().fromSnapshotId(), context().toSnapshotId()));
  }

  /**
   * 缩小增量区间为 (fromSnapshotId, toSnapshotId]。
   *
   * <p>逻辑：校验新区间在原区间范围内，然后构造新的 IncrementalDataTableScan。
   *
   * @param fromSnapshotId 新的起始快照（不含）
   * @param toSnapshotId 新的结束快照（含）
   * @return 新的增量扫描
   */
  @Override
  public TableScan appendsBetween(long fromSnapshotId, long toSnapshotId) {
    validateSnapshotIdsRefinement(fromSnapshotId, toSnapshotId);
    return new IncrementalDataTableScan(
        table(),
        schema(),
        context().fromSnapshotIdExclusive(fromSnapshotId).toSnapshotId(toSnapshotId));
  }

  /**
   * 扫描某快照之后的所有追加（appends）。
   *
   * @param newFromSnapshotId 起始快照（不含）
   * @return 新的增量扫描，区间为 (newFromSnapshotId, currentSnapshot]
   */
  @Override
  public TableScan appendsAfter(long newFromSnapshotId) {
    final Snapshot currentSnapshot = table().currentSnapshot();
    Preconditions.checkState(
        currentSnapshot != null,
        "Cannot scan appends after %s, there is no current snapshot",
        newFromSnapshotId);
    return appendsBetween(newFromSnapshotId, currentSnapshot.snapshotId());
  }

  /**
   * 执行增量扫描文件计划。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>取区间内所有 APPEND 快照及其 id 集合；
   *   <li>收集这些快照的 data manifests，过滤出 snapshotId 在集合中的；
   *   <li>构造 {@link ManifestGroup}，过滤出 status==ADDED 且 snapshotId 在集合中的 entry；
   *   <li>通知 {@link IncrementalScanEvent}；按需配置并行执行器；调用 planFiles。
   * </ol>
   *
   * @return 增量数据扫描任务集合
   */
  @Override
  public CloseableIterable<FileScanTask> planFiles() {
    Long fromSnapshotId = context().fromSnapshotId();
    Long toSnapshotId = context().toSnapshotId();

    List<Snapshot> snapshots = snapshotsWithin(table(), fromSnapshotId, toSnapshotId);
    Set<Long> snapshotIds = Sets.newHashSet(Iterables.transform(snapshots, Snapshot::snapshotId));
    Set<ManifestFile> manifests =
        FluentIterable.from(snapshots)
            .transformAndConcat(snapshot -> snapshot.dataManifests(table().io()))
            .filter(manifestFile -> snapshotIds.contains(manifestFile.snapshotId()))
            .toSet();

    ManifestGroup manifestGroup =
        new ManifestGroup(table().io(), manifests)
            .caseSensitive(isCaseSensitive())
            .select(scanColumns())
            .filterData(filter())
            .filterManifestEntries(
                manifestEntry ->
                    snapshotIds.contains(manifestEntry.snapshotId())
                        && manifestEntry.status() == ManifestEntry.Status.ADDED)
            .specsById(table().specs())
            .ignoreDeleted();

    if (shouldIgnoreResiduals()) {
      manifestGroup = manifestGroup.ignoreResiduals();
    }

    Listeners.notifyAll(
        new IncrementalScanEvent(
            table().name(), fromSnapshotId, toSnapshotId, filter(), schema(), false));

    if (manifests.size() > 1 && shouldPlanWithExecutor()) {
      manifestGroup = manifestGroup.planWith(planExecutor());
    }

    return manifestGroup.planFiles();
  }

  /**
   * 基于新的上下文派生新的扫描实例。
   *
   * @param table 表
   * @param schema schema
   * @param context 新的扫描上下文
   * @return 新的 {@link IncrementalDataTableScan}
   */
  @Override
  @SuppressWarnings("checkstyle:HiddenField")
  protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
    return new IncrementalDataTableScan(table, schema, context);
  }

  /**
   * 收集区间内所有 APPEND 快照；若含 OVERWRITE 则抛异常。
   *
   * @param table 表
   * @param fromSnapshotId 起始快照（不含）
   * @param toSnapshotId 结束快照（含）
   * @return 区间内的 APPEND 快照列表
   * @throws UnsupportedOperationException 区间内含 OVERWRITE 操作
   */
  private static List<Snapshot> snapshotsWithin(
      Table table, long fromSnapshotId, long toSnapshotId) {
    List<Snapshot> snapshots = Lists.newArrayList();
    for (Snapshot snapshot :
        SnapshotUtil.ancestorsBetween(toSnapshotId, fromSnapshotId, table::snapshot)) {
      // for now, incremental scan supports only appends
      if (snapshot.operation().equals(DataOperations.APPEND)) {
        snapshots.add(snapshot);
      } else if (snapshot.operation().equals(DataOperations.OVERWRITE)) {
        throw new UnsupportedOperationException(
            String.format(
                "Found %s operation, cannot support incremental data in snapshots (%s, %s]",
                DataOperations.OVERWRITE, fromSnapshotId, toSnapshotId));
      }
    }
    return snapshots;
  }

  /**
   * 校验新区间在原区间范围内（用于 appendsBetween 细化）。
   *
   * @param newFromSnapshotId 新起始快照
   * @param newToSnapshotId 新结束快照
   */
  private void validateSnapshotIdsRefinement(long newFromSnapshotId, long newToSnapshotId) {
    Set<Long> snapshotIdsRange =
        Sets.newHashSet(
            SnapshotUtil.ancestorIdsBetween(
                context().toSnapshotId(), context().fromSnapshotId(), table()::snapshot));
    // since snapshotIdsBetween return ids in range (fromSnapshotId, toSnapshotId]
    snapshotIdsRange.add(context().fromSnapshotId());
    Preconditions.checkArgument(
        snapshotIdsRange.contains(newFromSnapshotId),
        "from snapshot id %s not in existing snapshot ids range (%s, %s]",
        newFromSnapshotId,
        context().fromSnapshotId(),
        newToSnapshotId);
    Preconditions.checkArgument(
        snapshotIdsRange.contains(newToSnapshotId),
        "to snapshot id %s not in existing snapshot ids range (%s, %s]",
        newToSnapshotId,
        context().fromSnapshotId(),
        context().toSnapshotId());
  }

  /**
   * 校验起始/结束快照 id 的合法性：不相等、均存在、from 是 to 的祖先。
   *
   * @param table 表
   * @param fromSnapshotId 起始快照
   * @param toSnapshotId 结束快照
   */
  private static void validateSnapshotIds(Table table, long fromSnapshotId, long toSnapshotId) {
    Preconditions.checkArgument(
        fromSnapshotId != toSnapshotId, "from and to snapshot ids cannot be the same");
    Preconditions.checkArgument(
        table.snapshot(fromSnapshotId) != null, "from snapshot %s does not exist", fromSnapshotId);
    Preconditions.checkArgument(
        table.snapshot(toSnapshotId) != null, "to snapshot %s does not exist", toSnapshotId);
    Preconditions.checkArgument(
        SnapshotUtil.isAncestorOf(table, toSnapshotId, fromSnapshotId),
        "from snapshot %s is not an ancestor of to snapshot  %s",
        fromSnapshotId,
        toSnapshotId);
  }
}
