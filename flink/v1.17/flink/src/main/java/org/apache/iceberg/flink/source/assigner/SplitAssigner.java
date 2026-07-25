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
package org.apache.iceberg.flink.source.assigner;

import java.io.Closeable;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.iceberg.flink.source.ScanContext;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;

/**
 * 文件级说明：Split 分配器接口，可插拔的 split 分配策略。
 *
 * <p>所属模块：iceberg-flink（source/assigner 子包），由 source enumerator 调用。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>向 enumerator 提供 split 分配（getNext）。
 *   <li>接收 enumerator 发现的新 split（onDiscoveredSplits）。
 *   <li>接收 reader 失败后退回的 split（onUnassignedSplits）。
 *   <li>跟踪已完成 split（onCompletedSplits），用于 event time 对齐等场景。
 * </ul>
 *
 * <p>设计意图：将分配策略从 enumerator 中解耦，支持多种策略：
 *
 * <ul>
 *   <li>简单分配器：无顺序保证，无本地性优化。
 *   <li>本地性感知分配器：优先分配本地 split。
 *   <li>快照感知分配器：按提交顺序分配 split。
 *   <li>Event time 对齐分配器：满足时间顺序约束的分配。
 * </ul>
 *
 * <p>线程安全：Assigner 实现需线程安全。Enumerator 主要从协调器线程调用 API， 但可能从 I/O 线程调用 {@link #pendingSplitCount()}。
 *
 * <p>上下游关系：被 source enumerator（如 {@link AbstractIcebergEnumerator}）调用； 管理 {@link
 * IcebergSourceSplit} 的分配生命周期。
 */
public interface SplitAssigner extends Closeable {

  /** 启动 assigner（如启动后台线程或注册事件监听器），默认空实现。 */
  default void start() {}

  /** 关闭 assigner，默认空实现。 */
  @Override
  default void close() {}

  /**
   * 请求分配一个新的 split。
   *
   * <p>逻辑：enumerator 在尝试为等待中的 reader 分配 split 时调用。若无法分配（如 reader 断开）， enumerator 应通过 {@link
   * #onUnassignedSplits} 将 split 退回。
   *
   * @param hostname reader 所在主机名（可用于本地性优化，可为 null）
   * @return 分配结果
   */
  GetSplitResult getNext(@Nullable String hostname);

  /** 添加 enumerator 新发现的 split。 */
  void onDiscoveredSplits(Collection<IcebergSourceSplit> splits);

  /** 将退回的 split（来自失败的 reader）转发给 assigner。 */
  void onUnassignedSplits(Collection<IcebergSourceSplit> splits);

  /** 通知 assigner 已完成的 split（用于 event time 对齐等场景推进 watermark），默认空实现。 */
  default void onCompletedSplits(Collection<String> completedSplitIds) {}

  /**
   * Get assigner state for checkpointing. This is a super-set API that works for all currently
   * imagined assigners.
   */
  Collection<IcebergSourceSplitState> state();

  /**
   * Enumerator can get a notification via CompletableFuture when the assigner has more splits
   * available later. Enumerator should schedule assignment in the thenAccept action of the future.
   *
   * <p>Assigner will return the same future if this method is called again before the previous
   * future is completed.
   *
   * <p>The future can be completed from other thread, e.g. the coordinator thread from another
   * thread for event time alignment.
   *
   * <p>If enumerator need to trigger action upon the future completion, it may want to run it in
   * the coordinator thread using {@link SplitEnumeratorContext#runInCoordinatorThread(Runnable)}.
   */
  CompletableFuture<Void> isAvailable();

  /**
   * Return the number of pending splits that haven't been assigned yet.
   *
   * <p>The enumerator can poll this API to publish a metric on the number of pending splits.
   *
   * <p>The enumerator can also use this information to throttle split discovery for streaming read.
   * If there are already many pending splits tracked by the assigner, it is undesirable to discover
   * more splits and track them in the assigner. That will increase the memory footprint and
   * enumerator checkpoint size.
   *
   * <p>Throttling works better together with {@link ScanContext#maxPlanningSnapshotCount()}.
   * Otherwise, the next split discovery after throttling will just discover all non-enumerated
   * snapshots and splits, which defeats the purpose of throttling.
   */
  int pendingSplitCount();
}
