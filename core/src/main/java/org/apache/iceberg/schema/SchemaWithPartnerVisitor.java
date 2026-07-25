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
package org.apache.iceberg.schema;

import java.util.List;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 带 partner 结构的 Schema 访问者基类。
 *
 * <p>所属模块：iceberg-core（schema 子包）。职责：在遍历 Iceberg {@link Schema}/{@link Type} 树的同时， 携带一个外部 "partner"
 * 结构（如另一个 schema、列序号映射等）协同访问，用于 schema 对齐/比较等场景。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>PartnerAccessors 解耦：通过接口抽象如何从 partner 取出字段/键/值/元素对应的子 partner， 使访问者本身不绑定具体 partner 类型。
 *   <li>泛型 P/R：P 为 partner 类型，R 为访问返回类型，子类按需具化。
 *   <li>递归 visit：提供静态 {@link #visit} 入口，按 STRUCT/LIST/MAP 三种嵌套类型递归。
 * </ul>
 *
 * <p>上下游关系：被 core 中需要做 schema 比对/投影的代码继承使用。
 */
public abstract class SchemaWithPartnerVisitor<P, R> {

  public interface PartnerAccessors<P> {

    P fieldPartner(P partnerStruct, int fieldId, String name);

    P mapKeyPartner(P partnerMap);

    P mapValuePartner(P partnerMap);

    P listElementPartner(P partnerList);
  }

  public static <P, T> T visit(
      Schema schema,
      P partner,
      SchemaWithPartnerVisitor<P, T> visitor,
      PartnerAccessors<P> accessors) {
    return visitor.schema(schema, partner, visit(schema.asStruct(), partner, visitor, accessors));
  }

  public static <P, T> T visit(
      Type type, P partner, SchemaWithPartnerVisitor<P, T> visitor, PartnerAccessors<P> accessors) {
    switch (type.typeId()) {
      case STRUCT:
        Types.StructType struct = type.asNestedType().asStructType();
        List<T> results = Lists.newArrayListWithExpectedSize(struct.fields().size());
        for (Types.NestedField field : struct.fields()) {
          P fieldPartner =
              partner != null
                  ? accessors.fieldPartner(partner, field.fieldId(), field.name())
                  : null;
          visitor.beforeField(field, fieldPartner);
          T result;
          try {
            result = visit(field.type(), fieldPartner, visitor, accessors);
          } finally {
            visitor.afterField(field, fieldPartner);
          }
          results.add(visitor.field(field, fieldPartner, result));
        }
        return visitor.struct(struct, partner, results);

      case LIST:
        Types.ListType list = type.asNestedType().asListType();
        T elementResult;

        Types.NestedField elementField = list.field(list.elementId());
        P partnerElement = partner != null ? accessors.listElementPartner(partner) : null;
        visitor.beforeListElement(elementField, partnerElement);
        try {
          elementResult = visit(list.elementType(), partnerElement, visitor, accessors);
        } finally {
          visitor.afterListElement(elementField, partnerElement);
        }

        return visitor.list(list, partner, elementResult);

      case MAP:
        Types.MapType map = type.asNestedType().asMapType();
        T keyResult;
        T valueResult;

        Types.NestedField keyField = map.field(map.keyId());
        P keyPartner = partner != null ? accessors.mapKeyPartner(partner) : null;
        visitor.beforeMapKey(keyField, keyPartner);
        try {
          keyResult = visit(map.keyType(), keyPartner, visitor, accessors);
        } finally {
          visitor.afterMapKey(keyField, keyPartner);
        }

        Types.NestedField valueField = map.field(map.valueId());
        P valuePartner = partner != null ? accessors.mapValuePartner(partner) : null;
        visitor.beforeMapValue(valueField, valuePartner);
        try {
          valueResult = visit(map.valueType(), valuePartner, visitor, accessors);
        } finally {
          visitor.afterMapValue(valueField, valuePartner);
        }

        return visitor.map(map, partner, keyResult, valueResult);

      default:
        return visitor.primitive(type.asPrimitiveType(), partner);
    }
  }

  /**
   * 进入一个 struct 字段前的回调钩子（默认空实现，子类可覆盖）。
   *
   * @param field 当前字段
   * @param partnerField 字段对应的 partner
   */
  public void beforeField(Types.NestedField field, P partnerField) {}

  /**
   * 离开一个 struct 字段后的回调钩子（默认空实现，子类可覆盖）。
   *
   * @param field 当前字段
   * @param partnerField 字段对应的 partner
   */
  public void afterField(Types.NestedField field, P partnerField) {}

  /**
   * 进入 list 元素字段前的钩子，默认委托给 {@link #beforeField}。
   *
   * @param elementField list 元素字段
   * @param partnerField 元素对应的 partner
   */
  public void beforeListElement(Types.NestedField elementField, P partnerField) {
    beforeField(elementField, partnerField);
  }

  /**
   * 离开 list 元素字段后的钩子，默认委托给 {@link #afterField}。
   *
   * @param elementField list 元素字段
   * @param partnerField 元素对应的 partner
   */
  public void afterListElement(Types.NestedField elementField, P partnerField) {
    afterField(elementField, partnerField);
  }

  /**
   * 进入 map key 字段前的钩子，默认委托给 {@link #beforeField}。
   *
   * @param keyField map key 字段
   * @param partnerField key 对应的 partner
   */
  public void beforeMapKey(Types.NestedField keyField, P partnerField) {
    beforeField(keyField, partnerField);
  }

  /**
   * 离开 map key 字段后的钩子，默认委托给 {@link #afterField}。
   *
   * @param keyField map key 字段
   * @param partnerField key 对应的 partner
   */
  public void afterMapKey(Types.NestedField keyField, P partnerField) {
    afterField(keyField, partnerField);
  }

  /**
   * 进入 map value 字段前的钩子，默认委托给 {@link #beforeField}。
   *
   * @param valueField map value 字段
   * @param partnerField value 对应的 partner
   */
  public void beforeMapValue(Types.NestedField valueField, P partnerField) {
    beforeField(valueField, partnerField);
  }

  /**
   * 离开 map value 字段后的钩子，默认委托给 {@link #afterField}。
   *
   * @param valueField map value 字段
   * @param partnerField value 对应的 partner
   */
  public void afterMapValue(Types.NestedField valueField, P partnerField) {
    afterField(valueField, partnerField);
  }

  /**
   * 访问整个 Schema 时的回调（默认返回 null，子类按需覆盖）。
   *
   * @param schema 当前 schema
   * @param partner schema 级 partner
   * @param structResult struct 遍历得到的结果
   * @return 自定义返回值
   */
  public R schema(Schema schema, P partner, R structResult) {
    return null;
  }

  /**
   * 访问 StructType 时的回调。
   *
   * @param struct 当前 struct 类型
   * @param partner 当前 partner
   * @param fieldResults 各字段的结果列表（顺序与字段一致）
   * @return 自定义返回值
   */
  public R struct(Types.StructType struct, P partner, List<R> fieldResults) {
    return null;
  }

  /**
   * 访问单个 struct 字段时的回调，可结合字段元数据与子结果产出字段级结果。
   *
   * @param field 当前字段
   * @param partner 字段 partner
   * @param fieldResult 字段类型遍历得到的结果
   * @return 自定义返回值
   */
  public R field(Types.NestedField field, P partner, R fieldResult) {
    return null;
  }

  /**
   * 访问 ListType 时的回调。
   *
   * @param list 当前 list 类型
   * @param partner 当前 partner
   * @param elementResult 元素类型遍历的结果
   * @return 自定义返回值
   */
  public R list(Types.ListType list, P partner, R elementResult) {
    return null;
  }

  /**
   * 访问 MapType 时的回调。
   *
   * @param map 当前 map 类型
   * @param partner 当前 partner
   * @param keyResult key 类型遍历的结果
   * @param valueResult value 类型遍历的结果
   * @return 自定义返回值
   */
  public R map(Types.MapType map, P partner, R keyResult, R valueResult) {
    return null;
  }

  /**
   * 访问原始类型时的回调。
   *
   * @param primitive 当前原始类型
   * @param partner 当前 partner
   * @return 自定义返回值
   */
  public R primitive(Type.PrimitiveType primitive, P partner) {
    return null;
  }
}
