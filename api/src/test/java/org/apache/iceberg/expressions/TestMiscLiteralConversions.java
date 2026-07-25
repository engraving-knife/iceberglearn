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
package org.apache.iceberg.expressions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestMiscLiteralConversions 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestMiscLiteralConversions 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestMiscLiteralConversions {
  /**
   * 测试场景：Identity Conversions。
   *
   * <p>验证该方法在 Identity Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testIdentityConversions() {
    List<Pair<Literal<?>, Type>> pairs =
        Arrays.asList(
            Pair.of(Literal.of(true), Types.BooleanType.get()),
            Pair.of(Literal.of(34), Types.IntegerType.get()),
            Pair.of(Literal.of(34L), Types.LongType.get()),
            Pair.of(Literal.of(34.11F), Types.FloatType.get()),
            Pair.of(Literal.of(34.55D), Types.DoubleType.get()),
            Pair.of(Literal.of("34.55"), Types.DecimalType.of(9, 2)),
            Pair.of(Literal.of("2017-08-18"), Types.DateType.get()),
            Pair.of(Literal.of("14:21:01.919"), Types.TimeType.get()),
            Pair.of(Literal.of("2017-08-18T14:21:01.919"), Types.TimestampType.withoutZone()),
            Pair.of(Literal.of("abc"), Types.StringType.get()),
            Pair.of(Literal.of(UUID.randomUUID()), Types.UUIDType.get()),
            Pair.of(Literal.of(new byte[] {0, 1, 2}), Types.FixedType.ofLength(3)),
            Pair.of(Literal.of(ByteBuffer.wrap(new byte[] {0, 1, 2})), Types.BinaryType.get()));

    for (Pair<Literal<?>, Type> pair : pairs) {
      Literal<?> lit = pair.first();
      Type type = pair.second();

      // first, convert the literal to the target type (date/times start as strings)
      Literal<?> expected = lit.to(type);

      // then check that converting again to the same type results in an identical literal
      assertThat(expected.to(type))
          .as("Converting twice should produce identical values")
          .isSameAs(expected);
    }
  }

  /**
   * 测试场景：Binary To Fixed。
   *
   * <p>验证该方法在 Binary To Fixed 条件下的行为是否符合预期。
   */
  @Test
  public void testBinaryToFixed() {
    Literal<ByteBuffer> lit = Literal.of(ByteBuffer.wrap(new byte[] {0, 1, 2}));
    Literal<ByteBuffer> fixedLit = lit.to(Types.FixedType.ofLength(3));
    assertThat(fixedLit).as("Should allow conversion to correct fixed length").isNotNull();
    assertThat(fixedLit.value().duplicate())
        .as("Conversion should not change value")
        .isEqualTo(lit.value().duplicate());

    assertThat(lit.to(Types.FixedType.ofLength(4)))
        .as("Should not allow conversion to different fixed length")
        .isNull();
    assertThat(lit.to(Types.FixedType.ofLength(2)))
        .as("Should not allow conversion to different fixed length")
        .isNull();
  }

  /**
   * 测试场景：Fixed To Binary。
   *
   * <p>验证该方法在 Fixed To Binary 条件下的行为是否符合预期。
   */
  @Test
  public void testFixedToBinary() {
    Literal<ByteBuffer> lit = Literal.of(new byte[] {0, 1, 2});
    Literal<ByteBuffer> binaryLit = lit.to(Types.BinaryType.get());
    assertThat(binaryLit).as("Should allow conversion to binary").isNotNull();
    assertThat(binaryLit.value().duplicate())
        .as("Conversion should not change value")
        .isEqualTo(lit.value().duplicate());
  }

  /**
   * 测试场景：Invalid Boolean Conversions。
   *
   * <p>验证该方法在 Invalid Boolean Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidBooleanConversions() {
    testInvalidConversions(
        Literal.of(true),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.DateType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.DecimalType.of(9, 2),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Integer Conversions。
   *
   * <p>验证该方法在 Invalid Integer Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidIntegerConversions() {
    testInvalidConversions(
        Literal.of(34),
        Types.BooleanType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Long Conversions。
   *
   * <p>验证该方法在 Invalid Long Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidLongConversions() {
    testInvalidConversions(
        Literal.of(34L),
        Types.BooleanType.get(),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Float Conversions。
   *
   * <p>验证该方法在 Invalid Float Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidFloatConversions() {
    testInvalidConversions(
        Literal.of(34.11F),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.DateType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Double Conversions。
   *
   * <p>验证该方法在 Invalid Double Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidDoubleConversions() {
    testInvalidConversions(
        Literal.of(34.11D),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.DateType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Date Conversions。
   *
   * <p>验证该方法在 Invalid Date Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidDateConversions() {
    testInvalidConversions(
        Literal.of("2017-08-18").to(Types.DateType.get()),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.DecimalType.of(9, 4),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Time Conversions。
   *
   * <p>验证该方法在 Invalid Time Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidTimeConversions() {
    testInvalidConversions(
        Literal.of("14:21:01.919").to(Types.TimeType.get()),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.DateType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.DecimalType.of(9, 4),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Timestamp Conversions。
   *
   * <p>验证该方法在 Invalid Timestamp Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidTimestampConversions() {
    testInvalidConversions(
        Literal.of("2017-08-18T14:21:01.919").to(Types.TimestampType.withoutZone()),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.TimeType.get(),
        Types.DecimalType.of(9, 4),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Decimal Conversions。
   *
   * <p>验证该方法在 Invalid Decimal Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidDecimalConversions() {
    testInvalidConversions(
        Literal.of(new BigDecimal("34.11")),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.DateType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid String Conversions。
   *
   * <p>验证该方法在 Invalid String Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidStringConversions() {
    // Strings can be used for types that are difficult to construct, like decimal or timestamp,
    // but are not intended to support parsing strings to any type
    testInvalidConversions(
        Literal.of("abc"),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid UUID Conversions。
   *
   * <p>验证该方法在 Invalid UUID Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidUUIDConversions() {
    testInvalidConversions(
        Literal.of(UUID.randomUUID()),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.DateType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.DecimalType.of(9, 2),
        Types.StringType.get(),
        Types.FixedType.ofLength(1),
        Types.BinaryType.get());
  }

  /**
   * 测试场景：Invalid Fixed Conversions。
   *
   * <p>验证该方法在 Invalid Fixed Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidFixedConversions() {
    testInvalidConversions(
        Literal.of(new byte[] {0, 1, 2}),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.DateType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.DecimalType.of(9, 2),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1));
  }

  /**
   * 测试场景：Invalid Binary Conversions。
   *
   * <p>验证该方法在 Invalid Binary Conversions 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidBinaryConversions() {
    testInvalidConversions(
        Literal.of(ByteBuffer.wrap(new byte[] {0, 1, 2})),
        Types.BooleanType.get(),
        Types.IntegerType.get(),
        Types.LongType.get(),
        Types.FloatType.get(),
        Types.DoubleType.get(),
        Types.DateType.get(),
        Types.TimeType.get(),
        Types.TimestampType.withZone(),
        Types.TimestampType.withoutZone(),
        Types.DecimalType.of(9, 2),
        Types.StringType.get(),
        Types.UUIDType.get(),
        Types.FixedType.ofLength(1));
  }

  /**
   * 测试场景：Invalid Conversions。
   *
   * <p>验证该方法在 Invalid Conversions 条件下的行为是否符合预期。
   */
  private void testInvalidConversions(Literal<?> lit, Type... invalidTypes) {
    for (Type type : invalidTypes) {
      assertThat(lit.to(type))
          .as(lit.value().getClass().getName() + " literal to " + type + " is not allowed")
          .isNull();
    }
  }

  private static class Pair<X, Y> {
    /** 辅助方法：of。 */
    public static <X, Y> Pair<X, Y> of(X first, Y second) {
      return new Pair<>(first, second);
    }

    private final X first;
    private final Y second;

    /** 辅助方法：Pair。 */
    private Pair(X first, Y second) {
      this.first = first;
      this.second = second;
    }

    /** 辅助方法：first。 */
    public X first() {
      return first;
    }

    /** 辅助方法：second。 */
    public Y second() {
      return second;
    }
  }
}
