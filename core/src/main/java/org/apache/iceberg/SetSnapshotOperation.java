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

import java.util.List;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.Tasks;

/**
 * 设置当前快照或回滚快照的操作（iceberg-core 快照管理层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>直接将当前快照切换到指定快照；
 *   <li>支持按快照 ID 或按时间戳回滚；
 *   <li>通过重试机制提交新元数据，并将分支快照设置到 MAIN 分支。
 * </ul>
 *
 * <p>设计意图：
 *
 * <p>该更新不通过 Table API 暴露，而是作为包私有的 Transaction API 一部分， 供 {@link ManageSnapshots}
 * 使用。提交时即使元数据未变也会执行，以保证事务提交状态推进； 同时在每次重试时重新生成 UUID，避免与并发操作的 UUID 分配冲突。
 *
 * <p>上下游关系：由 {@link ManageSnapshots} 创建，依赖 {@link TableOperations}、 {@link TableMetadata} 与 {@link
 * SnapshotUtil}。
 */
class SetSnapshotOperation implements PendingUpdate<Snapshot> {

  private final TableOperations ops;
  private TableMetadata base;
  private Long targetSnapshotId = null;
  private boolean isRollback = false;

  /**
   * 构造方法，以当前表元数据作为基准。
   *
   * @param ops 表操作句柄
   */
  SetSnapshotOperation(TableOperations ops) {
    this.ops = ops;
    this.base = ops.current();
  }

  /**
   * 将当前快照直接切换到指定快照 ID。
   *
   * @param snapshotId 目标快照 ID
   * @return 当前操作实例，用于链式调用
   */
  public SetSnapshotOperation setCurrentSnapshot(long snapshotId) {
    ValidationException.check(
        base.snapshot(snapshotId) != null,
        "Cannot roll back to unknown snapshot id: %s",
        snapshotId);

    this.targetSnapshotId = snapshotId;

    return this;
  }

  /**
   * 回滚到给定时间戳之前最新的祖先快照。
   *
   * <p>逻辑：在当前快照的祖先链中查找时间戳小于 timestampMillis 且最新的快照， 设置为回滚目标并标记 isRollback。
   *
   * @param timestampMillis 时间戳（毫秒）
   * @return 当前操作实例
   */
  public SetSnapshotOperation rollbackToTime(long timestampMillis) {
    // find the latest snapshot by timestamp older than timestampMillis
    Snapshot snapshot = findLatestAncestorOlderThan(base, timestampMillis);
    Preconditions.checkArgument(
        snapshot != null, "Cannot roll back, no valid snapshot older than: %s", timestampMillis);

    this.targetSnapshotId = snapshot.snapshotId();
    this.isRollback = true;

    return this;
  }

  /**
   * 回滚到指定快照 ID，要求该快照为当前快照的祖先。
   *
   * <p>逻辑：校验快照存在且为当前快照的祖先后，委托 {@link #setCurrentSnapshot}。
   *
   * @param snapshotId 目标快照 ID
   * @return 当前操作实例
   */
  public SetSnapshotOperation rollbackTo(long snapshotId) {
    TableMetadata current = base;
    ValidationException.check(
        current.snapshot(snapshotId) != null,
        "Cannot roll back to unknown snapshot id: %s",
        snapshotId);
    ValidationException.check(
        isCurrentAncestor(current, snapshotId),
        "Cannot roll back to snapshot, not an ancestor of the current state: %s",
        snapshotId);
    return setCurrentSnapshot(snapshotId);
  }

  /**
   * 计算应用变更后的目标快照。
   *
   * <p>逻辑：刷新基准元数据；若未配置目标快照则返回当前快照（NOOP）； 若为回滚操作则校验目标快照仍是当前快照的祖先；最终返回目标快照。
   *
   * @return 目标快照
   */
  @Override
  public Snapshot apply() {
    this.base = ops.refresh();

    if (targetSnapshotId == null) {
      // if no target snapshot was configured then NOOP by returning current state
      return base.currentSnapshot();
    }

    ValidationException.check(
        !isRollback || isCurrentAncestor(base, targetSnapshotId),
        "Cannot roll back to %s: not an ancestor of the current table state",
        targetSnapshotId);

    return base.snapshot(targetSnapshotId);
  }

  /**
   * 提交快照切换，使用指数退避重试机制。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>按表配置的重试次数与退避参数重试 {@link CommitFailedException}；
   *   <li>每次重试调用 {@link #apply()} 计算目标快照，并通过 {@link TableMetadata#buildFrom} 将目标设为 MAIN 分支快照；
   *   <li>提交时附带 {@link TableMetadata#withUUID} 以确保 UUID 存在。
   * </ol>
   */
  @Override
  public void commit() {
    Tasks.foreach(ops)
        .retry(base.propertyAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
        .exponentialBackoff(
            base.propertyAsInt(COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
            2.0 /* exponential */)
        .onlyRetryOn(CommitFailedException.class)
        .run(
            taskOps -> {
              Snapshot snapshot = apply();
              TableMetadata updated =
                  TableMetadata.buildFrom(base)
                      .setBranchSnapshot(snapshot.snapshotId(), SnapshotRef.MAIN_BRANCH)
                      .build();

              // Do commit this operation even if the metadata has not changed, as we need to
              // advance the hasLastOpCommited for the transaction's commit to work properly.
              // (Without any other operations in the transaction, the commitTransaction() call
              // will be a no-op anyway)

              // if the table UUID is missing, add it here. the UUID will be re-created each time
              // this operation retries
              // to ensure that if a concurrent operation assigns the UUID, this operation will not
              // fail.
              taskOps.commit(base, updated.withUUID());
            });
  }

  /**
   * 在当前快照的祖先链中查找时间戳小于给定时间戳且最新的快照。
   *
   * @param meta 表元数据
   * @param timestampMillis 查找该时间戳之前的快照
   * @return 满足条件的快照，不存在返回 null
   */
  private static Snapshot findLatestAncestorOlderThan(TableMetadata meta, long timestampMillis) {
    long snapshotTimestamp = 0;
    Snapshot result = null;
    for (Long snapshotId : currentAncestors(meta)) {
      Snapshot snapshot = meta.snapshot(snapshotId);
      if (snapshot.timestampMillis() < timestampMillis
          && snapshot.timestampMillis() > snapshotTimestamp) {
        result = snapshot;
        snapshotTimestamp = snapshot.timestampMillis();
      }
    }
    return result;
  }

  /**
   * 返回当前快照的所有祖先快照 ID 列表。
   *
   * @param meta 表元数据
   * @return 祖先快照 ID 列表
   */
  private static List<Long> currentAncestors(TableMetadata meta) {
    return SnapshotUtil.ancestorIds(meta.currentSnapshot(), meta::snapshot);
  }

  /**
   * 判断给定快照 ID 是否为当前快照的祖先。
   *
   * @param meta 表元数据
   * @param snapshotId 待判断的快照 ID
   * @return 是祖先返回 true，否则 false
   */
  private static boolean isCurrentAncestor(TableMetadata meta, long snapshotId) {
    return currentAncestors(meta).contains(snapshotId);
  }
}
