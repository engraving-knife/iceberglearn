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
 * Not 重写访问器：把表达式树中的 {@link Not} 节点下推到叶子谓词，调用其 {@code negate()}。
 *
 * <p>所属模块：iceberg-api（表达式预处理工具；由 {@link Expressions#rewriteNot} 使用）。
 *
 * <p>职责：遍历表达式树，遇到 NOT 节点时对其子表达式调用 {@link Expression#negate()}， 从而消除所有 NOT 节点；其余节点保持结构不变。
 *
 * <p>设计意图：让下游求值器（如 ManifestEvaluator、StrictMetricsEvaluator）无需处理 嵌套 NOT，简化求值逻辑。采用单例模式避免重复分配。
 *
 * <p>上下游关系：被 {@link Expressions#rewriteNot} 与 {@link Binder}、 {@link ManifestEvaluator} 等在绑定前调用。
 */
class RewriteNot extends ExpressionVisitors.ExpressionVisitor<Expression> {
  private static final RewriteNot INSTANCE = new RewriteNot();

  /** 返回单例实例。 */
  static RewriteNot get() {
    return INSTANCE;
  }

  private RewriteNot() {}

  @Override
  public Expression alwaysTrue() {
    return Expressions.alwaysTrue();
  }

  @Override
  public Expression alwaysFalse() {
    return Expressions.alwaysFalse();
  }

  /**
   * 处理 NOT 节点：对子表达式取否定形式，消除 NOT 包装。
   *
   * @param result 已重写的子表达式
   * @return 子表达式的否定形式
   */
  @Override
  public Expression not(Expression result) {
    return result.negate();
  }

  @Override
  public Expression and(Expression leftResult, Expression rightResult) {
    return Expressions.and(leftResult, rightResult);
  }

  @Override
  public Expression or(Expression leftResult, Expression rightResult) {
    return Expressions.or(leftResult, rightResult);
  }

  /** 已绑定谓词保持不变。 */
  @Override
  public <T> Expression predicate(BoundPredicate<T> pred) {
    return pred;
  }

  /** 未绑定谓词保持不变。 */
  @Override
  public <T> Expression predicate(UnboundPredicate<T> pred) {
    return pred;
  }
}
