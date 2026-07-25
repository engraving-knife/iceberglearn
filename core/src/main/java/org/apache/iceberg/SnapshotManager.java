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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 快照管理器（{@link ManageSnapshots} 的 core 实现）。
 *
 * <p>所属模块：iceberg-core。职责：对已有快照执行 cherry-pick、回滚、设置当前快照、分支/标签管理等操作。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>基于事务：通过 {@link BaseTransaction} 把多个快照操作组合成一次事务提交，保证原子性。
 *   <li>外部事务支持：可接收外部已存在的事务，嵌入更大的事务上下文。
 *   <li>引用更新分离：单独维护 {@code updateSnapshotReferencesOperation}，commit 前先处理引用更新。
 * </ul>
 *
 * <p>上下游关系：上游为 {@link Table#manageSnapshots()}；下游依赖 {@link BaseTransaction} 提交。
 */
public class SnapshotManager implements ManageSnapshots {

  private final boolean isExternalTransaction;
  private final BaseTransaction transaction;
  private UpdateSnapshotReferencesOperation updateSnapshotReferencesOperation;

  SnapshotManager(String tableName, TableOperations ops) {
    Preconditions.checkState(
        ops.current() != null, "Cannot manage snapshots: table %s does not exist", tableName);
    this.transaction =
        new BaseTransaction(tableName, ops, BaseTransaction.TransactionType.SIMPLE, ops.refresh());
    this.isExternalTransaction = false;
  }

  SnapshotManager(BaseTransaction transaction) {
    Preconditions.checkArgument(transaction != null, "Invalid input transaction: null");
    this.transaction = transaction;
    this.isExternalTransaction = true;
  }

  /**
   * cherry-pick 指定快照到当前分支。
   *
   * @param snapshotId 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots cherrypick(long snapshotId) {
    commitIfRefUpdatesExist();
    transaction.cherryPick().cherrypick(snapshotId).commit();
    return this;
  }

  /**
   * 设置CurrentSnapshot。
   *
   * @param snapshotId 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots setCurrentSnapshot(long snapshotId) {
    commitIfRefUpdatesExist();
    transaction.setBranchSnapshot().setCurrentSnapshot(snapshotId).commit();
    return this;
  }

  /**
   * 回滚到指定时间戳对应的快照。
   *
   * @param timestampMillis 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots rollbackToTime(long timestampMillis) {
    commitIfRefUpdatesExist();
    transaction.setBranchSnapshot().rollbackToTime(timestampMillis).commit();
    return this;
  }

  /**
   * 回滚到指定快照。
   *
   * @param snapshotId 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots rollbackTo(long snapshotId) {
    commitIfRefUpdatesExist();
    transaction.setBranchSnapshot().rollbackTo(snapshotId).commit();
    return this;
  }

  /**
   * 创建分支。
   *
   * @param name 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots createBranch(String name) {
    Snapshot currentSnapshot = transaction.currentMetadata().currentSnapshot();
    if (currentSnapshot != null) {
      return createBranch(name, currentSnapshot.snapshotId());
    }

    SnapshotRef existingRef = transaction.currentMetadata().ref(name);
    Preconditions.checkArgument(existingRef == null, "Ref %s already exists", name);
    // Create an empty snapshot for the branch
    transaction.newFastAppend().toBranch(name).commit();
    return this;
  }

  /**
   * 创建分支。
   *
   * @param name 参数
   * @param snapshotId 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots createBranch(String name, long snapshotId) {
    updateSnapshotReferencesOperation().createBranch(name, snapshotId);
    return this;
  }

  /**
   * 创建标签。
   *
   * @param name 参数
   * @param snapshotId 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots createTag(String name, long snapshotId) {
    updateSnapshotReferencesOperation().createTag(name, snapshotId);
    return this;
  }

  /**
   * 移除分支。
   *
   * @param name 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots removeBranch(String name) {
    updateSnapshotReferencesOperation().removeBranch(name);
    return this;
  }

  /**
   * 移除标签。
   *
   * @param name 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots removeTag(String name) {
    updateSnapshotReferencesOperation().removeTag(name);
    return this;
  }

  /**
   * 设置MinSnapshotsTo。
   *
   * @param name 参数
   * @param minSnapshotsToKeep 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots setMinSnapshotsToKeep(String name, int minSnapshotsToKeep) {
    updateSnapshotReferencesOperation().setMinSnapshotsToKeep(name, minSnapshotsToKeep);
    return this;
  }

  /**
   * 设置MaxSnapshotAge。
   *
   * @param name 参数
   * @param maxSnapshotAgeMs 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots setMaxSnapshotAgeMs(String name, long maxSnapshotAgeMs) {
    updateSnapshotReferencesOperation().setMaxSnapshotAgeMs(name, maxSnapshotAgeMs);
    return this;
  }

  /**
   * 设置MaxRefAge。
   *
   * @param name 参数
   * @param maxRefAgeMs 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots setMaxRefAgeMs(String name, long maxRefAgeMs) {
    updateSnapshotReferencesOperation().setMaxRefAgeMs(name, maxRefAgeMs);
    return this;
  }

  /**
   * 替换标签的快照引用。
   *
   * @param name 参数
   * @param snapshotId 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots replaceTag(String name, long snapshotId) {
    updateSnapshotReferencesOperation().replaceTag(name, snapshotId);
    return this;
  }

  /**
   * 替换分支的快照引用。
   *
   * @param name 参数
   * @param snapshotId 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots replaceBranch(String name, long snapshotId) {
    updateSnapshotReferencesOperation().replaceBranch(name, snapshotId);
    return this;
  }

  /**
   * 替换分支的快照引用。
   *
   * @param name 参数
   * @param source 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots replaceBranch(String name, String source) {
    updateSnapshotReferencesOperation().replaceBranch(name, source);
    return this;
  }

  /**
   * 快进分支到指定分支的最新快照。
   *
   * @param name 参数
   * @param source 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots fastForwardBranch(String name, String source) {
    updateSnapshotReferencesOperation().fastForward(name, source);
    return this;
  }

  /**
   * 重命名分支。
   *
   * @param name 参数
   * @param newName 参数
   * @return 返回值
   */
  @Override
  public ManageSnapshots renameBranch(String name, String newName) {
    updateSnapshotReferencesOperation().renameBranch(name, newName);
    return this;
  }

  /**
   * 更新SnapshotReferencesOperation。
   *
   * @return 返回值
   */
  private UpdateSnapshotReferencesOperation updateSnapshotReferencesOperation() {
    if (updateSnapshotReferencesOperation == null) {
      this.updateSnapshotReferencesOperation = transaction.updateSnapshotReferencesOperation();
    }

    return updateSnapshotReferencesOperation;
  }

  private void commitIfRefUpdatesExist() {
    if (updateSnapshotReferencesOperation != null) {
      updateSnapshotReferencesOperation.commit();
      updateSnapshotReferencesOperation = null;
    }
  }

  /**
   * 应用累积变更，生成新的表元数据。
   *
   * @return 返回值
   */
  @Override
  public Snapshot apply() {
    return transaction.table().currentSnapshot();
  }

  @Override
  public void commit() {
    commitIfRefUpdatesExist();
    if (!isExternalTransaction) {
      transaction.commitTransaction();
    }
  }
}
