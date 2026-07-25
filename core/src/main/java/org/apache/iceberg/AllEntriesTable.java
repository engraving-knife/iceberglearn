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
 * 元数据表：以行形式暴露表所有 manifest 条目（同时包含数据文件与删除文件）的 {@link Table} 实现。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 Iceberg 元数据表体系中的一员。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把表内所有 manifest 条目（含已被删除的文件）以表格行的形式暴露给查询引擎。
 *   <li>区别于 {@link DataFilesTable} 仅展示当前活跃数据文件，本表会包含历史快照中已删除的文件条目， 便于审计与排查。
 * </ul>
 *
 * <p>设计意图：作为只读元数据表，复用 {@link BaseEntriesTable} 的通用扫描与投影逻辑， 通过特定 {@link
 * MetadataTableType#ALL_ENTRIES} 类型区分语义；扫描任务委托内部 {@link Scan}， 使用快照中所有 manifest（不区分
 * data/delete）输出条目。
 *
 * <p>上下游关系：被 Spark/Flink 等引擎通过元数据表 API 调用；底层依赖 {@link BaseEntriesTable}、 {@link
 * BaseAllMetadataTableScan} 的扫描能力。
 *
 * <p><b>注意</b>：此表会暴露表内部细节（如已删除文件），生产查询活跃数据请使用 {@link DataFilesTable}。
 */
public class AllEntriesTable extends BaseEntriesTable {

  /**
   * 构造方法：使用默认名称 <code>${表名}.all_entries</code> 创建元数据表视图。
   *
   * @param table 底层数据表
   */
  AllEntriesTable(Table table) {
    this(table, table.name() + ".all_entries");
  }

  /**
   * 构造方法：允许指定元数据表名称。
   *
   * @param table 底层数据表
   * @param name 元数据表名称
   */
  AllEntriesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 创建针对本元数据表的扫描任务。
   *
   * @return 新的 {@link Scan} 实例
   */
  @Override
  public TableScan newScan() {
    return new Scan(table(), schema());
  }

  /**
   * 返回本元数据表的类型标识，用于在元数据表注册体系中区分不同元数据视图。
   *
   * @return {@link MetadataTableType#ALL_ENTRIES}
   */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.ALL_ENTRIES;
  }

  /**
   * AllEntriesTable 的扫描实现：从所有可达快照的 manifest 中读取条目并生成 {@link FileScanTask}。
   *
   * <p>设计意图：复用 {@link BaseAllMetadataTableScan} 的快照投影与上下文管理能力， 仅在 {@link #doPlanFiles()} 中实现“取所有
   * manifest 并委托 BaseEntriesTable.planFiles”的具体逻辑。
   */
  private static class Scan extends BaseAllMetadataTableScan {

    Scan(Table table, Schema schema) {
      super(table, schema, MetadataTableType.ALL_ENTRIES);
    }

    private Scan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.ALL_ENTRIES, context);
    }

    /**
     * 基于当前 schema 与上下文生成细化扫描任务（用于投影/过滤后再次扫描）。
     *
     * @param table 底层数据表
     * @param schema 投影后的 schema
     * @param context 扫描上下文
     * @return 新的 {@link Scan}
     */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new Scan(table, schema, context);
    }

    /**
     * 实际规划扫描文件任务。
     *
     * <p>逻辑：先获取所有可达快照的 manifest 集合（不区分 data/delete），再委托 {@link BaseEntriesTable#planFiles} 将
     * manifest 中的条目转换为 {@link FileScanTask} 流。
     *
     * @return 文件扫描任务的迭代器
     */
    @Override
    protected CloseableIterable<FileScanTask> doPlanFiles() {
      CloseableIterable<ManifestFile> manifests =
          reachableManifests(snapshot -> snapshot.allManifests(table().io()));
      return BaseEntriesTable.planFiles(table(), manifests, tableSchema(), schema(), context());
    }
  }
}
