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
 * 元数据表：暴露当前快照的所有 manifest 条目（含数据与删除文件）为行的 {@link Table} 实现。
 *
 * <p>所属模块：iceberg-core（核心实现层），元数据表体系成员之一。
 *
 * <p>职责：把当前快照的 manifest 条目以表格行形式暴露，便于查询引擎审计文件级元数据。
 *
 * <p>设计意图：与 {@link AllEntriesTable} 不同，本表只暴露当前快照（而非所有历史快照）的 manifest 条目。 扫描实现 {@link
 * EntriesTableScan} 使用 {@link BaseMetadataTableScan} 的单快照扫描能力。
 *
 * <p>上下游关系：被引擎通过元数据表 API 调用；底层依赖 {@link BaseEntriesTable} 与 {@link BaseMetadataTableScan}。
 *
 * <p><b>注意</b>：此表会暴露表内部细节（如已删除文件），生产查询活跃数据请使用 {@link DataFilesTable}。
 */
public class ManifestEntriesTable extends BaseEntriesTable {

  /** 构造方法：使用默认名称 <code>${表名}.entries</code>。 */
  ManifestEntriesTable(Table table) {
    this(table, table.name() + ".entries");
  }

  /** 构造方法：允许指定名称。 */
  ManifestEntriesTable(Table table, String name) {
    super(table, name);
  }

  /** 创建针对本元数据表的扫描任务。 */
  @Override
  public TableScan newScan() {
    return new EntriesTableScan(table(), schema());
  }

  /** 返回元数据表类型标识 {@link MetadataTableType#ENTRIES}。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.ENTRIES;
  }

  /**
   * 当前快照的 manifest 条目扫描实现。
   *
   * <p>设计意图：复用 {@link BaseMetadataTableScan} 单快照扫描能力，仅在 {@link #doPlanFiles()} 中读取当前快照所有 manifest
   * 并委托 {@link BaseEntriesTable#planFiles} 转换为扫描任务。
   */
  private static class EntriesTableScan extends BaseMetadataTableScan {

    EntriesTableScan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.ENTRIES);
    }

    private EntriesTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.ENTRIES, context);
    }

    /** 基于新参数生成细化扫描。 */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new EntriesTableScan(table, schema, context);
    }

    /**
     * 规划扫描文件任务。
     *
     * <p>逻辑：取当前快照所有 manifest（含 data 与 delete），委托 BaseEntriesTable.planFiles 输出 FileScanTask。
     */
    @Override
    protected CloseableIterable<FileScanTask> doPlanFiles() {
      CloseableIterable<ManifestFile> manifests =
          CloseableIterable.withNoopClose(snapshot().allManifests(table().io()));
      return BaseEntriesTable.planFiles(table(), manifests, tableSchema(), schema(), context());
    }
  }
}
