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

import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.exceptions.CleanableFailure;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.metrics.LoggingMetricsReporter;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 表事务的 core 实现：把多个表更新操作聚合为一次原子提交。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link Transaction}，在一个事务内串行执行多个 {@link PendingUpdate}（如 append、overwrite、
 *       delete、rewrite 等），并最终一次性提交。
 *   <li>通过 {@link TransactionTable}/{@link TransactionTableOperations} 提供事务内的临时表视图， 各操作在该视图上
 *       commit，仅更新内存 current 元数据，不真正落盘。
 *   <li>支持四种事务类型：CREATE_TABLE、REPLACE_TABLE、CREATE_OR_REPLACE_TABLE、SIMPLE。
 *   <li>提交失败时清理未提交文件，提交成功后清理被删除文件（保留已提交 manifest）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>事务内每次操作 commit 只更新 current，不触碰底层 ops，保证事务内多操作可见性一致。
 *   <li>仅最后一次 {@link #commitTransaction} 才真正向底层 ops 提交，并通过 CAS 重试处理并发冲突。
 *   <li>重试时需重新应用全部 PendingUpdate（applyUpdates），因底层元数据可能已被其他提交改变。
 *   <li>checkLastOperationCommitted 保证同一时刻只有一个未提交操作，避免操作交错。
 * </ul>
 *
 * <p>上下游关系：由 {@link Transactions} 工厂创建；内部委托各 {@link PendingUpdate} 实现完成具体操作； 提交时调用底层 {@link
 * TableOperations#commit}。
 */
public class BaseTransaction implements Transaction {
  private static final Logger LOG = LoggerFactory.getLogger(BaseTransaction.class);

  /** 事务类型枚举：建表、替换表、建或替换表、普通多操作事务。 */
  enum TransactionType {
    CREATE_TABLE,
    REPLACE_TABLE,
    CREATE_OR_REPLACE_TABLE,
    SIMPLE
  }

  private final String tableName;
  private final TableOperations ops;
  private final TransactionTable transactionTable;
  private final TableOperations transactionOps;
  private final List<PendingUpdate> updates;
  private final Set<String> deletedFiles =
      Sets.newHashSet(); // keep track of files deleted in the most recent commit
  private final Consumer<String> enqueueDelete = deletedFiles::add;
  private TransactionType type;
  private TableMetadata base;
  private TableMetadata current;
  private boolean hasLastOpCommitted;
  private final MetricsReporter reporter;

  /**
   * 构造事务，使用默认日志指标上报器。
   *
   * @param tableName 表名
   * @param ops 底层表操作句柄
   * @param type 事务类型
   * @param start 事务起始元数据
   */
  BaseTransaction(
      String tableName, TableOperations ops, TransactionType type, TableMetadata start) {
    this(tableName, ops, type, start, LoggingMetricsReporter.instance());
  }

  /**
   * 构造事务，指定指标上报器。
   *
   * @param tableName 表名
   * @param ops 底层表操作句柄
   * @param type 事务类型
   * @param start 事务起始元数据
   * @param reporter 指标上报器
   */
  BaseTransaction(
      String tableName,
      TableOperations ops,
      TransactionType type,
      TableMetadata start,
      MetricsReporter reporter) {
    this.tableName = tableName;
    this.ops = ops;
    this.transactionTable = new TransactionTable();
    this.current = start;
    this.transactionOps = new TransactionTableOperations();
    this.updates = Lists.newArrayList();
    this.base = ops.current();
    this.type = type;
    this.hasLastOpCommitted = true;
    this.reporter = reporter;
  }

  /** 返回事务内的临时表视图。 */
  @Override
  public Table table() {
    return transactionTable;
  }

  /** 返回表名。 */
  public String tableName() {
    return tableName;
  }

  /** 返回事务起始时的表元数据（base）。 */
  public TableMetadata startMetadata() {
    return base;
  }

  /** 返回事务当前（含未提交变更）的表元数据。 */
  public TableMetadata currentMetadata() {
    return current;
  }

  /** 返回底层（非事务）表操作句柄。 */
  public TableOperations underlyingOps() {
    return ops;
  }

  /**
   * 校验上一个操作已提交，并标记当前操作未提交。
   *
   * <p>逻辑：若 hasLastOpCommitted 为 false 抛异常；否则置为 false。保证事务内操作串行提交。
   *
   * @param operation 操作名（用于异常信息）
   * @throws IllegalStateException 若上一操作未提交
   */
  private void checkLastOperationCommitted(String operation) {
    Preconditions.checkState(
        hasLastOpCommitted, "Cannot create new %s: last operation has not committed", operation);
    this.hasLastOpCommitted = false;
  }

  /** 创建 schema 更新操作并加入事务。 */
  @Override
  public UpdateSchema updateSchema() {
    checkLastOperationCommitted("UpdateSchema");
    UpdateSchema schemaChange = new SchemaUpdate(transactionOps);
    updates.add(schemaChange);
    return schemaChange;
  }

  /** 创建分区 spec 更新操作并加入事务。 */
  @Override
  public UpdatePartitionSpec updateSpec() {
    checkLastOperationCommitted("UpdateSpec");
    UpdatePartitionSpec partitionSpecChange = new BaseUpdatePartitionSpec(transactionOps);
    updates.add(partitionSpecChange);
    return partitionSpecChange;
  }

  /** 创建表属性更新操作并加入事务。 */
  @Override
  public UpdateProperties updateProperties() {
    checkLastOperationCommitted("UpdateProperties");
    UpdateProperties props = new PropertiesUpdate(transactionOps);
    updates.add(props);
    return props;
  }

  /** 创建排序顺序替换操作并加入事务。 */
  @Override
  public ReplaceSortOrder replaceSortOrder() {
    checkLastOperationCommitted("ReplaceSortOrder");
    ReplaceSortOrder replaceSortOrder = new BaseReplaceSortOrder(transactionOps);
    updates.add(replaceSortOrder);
    return replaceSortOrder;
  }

  /** 创建表位置更新操作并加入事务。 */
  @Override
  public UpdateLocation updateLocation() {
    checkLastOperationCommitted("UpdateLocation");
    UpdateLocation setLocation = new SetLocation(transactionOps);
    updates.add(setLocation);
    return setLocation;
  }

  /** 创建合并追加操作并加入事务，删除文件回调登记到事务级 deletedFiles。 */
  @Override
  public AppendFiles newAppend() {
    checkLastOperationCommitted("AppendFiles");
    AppendFiles append = new MergeAppend(tableName, transactionOps).reportWith(reporter);
    append.deleteWith(enqueueDelete);
    updates.add(append);
    return append;
  }

  /** 创建快速追加操作并加入事务（不合并 manifest）。 */
  @Override
  public AppendFiles newFastAppend() {
    checkLastOperationCommitted("AppendFiles");
    AppendFiles append = new FastAppend(tableName, transactionOps).reportWith(reporter);
    updates.add(append);
    return append;
  }

  /** 创建文件重写操作并加入事务。 */
  @Override
  public RewriteFiles newRewrite() {
    checkLastOperationCommitted("RewriteFiles");
    RewriteFiles rewrite = new BaseRewriteFiles(tableName, transactionOps).reportWith(reporter);
    rewrite.deleteWith(enqueueDelete);
    updates.add(rewrite);
    return rewrite;
  }

  /** 创建 manifest 重写操作并加入事务。 */
  @Override
  public RewriteManifests rewriteManifests() {
    checkLastOperationCommitted("RewriteManifests");
    RewriteManifests rewrite = new BaseRewriteManifests(transactionOps).reportWith(reporter);
    rewrite.deleteWith(enqueueDelete);
    updates.add(rewrite);
    return rewrite;
  }

  /** 创建覆写操作并加入事务。 */
  @Override
  public OverwriteFiles newOverwrite() {
    checkLastOperationCommitted("OverwriteFiles");
    OverwriteFiles overwrite =
        new BaseOverwriteFiles(tableName, transactionOps).reportWith(reporter);
    overwrite.deleteWith(enqueueDelete);
    updates.add(overwrite);
    return overwrite;
  }

  /** 创建行增量操作（含删除文件）并加入事务。 */
  @Override
  public RowDelta newRowDelta() {
    checkLastOperationCommitted("RowDelta");
    RowDelta delta = new BaseRowDelta(tableName, transactionOps).reportWith(reporter);
    delta.deleteWith(enqueueDelete);
    updates.add(delta);
    return delta;
  }

  /** 创建分区替换操作并加入事务。 */
  @Override
  public ReplacePartitions newReplacePartitions() {
    checkLastOperationCommitted("ReplacePartitions");
    ReplacePartitions replacePartitions =
        new BaseReplacePartitions(tableName, transactionOps).reportWith(reporter);
    replacePartitions.deleteWith(enqueueDelete);
    updates.add(replacePartitions);
    return replacePartitions;
  }

  /** 创建删除文件操作并加入事务。 */
  @Override
  public DeleteFiles newDelete() {
    checkLastOperationCommitted("DeleteFiles");
    DeleteFiles delete = new StreamingDelete(tableName, transactionOps).reportWith(reporter);
    delete.deleteWith(enqueueDelete);
    updates.add(delete);
    return delete;
  }

  /** 创建统计信息更新操作并加入事务。 */
  @Override
  public UpdateStatistics updateStatistics() {
    checkLastOperationCommitted("UpdateStatistics");
    UpdateStatistics updateStatistics = new SetStatistics(transactionOps);
    updates.add(updateStatistics);
    return updateStatistics;
  }

  /** 创建快照过期操作并加入事务。 */
  @Override
  public ExpireSnapshots expireSnapshots() {
    checkLastOperationCommitted("ExpireSnapshots");
    ExpireSnapshots expire = new RemoveSnapshots(transactionOps);
    expire.deleteWith(enqueueDelete);
    updates.add(expire);
    return expire;
  }

  /**
   * 创建快照管理操作并加入事务。
   *
   * <p>注意：manageSnapshots 不调用 checkLastOperationCommitted，因其不直接产生文件变更。
   */
  @Override
  public ManageSnapshots manageSnapshots() {
    SnapshotManager snapshotManager = new SnapshotManager(this);
    updates.add(snapshotManager);
    return snapshotManager;
  }

  /** 创建 cherry-pick 操作并加入事务。 */
  CherryPickOperation cherryPick() {
    checkLastOperationCommitted("CherryPick");
    CherryPickOperation cherrypick =
        new CherryPickOperation(tableName, transactionOps).reportWith(reporter);
    updates.add(cherrypick);
    return cherrypick;
  }

  /** 创建设置分支快照操作并加入事务。 */
  SetSnapshotOperation setBranchSnapshot() {
    checkLastOperationCommitted("SetBranchSnapshot");
    SetSnapshotOperation set = new SetSnapshotOperation(transactionOps);
    updates.add(set);
    return set;
  }

  /** 创建更新快照引用操作并加入事务。 */
  UpdateSnapshotReferencesOperation updateSnapshotReferencesOperation() {
    checkLastOperationCommitted("UpdateSnapshotReferencesOperation");
    UpdateSnapshotReferencesOperation manageSnapshotRefOperation =
        new UpdateSnapshotReferencesOperation(transactionOps);
    updates.add(manageSnapshotRefOperation);
    return manageSnapshotRefOperation;
  }

  /**
   * 提交整个事务：按事务类型分发到对应的提交方法。
   *
   * <p>逻辑：先校验上一操作已提交；再按 type 分发：CREATE→commitCreateTransaction，
   * REPLACE→commitReplaceTransaction(false)，CREATE_OR_REPLACE→commitReplaceTransaction(true)，
   * SIMPLE→commitSimpleTransaction。
   *
   * @throws IllegalStateException 若上一操作未提交
   */
  @Override
  public void commitTransaction() {
    Preconditions.checkState(
        hasLastOpCommitted, "Cannot commit transaction: last operation has not committed");

    switch (type) {
      case CREATE_TABLE:
        commitCreateTransaction();
        break;

      case REPLACE_TABLE:
        commitReplaceTransaction(false);
        break;

      case CREATE_OR_REPLACE_TABLE:
        commitReplaceTransaction(true);
        break;

      case SIMPLE:
        commitSimpleTransaction();
        break;
    }
  }

  /**
   * 提交建表事务：直接 commit(null, current)。
   *
   * <p>逻辑：建表无前置状态，不重试（表已存在即失败）；失败时清理各 update 产生的文件； 无论成败都删除事务内登记的 deletedFiles（建表无重试，安全删除）。
   *
   * @throws CommitStateUnknownException 提交状态未知时抛出
   */
  private void commitCreateTransaction() {
    // this operation creates the table. if the commit fails, this cannot retry because another
    // process has created the same table.
    try {
      ops.commit(null, current);

    } catch (CommitStateUnknownException e) {
      throw e;

    } catch (RuntimeException e) {
      // the commit failed and no files were committed. clean up each update
      if (!ops.requireStrictCleanup() || e instanceof CleanableFailure) {
        cleanAllUpdates();
      }

      throw e;
    } finally {
      // create table never needs to retry because the table has no previous state. because retries
      // are not a
      // concern, it is safe to delete all of the deleted files from individual operations
      Tasks.foreach(deletedFiles)
          .suppressFailureWhenFinished()
          .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
          .run(ops.io()::deleteFile);
    }
  }

  /**
   * 提交替换表事务：用重试机制处理并发，orCreate 控制表不存在时是否转为建表。
   *
   * <p>逻辑：按表属性配置的重试次数与退避策略重试；每次重试先 refresh（表不存在时若 orCreate 则忽略，否则抛 NoSuchTableException），再
   * commit(base, current)。失败时清理 updates； 最终删除 deletedFiles（替换表整表替换无重试顾虑，安全删除）。
   *
   * @param orCreate true 表示表不存在时转为建表
   * @throws CommitStateUnknownException 提交状态未知时抛出
   */
  private void commitReplaceTransaction(boolean orCreate) {
    Map<String, String> props = base != null ? base.properties() : current.properties();

    try {
      Tasks.foreach(ops)
          .retry(PropertyUtil.propertyAsInt(props, COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
          .exponentialBackoff(
              PropertyUtil.propertyAsInt(
                  props, COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
              PropertyUtil.propertyAsInt(
                  props, COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
              PropertyUtil.propertyAsInt(
                  props, COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
              2.0 /* exponential */)
          .onlyRetryOn(CommitFailedException.class)
          .run(
              underlyingOps -> {
                try {
                  underlyingOps.refresh();
                } catch (NoSuchTableException e) {
                  if (!orCreate) {
                    throw e;
                  }
                }

                // because this is a replace table, it will always completely replace the table
                // metadata. even if it was just updated.
                if (base != underlyingOps.current()) {
                  this.base = underlyingOps.current(); // just refreshed
                }

                underlyingOps.commit(base, current);
              });

    } catch (CommitStateUnknownException e) {
      throw e;

    } catch (RuntimeException e) {
      // the commit failed and no files were committed. clean up each update.
      if (!ops.requireStrictCleanup() || e instanceof CleanableFailure) {
        cleanAllUpdates();
      }

      throw e;

    } finally {
      // replace table never needs to retry because the table state is completely replaced. because
      // retries are not
      // a concern, it is safe to delete all of the deleted files from individual operations
      Tasks.foreach(deletedFiles)
          .suppressFailureWhenFinished()
          .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
          .run(ops.io()::deleteFile);
    }
  }

  /**
   * 提交普通多操作事务：用 CAS 重试，重试时重新应用全部 PendingUpdate。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若 base == current 表示无变更，直接返回；
   *   <li>记录起始快照集合，按重试策略重试：每次先 applyUpdates（重新应用各 update），再 commit(base, current)；
   *   <li>CommitFailedException 触发重试，PendingUpdateFailedException 表示重应用失败需中断重试；
   *   <li>失败时清理 updates 与 deletedFiles；
   *   <li>成功后计算新快照集合，收集已提交文件，仅删除不在已提交集合中的 deletedFiles （避免误删被其他提交复用的 manifest）。
   * </ol>
   *
   * @throws CommitStateUnknownException 提交状态未知时抛出
   * @throws org.apache.iceberg.exceptions.CommitFailedException 重应用 update 失败时抛出
   */
  private void commitSimpleTransaction() {
    // if there were no changes, don't try to commit
    if (base == current) {
      return;
    }

    Set<Long> startingSnapshots =
        base.snapshots().stream().map(Snapshot::snapshotId).collect(Collectors.toSet());
    try {
      Tasks.foreach(ops)
          .retry(base.propertyAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
          .exponentialBackoff(
              base.propertyAsInt(COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
              base.propertyAsInt(COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
              base.propertyAsInt(COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
              2.0 /* exponential */)
          .onlyRetryOn(CommitFailedException.class)
          .run(
              underlyingOps -> {
                applyUpdates(underlyingOps);

                underlyingOps.commit(base, current);
              });

    } catch (CommitStateUnknownException e) {
      throw e;

    } catch (PendingUpdateFailedException e) {
      cleanUpOnCommitFailure();
      throw e.wrapped();
    } catch (RuntimeException e) {
      if (!ops.requireStrictCleanup() || e instanceof CleanableFailure) {
        cleanUpOnCommitFailure();
      }

      throw e;
    }

    // the commit succeeded

    try {
      // clean up the data files that were deleted by each operation. first, get the list of
      // committed manifests to ensure that no committed manifest is deleted.
      // A manifest could be deleted in one successful operation commit, but reused in another
      // successful commit of that operation if the whole transaction is retried.
      Set<Long> newSnapshots = Sets.newHashSet();
      for (Snapshot snapshot : current.snapshots()) {
        if (!startingSnapshots.contains(snapshot.snapshotId())) {
          newSnapshots.add(snapshot.snapshotId());
        }
      }

      Set<String> committedFiles = committedFiles(ops, newSnapshots);
      if (committedFiles != null) {
        // delete all of the files that were deleted in the most recent set of operation commits
        Tasks.foreach(deletedFiles)
            .suppressFailureWhenFinished()
            .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
            .run(
                path -> {
                  if (!committedFiles.contains(path)) {
                    ops.io().deleteFile(path);
                  }
                });
      } else {
        LOG.warn("Failed to load metadata for a committed snapshot, skipping clean-up");
      }

    } catch (RuntimeException e) {
      LOG.warn("Failed to load committed metadata, skipping clean-up", e);
    }
  }

  /**
   * 提交失败清理：清理各 update 产生的文件，并删除 deletedFiles。
   *
   * <p>逻辑：先 cleanAllUpdates 清理 SnapshotProducer 类 update 的中间文件，再删除 deletedFiles。
   */
  private void cleanUpOnCommitFailure() {
    // the commit failed and no files were committed. clean up each update.
    cleanAllUpdates();

    // delete all the uncommitted files
    Tasks.foreach(deletedFiles)
        .suppressFailureWhenFinished()
        .onFailure((file, exc) -> LOG.warn("Failed to delete uncommitted file: {}", file, exc))
        .run(ops.io()::deleteFile);
  }

  /**
   * 清理所有 update：对实现了 SnapshotProducer 的 update 调用 cleanAll。
   *
   * <p>逻辑：遍历 updates，对 SnapshotProducer 实例调用其 cleanAll 清理中间文件。
   */
  private void cleanAllUpdates() {
    Tasks.foreach(updates)
        .suppressFailureWhenFinished()
        .run(
            update -> {
              if (update instanceof SnapshotProducer) {
                ((SnapshotProducer) update).cleanAll();
              }
            });
  }

  /**
   * 重试时重新应用全部 PendingUpdate 到刷新后的底层元数据。
   *
   * <p>逻辑：若底层已刷新（base != refresh 结果），则更新 base/current 为刷新值，再依次重新 commit 每个 update（在
   * TransactionTableOps 上更新 current）。若某 update 抛 CommitFailedException， 包装为
   * PendingUpdateFailedException 中断重试（无法通过重试解决）。
   *
   * @param underlyingOps 底层表操作句柄
   * @throws PendingUpdateFailedException 重应用 update 失败时抛出
   */
  private void applyUpdates(TableOperations underlyingOps) {
    if (base != underlyingOps.refresh()) {
      // use refreshed the metadata
      this.base = underlyingOps.current();
      this.current = underlyingOps.current();
      for (PendingUpdate update : updates) {
        // re-commit each update in the chain to apply it and update current
        try {
          update.commit();
        } catch (CommitFailedException e) {
          // Cannot pass even with retry due to conflicting metadata changes. So, break the
          // retry-loop.
          throw new PendingUpdateFailedException(e);
        }
      }
    }
  }

  /**
   * 计算给定快照集合对应的已提交文件集合（manifest list + manifests 路径）。
   *
   * <p>逻辑：遍历每个快照 id，从当前元数据取快照，加入其 manifest list 位置与所有 manifest 路径。 若某快照 id 在当前元数据中找不到（可能被并发过期），返回
   * null 表示无法确定。
   *
   * @param ops 表操作句柄
   * @param snapshotIds 快照 id 集合
   * @return 已提交文件路径集合；无法确定时返回 null
   */
  // committedFiles returns null whenever the set of committed files
  // cannot be determined from the provided snapshots
  private static Set<String> committedFiles(TableOperations ops, Set<Long> snapshotIds) {
    if (snapshotIds.isEmpty()) {
      return ImmutableSet.of();
    }

    Set<String> committedFiles = Sets.newHashSet();

    for (long snapshotId : snapshotIds) {
      Snapshot snap = ops.current().snapshot(snapshotId);
      if (snap != null) {
        committedFiles.add(snap.manifestListLocation());
        snap.allManifests(ops.io()).forEach(manifest -> committedFiles.add(manifest.path()));
      } else {
        return null;
      }
    }

    return committedFiles;
  }

  /**
   * 事务内的表操作句柄：把 commit 转为对事务 current 的内存更新，不真正落盘。
   *
   * <p>设计意图：让各 PendingUpdate 以为自己在操作真实表，实则只更新事务内存态。 commit 时校验 underlyingBase == current（CAS
   * 语义），不一致则抛 CommitFailedException 触发事务上层重试。
   */
  public class TransactionTableOperations implements TableOperations {
    private TableOperations tempOps = ops.temp(current);

    /** 返回事务当前元数据。 */
    @Override
    public TableMetadata current() {
      return current;
    }

    /** 返回事务当前元数据（不真正刷新）。 */
    @Override
    public TableMetadata refresh() {
      return current;
    }

    /**
     * 事务内提交：校验 underlyingBase == current，更新事务 current 与 tempOps，标记上一操作已提交。
     *
     * @param underlyingBase 期望的基础元数据
     * @param metadata 新元数据
     * @throws CommitFailedException 若 underlyingBase != current，触发事务重试
     */
    @Override
    @SuppressWarnings("ConsistentOverrides")
    public void commit(TableMetadata underlyingBase, TableMetadata metadata) {
      if (underlyingBase != current) {
        // trigger a refresh and retry
        throw new CommitFailedException("Table metadata refresh is required");
      }

      BaseTransaction.this.current = metadata;

      this.tempOps = ops.temp(metadata);

      BaseTransaction.this.hasLastOpCommitted = true;
    }

    /** 返回临时 ops 的 FileIO。 */
    @Override
    public FileIO io() {
      return tempOps.io();
    }

    /** 返回临时 ops 的加密管理器。 */
    @Override
    public EncryptionManager encryption() {
      return tempOps.encryption();
    }

    /** 返回临时 ops 的元数据文件位置。 */
    @Override
    public String metadataFileLocation(String fileName) {
      return tempOps.metadataFileLocation(fileName);
    }

    /** 返回临时 ops 的位置提供者。 */
    @Override
    public LocationProvider locationProvider() {
      return tempOps.locationProvider();
    }

    /** 返回临时 ops 的新快照 id。 */
    @Override
    public long newSnapshotId() {
      return tempOps.newSnapshotId();
    }
  }

  /**
   * 事务内的表视图：把对表的各种操作委托回外层 BaseTransaction，并基于事务 current 元数据提供查询。
   *
   * <p>设计意图：让引擎/调用方拿到一个"看起来是真实表"的对象，在其上发起操作， 实际所有变更都被纳入事务管理。不支持扫描（事务表无快照可扫）。
   */
  public class TransactionTable implements Table, HasTableOperations, Serializable {

    /** 返回事务内表操作句柄。 */
    @Override
    public TableOperations operations() {
      return transactionOps;
    }

    /** 返回表名。 */
    @Override
    public String name() {
      return tableName;
    }

    /** 事务表不支持刷新（无操作）。 */
    @Override
    public void refresh() {}

    /** 事务表不支持扫描。 */
    @Override
    public TableScan newScan() {
      throw new UnsupportedOperationException("Transaction tables do not support scans");
    }

    /** 返回事务当前 schema。 */
    @Override
    public Schema schema() {
      return current.schema();
    }

    /** 返回事务当前所有 schema。 */
    @Override
    public Map<Integer, Schema> schemas() {
      return current.schemasById();
    }

    /** 返回事务当前分区 spec。 */
    @Override
    public PartitionSpec spec() {
      return current.spec();
    }

    /** 返回事务当前所有分区 spec。 */
    @Override
    public Map<Integer, PartitionSpec> specs() {
      return current.specsById();
    }

    /** 返回事务当前排序顺序。 */
    @Override
    public SortOrder sortOrder() {
      return current.sortOrder();
    }

    /** 返回事务当前所有排序顺序。 */
    @Override
    public Map<Integer, SortOrder> sortOrders() {
      return current.sortOrdersById();
    }

    /** 返回事务当前表属性。 */
    @Override
    public Map<String, String> properties() {
      return current.properties();
    }

    /** 返回事务当前表位置。 */
    @Override
    public String location() {
      return current.location();
    }

    /** 返回事务当前快照。 */
    @Override
    public Snapshot currentSnapshot() {
      return current.currentSnapshot();
    }

    /** 按快照 id 返回事务中的快照。 */
    @Override
    public Snapshot snapshot(long snapshotId) {
      return current.snapshot(snapshotId);
    }

    /** 返回事务中所有快照。 */
    @Override
    public Iterable<Snapshot> snapshots() {
      return current.snapshots();
    }

    /** 返回事务中快照日志。 */
    @Override
    public List<HistoryEntry> history() {
      return current.snapshotLog();
    }

    /** 委托外层事务创建 schema 更新。 */
    @Override
    public UpdateSchema updateSchema() {
      return BaseTransaction.this.updateSchema();
    }

    /** 委托外层事务创建分区 spec 更新。 */
    @Override
    public UpdatePartitionSpec updateSpec() {
      return BaseTransaction.this.updateSpec();
    }

    /** 委托外层事务创建表属性更新。 */
    @Override
    public UpdateProperties updateProperties() {
      return BaseTransaction.this.updateProperties();
    }

    /** 委托外层事务创建排序顺序替换。 */
    @Override
    public ReplaceSortOrder replaceSortOrder() {
      return BaseTransaction.this.replaceSortOrder();
    }

    /** 委托外层事务创建表位置更新。 */
    @Override
    public UpdateLocation updateLocation() {
      return BaseTransaction.this.updateLocation();
    }

    /** 委托外层事务创建合并追加。 */
    @Override
    public AppendFiles newAppend() {
      return BaseTransaction.this.newAppend();
    }

    /** 委托外层事务创建快速追加。 */
    @Override
    public AppendFiles newFastAppend() {
      return BaseTransaction.this.newFastAppend();
    }

    /** 委托外层事务创建文件重写。 */
    @Override
    public RewriteFiles newRewrite() {
      return BaseTransaction.this.newRewrite();
    }

    /** 委托外层事务创建 manifest 重写。 */
    @Override
    public RewriteManifests rewriteManifests() {
      return BaseTransaction.this.rewriteManifests();
    }

    /** 委托外层事务创建覆写。 */
    @Override
    public OverwriteFiles newOverwrite() {
      return BaseTransaction.this.newOverwrite();
    }

    /** 委托外层事务创建行增量。 */
    @Override
    public RowDelta newRowDelta() {
      return BaseTransaction.this.newRowDelta();
    }

    /** 委托外层事务创建分区替换。 */
    @Override
    public ReplacePartitions newReplacePartitions() {
      return BaseTransaction.this.newReplacePartitions();
    }

    /** 委托外层事务创建删除文件。 */
    @Override
    public DeleteFiles newDelete() {
      return BaseTransaction.this.newDelete();
    }

    /** 委托外层事务创建统计信息更新。 */
    @Override
    public UpdateStatistics updateStatistics() {
      return BaseTransaction.this.updateStatistics();
    }

    /** 委托外层事务创建快照过期。 */
    @Override
    public ExpireSnapshots expireSnapshots() {
      return BaseTransaction.this.expireSnapshots();
    }

    /** 事务表不支持管理快照。 */
    @Override
    public ManageSnapshots manageSnapshots() {
      throw new UnsupportedOperationException(
          "Transaction tables do not support managing snapshots");
    }

    /** 事务表不支持嵌套事务。 */
    @Override
    public Transaction newTransaction() {
      throw new UnsupportedOperationException("Cannot create a transaction within a transaction");
    }

    /** 返回事务内 FileIO。 */
    @Override
    public FileIO io() {
      return transactionOps.io();
    }

    /** 返回事务内加密管理器。 */
    @Override
    public EncryptionManager encryption() {
      return transactionOps.encryption();
    }

    /** 返回事务内位置提供者。 */
    @Override
    public LocationProvider locationProvider() {
      return transactionOps.locationProvider();
    }

    /** 返回事务当前统计文件。 */
    @Override
    public List<StatisticsFile> statisticsFiles() {
      return current.statisticsFiles();
    }

    /** 返回事务当前快照引用。 */
    @Override
    public Map<String, SnapshotRef> refs() {
      return current.refs();
    }

    /** 返回表名。 */
    @Override
    public String toString() {
      return name();
    }

    /**
     * 序列化替换：用 {@link SerializableTable#copyOf} 生成可序列化副本。
     *
     * @return 可序列化的表副本
     */
    Object writeReplace() {
      return SerializableTable.copyOf(this);
    }
  }

  /** 测试用：返回底层 ops。 */
  @VisibleForTesting
  TableOperations ops() {
    return ops;
  }

  /** 测试用：返回事务内已登记的删除文件集合。 */
  @VisibleForTesting
  Set<String> deletedFiles() {
    return deletedFiles;
  }

  /**
   * 用于在重试时中断重应用 update 的异常：包装 {@link CommitFailedException}。
   *
   * <p>设计意图：把 CommitFailedException 包装为非受检 RuntimeException 以跳出 Tasks 重试循环， 在 {@link
   * #commitSimpleTransaction} 中捕获并解包抛出原异常。
   */
  private static class PendingUpdateFailedException extends RuntimeException {
    private final CommitFailedException wrapped;

    /**
     * 构造异常，包装给定 CommitFailedException。
     *
     * @param cause 被包装的 CommitFailedException
     */
    private PendingUpdateFailedException(CommitFailedException cause) {
      super(cause);
      this.wrapped = cause;
    }

    /** 返回被包装的 CommitFailedException。 */
    public CommitFailedException wrapped() {
      return wrapped;
    }
  }
}
