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
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 数据表的默认扫描实现：基于当前快照的数据/删除 manifest 规划文件扫描任务。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 {@link TableScan} 在数据表场景下的具体实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从当前快照读取数据 manifest 与删除 manifest，构建 {@link ManifestGroup} 规划 {@link FileScanTask}。
 *   <li>支持增量扫描（appendsAfter/appendsBetween），委托 {@link IncrementalDataTableScan}。
 *   <li>按扫描上下文应用列投影、过滤、大小写敏感性、残留过滤、并行规划等配置。
 * </ul>
 *
 * <p>设计意图：继承 {@link BaseTableScan} 复用公共扫描逻辑；{@link #useSnapshotSchema()} 返回 true， 使扫描使用快照对应的 schema
 * 而非当前表 schema，保证快照读取的 schema 一致性。
 *
 * <p>上下游关系：被 {@link BaseTable#newScan()} 创建；上游被引擎扫描入口调用， 下游通过 {@link ManifestGroup} 读取 manifest 文件。
 */
public class DataTableScan extends BaseTableScan {
  /**
   * 构造方法。
   *
   * @param table 目标数据表
   * @param schema 扫描 schema
   * @param context 扫描上下文
   */
  protected DataTableScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
  }

  /**
   * 创建增量扫描：扫描两个快照之间新增的文件。
   *
   * @param fromSnapshotId 起始快照 id（不含）
   * @param toSnapshotId 结束快照 id（含）
   * @return 增量扫描 {@link IncrementalDataTableScan}
   */
  @Override
  public TableScan appendsBetween(long fromSnapshotId, long toSnapshotId) {
    Preconditions.checkState(
        snapshotId() == null,
        "Cannot enable incremental scan, scan-snapshot set to id=%s",
        snapshotId());
    return new IncrementalDataTableScan(
        table(),
        schema(),
        context().fromSnapshotIdExclusive(fromSnapshotId).toSnapshotId(toSnapshotId));
  }

  /**
   * 创建增量扫描：扫描从指定快照到当前快照之间新增的文件。
   *
   * @param fromSnapshotId 起始快照 id（不含）
   * @return 增量扫描
   */
  @Override
  public TableScan appendsAfter(long fromSnapshotId) {
    Snapshot currentSnapshot = table().currentSnapshot();
    Preconditions.checkState(
        currentSnapshot != null,
        "Cannot scan appends after %s, there is no current snapshot",
        fromSnapshotId);
    return appendsBetween(fromSnapshotId, currentSnapshot.snapshotId());
  }

  /** 数据表扫描使用快照对应的 schema，保证读取历史快照时 schema 一致。 */
  @Override
  protected boolean useSnapshotSchema() {
    return true;
  }

  /** fluent 方法支持：基于新参数构造新的扫描实例。 */
  @Override
  protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
    return new DataTableScan(table, schema, context);
  }

  /**
   * 规划文件扫描任务。
   *
   * <p>逻辑：取当前快照的数据 manifest 与删除 manifest；构建 {@link ManifestGroup}，
   * 应用列投影、过滤、specsById、metrics、ignoreDeleted 等配置；按需启用忽略残留与并行规划 （仅当数据或删除 manifest 数大于 1 时使用线程池）；最终返回
   * {@link FileScanTask} 迭代器。
   *
   * @return 文件扫描任务流
   */
  @Override
  public CloseableIterable<FileScanTask> doPlanFiles() {
    Snapshot snapshot = snapshot();

    FileIO io = table().io();
    List<ManifestFile> dataManifests = snapshot.dataManifests(io);
    List<ManifestFile> deleteManifests = snapshot.deleteManifests(io);
    scanMetrics().totalDataManifests().increment((long) dataManifests.size());
    scanMetrics().totalDeleteManifests().increment((long) deleteManifests.size());
    ManifestGroup manifestGroup =
        new ManifestGroup(io, dataManifests, deleteManifests)
            .caseSensitive(isCaseSensitive())
            .select(scanColumns())
            .filterData(filter())
            .specsById(table().specs())
            .scanMetrics(scanMetrics())
            .ignoreDeleted();

    if (shouldIgnoreResiduals()) {
      manifestGroup = manifestGroup.ignoreResiduals();
    }

    if (shouldPlanWithExecutor() && (dataManifests.size() > 1 || deleteManifests.size() > 1)) {
      manifestGroup = manifestGroup.planWith(planExecutor());
    }

    return manifestGroup.planFiles();
  }
}
