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
 * 基于 Spark 执行的 Iceberg 表维护动作，执行快照过期、文件清理、数据压缩等表维护操作。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 ExpireSnapshotsSparkAction。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
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

  ExpireSnapshotsSparkAction(SparkSession spark, Table table) {
    super(spark);
    this.table = table;
    this.ops = ((HasTableOperations) table).operations();

    ValidationException.check(
        PropertyUtil.propertyAsBoolean(table.properties(), GC_ENABLED, GC_ENABLED_DEFAULT),
        "Cannot expire snapshots: GC is disabled (deleting files may corrupt other tables)");
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected ExpireSnapshotsSparkAction self() {
    return this;
  }

  /**
   * 执行具体逻辑。
   *
   * @param executorService 参数
   * @return 结果对象
   */
  @Override
  public ExpireSnapshotsSparkAction executeDeleteWith(ExecutorService executorService) {
    this.deleteExecutorService = executorService;
    return this;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param snapshotId 参数
   * @return 结果对象
   */
  @Override
  public ExpireSnapshotsSparkAction expireSnapshotId(long snapshotId) {
    expiredSnapshotIds.add(snapshotId);
    return this;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param timestampMillis 参数
   * @return 结果对象
   */
  @Override
  public ExpireSnapshotsSparkAction expireOlderThan(long timestampMillis) {
    this.expireOlderThanValue = timestampMillis;
    return this;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param numSnapshots 参数
   * @return 结果对象
   */
  @Override
  public ExpireSnapshotsSparkAction retainLast(int numSnapshots) {
    Preconditions.checkArgument(
        1 <= numSnapshots,
        "Number of snapshots to retain must be at least 1, cannot be: %s",
        numSnapshots);
    this.retainLastValue = numSnapshots;
    return this;
  }

  /**
   * 删除数据或文件。
   *
   * @param newDeleteFunc 参数
   * @return 结果对象
   */
  @Override
  public ExpireSnapshotsSparkAction deleteWith(Consumer<String> newDeleteFunc) {
    this.deleteFunc = newDeleteFunc;
    return this;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
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

  /**
   * 执行具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public ExpireSnapshots.Result execute() {
    JobGroupInfo info = newJobGroupInfo("EXPIRE-SNAPSHOTS", jobDesc());
    return withJobGroupInfo(info, this::doExecute);
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  private ExpireSnapshots.Result doExecute() {
    if (streamResults()) {
      return deleteFiles(expireFiles().toLocalIterator());
    } else {
      return deleteFiles(expireFiles().collectAsList().iterator());
    }
  }

  /** 执行该方法的具体逻辑。 */
  private boolean streamResults() {
    return PropertyUtil.propertyAsBoolean(options(), STREAM_RESULTS, STREAM_RESULTS_DEFAULT);
  }

  /** 执行该方法的具体逻辑。 */
  private Dataset<FileInfo> fileDS(TableMetadata metadata) {
    return fileDS(metadata, null);
  }

  /** 执行该方法的具体逻辑。 */
  private Dataset<FileInfo> fileDS(TableMetadata metadata, Set<Long> snapshotIds) {
    Table staticTable = newStaticTable(metadata, table.io());
    return contentFileDS(staticTable, snapshotIds)
        .union(manifestDS(staticTable, snapshotIds))
        .union(manifestListDS(staticTable, snapshotIds))
        .union(statisticsFileDS(staticTable, snapshotIds));
  }

  /** 查找并返回结果。 */
  private Set<Long> findExpiredSnapshotIds(
      TableMetadata originalMetadata, TableMetadata updatedMetadata) {
    Set<Long> retainedSnapshots =
        updatedMetadata.snapshots().stream().map(Snapshot::snapshotId).collect(Collectors.toSet());
    return originalMetadata.snapshots().stream()
        .map(Snapshot::snapshotId)
        .filter(id -> !retainedSnapshots.contains(id))
        .collect(Collectors.toSet());
  }

  /** 删除数据或文件。 */
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
