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

import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.SnapshotUtil;

/**
 * 用于批量更新表快照引用（分支/标签）的 {@link PendingUpdate} 实现。
 *
 * <p>所属模块：iceberg-core；层次定位：表元数据变更操作层，介于 Snapshot/Ref 管理 API 与 {@link TableOperations} 提交层之间。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在内存中维护一份待提交的引用映射 {@code updatedRefs}，记录增删改后的引用状态
 *   <li>提供创建/删除/重命名/替换分支与标签、调整保留策略等链式 API
 *   <li>在 {@link #commit()} 时通过 {@link #internalApply()} 计算差异并写入新的 {@link TableMetadata}
 * </ul>
 *
 * <p>设计意图：采用"先在内存中变更、再一次性提交"的模式以减少与底层 catalog 的交互次数；所有变更方法 返回 {@code this}
 * 以支持链式调用；引用映射基于基础元数据拷贝构造，提交时通过比较新旧引用集合计算 增量，保证幂等性。该类非线程安全，假定由单线程在一次事务上下文中使用。
 *
 * <p>上下游关系：依赖 {@link TableOperations} 获取并提交元数据、{@link SnapshotRef} 描述引用、{@link SnapshotUtil}
 * 判断祖先关系；被 {@code ManageSnapshots} 等 API 操作入口调用，最终通过 catalog 实现 落地。
 *
 * <p>待办：将 {@code SetSnapshotOperation} 中的 setCurrentSnapshot、rollBackTime、rollbackTo 等操作
 * 迁移到本类，以支持针对引用的同类操作。
 */
class UpdateSnapshotReferencesOperation implements PendingUpdate<Map<String, SnapshotRef>> {

  /** 用于读写表元数据的底层操作接口。 */
  private final TableOperations ops;
  /** 内存中维护的待提交引用映射，记录增删改后的引用状态。 */
  private final Map<String, SnapshotRef> updatedRefs;
  /** 提交所基于的基础元数据快照，构造时确定。 */
  private TableMetadata base;

  /**
   * 构造引用更新操作实例。
   *
   * <p>逻辑：从 {@code ops} 获取当前元数据作为基线，并拷贝其引用映射作为待提交集合的初始值。
   *
   * @param ops 表操作接口，用于获取并提交元数据
   */
  UpdateSnapshotReferencesOperation(TableOperations ops) {
    this.ops = ops;
    this.base = ops.current();
    this.updatedRefs = Maps.newHashMap(base.refs());
  }

  /** @return 当前待提交的引用映射（未与基础元数据做差异计算） */
  @Override
  public Map<String, SnapshotRef> apply() {
    return updatedRefs;
  }

  /**
   * 提交引用变更到底层表元数据。
   *
   * <p>逻辑：先通过 {@link #internalApply()} 计算出新的 {@link TableMetadata}，再调用 {@link
   * TableOperations#commit(TableMetadata, TableMetadata)} 进行乐观提交。
   */
  @Override
  public void commit() {
    TableMetadata updated = internalApply();
    ops.commit(base, updated);
  }

  /**
   * 创建一个指向指定快照的分支引用。
   *
   * @param name 分支名称，不能为 null
   * @param snapshotId 分支指向的快照 ID
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null 或同名引用已存在时抛出
   */
  public UpdateSnapshotReferencesOperation createBranch(String name, long snapshotId) {
    Preconditions.checkNotNull(name, "Branch name cannot be null");
    SnapshotRef branch = SnapshotRef.branchBuilder(snapshotId).build();
    SnapshotRef existingRef = updatedRefs.put(name, branch);
    Preconditions.checkArgument(existingRef == null, "Ref %s already exists", name);
    return this;
  }

