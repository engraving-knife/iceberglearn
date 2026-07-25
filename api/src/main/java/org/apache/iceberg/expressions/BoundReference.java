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

import org.apache.iceberg.Accessor;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 已绑定字段引用：基于字段 id 与访问器在数据行上取值的已绑定项。
 *
 * <p>所属模块：iceberg-api（表达式体系中最核心的已绑定项实现，同时实现 {@link BoundTerm} 与 {@link Reference}，是 {@link
 * NamedReference#bind} 的产物）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link Types.NestedField}（字段元信息）、{@link Accessor}（取值访问器）与列名。
 *   <li>提供 {@link #eval(StructLike)}：通过访问器从数据行中取出字段值。
 *   <li>提供 {@link #type()}、{@link #fieldId()}、{@link #accessor()} 等元信息访问。
 * </ul>
 *
 * <p>设计意图：用字段 id（而非列名）定位字段，保证 schema 演进（rename）下的稳定性； 访问器把“如何从嵌套 struct 中取到目标列”的路径计算前置到绑定阶段，求值时只需一次
 * {@code accessor.get(struct)}，避免每行重复反射/查找，保证行级求值性能。
 *
 * <p>上下游关系：由 {@link NamedReference#bind} 构造；被 {@link BoundPredicate}、 {@link BoundTransform} 等作为底层
 * term；被 {@link Evaluator}、 {@link InclusiveMetricsEvaluator}、{@link ResidualEvaluator} 在求值时调用
 * eval。
 *
 * @param <T> 引用字段的 Java 类型
 */
public class BoundReference<T> implements BoundTerm<T>, Reference<T> {
  private final Types.NestedField field;
  private final Accessor<StructLike> accessor;
  private final String name;

  BoundReference(Types.NestedField field, Accessor<StructLike> accessor, String name) {
    this.field = field;
    this.accessor = accessor;
    this.name = name;
  }

  /**
   * 在数据行上取本引用字段的值。
   *
   * <p>设计要点：通过绑定阶段计算好的 {@link Accessor} 直接取值，避免每行重复查找。
   *
   * @param struct 一条数据行
   * @return 字段值（未检查强转为 T）
   */
  @Override
  @SuppressWarnings("unchecked")
  public T eval(StructLike struct) {
    return (T) accessor.get(struct);
  }

  /** 返回绑定的字段元信息。 */
  public Types.NestedField field() {
    return field;
  }

  /** 返回自身（已绑定引用的 ref 即自身）。 */
  @Override
  public BoundReference<T> ref() {
    return this;
  }

  /** 返回字段的数据类型。 */
  @Override
  public Type type() {
    return field.type();
  }

  /** 返回列名。 */
  @Override
  public String name() {
    return name;
  }

  /**
   * 判断本引用是否与另一项等价。
   *
   * <p>逻辑：若对方也是 {@link BoundReference}，则按字段 id、类型、是否可空三者判定 （忽略列名与访问器，因 id 唯一定位字段）；否则委托对方反向判定。
   *
   * @param other 另一项
   * @return 两者等价返回 true
   */
  @Override
  public boolean isEquivalentTo(BoundTerm<?> other) {
    if (other instanceof BoundReference) {
      Types.NestedField otherField = ((BoundReference<?>) other).field();
      // equivalence only depends on the field ID, type, and optional. name and accessor are ignored
      return field.fieldId() == otherField.fieldId()
          && field.type().equals(otherField.type())
          && field.isOptional() == otherField.isOptional();
    }

    return other.isEquivalentTo(this);
  }

  /** 返回字段 id。 */
  public int fieldId() {
    return field.fieldId();
  }

  /** 返回用于取值的访问器。 */
  public Accessor<StructLike> accessor() {
    return accessor;
  }

  @Override
  public String toString() {
    return String.format("ref(id=%d, accessor-type=%s)", field.fieldId(), accessor.type());
  }
}
