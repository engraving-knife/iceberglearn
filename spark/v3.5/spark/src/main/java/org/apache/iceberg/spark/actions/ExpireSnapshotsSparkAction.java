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
package org.apache.iceberg.spark.actions;

import static org.apache.iceberg.TableProperties.GC_ENABLED;
import static org.apache.iceberg.TableProperties.GC_ENABLED_DEFAULT;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.actions.ExpireSnapshots;
import org.apache.iceberg.actions.ImmutableExpireSnapshots;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.spark.JobGroupInfo;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Spark 的过期快照（ExpireSnapshots）action 实现。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），actions 子包。功能等价于 {@link
 * org.apache.iceberg.ExpireSnapshots}，但借助 Spark 计算过期前后文件差集。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>先调用 Iceberg 原生 {@code expireSnapshots()} 提交快照过期（不清理文件），更新表元数据。
 *   <li>再用元数据表反连接（anti-join）计算过期前引用、过期后不再引用的文件集合。
 *   <li>支持流式（toLocalIterator）或批量（collectAsList）两种方式拉取待删文件到 driver 删除。
 *   <li>支持自定义删除函数、自定义删除线程池，以及批量删除优化。
 * </ul>
 *
 * <p>设计意图：把耗时的"找可删文件"工作下推到 Spark 分布式执行，避免单机遍历大量 manifest； 过期提交与文件删除解耦，保证元数据先稳定提交再清理物理文件。本操作涉及
 * shuffle， 并行度由 {@code spark.sql.shuffle.partitions} 控制。
 *
 * <p>上下游关系：继承 {@link BaseSparkAction}，实现 {@link ExpireSnapshots} 接口； 被 Spark 过程 {@code
 * expire_snapshots} 调用；依赖 Iceberg core 的 ExpireSnapshots 与 TableOperations。
 */
