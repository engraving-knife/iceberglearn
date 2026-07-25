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
import java.util.Map;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;

/**
 * 表接口：Iceberg 中对一张表的核心抽象，提供元数据访问与各类更新/扫描入口。
 *
 * <p>所属模块：iceberg-api（最顶层公共 API 模块，被 core 及所有引擎模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>暴露表的元数据：schema、分区规范、排序顺序、快照、属性、位置等。
 *   <li>提供扫描创建入口：{@link #newScan()}、{@link #newBatchScan()}、增量扫描等。
 *   <li>提供更新/操作入口：追加、覆写、删除、重写、schema 演进、快照管理等。
 *   <li>提供基础设施访问：{@link FileIO}、{@link EncryptionManager}、{@link LocationProvider}。
 * </ul>
 *
 * <p>设计意图：作为纯接口，把表的"契约"与"实现"解耦，使 Catalog 可以返回不同实现 （如内存表、REST 表、SQL 表等）。默认方法为可选能力提供兜底实现（如 {@link
 * #newBatchScan()} 默认通过 {@link BatchScanAdapter} 适配）。
 *
 * <p>上下游关系：由 {@code Catalog} 加载并返回；被引擎层、core 模块的更新实现类广泛使用。
 */
public interface Table {

  /**
   * 返回本表的全名。
   *
   * @return 表名
   */
  default String name() {
    return toString();
  }

  /** 刷新并加载最新的表元数据。 */
  void refresh();

  /**
   * 创建本表的 {@link TableScan} 扫描。
   *
   * <p>扫描创建后可进一步配置投影列与过滤条件。
   *
   * @return 表扫描
   */
  TableScan newScan();

  /**
   * 创建本表的 {@link BatchScan} 批量扫描。
   *
   * <p>默认实现通过 {@link BatchScanAdapter} 把 {@link TableScan} 适配为 BatchScan。
   *
   * @return 批量扫描
   */
  default BatchScan newBatchScan() {
    return new BatchScanAdapter(newScan());
  }

  /**
   * 创建本表的 {@link IncrementalAppendScan} 增量追加扫描。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，由具体实现类提供支持。
   *
   * @return 增量追加扫描
   */
  default IncrementalAppendScan newIncrementalAppendScan() {
    throw new UnsupportedOperationException("Incremental append scan is not supported");
  }

  /**
   * 创建本表的 {@link IncrementalChangelogScan} 增量变更日志扫描。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，由具体实现类提供支持。
   *
   * @return 增量变更日志扫描
   */
  default IncrementalChangelogScan newIncrementalChangelogScan() {
    throw new UnsupportedOperationException("Incremental changelog scan is not supported");
  }

  /**
   * 返回本表当前的 {@link Schema}。
   *
   * @return 表 schema
   */
  Schema schema();

  /**
   * 返回本表所有 schema 的映射（schema ID → Schema）。
   *
   * @return schema 映射
   */
  Map<Integer, Schema> schemas();

  /**
   * 返回本表当前的 {@link PartitionSpec} 分区规范。
   *
   * @return 分区规范
   */
  PartitionSpec spec();

  /**
   * 返回本表所有分区规范的映射（specID → PartitionSpec）。
   *
   * @return 分区规范映射
   */
  Map<Integer, PartitionSpec> specs();

  /**
   * 返回本表当前的 {@link SortOrder} 排序顺序。
   *
   * @return 排序顺序
   */
  SortOrder sortOrder();

  /**
   * 返回本表所有排序顺序的映射（sortOrderID → SortOrder）。
   *
   * @return 排序顺序映射
   */
  Map<Integer, SortOrder> sortOrders();

  /**
   * 返回本表的字符串属性映射。
   *
   * @return 表属性映射
   */
  Map<String, String> properties();

  /**
   * 返回本表的存储根路径。
   *
   * @return 表存储路径
   */
  String location();

  /**
   * 返回本表当前的 {@link Snapshot}，若无快照返回 null。
   *
   * @return 当前快照
   */
  Snapshot currentSnapshot();

  /**
   * 按快照 ID 查找 {@link Snapshot}，无匹配返回 null。
   *
   * @param snapshotId 快照 ID
   * @return 对应快照
   */
  Snapshot snapshot(long snapshotId);

  /**
   * 返回本表所有 {@link Snapshot} 的迭代器。
   *
   * @return 快照迭代器
   */
  Iterable<Snapshot> snapshots();

  /**
   * 返回本表的快照历史。
   *
   * @return 历史条目列表
   */
  List<HistoryEntry> history();

  /**
   * 创建 {@link UpdateSchema} 以修改本表列并提交。
   *
   * @return 新的 schema 更新器
   */
  UpdateSchema updateSchema();

