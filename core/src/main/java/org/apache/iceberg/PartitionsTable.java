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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.iceberg.expressions.ManifestEvaluator;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ParallelIterable;
import org.apache.iceberg.util.PartitionUtil;
import org.apache.iceberg.util.StructLikeMap;

/**
 * 所属模块：iceberg-core；元数据表（Metadata Table）层。
 *
 * <p>职责：将一张表的分区信息以"行"的形式暴露出来，作为可查询的元数据表。具体包括：
 *
 * <ul>
 *   <li>定义分区元数据表的 Schema（分区值、spec_id、记录数、文件数、删除文件统计、最后更新信息等）
 *   <li>基于当前快照的 Manifest 文件聚合出每个分区的统计指标
 *   <li>对外提供 {@link TableScan} 以读取分区行数据
 * </ul>
 *
 * <p>设计意图：作为只读元数据视图，复用底层 {@link Table} 的 IO、规格与快照能力；分区统计在扫描 时按需计算（非持久化），通过 {@link ParallelIterable}
 * 并行读取多个 Manifest 以提升性能。
 *
 * <p>上下游关系：依赖 {@link Table}、{@link StaticTableScan}、{@link ManifestFiles} 等；
 * 被需要展示分区信息的上层查询引擎或用户工具调用。
 */
public class PartitionsTable extends BaseMetadataTable {

  // 分区表行数据的 Schema，包含分区值、规格 ID、数据/删除文件计数与最近更新信息等字段
  private final Schema schema;

  // 标记底层表是否为非分区表，用于决定返回的 Schema 是否包含 partition 列
  private final boolean unpartitionedTable;

  /**
   * 构造分区元数据表，使用 {@code 原表名 + ".partitions"} 作为表名。
   *
   * @param table 底层 Iceberg 表
   */
  PartitionsTable(Table table) {
    this(table, table.name() + ".partitions");
  }

  /**
   * 构造分区元数据表，允许自定义表名。
   *
   * <p>逻辑：在调用父类构造器后，初始化分区表的 Schema（包含分区值、spec_id、各类文件/记录计数、 最后更新时间及快照 ID
   * 等字段），并根据分区类型字段是否为空判断是否为非分区表。
   *
   * @param table 底层 Iceberg 表
   * @param name 元数据表名
   */
  PartitionsTable(Table table, String name) {
    super(table, name);

    this.schema =
        new Schema(
            Types.NestedField.required(1, "partition", Partitioning.partitionType(table)),
            Types.NestedField.required(4, "spec_id", Types.IntegerType.get()),
            Types.NestedField.required(
                2, "record_count", Types.LongType.get(), "Count of records in data files"),
            Types.NestedField.required(
                3, "file_count", Types.IntegerType.get(), "Count of data files"),
            Types.NestedField.required(
                11,
                "total_data_file_size_in_bytes",
                Types.LongType.get(),
                "Total size in bytes of data files"),
            Types.NestedField.required(
                5,
                "position_delete_record_count",
                Types.LongType.get(),
                "Count of records in position delete files"),
            Types.NestedField.required(
                6,
                "position_delete_file_count",
                Types.IntegerType.get(),
                "Count of position delete files"),
            Types.NestedField.required(
                7,
                "equality_delete_record_count",
                Types.LongType.get(),
                "Count of records in equality delete files"),
            Types.NestedField.required(
                8,
                "equality_delete_file_count",
                Types.IntegerType.get(),
                "Count of equality delete files"),
            Types.NestedField.optional(
                9,
                "last_updated_at",
                Types.TimestampType.withZone(),
                "Commit time of snapshot that last updated this partition"),
            Types.NestedField.optional(
                10,
                "last_updated_snapshot_id",
                Types.LongType.get(),
                "Id of snapshot that last updated this partition"));
    this.unpartitionedTable = Partitioning.partitionType(table).fields().isEmpty();
  }

  /**
   * 创建针对分区元数据表的扫描器。
   *
   * @return 分区扫描器 {@link PartitionsScan}
   */
  @Override
  public TableScan newScan() {
    return new PartitionsScan(table());
  }