@SuppressWarnings("UnnecessaryAnonymousClass")
public class ExpireSnapshotsSparkAction extends BaseSparkAction<ExpireSnapshotsSparkAction>
    implements ExpireSnapshots {

  public static final String STREAM_RESULTS = "stream-results";
  public static final boolean STREAM_RESULTS_DEFAULT = false;

  private static final Logger LOG = LoggerFactory.getLogger(ExpireSnapshotsSparkAction.class);

  private final Table table;
  private final TableOperations ops;

  private final Set<Long> expiredSnapshotIds = Sets.newHashSet();
  private Long expireOlderThanValue = null;
  private Integer retainLastValue = null;
  private Consumer<String> deleteFunc = null;
  private ExecutorService deleteExecutorService = null;
  private Dataset<FileInfo> expiredFileDS = null;

  /**
   * 构造过期快照 action。
   *
   * <p>逻辑：保存表与 TableOperations，并校验表属性 {@code gc.enabled} 为 true， 否则抛出 {@link
   * ValidationException}（防止删除文件破坏其他共享文件的表）。
   *
   * @param spark SparkSession
   * @param table 目标表
   */
  ExpireSnapshotsSparkAction(SparkSession spark, Table table) {
    super(spark);
    this.table = table;
    this.ops = ((HasTableOperations) table).operations();

    ValidationException.check(
        PropertyUtil.propertyAsBoolean(table.properties(), GC_ENABLED, GC_ENABLED_DEFAULT),
        "Cannot expire snapshots: GC is disabled (deleting files may corrupt other tables)");
  }
  /** 执行 self 相关操作。 */
  @Override
  protected ExpireSnapshotsSparkAction self() {
    return this;
  }

  /** 指定删除文件所用的线程池，返回当前 action 以支持链式调用。 */
  @Override
  public ExpireSnapshotsSparkAction executeDeleteWith(ExecutorService executorService) {
    this.deleteExecutorService = executorService;
    return this;
  }

  /** 指定要过期的单个快照 ID。 */
  @Override
  public ExpireSnapshotsSparkAction expireSnapshotId(long snapshotId) {
    expiredSnapshotIds.add(snapshotId);
    return this;
  }

  /** 过期所有早于给定时间戳的快照。 */
  @Override
  public ExpireSnapshotsSparkAction expireOlderThan(long timestampMillis) {
    this.expireOlderThanValue = timestampMillis;
    return this;
  }

  /** 保留最近 N 个快照，其余过期；N 必须 >= 1。 */
  @Override
  public ExpireSnapshotsSparkAction retainLast(int numSnapshots) {
    Preconditions.checkArgument(
        1 <= numSnapshots,
        "Number of snapshots to retain must be at least 1, cannot be: %s",
        numSnapshots);
    this.retainLastValue = numSnapshots;
    return this;
  }

  /** 指定自定义删除函数，覆盖默认 IO 删除逻辑。 */
  @Override
  public ExpireSnapshotsSparkAction deleteWith(Consumer<String> newDeleteFunc) {
    this.deleteFunc = newDeleteFunc;
    return this;
  }

  /**
   * 执行快照过期并提交表元数据变更，返回待删除文件的 Dataset。
   *
   * <p>逻辑：先记录原始 metadata；调用 Iceberg 原生 {@code expireSnapshots()} 配置过期条件并 {@code
   * cleanExpiredFiles(false)} 后 commit；再 refresh 得到新 metadata，分别计算过期前后的文件集， 用 {@code except}
   * 求差集得到可安全删除的文件 Dataset。结果会缓存到 expiredFileDS 供多次使用。
   *
   * <p>注意：本方法只提交过期并返回文件清单，不实际删除数据文件，需调用 {@link #execute()} 删除。
   *
   * @return 不再被表引用的文件 Dataset
   */
  public Dataset<FileInfo> expireFiles() {
    if (expiredFileDS == null) {
      // fetch metadata before expiration
      TableMetadata originalMetadata = ops.current();

      // perform expiration
      org.apache.iceberg.ExpireSnapshots expireSnapshots = table.expireSnapshots();

      for (long id : expiredSnapshotIds) {
        expireSnapshots = expireSnapshots.expireSnapshotId(id);
      }

      if (expireOlderThanValue != null) {
        expireSnapshots = expireSnapshots.expireOlderThan(expireOlderThanValue);
      }

      if (retainLastValue != null) {
        expireSnapshots = expireSnapshots.retainLast(retainLastValue);
      }

      expireSnapshots.cleanExpiredFiles(false).commit();

      // fetch valid files after expiration
      TableMetadata updatedMetadata = ops.refresh();
      Dataset<FileInfo> validFileDS = fileDS(updatedMetadata);

      // fetch files referenced by expired snapshots
      Set<Long> deletedSnapshotIds = findExpiredSnapshotIds(originalMetadata, updatedMetadata);
      Dataset<FileInfo> deleteCandidateFileDS = fileDS(originalMetadata, deletedSnapshotIds);

      // determine expired files
      this.expiredFileDS = deleteCandidateFileDS.except(validFileDS);
    }

    return expiredFileDS;
  }

  /** 在 EXPIRE-SNAPSHOTS 作业组下执行过期与文件删除，返回结果统计。 */
  @Override
  public ExpireSnapshots.Result execute() {
    JobGroupInfo info = newJobGroupInfo("EXPIRE-SNAPSHOTS", jobDesc());
    return withJobGroupInfo(info, this::doExecute);
  }

  /** 构造用于 Spark UI 显示的作业描述，包含过期条件与表名。 */
  private String jobDesc() {
    List<String> options = Lists.newArrayList();

    if (expireOlderThanValue != null) {
      options.add("older_than=" + expireOlderThanValue);
    }

    if (retainLastValue != null) {
      options.add("retain_last=" + retainLastValue);
    }

    if (!expiredSnapshotIds.isEmpty()) {
      Long first = expiredSnapshotIds.stream().findFirst().get();
      if (expiredSnapshotIds.size() > 1) {
        options.add(
            String.format("snapshot_ids: %s (%s more...)", first, expiredSnapshotIds.size() - 1));
      } else {
        options.add(String.format("snapshot_id: %s", first));
      }
    }

    return String.format("Expiring snapshots (%s) in %s", COMMA_JOINER.join(options), table.name());
  }

  /**
   * 实际执行删除：根据 streamResults 选择流式或批量拉取待删文件并删除。
   *
   * <p>逻辑：流式则用 {@code toLocalIterator} 逐批拉取避免 driver OOM；否则 collectAsList 一次性拉取。
   */
  private ExpireSnapshots.Result doExecute() {
    if (streamResults()) {
      return deleteFiles(expireFiles().toLocalIterator());
    } else {
      return deleteFiles(expireFiles().collectAsList().iterator());
    }
  }

  /** 读取 stream-results 选项，决定是否流式拉取待删文件。 */
  private boolean streamResults() {
    return PropertyUtil.propertyAsBoolean(options(), STREAM_RESULTS, STREAM_RESULTS_DEFAULT);
  }

  /** 返回给定 metadata 下所有 manifest/内容/统计文件的并集 Dataset，不过滤快照。 */
  private Dataset<FileInfo> fileDS(TableMetadata metadata) {
    return fileDS(metadata, null);
  }

  /**
   * 返回给定 metadata 下内容文件、manifest、manifest list、统计文件的并集 Dataset。
   *
   * <p>逻辑：用 {@link #newStaticTable} 构造静态表，分别调用基类四个 DS 方法后 union。
   *
   * @param metadata 表元数据
   * @param snapshotIds 可选快照 ID 集合
   * @return 文件 FileInfo Dataset
   */
  private Dataset<FileInfo> fileDS(TableMetadata metadata, Set<Long> snapshotIds) {
    Table staticTable = newStaticTable(metadata, table.io());
    return contentFileDS(staticTable, snapshotIds)
        .union(manifestDS(staticTable, snapshotIds))
        .union(manifestListDS(staticTable, snapshotIds))
        .union(statisticsFileDS(staticTable, snapshotIds));
  }

  /**
   * 计算被过期掉的快照 ID 集合。
   *
   * <p>逻辑：用更新后 metadata 中保留的快照 ID 集合，从原始 metadata 的快照中过滤掉保留的， 剩下的即为被过期掉的快照。
   *
   * @param originalMetadata 过期前元数据
   * @param updatedMetadata 过期后元数据
   * @return 被过期的快照 ID 集合
   */
  private Set<Long> findExpiredSnapshotIds(
      TableMetadata originalMetadata, TableMetadata updatedMetadata) {
    Set<Long> retainedSnapshots =
        updatedMetadata.snapshots().stream().map(Snapshot::snapshotId).collect(Collectors.toSet());
    return originalMetadata.snapshots().stream()
        .map(Snapshot::snapshotId)
        .filter(id -> !retainedSnapshots.contains(id))
        .collect(Collectors.toSet());
  }

  /**
   * 删除待删文件并构造结果统计。
   *
   * <p>逻辑：若未提供自定义 deleteFunc 且 IO 支持 {@link SupportsBulkOperations}，则走批量删除； 否则用线程池并发逐个删除（默认
   * IO::deleteFile 或自定义 deleteFunc）。最后汇总到结果对象。
   *
   * @param files 待删文件迭代器
   * @return 过期结果统计
   */
  private ExpireSnapshots.Result deleteFiles(Iterator<FileInfo> files) {
    DeleteSummary summary;
    if (deleteFunc == null && table.io() instanceof SupportsBulkOperations) {
      summary = deleteFiles((SupportsBulkOperations) table.io(), files);
    } else {

      if (deleteFunc == null) {
        LOG.info(
            "Table IO {} does not support bulk operations. Using non-bulk deletes.",
            table.io().getClass().getName());
        summary = deleteFiles(deleteExecutorService, table.io()::deleteFile, files);
      } else {
        LOG.info("Custom delete function provided. Using non-bulk deletes");
        summary = deleteFiles(deleteExecutorService, deleteFunc, files);
      }
    }

    LOG.info("Deleted {} total files", summary.totalFilesCount());

    return ImmutableExpireSnapshots.Result.builder()
        .deletedDataFilesCount(summary.dataFilesCount())
        .deletedPositionDeleteFilesCount(summary.positionDeleteFilesCount())
        .deletedEqualityDeleteFilesCount(summary.equalityDeleteFilesCount())
        .deletedManifestsCount(summary.manifestsCount())
        .deletedManifestListsCount(summary.manifestListsCount())
        .deletedStatisticsFilesCount(summary.statisticsFilesCount())
        .build();
  }
}
