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
 * 将表的删除文件（delete files）以行形式暴露的元数据表实现。
 *
 * <p>所属模块：iceberg-core（metadata 表，删除文件元数据视图）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为 {@link Table} 的元数据视图，把表中所有 delete file（position delete 与 equality
 *       delete）以行为单位呈现，便于查询分析删除文件分布。
 *   <li>提供专属的 {@link DeleteFilesTableScan} 扫描器，仅扫描 delete manifest。
 * </ul>
 *
 * <p>设计意图：继承 {@link BaseFilesTable} 复用文件元数据表的通用结构（schema、task 规划等）， 仅在扫描器层面区分 data manifest 与
 * delete manifest，避免重复实现。
 *
 * <p>上下游关系：上游由 {@link MetadataTableUtils} 按表名创建；下游扫描器读取 {@link
 * org.apache.iceberg.Snapshot#deleteManifests} 获取删除文件清单。
 */
public class DeleteFilesTable extends BaseFilesTable {

  /**
   * 以源表构造删除文件元数据表，表名自动追加 {@code .delete_files} 后缀。
   *
   * @param table 源表
   */
  DeleteFilesTable(Table table) {
    this(table, table.name() + ".delete_files");
  }

  /**
   * 以源表与指定名称构造删除文件元数据表。
   *
   * @param table 源表
   * @param name 元数据表名
   */
  DeleteFilesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 创建新的删除文件表扫描器。
   *
   * @return 删除文件表扫描器
   */
  @Override
  public TableScan newScan() {
    return new DeleteFilesTableScan(table(), schema());
  }

  /** 返回该元数据表的类型标识。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.DELETE_FILES;
  }

  /**
   * 删除文件元数据表的扫描器，仅扫描 delete manifest。
   *
   * <p>所属模块：iceberg-core（metadata 表扫描器）。继承 {@link BaseFilesTableScan}， 复用文件元数据扫描的过滤、投影与 task
   * 规划逻辑，仅在 manifest 来源上限定为 delete manifest。
   */
  public static class DeleteFilesTableScan extends BaseFilesTableScan {

    /**
     * 以源表与 schema 构造删除文件扫描器。
     *
     * @param table 源表
     * @param schema 扫描 schema
     */
    DeleteFilesTableScan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.DELETE_FILES);
    }

    /**
     * 以源表、schema 与扫描上下文构造删除文件扫描器，用于精化扫描。
     *
     * @param table 源表
     * @param schema 扫描 schema
     * @param context 扫描上下文（过滤、投影等）
     */
    DeleteFilesTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.DELETE_FILES, context);
    }

    /**
     * 基于精化后的 schema 与上下文创建新的扫描器实例。
     *
     * @param table 源表
     * @param schema 精化后的 schema
     * @param context 精化后的扫描上下文
     * @return 新的删除文件扫描器
     */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new DeleteFilesTableScan(table, schema, context);
    }

    /**
     * 返回当前快照的 delete manifest 清单（无操作关闭的 {@link CloseableIterable}）。
     *
     * @return delete manifest 清单
     */
    @Override
    protected CloseableIterable<ManifestFile> manifests() {
      return CloseableIterable.withNoopClose(snapshot().deleteManifests(table().io()));
    }
  }
}
