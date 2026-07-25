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
package org.apache.iceberg.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestClosingIterator 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestClosingIterator 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestClosingIterator {
  /**
   * 测试场景：Empty Iterator。
   *
   * <p>验证该方法在 Empty Iterator 条件下的行为是否符合预期。
   */
  @Test
  public void testEmptyIterator() {
    CloseableIterator<String> underlying = mock(CloseableIterator.class);
    ClosingIterator<String> closingIterator = new ClosingIterator<>(underlying);
    assertThat(closingIterator).isExhausted();
  }

  /**
   * 测试场景：Has Next And Next。
   *
   * <p>验证该方法在 Has Next And Next 条件下的行为是否符合预期。
   */
  @Test
  public void testHasNextAndNext() {
    CloseableIterator<String> underlying = mock(CloseableIterator.class);
    when(underlying.hasNext()).thenReturn(true);
    when(underlying.next()).thenReturn("hello");
    ClosingIterator<String> closingIterator = new ClosingIterator<>(underlying);
    assertThat(closingIterator).hasNext();
    assertThat(closingIterator.next()).isEqualTo("hello");
  }

  /**
   * 测试场景：Underlying Iterator Close When Elements Are Exhausted。
   *
   * <p>验证该方法在 Underlying Iterator Close When Elements Are Exhausted 条件下的行为是否符合预期。
   */
  @Test
  public void testUnderlyingIteratorCloseWhenElementsAreExhausted() throws Exception {
    CloseableIterator<String> underlying = mock(CloseableIterator.class);
    when(underlying.hasNext()).thenReturn(true).thenReturn(false);
    when(underlying.next()).thenReturn("hello");
    ClosingIterator<String> closingIterator = new ClosingIterator<>(underlying);
    assertThat(closingIterator).hasNext();
    assertThat(closingIterator.next()).isEqualTo("hello");
    assertThat(closingIterator).isExhausted();
    verify(underlying, times(1)).close();
  }

  /**
   * 测试场景：Close Called Once For Multiple Has Next Calls。
   *
   * <p>验证该方法在 Close Called Once For Multiple Has Next Calls 条件下的行为是否符合预期。
   */
  @Test
  public void testCloseCalledOnceForMultipleHasNextCalls() throws Exception {
    CloseableIterator<String> underlying = mock(CloseableIterator.class);
    ClosingIterator<String> closingIterator = new ClosingIterator<>(underlying);
    assertThat(closingIterator).isExhausted();
    verify(underlying, times(1)).close();
  }

  /**
   * 测试场景：transform Null Check。
   *
   * <p>验证该方法在 transform Null Check 条件下的行为是否符合预期。
   */
  @Test
  public void transformNullCheck() {
    Assertions.assertThatThrownBy(
            () -> CloseableIterator.transform(CloseableIterator.empty(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid transform: null");
  }
}
