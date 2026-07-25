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

import org.apache.iceberg.StructLike;

/**
 * 已绑定值表达式：能够在传入的数据行上求出一个具体值。
 *
 * <p>所属模块：iceberg-api（表达式体系中“已绑定”一侧的顶层契约，是 {@link BoundTerm}、 {@link BoundPredicate}、{@link
 * BoundTransform} 等已绑定表达式的共同父接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>暴露底层字段引用 {@link #ref()}，供访问者等组件获取被引用字段。
 *   <li>提供 {@link #eval(StructLike)}，在一条数据行上求出当前表达式的值。
 * </ul>
 *
 * <p>设计意图：将“已绑定”这一语义独立成接口，使得访问者（如 {@link ExpressionVisitors}） 可以统一处理 term 与 predicate；泛型 {@code
 * <T>} 表示求值结果的 Java 类型， 例如 {@code BoundReference<Integer>} 求出 Integer、{@code BoundPredicate<T>} 求出
 * Boolean。
 *
 * <p>上下游关系：由 {@link Unbound#bind(Types.StructType, boolean)} 绑定产生； 被 {@link Evaluator}、{@link
 * InclusiveMetricsEvaluator}、{@link ResidualEvaluator} 等 求值器在遍历时调用求值。
 *
 * @param <T> 该表达式求值产生的 Java 类型
 */
public interface Bound<T> {
  /** 返回底层字段引用。 */
  BoundReference<?> ref();

  /**
   * 在传入的数据行上求出本表达式的值。
   *
   * @param struct 一条输入数据行
   * @return 本表达式在该行上的求值结果
   */
  T eval(StructLike struct);
}
