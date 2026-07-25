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
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.FluentIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.TableScanUtil;

/**
 * 增量 append 扫描实现：在指定快照区间内，仅返回 append 操作产生的数据文件扫描任务。
 *
 * <p>所属模块：iceberg-core（扫描计划核心实现层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>基于起始/结束快照区间，定位所有 append 类型的快照。
 *   <li>从这些快照的 manifest 中筛选 status=ADDED 的数据文件条目，组装为 {@link FileScanTask}。
 *   <li>把扫描结果进一步切分、合并为 {@link CombinedScanTask}，供引擎并行下发。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>增量扫描只关心 append，避免处理 overwrite/delete 等带来的复杂语义，提高增量拉取效率。
 *   <li>通过 {@link ManifestGroup} 复用 manifest 读取与过滤能力，统一了 planFiles 的执行路径。
 *   <li>支持 manifest 数量较多时使用计划执行器并行读取，提升大表增量扫描性能。
 * </ul>
 *
 * <p>上下游关系：继承 {@link BaseIncrementalScan}；被上层调用方（如引擎集成模块）通过 {@link IncrementalAppendScan} 接口使用。
 */
class BaseIncrementalAppendScan
    extends BaseIncrementalScan<IncrementalAppendScan, FileScanTask, CombinedScanTask>
    implements IncrementalAppendScan {

  /**
   * 构造增量 append 扫描器。
   *
   * @param table 目标表
   * @param schema 读取使用的 schema
   * @param context 扫描上下文（含起始/结束快照、过滤条件等）
   */
  BaseIncrementalAppendScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
  }

  /**
   * 创建一个用于"细化"（refined）的新扫描实例，常用于投影 schema 或下推过滤后重新计划。
   *
   * @param newTable 新表对象
   * @param newSchema 新 schema
   * @param newContext 新的扫描上下文
   * @return 新的 {@link IncrementalAppendScan} 实例
   */
  @Override
  protected IncrementalAppendScan newRefinedScan(
      Table newTable, Schema newSchema, TableScanContext newContext) {
    return new BaseIncrementalAppendScan(newTable, newSchema, newContext);
  }

  /**
   * 执行实际的文件计划：在指定快照区间内收集所有 append 快照并提取新增数据文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>调用 {@link #appendsBetween} 获取 (fromSnapshotIdExclusive, toSnapshotIdInclusive] 区间内所有
   *       APPEND 操作的快照；fromSnapshotIdExclusive 为 null 表示从最早快照开始。
   *   <li>若无快照则返回空迭代器。
   *   <li>否则调用 {@link #appendFilesFromSnapshots} 从 manifest 中提取 ADDED 文件。
   * </ol>
   *
   * @param fromSnapshotIdExclusive 起始快照（exclusive），可为 null
   * @param toSnapshotIdInclusive 结束快照（inclusive）
   * @return 文件扫描任务可关闭迭代器
   */
  @Override
  protected CloseableIterable<FileScanTask> doPlanFiles(
      Long fromSnapshotIdExclusive, long toSnapshotIdInclusive) {

    // appendsBetween handles null fromSnapshotId (exclusive) properly
    List<Snapshot> snapshots =
        appendsBetween(table(), fromSnapshotIdExclusive, toSnapshotIdInclusive);
    if (snapshots.isEmpty()) {
      return CloseableIterable.empty();
    }

    return appendFilesFromSnapshots(snapshots);
  }

  /**
   * 计划可下发的合并扫描任务（CombinedScanTask）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>调用 {@link #planFiles()} 得到文件扫描任务。
   *   <li>用 {@link TableScanUtil#splitFiles} 按目标分片大小拆分文件。
   *   <li>用 {@link TableScanUtil#planTasks} 把拆分后的文件按 bin-packing 合并为多个 CombinedScanTask。
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

  /**
   * 从给定快照列表中提取所有 ADDED 状态的数据文件，组装为文件扫描任务。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>收集快照 id 集合，并把这些快照对应的数据 manifest（按 snapshotId 过滤）汇总。
   *   <li>构建 {@link ManifestGroup}，并应用大小写敏感、列裁剪、数据过滤、manifest 条目过滤 （仅保留属于目标快照且 status=ADDED
   *       的条目）等条件。
   *   <li>若忽略 residuals 则设置 ignoreResiduals；若 manifest 多于一个且允许并行，则切换到 计划执行器执行。
   *   <li>调用 {@link ManifestGroup#planFiles()} 返回最终文件任务。
   * </ol>
   *
   * @param snapshots 目标 append 快照列表
   * @return 文件扫描任务可关闭迭代器
   */
  private CloseableIterable<FileScanTask> appendFilesFromSnapshots(List<Snapshot> snapshots) {
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

    if (context().ignoreResiduals()) {
      manifestGroup = manifestGroup.ignoreResiduals();
    }

    if (manifests.size() > 1 && shouldPlanWithExecutor()) {
      manifestGroup = manifestGroup.planWith(planExecutor());
    }

    return manifestGroup.planFiles();
  }

  /**
   * 在指定区间内收集所有 APPEND 操作的快照列表。
   *
   * <p>逻辑：遍历 {@link SnapshotUtil#ancestorsBetween} 返回的祖先快照，过滤出操作类型为 {@link DataOperations#APPEND}
   * 的快照。该方法不做参数校验，由调用方 {@link #planFiles()} 保证。
   *
   * @param table 目标表
   * @param fromSnapshotIdExclusive 起始快照（exclusive），可为 null
   * @param toSnapshotIdInclusive 结束快照（inclusive）
   * @return APPEND 快照列表
   */
  private static List<Snapshot> appendsBetween(
      Table table, Long fromSnapshotIdExclusive, long toSnapshotIdInclusive) {
    List<Snapshot> snapshots = Lists.newArrayList();
    for (Snapshot snapshot :
        SnapshotUtil.ancestorsBetween(
            toSnapshotIdInclusive, fromSnapshotIdExclusive, table::snapshot)) {
      if (snapshot.operation().equals(DataOperations.APPEND)) {
        snapshots.add(snapshot);
      }
    }

    return snapshots;
  }
}
