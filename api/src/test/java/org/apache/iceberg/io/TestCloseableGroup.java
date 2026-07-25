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

import java.io.Closeable;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 文件级说明：测试 TestCloseableGroup 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestCloseableGroup 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestCloseableGroup {

  /**
   * 测试场景：call Close To All Closeables。
   *
   * <p>验证该方法在 call Close To All Closeables 条件下的行为是否符合预期。
   */
  @Test
  public void callCloseToAllCloseables() throws IOException {
    Closeable closeable1 = Mockito.mock(Closeable.class);
    Closeable closeable2 = Mockito.mock(Closeable.class);
    Closeable closeable3 = Mockito.mock(Closeable.class);

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(closeable1);
    closeableGroup.addCloseable(closeable2);
    closeableGroup.addCloseable(closeable3);

    closeableGroup.close();
    Mockito.verify(closeable1).close();
    Mockito.verify(closeable2).close();
    Mockito.verify(closeable3).close();
  }

  /**
   * 测试场景：call Close Handles Auto Closeable。
   *
   * <p>验证该方法在 call Close Handles Auto Closeable 条件下的行为是否符合预期。
   */
  @Test
  public void callCloseHandlesAutoCloseable() throws Exception {
    Closeable closeable1 = Mockito.mock(Closeable.class);
    AutoCloseable closeable2 = Mockito.mock(AutoCloseable.class);

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(closeable1);
    closeableGroup.addCloseable(closeable2);

    closeableGroup.close();
    Mockito.verify(closeable1).close();
    Mockito.verify(closeable2).close();
  }

  /**
   * 测试场景：suppress Exception If Set Suppress Is True。
   *
   * <p>验证该方法在 suppress Exception If Set Suppress Is True 条件下的行为是否符合预期。
   */
  @Test
  public void suppressExceptionIfSetSuppressIsTrue() throws Exception {
    Closeable closeable1 = Mockito.mock(Closeable.class);
    AutoCloseable closeable2 = Mockito.mock(AutoCloseable.class);
    Closeable closeable3 = Mockito.mock(Closeable.class);
    Mockito.doThrow(new IOException("exception1")).when(closeable1).close();
    Mockito.doThrow(new RuntimeException("exception2")).when(closeable2).close();

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(closeable1);
    closeableGroup.addCloseable(closeable2);
    closeableGroup.addCloseable(closeable3);

    closeableGroup.setSuppressCloseFailure(true);
    closeableGroup.close();
    Mockito.verify(closeable1).close();
    Mockito.verify(closeable2).close();
    Mockito.verify(closeable3).close();
  }

  /**
   * 测试场景：not Suppress Exception If Set Suppress Is False。
   *
   * <p>验证该方法在 not Suppress Exception If Set Suppress Is False 条件下的行为是否符合预期。
   */
  @Test
  public void notSuppressExceptionIfSetSuppressIsFalse() throws Exception {
    IOException ioException = new IOException("e1");

    Closeable closeable1 = Mockito.mock(Closeable.class);
    Closeable closeable2 = Mockito.mock(Closeable.class);
    Closeable closeable3 = Mockito.mock(Closeable.class);
    Mockito.doThrow(ioException).when(closeable2).close();

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(closeable1);
    closeableGroup.addCloseable(closeable2);
    closeableGroup.addCloseable(closeable3);

    Assertions.assertThatThrownBy(closeableGroup::close).isEqualTo(ioException);
    Mockito.verify(closeable1).close();
    Mockito.verify(closeable2).close();
    Mockito.verifyNoInteractions(closeable3);
  }

  /**
   * 测试场景：not Suppress Exception If Set Suppress Is False For Auto Closeable。
   *
   * <p>验证该方法在 not Suppress Exception If Set Suppress Is False For Auto Closeable 条件下的行为是否符合预期。
   */
  @Test
  public void notSuppressExceptionIfSetSuppressIsFalseForAutoCloseable() throws Exception {
    IOException ioException = new IOException("e1");

    AutoCloseable closeable1 = Mockito.mock(AutoCloseable.class);
    AutoCloseable closeable2 = Mockito.mock(AutoCloseable.class);
    AutoCloseable closeable3 = Mockito.mock(AutoCloseable.class);
    Mockito.doThrow(ioException).when(closeable2).close();

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(closeable1);
    closeableGroup.addCloseable(closeable2);
    closeableGroup.addCloseable(closeable3);

    Assertions.assertThatThrownBy(closeableGroup::close).isEqualTo(ioException);
    Mockito.verify(closeable1).close();
    Mockito.verify(closeable2).close();
    Mockito.verifyNoInteractions(closeable3);
  }

  /**
   * 测试场景：wrap Auto Closeable Failures With Runtime Exception。
   *
   * <p>验证该方法在 wrap Auto Closeable Failures With Runtime Exception 条件下的行为是否符合预期。
   */
  @Test
  public void wrapAutoCloseableFailuresWithRuntimeException() throws Exception {
    Exception generalException = new Exception("e");
    AutoCloseable throwingAutoCloseable = Mockito.mock(AutoCloseable.class);
    Mockito.doThrow(generalException).when(throwingAutoCloseable).close();

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(throwingAutoCloseable);

    Assertions.assertThatThrownBy(closeableGroup::close)
        .isInstanceOf(RuntimeException.class)
        .hasRootCause(generalException);
  }

  /**
   * 测试场景：not Wrap Runtime Exception。
   *
   * <p>验证该方法在 not Wrap Runtime Exception 条件下的行为是否符合预期。
   */
  @Test
  public void notWrapRuntimeException() throws Exception {
    RuntimeException runtimeException = new RuntimeException("e2");

    Closeable throwingCloseable = Mockito.mock(Closeable.class);
    Mockito.doThrow(runtimeException).when(throwingCloseable).close();

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(throwingCloseable);

    Assertions.assertThatThrownBy(closeableGroup::close).isEqualTo(runtimeException);
  }

  /**
   * 测试场景：not Wrap Runtime Exception From Auto Closeable。
   *
   * <p>验证该方法在 not Wrap Runtime Exception From Auto Closeable 条件下的行为是否符合预期。
   */
  @Test
  public void notWrapRuntimeExceptionFromAutoCloseable() throws Exception {
    RuntimeException runtimeException = new RuntimeException("e2");
    AutoCloseable throwingAutoCloseable = Mockito.mock(AutoCloseable.class);
    Mockito.doThrow(runtimeException).when(throwingAutoCloseable).close();

    CloseableGroup closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(throwingAutoCloseable);

    Assertions.assertThatThrownBy(closeableGroup::close).isEqualTo(runtimeException);
  }
}
