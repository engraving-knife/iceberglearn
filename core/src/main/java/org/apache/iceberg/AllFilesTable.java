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

/**
 * 元数据表：将表中所有"有效文件"以行形式暴露出来的 {@link Table} 实现。
 *
 * <p>所属模块：iceberg-core，作为元数据表（metadata table）体系的一员，位于 core 模块根包， 对外提供面向文件级别的审计与诊断视图。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>聚合当前表所有快照中"可读"（valid/未过期）的数据与删除文件，输出为可扫描行集。
 *   <li>区别于 {@link AllDataFilesTable} 等只看单个快照的视图，本表跨全部快照汇总文件， 因此可能返回重复行（同一文件被多个快照引用）。
 * </ul>
 *
 * <p>设计意图：便于运维/审计场景下检索表中曾存在或现存的所有文件清单，无需逐快照扫描。 与 {@link BaseFilesTable} 共享文件扫描基础设施，子类仅决定 manifest
 * 收集策略。
 *
 * <p>上下游关系：继承 {@link BaseFilesTable}，被 {@link MetadataTableUtils} 在解析 {@code .all_files}
 * 后缀表名时创建；扫描结果由引擎层（Spark/Flink 等）消费。
 */
public class AllFilesTable extends BaseFilesTable {

  /**
   * 构造名为 {@code <表名>.all_files} 的全文件元数据表。
   *
   * @param table 底层原始表
   */
  AllFilesTable(Table table) {
    this(table, table.name() + ".all_files");
  }

  /**
   * 构造指定名称的全文件元数据表。
   *
   * @param table 底层原始表
   * @param name 元数据表名称
   */
  AllFilesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 创建新的扫描实例。
   *
   * @return 用于扫描全文件视图的 {@link AllFilesTableScan}
   */
  @Override
  public TableScan newScan() {
    return new AllFilesTableScan(table(), schema());
  }

  /**
   * 返回本元数据表类型标识，用于序列化与日志区分。
   *
   * @return {@link MetadataTableType#ALL_FILES}
   */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.ALL_FILES;
  }

  /**
   * 全文件元数据表的扫描实现：收集所有可达快照中的 manifest，再读取其中文件条目。
   *
   * <p>设计意图：与单快照扫描共享 {@link BaseAllFilesTableScan} 的文件读取与投影逻辑， 仅通过覆写 {@link #manifests()} 改变
   * manifest 来源为"所有快照的 manifest 并集"。
   */
  public static class AllFilesTableScan extends BaseAllFilesTableScan {

    /**
     * 构造扫描实例。
     *
     * @param table 底层原始表
     * @param schema 输出 schema
     */
    AllFilesTableScan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.ALL_FILES);
    }

    /**
     * 内部构造器：用于在扫描细化（refined scan）时携带上下文重建实例。
     *
     * @param table 底层原始表
     * @param schema 输出 schema
     * @param context 扫描上下文（含过滤、分片等配置）
     */
    private AllFilesTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.ALL_FILES, context);
    }

    /**
     * 基于新的 schema 与上下文生成细化扫描，保证链式配置可传递。
     *
     * @param table 底层原始表
     * @param schema 输出 schema
     * @param context 扫描上下文
     * @return 新的 {@link AllFilesTableScan}
     */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new AllFilesTableScan(table, schema, context);
    }

    /**
     * 收集所有可达快照的 manifest 文件。
     *
     * <p>逻辑：委托 {@link #reachableManifests(java.util.function.Function)}，对每个快照 调用 {@link
     * Snapshot#allManifests(org.apache.iceberg.io.FileIO)} 取其全部 manifest （含数据与删除
     * manifest），最终合并为去重后的可迭代集合。
     *
     * @return 所有可达快照的 manifest 集合
     */
    @Override
    protected CloseableIterable<ManifestFile> manifests() {
      return reachableManifests(snapshot -> snapshot.allManifests(table().io()));
    }
  }
}
