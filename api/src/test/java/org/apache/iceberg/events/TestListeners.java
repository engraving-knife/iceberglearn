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
package org.apache.iceberg.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestListeners 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestListeners 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestListeners {
  static {
    Listeners.register(TestListener.get()::event1, Event1.class);
    Listeners.register(TestListener.get()::event2, Event2.class);
  }

  public static class Event1 {}

  public static class Event2 {}

  public static class TestListener {
    private static final TestListener INSTANCE = new TestListener();

    /** 辅助方法：get。 */
    public static TestListener get() {
      return INSTANCE;
    }

    private Event1 e1 = null;
    private Event2 e2 = null;

    /** 辅助方法：event1。 */
    public void event1(Event1 event) {
      this.e1 = event;
    }

    /** 辅助方法：event2。 */
    public void event2(Event2 event) {
      this.e2 = event;
    }
  }

  /**
   * 测试场景：Event 1。
   *
   * <p>验证该方法在 Event 1 条件下的行为是否符合预期。
   */
  @Test
  public void testEvent1() {
    Event1 e1 = new Event1();

    Listeners.notifyAll(e1);

    assertThat(TestListener.get().e1).isEqualTo(e1);
  }

  /**
   * 测试场景：Event 2。
   *
   * <p>验证该方法在 Event 2 条件下的行为是否符合预期。
   */
  @Test
  public void testEvent2() {
    Event2 e2 = new Event2();

    Listeners.notifyAll(e2);

    assertThat(TestListener.get().e2).isEqualTo(e2);
  }

  /**
   * 测试场景：Multiple Listeners。
   *
   * <p>验证该方法在 Multiple Listeners 条件下的行为是否符合预期。
   */
  @Test
  public void testMultipleListeners() {
    TestListener other = new TestListener();
    Listeners.register(other::event1, Event1.class);

    Event1 e1 = new Event1();

    Listeners.notifyAll(e1);

    assertThat(TestListener.get().e1).isEqualTo(e1);
    assertThat(other.e1).isEqualTo(e1);
  }
}