  /**
   * 创建一个指向指定快照的标签引用。
   *
   * @param name 标签名称，不能为 null
   * @param snapshotId 标签指向的快照 ID
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null 或同名引用已存在时抛出
   */
  public UpdateSnapshotReferencesOperation createTag(String name, long snapshotId) {
    Preconditions.checkNotNull(name, "Tag name cannot be null");
    SnapshotRef tag = SnapshotRef.tagBuilder(snapshotId).build();
    SnapshotRef existingRef = updatedRefs.put(name, tag);
    Preconditions.checkArgument(existingRef == null, "Ref %s already exists", name);
    return this;
  }

  /**
   * 删除指定名称的分支引用。
   *
   * <p>逻辑：禁止删除主分支（main）；移除前校验引用存在且确为分支类型。
   *
   * @param name 分支名称，不能为 null
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null、为主分支、不存在或非分支时抛出
   */
  public UpdateSnapshotReferencesOperation removeBranch(String name) {
    Preconditions.checkNotNull(name, "Branch name cannot be null");
    Preconditions.checkArgument(!name.equals(SnapshotRef.MAIN_BRANCH), "Cannot remove main branch");
    SnapshotRef ref = updatedRefs.remove(name);
    Preconditions.checkArgument(ref != null, "Branch does not exist: %s", name);
    Preconditions.checkArgument(ref.isBranch(), "Ref %s is a tag not a branch", name);
    return this;
  }

  /**
   * 删除指定名称的标签引用。
   *
   * <p>逻辑：移除前校验引用存在且确为标签类型。
   *
   * @param name 标签名称，不能为 null
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null、不存在或非标签时抛出
   */
  public UpdateSnapshotReferencesOperation removeTag(String name) {
    Preconditions.checkNotNull(name, "Tag name cannot be null");
    SnapshotRef ref = updatedRefs.remove(name);
    Preconditions.checkArgument(ref != null, "Tag does not exist: %s", name);
    Preconditions.checkArgument(ref.isTag(), "Ref %s is a branch not a tag", name);
    return this;
  }

  /**
   * 重命名分支引用。
   *
   * <p>逻辑：禁止重命名主分支；校验原分支存在且为分支类型、目标名称尚未被占用，然后将引用以新名称 重新插入映射并移除旧名称。
   *
   * @param name 原分支名称，不能为 null
   * @param newName 新分支名称，不能为 null
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当参数为 null、原分支为主分支、不存在、非分支或目标名称已存在时抛出
   */
  public UpdateSnapshotReferencesOperation renameBranch(String name, String newName) {
    Preconditions.checkNotNull(name, "Branch to rename cannot be null");
    Preconditions.checkNotNull(newName, "New branch name cannot be null");
    Preconditions.checkArgument(!name.equals(SnapshotRef.MAIN_BRANCH), "Cannot rename main branch");
    SnapshotRef ref = updatedRefs.get(name);
    Preconditions.checkArgument(ref != null, "Branch does not exist: %s", name);
    Preconditions.checkArgument(ref.isBranch(), "Ref %s is a tag not a branch", name);
    SnapshotRef existing = updatedRefs.put(newName, ref);
    Preconditions.checkArgument(existing == null, "Ref %s already exists", newName);
    updatedRefs.remove(name, ref);
    return this;
  }

  /**
   * 将指定分支替换为指向新的快照 ID。
   *
   * @param name 分支名称，不能为 null
   * @param snapshotId 新的快照 ID
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null、分支不存在或引用非分支时抛出
   */
  public UpdateSnapshotReferencesOperation replaceBranch(String name, long snapshotId) {
    Preconditions.checkNotNull(name, "Branch name cannot be null");
    SnapshotRef ref = updatedRefs.get(name);
    Preconditions.checkArgument(ref != null, "Branch does not exist: %s", name);
    Preconditions.checkArgument(ref.isBranch(), "Ref %s is a tag not a branch", name);
    SnapshotRef updatedRef = SnapshotRef.builderFrom(ref, snapshotId).build();
    updatedRefs.put(name, updatedRef);
    return this;
  }

