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
package org.apache.iceberg.nessie;

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.Reference;

/**
 * 文件级说明：Nessie 引用（分支/Tag/Hash）的可更新封装。
 *
 * <p>所属模块：iceberg-nessie。职责：包装 Nessie 的 {@link Reference}（Branch 或 Tag）， 跟踪其当前指向的 commit
 * hash，并区分该引用是否"可变"（mutable）——只有未指定 hash 的 Branch 才可变，允许执行 commit/merge 等写操作；指定了 hash 的引用或 Tag
 * 视为只读快照。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把"引用可变性"显式化：通过 mutable 标志在构造期决定是否允许更新，避免写操作误用到 只读引用上。
 *   <li>支持 refresh 机制：在并发提交场景下重新拉取分支最新 hash，实现乐观锁重试。
 * </ul>
 *
 * <p>上下游：上游由 {@link NessieIcebergClient} 构造；下游被 Iceberg 的 commit 流程用于 获取/更新 Nessie 分支的 hash。
 */
class UpdateableReference {

  private Reference reference;
  private final boolean mutable;

  /**
   * 构造可更新引用。
   *
   * <p>逻辑：mutable = reference 是 Branch 且未显式指定 hash（hashReference=false）。 即只有"分支 + 不带 hash"的引用才可变。
   *
   * @param reference Nessie 引用对象（Branch/Tag）
   * @param hashReference 是否使用显式 hash 构造（true 表示只读快照）
   */
  UpdateableReference(Reference reference, boolean hashReference) {
    this.reference = reference;
    this.mutable = reference instanceof Branch && !hashReference;
  }

  /**
   * 刷新引用：从 Nessie 重新拉取分支最新状态。
   *
   * <p>逻辑：不可变引用直接返回 false；可变引用调用 Nessie API 按 refName 重新获取， 比较新旧 reference 是否变化。
   *
   * @param api Nessie API 客户端
   * @return true 表示引用 hash 发生变化
   * @throws NessieNotFoundException 若分支不存在
   */
  public boolean refresh(NessieApiV1 api) throws NessieNotFoundException {
    if (!mutable) {
      return false;
    }
    Reference oldReference = reference;
    reference = api.getReference().refName(reference.getName()).get();
    return !oldReference.equals(reference);
  }

  /**
   * 更新当前引用为指定 ref（用于 commit 后更新本地 hash）。
   *
   * @param ref 新的 Nessie 引用
   * @throws IllegalStateException 若引用不可变
   * @throws NullPointerException 若 ref 为 null
   */
  public void updateReference(Reference ref) {
    Preconditions.checkState(mutable, "Hash references cannot be updated.");
    this.reference = Preconditions.checkNotNull(ref, "ref is null");
  }

  /** 返回当前引用指向的 commit hash。 */
  public String getHash() {
    return reference.getHash();
  }

  /** 返回当前 Nessie 引用对象。 */
  public Reference getReference() {
    return reference;
  }

  /**
   * 校验本引用可变，否则抛异常。
   *
   * @throws IllegalArgumentException 若引用不可变（即使用了 hash 或非 Branch）
   */
  public void checkMutable() {
    Preconditions.checkArgument(
        mutable, "You can only mutate tables when using a branch without a hash or timestamp.");
  }

  /** 返回引用名称（分支名或 Tag 名）。 */
  public String getName() {
    return reference.getName();
  }

  /** 返回本引用是否可变（即未指定 hash 的 Branch）。 */
  public boolean isMutable() {
    return mutable;
  }
}
