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

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.SnapshotUtil;

/**
 * 元数据表：将表的快照历史以行形式暴露。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为 {@link Table} 实现，把表的快照日志（snapshot log）展示为元数据表行。
 *   <li>每行包含：快照时间戳、快照 id、父快照 id、是否为当前祖先。
 * </ul>
 *
 * <p>设计意图：基于元数据文件中的 snapshot log 构建，使用 {@link StaticDataTask} 直接从元数据生成数据，无需扫描
 * manifest。覆盖所有快照（不限当前快照）。
 *
 * <p>上下游关系：继承 {@link BaseMetadataTable}；由 {@link MetadataTableUtils} 创建。
 */
public class HistoryTable extends BaseMetadataTable {
  private static final Schema HISTORY_SCHEMA =
      new Schema(
          Types.NestedField.required(1, "made_current_at", Types.TimestampType.withZone()),
          Types.NestedField.required(2, "snapshot_id", Types.LongType.get()),
          Types.NestedField.optional(3, "parent_id", Types.LongType.get()),
          Types.NestedField.required(4, "is_current_ancestor", Types.BooleanType.get()));

  /** 构造名为 {@code &lt;table&gt;.history} 的历史元数据表。 */
  HistoryTable(Table table) {
    this(table, table.name() + ".history");
  }

  /**
   * 指定名称构造历史元数据表。
   *
   * @param table 底层物理表
   * @param name 元数据表名称
   */
  HistoryTable(Table table, String name) {
    super(table, name);
  }

  /** 创建针对本元数据表的扫描器。 */
  @Override
  public TableScan newScan() {
    return new HistoryScan(table());
  }

  /** 返回历史表的固定 schema。 */
  @Override
  public Schema schema() {
    return HISTORY_SCHEMA;
  }

  /** 返回本元数据表类型枚举值。 */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.HISTORY;
  }

  /**
   * 构建历史数据任务：从元数据文件读取快照日志并转换为行。
   *
   * <p>逻辑：用 {@link StaticDataTask#of} 从表的元数据文件构建静态数据任务， 用 {@link #convertHistoryEntryFunc} 把
   * HistoryEntry 转换为输出行。
   *
   * @param scan 表扫描
   * @return 静态数据任务
   */
  private DataTask task(TableScan scan) {
    return StaticDataTask.of(
        table().io().newInputFile(table().operations().current().metadataFileLocation()),
        schema(),
        scan.schema(),
        table().history(),
        convertHistoryEntryFunc(table()));
  }

  /**
   * 历史表扫描器：基于 {@link StaticTableScan}，覆盖所有快照。
   *
   * <p>设计意图：重写 planFiles 以跳过当前快照校验，因为历史表需要覆盖全部快照。
   */
  private class HistoryScan extends StaticTableScan {
    HistoryScan(Table table) {
      super(table, HISTORY_SCHEMA, MetadataTableType.HISTORY, HistoryTable.this::task);
    }

    /**
     * 带扫描上下文的构造器。
     *
     * @param table 底层物理表
     * @param context 扫描上下文
     */
    HistoryScan(Table table, TableScanContext context) {
      super(table, HISTORY_SCHEMA, MetadataTableType.HISTORY, HistoryTable.this::task, context);
    }

    /** 基于新上下文创建细化扫描器（不可变复制模式）。 */
    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new HistoryScan(table, context);
    }

    /**
     * 规划文件扫描任务：直接返回静态数据任务，跳过当前快照校验。
     *
     * <p>逻辑：重写此方法是因为历史表覆盖所有快照，不应校验当前快照是否存在。
     */
    @Override
    public CloseableIterable<FileScanTask> planFiles() {
      // override planFiles to avoid the check for a current snapshot because this metadata table is
      // for all snapshots
      return CloseableIterable.withNoopClose(HistoryTable.this.task(this));
    }
  }

  /**
   * 构造 HistoryEntry 到输出行的转换函数。
   *
   * <p>逻辑：先建立 snapshotId→Snapshot 映射与当前祖先 id 集合；对每个 HistoryEntry， 取其快照的 parentId，并判断是否为当前祖先。
   *
   * @param table 底层物理表
   * @return 转换函数
   */
  private static Function<HistoryEntry, StaticDataTask.Row> convertHistoryEntryFunc(Table table) {
    Map<Long, Snapshot> snapshots = Maps.newHashMap();
    for (Snapshot snap : table.snapshots()) {
      snapshots.put(snap.snapshotId(), snap);
    }

    Set<Long> ancestorIds = Sets.newHashSet(SnapshotUtil.currentAncestorIds(table));

    return historyEntry -> {
      long snapshotId = historyEntry.snapshotId();
      Snapshot snap = snapshots.get(snapshotId);
      return StaticDataTask.Row.of(
          historyEntry.timestampMillis() * 1000,
          historyEntry.snapshotId(),
          snap != null ? snap.parentId() : null,
          ancestorIds.contains(snapshotId));
    };
  }
}
