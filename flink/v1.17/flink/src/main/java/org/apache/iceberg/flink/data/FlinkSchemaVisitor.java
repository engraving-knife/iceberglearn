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
package org.apache.iceberg.flink.data;

import java.util.List;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * Flink {@link LogicalType} 与 Iceberg {@link Type} 之间的结构化模式访问器基类。
 *
 * <p>所属模块：iceberg-flink，用于在 Flink 类型系统与 Iceberg 类型系统之间做递归遍历。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按 Iceberg 类型结构（struct/map/list/primitive）递归遍历，同时携带对应的 Flink LogicalType。
 *   <li>在进入/退出字段、map 键值、list 元素时提供 hook（before/after 回调）。
 *   <li>子类通过重写 {@link #record}/{@link #list}/{@link #map}/{@link #primitive} 生成所需产物。
 * </ul>
 *
 * <p>设计意图：经典访问者模式，将类型遍历流程与具体处理逻辑解耦；通过 before/after 钩子支持 需要感知字段层级的场景（如统计、路径追踪）。
 *
 * <p>上下游关系：被 Flink 各类按类型分发的组件（读写器构造、schema 转换等）继承或调用。
 */
abstract class FlinkSchemaVisitor<T> {

  /**
   * 入口方法：以 {@link Schema} 的 struct 形式启动遍历。
   *
   * @param flinkType Flink RowType
   * @param schema Iceberg Schema
   * @param visitor 访问器实现
   * @param <T> 遍历产物类型
   * @return 遍历结果
   */
  static <T> T visit(RowType flinkType, Schema schema, FlinkSchemaVisitor<T> visitor) {
    return visit(flinkType, schema.asStruct(), visitor);
  }

  /**
   * 按 Iceberg 类型 id 递归遍历 Flink LogicalType 与 Iceberg Type 的对应结构。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>STRUCT：委托 {@link #visitRecord} 处理。
   *   <li>MAP：分别遍历键、值，并在前后触发 {@code beforeMapKey}/{@code afterMapKey} 与 {@code
   *       beforeMapValue}/{@code afterMapValue} 钩子，最后回调 visitor.map。
   *   <li>LIST：遍历元素并在前后触发 {@code beforeListElement}/{@code afterListElement}，最后回调 visitor.list。
   *   <li>其它：按基本类型回调 visitor.primitive。
   * </ul>
   *
   * @param flinkType 当前 Flink 逻辑类型
   * @param iType 当前 Iceberg 类型
   * @param visitor 访问器
   * @param <T> 产物类型
   * @return 遍历结果
   */
  private static <T> T visit(LogicalType flinkType, Type iType, FlinkSchemaVisitor<T> visitor) {
    switch (iType.typeId()) {
      case STRUCT:
        return visitRecord(flinkType, iType.asStructType(), visitor);

      case MAP:
        MapType mapType = (MapType) flinkType;
        Types.MapType iMapType = iType.asMapType();
        T key;
        T value;

        Types.NestedField keyField = iMapType.field(iMapType.keyId());
        visitor.beforeMapKey(keyField);
        try {
          key = visit(mapType.getKeyType(), iMapType.keyType(), visitor);
        } finally {
          visitor.afterMapKey(keyField);
        }

        Types.NestedField valueField = iMapType.field(iMapType.valueId());
        visitor.beforeMapValue(valueField);
        try {
          value = visit(mapType.getValueType(), iMapType.valueType(), visitor);
        } finally {
          visitor.afterMapValue(valueField);
        }

        return visitor.map(iMapType, key, value, mapType.getKeyType(), mapType.getValueType());

      case LIST:
        ArrayType listType = (ArrayType) flinkType;
        Types.ListType iListType = iType.asListType();
        T element;

        Types.NestedField elementField = iListType.field(iListType.elementId());
        visitor.beforeListElement(elementField);
        try {
          element = visit(listType.getElementType(), iListType.elementType(), visitor);
        } finally {
          visitor.afterListElement(elementField);
        }

        return visitor.list(iListType, element, listType.getElementType());

      default:
        return visitor.primitive(iType.asPrimitiveType(), flinkType);
    }
  }

  /**
   * 遍历 struct（record）类型的各字段。
   *
   * <p>逻辑：校验 flinkType 为 {@link RowType}；按 Iceberg struct 字段顺序，依据字段名在 RowType 中 定位索引，逐字段递归
   * visit，并在前后触发 {@code beforeField}/{@code afterField}； 最终以字段结果列表与字段类型列表回调 visitor.record。
   *
   * @param flinkType Flink 逻辑类型（须为 RowType）
   * @param struct Iceberg struct 类型
   * @param visitor 访问器
   * @param <T> 产物类型
   * @return record 遍历结果
   */
  private static <T> T visitRecord(
      LogicalType flinkType, Types.StructType struct, FlinkSchemaVisitor<T> visitor) {
    Preconditions.checkArgument(flinkType instanceof RowType, "%s is not a RowType.", flinkType);
    RowType rowType = (RowType) flinkType;

    int fieldSize = struct.fields().size();
    List<T> results = Lists.newArrayListWithExpectedSize(fieldSize);
    List<LogicalType> fieldTypes = Lists.newArrayListWithExpectedSize(fieldSize);
    List<Types.NestedField> nestedFields = struct.fields();

    for (int i = 0; i < fieldSize; i++) {
      Types.NestedField iField = nestedFields.get(i);
      int fieldIndex = rowType.getFieldIndex(iField.name());
      Preconditions.checkArgument(
          fieldIndex >= 0, "NestedField: %s is not found in flink RowType: %s", iField, rowType);

      LogicalType fieldFlinkType = rowType.getTypeAt(fieldIndex);

      fieldTypes.add(fieldFlinkType);

      visitor.beforeField(iField);
      try {
        results.add(visit(fieldFlinkType, iField.type(), visitor));
      } finally {
        visitor.afterField(iField);
      }
    }

    return visitor.record(struct, results, fieldTypes);
  }

  /**
   * struct 类型访问回调，子类重写以生成 struct 产物。默认返回 null。
   *
   * @param iStruct Iceberg struct 类型
   * @param results 各字段遍历结果
   * @param fieldTypes 各字段对应的 Flink LogicalType
   * @return 产物
   */
  public T record(Types.StructType iStruct, List<T> results, List<LogicalType> fieldTypes) {
    return null;
  }

  /**
   * list 类型访问回调，子类重写以生成 list 产物。默认返回 null。
   *
   * @param iList Iceberg list 类型
   * @param element 元素遍历结果
   * @param elementType 元素对应的 Flink LogicalType
   * @return 产物
   */
  public T list(Types.ListType iList, T element, LogicalType elementType) {
    return null;
  }

  /**
   * map 类型访问回调，子类重写以生成 map 产物。默认返回 null。
   *
   * @param iMap Iceberg map 类型
   * @param key 键遍历结果
   * @param value 值遍历结果
   * @param keyType 键对应的 Flink LogicalType
   * @param valueType 值对应的 Flink LogicalType
   * @return 产物
   */
  public T map(Types.MapType iMap, T key, T value, LogicalType keyType, LogicalType valueType) {
    return null;
  }

  /**
   * 基本类型访问回调，子类重写以生成基本类型产物。默认返回 null。
   *
   * @param iPrimitive Iceberg 基本类型
   * @param flinkPrimitive 对应的 Flink LogicalType
   * @return 产物
   */
  public T primitive(Type.PrimitiveType iPrimitive, LogicalType flinkPrimitive) {
    return null;
  }

  /** 进入某字段前的钩子，默认空实现。 */
  public void beforeField(Types.NestedField field) {}

  /** 退出某字段后的钩子，默认空实现。 */
  public void afterField(Types.NestedField field) {}

  /** 进入 list 元素前的钩子，默认委托 {@link #beforeField}。 */
  public void beforeListElement(Types.NestedField elementField) {
    beforeField(elementField);
  }

  /** 退出 list 元素后的钩子，默认委托 {@link #afterField}。 */
  public void afterListElement(Types.NestedField elementField) {
    afterField(elementField);
  }

  /** 进入 map 键前的钩子，默认委托 {@link #beforeField}。 */
  public void beforeMapKey(Types.NestedField keyField) {
    beforeField(keyField);
  }

  /** 退出 map 键后的钩子，默认委托 {@link #afterField}。 */
  public void afterMapKey(Types.NestedField keyField) {
    afterField(keyField);
  }

  /** 进入 map 值前的钩子，默认委托 {@link #beforeField}。 */
  public void beforeMapValue(Types.NestedField valueField) {
    beforeField(valueField);
  }

  /** 退出 map 值后的钩子，默认委托 {@link #afterField}。 */
  public void afterMapValue(Types.NestedField valueField) {
    afterField(valueField);
  }
}
