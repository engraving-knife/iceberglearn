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

/**
 * 聚合表达式抽象基类：描述可下推到 Iceberg 并在数据/文件指标上求值的聚合函数。
 *
 * <p>所属模块：iceberg-api（表达式体系中“聚合”分支的根，与谓词 {@link Predicate} 分支并列， 共同构成 {@link Expression} 树；当前支持
 * Max、Min、Count 三类聚合）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载聚合操作类型 {@link Operation}（COUNT/COUNT_STAR/MAX/MIN）与作用 term。
 *   <li>提供 {@link #op()} 与 {@link #term()} 访问器，以及可读 {@link #toString()}。
 * </ul>
 *
 * <p>设计意图：泛型 {@code <C extends Term>} 让同一基类既能表达未绑定聚合 （{@code Aggregate<UnboundTerm<T>>}，即 {@link
 * UnboundAggregate}），也能表达已绑定聚合 （{@code Aggregate<BoundTerm<T>>}，即 {@link BoundAggregate}），从而复用
 * op/term/toString 逻辑。
 *
 * <p>上下游关系：由 {@link Expressions#count}/{@code max}/{@code min} 构造未绑定形态； 经 {@link Binder} 绑定为 {@link
 * BoundAggregate}，被 {@link AggregateEvaluator} 驱动求值。
 *
 * @param <C> 作用项的类型（{@link UnboundTerm} 或 {@link BoundTerm}）
 */
public abstract class Aggregate<C extends Term> implements Expression {
  private final Operation op;
  private final C term;

  Aggregate(Operation op, C term) {
    this.op = op;
    this.term = term;
  }

  /** 返回聚合操作类型。 */
  @Override
  public Operation op() {
    return op;
  }

  /** 返回聚合作用的 term。 */
  public C term() {
    return term;
  }

  /**
   * 返回聚合的可读字符串表示。
   *
   * <p>逻辑：按操作类型拼装，如 count(term)、count(*)、max(term)、min(term)； 遇到不支持的操作抛 {@link
   * UnsupportedOperationException}。
   */
  @Override
  public String toString() {
    switch (op()) {
      case COUNT:
        return "count(" + term() + ")";
      case COUNT_STAR:
        return "count(*)";
      case MAX:
        return "max(" + term() + ")";
      case MIN:
        return "min(" + term() + ")";
      default:
        throw new UnsupportedOperationException("Invalid aggregate: " + op());
    }
  }
}
