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

import java.util.Objects;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 未知变换占位实现：当解析到无法识别的变换字符串时使用。
 *
 * <p>所属模块：iceberg-api（被 {@link Transforms#fromString} 在无法识别变换时构造）。
 *
 * <p>职责：保留原始变换字符串，使表元数据能加载但实际变换操作一律抛异常。
 *
 * <p>设计意图：保证旧版本/第三方变换不阻断元数据加载——canTransform 恒为 true、getResultType 返回 String、 project/projectStrict
 * 返回 null（不下推），但 apply/bind 抛异常防止误用。equals 仅比较变换字符串。
 *
 * <p>上下游关系：由 {@link Transforms#fromString(String)} 构造；被 PartitionSpec 持有； 被 {@link
 * PartitionSpecVisitor#unknown} / {@link SortOrderVisitor#unknown} 识别。
 *
 * @param <S> 源值的 Java 类型
 * @param <T> 变换后值的 Java 类型
 */
public class UnknownTransform<S, T> implements Transform<S, T> {

  private final String transform;

  /**
   * 构造一个未知变换占位实例。
   *
   * @param transform 原始变换字符串
   */
  UnknownTransform(String transform) {
    this.transform = transform;
  }

  /**
   * 应用变换（不支持）。
   *
   * @param value 源值
   * @return 永不返回
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public T apply(S value) {
    throw new UnsupportedOperationException(
        String.format("Cannot apply unsupported transform: %s", transform));
  }

  /**
   * 绑定到类型（不支持）。
   *
   * @param type 源类型
   * @return 永不返回
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public SerializableFunction<S, T> bind(Type type) {
    throw new UnsupportedOperationException(
        String.format("Cannot bind unsupported transform: %s", transform));
  }

  /**
   * 假定可应用于任何类型，返回 true。
   *
   * <p>设计要点：允许元数据加载时不因类型校验失败而中断。
   *
   * @param type 待校验类型
   * @return 始终 true
   */
  @Override
  public boolean canTransform(Type type) {
    // assume the transform function can be applied for any type
    return true;
  }

  /**
   * 返回结果类型：因实际类型未知，默认返回 String。
   *
   * @param type 源类型
   * @return StringType
   */
  @Override
  public Type getResultType(Type type) {
    // the actual result type is not known
    return Types.StringType.get();
  }

  /**
   * inclusive 投影：未知变换无法投影，返回 null。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 始终 null
   */
  @Override
  public UnboundPredicate<T> project(String name, BoundPredicate<S> predicate) {
    return null;
  }

  /**
   * strict 投影：未知变换无法投影，返回 null。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 始终 null
   */
  @Override
  public UnboundPredicate<T> projectStrict(String name, BoundPredicate<S> predicate) {
    return null;
  }

  /**
   * 返回原始变换字符串。
   *
   * @return 变换字符串
   */
  @Override
  public String toString() {
    return transform;
  }

  /**
   * 相等性：同为 UnknownTransform 且变换字符串相同。
   *
   * @param other 另一个对象
   * @return 相等返回 true
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof UnknownTransform)) {
      return false;
    }

    UnknownTransform<?, ?> that = (UnknownTransform<?, ?>) other;
    return transform.equals(that.transform);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(transform);
  }
}
