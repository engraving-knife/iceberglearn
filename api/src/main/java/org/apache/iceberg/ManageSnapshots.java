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

import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.DuplicateWAPCommitException;
import org.apache.iceberg.exceptions.ValidationException;

/**
 * 快照管理 API：支持回滚、cherry-pick、分支与标签（refs）等表快照级操作。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>回滚（rollback / setCurrentSnapshot / rollbackToTime）：把表当前状态切回历史快照。
 *   <li>Cherry-pick：把审计过的孤立快照的变更应用到当前快照之上，生成新快照。
 *   <li>分支与标签管理：创建/删除/重命名/替换 branch 与 tag，以及设置保留策略。
 * </ul>
 *
 * <p>设计意图：本 API 不允许同时混用 {@link #setCurrentSnapshot(long)} 与 {@link
 * #rollbackToTime(long)}，以避免语义冲突。提交时变更会应用到当前表元数据上， 但与追加类操作不同：本 API 在提交冲突时不会自动重试，而是直接抛出 {@link
 * CommitFailedException}，由调用方决定后续处理。WAP（Write-Audit-Publish） 工作流中重复提交相同 wapId 会抛出 {@link
 * DuplicateWAPCommitException}。
 *
 * <p>上下游关系：由 {@link Table#manageSnapshots()} 创建；下游实现位于 core 模块， 落地为表元数据中的 refs 与 current-snapshot
 * 变更。
 */
public interface ManageSnapshots extends PendingUpdate<Snapshot> {

  /**
   * 把表的当前快照直接设置为指定 ID 的快照。
   *
   * <p>注意：与 {@link #rollbackToTime(long)} 不可同时使用。
   *
   * @param snapshotId 目标快照 ID
   * @return this，便于链式调用
   * @throws IllegalArgumentException 表中不存在该 ID 的快照
   */
  ManageSnapshots setCurrentSnapshot(long snapshotId);

  /**
   * 把表回滚到给定时间戳之前最近的快照。
   *
   * @param timestampMillis 时间戳（毫秒），与 {@link System#currentTimeMillis()} 同基准
   * @return this，便于链式调用
   * @throws IllegalArgumentException 表中在该时间戳之前没有任何快照
   */
  ManageSnapshots rollbackToTime(long timestampMillis);

  /**
   * 把表状态回滚到指定 ID 的快照。
   *
   * <p>该快照必须是当前快照的祖先，否则抛出 {@link ValidationException}。
   *
   * @param snapshotId 目标快照 ID（必须是当前快照的祖先）
   * @return this，便于链式调用
   * @throws IllegalArgumentException 表中不存在该 ID 的快照
   * @throws ValidationException 该快照不是当前快照的祖先
   */
  ManageSnapshots rollbackTo(long snapshotId);

  /**
   * Cherry-pick：把指定快照中支持的变更应用到当前快照之上，并在提交时把生成的新快照设为当前。
   *
   * <p>典型用于审计工作流：新数据先写入一个未发布的孤立快照，审计通过后再 cherry-pick 到 当前快照上发布。
   *
   * @param snapshotId 待应用变更的来源快照 ID
   * @return this，便于链式调用
   * @throws IllegalArgumentException 表中不存在该 ID 的快照
   * @throws DuplicateWAPCommitException WAP 工作流下检测到重复的 wapId 提交
   */
  ManageSnapshots cherrypick(long snapshotId);

  /**
   * 创建一个新分支。若当前快照非空则指向当前快照，否则指向一个新建的空快照。
   *
   * <p>这是带默认值的便捷重载，默认实现抛出 {@link UnsupportedOperationException}，由具体子类提供实现。
   *
   * @param name 分支名
   * @return this，便于链式调用
   * @throws IllegalArgumentException 同名分支已存在
   */
  default ManageSnapshots createBranch(String name) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement createBranch(String)");
  }

  /**
   * 创建一个指向指定快照 ID 的新分支。
   *
   * @param name 分支名
   * @param snapshotId 分支头指向的快照 ID
   * @return this，便于链式调用
   * @throws IllegalArgumentException 同名分支已存在
   */
  ManageSnapshots createBranch(String name, long snapshotId);

  /**
   * 创建一个指向指定快照 ID 的新标签。
   *
   * @param name 标签名
   * @param snapshotId 标签指向的快照 ID
   * @return this，便于链式调用
   * @throws IllegalArgumentException 同名标签已存在
   */
  ManageSnapshots createTag(String name, long snapshotId);

  /**
   * 按名称删除分支。
   *
   * @param name 分支名
   * @return this，便于链式调用
   * @throws IllegalArgumentException 该分支不存在
   */
  ManageSnapshots removeBranch(String name);

  /**
   * 重命名分支。
   *
   * @param name 待重命名的分支名
   * @param newName 新分支名
   * @return this，便于链式调用
   * @throws IllegalArgumentException 待重命名分支不存在，或新名称已被占用
   */
  ManageSnapshots renameBranch(String name, String newName);

  /**
   * 按名称删除标签。
   *
   * @param name 标签名
   * @return this，便于链式调用
   * @throws IllegalArgumentException 该标签不存在
   */
  ManageSnapshots removeTag(String name);

  /**
   * 把指定标签重置为指向另一个快照。
   *
   * @param name 待替换的标签名
   * @param snapshotId 标签新指向的快照 ID
   * @return this，便于链式调用
   */
  ManageSnapshots replaceTag(String name, long snapshotId);

  /**
   * 把指定分支重置为指向另一个快照。
   *
   * @param name 待替换的分支名
   * @param snapshotId 分支新指向的快照 ID
   * @return this，便于链式调用
   */
  ManageSnapshots replaceBranch(String name, long snapshotId);

  /**
   * 把指定分支替换为指向某个源引用的当前快照，源引用保持不变，目标分支保留自身的保留策略。
   *
   * @param name 待替换的分支名
   * @param source 源引用名
   * @return this，便于链式调用
   */
  ManageSnapshots replaceBranch(String name, String source);

  /**
   * 快进：若目标分支是源引用的祖先，则把目标分支快进到源引用的快照，源引用保持不变， 目标分支保留自身的保留策略。
   *
   * @param name 待快进的目标分支名
   * @param source 源引用名
   * @return this，便于链式调用
   * @throws IllegalArgumentException 目标分支不是源引用的祖先
   */
  ManageSnapshots fastForwardBranch(String name, String source);

  /**
   * 设置分支上保留的最小快照数。
   *
   * @param branchName 分支名
   * @param minSnapshotsToKeep 最小保留快照数
   * @return this，便于链式调用
   * @throws IllegalArgumentException 该分支不存在
   */
  ManageSnapshots setMinSnapshotsToKeep(String branchName, int minSnapshotsToKeep);

  /**
   * 设置分支上快照的最大保留时长（毫秒）。
   *
   * @param branchName 分支名
   * @param maxSnapshotAgeMs 最大快照年龄（毫秒）
   * @return this，便于链式调用
   * @throws IllegalArgumentException 该分支不存在
   */
  ManageSnapshots setMaxSnapshotAgeMs(String branchName, long maxSnapshotAgeMs);

  /**
   * 设置引用本身的最大保留时长（毫秒），到期后该引用会被清理。
   *
   * @param name 引用名（分支或标签）
   * @param maxRefAgeMs 引用自身的保留年龄（毫秒）
   * @return this，便于链式调用
   * @throws IllegalArgumentException 该引用不存在
   */
  ManageSnapshots setMaxRefAgeMs(String name, long maxRefAgeMs);
}
