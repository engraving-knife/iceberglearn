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
package org.apache.iceberg.spark.data;

import java.util.Deque;
import java.util.List;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Type.Repetition;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * Parquet 类型与 Spark 类型协同遍历的访问者基类。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，data 子包负责 Spark 数据 格式与 Iceberg 之间的读写转换）。
 *
 * <p>职责：同时遍历 Parquet 的 {@link Type} 和对应的 Spark {@link DataType}， 在两者的结构对应关系上调用访问者的
 * message/struct/list/map/primitive 方法， 由子类决定如何利用这一遍历结果（如构建 Parquet 写入器或读取器）。
 *
 * <p>设计意图：Parquet 与 Spark 的类型系统不同（如 Parquet 用 GroupType 表示 struct/list/map，Spark 用
 * StructType/ArrayType/MapType），本类封装两者结构对齐 的遍历逻辑，通过访问者模式将"遍历"与"处理"分离。使用 fieldNames 栈跟踪当前 字段路径，支持
 * definition/repetition level 计算。
 *
 * <p>上下游关系：被 {@link SparkParquetWriters.WriteBuilder} 用于构建 Parquet 写入器， 也被 Spark Parquet 读取路径使用；依赖
 * Parquet schema 和 Spark schema 的结构匹配。
 *
 * @param <T> 访问者方法返回的 Java 类型
 */
public class ParquetWithSparkSchemaVisitor<T> {
  private final Deque<String> fieldNames = Lists.newLinkedList();

  /**
   * 静态入口方法：用给定访问者遍历 Spark 类型与 Parquet 类型的对应结构。
   *
   * <p>逻辑：根据 Parquet type 的种类分派——MessageType 调用 message()， 原始类型调用 primitive()，GroupType 根据
   * OriginalType（LIST/MAP/其他）分别 处理数组和映射，其余视为 struct。遍历过程中维护 fieldNames 栈用于路径跟踪。
   *
   * @param sType Spark 数据类型
   * @param type Parquet 类型
   * @param visitor 访问者实例
   * @param <T> 返回类型
   * @return 访问者遍历后产生的结果
   */
  public static <T> T visit(DataType sType, Type type, ParquetWithSparkSchemaVisitor<T> visitor) {
    Preconditions.checkArgument(sType != null, "Invalid DataType: null");
    if (type instanceof MessageType) {
      Preconditions.checkArgument(
          sType instanceof StructType, "Invalid struct: %s is not a struct", sType);
      StructType struct = (StructType) sType;
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
                !group.isRepetition(Repetition.REPEATED),
                "Invalid list: top-level group is repeated: %s",
                group);
            Preconditions.checkArgument(
                group.getFieldCount() == 1,
                "Invalid list: does not contain single repeated field: %s",
                group);

            GroupType repeatedElement = group.getFields().get(0).asGroupType();
            Preconditions.checkArgument(
                repeatedElement.isRepetition(Repetition.REPEATED),
                "Invalid list: inner group is not repeated");
            Preconditions.checkArgument(
                repeatedElement.getFieldCount() <= 1,
                "Invalid list: repeated group is not a single field: %s",
                group);

            Preconditions.checkArgument(
                sType instanceof ArrayType, "Invalid list: %s is not an array", sType);
            ArrayType array = (ArrayType) sType;
            StructField element =
                new StructField(
                    "element", array.elementType(), array.containsNull(), Metadata.empty());

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
                !group.isRepetition(Repetition.REPEATED),
                "Invalid map: top-level group is repeated: %s",
                group);
            Preconditions.checkArgument(
                group.getFieldCount() == 1,
                "Invalid map: does not contain single repeated field: %s",
                group);

            GroupType repeatedKeyValue = group.getType(0).asGroupType();
            Preconditions.checkArgument(
                repeatedKeyValue.isRepetition(Repetition.REPEATED),
                "Invalid map: inner group is not repeated");
            Preconditions.checkArgument(
                repeatedKeyValue.getFieldCount() <= 2,
                "Invalid map: repeated group does not have 2 fields");

