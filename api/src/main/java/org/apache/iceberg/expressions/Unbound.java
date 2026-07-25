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

import org.apache.iceberg.types.Types;

/**
 * 未绑定表达式节点：尚未与具体 schema 的字段解析关联，需在求值前调用 {@link #bind} 完成绑定。
 *
 * <p>所属模块：iceberg-api（表达式体系中“未绑定”一侧的顶层契约，是 {@link UnboundTerm}、 {@link UnboundPredicate}、{@link
 * UnboundAggregate} 等的共同父接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 {@link #bind(Types.StructType, boolean)}：依据输入 struct 类型与大小写敏感策略， 把基于列名的引用解析为基于字段 id
 *       的已绑定表达式。
 *   <li>暴露 {@link #ref()} 返回底层未绑定引用（{@link NamedReference}），便于投影、残差等 流程在绑定前访问列名。
 * </ul>
 *
 * <p>设计意图：采用“未绑定 / 已绑定”两段式设计，使表达式可先按列名构造（引擎友好）， 再在具体 schema 上绑定（类型校验、字面量转换、常量折叠），从而把“构造”与“求值”解耦。 泛型
 * {@code <T>} 为未绑定时的值类型，{@code <B>} 为绑定后产出的已绑定表达式类型。
 *
 * <p>上下游关系：由 {@link Expressions} 工厂方法构造；被 {@link Binder} 统一驱动绑定； 绑定产物（已绑定表达式）交给 {@link
 * Evaluator}、{@link InclusiveMetricsEvaluator} 等求值。
 *
 * @param <T> 该节点未绑定时的值 Java 类型
 * @param <B> 该节点经 {@link #bind(Types.StructType, boolean)} 绑定后产出的 Java 类型
 */
public interface Unbound<T, B> {
  /**
   * 将本表达式绑定到具体的 struct 类型。
   *
   * @param struct 输入数据的 struct 类型，用于按列名解析字段
   * @param caseSensitive 绑定时是否按大小写敏感方式匹配列名
   * @return 已绑定的表达式
   */
  B bind(Types.StructType struct, boolean caseSensitive);

  /** 返回本表达式底层未绑定引用。 */
  NamedReference<?> ref();
}
