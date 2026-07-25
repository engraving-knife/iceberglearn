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

/**
 * 事务装饰器：在委托事务提交成功后执行附加回调。
 *
 * <p>所属模块：iceberg-core，实现 {@link Transaction} 接口，用于在不修改原事务实现的前提下 挂载提交后副作用（如通知、缓存清理、指标上报）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将所有 {@link Transaction} 方法调用透传给被包装的 {@code wrapped} 事务。
 *   <li>仅在 {@link #commitTransaction()} 成功后触发 {@code callback}。
 * </ul>
 *
 * <p>设计意图：采用装饰器模式而非继承，使任意 {@link Transaction} 实现均可附加回调， 避免侵入具体事务类。{@link #addCallback(Transaction,
 * Runnable)} 是唯一构造入口， 保证回调与事务的绑定关系由工厂方法统一管理。
 *
 * <p>上下游关系：由需要"提交后回调"的调用方（如表 API 包装层）通过 {@link #addCallback} 创建， 内部委托给 {@link BaseTransaction}
 * 等实际事务实现。
 */
class CommitCallbackTransaction implements Transaction {
  /**
   * 工厂方法：为给定事务附加提交后回调。
   *
   * @param txn 被包装的事务
   * @param callback 提交成功后执行的回调
   * @return 装饰后的事务
   */
  static Transaction addCallback(Transaction txn, Runnable callback) {
    return new CommitCallbackTransaction(txn, callback);
  }

  private final Transaction wrapped;
  private final Runnable callback;

  private CommitCallbackTransaction(Transaction wrapped, Runnable callback) {
    this.wrapped = wrapped;
    this.callback = callback;
  }

  /** 透传：返回被包装事务对应的表。 */
  @Override
  public Table table() {
    return wrapped.table();
  }

  /** 透传：返回被包装事务的 schema 更新器。 */
  @Override
  public UpdateSchema updateSchema() {
    return wrapped.updateSchema();
  }

  /** 透传：返回被包装事务的分区 spec 更新器。 */
  @Override
  public UpdatePartitionSpec updateSpec() {
    return wrapped.updateSpec();
  }

  /** 透传：返回被包装事务的表属性更新器。 */
  @Override
  public UpdateProperties updateProperties() {
    return wrapped.updateProperties();
  }

  /** 透传：返回被包装事务的排序顺序替换器。 */
  @Override
  public ReplaceSortOrder replaceSortOrder() {
    return wrapped.replaceSortOrder();
  }

  /** 透传：返回被包装事务的表位置更新器。 */
  @Override
  public UpdateLocation updateLocation() {
    return wrapped.updateLocation();
  }

  /** 透传：返回被包装事务的快速追加文件操作。 */
  @Override
  public AppendFiles newFastAppend() {
    return wrapped.newFastAppend();
  }

  /** 透传：返回被包装事务的追加文件操作。 */
  @Override
  public AppendFiles newAppend() {
    return wrapped.newAppend();
  }

  /** 透传：返回被包装事务的文件重写操作。 */
  @Override
  public RewriteFiles newRewrite() {
    return wrapped.newRewrite();
  }

  /** 透传：返回被包装事务的 manifest 重写操作。 */
  @Override
  public RewriteManifests rewriteManifests() {
    return wrapped.rewriteManifests();
  }

  /** 透传：返回被包装事务的覆盖文件操作。 */
  @Override
  public OverwriteFiles newOverwrite() {
    return wrapped.newOverwrite();
  }

  /** 透传：返回被包装事务的行级变更操作。 */
  @Override
  public RowDelta newRowDelta() {
    return wrapped.newRowDelta();
  }

  /** 透传：返回被包装事务的分区替换操作。 */
  @Override
  public ReplacePartitions newReplacePartitions() {
    return wrapped.newReplacePartitions();
  }

  /** 透传：返回被包装事务的删除文件操作。 */
  @Override
  public DeleteFiles newDelete() {
    return wrapped.newDelete();
  }

  /** 透传：返回被包装事务的统计信息更新器。 */
  @Override
  public UpdateStatistics updateStatistics() {
    return wrapped.updateStatistics();
  }

  /** 透传：返回被包装事务的快照过期操作。 */
  @Override
  public ExpireSnapshots expireSnapshots() {
    return wrapped.expireSnapshots();
  }

  /** 透传：返回被包装事务的快照管理器。 */
  @Override
  public ManageSnapshots manageSnapshots() {
    return wrapped.manageSnapshots();
  }

  /**
   * 提交事务：先委托被包装事务提交，提交成功后触发回调。
   *
   * <p>逻辑：调用 {@code wrapped.commitTransaction()}，若未抛异常则执行 {@code callback.run()}。
   * 若被包装事务抛出异常，回调不执行，异常向上传播。
   */
  @Override
  public void commitTransaction() {
    wrapped.commitTransaction();
    callback.run();
  }
}
