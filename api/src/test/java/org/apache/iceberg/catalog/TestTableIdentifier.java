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

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestTableIdentifier 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestTableIdentifier 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestTableIdentifier {

  /**
   * 测试场景：Table Identifier Parsing。
   *
   * <p>验证该方法在 Table Identifier Parsing 条件下的行为是否符合预期。
   */
  @Test
  public void testTableIdentifierParsing() {
    TableIdentifier oneLevelIdentifier = TableIdentifier.parse("tbl");
    assertThat(oneLevelIdentifier.hasNamespace()).isFalse();
    assertThat(oneLevelIdentifier.name()).isEqualTo("tbl");

    TableIdentifier twoLevelIdentifier = TableIdentifier.parse("userdb.tbl");
    assertThat(twoLevelIdentifier.namespace().levels()).hasSize(1);
    assertThat(twoLevelIdentifier.namespace().levels()[0]).isEqualTo("userdb");
    assertThat(twoLevelIdentifier.name()).isEqualTo("tbl");

    TableIdentifier threeLevelIdentifier = TableIdentifier.parse("catalog.userdb.tbl");
    assertThat(threeLevelIdentifier.namespace().levels()).hasSize(2);
    assertThat(threeLevelIdentifier.namespace().levels()[0]).isEqualTo("catalog");
    assertThat(threeLevelIdentifier.namespace().levels()[1]).isEqualTo("userdb");
    assertThat(threeLevelIdentifier.name()).isEqualTo("tbl");
  }

  /**
   * 测试场景：To Lower Case。
   *
   * <p>验证该方法在 To Lower Case 条件下的行为是否符合预期。
   */
  @Test
  public void testToLowerCase() {
    assertThat(TableIdentifier.of("tbl")).isEqualTo(TableIdentifier.of("Tbl").toLowerCase());
    assertThat(TableIdentifier.of("db", "tbl"))
        .isEqualTo(TableIdentifier.of("dB", "TBL").toLowerCase());
    assertThat(TableIdentifier.of("catalog", "db", "tbl"))
        .isEqualTo(TableIdentifier.of("Catalog", "dB", "TBL").toLowerCase());
  }

  /**
   * 测试场景：Invalid Table Name。
   *
   * <p>验证该方法在 Invalid Table Name 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidTableName() {
    Assertions.assertThatThrownBy(() -> TableIdentifier.of(Namespace.empty(), ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table name: null or empty");

    Assertions.assertThatThrownBy(() -> TableIdentifier.of(Namespace.empty(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table name: null or empty");
  }

  /**
   * 测试场景：Nulls。
   *
   * <p>验证该方法在 Nulls 条件下的行为是否符合预期。
   */
  @Test
  public void testNulls() {
    Assertions.assertThatThrownBy(() -> TableIdentifier.of((String[]) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot create table identifier from null array");

    Assertions.assertThatThrownBy(() -> TableIdentifier.parse(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse table identifier: null");

    Assertions.assertThatThrownBy(() -> TableIdentifier.of(null, "name"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid Namespace: null");
  }
}
