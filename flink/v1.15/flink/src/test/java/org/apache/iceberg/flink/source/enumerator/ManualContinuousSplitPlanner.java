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
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.apache.iceberg.flink.source.ScanContext;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：测试 ManualContinuousSplitPlanner 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 ManualContinuousSplitPlanner 在各类场景下的行为是否符合预期，
 * 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
class ManualContinuousSplitPlanner implements ContinuousSplitPlanner {
  private final int maxPlanningSnapshotCount;
  // track splits per snapshot
  private final NavigableMap<Long, List<IcebergSourceSplit>> splits;
  private long latestSnapshotId;
  private int remainingFailures;

  ManualContinuousSplitPlanner(ScanContext scanContext, int expectedFailures) {
    this.maxPlanningSnapshotCount = scanContext.maxPlanningSnapshotCount();
    this.splits = new TreeMap<>();
    this.latestSnapshotId = 0L;
    this.remainingFailures = expectedFailures;
  }

  /** 辅助方法：planSplits，plan Splits。 */
  @Override
  public synchronized ContinuousEnumerationResult planSplits(
      IcebergEnumeratorPosition lastPosition) {
    if (remainingFailures > 0) {
      remainingFailures--;
      throw new RuntimeException("Expected failure at planning");
    }

    long fromSnapshotIdExclusive = 0;
    if (lastPosition != null && lastPosition.snapshotId() != null) {
      fromSnapshotIdExclusive = lastPosition.snapshotId();
    }

    Preconditions.checkArgument(
        fromSnapshotIdExclusive <= latestSnapshotId,
        "last enumerated snapshotId is greater than the latestSnapshotId");
    if (fromSnapshotIdExclusive == latestSnapshotId) {
      // already discovered everything.
      return new ContinuousEnumerationResult(Lists.newArrayList(), lastPosition, lastPosition);
    }

    // find the subset of snapshots to return discovered splits
    long toSnapshotIdInclusive;
    if (latestSnapshotId - fromSnapshotIdExclusive > maxPlanningSnapshotCount) {
      toSnapshotIdInclusive = fromSnapshotIdExclusive + maxPlanningSnapshotCount;
    } else {
      toSnapshotIdInclusive = latestSnapshotId;
    }

    List<IcebergSourceSplit> discoveredSplits = Lists.newArrayList();
    NavigableMap<Long, List<IcebergSourceSplit>> discoveredView =
        splits.subMap(fromSnapshotIdExclusive, false, toSnapshotIdInclusive, true);
    discoveredView.forEach((snapshotId, snapshotSplits) -> discoveredSplits.addAll(snapshotSplits));
    ContinuousEnumerationResult result =
        new ContinuousEnumerationResult(
            discoveredSplits,
            lastPosition,
            // use the snapshot Id as snapshot timestamp.
            IcebergEnumeratorPosition.of(toSnapshotIdInclusive, toSnapshotIdInclusive));
    return result;
  }

  /**
   * Add a collection of new splits. A monotonically increased snapshotId is assigned to each batch
   * of splits added by this method.
   */
  public synchronized void addSplits(List<IcebergSourceSplit> newSplits) {
    latestSnapshotId += 1;
    splits.put(latestSnapshotId, newSplits);
  }

  /** 辅助方法：close，close。 */
  @Override
  public void close() throws IOException {}
}
