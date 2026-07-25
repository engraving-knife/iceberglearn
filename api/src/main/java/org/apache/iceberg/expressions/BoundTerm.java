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
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Type;

/**
 * 已绑定项（BoundTerm）：在数据行上求出值并携带类型与比较器的已绑定表达式。
 *
 * <p>所属模块：iceberg-api（表达式体系“已绑定项”的核心接口，同时继承 {@link Bound} 与 {@link Term}，是 {@link
 * BoundReference}、{@link BoundTransform} 的父类型）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link #type()}：返回该项求值结果的 {@link Type}，用于类型校验与字面量转换。
 *   <li>提供 {@link #comparator()}：返回值比较器，供谓词判断大小关系使用。
 *   <li>提供 {@link #isEquivalentTo(BoundTerm)}：判定两个项是否对相同输入产生相同值， 用于表达式等价化简。
 * </ul>
 *
 * <p>设计意图：把“类型”与“比较器”下沉到 term 层，使谓词（如 {@link BoundLiteralPredicate}） 可直接复用 term
 * 的比较器进行大小判断，而无需各自实现。比较器默认基于原始类型 （{@link Comparators#forType}），子类可按需覆盖（如字符串使用 CharSequence 比较）。
 *
 * <p>上下游关系：由 {@link UnboundTerm#bind} 绑定产生；被 {@link BoundPredicate} 持有为操作数； 被 {@link
 * ExpressionVisitors.BoundVisitor}、{@link Evaluator} 等遍历求值。
 *
 * @param <T> 该项求值产生的 Java 类型
 */
public interface BoundTerm<T> extends Bound<T>, Term {
  /** 返回该项求值结果的数据类型。 */
  Type type();

  /**
   * 返回用于比较该项所产生值的 {@link Comparator}。
   *
   * <p>逻辑：默认按项的原始类型从 {@link Comparators#forType} 获取比较器。
   *
   * @return 值比较器
   */
  default Comparator<T> comparator() {
    return Comparators.forType(type().asPrimitiveType());
  }

  /**
   * 判断本项是否与另一项等价（对相同输入产生相同值）。
   *
   * @param other 另一项
   * @return 两者等价返回 true，否则 false
   */
  boolean isEquivalentTo(BoundTerm<?> other);
}
