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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.transforms.Transforms;

/**
 * 元数据表的抽象基类：为各类元数据表（entries/snapshots/history 等）提供公共实现。
 *
 * <p>所属模块：iceberg-core（核心实现层），元数据表体系的根抽象类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有底层数据表 {@link BaseTable} 引用，并把 schema/spec/sortOrder/快照/历史等只读 API 委托给底层表（或返回固定值）。
 *   <li>提供把数据表分区 spec 转换为“元数据表投影 spec”的工具方法 {@link #transformSpec}， 便于把用户谓词改写为针对 partition 列的过滤条件。
 *   <li>通过 {@link #writeReplace()} 在 Java 序列化时把本对象替换为基于 {@link StaticTableOperations}
 *       的只读副本，使反序列化端无需访问 Catalog。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseReadOnlyTable} 自动禁用所有写操作；额外实现 {@link HasTableOperations} 与 {@link
 *       Serializable} 以支持表操作与跨进程传递。
 *   <li>把元数据表自身的 spec/sortOrder 固定为 unpartitioned/unsorted，因为元数据表自身不参与 底层数据分区；分区投影通过 transformSpec
 *       在表达式层处理。
 *   <li>序列化替换为 StaticTable 副本：避免反序列化端依赖具体 Catalog，支持离线分析场景。
 * </ul>
 *
 * <p>上下游关系：被 {@link AllEntriesTable}、{@link SnapshotsTable} 等所有元数据表子类继承； 上游被元数据表工厂、引擎查询计划器调用。
 */
