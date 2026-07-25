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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.source.FlinkSplitPlanner;
import org.apache.iceberg.flink.source.ScanContext;
import org.apache.iceberg.flink.source.StreamingStartingStrategy;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 连续模式下 split 规划器实现，负责初始与增量 split 发现。
 *
 * <p>所属模块：iceberg-flink（source enumerator 侧），实现 {@link ContinuousSplitPlanner}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>首次规划：按 {@link StreamingStartingStrategy} 确定起始快照并发现初始 split。
 *   <li>增量规划：从上次消费位置到当前快照，发现新增 append 的 split。
 *   <li>受 maxPlanningSnapshotCount 限制单次规划的快照数量，避免单次规划过多。
 * </ul>
 *
 * <p>设计意图：每次规划前 refresh 表以感知新快照；线程池可独享或共享（threadName 为 null 时用共享池）。
 *
 * <p>上下游关系：被 {@link ContinuousIcebergEnumerator} 调用；上游依赖 {@link FlinkSplitPlanner} 与 {@link
 * SnapshotUtil}。
 */
@Internal
public class ContinuousSplitPlannerImpl implements ContinuousSplitPlanner {
  private static final Logger LOG = LoggerFactory.getLogger(ContinuousSplitPlannerImpl.class);

  private final Table table;
  private final ScanContext scanContext;
  private final boolean isSharedPool;
  private final ExecutorService workerPool;
  private final TableLoader tableLoader;

  /**
   * 构造规划器。
   *
   * @param tableLoader 克隆的表加载器
   * @param scanContext 扫描上下文
   * @param threadName 规划线程池名前缀；为 null 时使用共享线程池
   */
  public ContinuousSplitPlannerImpl(
      TableLoader tableLoader, ScanContext scanContext, String threadName) {
    this.tableLoader = tableLoader;
    this.tableLoader.open();
    this.table = tableLoader.loadTable();
    this.scanContext = scanContext;
    this.isSharedPool = threadName == null;
    this.workerPool =
        isSharedPool
            ? ThreadPools.getWorkerPool()
            : ThreadPools.newWorkerPool(
                "iceberg-plan-worker-pool-" + threadName, scanContext.planParallelism());
  }

  /** 关闭独享线程池与表加载器（共享池不关闭）。 */
  @Override
  public void close() throws IOException {
    if (!isSharedPool) {
      workerPool.shutdown();
    }
    tableLoader.close();
  }

  /**
   * 规划 split：刷新表后，依据是否有 lastPosition 走增量或初始发现。
   *
   * @param lastPosition 上次消费位置（首次为 null）
   * @return 枚举结果
   */
  @Override
  public ContinuousEnumerationResult planSplits(IcebergEnumeratorPosition lastPosition) {
    table.refresh();
    if (lastPosition != null) {
      return discoverIncrementalSplits(lastPosition);
    } else {
      return discoverInitialSplits();
    }
  }

  /**
   * 在 lastConsumedSnapshotId 到 currentSnapshot 之间，按 maxPlanningSnapshotCount 截取本次规划的上界快照。
   *
   * <p>逻辑：快照按提交历史逆序排列，超出数量上限时取较早的快照作为本次上界。
   */
  private Snapshot toSnapshotInclusive(
      Long lastConsumedSnapshotId, Snapshot currentSnapshot, int maxPlanningSnapshotCount) {
    // snapshots are in reverse order (latest snapshot first)
    List<Snapshot> snapshots =
        Lists.newArrayList(
            SnapshotUtil.ancestorsBetween(
                table, currentSnapshot.snapshotId(), lastConsumedSnapshotId));
    if (snapshots.size() <= maxPlanningSnapshotCount) {
      return currentSnapshot;
    } else {
      // Because snapshots are in reverse order of commit history, this index returns
      // the max allowed number of snapshots from the lastConsumedSnapshotId.
      return snapshots.get(snapshots.size() - maxPlanningSnapshotCount);
    }
  }

  /**
   * 增量发现 split。
   *
   * <p>逻辑：表为空或当前快照已枚举时返回空结果；否则按 toSnapshotInclusive 截取上界， 用 appendsBetween 扫描新增文件并规划 split，返回新位置。
   */
  private ContinuousEnumerationResult discoverIncrementalSplits(
      IcebergEnumeratorPosition lastPosition) {
    Snapshot currentSnapshot = table.currentSnapshot();
    if (currentSnapshot == null) {
      // empty table
      Preconditions.checkArgument(
          lastPosition.snapshotId() == null,
          "Invalid last enumerated position for an empty table: not null");
      LOG.info("Skip incremental scan because table is empty");
      return new ContinuousEnumerationResult(Collections.emptyList(), lastPosition, lastPosition);
    } else if (lastPosition.snapshotId() != null
        && currentSnapshot.snapshotId() == lastPosition.snapshotId()) {
      LOG.info("Current table snapshot is already enumerated: {}", currentSnapshot.snapshotId());
      return new ContinuousEnumerationResult(Collections.emptyList(), lastPosition, lastPosition);
    } else {
      Long lastConsumedSnapshotId = lastPosition != null ? lastPosition.snapshotId() : null;
      Snapshot toSnapshotInclusive =
          toSnapshotInclusive(
              lastConsumedSnapshotId, currentSnapshot, scanContext.maxPlanningSnapshotCount());
      IcebergEnumeratorPosition newPosition =
          IcebergEnumeratorPosition.of(
              toSnapshotInclusive.snapshotId(), toSnapshotInclusive.timestampMillis());
      ScanContext incrementalScan =
          scanContext.copyWithAppendsBetween(
              lastPosition.snapshotId(), toSnapshotInclusive.snapshotId());
      List<IcebergSourceSplit> splits =
          FlinkSplitPlanner.planIcebergSourceSplits(table, incrementalScan, workerPool);
      LOG.info(
          "Discovered {} splits from incremental scan: "
              + "from snapshot (exclusive) is {}, to snapshot (inclusive) is {}",
          splits.size(),
          lastPosition,
          newPosition);
      return new ContinuousEnumerationResult(splits, lastPosition, newPosition);
    }
  }