  /**
   * 创建 {@link UpdatePartitionSpec} 以修改本表分区规范并提交。
   *
   * @return 新的分区规范更新器
   */
  UpdatePartitionSpec updateSpec();

  /**
   * 创建 {@link UpdateProperties} 以更新表属性并提交。
   *
   * @return 新的属性更新器
   */
  UpdateProperties updateProperties();

  /**
   * 创建 {@link ReplaceSortOrder} 以设置表排序顺序并提交。
   *
   * @return 新的排序顺序替换器
   */
  ReplaceSortOrder replaceSortOrder();

  /**
   * 创建 {@link UpdateLocation} 以更新表存储位置并提交。
   *
   * @return 新的位置更新器
   */
  UpdateLocation updateLocation();

  /**
   * 创建 {@link AppendFiles} 追加 API 以向本表新增文件并提交。
   *
   * @return 新的追加 API
   */
  AppendFiles newAppend();

  /**
   * 创建快速追加 {@link AppendFiles} API。
   *
   * <p>逻辑：通知底层实现跳过额外工作以尽快提交。不推荐用于常规写入，因为快速提交可能 导致后续分片规划变慢。若实现不支持快速追加，则退化为 {@link #newAppend()}。
   *
   * @return 新的追加 API
   */
  default AppendFiles newFastAppend() {
    return newAppend();
  }

  /**
   * 创建 {@link RewriteFiles} 重写 API 以替换本表文件并提交。
   *
   * @return 新的文件重写 API
   */
  RewriteFiles newRewrite();

  /**
   * 创建 {@link RewriteManifests} 清单重写 API 以替换本表清单并提交。
   *
   * @return 新的清单重写 API
   */
  RewriteManifests rewriteManifests();

  /**
   * 创建 {@link OverwriteFiles} 覆写 API 以按过滤表达式覆写文件并提交。
   *
   * @return 新的覆写 API
   */
  OverwriteFiles newOverwrite();

  /**
   * 创建 {@link RowDelta} 行级增量 API 以删除或替换已有数据文件中的行并提交。
   *
   * @return 新的行级增量 API
   */
  RowDelta newRowDelta();

  /**
   * 不推荐：创建 {@link ReplacePartitions} 分区替换 API 以动态覆写表分区。
   *
   * <p>主要为兼容 Hive 风格 SQL 提供，推荐优先使用 {@link OverwriteFiles} 做显式覆写。
   *
   * @return 新的分区替换 API
   */
  ReplacePartitions newReplacePartitions();

  /**
   * 创建 {@link DeleteFiles} 删除 API 以删除本表文件并提交。
   *
   * @return 新的删除 API
   */
  DeleteFiles newDelete();

  /**
   * 创建 {@link UpdateStatistics} 统计文件更新 API 以新增/删除本表统计文件。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，由具体实现类提供支持。
   *
   * @return 新的统计更新 API
   */
  default UpdateStatistics updateStatistics() {
    throw new UnsupportedOperationException(
        "Updating statistics is not supported by " + getClass().getName());
  }

  /**
   * 创建 {@link ExpireSnapshots} 过期 API 以管理本表快照过期并提交。
   *
   * @return 新的快照过期 API
   */
  ExpireSnapshots expireSnapshots();

  /**
   * 创建 {@link ManageSnapshots} 快照管理 API 以管理本表快照并提交。
   *
   * @return 新的快照管理 API
   */
  ManageSnapshots manageSnapshots();

  /**
   * 创建 {@link Transaction} 事务 API 以一次性提交多个表操作。
   *
   * @return 新的事务 API
   */
  Transaction newTransaction();

  /** 返回用于读写表数据与元数据文件的 {@link FileIO}。 */
  FileIO io();

  /**
   * 返回用于加解密数据文件的 {@link EncryptionManager}。
   *
   * @return 加密管理器
   */
  EncryptionManager encryption();

  /** 返回用于为新数据文件生成存储路径的 {@link LocationProvider}。 */
  LocationProvider locationProvider();

  /**
   * 返回本表当前的统计文件列表。
   *
   * @return 统计文件列表
   */
  List<StatisticsFile> statisticsFiles();

  /**
   * 返回本表当前的快照引用（refs）映射。
   *
   * @return 引用名 → {@link SnapshotRef}
   */
  Map<String, SnapshotRef> refs();

  /**
   * 按引用名查找 {@link Snapshot}，无匹配引用返回 null。
   *
   * @param name 引用名（分支或标签）
   * @return 引用指向的快照
   */
  default Snapshot snapshot(String name) {
    SnapshotRef ref = refs().get(name);
    if (ref != null) {
      return snapshot(ref.snapshotId());
    }

    return null;
  }
}
