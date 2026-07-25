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
 * 逻辑与（AND）二元表达式节点：表示左右两个子表达式同时成立。
 *
 * <p>所属模块：iceberg-api（表达式树的合取连接节点，与 {@link Or}、{@link Not} 同属布尔连接词）。
 *
 * <p>职责：承载左右两个子 {@link Expression}，提供操作类型、等价判定、取反与字符串表示。
 *
 * <p>设计意图：不可变二元节点，构造由 {@link Expressions#and(Expression, Expression)} 工厂 负责（工厂会做
 * alwaysTrue/alwaysFalse 短路化简），本类仅做结构持有与语义实现。 {@link #negate()} 应用德摩根律 {@code not(and(a,b)) =>
 * or(not(a), not(b))}， 配合 {@link RewriteNot} 把 NOT 下沉到叶子，便于投影/残差等流程处理。
 *
 * <p>上下游关系：由 {@link Expressions#and} 构造；被 {@link ExpressionVisitors#visit} 在 后序遍历时调用 {@code
 * visitor.and(...)}；被 {@link Evaluator}、{@link Projections}、 {@link ResidualEvaluator} 等求值/投影器遍历。
 */
public class And implements Expression {
  private final Expression left;
  private final Expression right;

  And(Expression left, Expression right) {
    this.left = left;
    this.right = right;
  }

  /** 返回左侧子表达式。 */
  public Expression left() {
    return left;
  }

  /** 返回右侧子表达式。 */
  public Expression right() {
    return right;
  }

  @Override
  public Operation op() {
    return Expression.Operation.AND;
  }

  /**
   * 判断本 And 是否与另一表达式等价。
   *
   * <p>逻辑：仅当对方也是 AND 时比较；因合取可交换，故同时校验“左对左、右对右”与 “左对右、右对左”两种顺序，任一成立即等价。
   *
   * @param expr 另一表达式
   * @return 两者等价返回 true
   */
  @Override
  public boolean isEquivalentTo(Expression expr) {
    if (expr.op() == Operation.AND) {
      And other = (And) expr;
      return (left.isEquivalentTo(other.left()) && right.isEquivalentTo(other.right()))
          || (left.isEquivalentTo(other.right()) && right.isEquivalentTo(other.left()));
    }

    return false;
  }

  /**
   * 返回本 And 的否定表达式，应用德摩根律：not(and(a, b)) => or(not(a), not(b))。
   *
   * @return 等价于 not(this) 的表达式
   */
  @Override
  public Expression negate() {
    // not(and(a, b)) => or(not(a), not(b))
    return Expressions.or(left.negate(), right.negate());
  }

  @Override
  public String toString() {
    return String.format("(%s and %s)", left, right);
  }
}
