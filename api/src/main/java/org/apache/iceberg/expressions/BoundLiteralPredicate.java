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

import java.util.Comparator;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;

/**
 * 已绑定字面量谓词：携带单个 {@link Literal} 字面量，对字段值与字面量做比较判定
 * （LT/LT_EQ/GT/GT_EQ/EQ/NOT_EQ/STARTS_WITH/NOT_STARTS_WITH）。
 *
 * <p>所属模块：iceberg-api（表达式体系“已绑定谓词”的字面量分支，继承 {@link BoundPredicate}， 与 {@link
 * BoundUnaryPredicate}、{@link BoundSetPredicate} 并列）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link #test(Object)}：用字面量比较器对值与字面量做大小/相等/前缀判定。
 *   <li>提供 {@link #negate()}：返回取反操作的字面量谓词。
 *   <li>实现 {@link #isEquivalentTo(Expression)}：除直接等价外，还识别整型域上 {@code < 6 ≡ <= 5}、{@code > 5 ≡ >= 6}
 *       等跨操作等价。
 * </ul>
 *
 * <p>设计意图：不支持 IN/NOT_IN（集合判定由 {@link BoundSetPredicate} 承担），构造时校验； STARTS_WITH
 * 直接基于字符串前缀匹配，复用本类以避免额外类型。比较统一走 {@link Literal#comparator()}，保证不同类型（如 CharSequence）比较语义一致。
 *
 * <p>上下游关系：由 {@link UnboundPredicate#bind} 在绑定带值谓词（含 IN 单值化简）时构造； 被 {@link Evaluator}、{@link
 * InclusiveMetricsEvaluator}、{@link ResidualEvaluator} 等遍历求值。
 *
 * @param <T> 谓词所作用字段的 Java 类型
 */
public class BoundLiteralPredicate<T> extends BoundPredicate<T> {
  private static final Set<Type.TypeID> INTEGRAL_TYPES =
      Sets.newHashSet(
          Type.TypeID.INTEGER,
          Type.TypeID.LONG,
          Type.TypeID.DATE,
          Type.TypeID.TIME,
          Type.TypeID.TIMESTAMP);

  /** 将字面量值转为 long（用于整型域跨操作等价判定）。 */
  private static long toLong(Literal<?> lit) {
    return ((Number) lit.value()).longValue();
  }

  private final Literal<T> literal;

  BoundLiteralPredicate(Operation op, BoundTerm<T> term, Literal<T> lit) {
    super(op, term);
    Preconditions.checkArgument(
        op != Operation.IN && op != Operation.NOT_IN,
        "Bound literal predicate does not support operation: %s",
        op);
    this.literal = lit;
  }

  /**
   * 返回本字面量谓词的否定（取反操作，字面量不变）。
   *
   * @return 操作取反后的新 {@link BoundLiteralPredicate}
   */
  @Override
  public Expression negate() {
    return new BoundLiteralPredicate<>(op().negate(), term(), literal);
  }

  /** 返回本谓词携带的字面量。 */
  public Literal<T> literal() {
    return literal;
  }

  /** 本谓词是字面量谓词，返回 true。 */
  @Override
  public boolean isLiteralPredicate() {
    return true;
  }

  /** 以字面量谓词视图返回自身。 */
  @Override
  public BoundLiteralPredicate<T> asLiteralPredicate() {
    return this;
  }

  /**
   * 对单个值执行字面量比较判定。
   *
   * <p>逻辑：取字面量比较器，按操作类型分发——LT/LT_EQ/GT/GT_EQ/EQ/NOT_EQ 走 {@code
   * cmp.compare}；STARTS_WITH/NOT_STARTS_WITH 基于字符串前缀匹配。
   *
   * @param value 待判定的值
   * @return 判定成立返回 true
   */
  @Override
  public boolean test(T value) {
    Comparator<T> cmp = literal.comparator();
    switch (op()) {
      case LT:
        return cmp.compare(value, literal.value()) < 0;
      case LT_EQ:
        return cmp.compare(value, literal.value()) <= 0;
      case GT:
        return cmp.compare(value, literal.value()) > 0;
      case GT_EQ:
        return cmp.compare(value, literal.value()) >= 0;
      case EQ:
        return cmp.compare(value, literal.value()) == 0;
      case NOT_EQ:
        return cmp.compare(value, literal.value()) != 0;
      case STARTS_WITH:
        return String.valueOf(value).startsWith((String) literal.value());
      case NOT_STARTS_WITH:
        return !String.valueOf(value).startsWith((String) literal.value());
      default:
        throw new IllegalStateException("Invalid operation for BoundLiteralPredicate: " + op());
    }
  }

  /**
   * 判断本字面量谓词是否与另一表达式等价。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>操作相同：term 等价且字面量值相等即等价。
   *   <li>操作不同但同为字面量谓词：在整型域（INT/LONG/DATE/TIME/TIMESTAMP）上识别 {@code < N ≡ <= N-1}、{@code <= N ≡ <
   *       N+1}、{@code > N ≡ >= N+1}、 {@code >= N ≡ > N-1} 等跨操作等价。
   * </ul>
   *
   * @param expr 另一表达式
   * @return 等价返回 true
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean isEquivalentTo(Expression expr) {
    if (op() == expr.op()) {
      BoundLiteralPredicate<?> other = (BoundLiteralPredicate<?>) expr;
      if (term().isEquivalentTo(other.term())) {
        // because the term is equivalent, the literal must have the same type, T
        Literal<T> otherLiteral = (Literal<T>) other.literal();
        Comparator<T> cmp = literal.comparator();
        return cmp.compare(literal.value(), otherLiteral.value()) == 0;
      }

    } else if (expr instanceof BoundLiteralPredicate) {
      BoundLiteralPredicate<?> other = (BoundLiteralPredicate<?>) expr;
      if (INTEGRAL_TYPES.contains(term().type().typeId()) && term().isEquivalentTo(other.term())) {
        switch (op()) {
          case LT:
            if (other.op() == Operation.LT_EQ) {
              // < 6 is equivalent to <= 5
              return toLong(literal()) == toLong(other.literal()) + 1L;
            }
            break;
          case LT_EQ:
            if (other.op() == Operation.LT) {
              // <= 5 is equivalent to < 6
              return toLong(literal()) == toLong(other.literal()) - 1L;
            }
            break;
          case GT:
            if (other.op() == Operation.GT_EQ) {
              // > 5 is equivalent to >= 6
              return toLong(literal()) == toLong(other.literal()) - 1L;
            }
            break;
          case GT_EQ:
            if (other.op() == Operation.GT) {
              // >= 5 is equivalent to > 4
              return toLong(literal()) == toLong(other.literal()) + 1L;
            }
            break;
        }
      }
    }

    return false;
  }

  @Override
  public String toString() {
    switch (op()) {
      case LT:
        return term() + " < " + literal;
      case LT_EQ:
        return term() + " <= " + literal;
      case GT:
        return term() + " > " + literal;
      case GT_EQ:
        return term() + " >= " + literal;
      case EQ:
        return term() + " == " + literal;
      case NOT_EQ:
        return term() + " != " + literal;
      case STARTS_WITH:
        return term() + " startsWith \"" + literal + "\"";
      case NOT_STARTS_WITH:
        return term() + " notStartsWith \"" + literal + "\"";
      case IN:
        return term() + " in { " + literal + " }";
      case NOT_IN:
        return term() + " not in { " + literal + " }";
      default:
        return "Invalid literal predicate: operation = " + op();
    }
  }
}
