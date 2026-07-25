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
package org.apache.iceberg.types;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 分配新 ID 访问者：为 schema 中所有字段分配全新的字段 ID。
 *
 * <p>所属模块：iceberg-api（被 {@link TypeUtil#assignFreshIds} 使用）。
 *
 * <p>职责：前序遍历 schema，为每个字段分配新 ID；若提供 baseSchema，则按字段名从 baseSchema 复制已有 ID，保持兼容性。
 *
 * <p>设计意图：使用 CustomOrderSchemaVisitor 实现前序遍历（先分配当前层字段 ID， 再递归子类型），保证 ID 分配顺序与字段声明顺序一致。Supplier 懒求值使
 * ID 分配 在访问子类型前完成。
 */
class AssignFreshIds extends TypeUtil.CustomOrderSchemaVisitor<Type> {
  private final Schema visitingSchema;
  private final Schema baseSchema;
  private final TypeUtil.NextID nextId;

  AssignFreshIds(TypeUtil.NextID nextId) {
    this.visitingSchema = null;
    this.baseSchema = null;
    this.nextId = nextId;
  }

  /**
   * 构造可从 baseSchema 复制 ID 的分配器。
   *
   * @param visitingSchema 当前 schema（用于 ID→名称查找）
   * @param baseSchema 提供 已有 ID 的基准 schema
   * @param nextId 新字段 ID 分配器
   */
  AssignFreshIds(Schema visitingSchema, Schema baseSchema, TypeUtil.NextID nextId) {
    this.visitingSchema = visitingSchema;
    this.baseSchema = baseSchema;
    this.nextId = nextId;
  }

  private int idFor(String fullName) {
    if (baseSchema != null && fullName != null) {
      Types.NestedField field = baseSchema.findField(fullName);
      if (field != null) {
        return field.fieldId();
      }
    }

    return nextId.get();
  }

  private String name(int id) {
    if (visitingSchema != null) {
      return visitingSchema.findColumnName(id);
    }

    return null;
  }

  @Override
  public Type schema(Schema schema, Supplier<Type> future) {
    return future.get();
  }

  @Override
  public Type struct(Types.StructType struct, Iterable<Type> futures) {
    List<Types.NestedField> fields = struct.fields();
    int length = struct.fields().size();

    // assign IDs for this struct's fields first
    List<Integer> newIds = Lists.newArrayListWithExpectedSize(length);
    for (int i = 0; i < length; i += 1) {
      newIds.add(idFor(name(fields.get(i).fieldId())));
    }

    List<Types.NestedField> newFields = Lists.newArrayListWithExpectedSize(length);
    Iterator<Type> types = futures.iterator();
    for (int i = 0; i < length; i += 1) {
      Types.NestedField field = fields.get(i);
      Type type = types.next();
      if (field.isOptional()) {
        newFields.add(Types.NestedField.optional(newIds.get(i), field.name(), type, field.doc()));
      } else {
        newFields.add(Types.NestedField.required(newIds.get(i), field.name(), type, field.doc()));
      }
    }

    return Types.StructType.of(newFields);
  }

  @Override
  public Type field(Types.NestedField field, Supplier<Type> future) {
    return future.get();
  }

  @Override
  public Type list(Types.ListType list, Supplier<Type> future) {
    int newId = idFor(name(list.elementId()));
    if (list.isElementOptional()) {
      return Types.ListType.ofOptional(newId, future.get());
    } else {
      return Types.ListType.ofRequired(newId, future.get());
    }
  }

  @Override
  public Type map(Types.MapType map, Supplier<Type> keyFuture, Supplier<Type> valueFuture) {
    int newKeyId = idFor(name(map.keyId()));
    int newValueId = idFor(name(map.valueId()));
    if (map.isValueOptional()) {
      return Types.MapType.ofOptional(newKeyId, newValueId, keyFuture.get(), valueFuture.get());
    } else {
      return Types.MapType.ofRequired(newKeyId, newValueId, keyFuture.get(), valueFuture.get());
    }
  }

  @Override
  public Type primitive(Type.PrimitiveType primitive) {
    return primitive;
  }
}