  /**
   * 将指定分支替换为指向另一个引用（分支或标签）当前所指的快照，非快进模式。
   *
   * @param name 待替换的目标分支名称
   * @param source 源引用名称（提供新快照 ID）
   * @return 当前操作实例，用于链式调用
   * @see #replaceBranch(String, String, boolean)
   */
  public UpdateSnapshotReferencesOperation replaceBranch(String name, String source) {
    return replaceBranch(name, source, false);
  }

  /**
   * 将指定分支快进到另一个引用当前所指的快照，要求源快照必须是目标分支的后代。
   *
   * @param name 待快进的目标分支名称
   * @param source 源引用名称（提供新快照 ID）
   * @return 当前操作实例，用于链式调用
   * @see #replaceBranch(String, String, boolean)
   */
  public UpdateSnapshotReferencesOperation fastForward(String name, String source) {
    return replaceBranch(name, source, true);
  }

  /**
   * 将目标分支替换为指向源引用当前所指的快照，可选快进校验。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验目标分支与源引用均存在，且目标确为分支类型
   *   <li>若源与目标当前快照 ID 相同，则无需替换直接返回
   *   <li>基于目标原引用与新快照 ID 构造新的 {@link SnapshotRef}
   *   <li>若 {@code fastForward} 为 true，则校验目标分支的当前快照是源快照的祖先， 否则抛出 {@link IllegalArgumentException}
   *   <li>将新引用写入映射
   * </ol>
   *
   * @param name 目标分支名称，不能为 null
   * @param source 源引用名称，不能为 null
   * @param fastForward 是否要求快进（即目标快照必须是源快照的祖先）
   * @return 当前操作实例，用于链式调用
   */
  private UpdateSnapshotReferencesOperation replaceBranch(
      String name, String source, boolean fastForward) {
    Preconditions.checkNotNull(name, "Target branch cannot be null");
    Preconditions.checkNotNull(source, "Source ref cannot be null");
    SnapshotRef sourceRef = updatedRefs.get(source);
    SnapshotRef refToUpdate = updatedRefs.get(name);
    Preconditions.checkArgument(refToUpdate != null, "Target branch does not exist: %s", name);
    Preconditions.checkArgument(sourceRef != null, "Ref does not exist: %s", source);
    Preconditions.checkArgument(refToUpdate.isBranch(), "Ref %s is a tag not a branch", name);

    // Nothing to replace
    if (sourceRef.snapshotId() == refToUpdate.snapshotId()) {
      return this;
    }

    SnapshotRef updatedRef = SnapshotRef.builderFrom(refToUpdate, sourceRef.snapshotId()).build();

    if (fastForward) {
      boolean targetIsAncestor =
          SnapshotUtil.isAncestorOf(
              sourceRef.snapshotId(), refToUpdate.snapshotId(), base::snapshot);
      Preconditions.checkArgument(
          targetIsAncestor, "Cannot fast-forward: %s is not an ancestor of %s", name, source);
    }

    updatedRefs.put(name, updatedRef);
    return this;
  }

  /**
   * 将指定标签替换为指向新的快照 ID。
   *
   * @param name 标签名称，不能为 null
   * @param snapshotId 新的快照 ID
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null、标签不存在或引用非标签时抛出
   */
  public UpdateSnapshotReferencesOperation replaceTag(String name, long snapshotId) {
    Preconditions.checkNotNull(name, "Tag name cannot be null");
    SnapshotRef ref = updatedRefs.get(name);
    Preconditions.checkArgument(ref != null, "Tag does not exist: %s", name);
    Preconditions.checkArgument(ref.isTag(), "Ref %s is a branch not a tag", name);
    SnapshotRef updatedRef = SnapshotRef.builderFrom(ref, snapshotId).build();
    updatedRefs.put(name, updatedRef);
    return this;
  }

