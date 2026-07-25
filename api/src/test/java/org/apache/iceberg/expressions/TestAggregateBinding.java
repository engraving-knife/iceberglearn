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

import java.util.List;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestAggregateBinding 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestAggregateBinding 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestAggregateBinding {
  private static final List<UnboundAggregate<Integer>> list =
      ImmutableList.of(Expressions.count("x"), Expressions.max("x"), Expressions.min("x"));
  private static final StructType struct =
      StructType.of(Types.NestedField.required(10, "x", Types.IntegerType.get()));

  /**
   * 测试场景：Aggregate Binding。
   *
   * <p>验证该方法在 Aggregate Binding 条件下的行为是否符合预期。
   */
  @Test
  public void testAggregateBinding() {
    for (UnboundAggregate<Integer> unbound : list) {
      Expression expr = unbound.bind(struct, true);
      BoundAggregate<Integer, ?> bound = assertAndUnwrapAggregate(expr);
      assertThat(bound.ref().fieldId()).as("Should reference correct field ID").isEqualTo(10);
      assertThat(bound.op())
          .as("Should not change the comparison operation")
          .isEqualTo(unbound.op());
    }
  }

  /**
   * 测试场景：Count Star Binding。
   *
   * <p>验证该方法在 Count Star Binding 条件下的行为是否符合预期。
   */
  @Test
  public void testCountStarBinding() {
    UnboundAggregate<?> unbound = Expressions.countStar();
    Expression expr = unbound.bind(null, false);
    BoundAggregate<?, Long> bound = assertAndUnwrapAggregate(expr);

    assertThat(bound.op())
        .as("Should not change the comparison operation")
        .isEqualTo(Expression.Operation.COUNT_STAR);
  }

  /**
   * 测试场景：Bound Aggregate Fails。
   *
   * <p>验证该方法在 Bound Aggregate Fails 条件下的行为是否符合预期。
   */
  @Test
  public void testBoundAggregateFails() {
    Expression unbound = Expressions.count("x");
    Assertions.assertThatThrownBy(() -> Binder.bind(struct, Binder.bind(struct, unbound)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Found already bound aggregate");
  }

  /**
   * 测试场景：Case Insensitive Reference。
   *
   * <p>验证该方法在 Case Insensitive Reference 条件下的行为是否符合预期。
   */
  @Test
  public void testCaseInsensitiveReference() {
    Expression expr = Expressions.max("X");
    Expression boundExpr = Binder.bind(struct, expr, false);
    BoundAggregate<Integer, Integer> bound = assertAndUnwrapAggregate(boundExpr);
    assertThat(bound.ref().fieldId()).as("Should reference correct field ID").isEqualTo(10);
    assertThat(bound.op())
        .as("Should not change the comparison operation")
        .isEqualTo(Expression.Operation.MAX);
  }

  /**
   * 测试场景：Case Sensitive Reference。
   *
   * <p>验证该方法在 Case Sensitive Reference 条件下的行为是否符合预期。
   */
  @Test
  public void testCaseSensitiveReference() {
    Expression expr = Expressions.max("X");
    Assertions.assertThatThrownBy(() -> Binder.bind(struct, expr, true))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cannot find field 'X' in struct");
  }

  /**
   * 测试场景：Missing Field。
   *
   * <p>验证该方法在 Missing Field 条件下的行为是否符合预期。
   */
  @Test
  public void testMissingField() {
    UnboundAggregate<?> unbound = Expressions.count("missing");
    Assertions.assertThatThrownBy(() -> unbound.bind(struct, false))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cannot find field 'missing' in struct:");
  }

  /** 辅助方法：assertAndUnwrapAggregate。 */
  private static <T, C> BoundAggregate<T, C> assertAndUnwrapAggregate(Expression expr) {
    Assertions.assertThat(expr).isInstanceOf(BoundAggregate.class);
    return (BoundAggregate<T, C>) expr;
  }
}
