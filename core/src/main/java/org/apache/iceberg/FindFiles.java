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

import java.util.Arrays;
import java.util.List;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * 文件查找工具：以 Builder 方式按快照、时间、过滤条件等查找表中的数据文件。
 *
 * <p>所属模块：iceberg-core（扫描工具层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供链式 API 配置查找条件（快照 id、时间戳、行过滤、元数据过滤、分区过滤）；
 *   <li>在 {@link #collect()} 中基于条件扫描 manifest，返回匹配的 {@link DataFile} 集合。
 * </ul>
 *
 * <p>设计意图：封装扫描的复杂配置，提供比直接使用 TableScan 更简洁的查找接口， 适合需要按多种条件检索文件而非读取数据的场景。
 *
 * <p>上下游关系：由用户代码直接调用；底层使用 {@link ManifestGroup} 做实际扫描。
 */
public class FindFiles {
  private FindFiles() {}

  /**
   * 创建一个查找 Builder。
   *
   * @param table 目标表
   * @return {@link Builder}
   */
  public static Builder in(Table table) {
    return new Builder(table);
  }

  /**
   * 文件查找构建器：累积查找条件并最终通过 {@link #collect()} 执行查找。
   *
   * <p>设计意图：支持多种过滤维度（快照、时间、行级、元数据级、分区级）的组合， 各维度通过 AND/OR 表达式叠加，最终在 collect 时一次性应用。
   */
  public static class Builder {
    private final Table table;
    private final TableOperations ops;
    private boolean caseSensitive = true;
    private boolean includeColumnStats = false;
    private Long snapshotId = null;
    private Expression rowFilter = Expressions.alwaysTrue();
    private Expression fileFilter = Expressions.alwaysTrue();
    private Expression partitionFilter = Expressions.alwaysTrue();

    /**
     * 构造 Builder。
     *
     * @param table 目标表
     */
    public Builder(Table table) {
      this.table = table;
      this.ops = ((HasTableOperations) table).operations();
    }

    /** 设置查找大小写不敏感。 */
    public Builder caseInsensitive() {
      this.caseSensitive = false;
      return this;
    }

    /**
     * 设置查找是否大小写敏感。
     *
     * @param findCaseSensitive 是否大小写敏感
     */
    public Builder caseSensitive(boolean findCaseSensitive) {
      this.caseSensitive = findCaseSensitive;
      return this;
    }

    /** 标记查找结果包含列统计信息。 */
    public Builder includeColumnStats() {
      this.includeColumnStats = true;
      return this;
    }

    /**
     * 基于指定快照查找文件。
     *
     * @param findSnapshotId 快照 id
     * @return this
     */
    public Builder inSnapshot(long findSnapshotId) {
      Preconditions.checkArgument(
          this.snapshotId == null,
          "Cannot set snapshot multiple times, already set to id=%s",
          findSnapshotId);
      Preconditions.checkArgument(
          table.snapshot(findSnapshotId) != null, "Cannot find snapshot for id=%s", findSnapshotId);
      this.snapshotId = findSnapshotId;
      return this;
    }

    /**
     * 基于时间戳查找文件：使用该时间戳之前最新的快照。
     *
     * @param timestampMillis 时间戳（毫秒）
     * @return this
     */
    public Builder asOfTime(long timestampMillis) {
      Preconditions.checkArgument(
          this.snapshotId == null,
          "Cannot set snapshot multiple times, already set to id=%s",
          snapshotId);

      Long lastSnapshotId = null;
      for (HistoryEntry logEntry : ops.current().snapshotLog()) {
        if (logEntry.timestampMillis() <= timestampMillis) {
          lastSnapshotId = logEntry.snapshotId();
        } else {
          // the last snapshot ID was the last one older than the timestamp
          break;
        }
      }

      // the snapshot ID could be null if no entries were older than the requested time. in that
      // case, there is no valid snapshot to read.
      Preconditions.checkArgument(
          lastSnapshotId != null,
          "Cannot find a snapshot older than %s",
          DateTimeUtil.formatTimestampMillis(timestampMillis));
      return inSnapshot(lastSnapshotId);
    }

    /**
     * 按行过滤查找文件：返回可能包含至少一条匹配记录的文件。
     *
     * @param expr 行过滤表达式
     * @return this
     */
    public Builder withRecordsMatching(Expression expr) {
      this.rowFilter = Expressions.and(rowFilter, expr);
      return this;
    }

    /**
     * 按 {@link DataFile} 元数据列过滤查找文件。
     *
     * @param expr 元数据过滤表达式
     * @return this
     */
    public Builder withMetadataMatching(Expression expr) {
      this.fileFilter = Expressions.and(fileFilter, expr);
      return this;
    }

    /**
     * 限定查找范围为指定分区。
     *
     * @param spec 分区规格
     * @param partition 分区数据
     * @return this
     */
    public Builder inPartition(PartitionSpec spec, StructLike partition) {
      return inPartitions(spec, partition);
    }

    /**
     * 限定查找范围为多个分区（可变参数）。
     *
     * @param spec 分区规格
     * @param partitions 分区数据数组
     * @return this
     */
    public Builder inPartitions(PartitionSpec spec, StructLike... partitions) {
      return inPartitions(spec, Arrays.asList(partitions));
    }

    /**
     * 限定查找范围为多个分区（列表）。
     *
     * <p>逻辑：对每个分区构造等值表达式（所有分区字段相等），多个分区间用 OR 连接， 再与已有 partitionFilter 用 OR 合并。
     *
     * @param spec 分区规格
     * @param partitions 分区数据列表
     * @return this
     */
    public Builder inPartitions(PartitionSpec spec, List<StructLike> partitions) {
      Preconditions.checkArgument(
          spec.equals(ops.current().spec(spec.specId())),
          "Partition spec does not belong to table: %s",
          table);

      Expression partitionSetFilter = Expressions.alwaysFalse();
      for (StructLike partitionData : partitions) {
        Expression partFilter = Expressions.alwaysTrue();
        for (int i = 0; i < spec.fields().size(); i += 1) {
          PartitionField field = spec.fields().get(i);
          partFilter =
              Expressions.and(
                  partFilter, Expressions.equal(field.name(), partitionData.get(i, Object.class)));
        }
        partitionSetFilter = Expressions.or(partitionSetFilter, partFilter);
      }

      if (partitionFilter != Expressions.alwaysTrue()) {
        this.partitionFilter = Expressions.or(partitionFilter, partitionSetFilter);
      } else {
        this.partitionFilter = partitionSetFilter;
      }

      return this;
    }

    /**
     * 执行查找，返回所有匹配过滤条件的数据文件。
     *
     * <p>逻辑：确定目标快照（指定 id 或当前快照）；用 {@link ManifestGroup} 配置行过滤、文件过滤、 分区过滤，忽略已删除文件，扫描 entries 并转换为
     * DataFile。
     *
     * @return 匹配的数据文件集合
     */
    public CloseableIterable<DataFile> collect() {
      Snapshot snapshot =
          snapshotId != null ? ops.current().snapshot(snapshotId) : ops.current().currentSnapshot();

      // snapshot could be null when the table just gets created
      if (snapshot == null) {
        return CloseableIterable.empty();
      }

      // when snapshot is not null
      CloseableIterable<ManifestEntry<DataFile>> entries =
          new ManifestGroup(ops.io(), snapshot.dataManifests(ops.io()))
              .specsById(ops.current().specsById())
              .filterData(rowFilter)
              .filterFiles(fileFilter)
              .filterPartitions(partitionFilter)
              .ignoreDeleted()
              .caseSensitive(caseSensitive)
              .entries();

      return CloseableIterable.transform(entries, entry -> entry.file().copy(includeColumnStats));
    }
  }
}
