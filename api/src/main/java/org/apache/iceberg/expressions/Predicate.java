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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 谓词表达式抽象基类：一个 {@link Operation} 作用于一个 {@link Term}，构成布尔判定。
 *
 * <p>所属模块：iceberg-api（表达式体系谓词分支的根抽象；{@link BoundPredicate} 与 {@link UnboundPredicate} 均继承自本类）。
 *
 * <p>职责：持有操作类型与 term，提供统一的 {@link #op()} 与 {@link #term()} 访问入口。
 *
 * <p>设计意图：把“操作 + term”这一最小谓词结构下沉到基类，子类只需关注求值/绑定细节； 构造时强制 term 非空，避免后续求值出现 NPE。
 *
 * <p>上下游关系：被 {@link ExpressionVisitors} 的 predicate 方法分派；具体子类承载 实际求值与绑定逻辑。
 *
 * @param <T> 谓词输入值的 Java 类型
 * @param <C> term 的具体类型（{@link BoundTerm} 或 {@link UnboundTerm}）
 */
public abstract class Predicate<T, C extends Term> implements Expression {
  private final Operation op;
  private final C term;

  /**
   * 构造谓词。
   *
   * <p>逻辑：校验 term 非空，保存操作类型与 term。
   *
   * @param op 操作类型
   * @param term 谓词作用的 term
   */
  Predicate(Operation op, C term) {
    Preconditions.checkNotNull(term, "Term cannot be null");
    this.op = op;
    this.term = term;
  }

  /** 返回本谓词的操作类型。 */
  @Override
  public Operation op() {
    return op;
  }

  /**
   * 返回本谓词作用的 term。
   *
   * @return term
   */
  public C term() {
    return term;
  }
}
