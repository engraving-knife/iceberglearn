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
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 数据扫描的抽象基类：基于某个快照扫描数据文件（含关联的删除文件），产出 {@link ScanTask}。
 *
 * <p>所属模块：iceberg-core（扫描实现层，扩展 {@link SnapshotScan}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为数据类扫描（如 {@link DataTableScan}）提供通用的 manifest group 构造逻辑；
 *   <li>决定扫描时使用快照对应的 schema（而非当前表 schema），保证时间旅行读取的一致性。
 * </ul>
 *
 * <p>设计意图：把"构造 ManifestGroup 并配置过滤/投影/残留/并行执行"的公共流程抽到基类， 子类只需关注 {@link #doPlanFiles} 的具体产出方式。{@link
 * #newManifestGroup} 提供多个重载， 允许按需选择是否携带删除文件、是否返回列统计。
 *
 * <p>上下游关系：被 {@link DataTableScan}、{@link IncrementalDataTableScan} 等继承； 底层依赖 {@link ManifestGroup}
 * 做实际的 manifest 读取与任务切分。
 *
 * @param <ThisT> 实际扫描类型
 * @param <T> 扫描任务类型
 * @param <G> 扫描任务分组类型
 */
abstract class DataScan<ThisT, T extends ScanTask, G extends ScanTaskGroup<T>>
    extends SnapshotScan<ThisT, T, G> {

  /**
   * 构造数据扫描实例。
   *
   * @param table 表
   * @param schema 扫描 schema
   * @param context 扫描上下文
   */
  protected DataScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
  }

  /**
   * 是否使用快照对应的 schema 进行扫描。
   *
   * <p>设计要点：数据扫描需要时间旅行一致性，故返回 true，即使用目标快照时的 schema 而非 当前表 schema。
   *
   * @return 固定返回 true
   */
  @Override
  protected boolean useSnapshotSchema() {
    return true;
  }

  /**
   * 构造 manifest group：同时包含数据文件与删除文件，列统计按上下文配置决定。
   *
   * @param dataManifests 数据文件 manifests
   * @param deleteManifests 删除文件 manifests
   * @return 配置好的 {@link ManifestGroup}
   */
  protected ManifestGroup newManifestGroup(
      List<ManifestFile> dataManifests, List<ManifestFile> deleteManifests) {
    return newManifestGroup(dataManifests, deleteManifests, context().returnColumnStats());
  }

  /**
   * 构造 manifest group：仅含数据文件，按参数决定是否携带列统计。
   *
   * @param dataManifests 数据文件 manifests
   * @param withColumnStats 是否返回列统计
   * @return 配置好的 {@link ManifestGroup}
   */
  protected ManifestGroup newManifestGroup(
      List<ManifestFile> dataManifests, boolean withColumnStats) {
    return newManifestGroup(dataManifests, ImmutableList.of(), withColumnStats);
  }

  /**
   * 构造 manifest group 的核心方法：配置大小写敏感性、列选择、过滤、残留、并行执行等。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>创建 {@link ManifestGroup} 并设置大小写敏感性、列投影（含/不含统计）、行过滤、 specsById、scanMetrics、ignoreDeleted；
   *   <li>若应忽略残留表达式，则调用 {@code ignoreResiduals}；
   *   <li>若应使用执行器并行计划，且 manifests 数量大于 1，则配置 planExecutor。
   * </ul>
   *
   * @param dataManifests 数据文件 manifests
   * @param deleteManifests 删除文件 manifests
   * @param withColumnStats 是否返回列统计
   * @return 配置好的 {@link ManifestGroup}
   */
  protected ManifestGroup newManifestGroup(
      List<ManifestFile> dataManifests,
      List<ManifestFile> deleteManifests,
      boolean withColumnStats) {

    ManifestGroup manifestGroup =
        new ManifestGroup(io(), dataManifests, deleteManifests)
            .caseSensitive(isCaseSensitive())
            .select(withColumnStats ? SCAN_WITH_STATS_COLUMNS : SCAN_COLUMNS)
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

    return manifestGroup;
  }
}
