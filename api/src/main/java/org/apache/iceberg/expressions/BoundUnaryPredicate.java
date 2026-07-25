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

import org.apache.iceberg.util.NaNUtil;

/**
 * 已绑定一元谓词：不携带字面量的谓词，仅对字段值本身做判定（IS_NULL / NOT_NULL / IS_NAN / NOT_NAN）。
 *
 * <p>所属模块：iceberg-api（表达式体系“已绑定谓词”的一元分支，继承 {@link BoundPredicate}， 与 {@link
 * BoundLiteralPredicate}、{@link BoundSetPredicate} 并列）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link #test(Object)}：按操作类型判定值是否为 null 或 NaN。
 *   <li>提供 {@link #negate()}：返回取反的一元谓词（IS_NULL &lt;-&gt; NOT_NULL 等）。
 *   <li>实现类型判定 {@link #isUnaryPredicate()} 与 {@link #asUnaryPredicate()} 供访问者分发。
 * </ul>
 *
 * <p>设计意图：把“不需要字面量”的判定单独成类，避免 {@link BoundLiteralPredicate} 处理 null 字面量的特殊情况；NaN 判定委托 {@link
 * NaNUtil} 统一处理浮点与 NaN 语义。
 *
 * <p>上下游关系：由 {@link UnboundPredicate#bind} 在绑定 IS_NULL/NOT_NULL/IS_NAN/NOT_NAN 时构造； 被 {@link
 * Evaluator}、{@link InclusiveMetricsEvaluator}、{@link ResidualEvaluator} 等遍历求值。
 *
 * @param <T> 谓词所作用字段的 Java 类型
 */
public class BoundUnaryPredicate<T> extends BoundPredicate<T> {
  BoundUnaryPredicate(Operation op, BoundTerm<T> term) {
    super(op, term);
  }

  /**
   * 返回本一元谓词的否定（取反操作）。
   *
   * @return 操作取反后的新 {@link BoundUnaryPredicate}
   */
  @Override
  public Expression negate() {
    return new BoundUnaryPredicate<>(op().negate(), term());
  }

  /** 本谓词是一元谓词，返回 true。 */
  @Override
  public boolean isUnaryPredicate() {
    return true;
  }

  /** 以一元谓词视图返回自身。 */
  @Override
  public BoundUnaryPredicate<T> asUnaryPredicate() {
    return this;
  }

  /**
   * 对单个值执行一元判定。
   *
   * <p>逻辑：按操作类型分发——IS_NULL 判 null、NOT_NULL 判非 null、 IS_NAN/NOT_NAN 委托 {@link NaNUtil#isNaN}。
   *
   * @param value 待判定的值
   * @return 判定成立返回 true
   */
  @Override
  public boolean test(T value) {
    switch (op()) {
      case IS_NULL:
        return value == null;
      case NOT_NULL:
        return value != null;
      case IS_NAN:
        return NaNUtil.isNaN(value);
      case NOT_NAN:
        return !NaNUtil.isNaN(value);
      default:
        throw new IllegalStateException("Invalid operation for BoundUnaryPredicate: " + op());
    }
  }

  /**
   * 判断本一元谓词是否与另一表达式等价。
   *
   * <p>逻辑：操作相同且 term 等价即等价。
   *
   * @param other 另一表达式
   * @return 等价返回 true
   */
  @Override
  public boolean isEquivalentTo(Expression other) {
    if (op() == other.op()) {
      return term().isEquivalentTo(((BoundUnaryPredicate<?>) other).term());
    }

    return false;
  }

  @Override
  public String toString() {
    switch (op()) {
      case IS_NULL:
        return "is_null(" + term() + ")";
      case NOT_NULL:
        return "not_null(" + term() + ")";
      case IS_NAN:
        return "is_nan(" + term() + ")";
      case NOT_NAN:
        return "not_nan(" + term() + ")";
      default:
        return "Invalid unary predicate: operation = " + op();
    }
  }
}
