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
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.iceberg.mapping.MappedField;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

/**
 * 文件级说明：将 {@link NameMapping} 应用到 Parquet schema，为缺失字段 ID 的 Parquet 类型补充 ID。
 *
 * <p>所属模块：iceberg-parquet（Parquet schema 处理工具，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历 Parquet schema 树（通过 {@link ParquetTypeVisitor}），对每个字段按"字段名路径" 在 NameMapping 中查找对应的 ID。
 *   <li>若找到 ID，则用 {@code withId()} 为该 Parquet 类型附加字段 ID；否则保持原样。
 *   <li>对 list element、map key/value 等特殊字段名进行规范化（统一为 element/key/value）， 使不同命名风格的 schema 能匹配同一
 *       NameMapping。
 * </ul>
 *
 * <p>设计意图：Iceberg 依赖字段 ID 进行 schema 演进与列投影，但历史 Parquet 文件（Hive/Spark 旧版写入）可能没有字段 ID。NameMapping
 * 提供"字段名路径→ID"的映射规则，本类将其物化到 Parquet schema 上，使这些旧文件也能按 ID 方式读取。
 *
 * <p>上下游关系：依赖 NameMapping（映射规则）、ParquetTypeVisitor（遍历框架）； 被 ParquetSchemaUtil 在读取无 ID 文件时调用。
 */
class ApplyNameMapping extends ParquetTypeVisitor<Type> {
  private static final String LIST_ELEMENT_NAME = "element";
  private static final String MAP_KEY_NAME = "key";
  private static final String MAP_VALUE_NAME = "value";
  private final NameMapping nameMapping;
  private final Deque<String> fieldNames = Lists.newLinkedList();

  ApplyNameMapping(NameMapping nameMapping) {
    this.nameMapping = nameMapping;
  }

  /**
   * 处理顶层 message：用子字段构建新的 MessageType，保留原 message 名称。
   *
   * @param message 原 Parquet message 类型
   * @param fields 已处理（已附加 ID）的子字段列表
   * @return 带 ID 的新 MessageType
   */
  @Override
  public Type message(MessageType message, List<Type> fields) {
    Types.MessageTypeBuilder builder = Types.buildMessage();
    fields.stream().filter(Objects::nonNull).forEach(builder::addField);

    return builder.named(message.getName());
  }

  /**
   * 处理 struct 类型：用处理后的子字段重建 struct，并按 NameMapping 查找当前路径对应的 ID。
   *
   * @param struct 原 Parquet struct 类型
   * @param types 已处理的子类型列表
   * @return 带 ID 的新 struct 类型（若 NameMapping 未命中则不带 ID）
   */
  @Override
  public Type struct(GroupType struct, List<Type> types) {
    MappedField field = nameMapping.find(currentPath());
    List<Type> actualTypes = types.stream().filter(Objects::nonNull).collect(Collectors.toList());
    Type structType = struct.withNewFields(actualTypes);

    return field == null ? structType : structType.withId(field.id());
  }

  /**
   * 处理 list 类型：重建 LIST group 并查找 ID。
   *
   * <p>逻辑：判断 list element 的 repetition 类型——若 element 本身是 REPEATED， 直接作为子字段添加；否则包装为
   * repeatedGroup。最后按 NameMapping 查找当前路径的 ID。
   *
   * @param list 原 Parquet LIST group 类型
   * @param elementType 已处理的元素类型
   * @return 带 ID 的新 list 类型
   */
  @Override
  public Type list(GroupType list, Type elementType) {
    Preconditions.checkArgument(elementType != null, "List type must have element field");

    Type listElement = ParquetSchemaUtil.determineListElementType(list);
    MappedField field = nameMapping.find(currentPath());

    Types.GroupBuilder<GroupType> listBuilder =
        Types.buildGroup(list.getRepetition()).as(LogicalTypeAnnotation.listType());
    if (listElement.isRepetition(Type.Repetition.REPEATED)) {
      listBuilder.addFields(elementType);
    } else {
      listBuilder.repeatedGroup().addFields(elementType).named(list.getFieldName(0));
    }
    Type listType = listBuilder.named(list.getName());

    return field == null ? listType : listType.withId(field.id());
  }

  /**
   * 处理 map 类型：用处理后的 key/value 重建 MAP group 并查找 ID。
   *
   * @param map 原 Parquet MAP group 类型
   * @param keyType 已处理的 key 类型
   * @param valueType 已处理的 value 类型
   * @return 带 ID 的新 map 类型
   */
  @Override
  public Type map(GroupType map, Type keyType, Type valueType) {
    Preconditions.checkArgument(
        keyType != null && valueType != null, "Map type must have both key field and value field");

    MappedField field = nameMapping.find(currentPath());
    Type mapType =
        Types.buildGroup(map.getRepetition())
            .as(LogicalTypeAnnotation.mapType())
            .repeatedGroup()
            .addFields(keyType, valueType)
            .named(map.getFieldName(0))
            .named(map.getName());

    return field == null ? mapType : mapType.withId(field.id());
  }

  /**
   * 处理原始类型：按当前字段名路径在 NameMapping 中查找 ID 并附加。
   *
   * @param primitive 原 Parquet 原始类型
   * @return 带 ID 的新原始类型（若未命中则保持原样）
   */
  @Override
  public Type primitive(PrimitiveType primitive) {
    MappedField field = nameMapping.find(currentPath());
    return field == null ? primitive : primitive.withId(field.id());
  }

  @Override
  public void beforeField(Type type) {
    fieldNames.push(type.getName());
  }

  @Override
  public void afterField(Type type) {
    fieldNames.pop();
  }

  @Override
  public void beforeElementField(Type element) {
    // 将字段名规范化为 "element"，使不同命名的结构能匹配同一 NameMapping
    fieldNames.push(LIST_ELEMENT_NAME);
  }

  @Override
  public void beforeKeyField(Type key) {
    // 将字段名规范化为 "key"，使不同命名的结构能匹配同一 NameMapping
    fieldNames.push(MAP_KEY_NAME);
  }

  @Override
  public void beforeValueField(Type key) {
    // 将字段名规范化为 "value"，使不同命名的结构能匹配同一 NameMapping
    fieldNames.push(MAP_VALUE_NAME);
  }

  @Override
  public void beforeRepeatedElement(Type element) {
    // do not add the repeated element's name
  }

  @Override
  public void afterRepeatedElement(Type element) {
    // do not remove the repeated element's name
  }

  @Override
  public void beforeRepeatedKeyValue(Type keyValue) {
    // do not add the repeated element's name
  }

  @Override
  public void afterRepeatedKeyValue(Type keyValue) {
    // do not remove the repeated element's name
  }

  @Override
  protected String[] currentPath() {
    return Lists.newArrayList(fieldNames.descendingIterator()).toArray(new String[0]);
  }

  @Override
  protected String[] path(String name) {
    List<String> list = Lists.newArrayList(fieldNames.descendingIterator());
    list.add(name);
    return list.toArray(new String[0]);
  }
}
