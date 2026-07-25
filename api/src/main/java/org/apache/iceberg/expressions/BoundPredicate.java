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
 * 已绑定谓词的抽象基类：谓词操作（EQ/LT/IN 等）作用于一个 {@link BoundTerm}。
 *
 * <p>所属模块：iceberg-api（表达式体系的谓词分支；与 {@link BoundAggregate} 平行， 描述“对一行数据返回 true/false”的布尔判定语义）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载操作类型 {@link Operation} 与绑定的 term，提供 {@link #test(StructLike)}、 {@link
 *       #test(Object)}、{@link #eval(StructLike)} 等求值入口。
 *   <li>通过一组 {@code isXxxPredicate} / {@code asXxxPredicate} 方法实现类型分层与 安全向下转型，便于访问者按子类型分派处理。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>抽象基类只定义通用协议，具体求值由 {@link BoundUnaryPredicate}、 {@link BoundLiteralPredicate}、{@link
 *       BoundSetPredicate} 等子类实现， 通过 isXxx/asXxx 暴露类型分派能力，避免在访问者中用 instanceof 硬编码。
 *   <li>{@code test(StructLike)} 默认实现是“先求 term 值再调用 test(T)”，子类可覆盖以做 更精细的优化（如直接利用行内字段位置）。
 * </ul>
 *
 * <p>上下游关系：由 {@link Binder} 将 {@link UnboundPredicate} 绑定得到；被 {@link ManifestEvaluator}、{@link
 * StrictMetricsEvaluator}、{@link ExpressionVisitors} 等访问者消费。
 *
 * @param <T> 谓词输入值的 Java 类型
 */
public abstract class BoundPredicate<T> extends Predicate<T, BoundTerm<T>>
    implements Bound<Boolean> {
  /**
   * 构造已绑定谓词。
   *
   * @param op 谓词操作类型
   * @param term 已绑定的 term（被比较的字段或变换）
   */
  protected BoundPredicate(Operation op, BoundTerm<T> term) {
    super(op, term);
  }

  /**
   * 在一行数据上求此谓词的真值。
   *
   * <p>逻辑：先用 term 求出该行对应的值，再委托 {@link #test(Object)} 判定。
   *
   * @param struct 一行数据
   * @return 谓词成立返回 true
   */
  public boolean test(StructLike struct) {
    return test(term().eval(struct));
  }

  /**
   * 在一个具体值上判定此谓词是否成立（由子类实现）。
   *
   * @param value 待判定的值
   * @return 谓词成立返回 true
   */
  public abstract boolean test(T value);

  /**
   * 求值入口：返回此谓词对一行的判定结果（包装为 Boolean）。
   *
   * @param struct 一行数据
   * @return 判定结果
   */
  @Override
  public Boolean eval(StructLike struct) {
    return test(term().eval(struct));
  }

  /**
   * 返回此谓词绑定的字段引用。
   *
   * @return 绑定字段引用
   */
  @Override
  public BoundReference<?> ref() {
    return term().ref();
  }

  /** 是否为一元谓词（如 IS_NULL / NOT_NULL）。基类默认 false，由对应子类覆盖。 */
  public boolean isUnaryPredicate() {
    return false;
  }

  /**
   * 把本谓词当作一元谓词访问。
   *
   * @return 转型后的 {@link BoundUnaryPredicate}
   * @throws IllegalStateException 若本谓词不是一元谓词
   */
  public BoundUnaryPredicate<T> asUnaryPredicate() {
    throw new IllegalStateException("Not a unary predicate: " + this);
  }

  /** 是否为字面量谓词（如 EQ / LT / GT 等）。基类默认 false，由对应子类覆盖。 */
  public boolean isLiteralPredicate() {
    return false;
  }

  /**
   * 把本谓词当作字面量谓词访问。
   *
   * @return 转型后的 {@link BoundLiteralPredicate}
   * @throws IllegalStateException 若本谓词不是字面量谓词
   */
  public BoundLiteralPredicate<T> asLiteralPredicate() {
    throw new IllegalStateException("Not a literal predicate: " + this);
  }

  /** 是否为集合谓词（IN / NOT_IN）。基类默认 false，由对应子类覆盖。 */
  public boolean isSetPredicate() {
    return false;
  }

  /**
   * 把本谓词当作集合谓词访问。
   *
   * @return 转型后的 {@link BoundSetPredicate}
   * @throws IllegalStateException 若本谓词不是集合谓词
   */
  public BoundSetPredicate<T> asSetPredicate() {
    throw new IllegalStateException("Not a set predicate: " + this);
  }
}