  /**
   * 返回分区元数据表的 Schema。
   *
   * <p>逻辑：对于非分区表，剔除 partition 列仅返回统计字段；分区表返回完整 Schema。
   *
   * @return 当前表的 Schema
   */
  @Override
  public Schema schema() {
    if (unpartitionedTable) {
      return schema.select(
          "record_count",
          "file_count",
          "total_data_file_size_in_bytes",
          "position_delete_record_count",
          "position_delete_file_count",
          "equality_delete_record_count",
          "equality_delete_file_count",
          "last_updated_at",
          "last_updated_snapshot_id");
    }
    return schema;
  }

  /**
   * 返回该元数据表的类型标识，用于元数据表注册与识别。
   *
   * @return {@link MetadataTableType#PARTITIONS}
   */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.PARTITIONS;
  }

  /**
   * 基于静态扫描构建分区数据任务，将聚合好的分区统计信息输出为可读取的 {@link DataTask}。
   *
   * <p>逻辑：先调用 {@link #partitions(Table, StaticTableScan)} 聚合所有分区；对于非分区表， 因为只有一个根分区，使用专门的行映射函数省略
   * partition 列；对于分区表，使用 {@link #convertPartition(Partition)} 将 {@link Partition} 转换为行。
   *
   * @param scan 静态表扫描
   * @return 包装分区统计行的数据任务
   */
  private DataTask task(StaticTableScan scan) {
    Iterable<Partition> partitions = partitions(table(), scan);
    if (unpartitionedTable) {
      // the table is unpartitioned, partitions contains only the root partition
      return StaticDataTask.of(
          io().newInputFile(table().operations().current().metadataFileLocation()),
          schema(),
          scan.schema(),
          partitions,
          root ->
              StaticDataTask.Row.of(
                  root.dataRecordCount,
                  root.dataFileCount,
                  root.dataFileSizeInBytes,
                  root.posDeleteRecordCount,
                  root.posDeleteFileCount,
                  root.eqDeleteRecordCount,
                  root.eqDeleteFileCount,
                  root.lastUpdatedAt,
                  root.lastUpdatedSnapshotId));
    } else {
      return StaticDataTask.of(
          io().newInputFile(table().operations().current().metadataFileLocation()),
          schema(),
          scan.schema(),
          partitions,
          PartitionsTable::convertPartition);
    }
  }

  /**
   * 将一个 {@link Partition} 转换为 {@link StaticDataTask.Row}，用于分区表行的输出。
   *
   * @param partition 单个分区的聚合数据
   * @return 对应的行数据
   */
  private static StaticDataTask.Row convertPartition(Partition partition) {
    return StaticDataTask.Row.of(
        partition.partitionData,
        partition.specId,
        partition.dataRecordCount,
        partition.dataFileCount,
        partition.dataFileSizeInBytes,
        partition.posDeleteRecordCount,
        partition.posDeleteFileCount,
        partition.eqDeleteRecordCount,
        partition.eqDeleteFileCount,
        partition.lastUpdatedAt,
        partition.lastUpdatedSnapshotId);
  }

  /**
   * 遍历扫描命中的所有 ManifestEntry，按分区值聚合得到每个分区的统计信息。
   *
   * <p>逻辑：先获取表的统一分区类型；构造 {@link PartitionMap}；遍历每个 entry，将文件分区值 强制对齐到统一分区类型，再调用 {@link
   * Partition#update(ContentFile, Snapshot)} 累计统计。 IO 异常被包装为 {@link UncheckedIOException} 抛出。
   *
   * @param table 底层表
   * @param scan 静态表扫描
   * @return 所有分区的聚合结果
   */
  private static Iterable<Partition> partitions(Table table, StaticTableScan scan) {
    Types.StructType partitionType = Partitioning.partitionType(table);
    PartitionMap partitions = new PartitionMap(partitionType);
    try (CloseableIterable<ManifestEntry<? extends ContentFile<?>>> entries = planEntries(scan)) {
      for (ManifestEntry<? extends ContentFile<?>> entry : entries) {
        Snapshot snapshot = table.snapshot(entry.snapshotId());
        ContentFile<?> file = entry.file();
        StructLike partition =
            PartitionUtil.coercePartition(
                partitionType, table.specs().get(file.specId()), file.partition());
        partitions.get(partition).update(file, snapshot);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return partitions.all();
  }

  /**
   * 规划扫描所需的所有 {@link ManifestEntry}，作为分区聚合的输入。
   *
   * <p>逻辑：先获取过滤后的 Manifest 列表，再通过 {@link ParallelIterable} 并行读取每个 Manifest 中的 live entries。仅供测试可见。
   *
   * @param scan 静态表扫描
   * @return 可关闭的 ManifestEntry 迭代器
   */
  @VisibleForTesting
  static CloseableIterable<ManifestEntry<?>> planEntries(StaticTableScan scan) {
    Table table = scan.table();

    CloseableIterable<ManifestFile> filteredManifests =
        filteredManifests(scan, table, scan.snapshot().allManifests(table.io()));

    Iterable<CloseableIterable<ManifestEntry<?>>> tasks =
        CloseableIterable.transform(filteredManifests, manifest -> readEntries(manifest, scan));

    return new ParallelIterable<>(tasks, scan.planExecutor());
  }

  /**
   * 读取单个 Manifest 的 live entries，并去掉统计列以减小内存占用。
   *
   * @param manifest 待读取的 Manifest 文件
   * @param scan 静态表扫描，提供 IO、specs 与大小写敏感等配置
   * @return 可关闭的 ManifestEntry 迭代器
   */
  private static CloseableIterable<ManifestEntry<?>> readEntries(
      ManifestFile manifest, StaticTableScan scan) {
    Table table = scan.table();
    return CloseableIterable.transform(
        ManifestFiles.open(manifest, table.io(), table.specs())
            .caseSensitive(scan.isCaseSensitive())
            .select(scanColumns(manifest.content())) // don't select stats columns
            .liveEntries(),
        t ->
            (ManifestEntry<? extends ContentFile<?>>)
                // defensive copy of manifest entry without stats columns
                t.copyWithoutStats());
  }

  /**
   * 根据 Manifest 内容类型（数据 / 删除）选择对应的扫描列集合。
   *
   * @param content Manifest 内容类型
   * @return 列名列表
   */
  private static List<String> scanColumns(ManifestContent content) {
    switch (content) {
      case DATA:
        return BaseScan.SCAN_COLUMNS;
      case DELETES:
        return BaseScan.DELETE_SCAN_COLUMNS;
      default:
        throw new UnsupportedOperationException("Cannot read unknown manifest type: " + content);
    }
  }

  /**
   * 基于扫描的过滤条件对 Manifest 列表进行过滤。
   *
   * <p>逻辑：使用 Caffeine 缓存按 specId 构建 {@link ManifestEvaluator}，避免重复构造； 每个 Manifest
   * 通过对应规格的评估器判断是否可能命中过滤条件。
   *
   * @param scan 静态表扫描，提供过滤条件与表 Schema
   * @param table 底层表
   * @param manifestFilesList 待过滤的 Manifest 列表
   * @return 过滤后的 Manifest 迭代器
   */
  private static CloseableIterable<ManifestFile> filteredManifests(
      StaticTableScan scan, Table table, List<ManifestFile> manifestFilesList) {
    CloseableIterable<ManifestFile> manifestFiles =
        CloseableIterable.withNoopClose(manifestFilesList);

    LoadingCache<Integer, ManifestEvaluator> evalCache =
        Caffeine.newBuilder()
            .build(
                specId -> {
                  PartitionSpec spec = table.specs().get(specId);
                  PartitionSpec transformedSpec = transformSpec(scan.tableSchema(), spec);
                  return ManifestEvaluator.forRowFilter(
                      scan.filter(), transformedSpec, scan.isCaseSensitive());
                });

    return CloseableIterable.filter(
        manifestFiles, manifest -> evalCache.get(manifest.partitionSpecId()).eval(manifest));
  }

  /** 分区元数据表专用的静态扫描实现，将 task 构造委托给外层 {@link PartitionsTable}。 */
  private class PartitionsScan extends StaticTableScan {
    /**
     * 构造分区扫描器。
     *
     * @param table 底层表
     */
    PartitionsScan(Table table) {
      super(
          table,
          PartitionsTable.this.schema(),
          MetadataTableType.PARTITIONS,
          PartitionsTable.this::task);
    }
  }

  /**
   * 分区键到 {@link Partition} 的映射容器，负责按分区值组织聚合结果。
   *
   * <p>设计意图：封装 {@link StructLikeMap}，按需创建 {@link Partition}，避免外部直接操作底层 Map。
   */
  static class PartitionMap {
    private final StructLikeMap<Partition> partitions;
    private final Types.StructType keyType;

    /**
     * 构造分区映射，指定分区键类型。
     *
     * @param type 分区键结构类型
     */
    PartitionMap(Types.StructType type) {
      this.partitions = StructLikeMap.create(type);
      this.keyType = type;
    }

    /**
     * 获取指定分区键对应的 {@link Partition}，若不存在则新建并放入映射。
     *
     * @param key 分区键
     * @return 该分区键对应的聚合对象
     */
    Partition get(StructLike key) {
      Partition partition = partitions.get(key);
      if (partition == null) {
        partition = new Partition(key, keyType);
        partitions.put(key, partition);
      }
      return partition;
    }

    /**
     * 返回所有已聚合的分区。
     *
     * @return 分区集合
     */
    Iterable<Partition> all() {
      return partitions.values();
    }
  }

  /**
   * 单个分区的聚合统计结果，记录分区值与该分区下的数据文件、删除文件计数及最近更新信息。
   *
   * <p>设计意图：作为可变累加器，在扫描过程中由 {@link #update(ContentFile, Snapshot)} 不断累加； 最终转换为不可变的行数据输出。
   */
  static class Partition {
    private final PartitionData partitionData;
    private int specId;
    private long dataRecordCount;
    private int dataFileCount;
    private long dataFileSizeInBytes;
    private long posDeleteRecordCount;
    private int posDeleteFileCount;
    private long eqDeleteRecordCount;
    private int eqDeleteFileCount;
    private Long lastUpdatedAt;
    private Long lastUpdatedSnapshotId;

    /**
     * 构造分区聚合对象，将分区键转换为可序列化的 {@link PartitionData}，并将所有计数初始化为 0。
     *
     * @param key 分区键
     * @param keyType 分区键结构类型
     */
    Partition(StructLike key, Types.StructType keyType) {
      this.partitionData = toPartitionData(key, keyType);
      this.specId = 0;
      this.dataRecordCount = 0L;
      this.dataFileCount = 0;
      this.dataFileSizeInBytes = 0L;
      this.posDeleteRecordCount = 0L;
      this.posDeleteFileCount = 0;
      this.eqDeleteRecordCount = 0L;
      this.eqDeleteFileCount = 0;
    }

    /**
     * 用一个文件及其所属快照更新该分区的统计信息。
     *
     * <p>逻辑：若快照非空且提交时间更晚，则更新 lastUpdatedAt/lastUpdatedSnapshotId； 随后根据文件内容类型（DATA /
     * POSITION_DELETES / EQUALITY_DELETES）分别累加对应计数。 注意删除文件的 recordCount 是覆盖而非累加，保留最后一次写入的值。
     *
     * @param file 数据或删除文件
     * @param snapshot 文件所属快照
     */
    void update(ContentFile<?> file, Snapshot snapshot) {
      if (snapshot != null) {
        long snapshotCommitTime = snapshot.timestampMillis() * 1000;
        if (this.lastUpdatedAt == null || snapshotCommitTime > this.lastUpdatedAt) {
          this.lastUpdatedAt = snapshotCommitTime;
          this.lastUpdatedSnapshotId = snapshot.snapshotId();
        }
      }

      switch (file.content()) {
        case DATA:
          this.dataRecordCount += file.recordCount();
          this.dataFileCount += 1;
          this.specId = file.specId();
          this.dataFileSizeInBytes += file.fileSizeInBytes();
          break;
        case POSITION_DELETES:
          this.posDeleteRecordCount = file.recordCount();
          this.posDeleteFileCount += 1;
          this.specId = file.specId();
          break;
        case EQUALITY_DELETES:
          this.eqDeleteRecordCount = file.recordCount();
          this.eqDeleteFileCount += 1;
          this.specId = file.specId();
          break;
        default:
          throw new UnsupportedOperationException(
              "Unsupported file content type: " + file.content());
      }
    }

    /**
     * 将任意 {@link StructLike}（如 StructProjection）转换为可序列化的 {@link PartitionData}。
     *
     * <p>设计意图：因为 {@code StructProjection} 不可序列化，需要拷贝出独立可序列化的对象。
     *
     * @param key 原始分区键
     * @param keyType 分区键结构类型
     * @return 可序列化的分区数据
     */
    private PartitionData toPartitionData(StructLike key, Types.StructType keyType) {
      PartitionData data = new PartitionData(keyType);
      for (int i = 0; i < keyType.fields().size(); i++) {
        Object val = key.get(i, keyType.fields().get(i).type().typeId().javaClass());
        if (val != null) {
          data.set(i, val);
        }
      }
      return data;
    }
  }
}
