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
package org.apache.iceberg.transforms;

import java.io.ObjectStreamException;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 恒等（identity）分区变换：源值即分区值，不做任何变换。
 *
 * <p>所属模块：iceberg-api（最常用的分区变换，被 PartitionSpec 持有用于按值直接分区）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对任意基本类型值原样返回。
 *   <li>把源字段谓词直接投影为分区值谓词（inclusive 与 strict 等价）。
 *   <li>提供单例 {@link #get()} 供全局复用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>单例 + 类型擦除：通过泛型擦除使一个 INSTANCE 即可服务所有类型，避免重复实例化。
 *   <li>{@code preservesOrder()} 恒为 true，且 satisfiesOrderOf 对任何保持顺序的变换都成立， 这是排序复用的最宽泛基准。
 *   <li>project 与 projectStrict 行为一致：因为变换可逆且无损，谓词可直接套用到分区值上。
 *   <li>序列化通过 {@link SerializationProxies#IdentityTransformProxy} 替代，避免反序列化时 因单例产生多实例破坏 equals 语义。
 * </ul>
 *
 * <p>上下游关系：由 {@link Transforms#identity()} 构造；被 PartitionSpec 作为默认变换使用。
 *
 * @param <T> 源值与变换后值的 Java 类型
 */
class Identity<T> implements Transform<T, T> {
  private static final Identity<?> INSTANCE = new Identity<>();

  private final Type type;

  /**
   * 构造一个绑定了类型的 Identity 实例（已弃用）。
   *
   * <p>设计要点：旧 API 要求传入 Type；新代码应使用 {@link #get()} 获取无类型单例， 类型绑定延迟到 {@link #bind(Type)} 阶段。
   *
   * @param type 源字段类型
   * @deprecated 使用 {@link #get()} 替代；将在 2.0.0 移除
   */
  @Deprecated
  public static <I> Identity<I> get(Type type) {
    return new Identity<>(type);
  }

  /**
   * 返回 Identity 单例（类型擦除后服务所有类型）。
   *
   * @param <I> 源值类型
   * @return Identity 单例
   */
  @SuppressWarnings("unchecked")
  public static <I> Identity<I> get() {
    return (Identity<I>) INSTANCE;
  }

  /**
   * 恒等变换的可序列化函数实现，单例复用。
   *
   * <p>设计要点：bind 时返回此单例，apply 原样返回入参。
   */
  private static class Apply<T> implements SerializableFunction<T, T> {
    private static final Apply<?> APPLY_INSTANCE = new Apply<>();

    @SuppressWarnings("unchecked")
    private static <T> Apply<T> get() {
      return (Apply<T>) APPLY_INSTANCE;
    }

    @Override
    public T apply(T t) {
      return t;
    }
  }

  private Identity() {
    this(null);
  }

  private Identity(Type type) {
    this.type = type;
  }

  /**
   * 原样返回入参。
   *
   * @param value 源值
   * @return 同一值
   */
  @Override
  public T apply(T value) {
    return value;
  }

  /**
   * 绑定到具体类型，返回恒等函数单例。
   *
   * <p>逻辑：先校验类型是基本类型，再返回共享的 {@link Apply} 单例。
   *
   * @param type 源字段类型
   * @return 恒等可序列化函数
   */
  @Override
  @SuppressWarnings("checkstyle:HiddenField")
  public SerializableFunction<T, T> bind(Type type) {
    Preconditions.checkArgument(canTransform(type), "Cannot bind to unsupported type: %s", type);
    return Apply.get();
  }

  /**
   * 判断类型是否可变换：Identity 仅支持基本类型。
   *
   * @param maybePrimitive 待校验类型
   * @return 基本类型返回 true
   */
  @Override
  public boolean canTransform(Type maybePrimitive) {
    return maybePrimitive.isPrimitiveType();
  }

  /**
   * 返回变换值的人类可读字符串（已弃用，不感知类型时使用）。
   *
   * <p>逻辑：若实例绑定了类型则走 {@link #toHumanString(Type, Object)}，否则走父类默认实现。
   *
   * @param value 变换值
   * @return 人类可读字符串
   * @deprecated 使用 {@link #toHumanString(Type, Object)} 替代；将在 2.0.0 移除
   */
  @Deprecated
  @Override
  public String toHumanString(T value) {
    if (this.type != null) {
      return toHumanString(this.type, value);
    }
    return Transform.super.toHumanString(value);
  }

  /**
   * Identity 结果类型与源类型相同。
   *
   * @param sourceType 源类型
   * @return 同一类型
   */
  @Override
  public Type getResultType(Type sourceType) {
    return sourceType;
  }

  /** Identity 保持顺序。 */
  @Override
  public boolean preservesOrder() {
    return true;
  }

  /**
   * Identity 排序可满足任何保持顺序的变换的排序。
   *
   * @param other 另一个变换
   * @return 若 other 保持顺序返回 true
   */
  @Override
  public boolean satisfiesOrderOf(Transform<?, ?> other) {
    // ordering by value is the same as long as the other preserves order
    return other.preservesOrder();
  }

  /**
   * inclusive 投影：Identity 下与 strict 投影等价。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 分区值上的谓词
   */
  @Override
  public UnboundPredicate<T> project(String name, BoundPredicate<T> predicate) {
    return projectStrict(name, predicate);
  }

  /**
   * strict 投影：把谓词原样下推到分区列。
   *
   * <p>逻辑：一元谓词保留算子；字面量谓词保留算子与字面量；集合谓词保留算子与集合；其余返回 null。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 分区值上的谓词；不可投影返回 null
   */
  @Override
  public UnboundPredicate<T> projectStrict(String name, BoundPredicate<T> predicate) {
    if (predicate.isUnaryPredicate()) {
      return Expressions.predicate(predicate.op(), name);
    } else if (predicate.isLiteralPredicate()) {
      return Expressions.predicate(
          predicate.op(), name, predicate.asLiteralPredicate().literal().value());
    } else if (predicate.isSetPredicate()) {
      return Expressions.predicate(predicate.op(), name, predicate.asSetPredicate().literalSet());
    }
    return null;
  }

  /** 标记本变换为 identity。 */
  @Override
  public boolean isIdentity() {
    return true;
  }

  /**
   * 相等性：所有 Identity 实例相等（兼容旧 get(Type) 多实例情况）。
   *
   * <p>设计要点：可随 2.0.0 弃用 get(Type) 后简化为单例判断。
   */
  @Override
  public boolean equals(Object o) {
    // Can be removed with 2.0.0 deprecation of get(Type)
    if (this == o) {
      return true;
    } else if (o instanceof Identity) {
      return true;
    }
    return false;
  }

  /**
   * 哈希码：固定为 0，与 equals 语义匹配。
   *
   * <p>设计要点：可随 2.0.0 弃用 get(Type) 后移除。
   */
  @Override
  public int hashCode() {
    // Can be removed with 2.0.0 deprecation of get(Type)
    return 0;
  }

  /**
   * 返回 "identity" 字符串，用于元数据序列化。
   *
   * @return 字符串表示
   */
  @Override
  public String toString() {
    return "identity";
  }

  /**
   * 序列化替换：用代理对象替代本实例，避免反序列化产生新实例破坏单例语义。
   *
   * @return 序列化代理
   * @throws ObjectStreamException 不会抛出
   */
  Object writeReplace() throws ObjectStreamException {
    return SerializationProxies.IdentityTransformProxy.get();
  }
}
