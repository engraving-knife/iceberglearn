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
package org.apache.iceberg.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.BinPacking.ListPacker;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestBinPacking，用于验证 Bin Packing 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Bin Packing 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestBinPacking {
  /**
   * 测试场景：basic bin packing。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBasicBinPacking() {
    assertThat(pack(list(1, 2, 3, 4, 5), 3))
        .as("Should pack the first 2 values")
        .isEqualTo(list(list(1, 2), list(3), list(4), list(5)));

    assertThat(pack(list(1, 2, 3, 4, 5), 5))
        .as("Should pack the first 2 values")
        .isEqualTo(list(list(1, 2), list(3), list(4), list(5)));

    assertThat(pack(list(1, 2, 3, 4, 5), 6))
        .as("Should pack the first 3 values")
        .isEqualTo(list(list(1, 2, 3), list(4), list(5)));

    assertThat(pack(list(1, 2, 3, 4, 5), 8))
        .as("Should pack the first 3 values")
        .isEqualTo(list(list(1, 2, 3), list(4), list(5)));

    assertThat(pack(list(1, 2, 3, 4, 5), 9))
        .as("Should pack the first 3 values, last 2 values")
        .isEqualTo(list(list(1, 2, 3), list(4, 5)));

    assertThat(pack(list(1, 2, 3, 4, 5), 10))
        .as("Should pack the first 4 values")
        .isEqualTo(list(list(1, 2, 3, 4), list(5)));

    assertThat(pack(list(1, 2, 3, 4, 5), 14))
        .as("Should pack the first 4 values")
        .isEqualTo(list(list(1, 2, 3, 4), list(5)));

    assertThat(pack(list(1, 2, 3, 4, 5), 15))
        .as("Should pack the first 5 values")
        .isEqualTo(list(list(1, 2, 3, 4, 5)));
  }

  /**
   * 测试场景：reverse bin packing single lookback。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReverseBinPackingSingleLookback() {
    assertThat(packEnd(list(1, 2, 3, 4, 5), 3, 1))
        .as("Should pack the first 2 values")
        .isEqualTo(list(list(1, 2), list(3), list(4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 4, 1))
        .as("Should pack the first 2 values")
        .isEqualTo(list(list(1, 2), list(3), list(4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 5, 1))
        .as("Should pack the second and third values")
        .isEqualTo(list(list(1), list(2, 3), list(4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 6, 1))
        .as("Should pack the first 3 values")
        .isEqualTo(list(list(1, 2, 3), list(4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 7, 1))
        .as("Should pack the first two pairs of values")
        .isEqualTo(list(list(1, 2), list(3, 4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 8, 1))
        .as("Should pack the first two pairs of values")
        .isEqualTo(list(list(1, 2), list(3, 4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 9, 1))
        .as("Should pack the first 3 values, last 2 values")
        .isEqualTo(list(list(1, 2, 3), list(4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 11, 1))
        .as("Should pack the first 3 values, last 2 values")
        .isEqualTo(list(list(1, 2, 3), list(4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 12, 1))
        .as("Should pack the first 3 values, last 2 values")
        .isEqualTo(list(list(1, 2), list(3, 4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 14, 1))
        .as("Should pack the last 4 values")
        .isEqualTo(list(list(1), list(2, 3, 4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 15, 1))
        .as("Should pack the first 5 values")
        .isEqualTo(list(list(1, 2, 3, 4, 5)));
  }

  /**
   * 测试场景：reverse bin packing unlimited lookback。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReverseBinPackingUnlimitedLookback() {
    assertThat(packEnd(list(1, 2, 3, 4, 5), 3))
        .as("Should pack the first 2 values")
        .isEqualTo(list(list(1, 2), list(3), list(4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 4))
        .as("Should pack 1 with 3")
        .isEqualTo(list(list(2), list(1, 3), list(4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 5))
        .as("Should pack 2,3 and 1,4")
        .isEqualTo(list(list(2, 3), list(1, 4), list(5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 6))
        .as("Should pack 2,4 and 1,5")
        .isEqualTo(list(list(3), list(2, 4), list(1, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 7))
        .as("Should pack 3,4 and 2,5")
        .isEqualTo(list(list(1), list(3, 4), list(2, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 8))
        .as("Should pack 1,2,3 and 3,5")
        .isEqualTo(list(list(1, 2, 4), list(3, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 9))
        .as("Should pack the first 3 values, last 2 values")
        .isEqualTo(list(list(1, 2, 3), list(4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 10))
        .as("Should pack 2,3 and 1,4,5")
        .isEqualTo(list(list(2, 3), list(1, 4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 11))
        .as("Should pack 1,3 and 2,4,5")
        .isEqualTo(list(list(1, 3), list(2, 4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 12))
        .as("Should pack 1,2 and 3,4,5")
        .isEqualTo(list(list(1, 2), list(3, 4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 13))
        .as("Should pack 1,2 and 3,4,5")
        .isEqualTo(list(list(2), list(1, 3, 4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 14))
        .as("Should pack the last 4 values")
        .isEqualTo(list(list(1), list(2, 3, 4, 5)));

    assertThat(packEnd(list(1, 2, 3, 4, 5), 15))
        .as("Should pack the first 5 values")
        .isEqualTo(list(list(1, 2, 3, 4, 5)));
  }

  /**
   * 测试场景：bin packing look back。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBinPackingLookBack() {
    // lookback state:
    // 1. [5]
    // 2. [5, 1]
    // 3. [5, 1], [5]
    // 4. [5, 1, 1], [5]
    // 5. [5, 1, 1], [5], [5]
    // 6. [5, 1, 1, 1], [5], [5]
    assertThat(pack(list(5, 1, 5, 1, 5, 1), 8))
        .as("Unlimited look-back: should merge ones into first bin")
        .isEqualTo(list(list(5, 1, 1, 1), list(5), list(5)));

    // lookback state:
    // 1. [5]
    // 2. [5, 1]
    // 3. [5, 1], [5]
    // 4. [5, 1, 1], [5]
    // 5. [5], [5]          ([5, 1, 1] drops out of look-back)
    // 6. [5, 1], [5]
    assertThat(pack(list(5, 1, 5, 1, 5, 1), 8, 2))
        .as("2 bin look-back: should merge two ones into first bin")
        .isEqualTo(list(list(5, 1, 1), list(5, 1), list(5)));

    // lookback state:
    // 1. [5]
    // 2. [5, 1]
    // 3. [5]               ([5, 1] drops out of look-back)
    // 4. [5, 1]
    // 5. [5]               ([5, 1] #2 drops out of look-back)
    // 6. [5, 1]
    assertThat(pack(list(5, 1, 5, 1, 5, 1), 8, 1))
        .as("1 bin look-back: should merge ones with fives")
        .isEqualTo(list(list(5, 1), list(5, 1), list(5, 1)));

    assertThat(pack(list(36, 36, 36, 36, 65, 65, 128), 128, 2, true))
        .as("2 bin look-back: should merge until targetWeight when largestBinFirst is enabled")
        .isEqualTo(list(list(36, 36, 36), list(128), list(36, 65), list(65)));

    assertThat(pack(list(64, 64, 128, 32, 32, 32, 32), 128, 1, true))
        .as("1 bin look-back: should merge until targetWeight when largestBinFirst is enabled")
        .isEqualTo(list(list(64, 64), list(128), list(32, 32, 32, 32)));
  }

  /** 辅助方法：pack。 */
  private List<List<Integer>> pack(List<Integer> items, long targetWeight) {
    return pack(items, targetWeight, Integer.MAX_VALUE);
  }

  /** 辅助方法：pack。 */
  private List<List<Integer>> pack(List<Integer> items, long targetWeight, int lookback) {
    return pack(items, targetWeight, lookback, false);
  }

  /** 辅助方法：pack。 */
  private List<List<Integer>> pack(
      List<Integer> items, long targetWeight, int lookback, boolean largestBinFirst) {
    ListPacker<Integer> packer = new ListPacker<>(targetWeight, lookback, largestBinFirst);
    return packer.pack(items, Integer::longValue);
  }

  /** 辅助方法：pack end。 */
  private List<List<Integer>> packEnd(List<Integer> items, long targetWeight) {
    return packEnd(items, targetWeight, Integer.MAX_VALUE);
  }

  /** 辅助方法：pack end。 */
  private List<List<Integer>> packEnd(List<Integer> items, long targetWeight, int lookback) {
    ListPacker<Integer> packer = new ListPacker<>(targetWeight, lookback, false);
    return packer.packEnd(items, Integer::longValue);
  }

  /** 辅助方法：list。 */
  private <T> List<T> list(T... items) {
    return Lists.newArrayList(items);
  }
}
