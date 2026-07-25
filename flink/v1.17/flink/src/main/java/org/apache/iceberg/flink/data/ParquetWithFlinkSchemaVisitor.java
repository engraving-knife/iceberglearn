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

import java.util.Deque;
import java.util.List;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.RowType.RowField;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * 以 Flink {@link LogicalType} 与 Parquet {@link Type} 为双 partner 的模式访问器基类。
 *
 * <p>所属模块：iceberg-flink，用于在 Flink LogicalType 与 Parquet schema 之间做结构化遍历。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>递归遍历 Parquet schema（MessageType/GroupType/PrimitiveType），同时携带对应的 Flink LogicalType。
 *   <li>处理 Parquet 的 LIST/MAP 注解编码（兼容非标准编码），拆解出元素/键值并回调 visitor。
 *   <li>维护字段名栈（{@link #fieldNames}）以支持路径追踪。
 * </ul>
 *
 * <p>设计意图：把 Parquet 复杂的集合类型编码规则集中在本类处理，子类只需实现 message/struct/list/map/primitive 回调；通过 fieldNames
 * 栈在递归过程中维护当前字段路径，供子类获取 {@link #currentPath()} / {@link #path(String)}。
 *
 * <p>上下游关系：被 Flink Parquet 读写器构造逻辑继承使用。
 */
public class ParquetWithFlinkSchemaVisitor<T> {
  private final Deque<String> fieldNames = Lists.newLinkedList();

  /**
   * 入口：遍历 Flink LogicalType 与 Parquet Type 的对应结构并回调 visitor。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若是 {@link MessageType}（顶层消息），校验 Flink 类型为 {@link RowType}，遍历各字段后回调 visitor.message。
   *   <li>若是基本类型，直接回调 visitor.primitive。
   *   <li>否则为 group 类型：若带 LIST/MAP 注解，按 Parquet 集合编码规范校验并拆解元素/键值，递归后回调 visitor.list/map； 若无注解，按
   *       struct 处理，遍历字段后回调 visitor.struct。
   * </ul>
   *
   * 集合编码处理中对非标准结构（如重复顶层 group、字段数不符）做了严格的 Preconditions 校验。
   *
   * @param sType Flink 逻辑类型
   * @param type Parquet 类型
   * @param visitor 访问器
   * @param <T> 产物类型
   * @return 遍历结果
   */
  public static <T> T visit(
      LogicalType sType, Type type, ParquetWithFlinkSchemaVisitor<T> visitor) {
    Preconditions.checkArgument(sType != null, "Invalid DataType: null");
    if (type instanceof MessageType) {
      Preconditions.checkArgument(
          sType instanceof RowType, "Invalid struct: %s is not a struct", sType);
      RowType struct = (RowType) sType;
      return visitor.message(
          struct, (MessageType) type, visitFields(struct, type.asGroupType(), visitor));
    } else if (type.isPrimitive()) {
      return visitor.primitive(sType, type.asPrimitiveType());
    } else {
      // if not a primitive, the typeId must be a group
      GroupType group = type.asGroupType();
      OriginalType annotation = group.getOriginalType();
      if (annotation != null) {
        switch (annotation) {
          case LIST:
            Preconditions.checkArgument(
                !group.isRepetition(Type.Repetition.REPEATED),
                "Invalid list: top-level group is repeated: %s",
                group);
            Preconditions.checkArgument(
                group.getFieldCount() == 1,
                "Invalid list: does not contain single repeated field: %s",
                group);

            GroupType repeatedElement = group.getFields().get(0).asGroupType();
            Preconditions.checkArgument(
                repeatedElement.isRepetition(Type.Repetition.REPEATED),
                "Invalid list: inner group is not repeated");
            Preconditions.checkArgument(
                repeatedElement.getFieldCount() <= 1,
                "Invalid list: repeated group is not a single field: %s",
                group);

            Preconditions.checkArgument(
                sType instanceof ArrayType, "Invalid list: %s is not an array", sType);
            ArrayType array = (ArrayType) sType;
            RowType.RowField element =
                new RowField(
                    "element", array.getElementType(), "element of " + array.asSummaryString());

            visitor.fieldNames.push(repeatedElement.getName());
            try {
              T elementResult = null;
              if (repeatedElement.getFieldCount() > 0) {
                elementResult = visitField(element, repeatedElement.getType(0), visitor);
              }

              return visitor.list(array, group, elementResult);

            } finally {
              visitor.fieldNames.pop();
            }

          case MAP:
            Preconditions.checkArgument(
                !group.isRepetition(Type.Repetition.REPEATED),
                "Invalid map: top-level group is repeated: %s",
                group);
            Preconditions.checkArgument(
                group.getFieldCount() == 1,
                "Invalid map: does not contain single repeated field: %s",
                group);

            GroupType repeatedKeyValue = group.getType(0).asGroupType();
            Preconditions.checkArgument(
                repeatedKeyValue.isRepetition(Type.Repetition.REPEATED),
                "Invalid map: inner group is not repeated");
            Preconditions.checkArgument(
                repeatedKeyValue.getFieldCount() <= 2,
                "Invalid map: repeated group does not have 2 fields");

            Preconditions.checkArgument(
                sType instanceof MapType, "Invalid map: %s is not a map", sType);
            MapType map = (MapType) sType;
            RowField keyField =
                new RowField("key", map.getKeyType(), "key of " + map.asSummaryString());
            RowField valueField =
                new RowField("value", map.getValueType(), "value of " + map.asSummaryString());

            visitor.fieldNames.push(repeatedKeyValue.getName());
            try {
              T keyResult = null;
              T valueResult = null;
              switch (repeatedKeyValue.getFieldCount()) {
                case 2:
                  // if there are 2 fields, both key and value are projected
                  keyResult = visitField(keyField, repeatedKeyValue.getType(0), visitor);
                  valueResult = visitField(valueField, repeatedKeyValue.getType(1), visitor);
                  break;
                case 1:
                  // if there is just one, use the name to determine what it is
                  Type keyOrValue = repeatedKeyValue.getType(0);
                  if (keyOrValue.getName().equalsIgnoreCase("key")) {
                    keyResult = visitField(keyField, keyOrValue, visitor);
                    // value result remains null
                  } else {
                    valueResult = visitField(valueField, keyOrValue, visitor);
                    // key result remains null
                  }
                  break;
                default:
                  // both results will remain null
              }

              return visitor.map(map, group, keyResult, valueResult);

            } finally {
              visitor.fieldNames.pop();
            }

          default:
        }
      }
      Preconditions.checkArgument(
          sType instanceof RowType, "Invalid struct: %s is not a struct", sType);
      RowType struct = (RowType) sType;
      return visitor.struct(struct, group, visitFields(struct, group, visitor));
    }
  }

  /**
   * 访问单个字段：将字段名压栈后递归 visit，退出时弹栈，保证路径追踪正确。
   *
   * @param sField Flink 字段定义
   * @param field Parquet 字段类型
   * @param visitor 访问器
   * @param <T> 产物类型
   * @return 字段遍历结果
   */
  private static <T> T visitField(
      RowType.RowField sField, Type field, ParquetWithFlinkSchemaVisitor<T> visitor) {
    visitor.fieldNames.push(field.getName());
    try {
      return visit(sField.getType(), field, visitor);
    } finally {
      visitor.fieldNames.pop();
    }
  }

  /**
   * 遍历 struct 的所有字段，校验字段数量与名称（经 Avro 命名兼容处理）一致后逐字段递归。
   *
   * @param struct Flink RowType
   * @param group Parquet GroupType
   * @param visitor 访问器
   * @param <T> 产物类型
   * @return 各字段遍历结果列表
   */
  private static <T> List<T> visitFields(
      RowType struct, GroupType group, ParquetWithFlinkSchemaVisitor<T> visitor) {
    List<RowType.RowField> sFields = struct.getFields();
    Preconditions.checkArgument(
        sFields.size() == group.getFieldCount(), "Structs do not match: %s and %s", struct, group);
    List<T> results = Lists.newArrayListWithExpectedSize(group.getFieldCount());
    for (int i = 0; i < sFields.size(); i += 1) {
      Type field = group.getFields().get(i);
      RowType.RowField sField = sFields.get(i);
      Preconditions.checkArgument(
          field.getName().equals(AvroSchemaUtil.makeCompatibleName(sField.getName())),
          "Structs do not match: field %s != %s",
          field.getName(),
          sField.getName());
      results.add(visitField(sField, field, visitor));
    }

    return results;
  }

  /** 顶层消息回调，子类重写以生成产物。默认返回 null。 */
  public T message(RowType sStruct, MessageType message, List<T> fields) {
    return null;
  }

  /** struct 回调，子类重写以生成产物。默认返回 null。 */
  public T struct(RowType sStruct, GroupType struct, List<T> fields) {
    return null;
  }

  /** list 回调，子类重写以生成产物。默认返回 null。 */
  public T list(ArrayType sArray, GroupType array, T element) {
    return null;
  }

  /** map 回调，子类重写以生成产物。默认返回 null。 */
  public T map(MapType sMap, GroupType map, T key, T value) {
    return null;
  }

  /** 基本类型回调，子类重写以生成产物。默认返回 null。 */
  public T primitive(LogicalType sPrimitive, PrimitiveType primitive) {
    return null;
  }

  /**
   * 返回当前字段路径（从根到当前层级的字段名数组），基于 fieldNames 栈的逆序。
   *
   * @return 字段路径数组
   */
  protected String[] currentPath() {
    return Lists.newArrayList(fieldNames.descendingIterator()).toArray(new String[0]);
  }

  /**
   * 返回追加一个字段名后的路径。
   *
   * @param name 追加的字段名
   * @return 完整字段路径数组
   */
  protected String[] path(String name) {
    List<String> list = Lists.newArrayList(fieldNames.descendingIterator());
    list.add(name);
    return list.toArray(new String[0]);
  }
}
