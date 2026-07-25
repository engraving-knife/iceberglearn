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
 * 元数据表：将一张表所有有效数据文件以行形式暴露出来。
 *
 * <p>所属模块：iceberg-core（核心实现模块，位于 iceberg-api 之下，提供元数据表、扫描、提交等具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为 {@link Table} 的实现，把底层表中所有快照里仍可读的数据文件汇聚成一张元数据表。
 *   <li>所谓“有效数据文件”指从当前表追踪的任一快照视角都能读取到的文件。
 *   <li>该表可能返回重复行（同一文件被多个快照引用时不做去重行级展示）。
 * </ul>
 *
 * <p>设计意图：以元数据表形式对外暴露数据文件视图，便于引擎/工具以查询方式检视表的物理文件构成， 而无需直接操作 manifest。本类是只读视图，复用 {@link
 * BaseFilesTable} 通用骨架。
 *
 * <p>上下游关系：由 {@link MetadataTableUtils} 依据表名后缀创建；被引擎层用于"查看所有数据文件"类查询。
 */
public class AllDataFilesTable extends BaseFilesTable {

  /**
   * 构造名为 {@code &lt;table&gt;.all_data_files} 的全量数据文件元数据表。
   *
   * @param table 底层物理表
   */
  AllDataFilesTable(Table table) {
    this(table, table.name() + ".all_data_files");
  }

  /**
   * 指定名称构造全量数据文件元数据表。
   *
   * @param table 底层物理表
   * @param name 元数据表名称
   */
  AllDataFilesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 创建针对本元数据表的扫描器。
   *
   * @return 新的 {@link AllDataFilesTableScan}
   */
  @Override
  public TableScan newScan() {
    return new AllDataFilesTableScan(table(), schema());
  }

  /** 返回本元数据表类型枚举值。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.ALL_DATA_FILES;
  }

  /**
   * 全量数据文件元数据表的扫描器：覆盖所有快照中可达的数据 manifest。
   *
   * <p>设计意图：与仅看当前快照的 {@code DataFilesTable} 扫描器不同，本扫描器需要遍历表的所有快照， 收集它们各自的数据 manifest 集合，再去重后输出文件行。
   */
  public static class AllDataFilesTableScan extends BaseAllFilesTableScan {

    /**
     * 构造扫描器。
     *
     * @param table 底层物理表
     * @param schema 输出 schema
     */
    AllDataFilesTableScan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.ALL_DATA_FILES);
    }

    /**
     * 带扫描上下文的私有构造，用于在 refine 时复制出新扫描器。
     *
     * @param table 底层物理表
     * @param schema 输出 schema
     * @param context 扫描上下文（过滤、列选择等）
     */
    private AllDataFilesTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.ALL_DATA_FILES, context);
    }

    /**
     * 基于新上下文创建细化扫描器（实现不可变扫描器复制模式）。
     *
     * @param table 底层物理表
     * @param schema 输出 schema
     * @param context 新的扫描上下文
     * @return 新的 {@link AllDataFilesTableScan}
     */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new AllDataFilesTableScan(table, schema, context);
    }

    /**
     * 收集所有快照中可达的数据 manifest。
     *
     * <p>逻辑：借助 {@link #reachableManifests} 遍历表中全部快照，对每个快照取其数据 manifest 列表， 再在并行遍历后用集合去重，作为后续文件扫描输入。
     *
     * @return 去重后的数据 manifest 可关闭迭代器
     */
    @Override
    protected CloseableIterable<ManifestFile> manifests() {
      return reachableManifests(snapshot -> snapshot.dataManifests(table().io()));
    }
  }
}