  /**
   * 根据 {@link StreamingStartingStrategy} 发现初始 split。
   *
   * <p>逻辑：TABLE_SCAN_THEN_INCREMENTAL 先做批量扫描产出初始 split，后续增量从起始快照排他消费； 其余策略不产出初始 split，通过父快照 id
   * 实现起始快照的包含语义。
   */
  private ContinuousEnumerationResult discoverInitialSplits() {
    Optional<Snapshot> startSnapshotOptional = startSnapshot(table, scanContext);
    if (!startSnapshotOptional.isPresent()) {
      return new ContinuousEnumerationResult(
          Collections.emptyList(), null, IcebergEnumeratorPosition.empty());
    }

    Snapshot startSnapshot = startSnapshotOptional.get();
    LOG.info(
        "Get starting snapshot id {} based on strategy {}",
        startSnapshot.snapshotId(),
        scanContext.streamingStartingStrategy());
    List<IcebergSourceSplit> splits;
    IcebergEnumeratorPosition toPosition;
    if (scanContext.streamingStartingStrategy()
        == StreamingStartingStrategy.TABLE_SCAN_THEN_INCREMENTAL) {
      // do a batch table scan first
      splits =
          FlinkSplitPlanner.planIcebergSourceSplits(
              table, scanContext.copyWithSnapshotId(startSnapshot.snapshotId()), workerPool);
      LOG.info(
          "Discovered {} splits from initial batch table scan with snapshot Id {}",
          splits.size(),
          startSnapshot.snapshotId());
      // For TABLE_SCAN_THEN_INCREMENTAL, incremental mode starts exclusive from the startSnapshot
      toPosition =
          IcebergEnumeratorPosition.of(startSnapshot.snapshotId(), startSnapshot.timestampMillis());
    } else {
      // For all other modes, starting snapshot should be consumed inclusively.
      // Use parentId to achieve the inclusive behavior. It is fine if parentId is null.
      splits = Collections.emptyList();
      Long parentSnapshotId = startSnapshot.parentId();
      if (parentSnapshotId != null) {
        Snapshot parentSnapshot = table.snapshot(parentSnapshotId);
        Long parentSnapshotTimestampMs =
            parentSnapshot != null ? parentSnapshot.timestampMillis() : null;
        toPosition = IcebergEnumeratorPosition.of(parentSnapshotId, parentSnapshotTimestampMs);
      } else {
        toPosition = IcebergEnumeratorPosition.empty();
      }

      LOG.info(
          "Start incremental scan with start snapshot (inclusive): id = {}, timestamp = {}",
          startSnapshot.snapshotId(),
          startSnapshot.timestampMillis());
    }

    return new ContinuousEnumerationResult(splits, null, toPosition);
  }

  /**
   * 根据策略计算起始快照。
   *
   * <p>逻辑：按 {@link StreamingStartingStrategy} 分发——最新/最早快照、按 id 或按时间戳定位； 非
   * TABLE_SCAN_THEN_INCREMENTAL 时起始快照按包含语义消费。
   */
  @VisibleForTesting
  static Optional<Snapshot> startSnapshot(Table table, ScanContext scanContext) {
    switch (scanContext.streamingStartingStrategy()) {
      case TABLE_SCAN_THEN_INCREMENTAL:
      case INCREMENTAL_FROM_LATEST_SNAPSHOT:
        return Optional.ofNullable(table.currentSnapshot());
      case INCREMENTAL_FROM_EARLIEST_SNAPSHOT:
        return Optional.ofNullable(SnapshotUtil.oldestAncestor(table));
      case INCREMENTAL_FROM_SNAPSHOT_ID:
        Snapshot matchedSnapshotById = table.snapshot(scanContext.startSnapshotId());
        Preconditions.checkArgument(
            matchedSnapshotById != null,
            "Start snapshot id not found in history: " + scanContext.startSnapshotId());
        return Optional.of(matchedSnapshotById);
      case INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP:
        Snapshot matchedSnapshotByTimestamp =
            SnapshotUtil.oldestAncestorAfter(table, scanContext.startSnapshotTimestamp());
        Preconditions.checkArgument(
            matchedSnapshotByTimestamp != null,
            "Cannot find a snapshot after: " + scanContext.startSnapshotTimestamp());
        return Optional.of(matchedSnapshotByTimestamp);
      default:
        throw new IllegalArgumentException(
            "Unknown starting strategy: " + scanContext.streamingStartingStrategy());
    }
  }
}
