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
package org.apache.iceberg.parquet;

import java.util.Deque;
import java.util.List;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：Parquet schema 访问者基类，递归遍历 Parquet 类型树并按节点类型回调。
 *
 * <p>所属模块：iceberg-parquet（schema 遍历核心，被 reader/writer 构造与类型转换复用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link #visit} 静态入口，按 MessageType/Primitive/Group(List/Map/Struct) 分派到对应 visitor 方法。
 *   <li>在遍历过程中维护字段名路径栈（{@link #fieldNames}），供子类查询当前路径。
 *   <li>提供 before/after 钩子，让子类在进入/退出字段、list 元素、map key/value 时执行副作用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>访问者模式：把 schema 树结构与具体处理逻辑解耦，{@link MessageTypeToType}、 {@code
 *       BaseParquetWriter.WriteBuilder} 等只需重写关心的方法。
 *   <li>路径栈：通过 push/pop 字段名，子类可随时调用 {@link #currentPath()} / {@link #path(String)}
 *       获取从根到当前节点的字段名数组，用于查询 def/rep level。
 *   <li>三层级 list 兼容：支持 Parquet 的两层级与三层级 list 编码。
 * </ul>
 *
 * <p>上下游关系：被 {@link MessageTypeToType}、{@code BaseParquetWriter}、 {@link ParquetAvroWriter}
 * 等继承使用；依赖 parquet schema。
 */
public class ParquetTypeVisitor<T> {
  /** 当前遍历路径上的字段名栈，栈顶为当前字段名。 */
  private final Deque<String> fieldNames = Lists.newLinkedList();

  /**
   * 从给定 Parquet 类型节点开始递归访问。
   *
   * <p>逻辑：若是 MessageType，先递归访问各字段再回调 {@link #message}； 若是 Primitive，直接回调 {@link #primitive}；否则按
   * Group 的 OriginalType （LIST/MAP）分派到 {@link #visitList}/{@link #visitMap}，其余按 struct 处理。
   *
   * @param type Parquet 类型节点
   * @param visitor 访问者实例
   * @param <T> 访问返回类型
   * @return 访问结果
   */
  public static <T> T visit(Type type, ParquetTypeVisitor<T> visitor) {
    if (type instanceof MessageType) {
      return visitor.message((MessageType) type, visitFields(type.asGroupType(), visitor));

    } else if (type.isPrimitive()) {
      return visitor.primitive(type.asPrimitiveType());

    } else {
      // 非 primitive 则必为 group
      GroupType group = type.asGroupType();
      OriginalType annotation = group.getOriginalType();
      if (annotation != null) {
        switch (annotation) {
          case LIST:
            return visitList(group, visitor);

          case MAP:
            return visitMap(group, visitor);

          default:
        }
      }

      return visitor.struct(group, visitFields(group, visitor));
    }
  }

  /**
   * 访问 list 节点，兼容两层级与三层级 list 编码。
   *
   * <p>逻辑：校验 list 仅含一个 repeated 字段；通过 {@link ParquetSchemaUtil#determineListElementType} 定位元素；
   * 若元素本身为 REPEATED 则两层级，直接访问元素；否则走三层级 {@link #visitThreeLevelList}。
   *
   * @param list list group 类型
   * @param visitor 访问者
   * @param <T> 返回类型
   * @return list 访问结果
   */
  private static <T> T visitList(GroupType list, ParquetTypeVisitor<T> visitor) {
    Preconditions.checkArgument(
        list.getFieldCount() == 1,
        "Invalid list: does not contain single repeated field: %s",
        list);

    Type repeatedElement = list.getFields().get(0);
    Preconditions.checkArgument(
        repeatedElement.isRepetition(Type.Repetition.REPEATED),
        "Invalid list: inner group is not repeated");

    Type listElement = ParquetSchemaUtil.determineListElementType(list);
    if (listElement.isRepetition(Type.Repetition.REPEATED)) {
      T elementResult = visitListElement(listElement, visitor);
      return visitor.list(list, elementResult);
    } else {
      return visitThreeLevelList(list, repeatedElement, listElement, visitor);
    }
  }

  /**
   * 访问三层级 list（repeated group 包含 element 字段）。
   *
   * <p>逻辑：进入 repeated element 前调用 beforeRepeatedElement，访问元素， 退出时调用 afterRepeatedElement，确保路径栈正确维护。
   */
  private static <T> T visitThreeLevelList(
      GroupType list, Type repeated, Type listElement, ParquetTypeVisitor<T> visitor) {
    visitor.beforeRepeatedElement(repeated);
    try {
      T elementResult = visitListElement(listElement, visitor);
      return visitor.list(list, elementResult);
    } finally {
      visitor.afterRepeatedElement(repeated);
    }
  }

  /** 访问 list 元素字段，前后维护 element 字段路径栈。 */
  private static <T> T visitListElement(Type listElement, ParquetTypeVisitor<T> visitor) {
    T elementResult = null;

    visitor.beforeElementField(listElement);
    try {
      elementResult = visit(listElement, visitor);
    } finally {
      visitor.afterElementField(listElement);
    }

    return elementResult;
  }

  /**
   * 访问 map 节点。
   *
   * <p>逻辑：校验 map 顶层非 REPEATED 且仅含一个 repeated key-value group； 进入 repeated group 后按字段数处理：2 字段时
   * key、value 都访问； 1 字段时按名称判断是 key 还是 value；0 字段时两者都为 null。
   *
   * @param map map group 类型
   * @param visitor 访问者
   * @param <T> 返回类型
   * @return map 访问结果
   */
  private static <T> T visitMap(GroupType map, ParquetTypeVisitor<T> visitor) {
    Preconditions.checkArgument(
        !map.isRepetition(Type.Repetition.REPEATED),
        "Invalid map: top-level group is repeated: %s",
        map);
    Preconditions.checkArgument(
        map.getFieldCount() == 1, "Invalid map: does not contain single repeated field: %s", map);

    GroupType repeatedKeyValue = map.getType(0).asGroupType();
    Preconditions.checkArgument(
        repeatedKeyValue.isRepetition(Type.Repetition.REPEATED),
        "Invalid map: inner group is not repeated");
    Preconditions.checkArgument(
        repeatedKeyValue.getFieldCount() <= 2,
        "Invalid map: repeated group does not have 2 fields");

    visitor.beforeRepeatedKeyValue(repeatedKeyValue);
    try {
      T keyResult = null;
      T valueResult = null;
      switch (repeatedKeyValue.getFieldCount()) {
        case 2:
          // 2 个字段时 key、value 都投影
          Type keyType = repeatedKeyValue.getType(0);
          visitor.beforeKeyField(keyType);
          try {
            keyResult = visit(keyType, visitor);
          } finally {
            visitor.afterKeyField(keyType);
          }
          Type valueType = repeatedKeyValue.getType(1);
          visitor.beforeValueField(valueType);
          try {
            valueResult = visit(valueType, visitor);
          } finally {
            visitor.afterValueField(valueType);
          }
          break;

        case 1:
          // 仅 1 个字段时按名称判断是 key 还是 value
          Type keyOrValue = repeatedKeyValue.getType(0);
          if (keyOrValue.getName().equalsIgnoreCase("key")) {
            visitor.beforeKeyField(keyOrValue);
            try {
              keyResult = visit(keyOrValue, visitor);
            } finally {
              visitor.afterKeyField(keyOrValue);
            }
            // value 结果保持 null
          } else {
            visitor.beforeValueField(keyOrValue);
            try {
              valueResult = visit(keyOrValue, visitor);
            } finally {
              visitor.afterValueField(keyOrValue);
            }
            // key 结果保持 null
          }
          break;

        default:
          // key、value 结果均保持 null
      }

      return visitor.map(map, keyResult, valueResult);

    } finally {
      visitor.afterRepeatedKeyValue(repeatedKeyValue);
    }
  }

  /**
   * 访问 group 的所有字段，逐字段调用 before/after 钩子并递归访问。
   *
   * @param group struct/message group
   * @param visitor 访问者
   * @param <T> 返回类型
   * @return 各字段访问结果列表
   */
  private static <T> List<T> visitFields(GroupType group, ParquetTypeVisitor<T> visitor) {
    List<T> results = Lists.newArrayListWithExpectedSize(group.getFieldCount());
    for (Type field : group.getFields()) {
      visitor.beforeField(field);
      try {
        results.add(visit(field, visitor));
      } finally {
        visitor.afterField(field);
      }
    }

    return results;
  }

  /**
   * 访问 message 节点的回调，默认返回 null。
   *
   * @param message Parquet message 类型
   * @param fields 各字段访问结果
   * @return 处理结果
   */
  public T message(MessageType message, List<T> fields) {
    return null;
  }

  /**
   * 访问 struct 节点的回调，默认返回 null。
   *
   * @param struct struct group 类型
   * @param fields 各字段访问结果
   * @return 处理结果
   */
  public T struct(GroupType struct, List<T> fields) {
    return null;
  }

  /**
   * 访问 list 节点的回调，默认返回 null。
   *
   * @param array list group 类型
   * @param element 元素访问结果
   * @return 处理结果
   */
  public T list(GroupType array, T element) {
    return null;
  }

  /**
   * 访问 map 节点的回调，默认返回 null。
   *
   * @param map map group 类型
   * @param key key 访问结果
   * @param value value 访问结果
   * @return 处理结果
   */
  public T map(GroupType map, T key, T value) {
    return null;
  }

  /**
   * 访问 primitive 节点的回调，默认返回 null。
   *
   * @param primitive Parquet 原始类型
   * @return 处理结果
   */
  public T primitive(PrimitiveType primitive) {
    return null;
  }

  /** 进入字段前将字段名压入路径栈。 */
  public void beforeField(Type type) {
    fieldNames.push(type.getName());
  }

  /** 退出字段后弹出路径栈顶字段名。 */
  public void afterField(Type type) {
    fieldNames.pop();
  }

  /** 进入 list 的 repeated element 前的钩子，默认等同 beforeField。 */
  public void beforeRepeatedElement(Type element) {
    beforeField(element);
  }

  /** 退出 list 的 repeated element 后的钩子，默认等同 afterField。 */
  public void afterRepeatedElement(Type element) {
    afterField(element);
  }

  /** 进入 list element 字段前的钩子，默认等同 beforeField。 */
  public void beforeElementField(Type element) {
    beforeField(element);
  }

  /** 退出 list element 字段后的钩子，默认等同 afterField。 */
  public void afterElementField(Type element) {
    afterField(element);
  }

  /** 进入 map 的 repeated key-value group 前的钩子，默认等同 beforeField。 */
  public void beforeRepeatedKeyValue(Type keyValue) {
    beforeField(keyValue);
  }

  /** 退出 map 的 repeated key-value group 后的钩子，默认等同 afterField。 */
  public void afterRepeatedKeyValue(Type keyValue) {
    afterField(keyValue);
  }

  /** 进入 map key 字段前的钩子，默认等同 beforeField。 */
  public void beforeKeyField(Type key) {
    beforeField(key);
  }

  /** 退出 map key 字段后的钩子，默认等同 afterField。 */
  public void afterKeyField(Type key) {
    afterField(key);
  }

  /** 进入 map value 字段前的钩子，默认等同 beforeField。 */
  public void beforeValueField(Type value) {
    beforeField(value);
  }

  /** 退出 map value 字段后的钩子，默认等同 afterField。 */
  public void afterValueField(Type value) {
    afterField(value);
  }

  /**
   * 返回当前遍历路径上的字段名数组（从根到当前节点）。
   *
   * @return 字段名数组
   */
  protected String[] currentPath() {
    return Lists.newArrayList(fieldNames.descendingIterator()).toArray(new String[0]);
  }

  /**
   * 返回当前路径追加 {@code name} 后的字段名数组，用于查询某字段的 def/rep level。
   *
   * @param name 要追加的字段名
   * @return 完整字段名路径
   */
  protected String[] path(String name) {
    List<String> list = Lists.newArrayList(fieldNames.descendingIterator());
    list.add(name);
    return list.toArray(new String[0]);
  }
}
