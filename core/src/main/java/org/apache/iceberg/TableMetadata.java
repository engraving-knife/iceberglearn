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

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.LocationUtil;
import org.apache.iceberg.util.Pair;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SerializableSupplier;

/**
 * 表的元数据，描述一张 Iceberg 表在某个时刻的全部可序列化状态。
 *
 * <p>所属模块：iceberg-core，定位为表元数据的核心不可变数据模型。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载表的位置、UUID、格式版本、Schema、分区规格、排序规则、属性、快照、快照引用、统计文件等全部元信息；
 *   <li>提供基于现有元数据派生新元数据的不可变更新方法（如 {@link #updateSchema}、{@link #replaceProperties} 等），通过 {@link
 *       Builder} 构造新版本；
 *   <li>支持快照的懒加载（通过 {@link SerializableSupplier} 延迟读取快照列表）与变更追踪（{@link MetadataUpdate}）；
 *   <li>对元数据内部一致性进行校验（如快照日志有序性、refs 指向的快照存在、当前快照可解析等）。
 * </ul>
 *
 * <p>设计意图：采用不可变值对象模式，所有变更均通过 {@link Builder} 生成新的 {@link TableMetadata} 实例，
 * 便于在并发提交场景下做乐观比对与回滚。快照列表通过 {@code volatile} + {@code synchronized} 的双重检查 实现线程安全的懒加载，避免在仅访问
 * schema/properties 时也强制读取快照文件。{@code changes} 字段在 元数据未持久化前用于记录待应用的变更，便于事务合并多次修改后一次性提交。
 *
 * <p>上下游关系：上游被 {@link TableOperations} 读写、由 {@link TableMetadataParser} 序列化/反序列化；
 * 下游被扫描器、提交器、快照管理器等广泛消费。
 */
public class TableMetadata implements Serializable {
  /** 表的初始序列号，新表从此值开始递增。 */
  static final long INITIAL_SEQUENCE_NUMBER = 0;
  /** 无效序列号哨兵值，用于表示尚未分配或不可用的序列号。 */
  static final long INVALID_SEQUENCE_NUMBER = -1;
  /** 默认表格式版本，当前为 v2。 */
  static final int DEFAULT_TABLE_FORMAT_VERSION = 2;
  /** 当前支持的最高表格式版本。 */
  static final int SUPPORTED_TABLE_FORMAT_VERSION = 2;
  /** 初始分区规格 ID。 */
  static final int INITIAL_SPEC_ID = 0;
  /** 初始排序规则 ID。 */
  static final int INITIAL_SORT_ORDER_ID = 1;
  /** 初始 Schema ID。 */
  static final int INITIAL_SCHEMA_ID = 0;

  /** 一分钟对应的毫秒数，用于时间戳校验中的容忍阈值。 */
  private static final long ONE_MINUTE = TimeUnit.MINUTES.toMillis(1);

  /**
   * 基于给定的 Schema、分区规格、排序规则、表位置与属性构造新表的元数据。
   *
   * <p>会从属性中读取格式版本，并补充新表默认属性。
   *
   * @param schema 初始 Schema
   * @param spec 初始分区规格
   * @param sortOrder 初始排序规则
   * @param location 表的存储位置
   * @param properties 表属性（可包含格式版本等）
   * @return 新表的元数据
   */
  public static TableMetadata newTableMetadata(
      Schema schema,
      PartitionSpec spec,
      SortOrder sortOrder,
      String location,
      Map<String, String> properties) {
    int formatVersion =
        PropertyUtil.propertyAsInt(
            properties, TableProperties.FORMAT_VERSION, DEFAULT_TABLE_FORMAT_VERSION);
    return newTableMetadata(
        schema, spec, sortOrder, location, persistedProperties(properties), formatVersion);
  }

  /**
   * 基于给定的 Schema、分区规格、表位置与属性构造新表的元数据，排序规则默认为未排序。
   *
   * @param schema 初始 Schema
   * @param spec 初始分区规格
   * @param location 表的存储位置
   * @param properties 表属性
   * @return 新表的元数据
   */
  public static TableMetadata newTableMetadata(
      Schema schema, PartitionSpec spec, String location, Map<String, String> properties) {
    SortOrder sortOrder = SortOrder.unsorted();
    int formatVersion =
        PropertyUtil.propertyAsInt(
            properties, TableProperties.FORMAT_VERSION, DEFAULT_TABLE_FORMAT_VERSION);
    return newTableMetadata(
        schema, spec, sortOrder, location, persistedProperties(properties), formatVersion);
  }

