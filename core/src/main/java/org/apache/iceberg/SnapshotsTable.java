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
import org.apache.iceberg.types.Types;

/**
 * 元数据表：以行形式暴露表的所有已知快照（iceberg-core 元数据表层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将表的快照列表以结构化行（committed_at、snapshot_id、parent_id、operation、 manifest_list、summary）形式呈现；
 *   <li>通过 {@link StaticDataTask} 直接从元数据文件生成数据任务，无需扫描数据文件。
 * </ul>
 *
 * <p>设计意图：作为 {@link BaseMetadataTable} 的子类，复用元数据表框架； 不包含已被 {@link ExpireSnapshots} 过期的快照。
 *
 * <p>上下游关系：由 {@link MetadataTableUtils} 创建，依赖 {@link StaticTableScan} 与 {@link StaticDataTask}；底层读取
 * {@link Table#snapshots()}。
 */
public class SnapshotsTable extends BaseMetadataTable {
  private static final Schema SNAPSHOT_SCHEMA =
      new Schema(
          Types.NestedField.required(1, "committed_at", Types.TimestampType.withZone()),
          Types.NestedField.required(2, "snapshot_id", Types.LongType.get()),
          Types.NestedField.optional(3, "parent_id", Types.LongType.get()),
          Types.NestedField.optional(4, "operation", Types.StringType.get()),
          Types.NestedField.optional(5, "manifest_list", Types.StringType.get()),
          Types.NestedField.optional(
              6,
              "summary",
              Types.MapType.ofRequired(7, 8, Types.StringType.get(), Types.StringType.get())));

  /** 以默认名称（表名 + ".snapshots"）构造快照元数据表。 */
  SnapshotsTable(Table table) {
    this(table, table.name() + ".snapshots");
  }

  /**
   * 以指定名称构造快照元数据表。
   *
   * @param table 底层数据表
   * @param name 元数据表名称
   */
  SnapshotsTable(Table table, String name) {
    super(table, name);
  }

  /** 创建快照元数据表的扫描器。 */
  @Override
  public TableScan newScan() {
    return new SnapshotsTableScan(table());
  }

  /** 返回快照元数据表的固定 Schema。 */
  @Override
  public Schema schema() {
    return SNAPSHOT_SCHEMA;
  }

  /**
   * 构建静态数据任务，将表的所有快照转化为行数据。
   *
   * <p>逻辑：以元数据文件位置创建 {@link StaticDataTask}，传入快照迭代器与 {@link #snapshotToRow} 行映射函数。
   *
   * @param scan 触发该任务的表扫描
   * @return 包含快照行数据的 {@link DataTask}
   */
  private DataTask task(BaseTableScan scan) {
    return StaticDataTask.of(
        table().io().newInputFile(table().operations().current().metadataFileLocation()),
        schema(),
        scan.schema(),
        table().snapshots(),
        SnapshotsTable::snapshotToRow);
  }

  /** 返回该元数据表的类型标识。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.SNAPSHOTS;
  }

  /**
   * 快照元数据表的扫描器实现，继承 {@link StaticTableScan}（iceberg-core 元数据表层内部类）。
   *
   * <p>职责：覆盖 {@link #planFiles()} 以跳过当前快照存在性校验， 因为该元数据表需展示所有快照而非仅当前快照。
   */
  private class SnapshotsTableScan extends StaticTableScan {
    /** 构造快照表扫描器，使用快照 Schema 与 task 回调。 */
    SnapshotsTableScan(Table table) {
      super(table, SNAPSHOT_SCHEMA, MetadataTableType.SNAPSHOTS, SnapshotsTable.this::task);
    }

    /** 构造带扫描上下文的快照表扫描器。 */
    SnapshotsTableScan(Table table, TableScanContext context) {
      super(
          table, SNAPSHOT_SCHEMA, MetadataTableType.SNAPSHOTS, SnapshotsTable.this::task, context);
    }

    /** 基于精炼后的 schema 与上下文创建新的扫描实例。 */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new SnapshotsTableScan(table, context);
    }

    /**
     * 规划文件扫描任务，返回包含所有快照行数据的静态任务。
     *
     * <p>逻辑：覆盖父类实现以跳过当前快照存在性校验，因为该表展示全部快照。
     *
     * @return 包含快照行数据的 {@link FileScanTask} 可迭代集合
     */
    @Override
    public CloseableIterable<FileScanTask> planFiles() {
      // override planFiles to avoid the check for a current snapshot because this metadata table is
      // for all snapshots
      return CloseableIterable.withNoopClose(SnapshotsTable.this.task(this));
    }
  }

  /**
   * 将单个 {@link Snapshot} 转换为 {@link StaticDataTask.Row}。
   *
   * @param snap 快照对象
   * @return 包含 committed_at（微秒）、snapshot_id、parent_id、operation、manifest_list、summary 的行
   */
  private static StaticDataTask.Row snapshotToRow(Snapshot snap) {
    return StaticDataTask.Row.of(
        snap.timestampMillis() * 1000,
        snap.snapshotId(),
        snap.parentId(),
        snap.operation(),
        snap.manifestListLocation(),
        snap.summary());
  }
}
