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

import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：未绑定的变换项（UnboundTransform）实现。
 *
 * <p>所属模块：iceberg-api（表达式 API 包）。职责：表示一个尚未绑定到 Schema 的"字段变换" 表达式（如 {@code bucket(id, 16)}、{@code
 * truncate(name, 10)}），持有字段引用 （{@link NamedReference}）与变换函数（{@link Transform}）；调用 {@link #bind}
 * 后会校验 变换是否能作用于字段类型，并返回 {@link BoundTransform}。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把"字段引用 + 变换"封装为 {@link UnboundTerm}，使 {@link UnboundPredicate} 可以 对变换结果做谓词判断（如 {@code
 *       bucket(id, 16) = 5}），从而支持分区裁剪。
 *   <li>绑定期做类型校验：变换不能作用于不兼容类型时（如对字符串做 bucket）抛 {@link ValidationException}，把错误前置到查询计划期。
 * </ul>
 *
 * <p>上下游：上游由 {@link Expressions} 工厂或 Transform 自身构造； 下游在 {@code core} 中被 bind 后用于分区投影与谓词下推。
 */
public class UnboundTransform<S, T> implements UnboundTerm<T>, Term {
  private final NamedReference<S> ref;
  private final Transform<S, T> transform;

  /** 构造未绑定变换项，传入字段引用与变换函数。 */
  UnboundTransform(NamedReference<S> ref, Transform<S, T> transform) {
    this.ref = ref;
    this.transform = transform;
  }

  /** 返回字段引用。 */
  @Override
  public NamedReference<S> ref() {
    return ref;
  }

  /** 返回变换函数。 */
  public Transform<S, T> transform() {
    return transform;
  }

  /**
   * 将本未绑定变换项绑定到给定 Schema，返回 {@link BoundTransform}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>先把字段引用绑定为 {@link BoundReference}（解析字段、类型）。
   *   <li>校验变换是否能作用于字段类型（{@link Transform#canTransform}）； 不能则抛 {@link ValidationException}（同时捕获
   *       IllegalArgumentException 转换）。
   *   <li>返回由绑定引用与变换组成的 {@link BoundTransform}。
   * </ol>
   *
   * @param struct 表结构
   * @param caseSensitive 是否区分字段名大小写
   * @return 绑定后的 {@link BoundTransform}
   * @throws ValidationException 若变换不能作用于字段类型
   */
  @Override
  public BoundTransform<S, T> bind(Types.StructType struct, boolean caseSensitive) {
    BoundReference<S> boundRef = ref.bind(struct, caseSensitive);

    try {
      ValidationException.check(
          transform.canTransform(boundRef.type()),
          "Cannot bind: %s cannot transform %s values from '%s'",
          transform,
          boundRef.type(),
          ref.name());
    } catch (IllegalArgumentException e) {
      throw new ValidationException(
          "Cannot bind: %s cannot transform %s values from '%s'",
          transform, boundRef.type(), ref.name());
    }

    return new BoundTransform<>(boundRef, transform);
  }

  /** 返回形如 {@code transform(ref)} 的字符串表示。 */
  @Override
  public String toString() {
    return transform + "(" + ref + ")";
  }
}
