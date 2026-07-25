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
 * 元数据表：将当前快照的所有文件（数据文件+删除文件）以行形式暴露。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：作为 {@link Table} 实现，把当前快照中的全部文件（含数据文件和删除文件） 展示为元数据表行。与 {@link DataFilesTable} 不同，本表包含删除文件。
 *
 * <p>设计意图：只读视图，复用 {@link BaseFilesTable} 通用骨架，仅覆盖当前快照。
 *
 * <p>上下游关系：由 {@link MetadataTableUtils} 依据表名后缀创建。
 */
public class FilesTable extends BaseFilesTable {

  /** 构造名为 {@code &lt;table&gt;.files} 的文件元数据表。 */
  FilesTable(Table table) {
    this(table, table.name() + ".files");
  }

  /**
   * 指定名称构造文件元数据表。
   *
   * @param table 底层物理表
   * @param name 元数据表名称
   */
  FilesTable(Table table, String name) {
    super(table, name);
  }

  /** 创建针对本元数据表的扫描器。 */
  @Override
  public TableScan newScan() {
    return new FilesTableScan(table(), schema());
  }

  /** 返回本元数据表类型枚举值。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.FILES;
  }

  /** 文件元数据表的扫描器：覆盖当前快照的全部 manifest（数据+删除）。 */
  public static class FilesTableScan extends BaseFilesTableScan {

    /** 构造扫描器。 */
    FilesTableScan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.FILES);
    }

    /**
     * 带扫描上下文的构造器，用于 refine 时复制扫描器。
     *
     * @param table 底层物理表
     * @param schema 输出 schema
     * @param context 扫描上下文
     */
    FilesTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.FILES, context);
    }

    /** 基于新上下文创建细化扫描器（不可变复制模式）。 */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new FilesTableScan(table, schema, context);
    }

    /**
     * 返回当前快照的全部 manifest（数据 manifest + 删除 manifest）。
     *
     * <p>逻辑：调用 {@code snapshot().allManifests()} 取当前快照所有 manifest，包成 noop-close 迭代器。
     */
    @Override
    protected CloseableIterable<ManifestFile> manifests() {
      return CloseableIterable.withNoopClose(snapshot().allManifests(table().io()));
    }
  }
}