  /**
   * 过滤出非保留属性。保留属性由 Iceberg 内部维护，不允许由用户直接设置。
   *
   * @param rawProperties 原始属性
   * @return 仅包含非保留属性的不可变 Map
   */
  private static Map<String, String> unreservedProperties(Map<String, String> rawProperties) {
    return rawProperties.entrySet().stream()
        .filter(e -> !TableProperties.RESERVED_PROPERTIES.contains(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * 计算新表落盘时实际持久化的属性集合，在新表默认值基础上叠加用户传入的非保留属性。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>先放入新表专属默认值（例如 1.4.0 起的 Parquet 压缩默认值）；
   *   <li>再以用户传入的非保留属性覆盖或补充对应键。
   * </ol>
   *
   * @param rawProperties 用户传入的原始属性
   * @return 用于持久化的属性 Map
   */
  private static Map<String, String> persistedProperties(Map<String, String> rawProperties) {
    Map<String, String> persistedProperties = Maps.newHashMap();

    // explicitly set defaults that apply only to new tables
    persistedProperties.put(
        TableProperties.PARQUET_COMPRESSION,
        TableProperties.PARQUET_COMPRESSION_DEFAULT_SINCE_1_4_0);

    rawProperties.entrySet().stream()
        .filter(entry -> !TableProperties.RESERVED_PROPERTIES.contains(entry.getKey()))
        .forEach(entry -> persistedProperties.put(entry.getKey(), entry.getValue()));

    return persistedProperties;
  }

  /**
   * 构造新表元数据的内部实现，负责为 schema/分区/排序重新分配 ID 并执行校验。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验属性中不包含保留属性；
   *   <li>为传入 schema 重新分配全新的列 ID，保证新表 ID 从 0 起递增；
   *   <li>基于新 schema 重建分区规格，沿用原 transform 但重新分配分区字段 ID；
   *   <li>基于新 schema 重建排序规则；
   *   <li>校验 metrics 配置所引用的列存在；
   *   <li>通过 {@link Builder} 组装最终的 {@link TableMetadata}。
   * </ol>
   *
   * @param schema 初始 Schema
   * @param spec 初始分区规格
   * @param sortOrder 初始排序规则
   * @param location 表位置
   * @param properties 已经过滤的属性
   * @param formatVersion 表格式版本
   * @return 新表元数据
   */
  static TableMetadata newTableMetadata(
      Schema schema,
      PartitionSpec spec,
      SortOrder sortOrder,
      String location,
      Map<String, String> properties,
      int formatVersion) {
    Preconditions.checkArgument(
        properties.keySet().stream().noneMatch(TableProperties.RESERVED_PROPERTIES::contains),
        "Table properties should not contain reserved properties, but got %s",
        properties);

    // reassign all column ids to ensure consistency
    AtomicInteger lastColumnId = new AtomicInteger(0);
    Schema freshSchema =
        TypeUtil.assignFreshIds(INITIAL_SCHEMA_ID, schema, lastColumnId::incrementAndGet);

    // rebuild the partition spec using the new column ids
    PartitionSpec.Builder specBuilder =
        PartitionSpec.builderFor(freshSchema).withSpecId(INITIAL_SPEC_ID);
    for (PartitionField field : spec.fields()) {
      // look up the name of the source field in the old schema to get the new schema's id
      String sourceName = schema.findColumnName(field.sourceId());
      // reassign all partition fields with fresh partition field Ids to ensure consistency
      specBuilder.add(freshSchema.findField(sourceName).fieldId(), field.name(), field.transform());
    }
    PartitionSpec freshSpec = specBuilder.build();

    // rebuild the sort order using the new column ids
    int freshSortOrderId = sortOrder.isUnsorted() ? sortOrder.orderId() : INITIAL_SORT_ORDER_ID;
    SortOrder freshSortOrder = freshSortOrder(freshSortOrderId, freshSchema, sortOrder);

    // Validate the metrics configuration. Note: we only do this on new tables to we don't
    // break existing tables.
    MetricsConfig.fromProperties(properties).validateReferencedColumns(schema);

    return new Builder()
        .setInitialFormatVersion(formatVersion)
        .setCurrentSchema(freshSchema, lastColumnId.get())
        .setDefaultPartitionSpec(freshSpec)
        .setDefaultSortOrder(freshSortOrder)
        .setLocation(location)
        .setProperties(properties)
        .build();
  }

  /** 快照日志条目，记录某个快照成为当前快照的时间戳与快照 ID。 */
  public static class SnapshotLogEntry implements HistoryEntry {
    private final long timestampMillis;
    private final long snapshotId;

    /**
     * 构造一个快照日志条目。
     *
     * @param timestampMillis 快照成为当前快照的时间戳（毫秒）
     * @param snapshotId 快照 ID
     */
    SnapshotLogEntry(long timestampMillis, long snapshotId) {
      this.timestampMillis = timestampMillis;
      this.snapshotId = snapshotId;
    }

    /** 返回快照成为当前快照的时间戳（毫秒）。 */
    @Override
    public long timestampMillis() {
      return timestampMillis;
    }

    /** 返回该日志条目对应的快照 ID。 */
    @Override
    public long snapshotId() {
      return snapshotId;
    }

    /** 基于时间戳与快照 ID 判断相等性。 */
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if (!(other instanceof SnapshotLogEntry)) {
        return false;
      }
      SnapshotLogEntry that = (SnapshotLogEntry) other;
      return timestampMillis == that.timestampMillis && snapshotId == that.snapshotId;
    }

    /** 基于时间戳与快照 ID 计算哈希值。 */
    @Override
    public int hashCode() {
      return Objects.hashCode(timestampMillis, snapshotId);
    }

    /** 返回可读的字符串表示，便于调试。 */
    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("timestampMillis", timestampMillis)
          .add("snapshotId", snapshotId)
          .toString();
    }
  }

  /** 元数据文件历史日志条目，记录曾经存在过的元数据文件路径与时间戳。 */
  public static class MetadataLogEntry {
    private final long timestampMillis;
    private final String file;

    /**
     * 构造一个元数据历史日志条目。
     *
     * @param timestampMillis 该元数据文件创建时的时间戳（毫秒）
     * @param file 元数据文件路径
     */
    MetadataLogEntry(long timestampMillis, String file) {
      this.timestampMillis = timestampMillis;
      this.file = file;
    }

    /** 返回该元数据文件创建时的时间戳（毫秒）。 */
    public long timestampMillis() {
      return timestampMillis;
    }

    /** 返回元数据文件路径。 */
    public String file() {
      return file;
    }

    /** 基于时间戳与文件路径判断相等性。 */
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if (!(other instanceof MetadataLogEntry)) {
        return false;
      }
      MetadataLogEntry that = (MetadataLogEntry) other;
      return timestampMillis == that.timestampMillis && java.util.Objects.equals(file, that.file);
    }

    /** 基于时间戳与文件路径计算哈希值。 */
    @Override
    public int hashCode() {
      return Objects.hashCode(timestampMillis, file);
    }

    /** 返回可读的字符串表示，便于调试。 */
    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("timestampMillis", timestampMillis)
          .add("file", file)
          .toString();
    }
  }

  // stored metadata
  private final String metadataFileLocation;
  private final int formatVersion;
  private final String uuid;
  private final String location;
  private final long lastSequenceNumber;
  private final long lastUpdatedMillis;
  private final int lastColumnId;
  private final int currentSchemaId;
  private final List<Schema> schemas;
  private final int defaultSpecId;
  private final List<PartitionSpec> specs;
  private final int lastAssignedPartitionId;
  private final int defaultSortOrderId;
  private final List<SortOrder> sortOrders;
  private final Map<String, String> properties;
  private final long currentSnapshotId;
  private final Map<Integer, Schema> schemasById;
  private final Map<Integer, PartitionSpec> specsById;
  private final Map<Integer, SortOrder> sortOrdersById;
  private final List<HistoryEntry> snapshotLog;
  private final List<MetadataLogEntry> previousFiles;
  private final List<StatisticsFile> statisticsFiles;
  private final List<MetadataUpdate> changes;
  private SerializableSupplier<List<Snapshot>> snapshotsSupplier;
  private volatile List<Snapshot> snapshots;
  private volatile Map<Long, Snapshot> snapshotsById;
  private volatile Map<String, SnapshotRef> refs;
  private volatile boolean snapshotsLoaded;

  /**
   * 全参构造方法，直接基于已组装好的全部字段构造一个 {@link TableMetadata} 实例。
   *
   * <p>逻辑：本方法承担元数据内部一致性的最终校验，包括：
   *
   * <ol>
   *   <li>分区规格与排序规则非空、格式版本受支持、v2 必须有 UUID、v1 序列号必须为 0；
   *   <li>不能同时携带 metadata 文件位置与未应用变更（changes 必须为空）；
   *   <li>对快照、Schema、分区、排序、refs 建立索引并校验其引用关系；
   *   <li>校验快照日志与元数据历史日志按时间戳递增，并允许一定时钟偏移（{@link #ONE_MINUTE}）；
   *   <li>校验当前快照 ID 在快照索引中可解析。
   * </ol>
   *
   * <p>设计要点：将所有校验集中在构造期完成，保证构造出的对象始终处于一致状态，后续读取无需重复校验。
   *
   * @param metadataFileLocation 本元数据对应的文件路径，可为 null（尚未持久化）
   * @param formatVersion 表格式版本（1 或 2）
   * @param uuid 表的 UUID，v2 必填
   * @param location 表的存储位置
   * @param lastSequenceNumber 最近一次提交后的序列号
   * @param lastUpdatedMillis 最近一次更新的时间戳（毫秒）
   * @param lastColumnId 当前 schema 中已分配的最大列 ID
   * @param currentSchemaId 当前生效的 Schema ID
   * @param schemas 全部历史 Schema 列表
   * @param defaultSpecId 默认分区规格 ID
   * @param specs 全部分区规格列表
   * @param lastAssignedPartitionId 已分配的最大分区字段 ID
   * @param defaultSortOrderId 默认排序规则 ID
   * @param sortOrders 全部排序规则列表
   * @param properties 表属性
   * @param currentSnapshotId 当前快照 ID，无则为 -1
   * @param snapshots 已加载的快照列表，可为空交由 supplier 懒加载
   * @param snapshotsSupplier 快照列表的懒加载供应器，为 null 表示已全部加载
   * @param snapshotLog 快照日志
   * @param previousFiles 元数据历史文件列表
   * @param refs 快照引用（分支/标签）
   * @param statisticsFiles 统计文件列表
   * @param changes 尚未持久化的变更记录
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  TableMetadata(
      String metadataFileLocation,
      int formatVersion,
      String uuid,
      String location,
      long lastSequenceNumber,
      long lastUpdatedMillis,
      int lastColumnId,
      int currentSchemaId,
      List<Schema> schemas,
      int defaultSpecId,
      List<PartitionSpec> specs,
      int lastAssignedPartitionId,
      int defaultSortOrderId,
      List<SortOrder> sortOrders,
      Map<String, String> properties,
      long currentSnapshotId,
      List<Snapshot> snapshots,
      SerializableSupplier<List<Snapshot>> snapshotsSupplier,
      List<HistoryEntry> snapshotLog,
      List<MetadataLogEntry> previousFiles,
      Map<String, SnapshotRef> refs,
      List<StatisticsFile> statisticsFiles,
      List<MetadataUpdate> changes) {
    Preconditions.checkArgument(
        specs != null && !specs.isEmpty(), "Partition specs cannot be null or empty");
    Preconditions.checkArgument(
        sortOrders != null && !sortOrders.isEmpty(), "Sort orders cannot be null or empty");
    Preconditions.checkArgument(
        formatVersion <= SUPPORTED_TABLE_FORMAT_VERSION,
        "Unsupported format version: v%s",
        formatVersion);
    Preconditions.checkArgument(
        formatVersion == 1 || uuid != null, "UUID is required in format v%s", formatVersion);
    Preconditions.checkArgument(
        formatVersion > 1 || lastSequenceNumber == 0,
        "Sequence number must be 0 in v1: %s",
        lastSequenceNumber);
    Preconditions.checkArgument(
        metadataFileLocation == null || changes.isEmpty(),
        "Cannot create TableMetadata with a metadata location and changes");

    this.metadataFileLocation = metadataFileLocation;
    this.formatVersion = formatVersion;
    this.uuid = uuid;
    this.location = location != null ? LocationUtil.stripTrailingSlash(location) : null;
    this.lastSequenceNumber = lastSequenceNumber;
    this.lastUpdatedMillis = lastUpdatedMillis;
    this.lastColumnId = lastColumnId;
    this.currentSchemaId = currentSchemaId;
    this.schemas = schemas;
    this.specs = specs;
    this.defaultSpecId = defaultSpecId;
    this.lastAssignedPartitionId = lastAssignedPartitionId;
    this.defaultSortOrderId = defaultSortOrderId;
    this.sortOrders = sortOrders;
    this.properties = properties;
    this.currentSnapshotId = currentSnapshotId;
    this.snapshots = snapshots;
    this.snapshotsSupplier = snapshotsSupplier;
    this.snapshotsLoaded = snapshotsSupplier == null;
    this.snapshotLog = snapshotLog;
    this.previousFiles = previousFiles;

    // changes are carried through until metadata is read from a file
    this.changes = changes;

    this.snapshotsById = indexAndValidateSnapshots(snapshots, lastSequenceNumber);
    this.schemasById = indexSchemas();
    this.specsById = indexSpecs(specs);
    this.sortOrdersById = indexSortOrders(sortOrders);
    this.refs = validateRefs(currentSnapshotId, refs, snapshotsById);
    this.statisticsFiles = ImmutableList.copyOf(statisticsFiles);

    HistoryEntry last = null;
    for (HistoryEntry logEntry : snapshotLog) {
      if (last != null) {
        Preconditions.checkArgument(
            (logEntry.timestampMillis() - last.timestampMillis()) >= -ONE_MINUTE,
            "[BUG] Expected sorted snapshot log entries.");
      }
      last = logEntry;
    }
    if (last != null) {
      Preconditions.checkArgument(
          // commits can happen concurrently from different machines.
          // A tolerance helps us avoid failure for small clock skew
          lastUpdatedMillis - last.timestampMillis() >= -ONE_MINUTE,
          "Invalid update timestamp %s: before last snapshot log entry at %s",
          lastUpdatedMillis,
          last.timestampMillis());
    }

    MetadataLogEntry previous = null;
    for (MetadataLogEntry metadataEntry : previousFiles) {
      if (previous != null) {
        Preconditions.checkArgument(
            // commits can happen concurrently from different machines.
            // A tolerance helps us avoid failure for small clock skew
            (metadataEntry.timestampMillis() - previous.timestampMillis()) >= -ONE_MINUTE,
            "[BUG] Expected sorted previous metadata log entries.");
      }
      previous = metadataEntry;
    }
    // Make sure that this update's lastUpdatedMillis is > max(previousFile's timestamp)
    if (previous != null) {
      Preconditions.checkArgument(
          // commits can happen concurrently from different machines.
          // A tolerance helps us avoid failure for small clock skew
          lastUpdatedMillis - previous.timestampMillis >= -ONE_MINUTE,
          "Invalid update timestamp %s: before the latest metadata log entry timestamp %s",
          lastUpdatedMillis,
          previous.timestampMillis);
    }

    validateCurrentSnapshot();
  }

  /** 返回表格式版本（1 或 2）。 */
  public int formatVersion() {
    return formatVersion;
  }

  /** 返回本元数据对应的文件路径，尚未持久化时为 null。 */
  public String metadataFileLocation() {
    return metadataFileLocation;
  }

  /** 返回表的 UUID。 */
  public String uuid() {
    return uuid;
  }

  /** 返回最近一次提交后的序列号。 */
  public long lastSequenceNumber() {
    return lastSequenceNumber;
  }

  /**
   * 返回下一次提交应使用的序列号。
   *
   * <p>v2 及以上版本每次提交序列号加 1；v1 不支持序列号，始终返回 {@link #INITIAL_SEQUENCE_NUMBER}。
   *
   * @return 下一次提交的序列号
   */
  public long nextSequenceNumber() {
    return formatVersion > 1 ? lastSequenceNumber + 1 : INITIAL_SEQUENCE_NUMBER;
  }

  /** 返回最近一次更新的时间戳（毫秒）。 */
  public long lastUpdatedMillis() {
    return lastUpdatedMillis;
  }

  /** 返回当前 schema 中已分配的最大列 ID。 */
  public int lastColumnId() {
    return lastColumnId;
  }

  /** 返回当前生效的 Schema。 */
  public Schema schema() {
    return schemasById.get(currentSchemaId);
  }

  /** 返回全部历史 Schema 列表。 */
  public List<Schema> schemas() {
    return schemas;
  }

  /** 返回按 Schema ID 索引的 Schema 映射。 */
  public Map<Integer, Schema> schemasById() {
    return schemasById;
  }

  /** 返回当前生效的 Schema ID。 */
  public int currentSchemaId() {
    return currentSchemaId;
  }

  /** 返回默认分区规格。 */
  public PartitionSpec spec() {
    return specsById.get(defaultSpecId);
  }

  /**
   * 按分区规格 ID 查询分区规格。
   *
   * @param id 分区规格 ID
   * @return 对应的分区规格，不存在则返回 null
   */
  public PartitionSpec spec(int id) {
    return specsById.get(id);
  }

  /** 返回全部分区规格列表。 */
  public List<PartitionSpec> specs() {
    return specs;
  }

  /** 返回按分区规格 ID 索引的映射。 */
  public Map<Integer, PartitionSpec> specsById() {
    return specsById;
  }

  /** 返回已分配的最大分区字段 ID。 */
  public int lastAssignedPartitionId() {
    return lastAssignedPartitionId;
  }

  /** 返回默认分区规格 ID。 */
  public int defaultSpecId() {
    return defaultSpecId;
  }

  /** 返回默认排序规则 ID。 */
  public int defaultSortOrderId() {
    return defaultSortOrderId;
  }

  /** 返回默认排序规则。 */
  public SortOrder sortOrder() {
    return sortOrdersById.get(defaultSortOrderId);
  }

  /** 返回全部排序规则列表。 */
  public List<SortOrder> sortOrders() {
    return sortOrders;
  }

  /** 返回按排序规则 ID 索引的映射。 */
  public Map<Integer, SortOrder> sortOrdersById() {
    return sortOrdersById;
  }

  /** 返回表的存储位置。 */
  public String location() {
    return location;
  }

  /** 返回表的全部属性。 */
  public Map<String, String> properties() {
    return properties;
  }

  /**
   * 按属性名读取字符串属性，未设置时返回默认值。
   *
   * @param property 属性名
   * @param defaultValue 默认值
   * @return 属性值或默认值
   */
  public String property(String property, String defaultValue) {
    return properties.getOrDefault(property, defaultValue);
  }

  /**
   * 按属性名读取布尔属性，未设置或解析失败时返回默认值。
   *
   * @param property 属性名
   * @param defaultValue 默认值
   * @return 布尔属性值
   */
  public boolean propertyAsBoolean(String property, boolean defaultValue) {
    return PropertyUtil.propertyAsBoolean(properties, property, defaultValue);
  }

  /**
   * 按属性名读取 int 属性，未设置或解析失败时返回默认值。
   *
   * @param property 属性名
   * @param defaultValue 默认值
   * @return int 属性值
   */
  public int propertyAsInt(String property, int defaultValue) {
    return PropertyUtil.propertyAsInt(properties, property, defaultValue);
  }

  /**
   * 按属性名读取 long 属性，未设置或解析失败时返回默认值。
   *
   * @param property 属性名
   * @param defaultValue 默认值
   * @return long 属性值
   */
  public long propertyAsLong(String property, long defaultValue) {
    return PropertyUtil.propertyAsLong(properties, property, defaultValue);
  }

  /**
   * 按快照 ID 查询快照。若快照列表尚未懒加载完成，会先触发加载。
   *
   * @param snapshotId 快照 ID
   * @return 对应快照，不存在则返回 null
   */
  public Snapshot snapshot(long snapshotId) {
    if (!snapshotsById.containsKey(snapshotId)) {
      ensureSnapshotsLoaded();
    }

    return snapshotsById.get(snapshotId);
  }

  /** 返回当前快照，可能为 null。 */
  public Snapshot currentSnapshot() {
    return snapshotsById.get(currentSnapshotId);
  }

  /**
   * 返回全部快照列表，若快照尚未懒加载完成会先触发加载。
   *
   * @return 不可变的快照列表
   */
  public List<Snapshot> snapshots() {
    ensureSnapshotsLoaded();

    return snapshots;
  }

  /**
   * 确保快照列表已通过 {@link #snapshotsSupplier} 完成懒加载。
   *
   * <p>逻辑：若 {@code snapshotsLoaded} 为 false，调用 supplier 获取全部快照， 过滤掉序列号大于 {@code lastSequenceNumber}
   * 的快照（防止读到尚未提交的快照）， 重建快照索引、refs 并重新校验当前快照；加载完成后清空 supplier 释放引用。
   *
   * <p>线程安全：通过 synchronized 保证仅加载一次，加载完成后字段以 volatile 可见。
   */
  private synchronized void ensureSnapshotsLoaded() {
    if (!snapshotsLoaded) {
      List<Snapshot> loadedSnapshots = Lists.newArrayList(snapshotsSupplier.get());
      loadedSnapshots.removeIf(s -> s.sequenceNumber() > lastSequenceNumber);

      this.snapshots = ImmutableList.copyOf(loadedSnapshots);
      this.snapshotsById = indexAndValidateSnapshots(snapshots, lastSequenceNumber);
      validateCurrentSnapshot();

      this.refs = validateRefs(currentSnapshotId, refs, snapshotsById);

      this.snapshotsLoaded = true;
      this.snapshotsSupplier = null;
    }
  }

  /**
   * 按名称查询快照引用（分支或标签）。
   *
   * @param name 引用名称
   * @return 对应的 {@link SnapshotRef}，不存在则返回 null
   */
  public SnapshotRef ref(String name) {
    return refs.get(name);
  }

  /** 返回全部快照引用。 */
  public Map<String, SnapshotRef> refs() {
    return refs;
  }

  /** 返回全部统计文件列表。 */
  public List<StatisticsFile> statisticsFiles() {
    return statisticsFiles;
  }

  /** 返回快照日志。 */
  public List<HistoryEntry> snapshotLog() {
    return snapshotLog;
  }

  /** 返回历史元数据文件列表。 */
  public List<MetadataLogEntry> previousFiles() {
    return previousFiles;
  }

  /** 返回尚未持久化的变更记录。 */
  public List<MetadataUpdate> changes() {
    return changes;
  }

  /**
   * 为本元数据补一个 UUID（若当前为空），返回新的元数据实例。
   *
   * @return 包含 UUID 的元数据
   */
  public TableMetadata withUUID() {
    return new Builder(this).assignUUID().build();
  }

  /**
   * 更新 Schema 与最大列 ID，返回新的元数据实例。
   *
   * @param newSchema 新 Schema
   * @param newLastColumnId 新的最大列 ID
   * @return 更新后的元数据
   */
  public TableMetadata updateSchema(Schema newSchema, int newLastColumnId) {
    return new Builder(this).setCurrentSchema(newSchema, newLastColumnId).build();
  }

  /**
   * 更新默认分区规格。
   *
   * <p>注意：调用方需自行保证传入的分区字段 ID 正确。
   *
   * @param newPartitionSpec 新分区规格
   * @return 更新后的元数据
   */
  // The caller is responsible to pass a newPartitionSpec with correct partition field IDs
  public TableMetadata updatePartitionSpec(PartitionSpec newPartitionSpec) {
    return new Builder(this).setDefaultPartitionSpec(newPartitionSpec).build();
  }

  /**
   * 替换默认排序规则。
   *
   * @param newOrder 新排序规则
   * @return 更新后的元数据
   */
  public TableMetadata replaceSortOrder(SortOrder newOrder) {
    return new Builder(this).setDefaultSortOrder(newOrder).build();
  }

  /**
   * 按谓词移除满足条件的快照，返回新的元数据实例。
   *
   * @param removeIf 判断快照是否应被移除的谓词
   * @return 移除快照后的元数据
   */
  public TableMetadata removeSnapshotsIf(Predicate<Snapshot> removeIf) {
    List<Snapshot> toRemove = snapshots().stream().filter(removeIf).collect(Collectors.toList());
    return new Builder(this).removeSnapshots(toRemove).build();
  }

  /**
   * 用新的属性集合替换当前属性，返回新的元数据实例。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验入参非空，并过滤掉保留属性；
   *   <li>对比新旧属性，计算被移除的键集合与新增/更新的键集合；
   *   <li>从原始属性中读取可能的格式版本覆盖；
   *   <li>通过 Builder 应用 setProperties、removeProperties 与 upgradeFormatVersion 后构造新实例。
   * </ol>
   *
   * @param rawProperties 新的属性集合
   * @return 替换属性后的元数据
   */
  public TableMetadata replaceProperties(Map<String, String> rawProperties) {
    ValidationException.check(rawProperties != null, "Cannot set properties to null");
    Map<String, String> newProperties = unreservedProperties(rawProperties);

    Set<String> removed = Sets.newHashSet(properties.keySet());
    Map<String, String> updated = Maps.newHashMap();
    for (Map.Entry<String, String> entry : newProperties.entrySet()) {
      removed.remove(entry.getKey());
      String current = properties.get(entry.getKey());
      if (current == null || !current.equals(entry.getValue())) {
        updated.put(entry.getKey(), entry.getValue());
      }
    }

    int newFormatVersion =
        PropertyUtil.propertyAsInt(rawProperties, TableProperties.FORMAT_VERSION, formatVersion);

    return new Builder(this)
        .setProperties(updated)
        .removeProperties(removed)
        .upgradeFormatVersion(newFormatVersion)
        .build();
  }

  /** 校验当前快照 ID 在快照索引中存在或为无效值。 */
  private void validateCurrentSnapshot() {
    Preconditions.checkArgument(
        currentSnapshotId < 0 || snapshotsById.containsKey(currentSnapshotId),
        "Invalid table metadata: Cannot find current version");
  }

  /**
   * 按当前表已有的分区字段 ID 重新分配传入分区规格的字段 ID，使新旧规格在字段 ID 上保持一致。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>v2 及以上：复用已存在的字段 ID（按 sourceId+transform 去重取最大），缺失时通过 nextID 分配新 ID；
   *   <li>v1：保留旧字段顺序，缺失字段以 alwaysNull 替换；新增字段追加在末尾并分配新 ID。
   * </ul>
   *
   * @param partitionSpec 待对齐的分区规格
   * @param nextID 用于分配新分区字段 ID 的回调
   * @return 字段 ID 与现有规格对齐后的分区规格
   */
  private PartitionSpec reassignPartitionIds(PartitionSpec partitionSpec, TypeUtil.NextID nextID) {
    PartitionSpec.Builder specBuilder =
        PartitionSpec.builderFor(partitionSpec.schema()).withSpecId(partitionSpec.specId());

    if (formatVersion > 1) {
      // for v2 and later, reuse any existing field IDs, but reproduce the same spec
      Map<Pair<Integer, String>, Integer> transformToFieldId =
          specs.stream()
              .flatMap(spec -> spec.fields().stream())
              .collect(
                  Collectors.toMap(
                      field -> Pair.of(field.sourceId(), field.transform().toString()),
                      PartitionField::fieldId,
                      Math::max));

      for (PartitionField field : partitionSpec.fields()) {
        // reassign the partition field ids
        int partitionFieldId =
            transformToFieldId.computeIfAbsent(
                Pair.of(field.sourceId(), field.transform().toString()), k -> nextID.get());
        specBuilder.add(field.sourceId(), partitionFieldId, field.name(), field.transform());
      }

    } else {
      // for v1, preserve the existing spec and carry forward all fields, replacing missing fields
      // with void
      Map<Pair<Integer, String>, PartitionField> newFields = Maps.newLinkedHashMap();
      for (PartitionField newField : partitionSpec.fields()) {
        newFields.put(Pair.of(newField.sourceId(), newField.transform().toString()), newField);
      }
      List<String> newFieldNames =
          newFields.values().stream().map(PartitionField::name).collect(Collectors.toList());

      for (PartitionField field : spec().fields()) {
        // ensure each field is either carried forward or replaced with void
        PartitionField newField =
            newFields.remove(Pair.of(field.sourceId(), field.transform().toString()));
        if (newField != null) {
          // copy the new field with the existing field ID
          specBuilder.add(
              newField.sourceId(), field.fieldId(), newField.name(), newField.transform());
        } else {
          // Rename old void transforms that would otherwise conflict
          String voidName =
              newFieldNames.contains(field.name())
                  ? field.name() + "_" + field.fieldId()
                  : field.name();
          specBuilder.add(field.sourceId(), field.fieldId(), voidName, Transforms.alwaysNull());
        }
      }

      // add any remaining new fields at the end and assign new partition field IDs
      for (PartitionField newField : newFields.values()) {
        specBuilder.add(newField.sourceId(), nextID.get(), newField.name(), newField.transform());
      }
    }

    return specBuilder.build();
  }

  /**
   * 基于新的 schema、分区规格、排序规则、位置与属性构造替换后的元数据实例。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>v1 表要求分区字段 ID 连续递增；
   *   <li>基于现有 schema 为新 schema 重新分配列 ID，保留原有列 ID；
   *   <li>基于新 schema 重建分区规格并对齐现有分区字段 ID；
   *   <li>基于新 schema 重建排序规则；
   *   <li>从属性读取可能的格式版本覆盖；
   *   <li>通过 Builder 应用各项变更并移除 main 分支引用，构造新元数据。
   * </ol>
   *
   * @param updatedSchema 新 Schema
   * @param updatedPartitionSpec 新分区规格
   * @param updatedSortOrder 新排序规则
   * @param newLocation 新表位置
   * @param updatedProperties 新属性
   * @return 替换后的元数据
   */
  // The caller is responsible to pass a updatedPartitionSpec with correct partition field IDs
  public TableMetadata buildReplacement(
      Schema updatedSchema,
      PartitionSpec updatedPartitionSpec,
      SortOrder updatedSortOrder,
      String newLocation,
      Map<String, String> updatedProperties) {
    ValidationException.check(
        formatVersion > 1 || PartitionSpec.hasSequentialIds(updatedPartitionSpec),
        "Spec does not use sequential IDs that are required in v1: %s",
        updatedPartitionSpec);

    AtomicInteger newLastColumnId = new AtomicInteger(lastColumnId);
    Schema freshSchema =
        TypeUtil.assignFreshIds(updatedSchema, schema(), newLastColumnId::incrementAndGet);

    // rebuild the partition spec using the new column ids and reassign partition field ids to align
    // with existing
    // partition specs in the table
    PartitionSpec freshSpec =
        reassignPartitionIds(
            freshSpec(INITIAL_SPEC_ID, freshSchema, updatedPartitionSpec),
            new AtomicInteger(lastAssignedPartitionId)::incrementAndGet);

    // rebuild the sort order using new column ids
    SortOrder freshSortOrder = freshSortOrder(INITIAL_SORT_ORDER_ID, freshSchema, updatedSortOrder);

    // check if there is format version override
    int newFormatVersion =
        PropertyUtil.propertyAsInt(
            updatedProperties, TableProperties.FORMAT_VERSION, formatVersion);

    return new Builder(this)
        .upgradeFormatVersion(newFormatVersion)
        .removeRef(SnapshotRef.MAIN_BRANCH)
        .setCurrentSchema(freshSchema, newLastColumnId.get())
        .setDefaultPartitionSpec(freshSpec)
        .setDefaultSortOrder(freshSortOrder)
        .setLocation(newLocation)
        .setProperties(persistedProperties(updatedProperties))
        .build();
  }

  /**
   * 更新表的存储位置。
   *
   * @param newLocation 新位置
   * @return 更新后的元数据
   */
  public TableMetadata updateLocation(String newLocation) {
    return new Builder(this).setLocation(newLocation).build();
  }

  /**
   * 升级表格式版本（不可降级），返回新的元数据实例。
   *
   * @param newFormatVersion 目标格式版本
   * @return 升级后的元数据
   */
  public TableMetadata upgradeToFormatVersion(int newFormatVersion) {
    return new Builder(this).upgradeFormatVersion(newFormatVersion).build();
  }

  /**
   * 用新 Schema 替换分区规格内部对 Schema 的引用，但保留所有字段 ID 不变。
   *
   * <p>用于 schema 变更后让旧分区规格仍可被解析：构建时不做合法性校验， 因为 schema 变化可能让旧 spec 失效，但仍需保留以便理解历史元数据。
   *
   * @param schema 新 Schema
   * @param partitionSpec 待更新的分区规格
   * @return 绑定到新 Schema 的分区规格
   */
  private static PartitionSpec updateSpecSchema(Schema schema, PartitionSpec partitionSpec) {
    PartitionSpec.Builder specBuilder =
        PartitionSpec.builderFor(schema).withSpecId(partitionSpec.specId());

    // add all the fields to the builder. IDs should not change.
    for (PartitionField field : partitionSpec.fields()) {
      specBuilder.add(field.sourceId(), field.fieldId(), field.name(), field.transform());
    }

    // build without validation because the schema may have changed in a way that makes this spec
    // invalid. the spec
    // should still be preserved so that older metadata can be interpreted.
    return specBuilder.buildUnchecked();
  }

  /**
   * 用新 Schema 替换排序规则内部对 Schema 的引用，但保留所有字段 ID 不变。
   *
   * <p>用于 schema 变更后让旧排序规则仍可被解析：构建时不做合法性校验， 因为 schema 变化可能让旧 order 失效，但仍需保留以便理解历史元数据。
   *
   * @param schema 新 Schema
   * @param sortOrder 待更新的排序规则
   * @return 绑定到新 Schema 的排序规则
   */
  private static SortOrder updateSortOrderSchema(Schema schema, SortOrder sortOrder) {
    SortOrder.Builder builder = SortOrder.builderFor(schema).withOrderId(sortOrder.orderId());

    // add all the fields to the builder. IDs should not change.
    for (SortField field : sortOrder.fields()) {
      builder.addSortField(
          field.transform(), field.sourceId(), field.direction(), field.nullOrder());
    }

    // build without validation because the schema may have changed in a way that makes this order
    // invalid. the order
    // should still be preserved so that older metadata can be interpreted.
    return builder.buildUnchecked();
  }

  /**
   * 基于新 Schema 重建分区规格，沿用原 transform 与字段名，但将 sourceId 重映射到新 Schema 的列 ID。
   *
   * <p>当原列已被删除（sourceName 为 null）时，仅 v1 表会保留原 sourceId 作为 void transform 引用。
   *
   * @param specId 新分区规格 ID
   * @param schema 新 Schema
   * @param partitionSpec 原分区规格
   * @return 绑定到新 Schema 的分区规格
   */
  private static PartitionSpec freshSpec(int specId, Schema schema, PartitionSpec partitionSpec) {
    UnboundPartitionSpec.Builder specBuilder = UnboundPartitionSpec.builder().withSpecId(specId);

    for (PartitionField field : partitionSpec.fields()) {
      // look up the name of the source field in the old schema to get the new schema's id
      String sourceName = partitionSpec.schema().findColumnName(field.sourceId());

      final int fieldId;
      if (sourceName != null) {
        fieldId = schema.findField(sourceName).fieldId();
      } else {
        // In the case of a null sourceName, the column has been deleted.
        // This only happens in V1 tables where the reference is still around as a void transform
        fieldId = field.sourceId();
      }
      specBuilder.addField(field.transform().toString(), fieldId, field.fieldId(), field.name());
    }

    return specBuilder.build().bind(schema);
  }

  /**
   * 基于新 Schema 重建排序规则，沿用原 transform、方向与 null 顺序，并将 sourceId 重映射到新 Schema 的列 ID。
   *
   * @param orderId 新排序规则 ID
   * @param schema 新 Schema
   * @param sortOrder 原排序规则
   * @return 绑定到新 Schema 的排序规则
   */
  private static SortOrder freshSortOrder(int orderId, Schema schema, SortOrder sortOrder) {
    UnboundSortOrder.Builder builder = UnboundSortOrder.builder();

    if (sortOrder.isSorted()) {
      builder.withOrderId(orderId);
    }

    for (SortField field : sortOrder.fields()) {
      // look up the name of the source field in the old schema to get the new schema's id
      String sourceName = sortOrder.schema().findColumnName(field.sourceId());
      // reassign all sort fields with fresh sort field IDs
      int newSourceId = schema.findField(sourceName).fieldId();
      builder.addSortField(
          field.transform().toString(), newSourceId, field.direction(), field.nullOrder());
    }

    return builder.build().bind(schema);
  }

  /**
   * 将快照列表按快照 ID 建立索引，并校验每个快照的序列号不超过 {@code lastSequenceNumber}。
   *
   * @param snapshots 快照列表
   * @param lastSequenceNumber 最近一次提交的序列号上限
   * @return 按快照 ID 索引的不可变 Map
   */
  private static Map<Long, Snapshot> indexAndValidateSnapshots(
      List<Snapshot> snapshots, long lastSequenceNumber) {
    ImmutableMap.Builder<Long, Snapshot> builder = ImmutableMap.builder();
    for (Snapshot snap : snapshots) {
      ValidationException.check(
          snap.sequenceNumber() <= lastSequenceNumber,
          "Invalid snapshot with sequence number %s greater than last sequence number %s",
          snap.sequenceNumber(),
          lastSequenceNumber);
      builder.put(snap.snapshotId(), snap);
    }
    return builder.build();
  }

  /** 将 Schema 列表按 Schema ID 建立索引。 */
  private Map<Integer, Schema> indexSchemas() {
    ImmutableMap.Builder<Integer, Schema> builder = ImmutableMap.builder();
    for (Schema schema : schemas) {
      builder.put(schema.schemaId(), schema);
    }
    return builder.build();
  }

  /** 将分区规格列表按规格 ID 建立索引。 */
  private static Map<Integer, PartitionSpec> indexSpecs(List<PartitionSpec> specs) {
    ImmutableMap.Builder<Integer, PartitionSpec> builder = ImmutableMap.builder();
    for (PartitionSpec spec : specs) {
      builder.put(spec.specId(), spec);
    }
    return builder.build();
  }

  /** 将排序规则列表按规则 ID 建立索引。 */
  private static Map<Integer, SortOrder> indexSortOrders(List<SortOrder> sortOrders) {
    ImmutableMap.Builder<Integer, SortOrder> builder = ImmutableMap.builder();
    for (SortOrder sortOrder : sortOrders) {
      builder.put(sortOrder.orderId(), sortOrder);
    }
    return builder.build();
  }

  /**
   * 校验快照引用集合的合法性：所有引用的快照必须存在；若存在 main 分支，其快照 ID 必须等于当前快照 ID。
   *
   * @param currentSnapshotId 当前快照 ID
   * @param inputRefs 输入的快照引用
   * @param snapshotsById 按快照 ID 索引的快照
   * @return 通过校验的引用集合
   */
  private static Map<String, SnapshotRef> validateRefs(
      Long currentSnapshotId,
      Map<String, SnapshotRef> inputRefs,
      Map<Long, Snapshot> snapshotsById) {
    for (SnapshotRef ref : inputRefs.values()) {
      Preconditions.checkArgument(
          snapshotsById.containsKey(ref.snapshotId()),
          "Snapshot for reference %s does not exist in the existing snapshots list",
          ref);
    }

    SnapshotRef main = inputRefs.get(SnapshotRef.MAIN_BRANCH);
    if (currentSnapshotId != -1) {
      Preconditions.checkArgument(
          main == null || currentSnapshotId == main.snapshotId(),
          "Current snapshot ID does not match main branch (%s != %s)",
          currentSnapshotId,
          main != null ? main.snapshotId() : null);
    } else {
      Preconditions.checkArgument(
          main == null, "Current snapshot is not set, but main branch exists: %s", main);
    }

    return inputRefs;
  }

  /**
   * 基于已有元数据创建一个 {@link Builder}，用于在其基础上进行增量修改。
   *
   * @param base 基础元数据
   * @return 预填充了 base 内容的构建器
   */
  public static Builder buildFrom(TableMetadata base) {
    return new Builder(base);
  }

  /**
   * 创建一个空的 {@link Builder}，用于从零开始构建新表元数据。
   *
   * @return 空的构建器
   */
  public static Builder buildFromEmpty() {
    return new Builder();
  }

  /**
   * {@link TableMetadata} 的构建器，采用累积变更模式记录所有修改，最终通过 {@link #build()} 生成新的不可变元数据。
   *
   * <p>设计意图：构建器内部维护 schema、spec、sortOrder、snapshot、ref 等可变集合及其索引， 并通过 {@link MetadataUpdate}
   * 列表追踪所有变更。当 {@link #build()} 被调用时，变更列表会被写入 新元数据的 {@code changes} 字段，供事务系统感知具体修改内容。{@code
   * LAST_ADDED = -1} 作为哨兵值， 用于在变更序列中引用"最近添加的"schema/spec/sortOrder，避免在合并变更时硬编码 ID。
   */
  public static class Builder {
    private static final int LAST_ADDED = -1;

    private final TableMetadata base;
    private String metadataLocation;
    private int formatVersion;
    private String uuid;
    private Long lastUpdatedMillis;
    private String location;
    private long lastSequenceNumber;
    private int lastColumnId;
    private int currentSchemaId;
    private final List<Schema> schemas;
    private int defaultSpecId;
    private List<PartitionSpec> specs;
    private int lastAssignedPartitionId;
    private int defaultSortOrderId;
    private List<SortOrder> sortOrders;
    private final Map<String, String> properties;
    private long currentSnapshotId;
    private List<Snapshot> snapshots;
    private SerializableSupplier<List<Snapshot>> snapshotsSupplier;
    private final Map<String, SnapshotRef> refs;
    private final Map<Long, List<StatisticsFile>> statisticsFiles;

    // change tracking
    private final List<MetadataUpdate> changes;
    private final int startingChangeCount;
    private boolean discardChanges = false;
    private Integer lastAddedSchemaId = null;
    private Integer lastAddedSpecId = null;
    private Integer lastAddedOrderId = null;

    // handled in build
    private final List<HistoryEntry> snapshotLog;
    private String previousFileLocation;
    private final List<MetadataLogEntry> previousFiles;

    // indexes for convenience
    private final Map<Long, Snapshot> snapshotsById;
    private final Map<Integer, Schema> schemasById;
    private final Map<Integer, PartitionSpec> specsById;
    private final Map<Integer, SortOrder> sortOrdersById;

    /** 无参构造器，初始化一个空的构建器，生成随机 UUID，所有集合初始化为空。 */
    private Builder() {
      this.base = null;
      this.formatVersion = DEFAULT_TABLE_FORMAT_VERSION;
      this.lastSequenceNumber = INITIAL_SEQUENCE_NUMBER;
      this.uuid = UUID.randomUUID().toString();
      this.schemas = Lists.newArrayList();
      this.specs = Lists.newArrayList();
      this.sortOrders = Lists.newArrayList();
      this.properties = Maps.newHashMap();
      this.snapshots = Lists.newArrayList();
      this.currentSnapshotId = -1;
      this.changes = Lists.newArrayList();
      this.startingChangeCount = 0;
      this.snapshotLog = Lists.newArrayList();
      this.previousFiles = Lists.newArrayList();
      this.refs = Maps.newHashMap();
      this.statisticsFiles = Maps.newHashMap();
      this.snapshotsById = Maps.newHashMap();
      this.schemasById = Maps.newHashMap();
      this.specsById = Maps.newHashMap();
      this.sortOrdersById = Maps.newHashMap();
    }

    /**
     * 基于现有元数据构造 Builder，复制全部状态作为后续变更的起点。
     *
     * @param base 基线元数据
     */
    private Builder(TableMetadata base) {
      this.base = base;
      this.formatVersion = base.formatVersion;
      this.uuid = base.uuid;
      this.lastUpdatedMillis = null;
      this.location = base.location;
      this.lastSequenceNumber = base.lastSequenceNumber;
      this.lastColumnId = base.lastColumnId;
      this.currentSchemaId = base.currentSchemaId;
      this.schemas = Lists.newArrayList(base.schemas);
      this.defaultSpecId = base.defaultSpecId;
      this.specs = Lists.newArrayList(base.specs);
      this.lastAssignedPartitionId = base.lastAssignedPartitionId;
      this.defaultSortOrderId = base.defaultSortOrderId;
      this.sortOrders = Lists.newArrayList(base.sortOrders);
      this.properties = Maps.newHashMap(base.properties);
      this.currentSnapshotId = base.currentSnapshotId;
      this.snapshots = Lists.newArrayList(base.snapshots());
      this.changes = Lists.newArrayList(base.changes);
      this.startingChangeCount = changes.size();

      this.snapshotLog = Lists.newArrayList(base.snapshotLog);
      this.previousFileLocation = base.metadataFileLocation;
      this.previousFiles = base.previousFiles;
      this.refs = Maps.newHashMap(base.refs);
      this.statisticsFiles =
          base.statisticsFiles.stream().collect(Collectors.groupingBy(StatisticsFile::snapshotId));

      this.snapshotsById = Maps.newHashMap(base.snapshotsById);
      this.schemasById = Maps.newHashMap(base.schemasById);
      this.specsById = Maps.newHashMap(base.specsById);
      this.sortOrdersById = Maps.newHashMap(base.sortOrdersById);
    }

    /**
     * 设置本次构建产出的元数据文件位置。
     *
     * @param newMetadataLocation 元数据文件位置
     * @return 当前 Builder
     */
    public Builder withMetadataLocation(String newMetadataLocation) {
      this.metadataLocation = newMetadataLocation;
      return this;
    }

    /**
     * 若当前 UUID 为空，则随机生成一个并记录变更。
     *
     * @return 当前 Builder
     */
    public Builder assignUUID() {
      if (uuid == null) {
        this.uuid = UUID.randomUUID().toString();
        changes.add(new MetadataUpdate.AssignUUID(uuid));
      }

      return this;
    }

    /**
     * 显式设置表的 UUID。若与当前不同，则更新并记录变更。
     *
     * @param newUuid 新 UUID
     * @return 当前 Builder
     */
    public Builder assignUUID(String newUuid) {
      Preconditions.checkArgument(newUuid != null, "Cannot set uuid to null");

      if (!newUuid.equals(uuid)) {
        this.uuid = newUuid;
        changes.add(new MetadataUpdate.AssignUUID(uuid));
      }

      return this;
    }

    /**
     * 仅在创建新表时直接设置格式版本。其他场景请使用 {@link #upgradeFormatVersion(int)}。
     *
     * @param newFormatVersion 目标格式版本
     * @return 当前 Builder
     */
    // it is only safe to set the format version directly while creating tables
    // in all other cases, use upgradeFormatVersion
    private Builder setInitialFormatVersion(int newFormatVersion) {
      Preconditions.checkArgument(
          newFormatVersion <= SUPPORTED_TABLE_FORMAT_VERSION,
          "Unsupported format version: v%s (supported: v%s)",
          newFormatVersion,
          SUPPORTED_TABLE_FORMAT_VERSION);
      this.formatVersion = newFormatVersion;
      return this;
    }

    /**
     * 升级表格式版本（不可降级），并记录变更。
     *
     * @param newFormatVersion 目标格式版本
     * @return 当前 Builder
     */
    public Builder upgradeFormatVersion(int newFormatVersion) {
      Preconditions.checkArgument(
          newFormatVersion <= SUPPORTED_TABLE_FORMAT_VERSION,
          "Cannot upgrade table to unsupported format version: v%s (supported: v%s)",
          newFormatVersion,
          SUPPORTED_TABLE_FORMAT_VERSION);
      Preconditions.checkArgument(
          newFormatVersion >= formatVersion,
          "Cannot downgrade v%s table to v%s",
          formatVersion,
          newFormatVersion);

      if (newFormatVersion == formatVersion) {
        return this;
      }

      this.formatVersion = newFormatVersion;
      changes.add(new MetadataUpdate.UpgradeFormatVersion(newFormatVersion));

      return this;
    }

    /**
     * 添加新 Schema 并将其设为当前 Schema。
     *
     * @param newSchema 新 Schema
     * @param newLastColumnId 新的最大列 ID
     * @return 当前 Builder
     */
    public Builder setCurrentSchema(Schema newSchema, int newLastColumnId) {
      setCurrentSchema(addSchemaInternal(newSchema, newLastColumnId));
      return this;
    }

    /**
     * 将指定的 Schema ID 设为当前 Schema。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>若 schemaId 为 -1，则切换到上一次新增的 Schema；
     *   <li>若与当前相同，直接返回；
     *   <li>否则用新 Schema 重建所有分区规格与排序规则的 Schema 引用，确保它们仍可解析。
     * </ul>
     *
     * @param schemaId 目标 Schema ID，或 -1 表示上一次新增的 Schema
     * @return 当前 Builder
     */
    public Builder setCurrentSchema(int schemaId) {
      if (schemaId == -1) {
        ValidationException.check(
            lastAddedSchemaId != null, "Cannot set last added schema: no schema has been added");
        return setCurrentSchema(lastAddedSchemaId);
      }

      if (currentSchemaId == schemaId) {
        return this;
      }

      Schema schema = schemasById.get(schemaId);
      Preconditions.checkArgument(
          schema != null, "Cannot set current schema to unknown schema: %s", schemaId);

      // rebuild all the partition specs and sort orders for the new current schema
      this.specs =
          Lists.newArrayList(Iterables.transform(specs, spec -> updateSpecSchema(schema, spec)));
      specsById.clear();
      specsById.putAll(indexSpecs(specs));

      this.sortOrders =
          Lists.newArrayList(
              Iterables.transform(sortOrders, order -> updateSortOrderSchema(schema, order)));
      sortOrdersById.clear();
      sortOrdersById.putAll(indexSortOrders(sortOrders));

      this.currentSchemaId = schemaId;

      if (lastAddedSchemaId != null && lastAddedSchemaId == schemaId) {
        changes.add(new MetadataUpdate.SetCurrentSchema(LAST_ADDED));
      } else {
        changes.add(new MetadataUpdate.SetCurrentSchema(schemaId));
      }

      return this;
    }

    /**
     * 仅添加一个 Schema，不切换当前 Schema。
     *
     * @param schema 待添加的 Schema
     * @param newLastColumnId 新的最大列 ID
     * @return 当前 Builder
     */
    public Builder addSchema(Schema schema, int newLastColumnId) {
      // TODO: remove requirement for newLastColumnId
      addSchemaInternal(schema, newLastColumnId);
      return this;
    }

    /**
     * 添加分区规格并将其设为默认规格。
     *
     * @param spec 待添加的分区规格
     * @return 当前 Builder
     */
    public Builder setDefaultPartitionSpec(PartitionSpec spec) {
      setDefaultPartitionSpec(addPartitionSpecInternal(spec));
      return this;
    }

    /**
     * 将指定的分区规格 ID 设为默认规格。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>若 specId 为 -1，切换到上一次新增的分区规格；
     *   <li>若与当前默认相同，直接返回；
     *   <li>否则更新默认 ID 并记录变更。
     * </ul>
     *
     * @param specId 目标分区规格 ID，或 -1 表示上一次新增的规格
     * @return 当前 Builder
     */
    public Builder setDefaultPartitionSpec(int specId) {
      if (specId == -1) {
        ValidationException.check(
            lastAddedSpecId != null, "Cannot set last added spec: no spec has been added");
        return setDefaultPartitionSpec(lastAddedSpecId);
      }

      if (defaultSpecId == specId) {
        // the new spec is already current and no change is needed
        return this;
      }

      this.defaultSpecId = specId;
      if (lastAddedSpecId != null && lastAddedSpecId == specId) {
        changes.add(new MetadataUpdate.SetDefaultPartitionSpec(LAST_ADDED));
      } else {
        changes.add(new MetadataUpdate.SetDefaultPartitionSpec(specId));
      }

      return this;
    }

    /**
     * 以未绑定形式添加分区规格，绑定到当前 Schema。
     *
     * @param spec 未绑定的分区规格
     * @return 当前 Builder
     */
    public Builder addPartitionSpec(UnboundPartitionSpec spec) {
      addPartitionSpecInternal(spec.bind(schemasById.get(currentSchemaId)));
      return this;
    }

    /**
     * 添加已绑定的分区规格。
     *
     * @param spec 已绑定的分区规格
     * @return 当前 Builder
     */
    public Builder addPartitionSpec(PartitionSpec spec) {
      addPartitionSpecInternal(spec);
      return this;
    }

    /**
     * 添加排序规则并将其设为默认排序规则。
     *
     * @param order 待添加的排序规则
     * @return 当前 Builder
     */
    public Builder setDefaultSortOrder(SortOrder order) {
      setDefaultSortOrder(addSortOrderInternal(order));
      return this;
    }

    /**
     * 将指定的排序规则 ID 设为默认排序规则。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>若 sortOrderId 为 -1，切换到上一次新增的排序规则；
     *   <li>若与当前默认相同，直接返回；
     *   <li>否则更新默认 ID 并记录变更。
     * </ul>
     *
     * @param sortOrderId 目标排序规则 ID，或 -1 表示上一次新增的排序规则
     * @return 当前 Builder
     */
    public Builder setDefaultSortOrder(int sortOrderId) {
      if (sortOrderId == -1) {
        ValidationException.check(
            lastAddedOrderId != null,
            "Cannot set last added sort order: no sort order has been added");
        return setDefaultSortOrder(lastAddedOrderId);
      }

      if (sortOrderId == defaultSortOrderId) {
        return this;
      }

      this.defaultSortOrderId = sortOrderId;
      if (lastAddedOrderId != null && lastAddedOrderId == sortOrderId) {
        changes.add(new MetadataUpdate.SetDefaultSortOrder(LAST_ADDED));
      } else {
        changes.add(new MetadataUpdate.SetDefaultSortOrder(sortOrderId));
      }

      return this;
    }

    /**
     * 以未绑定形式添加排序规则，绑定到当前 Schema。
     *
     * @param order 未绑定的排序规则
     * @return 当前 Builder
     */
    public Builder addSortOrder(UnboundSortOrder order) {
      addSortOrderInternal(order.bind(schemasById.get(currentSchemaId)));
      return this;
    }

    /**
     * 添加已绑定的排序规则。
     *
     * @param order 已绑定的排序规则
     * @return 当前 Builder
     */
    public Builder addSortOrder(SortOrder order) {
      addSortOrderInternal(order);
      return this;
    }

    /**
     * 添加一个快照到元数据中，并更新相关索引、序列号与最后更新时间戳。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>对 null 快照视为 no-op；
     *   <li>校验 schema/spec/sortOrder 已存在、快照 ID 未重复；
     *   <li>v2+ 要求新快照序列号大于最近序列号，或新快照为根快照（无 parent）；
     *   <li>更新 lastUpdatedMillis、lastSequenceNumber 与快照索引，并记录 AddSnapshot 变更。
     * </ul>
     *
     * @param snapshot 待添加的快照
     * @return 当前 Builder
     */
    public Builder addSnapshot(Snapshot snapshot) {
      if (snapshot == null) {
        // change is a noop
        return this;
      }

      ValidationException.check(
          !schemas.isEmpty(), "Attempting to add a snapshot before a schema is added");
      ValidationException.check(
          !specs.isEmpty(), "Attempting to add a snapshot before a partition spec is added");
      ValidationException.check(
          !sortOrders.isEmpty(), "Attempting to add a snapshot before a sort order is added");

      ValidationException.check(
          !snapshotsById.containsKey(snapshot.snapshotId()),
          "Snapshot already exists for id: %s",
          snapshot.snapshotId());

      ValidationException.check(
          formatVersion == 1
              || snapshot.sequenceNumber() > lastSequenceNumber
              || snapshot.parentId() == null,
          "Cannot add snapshot with sequence number %s older than last sequence number %s",
          snapshot.sequenceNumber(),
          lastSequenceNumber);

      this.lastUpdatedMillis = snapshot.timestampMillis();
      this.lastSequenceNumber = snapshot.sequenceNumber();
      snapshots.add(snapshot);
      snapshotsById.put(snapshot.snapshotId(), snapshot);
      changes.add(new MetadataUpdate.AddSnapshot(snapshot));

      return this;
    }

    /**
     * 设置快照列表的懒加载供应器，用于在 build 后延迟读取快照。
     *
     * @param snapshotsSupplier 快照供应器
     * @return 当前 Builder
     */
    public Builder setSnapshotsSupplier(SerializableSupplier<List<Snapshot>> snapshotsSupplier) {
      this.snapshotsSupplier = snapshotsSupplier;
      return this;
    }

    /**
     * 添加快照并将其指向指定分支。
     *
     * @param snapshot 待添加的快照
     * @param branch 目标分支名
     * @return 当前 Builder
     */
    public Builder setBranchSnapshot(Snapshot snapshot, String branch) {
      addSnapshot(snapshot);
      setBranchSnapshotInternal(snapshot, branch);
      return this;
    }

    /**
     * 将已有快照指向指定分支。
     *
     * <p>逻辑：若该分支已指向同一快照则 no-op；否则校验快照存在并调用内部方法更新分支引用。
     *
     * @param snapshotId 待指向的快照 ID
     * @param branch 目标分支名
     * @return 当前 Builder
     */
    public Builder setBranchSnapshot(long snapshotId, String branch) {
      SnapshotRef ref = refs.get(branch);
      if (ref != null && ref.snapshotId() == snapshotId) {
        // change is a noop
        return this;
      }

      Snapshot snapshot = snapshotsById.get(snapshotId);
      ValidationException.check(
          snapshot != null, "Cannot set %s to unknown snapshot: %s", branch, snapshotId);

      setBranchSnapshotInternal(snapshot, branch);

      return this;
    }

    /**
     * 设置指定名称的快照引用（分支或标签）。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>若引用已存在且相同，则 no-op；
     *   <li>校验快照存在；若是本次新增的快照，则更新 lastUpdatedMillis；
     *   <li>若 name 是 main 分支，则同步更新 currentSnapshotId、lastUpdatedMillis 并向快照日志追加一条记录；
     *   <li>记录 SetSnapshotRef 变更。
     * </ul>
     *
     * @param name 引用名
     * @param ref 快照引用
     * @return 当前 Builder
     */
    public Builder setRef(String name, SnapshotRef ref) {
      SnapshotRef existingRef = refs.get(name);
      if (existingRef != null && existingRef.equals(ref)) {
        return this;
      }

      long snapshotId = ref.snapshotId();
      Snapshot snapshot = snapshotsById.get(snapshotId);
      ValidationException.check(
          snapshot != null, "Cannot set %s to unknown snapshot: %s", name, snapshotId);
      if (isAddedSnapshot(snapshotId)) {
        this.lastUpdatedMillis = snapshot.timestampMillis();
      }

      if (SnapshotRef.MAIN_BRANCH.equals(name)) {
        this.currentSnapshotId = ref.snapshotId();
        if (lastUpdatedMillis == null) {
          this.lastUpdatedMillis = System.currentTimeMillis();
        }

        snapshotLog.add(new SnapshotLogEntry(lastUpdatedMillis, ref.snapshotId()));
      }

      refs.put(name, ref);
      MetadataUpdate.SetSnapshotRef refUpdate =
          new MetadataUpdate.SetSnapshotRef(
              name,
              ref.snapshotId(),
              ref.type(),
              ref.minSnapshotsToKeep(),
              ref.maxSnapshotAgeMs(),
              ref.maxRefAgeMs());
      changes.add(refUpdate);
      return this;
    }

    /**
     * 移除指定名称的快照引用。
     *
     * <p>逻辑：若 name 是 main 分支，则同时清空当前快照 ID 与快照日志；从 refs 中移除引用并记录变更。
     *
     * @param name 引用名
     * @return 当前 Builder
     */
    public Builder removeRef(String name) {
      if (SnapshotRef.MAIN_BRANCH.equals(name)) {
        this.currentSnapshotId = -1;
        snapshotLog.clear();
      }

      SnapshotRef ref = refs.remove(name);
      if (ref != null) {
        changes.add(new MetadataUpdate.RemoveSnapshotRef(name));
      }

      return this;
    }

    /**
     * 为指定快照设置统计文件，覆盖该快照原有的统计文件。
     *
     * @param snapshotId 快照 ID
     * @param statisticsFile 统计文件
     * @return 当前 Builder
     */
    public Builder setStatistics(long snapshotId, StatisticsFile statisticsFile) {
      Preconditions.checkNotNull(statisticsFile, "statisticsFile is null");
      Preconditions.checkArgument(
          snapshotId == statisticsFile.snapshotId(),
          "snapshotId does not match: %s vs %s",
          snapshotId,
          statisticsFile.snapshotId());
      statisticsFiles.put(statisticsFile.snapshotId(), ImmutableList.of(statisticsFile));
      changes.add(new MetadataUpdate.SetStatistics(snapshotId, statisticsFile));
      return this;
    }

    /**
     * 移除指定快照的统计文件。
     *
     * @param snapshotId 快照 ID
     * @return 当前 Builder
     */
    public Builder removeStatistics(long snapshotId) {
      Preconditions.checkNotNull(snapshotId, "snapshotId is null");
      if (statisticsFiles.remove(snapshotId) == null) {
        return this;
      }
      changes.add(new MetadataUpdate.RemoveStatistics(snapshotId));
      return this;
    }

    /**
     * 抑制历史快照的元数据，便于懒加载。被抑制的快照不会从元数据中"删除"，也不会生成 RemoveSnapshot 变更。
     *
     * <p>判定标准：未被任何 ref 直接引用的快照视为历史快照。
     *
     * @return 当前 Builder
     */
    public Builder suppressHistoricalSnapshots() {
      Set<Long> refSnapshotIds =
          refs.values().stream().map(SnapshotRef::snapshotId).collect(Collectors.toSet());
      Set<Long> suppressedSnapshotIds = Sets.difference(snapshotsById.keySet(), refSnapshotIds);
      rewriteSnapshotsInternal(suppressedSnapshotIds, true);
      return this;
    }

    /** 按快照对象列表移除快照。 */
    public Builder removeSnapshots(List<Snapshot> snapshotsToRemove) {
      Set<Long> idsToRemove =
          snapshotsToRemove.stream().map(Snapshot::snapshotId).collect(Collectors.toSet());
      return removeSnapshots(idsToRemove);
    }

    /** 按快照 ID 集合移除快照。 */
    public Builder removeSnapshots(Collection<Long> idsToRemove) {
      return rewriteSnapshotsInternal(idsToRemove, false);
    }

    /**
     * 通过移除指定 ID 的快照来重写构建器的快照列表。
     *
     * <p>逻辑：遍历快照列表，将被移除的快照从索引中删除并移除其统计文件； 若 suppress 为 false 则记录 RemoveSnapshot 变更。最后清理指向已移除快照的悬空
     * ref。
     *
     * @param idsToRemove 待移除的快照 ID 集合
     * @param suppress 为 true 时表示抑制快照（不生成变更，保留历史），为 false 时表示真正移除
     * @return this 用于链式调用
     */
    private Builder rewriteSnapshotsInternal(Collection<Long> idsToRemove, boolean suppress) {
      List<Snapshot> retainedSnapshots =
          Lists.newArrayListWithExpectedSize(snapshots.size() - idsToRemove.size());
      for (Snapshot snapshot : snapshots) {
        long snapshotId = snapshot.snapshotId();
        if (idsToRemove.contains(snapshotId)) {
          snapshotsById.remove(snapshotId);
          if (!suppress) {
            changes.add(new MetadataUpdate.RemoveSnapshot(snapshotId));
          }
          removeStatistics(snapshotId);
        } else {
          retainedSnapshots.add(snapshot);
        }
      }

      this.snapshots = retainedSnapshots;

      // remove any refs that are no longer valid
      Set<String> danglingRefs = Sets.newHashSet();
      for (Map.Entry<String, SnapshotRef> refEntry : refs.entrySet()) {
        if (!snapshotsById.containsKey(refEntry.getValue().snapshotId())) {
          danglingRefs.add(refEntry.getKey());
        }
      }

      danglingRefs.forEach(this::removeRef);

      return this;
    }

    /**
     * 增量设置或更新属性，并记录 SetProperties 变更。
     *
     * @param updated 待更新的属性
     * @return 当前 Builder
     */
    public Builder setProperties(Map<String, String> updated) {
      if (updated.isEmpty()) {
        return this;
      }

      properties.putAll(updated);
      changes.add(new MetadataUpdate.SetProperties(updated));

      return this;
    }

    /**
     * 移除指定属性键集合，并记录 RemoveProperties 变更。
     *
     * @param removed 待移除的属性键集合
     * @return 当前 Builder
     */
    public Builder removeProperties(Set<String> removed) {
      if (removed.isEmpty()) {
        return this;
      }

      removed.forEach(properties::remove);
      changes.add(new MetadataUpdate.RemoveProperties(removed));

      return this;
    }

    /**
     * 设置表的存储位置，并记录 SetLocation 变更。
     *
     * @param newLocation 新位置
     * @return 当前 Builder
     */
    public Builder setLocation(String newLocation) {
      if (location != null && location.equals(newLocation)) {
        return this;
      }

      this.location = newLocation;
      changes.add(new MetadataUpdate.SetLocation(newLocation));

      return this;
    }

    /** 标记后续 build 时丢弃变更记录（仍会构造新的元数据，但 changes 列表为空）。 */
    public Builder discardChanges() {
      this.discardChanges = true;
      return this;
    }

    /** 设置上一个元数据文件的位置，用于构造历史元数据日志。 */
    public Builder setPreviousFileLocation(String previousFileLocation) {
      this.previousFileLocation = previousFileLocation;
      return this;
    }

    /** 判断自构建器创建以来是否有实质变更（含变更数量、discardChanges 标记或元数据位置变化）。 */
    private boolean hasChanges() {
      return changes.size() != startingChangeCount
          || (discardChanges && changes.size() > 0)
          || metadataLocation != null;
    }

    /**
     * 基于当前构建器的累积状态生成不可变的 {@link TableMetadata} 实例。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>若无变更则直接返回 base 元数据；
     *   <li>若 lastUpdatedMillis 未设置则取当前时间；
     *   <li>校验"元数据位置 + 变更"互斥约束（关联文件的元数据不能带变更）；
     *   <li>校验当前 schema 与默认 spec/sortOrder 的兼容性；
     *   <li>构建历史元数据文件列表（addPreviousFile）和快照日志（updateSnapshotLog）；
     *   <li>构造并返回新的 TableMetadata，若 discardChanges 则不携带变更列表。
     * </ol>
     *
     * @return 新的不可变表元数据
     */
    public TableMetadata build() {
      if (!hasChanges()) {
        return base;
      }

      if (lastUpdatedMillis == null) {
        this.lastUpdatedMillis = System.currentTimeMillis();
      }

      // when associated with a metadata file, table metadata must have no changes so that the
      // metadata matches exactly
      // what is in the metadata file, which does not store changes. metadata location with changes
      // is inconsistent.
      Preconditions.checkArgument(
          changes.size() == 0 || discardChanges || metadataLocation == null,
          "Cannot set metadata location with changes to table metadata: %s changes",
          changes.size());

      Schema schema = schemasById.get(currentSchemaId);
      PartitionSpec.checkCompatibility(specsById.get(defaultSpecId), schema);
      SortOrder.checkCompatibility(sortOrdersById.get(defaultSortOrderId), schema);

      List<MetadataLogEntry> metadataHistory;
      if (base == null) {
        metadataHistory = Lists.newArrayList();
      } else {
        metadataHistory =
            addPreviousFile(
                previousFiles, previousFileLocation, base.lastUpdatedMillis(), properties);
      }
      List<HistoryEntry> newSnapshotLog =
          updateSnapshotLog(snapshotLog, snapshotsById, currentSnapshotId, changes);

      return new TableMetadata(
          metadataLocation,
          formatVersion,
          uuid,
          location,
          lastSequenceNumber,
          lastUpdatedMillis,
          lastColumnId,
          currentSchemaId,
          ImmutableList.copyOf(schemas),
          defaultSpecId,
          ImmutableList.copyOf(specs),
          lastAssignedPartitionId,
          defaultSortOrderId,
          ImmutableList.copyOf(sortOrders),
          ImmutableMap.copyOf(properties),
          currentSnapshotId,
          ImmutableList.copyOf(snapshots),
          snapshotsSupplier,
          ImmutableList.copyOf(newSnapshotLog),
          ImmutableList.copyOf(metadataHistory),
          ImmutableMap.copyOf(refs),
          statisticsFiles.values().stream().flatMap(List::stream).collect(Collectors.toList()),
          discardChanges ? ImmutableList.of() : ImmutableList.copyOf(changes));
    }

    /**
     * 内部添加 Schema 的实现：复用已有 ID 或分配新 ID，更新 lastColumnId 并记录变更。
     *
     * <p>逻辑：若 Schema 已存在且 lastColumnId 未变则仅更新 lastAddedSchemaId；否则分配新 Schema ID、 更新 lastColumnId、将新
     * Schema 加入列表与索引，并记录 AddSchema 变更。
     *
     * @param schema 待添加的 Schema
     * @param newLastColumnId 新的最大列 ID
     * @return 分配或复用的 Schema ID
     */
    private int addSchemaInternal(Schema schema, int newLastColumnId) {
      Preconditions.checkArgument(
          newLastColumnId >= lastColumnId,
          "Invalid last column ID: %s < %s (previous last column ID)",
          newLastColumnId,
          lastColumnId);

      int newSchemaId = reuseOrCreateNewSchemaId(schema);
      boolean schemaFound = schemasById.containsKey(newSchemaId);
      if (schemaFound && newLastColumnId == lastColumnId) {
        // the new spec and last column id is already current and no change is needed
        // update lastAddedSchemaId if the schema was added in this set of changes (since it is now
        // the last)
        boolean isNewSchema =
            lastAddedSchemaId != null
                && changes(MetadataUpdate.AddSchema.class)
                    .anyMatch(added -> added.schema().schemaId() == newSchemaId);
        this.lastAddedSchemaId = isNewSchema ? newSchemaId : null;
        return newSchemaId;
      }

      this.lastColumnId = newLastColumnId;

      Schema newSchema;
      if (newSchemaId != schema.schemaId()) {
        newSchema = new Schema(newSchemaId, schema.columns(), schema.identifierFieldIds());
      } else {
        newSchema = schema;
      }

      if (!schemaFound) {
        schemas.add(newSchema);
        schemasById.put(newSchema.schemaId(), newSchema);
      }

      changes.add(new MetadataUpdate.AddSchema(newSchema, lastColumnId));

      this.lastAddedSchemaId = newSchemaId;

      return newSchemaId;
    }

    /** 复用结构相同的已有 Schema 的 ID，否则分配最高 ID + 1 作为新 ID。 */
    private int reuseOrCreateNewSchemaId(Schema newSchema) {
      // if the schema already exists, use its id; otherwise use the highest id + 1
      int newSchemaId = currentSchemaId;
      for (Schema schema : schemas) {
        if (schema.sameSchema(newSchema)) {
          return schema.schemaId();
        } else if (schema.schemaId() >= newSchemaId) {
          newSchemaId = schema.schemaId() + 1;
        }
      }
      return newSchemaId;
    }

    /**
     * 内部添加分区规格的实现：复用或分配 spec ID，校验与当前 Schema 的兼容性，重建规格并记录变更。
     *
     * @param spec 待添加的分区规格
     * @return 分配或复用的分区规格 ID
     */
    private int addPartitionSpecInternal(PartitionSpec spec) {
      int newSpecId = reuseOrCreateNewSpecId(spec);
      if (specsById.containsKey(newSpecId)) {
        // update lastAddedSpecId if the spec was added in this set of changes (since it is now the
        // last)
        boolean isNewSpec =
            lastAddedSpecId != null
                && changes(MetadataUpdate.AddPartitionSpec.class)
                    .anyMatch(added -> added.spec().specId() == lastAddedSpecId);
        this.lastAddedSpecId = isNewSpec ? newSpecId : null;
        return newSpecId;
      }

      Schema schema = schemasById.get(currentSchemaId);
      PartitionSpec.checkCompatibility(spec, schema);
      ValidationException.check(
          formatVersion > 1 || PartitionSpec.hasSequentialIds(spec),
          "Spec does not use sequential IDs that are required in v1: %s",
          spec);

      PartitionSpec newSpec = freshSpec(newSpecId, schema, spec);
      this.lastAssignedPartitionId =
          Math.max(lastAssignedPartitionId, newSpec.lastAssignedFieldId());
      specs.add(newSpec);
      specsById.put(newSpecId, newSpec);

      changes.add(new MetadataUpdate.AddPartitionSpec(newSpec));

      this.lastAddedSpecId = newSpecId;

      return newSpecId;
    }

    /** 复用兼容的已有分区规格 ID，否则分配最高 ID + 1 作为新 ID。 */
    private int reuseOrCreateNewSpecId(PartitionSpec newSpec) {
      // if the spec already exists, use the same ID. otherwise, use 1 more than the highest ID.
      int newSpecId = INITIAL_SPEC_ID;
      for (PartitionSpec spec : specs) {
        if (newSpec.compatibleWith(spec)) {
          return spec.specId();
        } else if (newSpecId <= spec.specId()) {
          newSpecId = spec.specId() + 1;
        }
      }

      return newSpecId;
    }

    /**
     * 内部添加排序规则的实现：复用或分配 order ID，校验兼容性，重建排序规则并记录变更。
     *
     * @param order 待添加的排序规则
     * @return 分配或复用的排序规则 ID
     */
    private int addSortOrderInternal(SortOrder order) {
      int newOrderId = reuseOrCreateNewSortOrderId(order);
      if (sortOrdersById.containsKey(newOrderId)) {
        // update lastAddedOrderId if the order was added in this set of changes (since it is now
        // the last)
        boolean isNewOrder =
            lastAddedOrderId != null
                && changes(MetadataUpdate.AddSortOrder.class)
                    .anyMatch(added -> added.sortOrder().orderId() == lastAddedOrderId);
        this.lastAddedOrderId = isNewOrder ? newOrderId : null;
        return newOrderId;
      }

      Schema schema = schemasById.get(currentSchemaId);
      SortOrder.checkCompatibility(order, schema);

      SortOrder newOrder;
      if (order.isUnsorted()) {
        newOrder = SortOrder.unsorted();
      } else {
        // rebuild the sort order using new column ids
        newOrder = freshSortOrder(newOrderId, schema, order);
      }

      sortOrders.add(newOrder);
      sortOrdersById.put(newOrderId, newOrder);

      changes.add(new MetadataUpdate.AddSortOrder(newOrder));

      this.lastAddedOrderId = newOrderId;

      return newOrderId;
    }

    /** 复用相同的已有排序规则 ID，否则分配最高 ID + 1 作为新 ID；无序排序规则使用固定 ID。 */
    private int reuseOrCreateNewSortOrderId(SortOrder newOrder) {
      if (newOrder.isUnsorted()) {
        return SortOrder.unsorted().orderId();
      }

      // determine the next order id
      int newOrderId = INITIAL_SORT_ORDER_ID;
      for (SortOrder order : sortOrders) {
        if (order.sameOrder(newOrder)) {
          return order.orderId();
        } else if (newOrderId <= order.orderId()) {
          newOrderId = order.orderId() + 1;
        }
      }

      return newOrderId;
    }

    /**
     * 内部方法：将指定分支指向某个快照。若分支已存在则校验其为分支类型（非 tag）， 并基于原 ref 属性重建新 ref；若不存在则创建新分支 ref。最终通过 {@link
     * #setRef} 落实。
     *
     * @param snapshot 目标快照
     * @param branch 分支名称
     */
    private void setBranchSnapshotInternal(Snapshot snapshot, String branch) {
      long replacementSnapshotId = snapshot.snapshotId();
      SnapshotRef ref = refs.get(branch);
      if (ref != null) {
        ValidationException.check(ref.isBranch(), "Cannot update branch: %s is a tag", branch);
        if (ref.snapshotId() == replacementSnapshotId) {
          return;
        }
      }

      ValidationException.check(
          formatVersion == 1 || snapshot.sequenceNumber() <= lastSequenceNumber,
          "Last sequence number %s is less than existing snapshot sequence number %s",
          lastSequenceNumber,
          snapshot.sequenceNumber());

      SnapshotRef newRef;
      if (ref != null) {
        newRef = SnapshotRef.builderFrom(ref, replacementSnapshotId).build();
      } else {
        newRef = SnapshotRef.branchBuilder(replacementSnapshotId).build();
      }

      setRef(branch, newRef);
    }

    /**
     * 将上一个元数据文件位置添加到历史记录列表中，并根据配置的最大保留数量截断旧记录。
     *
     * @param previousFiles 已有的历史元数据文件列表
     * @param previousFileLocation 上一个元数据文件的位置
     * @param timestampMillis 时间戳
     * @param properties 表属性（用于读取最大保留数量配置）
     * @return 更新后的历史元数据文件列表
     */
    private static List<MetadataLogEntry> addPreviousFile(
        List<MetadataLogEntry> previousFiles,
        String previousFileLocation,
        long timestampMillis,
        Map<String, String> properties) {
      if (previousFileLocation == null) {
        return previousFiles;
      }

      int maxSize =
          Math.max(
              1,
              PropertyUtil.propertyAsInt(
                  properties,
                  TableProperties.METADATA_PREVIOUS_VERSIONS_MAX,
                  TableProperties.METADATA_PREVIOUS_VERSIONS_MAX_DEFAULT));

      List<MetadataLogEntry> newMetadataLog;
      if (previousFiles.size() >= maxSize) {
        int removeIndex = previousFiles.size() - maxSize + 1;
        newMetadataLog =
            Lists.newArrayList(previousFiles.subList(removeIndex, previousFiles.size()));
      } else {
        newMetadataLog = Lists.newArrayList(previousFiles);
      }
      newMetadataLog.add(new MetadataLogEntry(timestampMillis, previousFileLocation));

      return newMetadataLog;
    }

    /**
     * 找出"中间快照"——即被添加但最终未成为当前快照的快照 ID 集合。
     *
     * <p>事务可能在内部产生多个快照，但只有最后一个会成为当前快照。每个中间快照在被添加时 都会写入快照日志（假定它会成为当前快照），因此当日志中存在多个快照更新时，需要通过
     * 抑制这些中间快照的日志条目来修正。
     *
     * <p>判定标准：被 AddSnapshot 添加、随后被 SetSnapshotRef 指向 main 分支， 但其 ID 不等于最终 currentSnapshotId
     * 的快照即为中间快照。
     *
     * @param changes 本次构建器累积的变更列表
     * @param currentSnapshotId 最终的当前快照 ID
     * @return 中间快照的 ID 集合
     */
    private static Set<Long> intermediateSnapshotIdSet(
        List<MetadataUpdate> changes, long currentSnapshotId) {
      Set<Long> addedSnapshotIds = Sets.newHashSet();
      Set<Long> intermediateSnapshotIds = Sets.newHashSet();
      for (MetadataUpdate update : changes) {
        if (update instanceof MetadataUpdate.AddSnapshot) {
          // adds must always come before set current snapshot
          MetadataUpdate.AddSnapshot addSnapshot = (MetadataUpdate.AddSnapshot) update;
          addedSnapshotIds.add(addSnapshot.snapshot().snapshotId());
        } else if (update instanceof MetadataUpdate.SetSnapshotRef) {
          MetadataUpdate.SetSnapshotRef setRef = (MetadataUpdate.SetSnapshotRef) update;
          long snapshotId = setRef.snapshotId();
          if (addedSnapshotIds.contains(snapshotId)
              && SnapshotRef.MAIN_BRANCH.equals(setRef.name())
              && snapshotId != currentSnapshotId) {
            intermediateSnapshotIds.add(snapshotId);
          }
        }
      }

      return intermediateSnapshotIds;
    }

    /**
     * 根据中间快照与已移除快照情况，重建快照日志。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>若无中间快照且无移除快照，直接返回原日志；
     *   <li>遍历原日志：跳过中间快照条目；若发现日志条目对应的快照已不存在（且确有移除操作）， 则清空此前累积的全部日志，避免出现历史断层导致时间旅行查询错误；
     *   <li>校验最后一条日志的快照 ID 必须等于当前快照 ID。
     * </ul>
     *
     * @param snapshotLog 原快照日志
     * @param snapshotsById 按快照 ID 索引的快照
     * @param currentSnapshotId 当前快照 ID
     * @param changes 本次构建器累积的变更列表
     * @return 修正后的快照日志
     */
    private static List<HistoryEntry> updateSnapshotLog(
        List<HistoryEntry> snapshotLog,
        Map<Long, Snapshot> snapshotsById,
        long currentSnapshotId,
        List<MetadataUpdate> changes) {
      Set<Long> intermediateSnapshotIds = intermediateSnapshotIdSet(changes, currentSnapshotId);
      boolean hasIntermediateSnapshots = !intermediateSnapshotIds.isEmpty();
      boolean hasRemovedSnapshots =
          changes.stream().anyMatch(MetadataUpdate.RemoveSnapshot.class::isInstance);

      if (!hasIntermediateSnapshots && !hasRemovedSnapshots) {
        return snapshotLog;
      }

      // update the snapshot log
      List<HistoryEntry> newSnapshotLog = Lists.newArrayList();
      for (HistoryEntry logEntry : snapshotLog) {
        long snapshotId = logEntry.snapshotId();
        if (snapshotsById.containsKey(snapshotId)) {
          if (!intermediateSnapshotIds.contains(snapshotId)) {
            // copy the log entries that are still valid
            newSnapshotLog.add(logEntry);
          }
        } else if (hasRemovedSnapshots) {
          // any invalid entry causes the history before it to be removed. otherwise, there could be
          // history gaps that cause time-travel queries to produce incorrect results. for example,
          // if history is [(t1, s1), (t2, s2), (t3, s3)] and s2 is removed, the history cannot be
          // [(t1, s1), (t3, s3)] because it appears that s3 was current during the time between t2
          // and t3 when in fact s2 was the current snapshot.
          newSnapshotLog.clear();
        }
      }

      if (snapshotsById.get(currentSnapshotId) != null) {
        ValidationException.check(
            Iterables.getLast(newSnapshotLog).snapshotId() == currentSnapshotId,
            "Cannot set invalid snapshot log: latest entry is not the current snapshot");
      }

      return newSnapshotLog;
    }

    /** 判断指定快照 ID 是否由本次构建器的 AddSnapshot 变更新增。 */
    private boolean isAddedSnapshot(long snapshotId) {
      return changes(MetadataUpdate.AddSnapshot.class)
          .anyMatch(add -> add.snapshot().snapshotId() == snapshotId);
    }

    /**
     * 按变更类型过滤并转换变更列表。
     *
     * @param updateClass 目标变更类型
     * @param <U> 变更类型的泛型上界
     * @return 该类型变更的流
     */
    private <U extends MetadataUpdate> Stream<U> changes(Class<U> updateClass) {
      return changes.stream().filter(updateClass::isInstance).map(updateClass::cast);
    }
  }
}
