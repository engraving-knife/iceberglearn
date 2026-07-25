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
package org.apache.iceberg.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestComparators 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestComparators 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestComparators {

  /** 辅助方法：assertComparesCorrectly。 */
  private <T> void assertComparesCorrectly(Comparator<T> cmp, T less, T greater) {
    assertThat(cmp.compare(greater, greater)).isZero();
    assertThat(cmp.compare(less, less)).isZero();
    assertThat(Integer.signum(cmp.compare(less, greater))).isEqualTo(-1);
    assertThat(Integer.signum(cmp.compare(greater, less))).isOne();
  }

  /**
   * 测试场景：Boolean。
   *
   * <p>验证该方法在 Boolean 条件下的行为是否符合预期。
   */
  @Test
  public void testBoolean() {
    assertComparesCorrectly(Comparators.forType(Types.BooleanType.get()), false, true);
  }

  /**
   * 测试场景：Int。
   *
   * <p>验证该方法在 Int 条件下的行为是否符合预期。
   */
  @Test
  public void testInt() {
    assertComparesCorrectly(Comparators.forType(Types.IntegerType.get()), 0, 1);
  }

  /**
   * 测试场景：Long。
   *
   * <p>验证该方法在 Long 条件下的行为是否符合预期。
   */
  @Test
  public void testLong() {
    assertComparesCorrectly(Comparators.forType(Types.LongType.get()), 0L, 1L);
  }

  /**
   * 测试场景：Float。
   *
   * <p>验证该方法在 Float 条件下的行为是否符合预期。
   */
  @Test
  public void testFloat() {
    assertComparesCorrectly(Comparators.forType(Types.FloatType.get()), 0.1f, 0.2f);
  }

  /**
   * 测试场景：Double。
   *
   * <p>验证该方法在 Double 条件下的行为是否符合预期。
   */
  @Test
  public void testDouble() {
    assertComparesCorrectly(Comparators.forType(Types.DoubleType.get()), 0.1d, 0.2d);
  }

  /**
   * 测试场景：Date。
   *
   * <p>验证该方法在 Date 条件下的行为是否符合预期。
   */
  @Test
  public void testDate() {
    assertComparesCorrectly(Comparators.forType(Types.DateType.get()), 111, 222);
  }

  /**
   * 测试场景：Time。
   *
   * <p>验证该方法在 Time 条件下的行为是否符合预期。
   */
  @Test
  public void testTime() {
    assertComparesCorrectly(Comparators.forType(Types.TimeType.get()), 111, 222);
  }

  /**
   * 测试场景：Timestamp。
   *
   * <p>验证该方法在 Timestamp 条件下的行为是否符合预期。
   */
  @Test
  public void testTimestamp() {
    assertComparesCorrectly(Comparators.forType(Types.TimestampType.withoutZone()), 111, 222);
    assertComparesCorrectly(Comparators.forType(Types.TimestampType.withZone()), 111, 222);
  }

  /**
   * 测试场景：String。
   *
   * <p>验证该方法在 String 条件下的行为是否符合预期。
   */
  @Test
  public void testString() {
    assertComparesCorrectly(Comparators.forType(Types.StringType.get()), "a", "b");
  }

  /**
   * 测试场景：Uuid。
   *
   * <p>验证该方法在 Uuid 条件下的行为是否符合预期。
   */
  @Test
  public void testUuid() {
    assertComparesCorrectly(
        Comparators.forType(Types.UUIDType.get()),
        UUID.fromString("81873e7d-1374-4493-8e1d-9095eff7046c"),
        UUID.fromString("fd02441d-1423-4a3f-8785-c7dd5647e26b"));
  }

  /**
   * 测试场景：Fixed。
   *
   * <p>验证该方法在 Fixed 条件下的行为是否符合预期。
   */
  @Test
  public void testFixed() {
    assertComparesCorrectly(
        Comparators.forType(Types.FixedType.ofLength(3)),
        ByteBuffer.wrap(new byte[] {1, 1, 3}),
        ByteBuffer.wrap(new byte[] {1, 2, 1}));
  }

  /**
   * 测试场景：Binary。
   *
   * <p>验证该方法在 Binary 条件下的行为是否符合预期。
   */
  @Test
  public void testBinary() {
    assertComparesCorrectly(
        Comparators.forType(Types.BinaryType.get()),
        ByteBuffer.wrap(new byte[] {1, 1}),
        ByteBuffer.wrap(new byte[] {1, 1, 1}));
  }

  /**
   * 测试场景：Decimal。
   *
   * <p>验证该方法在 Decimal 条件下的行为是否符合预期。
   */
  @Test
  public void testDecimal() {
    assertComparesCorrectly(
        Comparators.forType(Types.DecimalType.of(5, 7)),
        BigDecimal.valueOf(0.1),
        BigDecimal.valueOf(0.2));
  }

  /**
   * 测试场景：List。
   *
   * <p>验证该方法在 List 条件下的行为是否符合预期。
   */
  @Test
  public void testList() {
    assertComparesCorrectly(
        Comparators.forType(Types.ListType.ofRequired(18, Types.IntegerType.get())),
        ImmutableList.of(1, 1, 1),
        ImmutableList.of(1, 1, 2));

    assertComparesCorrectly(
        Comparators.forType(Types.ListType.ofRequired(18, Types.IntegerType.get())),
        ImmutableList.of(1, 1),
        ImmutableList.of(1, 1, 1));

    assertComparesCorrectly(
        Comparators.forType(Types.ListType.ofOptional(18, Types.IntegerType.get())),
        Collections.singletonList(null),
        Collections.singletonList(1));
  }

  /**
   * 测试场景：Struct。
   *
   * <p>验证该方法在 Struct 条件下的行为是否符合预期。
   */
  @Test
  public void testStruct() {
    assertComparesCorrectly(
        Comparators.forType(
            Types.StructType.of(
                Types.NestedField.required(18, "str19", Types.StringType.get()),
                Types.NestedField.required(19, "int19", Types.IntegerType.get()))),
        TestHelpers.Row.of("a", 1),
        TestHelpers.Row.of("a", 2));

    assertComparesCorrectly(
        Comparators.forType(
            Types.StructType.of(Types.NestedField.optional(18, "str19", Types.StringType.get()))),
        TestHelpers.Row.of((String) null),
        TestHelpers.Row.of("a"));
  }
}
