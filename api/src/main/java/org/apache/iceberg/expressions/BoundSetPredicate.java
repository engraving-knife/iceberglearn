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

import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 已绑定集合谓词：用于 IN / NOT_IN 操作，把字段值与一组字面量集合做包含判定。
 *
 * <p>所属模块：iceberg-api（谓词分支的具体实现之一，对应 {@link Operation#IN} 与 {@link Operation#NOT_IN}）。
 *
 * <p>职责：在绑定阶段把用户传入的集合字面量收敛为 {@link Set}，并在求值时按操作类型 判定字段值是否属于该集合。
 *
 * <p>设计意图：集合谓词单独成类，使访问者可经 {@code isSetPredicate}/{@code asSetPredicate}
 * 精准分派，避免与字面量谓词耦合。绑定阶段会把单元素集合简化为字面量谓词，因此本类 只承接“真正多元素集合”的情形。
 *
 * <p>上下游关系：由 {@link Binder} 绑定 {@link UnboundPredicate} 得到；被各类 Evaluator 与 {@link
 * ExpressionVisitors} 访问消费。
 *
 * @param <T> 谓词输入值的 Java 类型
 */
public class BoundSetPredicate<T> extends BoundPredicate<T> {
  private static final Joiner COMMA = Joiner.on(", ");
  private final Set<T> literalSet;

  /**
   * 构造集合谓词。
   *
   * <p>逻辑：校验操作类型只能是 {@link Operation#IN} 或 {@link Operation#NOT_IN}， 否则抛 {@link
   * IllegalArgumentException}。
   *
   * @param op 操作类型，必须为 IN 或 NOT_IN
   * @param term 已绑定 term
   * @param lits 字面量集合
   */
  BoundSetPredicate(Operation op, BoundTerm<T> term, Set<T> lits) {
    super(op, term);
    Preconditions.checkArgument(
        op == Operation.IN || op == Operation.NOT_IN,
        "%s predicate does not support a literal set",
        op);
    this.literalSet = lits;
  }

  /**
   * 返回此谓词的否定形式（IN ↔ NOT_IN，集合不变）。
   *
   * @return 否定后的表达式
   */
  @Override
  public Expression negate() {
    return new BoundSetPredicate<>(op().negate(), term(), literalSet);
  }

  /** 集合谓词恒返回 true。 */
  @Override
  public boolean isSetPredicate() {
    return true;
  }

  /** 直接返回 this。 */
  @Override
  public BoundSetPredicate<T> asSetPredicate() {
    return this;
  }

  /**
   * 返回此谓词持有的字面量集合。
   *
   * @return 字面量集合
   */
  public Set<T> literalSet() {
    return literalSet;
  }

  /**
   * 判定给定值是否满足本集合谓词。
   *
   * <p>逻辑：IN 返回集合包含结果；NOT_IN 返回集合不包含结果；其他操作非法抛异常。
   *
   * @param value 待判定的值
   * @return 满足谓词返回 true
   */
  @Override
  public boolean test(T value) {
    switch (op()) {
      case IN:
        return literalSet.contains(value);
      case NOT_IN:
        return !literalSet.contains(value);
      default:
        throw new IllegalStateException("Invalid operation for BoundSetPredicate: " + op());
    }
  }

  /**
   * 判定本谓词是否与另一表达式语义等价。
   *
   * <p>逻辑：仅当对方同为 BoundSetPredicate 且操作类型相同、字面量集合相等时返回 true。 单元素集合在绑定时已转为字面量谓词，故此处只需比较
   * BoundSetPredicate。
   *
   * @param other 另一表达式
   * @return 语义等价返回 true
   */
  @Override
  public boolean isEquivalentTo(Expression other) {
    // only check bound set predicate; binding will convert sets of a single item to a literal
    // predicate
    if (op() == other.op()) {
      BoundSetPredicate<?> pred = (BoundSetPredicate<?>) other;
      return literalSet().equals(pred.literalSet());
    }

    return false;
  }

  /**
   * 返回可读字符串表示，如 "ref in (a, b)" 或 "ref not in (a, b)"。
   *
   * @return 可读字符串
   */
  @Override
  public String toString() {
    switch (op()) {
      case IN:
        return term() + " in (" + COMMA.join(literalSet) + ")";
      case NOT_IN:
        return term() + " not in (" + COMMA.join(literalSet) + ")";
      default:
        return "Invalid unary predicate: operation = " + op();
    }
  }
}
