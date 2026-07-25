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
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 已绑定变换 term：把一个 {@link BoundReference} 的值通过 {@link Transform} 变换后作为 term 值。
 *
 * <p>所属模块：iceberg-api（term 体系的一员；让表达式可以作用于“变换后的字段”，例如 bucket(ts, N)、truncate(s, W) 等隐藏分区函数）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有底层绑定引用与变换函数，在 {@link #eval(StructLike)} 时先取字段值再应用变换。
 *   <li>暴露变换结果类型 {@link #type()}，供上层推导表达式类型。
 *   <li>提供等价性判定 {@link #isEquivalentTo(BoundTerm)}，支持表达式化简与去重。
 * </ul>
 *
 * <p>设计意图：变换函数在构造时通过 {@link Transform#bind} 绑定到字段类型，得到 {@link SerializableFunction}，使后续求值只做一次
 * apply，避免重复绑定开销。
 *
 * <p>上下游关系：由 {@link Binder} 在绑定含变换的 {@link UnboundTerm} 时构造；被 {@link BoundPredicate} / {@link
 * BoundAggregate} 等作为 term 使用。
 *
 * @param <S> 变换输入（源字段）值的 Java 类型
 * @param <T> 变换输出值的 Java 类型
 */
public class BoundTransform<S, T> implements BoundTerm<T> {
  private final BoundReference<S> ref;
  private final Transform<S, T> transform;
  private final SerializableFunction<S, T> func;

  /**
   * 构造已绑定变换。
   *
   * <p>逻辑：保存引用与变换，并立即调用 {@link Transform#bind} 把变换绑定到字段类型， 得到可执行的 {@link SerializableFunction}。
   *
   * @param ref 已绑定字段引用
   * @param transform 变换函数
   */
  BoundTransform(BoundReference<S> ref, Transform<S, T> transform) {
    this.ref = ref;
    this.transform = transform;
    this.func = transform.bind(ref.type());
  }

  /**
   * 在一行数据上求变换后的值。
   *
   * <p>逻辑：先由 ref 取出字段值，再用预绑定的 func 应用变换。
   *
   * @param struct 一行数据
   * @return 变换后的值
   */
  @Override
  public T eval(StructLike struct) {
    return func.apply(ref.eval(struct));
  }

  /**
   * 返回底层绑定引用。
   *
   * @return 绑定字段引用
   */
  @Override
  public BoundReference<S> ref() {
    return ref;
  }

  /**
   * 返回本变换使用的 {@link Transform}。
   *
   * @return 变换函数
   */
  public Transform<S, T> transform() {
    return transform;
  }

  /**
   * 推导变换结果类型。
   *
   * <p>逻辑：委托 {@link Transform#getResultType} 根据字段类型计算结果类型。
   *
   * @return 变换结果类型
   */
  @Override
  public Type type() {
    return transform.getResultType(ref.type());
  }

  /**
   * 判定本 term 是否与另一 term 语义等价。
   *
   * <p>逻辑：若对方同为 BoundTransform，则要求引用等价且变换相等；若对方为 BoundReference 且本变换是恒等变换，则退化为引用等价判定；其余返回 false。
   *
   * @param other 另一 term
   * @return 语义等价返回 true
   */
  @Override
  public boolean isEquivalentTo(BoundTerm<?> other) {
    if (other instanceof BoundTransform) {
      BoundTransform<?, ?> bound = (BoundTransform<?, ?>) other;
      return ref.isEquivalentTo(bound.ref()) && transform.equals(bound.transform());
    } else if (transform.isIdentity() && other instanceof BoundReference) {
      return ref.isEquivalentTo(other);
    }

    return false;
  }

  /**
   * 返回可读字符串表示，形如 "transform(ref)"。
   *
   * @return 可读字符串
   */
  @Override
  public String toString() {
    return transform + "(" + ref + ")";
  }
}
