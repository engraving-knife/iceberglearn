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

import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Types;

/**
 * 命名引用：按列名引用某个字段的未绑定项。
 *
 * <p>所属模块：iceberg-api（表达式体系中最常见的“列引用”节点，同时实现 {@link UnboundTerm} 与 {@link Reference}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一个列名字符串，作为构造谓词/项时的列定位依据。
 *   <li>提供 {@link #bind(Types.StructType, boolean)}：在给定 struct 类型上按列名查找字段， 生成基于字段 id 与访问器（{@link
 *       Accessor}）的 {@link BoundReference}。
 * </ul>
 *
 * <p>设计意图：表达式在构造阶段只知道列名（引擎侧友好），绑定阶段才解析到具体字段 id 与 访问路径，从而把“按名引用”与“按 id 求值”解耦；大小写敏感由 {@code
 * caseSensitive} 控制， 以适配不同引擎的列名匹配规则。
 *
 * <p>上下游关系：由 {@link Expressions#ref(String)} 构造；被 {@link UnboundPredicate}、 {@link
 * UnboundTransform}、{@link UnboundAggregate} 等持有为底层引用；绑定产物 {@link BoundReference} 被各求值器使用。
 *
 * @param <T> 引用字段的 Java 类型
 */
public class NamedReference<T> implements UnboundTerm<T>, Reference<T> {
  private final String name;

  NamedReference(String name) {
    Preconditions.checkNotNull(name, "Name cannot be null");
    this.name = name;
  }

  /** 返回所引用的列名。 */
  @Override
  public String name() {
    return name;
  }

  /**
   * 将本命名引用绑定到具体 struct 类型，返回 {@link BoundReference}。
   *
   * <p>逻辑：以 struct 字段构造临时 {@link Schema}，按 {@code caseSensitive} 选择大小写敏感或 不敏感的字段查找；若找不到字段则抛 {@link
   * ValidationException}；命中后用字段 id 取出 访问器，构造 {@link BoundReference}。
   *
   * @param struct 输入数据的 struct 类型
   * @param caseSensitive 是否大小写敏感匹配列名
   * @return 与字段 id 绑定的 {@link BoundReference}
   * @throws ValidationException 当列名在 struct 中不存在时
   */
  @Override
  public BoundReference<T> bind(Types.StructType struct, boolean caseSensitive) {
    Schema schema = new Schema(struct.fields());
    Types.NestedField field =
        caseSensitive ? schema.findField(name) : schema.caseInsensitiveFindField(name);

    ValidationException.check(
        field != null, "Cannot find field '%s' in struct: %s", name, schema.asStruct());

    return new BoundReference<>(field, schema.accessorForField(field.fieldId()), name);
  }

  /** 返回自身（命名引用自身的 ref 即自身）。 */
  @Override
  public NamedReference<T> ref() {
    return this;
  }

  @Override
  public String toString() {
    return String.format("ref(name=\"%s\")", name);
  }
}
