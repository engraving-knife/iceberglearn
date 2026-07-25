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

import org.junit.jupiter.api.Test;

/**
 * 测试类：TestArrayUtil，用于验证 Array Util 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Array Util 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestArrayUtil {

  /**
   * 测试场景：strictly ascending long arrays。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testStrictlyAscendingLongArrays() {
    long[] emptyArray = new long[0];
    assertThat(ArrayUtil.isStrictlyAscending(emptyArray)).isTrue();

    long[] singleElementArray = new long[] {1};
    assertThat(ArrayUtil.isStrictlyAscending(singleElementArray)).isTrue();

    long[] strictlyAscendingArray = new long[] {1, 2, 3};
    assertThat(ArrayUtil.isStrictlyAscending(strictlyAscendingArray)).isTrue();

    long[] descendingArray = new long[] {3, 2, 1};
    assertThat(ArrayUtil.isStrictlyAscending(descendingArray)).isFalse();

    long[] ascendingArray = new long[] {1, 2, 2, 3};
    assertThat(ArrayUtil.isStrictlyAscending(ascendingArray)).isFalse();
  }
}
