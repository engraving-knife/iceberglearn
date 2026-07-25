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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.iceberg.flink.source.SplitHelpers;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitStatus;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestIcebergEnumeratorStateSerializer 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestIcebergEnumeratorStateSerializer 在各类场景下的行为是否符合预期，
 * 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public class TestIcebergEnumeratorStateSerializer {
  @ClassRule public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

  private final IcebergEnumeratorStateSerializer serializer =
      new IcebergEnumeratorStateSerializer(true);

  protected final int version;

  /** 辅助方法：parameters，parameters。 */
  @Parameterized.Parameters(name = "version={0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：TestIcebergEnumeratorStateSerializer，Iceberg Enumerator State Serializer。 */
  public TestIcebergEnumeratorStateSerializer(int version) {
    this.version = version;
  }

  /**
   * 测试场景：Empty Snapshot Id And Pending Splits。
   *
   * <p>验证该方法在 Empty Snapshot Id And Pending Splits 条件下的行为是否符合预期。
   */
  @Test
  public void testEmptySnapshotIdAndPendingSplits() throws Exception {
    IcebergEnumeratorState enumeratorState = new IcebergEnumeratorState(Collections.emptyList());
    testSerializer(enumeratorState);
  }

  /**
   * 测试场景：Some Snapshot Id And Empty Pending Splits。
   *
   * <p>验证该方法在 Some Snapshot Id And Empty Pending Splits 条件下的行为是否符合预期。
   */
  @Test
  public void testSomeSnapshotIdAndEmptyPendingSplits() throws Exception {
    IcebergEnumeratorPosition position =
        IcebergEnumeratorPosition.of(1L, System.currentTimeMillis());

    IcebergEnumeratorState enumeratorState =
        new IcebergEnumeratorState(position, Collections.emptyList());
    testSerializer(enumeratorState);
  }

  /**
   * 测试场景：Some Snapshot Id And Pending Splits。
   *
   * <p>验证该方法在 Some Snapshot Id And Pending Splits 条件下的行为是否符合预期。
   */
  @Test
  public void testSomeSnapshotIdAndPendingSplits() throws Exception {
    IcebergEnumeratorPosition position =
        IcebergEnumeratorPosition.of(2L, System.currentTimeMillis());
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 3, 1);
    Collection<IcebergSourceSplitState> pendingSplits = Lists.newArrayList();
    pendingSplits.add(
        new IcebergSourceSplitState(splits.get(0), IcebergSourceSplitStatus.UNASSIGNED));
    pendingSplits.add(
        new IcebergSourceSplitState(splits.get(1), IcebergSourceSplitStatus.ASSIGNED));
    pendingSplits.add(
        new IcebergSourceSplitState(splits.get(2), IcebergSourceSplitStatus.COMPLETED));

    IcebergEnumeratorState enumeratorState = new IcebergEnumeratorState(position, pendingSplits);
    testSerializer(enumeratorState);
  }

  /**
   * 测试场景：Enumeration Split Count History。
   *
   * <p>验证该方法在 Enumeration Split Count History 条件下的行为是否符合预期。
   */
  @Test
  public void testEnumerationSplitCountHistory() throws Exception {
    if (version == 2) {
      IcebergEnumeratorPosition position =
          IcebergEnumeratorPosition.of(2L, System.currentTimeMillis());
      List<IcebergSourceSplit> splits =
          SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 3, 1);
      Collection<IcebergSourceSplitState> pendingSplits = Lists.newArrayList();
      pendingSplits.add(
          new IcebergSourceSplitState(splits.get(0), IcebergSourceSplitStatus.UNASSIGNED));
      pendingSplits.add(
          new IcebergSourceSplitState(splits.get(1), IcebergSourceSplitStatus.ASSIGNED));
      pendingSplits.add(
          new IcebergSourceSplitState(splits.get(2), IcebergSourceSplitStatus.COMPLETED));
      int[] enumerationSplitCountHistory = {1, 2, 3};

      IcebergEnumeratorState enumeratorState =
          new IcebergEnumeratorState(position, pendingSplits, enumerationSplitCountHistory);
      testSerializer(enumeratorState);
    }
  }

  /**
   * 测试场景：Serializer。
   *
   * <p>验证该方法在 Serializer 条件下的行为是否符合预期。
   */
  private void testSerializer(IcebergEnumeratorState enumeratorState) throws IOException {
    byte[] result;
    if (version == 1) {
      result = serializer.serializeV1(enumeratorState);
    } else {
      result = serializer.serialize(enumeratorState);
    }

    IcebergEnumeratorState deserialized = serializer.deserialize(version, result);
    assertEnumeratorStateEquals(enumeratorState, deserialized);
  }

  /** 辅助方法：assertEnumeratorStateEquals，assert Enumerator State Equals。 */
  private void assertEnumeratorStateEquals(
      IcebergEnumeratorState expected, IcebergEnumeratorState actual) {
    Assert.assertEquals(expected.lastEnumeratedPosition(), actual.lastEnumeratedPosition());

    Assert.assertEquals(expected.pendingSplits().size(), actual.pendingSplits().size());
    Iterator<IcebergSourceSplitState> expectedIterator = expected.pendingSplits().iterator();
    Iterator<IcebergSourceSplitState> actualIterator = actual.pendingSplits().iterator();
    for (int i = 0; i < expected.pendingSplits().size(); ++i) {
      IcebergSourceSplitState expectedSplitState = expectedIterator.next();
      IcebergSourceSplitState actualSplitState = actualIterator.next();
      Assert.assertEquals(expectedSplitState.split().splitId(), actualSplitState.split().splitId());
      Assert.assertEquals(
          expectedSplitState.split().fileOffset(), actualSplitState.split().fileOffset());
      Assert.assertEquals(
          expectedSplitState.split().recordOffset(), actualSplitState.split().recordOffset());
      Assert.assertEquals(expectedSplitState.status(), actualSplitState.status());
    }

    Assert.assertArrayEquals(
        expected.enumerationSplitCountHistory(), actual.enumerationSplitCountHistory());
  }
}
