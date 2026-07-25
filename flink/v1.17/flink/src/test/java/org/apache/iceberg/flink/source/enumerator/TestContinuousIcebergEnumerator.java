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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.flink.connector.testutils.source.reader.TestingSplitEnumeratorContext;
import org.apache.iceberg.flink.source.ScanContext;
import org.apache.iceberg.flink.source.SplitHelpers;
import org.apache.iceberg.flink.source.StreamingStartingStrategy;
import org.apache.iceberg.flink.source.assigner.DefaultSplitAssigner;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitStatus;
import org.apache.iceberg.flink.source.split.SplitRequestEvent;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestContinuousIcebergEnumerator 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestContinuousIcebergEnumerator 在各类场景下的行为是否符合预期，
 * 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestContinuousIcebergEnumerator {
  @ClassRule public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  /**
   * 测试场景：Discover Split When No Reader Registered。
   *
   * <p>验证该方法在 Discover Split When No Reader Registered 条件下的行为是否符合预期。
   */
  @Test
  public void testDiscoverSplitWhenNoReaderRegistered() throws Exception {
    TestingSplitEnumeratorContext<IcebergSourceSplit> enumeratorContext =
        new TestingSplitEnumeratorContext<>(4);
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.TABLE_SCAN_THEN_INCREMENTAL)
            .build();
    ManualContinuousSplitPlanner splitPlanner = new ManualContinuousSplitPlanner(scanContext, 0);
    ContinuousIcebergEnumerator enumerator =
        createEnumerator(enumeratorContext, scanContext, splitPlanner);

    Collection<IcebergSourceSplitState> pendingSplitsEmpty =
        enumerator.snapshotState(1).pendingSplits();
    Assert.assertEquals(0, pendingSplitsEmpty.size());

    // make one split available and trigger the periodic discovery
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 1, 1);
    splitPlanner.addSplits(splits);
    enumeratorContext.triggerAllActions();

    Collection<IcebergSourceSplitState> pendingSplits = enumerator.snapshotState(2).pendingSplits();
    Assert.assertEquals(1, pendingSplits.size());
    IcebergSourceSplitState pendingSplit = pendingSplits.iterator().next();
    Assert.assertEquals(splits.get(0).splitId(), pendingSplit.split().splitId());
    Assert.assertEquals(IcebergSourceSplitStatus.UNASSIGNED, pendingSplit.status());
  }

  /**
   * 测试场景：Discover When Reader Registered。
   *
   * <p>验证该方法在 Discover When Reader Registered 条件下的行为是否符合预期。
   */
  @Test
  public void testDiscoverWhenReaderRegistered() throws Exception {
    TestingSplitEnumeratorContext<IcebergSourceSplit> enumeratorContext =
        new TestingSplitEnumeratorContext<>(4);
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.TABLE_SCAN_THEN_INCREMENTAL)
            .build();
    ManualContinuousSplitPlanner splitPlanner = new ManualContinuousSplitPlanner(scanContext, 0);
    ContinuousIcebergEnumerator enumerator =
        createEnumerator(enumeratorContext, scanContext, splitPlanner);

    // register one reader, and let it request a split
    enumeratorContext.registerReader(2, "localhost");
    enumerator.addReader(2);
    enumerator.handleSourceEvent(2, new SplitRequestEvent());

    // make one split available and trigger the periodic discovery
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 1, 1);
    splitPlanner.addSplits(splits);
    enumeratorContext.triggerAllActions();

    Assert.assertTrue(enumerator.snapshotState(1).pendingSplits().isEmpty());
    Assertions.assertThat(enumeratorContext.getSplitAssignments().get(2).getAssignedSplits())
        .contains(splits.get(0));
  }

  /**
   * 测试场景：Requesting Reader Unavailable When Split Discovered。
   *
   * <p>验证该方法在 Requesting Reader Unavailable When Split Discovered 条件下的行为是否符合预期。
   */
  @Test
  public void testRequestingReaderUnavailableWhenSplitDiscovered() throws Exception {
    TestingSplitEnumeratorContext<IcebergSourceSplit> enumeratorContext =
        new TestingSplitEnumeratorContext<>(4);
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.TABLE_SCAN_THEN_INCREMENTAL)
            .build();
    ManualContinuousSplitPlanner splitPlanner = new ManualContinuousSplitPlanner(scanContext, 0);
    ContinuousIcebergEnumerator enumerator =
        createEnumerator(enumeratorContext, scanContext, splitPlanner);

    // register one reader, and let it request a split
    enumeratorContext.registerReader(2, "localhost");
    enumerator.addReader(2);
    enumerator.handleSourceEvent(2, new SplitRequestEvent());

    // remove the reader (like in a failure)
    enumeratorContext.registeredReaders().remove(2);

    // make one split available and trigger the periodic discovery
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 1, 1);
    Assert.assertEquals(1, splits.size());
    splitPlanner.addSplits(splits);
    enumeratorContext.triggerAllActions();

    Assert.assertFalse(enumeratorContext.getSplitAssignments().containsKey(2));
    List<String> pendingSplitIds =
        enumerator.snapshotState(1).pendingSplits().stream()
            .map(IcebergSourceSplitState::split)
            .map(IcebergSourceSplit::splitId)
            .collect(Collectors.toList());
    Assert.assertEquals(splits.size(), pendingSplitIds.size());
    Assert.assertEquals(splits.get(0).splitId(), pendingSplitIds.get(0));

    // register the reader again, and let it request a split
    enumeratorContext.registerReader(2, "localhost");
    enumerator.addReader(2);
    enumerator.handleSourceEvent(2, new SplitRequestEvent());

    Assert.assertTrue(enumerator.snapshotState(2).pendingSplits().isEmpty());
    Assertions.assertThat(enumeratorContext.getSplitAssignments().get(2).getAssignedSplits())
        .contains(splits.get(0));
  }

  /**
   * 测试场景：Throttling Discovery。
   *
   * <p>验证该方法在 Throttling Discovery 条件下的行为是否符合预期。
   */
  @Test
  public void testThrottlingDiscovery() throws Exception {
    // create 10 splits
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 10, 1);

    TestingSplitEnumeratorContext<IcebergSourceSplit> enumeratorContext =
        new TestingSplitEnumeratorContext<>(4);
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_EARLIEST_SNAPSHOT)
            // discover one snapshot at a time
            .maxPlanningSnapshotCount(1)
            .build();
    ManualContinuousSplitPlanner splitPlanner = new ManualContinuousSplitPlanner(scanContext, 0);
    ContinuousIcebergEnumerator enumerator =
        createEnumerator(enumeratorContext, scanContext, splitPlanner);

    // register reader-2, and let it request a split
    enumeratorContext.registerReader(2, "localhost");
    enumerator.addReader(2);
    enumerator.handleSourceEvent(2, new SplitRequestEvent());

    // add splits[0] to the planner for next discovery
    splitPlanner.addSplits(Arrays.asList(splits.get(0)));
    enumeratorContext.triggerAllActions();

    // because discovered split was assigned to reader, pending splits should be empty
    Assert.assertEquals(0, enumerator.snapshotState(1).pendingSplits().size());
    // split assignment to reader-2 should contain splits[0, 1)
    Assert.assertEquals(
        splits.subList(0, 1), enumeratorContext.getSplitAssignments().get(2).getAssignedSplits());

    // add the remaining 9 splits (one for every snapshot)
    // run discovery cycles while reader-2 still processing the splits[0]
    for (int i = 1; i < 10; ++i) {
      splitPlanner.addSplits(Arrays.asList(splits.get(i)));
      enumeratorContext.triggerAllActions();
    }

    // can only discover up to 3 snapshots/splits
    Assert.assertEquals(3, enumerator.snapshotState(2).pendingSplits().size());
    // split assignment to reader-2 should be splits[0, 1)
    Assert.assertEquals(
        splits.subList(0, 1), enumeratorContext.getSplitAssignments().get(2).getAssignedSplits());

    // now reader-2 finished splits[0]
    enumerator.handleSourceEvent(2, new SplitRequestEvent(Arrays.asList(splits.get(0).splitId())));
    enumeratorContext.triggerAllActions();
    // still have 3 pending splits. After assigned splits[1] to reader-2, one more split was
    // discovered and added.
    Assert.assertEquals(3, enumerator.snapshotState(3).pendingSplits().size());
    // split assignment to reader-2 should be splits[0, 2)
    Assert.assertEquals(
        splits.subList(0, 2), enumeratorContext.getSplitAssignments().get(2).getAssignedSplits());

    // run 3 more split discovery cycles
    for (int i = 0; i < 3; ++i) {
      enumeratorContext.triggerAllActions();
    }

    // no more splits are discovered due to throttling
    Assert.assertEquals(3, enumerator.snapshotState(4).pendingSplits().size());
    // split assignment to reader-2 should still be splits[0, 2)
    Assert.assertEquals(
        splits.subList(0, 2), enumeratorContext.getSplitAssignments().get(2).getAssignedSplits());

    // now reader-2 finished splits[1]
    enumerator.handleSourceEvent(2, new SplitRequestEvent(Arrays.asList(splits.get(1).splitId())));
    enumeratorContext.triggerAllActions();
    // still have 3 pending splits. After assigned new splits[2] to reader-2, one more split was
    // discovered and added.
    Assert.assertEquals(3, enumerator.snapshotState(5).pendingSplits().size());
    // split assignment to reader-2 should be splits[0, 3)
    Assert.assertEquals(
        splits.subList(0, 3), enumeratorContext.getSplitAssignments().get(2).getAssignedSplits());
  }

  /**
   * 测试场景：Transient Planning Errors With Successful Retry。
   *
   * <p>验证该方法在 Transient Planning Errors With Successful Retry 条件下的行为是否符合预期。
   */
  @Test
  public void testTransientPlanningErrorsWithSuccessfulRetry() throws Exception {
    TestingSplitEnumeratorContext<IcebergSourceSplit> enumeratorContext =
        new TestingSplitEnumeratorContext<>(4);
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_EARLIEST_SNAPSHOT)
            .maxPlanningSnapshotCount(1)
            .maxAllowedPlanningFailures(2)
            .build();
    ManualContinuousSplitPlanner splitPlanner = new ManualContinuousSplitPlanner(scanContext, 1);
    ContinuousIcebergEnumerator enumerator =
        createEnumerator(enumeratorContext, scanContext, splitPlanner);

    // Make one split available and trigger the periodic discovery
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 1, 1);
    splitPlanner.addSplits(splits);

    // Trigger a planning and check that no splits returned due to the planning error
    enumeratorContext.triggerAllActions();
    Assert.assertEquals(0, enumerator.snapshotState(2).pendingSplits().size());

    // Second scan planning should succeed and discover the expected splits
    enumeratorContext.triggerAllActions();
    Collection<IcebergSourceSplitState> pendingSplits = enumerator.snapshotState(3).pendingSplits();
    Assert.assertEquals(1, pendingSplits.size());
    IcebergSourceSplitState pendingSplit = pendingSplits.iterator().next();
    Assert.assertEquals(splits.get(0).splitId(), pendingSplit.split().splitId());
    Assert.assertEquals(IcebergSourceSplitStatus.UNASSIGNED, pendingSplit.status());
  }

  /**
   * 测试场景：Over Max Allowed Planning Errors。
   *
   * <p>验证该方法在 Over Max Allowed Planning Errors 条件下的行为是否符合预期。
   */
  @Test
  public void testOverMaxAllowedPlanningErrors() throws Exception {
    TestingSplitEnumeratorContext<IcebergSourceSplit> enumeratorContext =
        new TestingSplitEnumeratorContext<>(4);
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_EARLIEST_SNAPSHOT)
            .maxPlanningSnapshotCount(1)
            .maxAllowedPlanningFailures(1)
            .build();
    ManualContinuousSplitPlanner splitPlanner = new ManualContinuousSplitPlanner(scanContext, 2);
    createEnumerator(enumeratorContext, scanContext, splitPlanner);

    // Make one split available and trigger the periodic discovery
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 1, 1);
    splitPlanner.addSplits(splits);

    // Check that the scheduler response ignores the current error and continues to run until the
    // failure limit is reached
    enumeratorContext.triggerAllActions();
    Assert.assertFalse(
        enumeratorContext.getExecutorService().getAllScheduledTasks().get(0).isDone());

    // Check that the task has failed with the expected exception after the failure limit is reached
    enumeratorContext.triggerAllActions();
    Assert.assertTrue(
        enumeratorContext.getExecutorService().getAllScheduledTasks().get(0).isDone());
    Assertions.assertThatThrownBy(
            () -> enumeratorContext.getExecutorService().getAllScheduledTasks().get(0).get())
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to discover new split");
  }

  /**
   * 测试场景：Planning Ignoring Errors。
   *
   * <p>验证该方法在 Planning Ignoring Errors 条件下的行为是否符合预期。
   */
  @Test
  public void testPlanningIgnoringErrors() throws Exception {
    int expectedFailures = 3;
    TestingSplitEnumeratorContext<IcebergSourceSplit> enumeratorContext =
        new TestingSplitEnumeratorContext<>(4);
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_EARLIEST_SNAPSHOT)
            .maxPlanningSnapshotCount(1)
            .maxAllowedPlanningFailures(-1)
            .build();
    ManualContinuousSplitPlanner splitPlanner =
        new ManualContinuousSplitPlanner(scanContext, expectedFailures);
    ContinuousIcebergEnumerator enumerator =
        createEnumerator(enumeratorContext, scanContext, splitPlanner);

    // Make one split available and trigger the periodic discovery
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 1, 1);
    splitPlanner.addSplits(splits);

    Collection<IcebergSourceSplitState> pendingSplits;
    // Can not discover the new split with planning failures
    for (int i = 0; i < expectedFailures; ++i) {
      enumeratorContext.triggerAllActions();
      pendingSplits = enumerator.snapshotState(i).pendingSplits();
      Assert.assertEquals(0, pendingSplits.size());
    }

    // Discovered the new split after a successful scan planning
    enumeratorContext.triggerAllActions();
    pendingSplits = enumerator.snapshotState(expectedFailures + 1).pendingSplits();
    Assert.assertEquals(1, pendingSplits.size());
    IcebergSourceSplitState pendingSplit = pendingSplits.iterator().next();
    Assert.assertEquals(splits.get(0).splitId(), pendingSplit.split().splitId());
    Assert.assertEquals(IcebergSourceSplitStatus.UNASSIGNED, pendingSplit.status());
  }

  /** 辅助方法：createEnumerator，create Enumerator。 */
  private static ContinuousIcebergEnumerator createEnumerator(
      SplitEnumeratorContext<IcebergSourceSplit> context,
      ScanContext scanContext,
      ContinuousSplitPlanner splitPlanner) {

    ContinuousIcebergEnumerator enumerator =
        new ContinuousIcebergEnumerator(
            context,
            new DefaultSplitAssigner(null, Collections.emptyList()),
            scanContext,
            splitPlanner,
            null);
    enumerator.start();
    return enumerator;
  }
}
