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
import static org.assertj.core.data.Offset.offset;

import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestNumericLiteralConversions 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestNumericLiteralConversions 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestNumericLiteralConversions {
  /**
   * 测试场景：Integer To Long Conversion。
   *
   * <p>验证该方法在 Integer To Long Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testIntegerToLongConversion() {
    Literal<Integer> lit = Literal.of(34);
    Literal<Long> longLit = lit.to(Types.LongType.get());

    assertThat((long) longLit.value()).isEqualTo(34L);
  }

  /**
   * 测试场景：Integer To Float Conversion。
   *
   * <p>验证该方法在 Integer To Float Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testIntegerToFloatConversion() {
    Literal<Integer> lit = Literal.of(34);
    Literal<Float> floatLit = lit.to(Types.FloatType.get());

    assertThat(floatLit.value()).isCloseTo(34.0F, offset(0.0000000001F));
  }

  /**
   * 测试场景：Integer To Double Conversion。
   *
   * <p>验证该方法在 Integer To Double Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testIntegerToDoubleConversion() {
    Literal<Integer> lit = Literal.of(34);
    Literal<Double> doubleLit = lit.to(Types.DoubleType.get());

    assertThat(doubleLit.value()).isCloseTo(34.0D, offset(0.0000000001D));
  }

  /**
   * 测试场景：Integer To Decimal Conversion。
   *
   * <p>验证该方法在 Integer To Decimal Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testIntegerToDecimalConversion() {
    Literal<Integer> lit = Literal.of(34);

    assertThat(lit.to(Types.DecimalType.of(9, 0)).value()).isEqualTo(new BigDecimal("34"));
    assertThat(lit.to(Types.DecimalType.of(9, 2)).value()).isEqualTo(new BigDecimal("34.00"));
    assertThat(lit.to(Types.DecimalType.of(9, 4)).value()).isEqualTo(new BigDecimal("34.0000"));
  }

  /**
   * 测试场景：Long To Integer Conversion。
   *
   * <p>验证该方法在 Long To Integer Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testLongToIntegerConversion() {
    Literal<Long> lit = Literal.of(34L);
    Literal<Integer> intLit = lit.to(Types.IntegerType.get());

    assertThat((int) intLit.value()).isEqualTo(34);

    assertThat(Literal.of((long) Integer.MAX_VALUE + 1L).to(Types.IntegerType.get()))
        .as("Values above Integer.MAX_VALUE should be Literals.aboveMax()")
        .isEqualTo(Literals.aboveMax());
    assertThat(Literal.of((long) Integer.MIN_VALUE - 1L).to(Types.IntegerType.get()))
        .as("Values below Integer.MIN_VALUE should be Literals.belowMin()")
        .isEqualTo(Literals.belowMin());
  }

  /**
   * 测试场景：Long To Float Conversion。
   *
   * <p>验证该方法在 Long To Float Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testLongToFloatConversion() {
    Literal<Long> lit = Literal.of(34L);
    Literal<Float> floatLit = lit.to(Types.FloatType.get());

    assertThat(floatLit.value()).isCloseTo(34.0F, offset(0.0000000001F));
  }

  /**
   * 测试场景：Long To Double Conversion。
   *
   * <p>验证该方法在 Long To Double Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testLongToDoubleConversion() {
    Literal<Long> lit = Literal.of(34L);
    Literal<Double> doubleLit = lit.to(Types.DoubleType.get());

    assertThat(doubleLit.value()).isCloseTo(34.0D, offset(0.0000000001D));
  }

  /**
   * 测试场景：Long To Decimal Conversion。
   *
   * <p>验证该方法在 Long To Decimal Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testLongToDecimalConversion() {
    Literal<Long> lit = Literal.of(34L);

    assertThat(lit.to(Types.DecimalType.of(9, 0)).value()).isEqualTo(new BigDecimal("34"));
    assertThat(lit.to(Types.DecimalType.of(9, 2)).value()).isEqualTo(new BigDecimal("34.00"));
    assertThat(lit.to(Types.DecimalType.of(9, 4)).value()).isEqualTo(new BigDecimal("34.0000"));
  }

  /**
   * 测试场景：Float To Double Conversion。
   *
   * <p>验证该方法在 Float To Double Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testFloatToDoubleConversion() {
    Literal<Float> lit = Literal.of(34.56F);
    Literal<Double> doubleLit = lit.to(Types.DoubleType.get());

    assertThat(doubleLit.value()).isCloseTo(34.56D, offset(0.001D));
  }

  /**
   * 测试场景：Float To Decimal Conversion。
   *
   * <p>验证该方法在 Float To Decimal Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testFloatToDecimalConversion() {
    Literal<Float> lit = Literal.of(34.56F);

    assertThat(lit.to(Types.DecimalType.of(9, 1)).value())
        .as("Value should round using HALF_UP")
        .isEqualTo(new BigDecimal("34.6"));
    assertThat(lit.to(Types.DecimalType.of(9, 2)).value()).isEqualTo(new BigDecimal("34.56"));
    assertThat(lit.to(Types.DecimalType.of(9, 4)).value()).isEqualTo(new BigDecimal("34.5600"));
  }

  /**
   * 测试场景：Double To Float Conversion。
   *
   * <p>验证该方法在 Double To Float Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testDoubleToFloatConversion() {
    Literal<Double> lit = Literal.of(34.56D);
    Literal<Float> doubleLit = lit.to(Types.FloatType.get());

    assertThat(doubleLit.value()).isCloseTo(34.56F, offset(0.001F));

    // this adjusts Float.MAX_VALUE using multipliers because most integer adjustments are lost by
    // floating point precision.
    assertThat(Literal.of(2 * ((double) Float.MAX_VALUE)).to(Types.FloatType.get()))
        .as("Values above Float.MAX_VALUE should be Literals.aboveMax()")
        .isEqualTo(Literals.aboveMax());
    assertThat(Literal.of(-2 * ((double) Float.MAX_VALUE)).to(Types.FloatType.get()))
        .as("Values below Float.MIN_VALUE should be Literals.belowMin()")
        .isEqualTo(Literals.belowMin());
  }

  /**
   * 测试场景：Double To Decimal Conversion。
   *
   * <p>验证该方法在 Double To Decimal Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testDoubleToDecimalConversion() {
    Literal<Double> lit = Literal.of(34.56D);

    assertThat(lit.to(Types.DecimalType.of(9, 1)).value())
        .as("Value should round using HALF_UP")
        .isEqualTo(new BigDecimal("34.6"));
    assertThat(lit.to(Types.DecimalType.of(9, 2)).value()).isEqualTo(new BigDecimal("34.56"));
    assertThat(lit.to(Types.DecimalType.of(9, 4)).value()).isEqualTo(new BigDecimal("34.5600"));
  }

  /**
   * 测试场景：Decimal To Decimal Conversion。
   *
   * <p>验证该方法在 Decimal To Decimal Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testDecimalToDecimalConversion() {
    Literal<BigDecimal> lit = Literal.of(new BigDecimal("34.11"));

    IntStream.range(0, 10)
        .forEach(
            scale -> {
              assertThat(lit.to(Types.DecimalType.of(9, scale)))
                  .as("Should return identical object")
                  .isSameAs(lit);
              assertThat(lit.to(Types.DecimalType.of(11, scale)))
                  .as("Should return identical object")
                  .isSameAs(lit);
            });
  }

  /**
   * 测试场景：Integer To Date Conversion。
   *
   * <p>验证该方法在 Integer To Date Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testIntegerToDateConversion() {
    Literal<Integer> lit = Literal.of(0);
    assertThat(new Literals.DateLiteral(0)).isEqualTo(lit.to(Types.DateType.get()));
    lit = Literal.of(365 * 50);
    assertThat(new Literals.DateLiteral(365 * 50)).isEqualTo(lit.to(Types.DateType.get()));
  }

  /**
   * 测试场景：Long To Date Conversion。
   *
   * <p>验证该方法在 Long To Date Conversion 条件下的行为是否符合预期。
   */
  @Test
  public void testLongToDateConversion() {
    Literal<Long> lit = Literal.of(0L);
    assertThat(new Literals.DateLiteral(0)).isEqualTo(lit.to(Types.DateType.get()));
    lit = Literal.of(365L * 50);
    assertThat(new Literals.DateLiteral(365 * 50)).isEqualTo(lit.to(Types.DateType.get()));
  }
}
