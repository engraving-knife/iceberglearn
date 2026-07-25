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
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.metrics.LoggingMetricsReporter;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Iceberg {@link Table} 的核心实现，通过持有 {@link TableOperations} 桥接元数据存储。
 *
 * <p>所属模块：iceberg-core。所有表操作的统一入口，被 Catalog 各实现作为表对象的实际类型返回。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 {@link Table} 接口的所有方法委托给 {@link TableOperations#current()} 的 {@link
 *       TableMetadata}，保证读取的元数据与最新提交一致。
 *   <li>提供各类更新操作的工厂方法（schema、分区、排序、append、overwrite、rowDelta 等）。
 *   <li>承载 {@link MetricsReporter} 用于扫描/提交指标上报。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable} 并通过 {@link #writeReplace()} 替换为 {@link
 *       SerializableTable}，使序列化后的对象不再依赖 Catalog，便于跨进程传递只读视图。
 *   <li>更新类操作（append/overwrite 等）创建独立的更新对象而非内嵌实现，保证多次并发更新互不干扰。
 * </ul>
 *
 * <p>上下游关系：由 {@link BaseMetastoreCatalog}、{@link CatalogUtil} 等创建；下游通过 {@link TableOperations} 访问具体
 * metastore 与文件系统。
 */
public class BaseTable implements Table, HasTableOperations, Serializable {
  private final TableOperations ops;
  private final String name;
  private final MetricsReporter reporter;

  public BaseTable(TableOperations ops, String name) {
    this(ops, name, LoggingMetricsReporter.instance());
  }

  /**
   * 构造 BaseTable，显式指定 metrics reporter。
   *
   * @param ops 表操作对象，负责元数据读写
   * @param name 表的全限定名
   * @param reporter 指标上报器，不能为 null
   */
  public BaseTable(TableOperations ops, String name, MetricsReporter reporter) {
    Preconditions.checkNotNull(reporter, "reporter cannot be null");
    this.ops = ops;
    this.name = name;
    this.reporter = reporter;
  }

  /** 包级可见：返回当前表使用的指标上报器。 */
  MetricsReporter reporter() {
    return reporter;
  }

  @Override
  public TableOperations operations() {
    return ops;
  }

  @Override
  public String name() {
    return name;
  }

  /** 刷新表元数据，委托给 {@link TableOperations#refresh()}。 */
  @Override
  public void refresh() {
    ops.refresh();
  }

  /** 创建一个新的全表扫描，注入当前 reporter。 */
  @Override
  public TableScan newScan() {
    return new DataTableScan(
        this, schema(), ImmutableTableScanContext.builder().metricsReporter(reporter).build());
  }

  /** 创建增量追加扫描，用于读取指定快照之后的追加数据。 */
  @Override
  public IncrementalAppendScan newIncrementalAppendScan() {
    return new BaseIncrementalAppendScan(
        this, schema(), ImmutableTableScanContext.builder().metricsReporter(reporter).build());
  }

  /** 创建增量变更日志扫描，用于消费 insert/delete/update 事件流。 */
  @Override
  public IncrementalChangelogScan newIncrementalChangelogScan() {
    return new BaseIncrementalChangelogScan(this);
  }

  @Override
  public Schema schema() {
    return ops.current().schema();
  }

  @Override
  public Map<Integer, Schema> schemas() {
    return ops.current().schemasById();
  }

  @Override
  public PartitionSpec spec() {
    return ops.current().spec();
  }

  @Override
  public Map<Integer, PartitionSpec> specs() {
    return ops.current().specsById();
  }

  @Override
  public SortOrder sortOrder() {
    return ops.current().sortOrder();
  }

  @Override
  public Map<Integer, SortOrder> sortOrders() {
    return ops.current().sortOrdersById();
  }

  @Override
  public Map<String, String> properties() {
    return ops.current().properties();
  }

  @Override
  public String location() {
    return ops.current().location();
  }

  @Override
  public Snapshot currentSnapshot() {
    return ops.current().currentSnapshot();
  }

  @Override
  public Snapshot snapshot(long snapshotId) {
    return ops.current().snapshot(snapshotId);
  }

  @Override
  public Iterable<Snapshot> snapshots() {
    return ops.current().snapshots();
  }