            Preconditions.checkArgument(
                sType instanceof MapType, "Invalid map: %s is not a map", sType);
            MapType map = (MapType) sType;
            StructField keyField = new StructField("key", map.keyType(), false, Metadata.empty());
            StructField valueField =
                new StructField(
                    "value", map.valueType(), map.valueContainsNull(), Metadata.empty());

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
          sType instanceof StructType, "Invalid struct: %s is not a struct", sType);
      StructType struct = (StructType) sType;
      return visitor.struct(struct, group, visitFields(struct, group, visitor));
    }
  }
  /** 执行 visitField 相关操作。 */
  private static <T> T visitField(
      StructField sField, Type field, ParquetWithSparkSchemaVisitor<T> visitor) {
    visitor.fieldNames.push(field.getName());
    try {
      return visit(sField.dataType(), field, visitor);
    } finally {
      visitor.fieldNames.pop();
    }
  }
  /** 执行 visitFields 相关操作。 */
  private static <T> List<T> visitFields(
      StructType struct, GroupType group, ParquetWithSparkSchemaVisitor<T> visitor) {
    StructField[] sFields = struct.fields();
    Preconditions.checkArgument(
        sFields.length == group.getFieldCount(), "Structs do not match: %s and %s", struct, group);
    List<T> results = Lists.newArrayListWithExpectedSize(group.getFieldCount());
    for (int i = 0; i < sFields.length; i += 1) {
      Type field = group.getFields().get(i);
      StructField sField = sFields[i];
      Preconditions.checkArgument(
          field.getName().equals(AvroSchemaUtil.makeCompatibleName(sField.name())),
          "Structs do not match: field %s != %s",
          field.getName(),
          sField.name());
      results.add(visitField(sField, field, visitor));
    }

    return results;
  }

  /**
   * 访问 Parquet 消息（顶层 MessageType）时的回调。默认返回 null，子类按需覆写。
   *
   * @param sStruct 对应的 Spark StructType
   * @param message Parquet MessageType
   * @param fields 子字段访问结果列表
   * @return 访问结果
   */
  public T message(StructType sStruct, MessageType message, List<T> fields) {
    return null;
  }

  /**
   * 访问 struct 类型时的回调。默认返回 null，子类按需覆写。
   *
   * @param sStruct 对应的 Spark StructType
   * @param struct Parquet GroupType
   * @param fields 子字段访问结果列表
   * @return 访问结果
   */
  public T struct(StructType sStruct, GroupType struct, List<T> fields) {
    return null;
  }

  /**
   * 访问 list/array 类型时的回调。默认返回 null，子类按需覆写。
   *
   * @param sArray 对应的 Spark ArrayType
   * @param array Parquet GroupType（LIST 注解）
   * @param element 元素类型的访问结果
   * @return 访问结果
   */
  public T list(ArrayType sArray, GroupType array, T element) {
    return null;
  }

  /**
   * 访问 map 类型时的回调。默认返回 null，子类按需覆写。
   *
   * @param sMap 对应的 Spark MapType
   * @param map Parquet GroupType（MAP 注解）
   * @param key 键类型的访问结果
   * @param value 值类型的访问结果
   * @return 访问结果
   */
  public T map(MapType sMap, GroupType map, T key, T value) {
    return null;
  }

  /**
   * 访问原始类型时的回调。默认返回 null，子类按需覆写。
   *
   * @param sPrimitive 对应的 Spark DataType
   * @param primitive Parquet PrimitiveType
   * @return 访问结果
   */
  public T primitive(DataType sPrimitive, PrimitiveType primitive) {
    return null;
  }

  /**
   * 返回当前正在访问的字段路径（从根到当前字段名的数组）。
   *
   * <p>逻辑：fieldNames 是栈结构（push 顺序为深度优先），通过 descendingIterator 获取从栈底到栈顶的顺序即为根到当前的路径。
   *
   * @return 当前字段路径数组
   */
  protected String[] currentPath() {
    return Lists.newArrayList(fieldNames.descendingIterator()).toArray(new String[0]);
  }

  /**
   * 返回当前路径加上指定名称后的完整字段路径。
   *
   * @param name 要追加的字段名
   * @return 包含指定名称的完整路径数组
   */
  protected String[] path(String name) {
    List<String> list = Lists.newArrayList(fieldNames.descendingIterator());
    list.add(name);
    return list.toArray(new String[0]);
  }
}
