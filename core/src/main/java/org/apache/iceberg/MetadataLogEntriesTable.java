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

import java.util.List;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.SnapshotUtil;

/**
 * 文件级说明：元数据日志条目元数据表（metadata_log_entries）。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 TableMetadata 中保存的历史元数据文件日志（previousFiles）以表的形式暴露出来， 每一行对应一个历史 metadata.json
 *       文件，并附带该时刻对应的最新快照信息。
 *   <li>实现 Iceberg 的“元数据表”机制，让用户可以像查询普通表一样查询元数据变更历史。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseMetadataTable} 复用元数据表通用骨架；通过静态 schema 固定输出列。
 *   <li>使用 {@link StaticTableScan} + {@link StaticDataTask} 将内存中的元数据日志条目 转换为可被引擎扫描的数据任务，避免落盘。
 *   <li>在 {@code metadataLogEntryToRow} 中通过时间戳反查快照，若该 metadata 文件对应表 创建时刻（无快照），则忽略异常并以 null 表示。
 * </ul>
 *
 * <p>上下游关系：被引擎层通过 {@link MetadataTableUtils#loadMetadataTable} 加载； 依赖 {@link TableMetadata}
 * 提供日志条目，依赖 {@link SnapshotUtil} 做时间戳到快照的转换。
 */
public class MetadataLogEntriesTable extends BaseMetadataTable {

  private static final Schema METADATA_LOG_ENTRIES_SCHEMA =
      new Schema(
          Types.NestedField.required(1, "timestamp", Types.TimestampType.withZone()),
          Types.NestedField.required(2, "file", Types.StringType.get()),
          Types.NestedField.optional(3, "latest_snapshot_id", Types.LongType.get()),
          Types.NestedField.optional(4, "latest_schema_id", Types.IntegerType.get()),
          Types.NestedField.optional(5, "latest_sequence_number", Types.LongType.get()));

  /**
   * 构造元数据日志条目表，使用默认表名（原表名 + ".metadata_log_entries"）。
   *
   * @param table 关联的 Iceberg 主表
   */
  MetadataLogEntriesTable(Table table) {
    this(table, table.name() + ".metadata_log_entries");
  }

  /**
   * 构造元数据日志条目表，允许自定义表名。
   *
   * @param table 关联的 Iceberg 主表
   * @param name 元数据表名称
   */
  MetadataLogEntriesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 返回此元数据表的类型标识。
   *
   * @return {@link MetadataTableType#METADATA_LOG_ENTRIES}
   */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.METADATA_LOG_ENTRIES;
  }

  /**
   * 创建元数据日志条目表的扫描实例。
   *
   * @return 新的 MetadataLogScan 实例
   */
  @Override
  public TableScan newScan() {
    return new MetadataLogScan(table());
  }

  /**
   * 返回此元数据表的固定输出 schema。
   *
   * @return 包含 timestamp/file/latest_snapshot_id/latest_schema_id/latest_sequence_number 列的 schema
   */
  @Override
  public Schema schema() {
    return METADATA_LOG_ENTRIES_SCHEMA;
  }

  /**
   * 构建一个内存 {@link DataTask}，把当前 TableMetadata 的历史日志条目 + 当前 metadata 文件条目 一起作为静态数据返回。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>取当前 TableMetadata 的 previousFiles 作为历史日志条目列表的副本；
   *   <li>追加当前 metadata 文件条目（用 lastUpdatedMillis 作为时间戳）；
   *   <li>用 {@link StaticDataTask#of} 把列表转换成按行输出的 DataTask。
   * </ol>
   *
   * @param scan 触发本次任务的表扫描上下文，用于获取投影后的 schema
   * @return 可被引擎消费的静态数据任务
   */
  private DataTask task(TableScan scan) {
    TableMetadata current = table().operations().current();
    List<TableMetadata.MetadataLogEntry> metadataLogEntries =
        Lists.newArrayList(current.previousFiles().listIterator());
    metadataLogEntries.add(
        new TableMetadata.MetadataLogEntry(
            current.lastUpdatedMillis(), current.metadataFileLocation()));
    return StaticDataTask.of(
        table().io().newInputFile(current.metadataFileLocation()),
        schema(),
        scan.schema(),
        metadataLogEntries,
        metadataLogEntry ->
            MetadataLogEntriesTable.metadataLogEntryToRow(metadataLogEntry, table()));
  }

  /**
   * 内部扫描实现：基于 {@link StaticTableScan}，把 {@link #task} 作为数据来源暴露给引擎。
   *
   * <p>设计要点：通过函数式回调 {@code MetadataLogEntriesTable.this::task} 注入数据生成逻辑， 并在 {@link #planFiles()}
   * 中将其包装为 CloseableIterable 返回。
   */
  private class MetadataLogScan extends StaticTableScan {
    MetadataLogScan(Table table) {
      super(
          table,
          METADATA_LOG_ENTRIES_SCHEMA,
          MetadataTableType.METADATA_LOG_ENTRIES,
          MetadataLogEntriesTable.this::task);
    }

    MetadataLogScan(Table table, TableScanContext context) {
      super(
          table,
          METADATA_LOG_ENTRIES_SCHEMA,
          MetadataTableType.METADATA_LOG_ENTRIES,
          MetadataLogEntriesTable.this::task,
          context);
    }

    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new MetadataLogScan(table, context);
    }

    @Override
    public CloseableIterable<FileScanTask> planFiles() {
      return CloseableIterable.withNoopClose(MetadataLogEntriesTable.this.task(this));
    }
  }

  /**
   * 把一个元数据日志条目转换为输出行。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>用日志条目的时间戳通过 {@link SnapshotUtil#snapshotIdAsOfTime} 反查当时最新的快照 id；
   *   <li>若反查抛出 {@link IllegalArgumentException}（说明对应表创建时刻无快照），则将 快照相关字段置为 null；
   *   <li>组装为 {@link StaticDataTask.Row}（时间戳转为微秒以匹配 TimestampType.withZone）。
   * </ol>
   *
   * @param metadataLogEntry 元数据日志条目（包含时间戳与文件位置）
   * @param table 关联的 Iceberg 主表
   * @return 由 (timestamp, file, latest_snapshot_id, latest_schema_id, latest_sequence_number) 组成的行
   */
  private static StaticDataTask.Row metadataLogEntryToRow(
      TableMetadata.MetadataLogEntry metadataLogEntry, Table table) {
    Long latestSnapshotId = null;
    Snapshot latestSnapshot = null;
    try {
      latestSnapshotId = SnapshotUtil.snapshotIdAsOfTime(table, metadataLogEntry.timestampMillis());
      latestSnapshot = table.snapshot(latestSnapshotId);
    } catch (IllegalArgumentException ignored) {
      // implies this metadata file was created at table creation
    }

    return StaticDataTask.Row.of(
        metadataLogEntry.timestampMillis() * 1000,
        metadataLogEntry.file(),
        // latest snapshot in this file corresponding to the log entry
        latestSnapshotId,
        latestSnapshot != null ? latestSnapshot.schemaId() : null,
        latestSnapshot != null ? latestSnapshot.sequenceNumber() : null);
  }
}