  @Override
  public List<HistoryEntry> history() {
    return ops.current().snapshotLog();
  }

  /** 创建 schema 更新操作。 */
  @Override
  public UpdateSchema updateSchema() {
    return new SchemaUpdate(ops);
  }

  /** 创建分区规则更新操作。 */
  @Override
  public UpdatePartitionSpec updateSpec() {
    return new BaseUpdatePartitionSpec(ops);
  }

  /** 创建表属性更新操作。 */
  @Override
  public UpdateProperties updateProperties() {
    return new PropertiesUpdate(ops);
  }

  /** 创建排序规则替换操作。 */
  @Override
  public ReplaceSortOrder replaceSortOrder() {
    return new BaseReplaceSortOrder(ops);
  }

  /** 创建存储位置更新操作。 */
  @Override
  public UpdateLocation updateLocation() {
    return new SetLocation(ops);
  }

  /** 创建合并追加操作（会重写 manifest 以合并小文件）。 */
  @Override
  public AppendFiles newAppend() {
    return new MergeAppend(name, ops).reportWith(reporter);
  }

  /** 创建快速追加操作（不重写 manifest，直接追加新 manifest）。 */
  @Override
  public AppendFiles newFastAppend() {
    return new FastAppend(name, ops).reportWith(reporter);
  }

  /** 创建文件重写操作（用于 compaction 等）。 */
  @Override
  public RewriteFiles newRewrite() {
    return new BaseRewriteFiles(name, ops).reportWith(reporter);
  }

  /** 创建 manifest 重写操作（用于 manifest compaction）。 */
  @Override
  public RewriteManifests rewriteManifests() {
    return new BaseRewriteManifests(ops).reportWith(reporter);
  }

  /** 创建覆写文件操作（原子性地添加新文件、删除旧文件）。 */
  @Override
  public OverwriteFiles newOverwrite() {
    return new BaseOverwriteFiles(name, ops).reportWith(reporter);
  }

  /** 创建 RowDelta 操作（行级变更：追加数据 + 删除文件）。 */
  @Override
  public RowDelta newRowDelta() {
    return new BaseRowDelta(name, ops).reportWith(reporter);
  }

  /** 创建全量替换分区操作（覆盖指定分区的所有数据）。 */
  @Override
  public ReplacePartitions newReplacePartitions() {
    return new BaseReplacePartitions(name, ops).reportWith(reporter);
  }

  /** 创建删除文件操作。 */
  @Override
  public DeleteFiles newDelete() {
    return new StreamingDelete(name, ops).reportWith(reporter);
  }

  /** 创建统计信息更新操作。 */
  @Override
  public UpdateStatistics updateStatistics() {
    return new SetStatistics(ops);
  }

  /** 创建过期快照操作，用于清理历史快照及其引用的文件。 */
  @Override
  public ExpireSnapshots expireSnapshots() {
    return new RemoveSnapshots(ops);
  }

  /** 创建快照管理操作（回滚、cherrypick 等）。 */
  @Override
  public ManageSnapshots manageSnapshots() {
    return new SnapshotManager(name, ops);
  }

  /** 创建一个新事务，支持在事务内组合多个更新操作。 */
  @Override
  public Transaction newTransaction() {
    return Transactions.newTransaction(name, ops, reporter);
  }

  @Override
  public FileIO io() {
    return ops.io();
  }

  @Override
  public EncryptionManager encryption() {
    return ops.encryption();
  }

  @Override
  public LocationProvider locationProvider() {
    return ops.locationProvider();
  }

  @Override
  public List<StatisticsFile> statisticsFiles() {
    return ops.current().statisticsFiles();
  }

  @Override
  public Map<String, SnapshotRef> refs() {
    return ops.current().refs();
  }

  @Override
  public String toString() {
    return name();
  }

  /**
   * 序列化替换钩子：把 BaseTable 替换为 {@link SerializableTable} 的只读副本。
   *
   * <p>设计意图：序列化后的对象使用 {@link StaticTableOperations}，不再需要访问原 Catalog， 适合在分布式作业中传递只读表视图。
   *
   * @return 可序列化的只读表对象
   */
  Object writeReplace() {
    return SerializableTable.copyOf(this);
  }
}
