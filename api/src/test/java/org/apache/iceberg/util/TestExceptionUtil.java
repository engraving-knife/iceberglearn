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

import java.io.IOException;
import java.util.Arrays;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestExceptionUtil 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestExceptionUtil 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestExceptionUtil {

  private static class CustomCheckedException extends Exception {
    /** 辅助方法：CustomCheckedException。 */
    private CustomCheckedException(String message) {
      super(message);
    }
  }

  /**
   * 测试场景：Run Safely。
   *
   * <p>验证该方法在 Run Safely 条件下的行为是否符合预期。
   */
  @Test
  public void testRunSafely() {
    CustomCheckedException exc = new CustomCheckedException("test");
    Exception suppressedOne = new Exception("test catch suppression");
    RuntimeException suppressedTwo = new RuntimeException("test finally suppression");
    Assertions.assertThatThrownBy(
            () ->
                ExceptionUtil.runSafely(
                    () -> {
                      throw exc;
                    },
                    e -> {
                      throw suppressedOne;
                    },
                    () -> {
                      throw suppressedTwo;
                    },
                    CustomCheckedException.class))
        .isInstanceOf(CustomCheckedException.class)
        .isEqualTo(exc)
        .extracting(e -> Arrays.asList(e.getSuppressed()))
        .asList()
        .hasSize(2)
        .containsExactly(suppressedOne, suppressedTwo);
  }

  /**
   * 测试场景：Run Safely Two Exceptions。
   *
   * <p>验证该方法在 Run Safely Two Exceptions 条件下的行为是否符合预期。
   */
  @Test
  public void testRunSafelyTwoExceptions() {
    CustomCheckedException exc = new CustomCheckedException("test");
    Exception suppressedOne = new Exception("test catch suppression");
    RuntimeException suppressedTwo = new RuntimeException("test finally suppression");
    Assertions.assertThatThrownBy(
            () ->
                ExceptionUtil.runSafely(
                    (ExceptionUtil.Block<
                            Void, CustomCheckedException, IOException, RuntimeException>)
                        () -> {
                          throw exc;
                        },
                    e -> {
                      throw suppressedOne;
                    },
                    () -> {
                      throw suppressedTwo;
                    },
                    CustomCheckedException.class,
                    IOException.class))
        .isInstanceOf(CustomCheckedException.class)
        .isEqualTo(exc)
        .extracting(e -> Arrays.asList(e.getSuppressed()))
        .asList()
        .hasSize(2)
        .containsExactly(suppressedOne, suppressedTwo);
  }

  /**
   * 测试场景：Run Safely Three Exceptions。
   *
   * <p>验证该方法在 Run Safely Three Exceptions 条件下的行为是否符合预期。
   */
  @Test
  public void testRunSafelyThreeExceptions() {
    CustomCheckedException exc = new CustomCheckedException("test");
    Exception suppressedOne = new Exception("test catch suppression");
    RuntimeException suppressedTwo = new RuntimeException("test finally suppression");
    Assertions.assertThatThrownBy(
            () ->
                ExceptionUtil.runSafely(
                    (ExceptionUtil.Block<
                            Void, CustomCheckedException, IOException, ClassNotFoundException>)
                        () -> {
                          throw exc;
                        },
                    e -> {
                      throw suppressedOne;
                    },
                    () -> {
                      throw suppressedTwo;
                    },
                    CustomCheckedException.class,
                    IOException.class,
                    ClassNotFoundException.class))
        .isInstanceOf(CustomCheckedException.class)
        .isEqualTo(exc)
        .extracting(e -> Arrays.asList(e.getSuppressed()))
        .asList()
        .hasSize(2)
        .containsExactly(suppressedOne, suppressedTwo);
  }

  /**
   * 测试场景：Run Safely Runtime Exceptions。
   *
   * <p>验证该方法在 Run Safely Runtime Exceptions 条件下的行为是否符合预期。
   */
  @Test
  public void testRunSafelyRuntimeExceptions() {
    RuntimeException exc = new RuntimeException("test");
    Exception suppressedOne = new Exception("test catch suppression");
    CustomCheckedException suppressedTwo = new CustomCheckedException("test finally suppression");
    Assertions.assertThatThrownBy(
            () ->
                ExceptionUtil.runSafely(
                    () -> {
                      throw exc;
                    },
                    e -> {
                      throw suppressedOne;
                    },
                    () -> {
                      throw suppressedTwo;
                    }))
        .isInstanceOf(RuntimeException.class)
        .isEqualTo(exc)
        .extracting(e -> Arrays.asList(e.getSuppressed()))
        .asList()
        .hasSize(2)
        .containsExactly(suppressedOne, suppressedTwo);
  }
}
