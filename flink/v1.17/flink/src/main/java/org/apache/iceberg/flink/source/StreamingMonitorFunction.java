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
package org.apache.iceberg.flink.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeutils.base.LongSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.streaming.api.operators.StreamingRuntimeContext;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：流式 Iceberg source 的单并行度监控算子。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source 子包）。
 *
 * <p>职责：
 *
 * <ol>
 *   <li>周期性监控 Iceberg 表的 snapshot。
 *   <li>把新增的增量文件切分为 {@link FlinkInputSplit}。
 *   <li>把 split 分发给下游 {@code StreamingReaderOperator}（并行度可大于 1）。
 * </ol>
 *
 * <p>设计意图：单线程监控避免重复扫描，使用 {@link ListState} 持久化 lastSnapshotId 保证故障恢复后能从上次位置继续；通过 checkpoint lock 保证
 * emit 与状态更新原子。
 *
 * <p>上下游关系：上游为 Iceberg 表的 snapshot，下游为 {@code StreamingReaderOperator}。
 */
public class StreamingMonitorFunction extends RichSourceFunction<FlinkInputSplit>
    implements CheckpointedFunction {

  private static final Logger LOG = LoggerFactory.getLogger(StreamingMonitorFunction.class);

  private static final long INIT_LAST_SNAPSHOT_ID = -1L;

  private final TableLoader tableLoader;
  private final ScanContext scanContext;

  private volatile boolean isRunning = true;

  // SourceStreamTask 中 checkpoint 线程与运行线程不同，必须用 volatile 保证可见性。
  private volatile long lastSnapshotId = INIT_LAST_SNAPSHOT_ID;

  private transient SourceContext<FlinkInputSplit> sourceContext;
  private transient Table table;
  private transient ListState<Long> lastSnapshotIdState;
  private transient ExecutorService workerPool;

  /**
   * 构造监控算子，校验流式读取相关参数合法性。
   *
   * @param tableLoader 表加载器
   * @param scanContext 扫描上下文
   */
  public StreamingMonitorFunction(TableLoader tableLoader, ScanContext scanContext) {
    Preconditions.checkArgument(
        scanContext.snapshotId() == null, "Cannot set snapshot-id option for streaming reader");
    Preconditions.checkArgument(
        scanContext.asOfTimestamp() == null,
        "Cannot set as-of-timestamp option for streaming reader");
    Preconditions.checkArgument(
        scanContext.endSnapshotId() == null,
        "Cannot set end-snapshot-id option for streaming reader");
    Preconditions.checkArgument(
        scanContext.endTag() == null, "Cannot set end-tag option for streaming reader");
    Preconditions.checkArgument(
        scanContext.maxPlanningSnapshotCount() > 0,
        "The max-planning-snapshot-count must be greater than zero");
    this.tableLoader = tableLoader;
    this.scanContext = scanContext;
  }

  /**
   * 打开算子，创建工作线程池。
   *
   * <p>逻辑：要求 RuntimeContext 为 StreamingRuntimeContext， 用算子 ID 命名工作池，并发度由
   * scanContext.planParallelism 决定。
   */
  @Override
  public void open(Configuration parameters) throws Exception {
    super.open(parameters);

    final RuntimeContext runtimeContext = getRuntimeContext();
    ValidationException.check(
        runtimeContext instanceof StreamingRuntimeContext,
        "context should be instance of StreamingRuntimeContext");
    final String operatorID = ((StreamingRuntimeContext) runtimeContext).getOperatorUniqueID();
    this.workerPool =
        ThreadPools.newWorkerPool(
            "iceberg-worker-pool-" + operatorID, scanContext.planParallelism());
  }

  /**
   * 初始化状态，加载表与 lastSnapshotId。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>打开 tableLoader 并加载表。
   *   <li>从 Flink 状态恢复 lastSnapshotId（如果是从 checkpoint 恢复）。
   *   <li>若未恢复且配置了 startTag/startSnapshotId，校验并设置为起始 snapshot。
   * </ol>
   */
  @Override
  public void initializeState(FunctionInitializationContext context) throws Exception {
    // 通过 tableLoader 加载 Iceberg 表
    tableLoader.open();
    table = tableLoader.loadTable();

    // 初始化 lastSnapshotId 的 Flink 状态
    lastSnapshotIdState =
        context
            .getOperatorStateStore()
            .getListState(new ListStateDescriptor<>("snapshot-id-state", LongSerializer.INSTANCE));

    // 若从 checkpoint 恢复，则恢复 lastSnapshotId
    if (context.isRestored()) {
      LOG.info("Restoring state for the {}.", getClass().getSimpleName());
      lastSnapshotId = lastSnapshotIdState.get().iterator().next();
    } else if (scanContext.startTag() != null || scanContext.startSnapshotId() != null) {
      Preconditions.checkArgument(
          !(scanContext.startTag() != null && scanContext.startSnapshotId() != null),
          "START_SNAPSHOT_ID and START_TAG cannot both be set.");
      Preconditions.checkArgument(
          scanContext.branch() == null,
          "Cannot scan table using ref %s configured for streaming reader yet.");
      Preconditions.checkNotNull(
          table.currentSnapshot(), "Don't have any available snapshot in table.");

      long startSnapshotId;
      if (scanContext.startTag() != null) {
        Preconditions.checkArgument(
            table.snapshot(scanContext.startTag()) != null,
            "Cannot find snapshot with tag %s in table.",
            scanContext.startTag());
        startSnapshotId = table.snapshot(scanContext.startTag()).snapshotId();
      } else {
        startSnapshotId = scanContext.startSnapshotId();
      }

      long currentSnapshotId = table.currentSnapshot().snapshotId();
      Preconditions.checkState(
          SnapshotUtil.isAncestorOf(table, currentSnapshotId, startSnapshotId),
          "The option start-snapshot-id %s is not an ancestor of the current snapshot.",
          startSnapshotId);

      lastSnapshotId = startSnapshotId;
    }
  }

  /**
   * 把 lastSnapshotId 持久化到 Flink 状态。
   *
   * <p>逻辑：清空旧值后写入当前 lastSnapshotId。
   */
  @Override
  public void snapshotState(FunctionSnapshotContext context) throws Exception {
    lastSnapshotIdState.clear();
    lastSnapshotIdState.add(lastSnapshotId);
  }

  /**
   * 运行监控循环。
   *
   * <p>逻辑：循环调用 {@link #monitorAndForwardSplits}，按 monitorInterval 间隔休眠。
   */
  @Override
  public void run(SourceContext<FlinkInputSplit> ctx) throws Exception {
    this.sourceContext = ctx;
    while (isRunning) {
      monitorAndForwardSplits();
      Thread.sleep(scanContext.monitorInterval().toMillis());
    }
  }

  /**
   * 计算本次规划应消费到的 snapshot ID（包含）。
   *
   * <p>逻辑：若待消费 snapshot 数量不超过 maxPlanningSnapshotCount，返回当前 snapshot； 否则按提交时间降序取第 N 个，截断到
   * maxPlanningSnapshotCount 个。
   *
   * @param lastConsumedSnapshotId 上次消费的 snapshot ID（不含）
   * @param currentSnapshotId 当前 snapshot ID
   * @param maxPlanningSnapshotCount 单次规划最大 snapshot 数
   * @return 本次应消费到的 snapshot ID（包含）
   */
  private long toSnapshotIdInclusive(
      long lastConsumedSnapshotId, long currentSnapshotId, int maxPlanningSnapshotCount) {
    List<Long> snapshotIds =
        SnapshotUtil.snapshotIdsBetween(table, lastConsumedSnapshotId, currentSnapshotId);
    if (snapshotIds.size() <= maxPlanningSnapshotCount) {
      return currentSnapshotId;
    } else {
      // snapshotIdsBetween 返回按提交时间降序的 ID，故用倒序索引。
      return snapshotIds.get(snapshotIds.size() - maxPlanningSnapshotCount);
    }
  }

  /** 测试用：注入 SourceContext。 */
  @VisibleForTesting
  void sourceContext(SourceContext<FlinkInputSplit> ctx) {
    this.sourceContext = ctx;
  }

  /**
   * 监控表的新 snapshot 并把对应的 split 转发给下游。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>刷新表，取最新 snapshot。
   *   <li>若 snapshot 与 lastSnapshotId 不同，按增量方式构建 ScanContext。
   *   <li>调用 FlinkSplitPlanner 规划 split，加 checkpoint 锁 emit 并更新 lastSnapshotId。
   * </ol>
   */
  @VisibleForTesting
  void monitorAndForwardSplits() {
    // 刷新表以获取最新已提交的 snapshot
    table.refresh();

    Snapshot snapshot = table.currentSnapshot();
    if (snapshot != null && snapshot.snapshotId() != lastSnapshotId) {
      long snapshotId = snapshot.snapshotId();

      ScanContext newScanContext;
      if (lastSnapshotId == INIT_LAST_SNAPSHOT_ID) {
        newScanContext = scanContext.copyWithSnapshotId(snapshotId);
      } else {
        snapshotId =
            toSnapshotIdInclusive(
                lastSnapshotId, snapshotId, scanContext.maxPlanningSnapshotCount());
        newScanContext = scanContext.copyWithAppendsBetween(lastSnapshotId, snapshotId);
      }

      LOG.debug(
          "Start discovering splits from {} (exclusive) to {} (inclusive)",
          lastSnapshotId,
          snapshotId);
      long start = System.currentTimeMillis();
      FlinkInputSplit[] splits =
          FlinkSplitPlanner.planInputSplits(table, newScanContext, workerPool);
      LOG.debug(
          "Discovered {} splits, time elapsed {}ms",
          splits.length,
          System.currentTimeMillis() - start);

      // 仅在 emit split 与更新 lastSnapshotId 时持有 checkpoint 锁
      start = System.currentTimeMillis();
      synchronized (sourceContext.getCheckpointLock()) {
        for (FlinkInputSplit split : splits) {
          sourceContext.collect(split);
        }

        lastSnapshotId = snapshotId;
      }
      LOG.debug(
          "Forwarded {} splits, time elapsed {}ms",
          splits.length,
          System.currentTimeMillis() - start);
    }
  }

  /** 取消监控循环并释放 tableLoader 资源。 */
  @Override
  public void cancel() {
    // 处理 cancel() 在 run() 之前被调用的情况
    if (sourceContext != null) {
      synchronized (sourceContext.getCheckpointLock()) {
        isRunning = false;
      }
    } else {
      isRunning = false;
    }

    // 释放资源
    if (tableLoader != null) {
      try {
        tableLoader.close();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /** 关闭算子，先取消监控再关闭工作线程池。 */
  @Override
  public void close() {
    cancel();

    if (workerPool != null) {
      workerPool.shutdown();
    }
  }
}
