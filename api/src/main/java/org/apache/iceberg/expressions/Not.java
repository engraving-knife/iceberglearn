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
 * 逻辑非表达式节点：对子表达式取反，对应 {@link Expression.Operation#NOT}。
 *
 * <p>所属模块：iceberg-api（表达式体系中布尔逻辑组合节点之一；与 And/Or 配对）。
 *
 * <p>职责：包裹一个子表达式并标记“取反”语义。
 *
 * <p>设计意图：Iceberg 推荐在绑定前先用 {@link Expressions#rewriteNot} 把 NOT 节点下推到 叶子谓词（调用各谓词的
 * negate），使下游求值器无需处理嵌套 NOT。本类作为中间表达形式 存在，{@link #negate()} 直接返回 child 以体现双重否定抵消。
 *
 * <p>上下游关系：由 {@link Expressions#not} 构造；被 {@link RewriteNot} 访问器重写消除。
 */
public class Not implements Expression {
  private final Expression child;

  /**
   * 构造 NOT 节点。
   *
   * @param child 被取反的子表达式
   */
  Not(Expression child) {
    this.child = child;
  }

  /**
   * 返回被取反的子表达式。
   *
   * @return 子表达式
   */
  public Expression child() {
    return child;
  }

  /** 返回 {@link Expression.Operation#NOT}。 */
  @Override
  public Operation op() {
    return Expression.Operation.NOT;
  }

  /**
   * 返回此 NOT 表达式的否定形式，即双重否定抵消，直接返回 child。
   *
   * @return 子表达式
   */
  @Override
  public Expression negate() {
    return child;
  }

  /** 返回可读字符串 "not(child)"。 */
  @Override
  public String toString() {
    return String.format("not(%s)", child);
  }
}
