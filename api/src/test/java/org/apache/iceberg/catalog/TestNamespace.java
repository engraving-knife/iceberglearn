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
package org.apache.iceberg.catalog;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestNamespace 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestNamespace 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestNamespace {

  /**
   * 测试场景：With Null And Empty。
   *
   * <p>验证该方法在 With Null And Empty 条件下的行为是否符合预期。
   */
  @Test
  public void testWithNullAndEmpty() {
    Assertions.assertThatThrownBy(() -> Namespace.of((String[]) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create Namespace from null array");

    Assertions.assertThat(Namespace.of()).isEqualTo(Namespace.empty());
  }

  /**
   * 测试场景：Namespace。
   *
   * <p>验证该方法在 Namespace 条件下的行为是否符合预期。
   */
  @Test
  public void testNamespace() {
    String[] levels = {"a", "b", "c", "d"};
    Namespace namespace = Namespace.of(levels);
    Assertions.assertThat(namespace).isNotNull();
    Assertions.assertThat(namespace.levels()).hasSize(4);
    Assertions.assertThat(namespace).hasToString("a.b.c.d");
    for (int i = 0; i < levels.length; i++) {
      Assertions.assertThat(namespace.level(i)).isEqualTo(levels[i]);
    }
  }

  /**
   * 测试场景：With Null In Level。
   *
   * <p>验证该方法在 With Null In Level 条件下的行为是否符合预期。
   */
  @Test
  public void testWithNullInLevel() {
    Assertions.assertThatThrownBy(() -> Namespace.of("a", null, "b"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Cannot create a namespace with a null level");
  }

  /**
   * 测试场景：Disallows Namespace With Null Byte。
   *
   * <p>验证该方法在 Disallows Namespace With Null Byte 条件下的行为是否符合预期。
   */
  @Test
  public void testDisallowsNamespaceWithNullByte() {
    Assertions.assertThatThrownBy(() -> Namespace.of("ac", "\u0000c", "b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create a namespace with the null-byte character");

    Assertions.assertThatThrownBy(() -> Namespace.of("ac", "c\0", "b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create a namespace with the null-byte character");
  }
}
