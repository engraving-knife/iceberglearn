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
 * 元数据表：将当前快照的数据文件以行形式暴露。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：作为 {@link Table} 实现，把当前快照中的数据文件（data files）展示为元数据表行， 便于引擎/工具以查询方式检视数据文件构成。仅覆盖当前快照，不含历史文件。
 *
 * <p>设计意图：只读视图，复用 {@link BaseFilesTable} 通用骨架。与 {@link AllDataFilesTable} 不同，本表只看当前快照的数据 manifest。
 *
 * <p>上下游关系：由 {@link MetadataTableUtils} 依据表名后缀创建。
 */
public class DataFilesTable extends BaseFilesTable {

  /** 构造名为 {@code &lt;table&gt;.data_files} 的数据文件元数据表。 */
  DataFilesTable(Table table) {
    this(table, table.name() + ".data_files");
  }

  /**
   * 指定名称构造数据文件元数据表。
   *
   * @param table 底层物理表
   * @param name 元数据表名称
   */
  DataFilesTable(Table table, String name) {
    super(table, name);
  }

  /** 创建针对本元数据表的扫描器。 */
  @Override
  public TableScan newScan() {
    return new DataFilesTableScan(table(), schema());
  }

  /** 返回本元数据表类型枚举值。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.DATA_FILES;
  }

  /**
   * 数据文件元数据表的扫描器：仅覆盖当前快照的数据 manifest。
   *
   * <p>设计意图：与 {@link AllDataFilesTable.AllDataFilesTableScan} 不同，本扫描器只取当前快照 的数据 manifest，不遍历全部快照。
   */
  public static class DataFilesTableScan extends BaseFilesTableScan {

    /** 构造扫描器。 */
    DataFilesTableScan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.DATA_FILES);
    }

    /**
     * 带扫描上下文的构造器，用于 refine 时复制扫描器。
     *
     * @param table 底层物理表
     * @param schema 输出 schema
     * @param context 扫描上下文
     */
    DataFilesTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.DATA_FILES, context);
    }

    /** 基于新上下文创建细化扫描器（不可变复制模式）。 */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new DataFilesTableScan(table, schema, context);
    }

    /**
     * 返回当前快照的数据 manifest。
     *
     * <p>逻辑：直接取当前快照的 dataManifests，包成 noop-close 迭代器。
     */
    @Override
    protected CloseableIterable<ManifestFile> manifests() {
      return CloseableIterable.withNoopClose(snapshot().dataManifests(table().io()));
    }
  }
}
