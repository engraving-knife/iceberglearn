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

import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestEnumerationHistory 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestEnumerationHistory 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestEnumerationHistory {
  private static final int MAX_HISTORY_SIZE = 3;
  private static final int FEW_PENDING_SPLITS = 2;
  private static final int TOO_MANY_PENDING_SPLITS = 100;

  /**
   * 测试场景：Empty History。
   *
   * <p>验证该方法在 Empty History 条件下的行为是否符合预期。
   */
  @Test
  public void testEmptyHistory() {
    EnumerationHistory history = new EnumerationHistory(MAX_HISTORY_SIZE);
    int[] expectedHistorySnapshot = new int[0];
    testHistory(history, expectedHistorySnapshot);
  }

  /**
   * 测试场景：Not Full History。
   *
   * <p>验证该方法在 Not Full History 条件下的行为是否符合预期。
   */
  @Test
  public void testNotFullHistory() {
    EnumerationHistory history = new EnumerationHistory(3);
    history.add(1);
    history.add(2);
    int[] expectedHistorySnapshot = {1, 2};
    testHistory(history, expectedHistorySnapshot);
  }

  /**
   * 测试场景：Exact Full History。
   *
   * <p>验证该方法在 Exact Full History 条件下的行为是否符合预期。
   */
  @Test
  public void testExactFullHistory() {
    EnumerationHistory history = new EnumerationHistory(3);
    history.add(1);
    history.add(2);
    history.add(3);
    int[] expectedHistorySnapshot = {1, 2, 3};
    testHistory(history, expectedHistorySnapshot);
  }

  /**
   * 测试场景：One More Than Full History。
   *
   * <p>验证该方法在 One More Than Full History 条件下的行为是否符合预期。
   */
  @Test
  public void testOneMoreThanFullHistory() {
    EnumerationHistory history = new EnumerationHistory(3);
    history.add(1);
    history.add(2);
    history.add(3);
    history.add(4);
    int[] expectedHistorySnapshot = {2, 3, 4};
    testHistory(history, expectedHistorySnapshot);
  }

  /**
   * 测试场景：Two More Than Full History。
   *
   * <p>验证该方法在 Two More Than Full History 条件下的行为是否符合预期。
   */
  @Test
  public void testTwoMoreThanFullHistory() {
    EnumerationHistory history = new EnumerationHistory(3);
    history.add(1);
    history.add(2);
    history.add(3);
    history.add(4);
    history.add(5);
    int[] expectedHistorySnapshot = {3, 4, 5};
    testHistory(history, expectedHistorySnapshot);
  }

  /**
   * 测试场景：Three More Than Full History。
   *
   * <p>验证该方法在 Three More Than Full History 条件下的行为是否符合预期。
   */
  @Test
  public void testThreeMoreThanFullHistory() {
    EnumerationHistory history = new EnumerationHistory(3);
    history.add(1);
    history.add(2);
    history.add(3);
    history.add(4);
    history.add(5);
    history.add(6);
    int[] expectedHistorySnapshot = {4, 5, 6};
    testHistory(history, expectedHistorySnapshot);
  }

  /**
   * 测试场景：History。
   *
   * <p>验证该方法在 History 条件下的行为是否符合预期。
   */
  private void testHistory(EnumerationHistory history, int[] expectedHistorySnapshot) {
    Assert.assertFalse(history.shouldPauseSplitDiscovery(FEW_PENDING_SPLITS));
    if (history.hasFullHistory()) {
      // throttle because pending split count is more than the sum of enumeration history
      Assert.assertTrue(history.shouldPauseSplitDiscovery(TOO_MANY_PENDING_SPLITS));
    } else {
      // skipped throttling check because there is not enough history
      Assert.assertFalse(history.shouldPauseSplitDiscovery(TOO_MANY_PENDING_SPLITS));
    }

    int[] historySnapshot = history.snapshot();
    Assert.assertArrayEquals(expectedHistorySnapshot, historySnapshot);

    EnumerationHistory restoredHistory = new EnumerationHistory(MAX_HISTORY_SIZE);
    restoredHistory.restore(historySnapshot);

    Assert.assertFalse(history.shouldPauseSplitDiscovery(FEW_PENDING_SPLITS));
    if (history.hasFullHistory()) {
      // throttle because pending split count is more than the sum of enumeration history
      Assert.assertTrue(history.shouldPauseSplitDiscovery(TOO_MANY_PENDING_SPLITS));
    } else {
      // skipped throttling check because there is not enough history
      Assert.assertFalse(history.shouldPauseSplitDiscovery(30));
    }
  }

  /**
   * 测试场景：Restore Different Size。
   *
   * <p>验证该方法在 Restore Different Size 条件下的行为是否符合预期。
   */
  @Test
  public void testRestoreDifferentSize() {
    EnumerationHistory history = new EnumerationHistory(3);
    history.add(1);
    history.add(2);
    history.add(3);
    int[] historySnapshot = history.snapshot();

    EnumerationHistory smallerHistory = new EnumerationHistory(2);
    smallerHistory.restore(historySnapshot);
    int[] expectedRestoredHistorySnapshot = {2, 3};
    Assert.assertArrayEquals(expectedRestoredHistorySnapshot, smallerHistory.snapshot());

    EnumerationHistory largerHisotry = new EnumerationHistory(4);
    largerHisotry.restore(historySnapshot);
    Assert.assertArrayEquals(historySnapshot, largerHisotry.snapshot());
  }
}
