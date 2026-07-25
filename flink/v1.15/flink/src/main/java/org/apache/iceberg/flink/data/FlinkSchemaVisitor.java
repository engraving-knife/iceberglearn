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
 * Iceberg schema 与 Flink {@link LogicalType} 之间的结构化访问者基类。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：以 Iceberg 类型结构为骨架，按 struct/map/list/primitive 递归遍历 Flink
 * LogicalType，并在每个节点回调子类构造的结果。
 *
 * <p>设计意图：访问者模式，将类型遍历与具体构造逻辑解耦；上下游：被 Parquet/Avro 读写器调用， 上游接收 Iceberg {@link Schema} 与 Flink {@link
 * RowType}。
 */
abstract class FlinkSchemaVisitor<T> {

  /** 入口方法：以 schema 的 struct 形式开始遍历。 */
  static <T> T visit(RowType flinkType, Schema schema, FlinkSchemaVisitor<T> visitor) {
    return visit(flinkType, schema.asStruct(), visitor);
  }

  /**
   * 按 Iceberg 类型分支递归遍历 Flink 类型。
   *
   * <p>逻辑：根据 iType 的类型 ID 分派到 record/map/list/primitive 四种处理方式。
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

  /** 遍历 struct 类型，按字段名匹配 RowType 中的字段并递归。 */
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

  /** 处理 struct 类型结果，默认返回 null，由子类覆盖。 */
  public T record(Types.StructType iStruct, List<T> results, List<LogicalType> fieldTypes) {
    return null;
  }

  /** 处理 list 类型结果，默认返回 null，由子类覆盖。 */
  public T list(Types.ListType iList, T element, LogicalType elementType) {
    return null;
  }

  /** 处理 map 类型结果，默认返回 null，由子类覆盖。 */
  public T map(Types.MapType iMap, T key, T value, LogicalType keyType, LogicalType valueType) {
    return null;
  }

  /** 处理 primitive 类型结果，默认返回 null，由子类覆盖。 */
  public T primitive(Type.PrimitiveType iPrimitive, LogicalType flinkPrimitive) {
    return null;
  }

  /** 进入字段前的回调钩子，默认空实现。 */
  public void beforeField(Types.NestedField field) {}

  /** 离开字段后的回调钩子，默认空实现。 */
  public void afterField(Types.NestedField field) {}

  /** 进入 list 元素字段前的回调。 */
  public void beforeListElement(Types.NestedField elementField) {
    beforeField(elementField);
  }

  /** 离开 list 元素字段后的回调。 */
  public void afterListElement(Types.NestedField elementField) {
    afterField(elementField);
  }

  /** 进入 map key 字段前的回调。 */
  public void beforeMapKey(Types.NestedField keyField) {
    beforeField(keyField);
  }

  /** 离开 map key 字段后的回调。 */
  public void afterMapKey(Types.NestedField keyField) {
    afterField(keyField);
  }

  /** 进入 map value 字段前的回调。 */
  public void beforeMapValue(Types.NestedField valueField) {
    beforeField(valueField);
  }

  /** 离开 map value 字段后的回调。 */
  public void afterMapValue(Types.NestedField valueField) {
    afterField(valueField);
  }
}
