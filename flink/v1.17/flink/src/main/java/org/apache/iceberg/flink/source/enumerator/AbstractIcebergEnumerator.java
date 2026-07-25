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
package org.apache.iceberg.flink.source.enumerator;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.iceberg.flink.source.assigner.GetSplitResult;
import org.apache.iceberg.flink.source.assigner.SplitAssigner;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.SplitRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Iceberg Flink Source 的枚举器抽象基类。
 *
 * <p>所属模块：iceberg-flink（source/enumerator 子包），实现 Flink 的 {@link SplitEnumerator} 接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>协调 split 的发现和分配：通过 {@link SplitAssigner} 管理 split 的分配策略。
 *   <li>处理 reader 的 split 请求：维护等待 split 的 reader 队列，在有可用 split 时分配。
 *   <li>处理 reader 上报的已完成 split 和退回的 split。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>将 split 分配策略委托给 {@link SplitAssigner}，enumerator 专注于协调逻辑。
 *   <li>使用 CompletableFuture + AtomicReference 实现异步等待：当 assigner 无可用 split 时， reader 进入等待状态，待新
 *       split 发现后通过 future 唤醒。
 *   <li>使用自定义 SplitRequestEvent 携带已完成 split id，而非 Flink 默认的 split 请求机制。
 * </ul>
 *
 * <p>上下游关系：被 {@link IcebergSource} 创建；通过 {@link SplitAssigner} 管理 split； 与 reader 通过 SourceEvent
 * 通信。
 */
abstract class AbstractIcebergEnumerator
    implements SplitEnumerator<IcebergSourceSplit, IcebergEnumeratorState> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractIcebergEnumerator.class);

  private final SplitEnumeratorContext<IcebergSourceSplit> enumeratorContext;
  private final SplitAssigner assigner;
  private final Map<Integer, String> readersAwaitingSplit;
  private final AtomicReference<CompletableFuture<Void>> availableFuture;

  /**
   * 构造方法。
   *
   * @param enumeratorContext Flink SplitEnumerator 上下文
   * @param assigner split 分配器
   */
  AbstractIcebergEnumerator(
      SplitEnumeratorContext<IcebergSourceSplit> enumeratorContext, SplitAssigner assigner) {
    this.enumeratorContext = enumeratorContext;
    this.assigner = assigner;
    this.readersAwaitingSplit = new LinkedHashMap<>();
    this.availableFuture = new AtomicReference<>();
  }

  @Override
  public void start() {
    assigner.start();
  }

  @Override
  public void close() throws IOException {
    assigner.close();
  }

  /**
   * 处理 Flink 默认的 split 请求（不支持，Iceberg 使用自定义事件）。
   *
   * <p>设计要点：Iceberg source 使用 {@link SplitRequestEvent} 自定义事件携带已完成 split id， 不走 Flink 默认的
   * handleSplitRequest 路径。
   */
  @Override
  public void handleSplitRequest(int subtaskId, @Nullable String requesterHostname) {
    // Iceberg source uses custom split request event to piggyback finished split ids.
    throw new UnsupportedOperationException(
        String.format(
            "Received invalid default split request event "
                + "from subtask %d as Iceberg source uses custom split request event",
            subtaskId));
  }

  @Override
  public void handleSourceEvent(int subtaskId, SourceEvent sourceEvent) {
    if (sourceEvent instanceof SplitRequestEvent) {
      SplitRequestEvent splitRequestEvent = (SplitRequestEvent) sourceEvent;
      LOG.info("Received request split event from subtask {}", subtaskId);
      assigner.onCompletedSplits(splitRequestEvent.finishedSplitIds());
      readersAwaitingSplit.put(subtaskId, splitRequestEvent.requesterHostname());
      assignSplits();
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Received unknown event from subtask %d: %s",
              subtaskId, sourceEvent.getClass().getCanonicalName()));
    }
  }

  @Override
  public void addSplitsBack(List<IcebergSourceSplit> splits, int subtaskId) {
    LOG.info("Add {} splits back to the pool for failed subtask {}", splits.size(), subtaskId);
    assigner.onUnassignedSplits(splits);
    assignSplits();
  }

  @Override
  public void addReader(int subtaskId) {
    LOG.info("Added reader: {}", subtaskId);
  }

  private void assignSplits() {
    LOG.info("Assigning splits for {} awaiting readers", readersAwaitingSplit.size());
    Iterator<Map.Entry<Integer, String>> awaitingReader =
        readersAwaitingSplit.entrySet().iterator();
    while (awaitingReader.hasNext()) {
      Map.Entry<Integer, String> nextAwaiting = awaitingReader.next();
      // if the reader that requested another split has failed in the meantime, remove
      // it from the list of waiting readers
      if (!enumeratorContext.registeredReaders().containsKey(nextAwaiting.getKey())) {
        awaitingReader.remove();
        continue;
      }

      int awaitingSubtask = nextAwaiting.getKey();
      String hostname = nextAwaiting.getValue();
      GetSplitResult getResult = assigner.getNext(hostname);
      if (getResult.status() == GetSplitResult.Status.AVAILABLE) {
        LOG.info("Assign split to subtask {}: {}", awaitingSubtask, getResult.split());
        enumeratorContext.assignSplit(getResult.split(), awaitingSubtask);
        awaitingReader.remove();
      } else if (getResult.status() == GetSplitResult.Status.CONSTRAINED) {
        getAvailableFutureIfNeeded();
        break;
      } else if (getResult.status() == GetSplitResult.Status.UNAVAILABLE) {
        if (shouldWaitForMoreSplits()) {
          getAvailableFutureIfNeeded();
          break;
        } else {
          LOG.info("No more splits available for subtask {}", awaitingSubtask);
          enumeratorContext.signalNoMoreSplits(awaitingSubtask);
          awaitingReader.remove();
        }
      } else {
        throw new IllegalArgumentException("Unsupported status: " + getResult.status());
      }
    }
  }

  /** return true if enumerator should wait for splits like in the continuous enumerator case */
  protected abstract boolean shouldWaitForMoreSplits();

  private synchronized void getAvailableFutureIfNeeded() {
    if (availableFuture.get() != null) {
      return;
    }

    CompletableFuture<Void> future =
        assigner
            .isAvailable()
            .thenAccept(
                ignore ->
                    // Must run assignSplits in coordinator thread
                    // because the future may be completed from other threads.
                    // E.g., in event time alignment assigner,
                    // watermark advancement from another source may
                    // cause the available future to be completed
                    enumeratorContext.runInCoordinatorThread(
                        () -> {
                          LOG.debug("Executing callback of assignSplits");
                          availableFuture.set(null);
                          assignSplits();
                        }));
    availableFuture.set(future);
    LOG.debug("Registered callback for future available splits");
  }
}
