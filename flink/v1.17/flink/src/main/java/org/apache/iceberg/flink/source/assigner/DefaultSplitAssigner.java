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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.flink.annotation.Internal;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitStatus;
import org.apache.iceberg.flink.source.split.SerializableComparator;

/**
 * 文件级说明：默认的 split 分配器实现。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/assigner 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护待分配的 split 队列（可按 comparator 排序）。
 *   <li>响应 enumerator 的 add/clear 请求，并为 reader 请求提供 split。
 *   <li>跟踪已分配但未完成的 split，回收失败的 split。
 * </ul>
 *
 * <p>设计意图：所有方法都在 source coordinator 线程中由 enumerator 调用， 无需加锁；通过 {@link CompletableFuture} 通知等待
 * split 的 reader。
 *
 * <p>上下游关系：上游为 {@link org.apache.iceberg.flink.source.enumerator.AbstractIcebergEnumerator} 添加
 * split，下游为 reader 通过 {@code SplitRequestEvent} 请求 split。
 */
@Internal
public class DefaultSplitAssigner implements SplitAssigner {

  private final Queue<IcebergSourceSplit> pendingSplits;
  private CompletableFuture<Void> availableFuture;

  public DefaultSplitAssigner(SerializableComparator<IcebergSourceSplit> comparator) {
    this.pendingSplits = comparator == null ? new ArrayDeque<>() : new PriorityQueue<>(comparator);
  }

  public DefaultSplitAssigner(
      SerializableComparator<IcebergSourceSplit> comparator,
      Collection<IcebergSourceSplitState> assignerState) {
    this(comparator);
    // Because default assigner only tracks unassigned splits,
    // there is no need to filter splits based on status (unassigned) here.
    assignerState.forEach(splitState -> pendingSplits.add(splitState.split()));
  }

  @Override
  public synchronized GetSplitResult getNext(@Nullable String hostname) {
    if (pendingSplits.isEmpty()) {
      return GetSplitResult.unavailable();
    } else {
      IcebergSourceSplit split = pendingSplits.poll();
      return GetSplitResult.forSplit(split);
    }
  }

  @Override
  public void onDiscoveredSplits(Collection<IcebergSourceSplit> splits) {
    addSplits(splits);
  }

  @Override
  public void onUnassignedSplits(Collection<IcebergSourceSplit> splits) {
    addSplits(splits);
  }

  private synchronized void addSplits(Collection<IcebergSourceSplit> splits) {
    if (!splits.isEmpty()) {
      pendingSplits.addAll(splits);
      // only complete pending future if new splits are discovered
      completeAvailableFuturesIfNeeded();
    }
  }

  /** Simple assigner only tracks unassigned splits */
  @Override
  public synchronized Collection<IcebergSourceSplitState> state() {
    return pendingSplits.stream()
        .map(split -> new IcebergSourceSplitState(split, IcebergSourceSplitStatus.UNASSIGNED))
        .collect(Collectors.toList());
  }

  @Override
  public synchronized CompletableFuture<Void> isAvailable() {
    if (availableFuture == null) {
      availableFuture = new CompletableFuture<>();
    }
    return availableFuture;
  }

  @Override
  public synchronized int pendingSplitCount() {
    return pendingSplits.size();
  }

  private synchronized void completeAvailableFuturesIfNeeded() {
    if (availableFuture != null && !pendingSplits.isEmpty()) {
      availableFuture.complete(null);
    }
    availableFuture = null;
  }
}
