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
 * 元数据表实现：将表中所有"有效删除文件"以行的形式暴露出来。
 *
 * <p>所属模块：iceberg-core（表扫描与元数据表实现层，位于 api 之下，提供扫描/事务的具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为 {@link Table} 的元数据视图，把所有当前快照链上仍可读的 delete 文件汇总成可扫描的行集。
 *   <li>"有效"指从任意当前跟踪的快照可读，即未被过期清理、未被引用移除的删除文件。
 * </ul>
 *
 * <p>设计意图：作为只读的元数据表，用于审计、排查 delete 文件分布，结果可能重复（同一 delete 文件 出现在多个快照链上即会重复返回）。
 *
 * <p>上下游关系：继承自 {@link BaseFilesTable}，由元数据表工厂在请求 "&lt;表名&gt;.all_delete_files" 时构造；扫描时通过 {@link
 * AllDeleteFilesTableScan} 收集所有可达快照的 delete manifests。
 */
public class AllDeleteFilesTable extends BaseFilesTable {

  /**
   * 构造一个名为 "&lt;表名&gt;.all_delete_files" 的全量删除文件元数据表。
   *
   * @param table 被包装的底层 Iceberg 表
   */
  AllDeleteFilesTable(Table table) {
    this(table, table.name() + ".all_delete_files");
  }

  /**
   * 构造指定名称的全量删除文件元数据表。
   *
   * @param table 被包装的底层 Iceberg 表
   * @param name 元数据表名称
   */
  AllDeleteFilesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 创建一个新的扫描实例，用于遍历所有有效删除文件。
   *
   * @return 当前表的 {@link AllDeleteFilesTableScan}
   */
  @Override
  public TableScan newScan() {
    return new AllDeleteFilesTableScan(table(), schema());
  }

  /** 返回该元数据表的类型标识，用于元数据表注册与路由。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.ALL_DELETE_FILES;
  }

  /**
   * 全量删除文件元数据表的扫描实现：收集所有可达快照链上的 delete manifests。
   *
   * <p>设计意图：复用 {@link BaseAllFilesTableScan} 的通用"收集所有快照可达 manifest"逻辑， 仅在 {@link #manifests()} 中通过
   * {@link Snapshot#deleteManifests} 取出 delete manifests， 避免重复实现可达性遍历。
   */
  public static class AllDeleteFilesTableScan extends BaseAllFilesTableScan {

    /**
     * 构造扫描实例。
     *
     * @param table 被扫描的底层表
     * @param schema 扫描输出 schema
     */
    AllDeleteFilesTableScan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.ALL_DELETE_FILES);
    }

    /**
     * 内部构造器：携带扫描上下文（过滤、投影等）创建扫描实例，用于 refined scan 链式构造。
     *
     * @param table 被扫描的底层表
     * @param schema 扫描输出 schema
     * @param context 扫描上下文（含过滤、分支、快照 id 等）
     */
    private AllDeleteFilesTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.ALL_DELETE_FILES, context);
    }

    /**
     * 基于新的上下文派生出一个新的扫描实例，保持扫描类型一致。
     *
     * @param table 表
     * @param schema schema
     * @param context 新的扫描上下文
     * @return 新的 {@link AllDeleteFilesTableScan}
     */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new AllDeleteFilesTableScan(table, schema, context);
    }

    /**
     * 返回当前表所有可达快照的 delete manifests。
     *
     * <p>逻辑：通过 {@link #reachableManifests} 遍历所有可达快照，对每个快照调用 {@code
     * snapshot.deleteManifests(table().io())} 收集其 delete manifests，并去重合并。
     *
     * @return 所有可达快照的 delete manifests
     */
    @Override
    protected CloseableIterable<ManifestFile> manifests() {
      return reachableManifests(snapshot -> snapshot.deleteManifests(table().io()));
    }
  }
}
