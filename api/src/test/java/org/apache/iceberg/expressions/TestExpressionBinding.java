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

import static org.apache.iceberg.expressions.Expressions.alwaysFalse;
import static org.apache.iceberg.expressions.Expressions.alwaysTrue;
import static org.apache.iceberg.expressions.Expressions.and;
import static org.apache.iceberg.expressions.Expressions.bucket;
import static org.apache.iceberg.expressions.Expressions.equal;
import static org.apache.iceberg.expressions.Expressions.greaterThan;
import static org.apache.iceberg.expressions.Expressions.lessThan;
import static org.apache.iceberg.expressions.Expressions.not;
import static org.apache.iceberg.expressions.Expressions.notStartsWith;
import static org.apache.iceberg.expressions.Expressions.or;
import static org.apache.iceberg.expressions.Expressions.startsWith;
import static org.apache.iceberg.types.Types.NestedField.required;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestExpressionBinding 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestExpressionBinding 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestExpressionBinding {
  private static final StructType STRUCT =
      StructType.of(
          required(0, "x", Types.IntegerType.get()),
          required(1, "y", Types.IntegerType.get()),
          required(2, "z", Types.IntegerType.get()),
          required(3, "data", Types.StringType.get()));

  /**
   * 测试场景：Missing Reference。
   *
   * <p>验证该方法在 Missing Reference 条件下的行为是否符合预期。
   */
  @Test
  public void testMissingReference() {
    Expression expr = and(equal("t", 5), equal("x", 7));
    Assertions.assertThatThrownBy(() -> Binder.bind(STRUCT, expr))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cannot find field 't' in struct");
  }

  /**
   * 测试场景：Bound Expression Fails。
   *
   * <p>验证该方法在 Bound Expression Fails 条件下的行为是否符合预期。
   */
  @Test
  public void testBoundExpressionFails() {
    Expression expr = not(equal("x", 7));
    Assertions.assertThatThrownBy(() -> Binder.bind(STRUCT, Binder.bind(STRUCT, expr)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Found already bound predicate");
  }

  /**
   * 测试场景：Single Reference。
   *
   * <p>验证该方法在 Single Reference 条件下的行为是否符合预期。
   */
  @Test
  public void testSingleReference() {
    Expression expr = not(equal("x", 7));
    TestHelpers.assertAllReferencesBound("Single reference", Binder.bind(STRUCT, expr, true));
  }

  /**
   * 测试场景：Case Insensitive Reference。
   *
   * <p>验证该方法在 Case Insensitive Reference 条件下的行为是否符合预期。
   */
  @Test
  public void testCaseInsensitiveReference() {
    Expression expr = not(equal("X", 7));
    TestHelpers.assertAllReferencesBound("Single reference", Binder.bind(STRUCT, expr, false));
  }

  /**
   * 测试场景：Case Sensitive Reference。
   *
   * <p>验证该方法在 Case Sensitive Reference 条件下的行为是否符合预期。
   */
  @Test
  public void testCaseSensitiveReference() {
    Expression expr = not(equal("X", 7));
    Assertions.assertThatThrownBy(() -> Binder.bind(STRUCT, expr, true))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cannot find field 'X' in struct");
  }

  /**
   * 测试场景：Multiple References。
   *
   * <p>验证该方法在 Multiple References 条件下的行为是否符合预期。
   */
  @Test
  public void testMultipleReferences() {
    Expression expr = or(and(equal("x", 7), lessThan("y", 100)), greaterThan("z", -100));
    TestHelpers.assertAllReferencesBound("Multiple references", Binder.bind(STRUCT, expr));
  }

  /**
   * 测试场景：And。
   *
   * <p>验证该方法在 And 条件下的行为是否符合预期。
   */
  @Test
  public void testAnd() {
    Expression expr = and(equal("x", 7), lessThan("y", 100));
    Expression boundExpr = Binder.bind(STRUCT, expr);
    TestHelpers.assertAllReferencesBound("And", boundExpr);

    // make sure the result is an And
    And and = TestHelpers.assertAndUnwrap(boundExpr, And.class);

    // make sure the refs are for the right fields
    BoundPredicate<?> left = TestHelpers.assertAndUnwrap(and.left());
    assertThat(left.term().ref().fieldId()).as("Should bind x correctly").isZero();
    BoundPredicate<?> right = TestHelpers.assertAndUnwrap(and.right());
    assertThat(right.term().ref().fieldId()).as("Should bind y correctly").isOne();
  }

  /**
   * 测试场景：Or。
   *
   * <p>验证该方法在 Or 条件下的行为是否符合预期。
   */
  @Test
  public void testOr() {
    Expression expr = or(greaterThan("z", -100), lessThan("y", 100));
    Expression boundExpr = Binder.bind(STRUCT, expr);
    TestHelpers.assertAllReferencesBound("Or", boundExpr);

    // make sure the result is an Or
    Or or = TestHelpers.assertAndUnwrap(boundExpr, Or.class);

    // make sure the refs are for the right fields
    BoundPredicate<?> left = TestHelpers.assertAndUnwrap(or.left());
    assertThat(left.term().ref().fieldId()).as("Should bind z correctly").isEqualTo(2);
    BoundPredicate<?> right = TestHelpers.assertAndUnwrap(or.right());
    assertThat(right.term().ref().fieldId()).as("Should bind y correctly").isOne();
  }

  /**
   * 测试场景：Not。
   *
   * <p>验证该方法在 Not 条件下的行为是否符合预期。
   */
  @Test
  public void testNot() {
    Expression expr = not(equal("x", 7));
    Expression boundExpr = Binder.bind(STRUCT, expr);
    TestHelpers.assertAllReferencesBound("Not", boundExpr);

    // make sure the result is a Not
    Not not = TestHelpers.assertAndUnwrap(boundExpr, Not.class);

    // make sure the refs are for the right fields
    BoundPredicate<?> child = TestHelpers.assertAndUnwrap(not.child());
    assertThat(child.term().ref().fieldId()).as("Should bind x correctly").isZero();
  }

  /**
   * 测试场景：Starts With。
   *
   * <p>验证该方法在 Starts With 条件下的行为是否符合预期。
   */
  @Test
  public void testStartsWith() {
    StructType struct = StructType.of(required(0, "s", Types.StringType.get()));
    Expression expr = startsWith("s", "abc");
    Expression boundExpr = Binder.bind(struct, expr);
    TestHelpers.assertAllReferencesBound("StartsWith", boundExpr);
    // make sure the expression is a StartsWith
    BoundPredicate<?> pred = TestHelpers.assertAndUnwrap(boundExpr, BoundPredicate.class);
    assertThat(pred.op())
        .as("Should be right operation")
        .isEqualTo(Expression.Operation.STARTS_WITH);
    assertThat(pred.term().ref().fieldId()).as("Should bind s correctly").isZero();
  }

  /**
   * 测试场景：Not Starts With。
   *
   * <p>验证该方法在 Not Starts With 条件下的行为是否符合预期。
   */
  @Test
  public void testNotStartsWith() {
    StructType struct = StructType.of(required(21, "s", Types.StringType.get()));
    Expression expr = notStartsWith("s", "abc");
    Expression boundExpr = Binder.bind(struct, expr);
    TestHelpers.assertAllReferencesBound("NotStartsWith", boundExpr);
    // Make sure the expression is a NotStartsWith
    BoundPredicate<?> pred = TestHelpers.assertAndUnwrap(boundExpr, BoundPredicate.class);
    assertThat(pred.op())
        .as("Should be right operation")
        .isEqualTo(Expression.Operation.NOT_STARTS_WITH);
    assertThat(pred.term().ref().fieldId())
        .as("Should bind term to correct field id")
        .isEqualTo(21);
  }

  /**
   * 测试场景：Always True。
   *
   * <p>验证该方法在 Always True 条件下的行为是否符合预期。
   */
  @Test
  public void testAlwaysTrue() {
    assertThat(Binder.bind(STRUCT, alwaysTrue()))
        .as("Should not change alwaysTrue")
        .isEqualTo(alwaysTrue());
  }

  /**
   * 测试场景：Always False。
   *
   * <p>验证该方法在 Always False 条件下的行为是否符合预期。
   */
  @Test
  public void testAlwaysFalse() {
    assertThat(Binder.bind(STRUCT, alwaysFalse()))
        .as("Should not change alwaysFalse")
        .isEqualTo(alwaysFalse());
  }

  /**
   * 测试场景：Basic Simplification。
   *
   * <p>验证该方法在 Basic Simplification 条件下的行为是否符合预期。
   */
  @Test
  public void testBasicSimplification() {
    // this tests that a basic simplification is done by calling the helpers in Expressions. those
    // are more thoroughly tested in TestExpressionHelpers.

    // the second predicate is always true once it is bound because z is an integer and the literal
    // is less than any 32-bit integer value
    assertThat(Binder.bind(STRUCT, or(lessThan("y", 100), greaterThan("z", -9999999999L))))
        .as("Should simplify or expression to alwaysTrue")
        .isEqualTo(alwaysTrue());
    // similarly, the second predicate is always false
    assertThat(Binder.bind(STRUCT, and(lessThan("y", 100), lessThan("z", -9999999999L))))
        .as("Should simplify and expression to predicate")
        .isEqualTo(alwaysFalse());

    Expression bound = Binder.bind(STRUCT, not(not(lessThan("y", 100))));
    BoundPredicate<?> pred = TestHelpers.assertAndUnwrap(bound);
    assertThat(pred.term().ref().fieldId()).as("Should have the correct bound field").isOne();
  }

  /**
   * 测试场景：Transform Expression Binding。
   *
   * <p>验证该方法在 Transform Expression Binding 条件下的行为是否符合预期。
   */
  @Test
  public void testTransformExpressionBinding() {
    Expression bound = Binder.bind(STRUCT, equal(bucket("x", 16), 10));
    TestHelpers.assertAllReferencesBound("BoundTransform", bound);
    BoundPredicate<?> pred = TestHelpers.assertAndUnwrap(bound);
    Assertions.assertThat(pred.term())
        .as("Should use a BoundTransform child")
        .isInstanceOf(BoundTransform.class);
    BoundTransform<?, ?> transformExpr = (BoundTransform<?, ?>) pred.term();
    assertThat(transformExpr.transform())
        .as("Should use a bucket[16] transform")
        .hasToString("bucket[16]");
  }
}
