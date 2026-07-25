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
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.data.GenericAppenderHelper;
import org.apache.iceberg.data.RandomGenericData;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.flink.HadoopTableResource;
import org.apache.iceberg.flink.TestFixtures;
import org.apache.iceberg.flink.source.ScanContext;
import org.apache.iceberg.flink.source.StreamingStartingStrategy;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

/**
 * 文件级说明：测试 TestContinuousSplitPlannerImplStartStrategy 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestContinuousSplitPlannerImplStartStrategy
 * 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestContinuousSplitPlannerImplStartStrategy {
  private static final FileFormat FILE_FORMAT = FileFormat.PARQUET;

  public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  public final HadoopTableResource tableResource =
      new HadoopTableResource(
          temporaryFolder, TestFixtures.DATABASE, TestFixtures.TABLE, TestFixtures.SCHEMA);
  @Rule public final TestRule chain = RuleChain.outerRule(temporaryFolder).around(tableResource);

  private GenericAppenderHelper dataAppender;
  private Snapshot snapshot1;
  private Snapshot snapshot2;
  private Snapshot snapshot3;

  /** 辅助方法：before，before。 */
  @Before
  public void before() throws IOException {
    dataAppender = new GenericAppenderHelper(tableResource.table(), FILE_FORMAT, temporaryFolder);
  }

  /** 辅助方法：appendThreeSnapshots，append Three Snapshots。 */
  private void appendThreeSnapshots() throws IOException {
    List<Record> batch1 = RandomGenericData.generate(TestFixtures.SCHEMA, 2, 0L);
    dataAppender.appendToTable(batch1);
    snapshot1 = tableResource.table().currentSnapshot();

    List<Record> batch2 = RandomGenericData.generate(TestFixtures.SCHEMA, 2, 1L);
    dataAppender.appendToTable(batch2);
    snapshot2 = tableResource.table().currentSnapshot();

    List<Record> batch3 = RandomGenericData.generate(TestFixtures.SCHEMA, 2, 2L);
    dataAppender.appendToTable(batch3);
    snapshot3 = tableResource.table().currentSnapshot();
  }

  /**
   * 测试场景：Table Scan Then Incremental Strategy。
   *
   * <p>验证该方法在 Table Scan Then Incremental Strategy 条件下的行为是否符合预期。
   */
  @Test
  public void testTableScanThenIncrementalStrategy() throws IOException {
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.TABLE_SCAN_THEN_INCREMENTAL)
            .build();

    // empty table
    Assert.assertFalse(
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).isPresent());

    appendThreeSnapshots();
    Snapshot startSnapshot =
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).get();
    Assert.assertEquals(snapshot3.snapshotId(), startSnapshot.snapshotId());
  }

  /**
   * 测试场景：For Latest Snapshot Strategy。
   *
   * <p>验证该方法在 For Latest Snapshot Strategy 条件下的行为是否符合预期。
   */
  @Test
  public void testForLatestSnapshotStrategy() throws IOException {
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_LATEST_SNAPSHOT)
            .build();

    // empty table
    Assert.assertFalse(
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).isPresent());

    appendThreeSnapshots();
    Snapshot startSnapshot =
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).get();
    Assert.assertEquals(snapshot3.snapshotId(), startSnapshot.snapshotId());
  }

  /**
   * 测试场景：For Earliest Snapshot Strategy。
   *
   * <p>验证该方法在 For Earliest Snapshot Strategy 条件下的行为是否符合预期。
   */
  @Test
  public void testForEarliestSnapshotStrategy() throws IOException {
    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_EARLIEST_SNAPSHOT)
            .build();

    // empty table
    Assert.assertFalse(
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).isPresent());

    appendThreeSnapshots();
    Snapshot startSnapshot =
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).get();
    Assert.assertEquals(snapshot1.snapshotId(), startSnapshot.snapshotId());
  }

  /**
   * 测试场景：For Specific Snapshot Id Strategy。
   *
   * <p>验证该方法在 For Specific Snapshot Id Strategy 条件下的行为是否符合预期。
   */
  @Test
  public void testForSpecificSnapshotIdStrategy() throws IOException {
    ScanContext scanContextInvalidSnapshotId =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_SNAPSHOT_ID)
            .startSnapshotId(1L)
            .build();

    // empty table
    Assertions.assertThatThrownBy(
            () ->
                ContinuousSplitPlannerImpl.startSnapshot(
                    tableResource.table(), scanContextInvalidSnapshotId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Start snapshot id not found in history: 1");

    appendThreeSnapshots();

    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_SNAPSHOT_ID)
            .startSnapshotId(snapshot2.snapshotId())
            .build();

    Snapshot startSnapshot =
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).get();
    Assert.assertEquals(snapshot2.snapshotId(), startSnapshot.snapshotId());
  }

  /**
   * 测试场景：For Specific Snapshot Timestamp Strategy Snapshot 2。
   *
   * <p>验证该方法在 For Specific Snapshot Timestamp Strategy Snapshot 2 条件下的行为是否符合预期。
   */
  @Test
  public void testForSpecificSnapshotTimestampStrategySnapshot2() throws IOException {
    ScanContext scanContextInvalidSnapshotTimestamp =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP)
            .startSnapshotTimestamp(1L)
            .build();

    // empty table
    Assertions.assertThatThrownBy(
            () ->
                ContinuousSplitPlannerImpl.startSnapshot(
                    tableResource.table(), scanContextInvalidSnapshotTimestamp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot find a snapshot after: ");

    appendThreeSnapshots();

    ScanContext scanContext =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP)
            .startSnapshotTimestamp(snapshot2.timestampMillis())
            .build();

    Snapshot startSnapshot =
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), scanContext).get();
    Assert.assertEquals(snapshot2.snapshotId(), startSnapshot.snapshotId());
  }

  /**
   * 测试场景：For Specific Snapshot Timestamp Strategy Snapshot 2 Minus 1。
   *
   * <p>验证该方法在 For Specific Snapshot Timestamp Strategy Snapshot 2 Minus 1 条件下的行为是否符合预期。
   */
  @Test
  public void testForSpecificSnapshotTimestampStrategySnapshot2Minus1() throws IOException {
    appendThreeSnapshots();

    ScanContext config =
        ScanContext.builder()
            .streaming(true)
            .startingStrategy(StreamingStartingStrategy.INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP)
            .startSnapshotTimestamp(snapshot2.timestampMillis() - 1L)
            .build();

    Snapshot startSnapshot =
        ContinuousSplitPlannerImpl.startSnapshot(tableResource.table(), config).get();
    Assert.assertEquals(snapshot2.snapshotId(), startSnapshot.snapshotId());
  }
}
