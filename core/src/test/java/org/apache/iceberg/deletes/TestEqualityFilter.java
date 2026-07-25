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
package org.apache.iceberg.deletes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.TestHelpers.Row;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.NestedField;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestEqualityFilter，用于验证 Equality Filter 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Equality Filter 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestEqualityFilter {
  private static final Schema ROW_SCHEMA =
      new Schema(
          NestedField.required(1, "id", Types.LongType.get()),
          NestedField.required(2, "name", Types.StringType.get()),
          NestedField.optional(3, "description", Types.StringType.get()));

  private static final CloseableIterable<StructLike> ROWS =
      CloseableIterable.withNoopClose(
          Lists.newArrayList(
              Row.of(0L, "a", "panda"),
              Row.of(1L, "b", "koala"),
              Row.of(2L, "c", new Utf8("kodiak")),
              Row.of(4L, new Utf8("d"), "gummy"),
              Row.of(5L, "e", "brown"),
              Row.of(6L, "f", new Utf8("teddy")),
              Row.of(7L, "g", "grizzly"),
              Row.of(8L, "h", null)));

  /**
   * 测试场景：equality set filter long column。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEqualitySetFilterLongColumn() {
    CloseableIterable<StructLike> deletes =
        CloseableIterable.withNoopClose(Lists.newArrayList(Row.of(4L), Row.of(3L), Row.of(6L)));

    List<StructLike> expected =
        Lists.newArrayList(
            Row.of(0L, "a", "panda"),
            Row.of(1L, "b", "koala"),
            Row.of(2L, "c", new Utf8("kodiak")),
            Row.of(5L, "e", "brown"),
            Row.of(7L, "g", "grizzly"),
            Row.of(8L, "h", null));

    List<StructLike> actual =
        Lists.newArrayList(
            Deletes.filter(
                ROWS,
                row -> Row.of(row.get(0, Long.class)),
                Deletes.toEqualitySet(deletes, ROW_SCHEMA.select("id").asStruct())));

    assertThat(actual).as("Filter should produce expected rows").isEqualTo(expected);
  }

  /**
   * 测试场景：equality set filter string column。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEqualitySetFilterStringColumn() {
    CloseableIterable<StructLike> deletes =
        CloseableIterable.withNoopClose(Lists.newArrayList(Row.of("a"), Row.of("d"), Row.of("h")));

    List<StructLike> expected =
        Lists.newArrayList(
            Row.of(1L, "b", "koala"),
            Row.of(2L, "c", new Utf8("kodiak")),
            Row.of(5L, "e", "brown"),
            Row.of(6L, "f", new Utf8("teddy")),
            Row.of(7L, "g", "grizzly"));

    List<StructLike> actual =
        Lists.newArrayList(
            Deletes.filter(
                ROWS,
                row -> Row.of(row.get(1, CharSequence.class)),
                Deletes.toEqualitySet(deletes, ROW_SCHEMA.select("name").asStruct())));

    assertThat(actual).as("Filter should produce expected rows").isEqualTo(expected);
  }

  /**
   * 测试场景：equality set filter string column with null。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEqualitySetFilterStringColumnWithNull() {
    CloseableIterable<StructLike> deletes =
        CloseableIterable.withNoopClose(Lists.newArrayList(Row.of(new Object[] {null})));

    List<StructLike> expected =
        Lists.newArrayList(
            Row.of(0L, "a", "panda"),
            Row.of(1L, "b", "koala"),
            Row.of(2L, "c", new Utf8("kodiak")),
            Row.of(4L, new Utf8("d"), "gummy"),
            Row.of(5L, "e", "brown"),
            Row.of(6L, "f", new Utf8("teddy")),
            Row.of(7L, "g", "grizzly"));

    List<StructLike> actual =
        Lists.newArrayList(
            Deletes.filter(
                ROWS,
                row -> Row.of(row.get(2, CharSequence.class)),
                Deletes.toEqualitySet(deletes, ROW_SCHEMA.select("description").asStruct())));

    assertThat(actual).as("Filter should produce expected rows").isEqualTo(expected);
  }

  /**
   * 测试场景：equality set filter multiple columns。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testEqualitySetFilterMultipleColumns() {
    CloseableIterable<StructLike> deletes =
        CloseableIterable.withNoopClose(
            Lists.newArrayList(Row.of(2L, "kodiak"), Row.of(3L, "care"), Row.of(8L, null)));

    List<StructLike> expected =
        Lists.newArrayList(
            Row.of(0L, "a", "panda"),
            Row.of(1L, "b", "koala"),
            Row.of(4L, new Utf8("d"), "gummy"),
            Row.of(5L, "e", "brown"),
            Row.of(6L, "f", new Utf8("teddy")),
            Row.of(7L, "g", "grizzly"));

    List<StructLike> actual =
        Lists.newArrayList(
            Deletes.filter(
                ROWS,
                row -> Row.of(row.get(0, Long.class), row.get(2, CharSequence.class)),
                Deletes.toEqualitySet(deletes, ROW_SCHEMA.select("id", "description").asStruct())));

    assertThat(actual).as("Filter should produce expected rows").isEqualTo(expected);
  }
}
