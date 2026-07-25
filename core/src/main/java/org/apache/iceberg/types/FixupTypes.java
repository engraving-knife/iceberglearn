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

import java.util.List;
import java.util.function.Supplier;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 用于修正原始类型（primitive type）以匹配表 Schema 的抽象访问器。
 *
 * <p>所属模块：iceberg-core（类型系统层），定位为 {@link TypeUtil.CustomOrderSchemaVisitor} 的实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历目标 Schema 的类型树，参照一份"参考 Schema"逐字段修正类型；
 *   <li>在序列化/反序列化往返（round-trip）过程中，恢复可能丢失的精确类型信息（如精度、标量）；
 *   <li>对 struct、list、map 等复合类型递归处理，对原始类型委托给子类决策。
 * </ul>
 *
 * <p>设计意图：某些类型在经过 Avro/Parquet 等格式转换后可能丢失精度（例如 decimal 的 scale）， 本类以"参考 Schema"为权威来源，按相同的字段 ID
 * 逐层下钻，将目标类型修正为参考类型。 采用自定义遍历顺序的 SchemaVisitor，是因为需要同步维护一个 {@code sourceType} 指针来追踪 当前在参考 Schema
 * 中对应的类型位置。{@code sourceType} 在进入子节点前更新、退出后恢复（try-finally）。
 *
 * <p>上下游关系：继承 {@link TypeUtil.CustomOrderSchemaVisitor}，由 {@link TypeUtil} 的类型修正流程调用； 子类需实现 {@link
 * #fixupPrimitive} 以定义原始类型的具体修正规则。
 */
public abstract class FixupTypes extends TypeUtil.CustomOrderSchemaVisitor<Type> {
  private final Schema referenceSchema;
  private Type sourceType;

  /**
   * 构造一个类型修正器。
   *
   * @param referenceSchema 参考Schema，作为类型修正的权威来源
   */
  protected FixupTypes(Schema referenceSchema) {
    this.referenceSchema = referenceSchema;
    this.sourceType = referenceSchema.asStruct();
  }

  /**
   * 访问 Schema 根节点，将 sourceType 重置为参考 Schema 的根 struct，然后递归处理子节点。
   *
   * @param schema 待修正的 Schema
   * @param future 延迟计算的子节点结果
   * @return 修正后的类型
   */
  @Override
  public Type schema(Schema schema, Supplier<Type> future) {
    this.sourceType = referenceSchema.asStruct();
    return future.get();
  }

  /**
   * 处理 struct 类型，逐字段递归修正类型。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验当前 sourceType 为 struct 类型；
   *   <li>遍历每个字段，根据修正后的子类型重建 NestedField（保留原 required/optional 语义）；
   *   <li>仅当有字段类型发生变化时才构造新的 StructType，否则返回原对象以减少对象创建。
   * </ol>
   *
   * @param struct 待修正的 struct 类型
   * @param fieldTypes 各字段修正后的类型迭代器
   * @return 修正后的 struct 类型，若无变化则返回原对象
   */
  @Override
  public Type struct(Types.StructType struct, Iterable<Type> fieldTypes) {
    Preconditions.checkArgument(sourceType.isStructType(), "Not a struct: %s", sourceType);

    List<Types.NestedField> fields = struct.fields();
    int length = fields.size();

    List<Type> types = Lists.newArrayList(fieldTypes);
    List<Types.NestedField> newFields = Lists.newArrayListWithExpectedSize(length);
    boolean hasChange = false;
    for (int i = 0; i < length; i += 1) {
      Types.NestedField field = fields.get(i);
      Type resultType = types.get(i);

      if (field.type() == resultType) {
        newFields.add(field);

      } else if (field.isRequired()) {
        hasChange = true;
        newFields.add(
            Types.NestedField.required(field.fieldId(), field.name(), resultType, field.doc()));

      } else {
        hasChange = true;
        newFields.add(
            Types.NestedField.optional(field.fieldId(), field.name(), resultType, field.doc()));
      }
    }

    if (hasChange) {
      return Types.StructType.of(newFields);
    }

    return struct;
  }

  /**
   * 处理 struct 中的单个字段，根据字段 ID 在参考 Schema 中定位对应类型并下钻递归。
   *
   * <p>逻辑：在 sourceType（当前 struct）中按 fieldId 查找参考字段；若找到则将 sourceType 切换为该字段的类型并递归处理，退出时恢复；若参考 Schema
   * 中不存在该字段则直接返回原类型。
   *
   * @param field 待处理的字段
   * @param future 延迟计算的子类型结果
   * @return 修正后的字段类型
   */
  @Override
  public Type field(Types.NestedField field, Supplier<Type> future) {
    Preconditions.checkArgument(sourceType.isStructType(), "Not a struct: %s", sourceType);

    Types.StructType sourceStruct = sourceType.asStructType();
    Types.NestedField sourceField = sourceStruct.field(field.fieldId());
    if (sourceField != null) {
      this.sourceType = sourceField.type();
      try {
        return future.get();
      } finally {
        sourceType = sourceStruct;
      }
    } else {
      return field.type();
    }
  }

  /**
   * 处理 list 类型，递归修正元素类型。
   *
   * <p>逻辑：将 sourceType 切换为参考 list 的元素类型，递归获取修正后的元素类型， 然后按原 optional/required 语义重建
   * ListType；若无变化则返回原对象。
   *
   * @param list 待修正的 list 类型
   * @param elementTypeFuture 延迟计算的元素类型结果
   * @return 修正后的 list 类型
   */
  @Override
  public Type list(Types.ListType list, Supplier<Type> elementTypeFuture) {
    Preconditions.checkArgument(sourceType.isListType(), "Not a list: %s", sourceType);

    Types.ListType sourceList = sourceType.asListType();
    this.sourceType = sourceList.elementType();
    try {
      Type elementType = elementTypeFuture.get();
      if (list.elementType() == elementType) {
        return list;
      }

      if (list.isElementOptional()) {
        return Types.ListType.ofOptional(list.elementId(), elementType);
      } else {
        return Types.ListType.ofRequired(list.elementId(), elementType);
      }

    } finally {
      this.sourceType = sourceList;
    }
  }

  /**
   * 处理 map 类型，分别递归修正 key 和 value 的类型。
   *
   * <p>逻辑：先切换 sourceType 为参考 map 的 keyType 并递归修正 key，再切换为 valueType 递归修正 value，最后按原
   * optional/required 语义重建 MapType；若无变化则返回原对象。
   *
   * @param map 待修正的 map 类型
   * @param keyTypeFuture 延迟计算的 key 类型结果
   * @param valueTypeFuture 延迟计算的 value 类型结果
   * @return 修正后的 map 类型
   */
  @Override
  public Type map(Types.MapType map, Supplier<Type> keyTypeFuture, Supplier<Type> valueTypeFuture) {
    Preconditions.checkArgument(sourceType.isMapType(), "Not a map: %s", sourceType);

    Types.MapType sourceMap = sourceType.asMapType();
    try {
      this.sourceType = sourceMap.keyType();
      Type keyType = keyTypeFuture.get();

      this.sourceType = sourceMap.valueType();
      Type valueType = valueTypeFuture.get();

      if (map.keyType() == keyType && map.valueType() == valueType) {
        return map;
      }

      if (map.isValueOptional()) {
        return Types.MapType.ofOptional(map.keyId(), map.valueId(), keyType, valueType);
      } else {
        return Types.MapType.ofRequired(map.keyId(), map.valueId(), keyType, valueType);
      }

    } finally {
      this.sourceType = sourceMap;
    }
  }

  /**
   * 处理原始类型，决定是否用参考类型替换当前类型。
   *
   * <p>逻辑：若 sourceType 与 primitive 相同则无需修正；否则调用 {@link #fixupPrimitive} 判断是否可修正，可修正则返回
   * sourceType，不可修正则返回原类型交由后续校验处理。
   *
   * @param primitive 待修正的原始类型
   * @return 修正后的类型
   */
  @Override
  public Type primitive(Type.PrimitiveType primitive) {
    if (sourceType.equals(primitive)) {
      return primitive; // already correct
    }

    if (fixupPrimitive(primitive, sourceType)) {
      return sourceType;
    }

    // nothing to fix up, let validation catch promotion errors
    return primitive;
  }

  /**
   * 由子类实现的原始类型修正决策方法。
   *
   * @param type 待修正的原始类型
   * @param source 参考类型（来自参考 Schema）
   * @return 若可以将 type 修正为 source 则返回 true，否则返回 false
   */
  protected abstract boolean fixupPrimitive(Type.PrimitiveType type, Type source);
}