public abstract class BaseMetadataTable extends BaseReadOnlyTable
    implements HasTableOperations, Serializable {
  private final PartitionSpec spec = PartitionSpec.unpartitioned();
  private final SortOrder sortOrder = SortOrder.unsorted();
  private final BaseTable table;
  private final String name;

  /**
   * 构造方法。
   *
   * <p>逻辑：调用父类构造，描述符固定为 "metadata"；校验底层表必须是 {@link BaseTable} （非数据表无法承载元数据视图），保存表引用与元数据表名称。
   *
   * @param table 底层数据表
   * @param name 元数据表名称
   * @throws IllegalArgumentException 当 table 不是 BaseTable 时抛出
   */
  protected BaseMetadataTable(Table table, String name) {
    super("metadata");
    Preconditions.checkArgument(
        table instanceof BaseTable, "Cannot create metadata table for non-data table: %s", table);
    this.table = (BaseTable) table;
    this.name = name;
  }

  /**
   * 将数据表分区 spec 转换为可用于在元数据表上投影用户谓词的 spec。
   *
   * <p>逻辑：以元数据表 schema 为基础新建一个 spec，specId 沿用原 spec；对原 spec 中每个 {@link PartitionField}，以 identity
   * transform 重新加入，字段名/字段 id 不变。
   *
   * <p>设计要点：元数据表中分区值以 partition.X 命名列，identity transform 使表达式投影时 自动去除 "partition."
   * 前缀，并把非分区字段谓词过滤掉，从而把针对元数据表的过滤改写为 针对底层分区值的过滤。
   *
   * @param metadataTableSchema 元数据表 schema
   * @param spec 底层数据表 spec
   * @return 可用于 inclusive projection 的 spec
   */
  static PartitionSpec transformSpec(Schema metadataTableSchema, PartitionSpec spec) {
    PartitionSpec.Builder builder =
        PartitionSpec.builderFor(metadataTableSchema)
            .withSpecId(spec.specId())
            .checkConflicts(false);

    for (PartitionField field : spec.fields()) {
      builder.add(field.fieldId(), field.fieldId(), field.name(), Transforms.identity());
    }
    return builder.build();
  }

  /**
   * 批量转换多个分区 spec（见 {@link #transformSpec(Schema, PartitionSpec)}）。
   *
   * @param metadataTableSchema 元数据表 schema
   * @param specs 底层数据表 spec 映射
   * @return 转换后的 spec 映射，键为 specId
   */
  static Map<Integer, PartitionSpec> transformSpecs(
      Schema metadataTableSchema, Map<Integer, PartitionSpec> specs) {
    return specs.values().stream()
        .map(spec -> transformSpec(metadataTableSchema, spec))
        .collect(Collectors.toMap(PartitionSpec::specId, spec -> spec));
  }

  /** 子类返回本元数据表的类型标识。 */
  abstract MetadataTableType metadataTableType();

  /** 返回底层 {@link BaseTable}。 */
  protected BaseTable table() {
    return table;
  }

  /**
   * 返回底层表的 {@link TableOperations}。
   *
   * @deprecated 1.4.0 起将移除，不应再使用元数据表的 TableOperations。
   */
  @Override
  @Deprecated
  public TableOperations operations() {
    return table.operations();
  }

  /** 返回元数据表名称。 */
  @Override
  public String name() {
    return name;
  }

  /** 返回底层表的 {@link FileIO}。 */
  @Override
  public FileIO io() {
    return table().io();
  }

  /** 返回底层表的存储位置。 */
  @Override
  public String location() {
    return table().location();
  }

  /** 返回底层表的 {@link EncryptionManager}。 */
  @Override
  public EncryptionManager encryption() {
    return table().encryption();
  }

  /** 返回底层表的 {@link LocationProvider}。 */
  @Override
  public LocationProvider locationProvider() {
    return table().locationProvider();
  }

  /** 刷新底层表元数据。 */
  @Override
  public void refresh() {
    table().refresh();
  }

  /** 元数据表只暴露自身单一 schema（INITIAL_SCHEMA_ID）。 */
  @Override
  public Map<Integer, Schema> schemas() {
    return ImmutableMap.of(TableMetadata.INITIAL_SCHEMA_ID, schema());
  }

  /** 元数据表自身不分区，返回 unpartitioned。 */
  @Override
  public PartitionSpec spec() {
    return spec;
  }

  /** 元数据表只暴露自身单一 spec。 */
  @Override
  public Map<Integer, PartitionSpec> specs() {
    return ImmutableMap.of(spec.specId(), spec);
  }

  /** 元数据表自身不排序，返回 unsorted。 */
  @Override
  public SortOrder sortOrder() {
    return sortOrder;
  }

  /** 元数据表只暴露自身单一 sortOrder。 */
  @Override
  public Map<Integer, SortOrder> sortOrders() {
    return ImmutableMap.of(sortOrder.orderId(), sortOrder);
  }

  /** 元数据表自身无 properties，返回空 Map。 */
  @Override
  public Map<String, String> properties() {
    return ImmutableMap.of();
  }

  /** 返回底层表当前快照。 */
  @Override
  public Snapshot currentSnapshot() {
    return table().currentSnapshot();
  }

  /** 返回底层表所有快照。 */
  @Override
  public Iterable<Snapshot> snapshots() {
    return table().snapshots();
  }

  /** 按快照 id 查询底层表快照。 */
  @Override
  public Snapshot snapshot(long snapshotId) {
    return table().snapshot(snapshotId);
  }

  /** 返回底层表历史记录。 */
  @Override
  public List<HistoryEntry> history() {
    return table().history();
  }

  /** 元数据表不暴露统计文件，返回空列表。 */
  @Override
  public List<StatisticsFile> statisticsFiles() {
    return ImmutableList.of();
  }

  /** 返回底层表快照引用映射。 */
  @Override
  public Map<String, SnapshotRef> refs() {
    return table().refs();
  }

  @Override
  public String toString() {
    return name();
  }

  /**
   * Java 序列化替换：把本元数据表替换为 {@link SerializableTable#copyOf} 生成的只读副本。
   *
   * <p>设计意图：副本基于 {@link StaticTableOperations}，反序列化端无需访问 Catalog 即可读取 表数据，支持离线/分布式分析场景。
   *
   * @return 可序列化的只读表副本
   */
  final Object writeReplace() {
    return SerializableTable.copyOf(this);
  }
}