  /**
   * 设置指定分支保留的最小快照数量。
   *
   * @param name 分支名称，不能为 null
   * @param minSnapshotsToKeep 最小保留快照数
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null 或分支不存在时抛出
   */
  public UpdateSnapshotReferencesOperation setMinSnapshotsToKeep(
      String name, int minSnapshotsToKeep) {
    Preconditions.checkNotNull(name, "Branch name cannot be null");
    SnapshotRef ref = updatedRefs.get(name);
    Preconditions.checkArgument(ref != null, "Branch does not exist: %s", name);
    SnapshotRef updateBranch =
        SnapshotRef.builderFrom(ref).minSnapshotsToKeep(minSnapshotsToKeep).build();
    updatedRefs.put(name, updateBranch);
    return this;
  }

  /**
   * 设置指定分支上快照的最大保留时长（毫秒）。
   *
   * @param name 分支名称，不能为 null
   * @param maxSnapshotAgeMs 最大保留时长（毫秒）
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null 或分支不存在时抛出
   */
  public UpdateSnapshotReferencesOperation setMaxSnapshotAgeMs(String name, long maxSnapshotAgeMs) {
    Preconditions.checkNotNull(name, "Branch name cannot be null");
    SnapshotRef ref = updatedRefs.get(name);
    Preconditions.checkArgument(ref != null, "Branch does not exist: %s", name);
    SnapshotRef updateBranch =
        SnapshotRef.builderFrom(ref).maxSnapshotAgeMs(maxSnapshotAgeMs).build();
    updatedRefs.put(name, updateBranch);
    return this;
  }

  /**
   * 设置指定引用（分支或标签）的最大存活时长（毫秒），超过后引用本身将被回收。
   *
   * @param name 引用名称，不能为 null
   * @param maxRefAgeMs 最大存活时长（毫秒）
   * @return 当前操作实例，用于链式调用
   * @throws IllegalArgumentException 当 name 为 null 或引用不存在时抛出
   */
  public UpdateSnapshotReferencesOperation setMaxRefAgeMs(String name, long maxRefAgeMs) {
    Preconditions.checkNotNull(name, "Reference name cannot be null");
    SnapshotRef ref = updatedRefs.get(name);
    Preconditions.checkArgument(ref != null, "Ref does not exist: %s", name);
    SnapshotRef updatedRef = SnapshotRef.builderFrom(ref).maxRefAgeMs(maxRefAgeMs).build();
    updatedRefs.put(name, updatedRef);
    return this;
  }

  /**
   * 将内存中的引用变更应用到基础元数据，生成新的 {@link TableMetadata}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>基于 {@code base} 构造 {@link TableMetadata.Builder}
   *   <li>遍历基础引用集合，删除那些在 {@code updatedRefs} 中已不存在的引用
   *   <li>遍历 {@code updatedRefs}，对于新增或与基础版本不一致的引用，调用 {@code setRef} 写入
   *   <li>构建并返回新的 {@link TableMetadata}
   * </ol>
   *
   * @return 应用变更后的新 {@link TableMetadata}
   */
  private TableMetadata internalApply() {
    TableMetadata.Builder updatedBuilder = TableMetadata.buildFrom(base);
    // Identify references which have been removed
    Map<String, SnapshotRef> currRefs = base.refs();
    for (Map.Entry<String, SnapshotRef> currRefEntry : currRefs.entrySet()) {
      if (!updatedRefs.containsKey(currRefEntry.getKey())) {
        updatedBuilder.removeRef(currRefEntry.getKey());
      }
    }

    // Identify references which have been created or updated.
    for (Map.Entry<String, SnapshotRef> newRefEntry : updatedRefs.entrySet()) {
      final String name = newRefEntry.getKey();
      SnapshotRef currRef = currRefs.get(name);
      SnapshotRef updatedRef = updatedRefs.get(name);
      if (currRef == null || !currRef.equals(updatedRef)) {
        updatedBuilder.setRef(name, updatedRef);
      }
    }

    return updatedBuilder.build();
  }
}
