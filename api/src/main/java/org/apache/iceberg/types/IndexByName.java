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

import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 按名称索引访问者：遍历 schema 构建字段全名→字段 ID 的映射。
 *
 * <p>所属模块：iceberg-api（被 {@link TypeUtil#indexByName}、{@link TypeUtil#indexNameById} 使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构建全限定名（如 "a.b.c"）→字段 ID 的映射。
 *   <li>构建短名（省略 list 的 "element"/map 的 "value" 中间层）→字段 ID 的映射。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 fieldNames 栈维护当前字段路径，beforeField/afterField 压栈/弹栈。
 *   <li>短名优化：list 元素为 struct 时省略 "element"（如 "locations.latitude" 而非 "locations.element.latitude"），
 *       使字段名更自然；map value 同理省略 "value"。
 *   <li>quotingFunc 支持对字段名加引号，用于 SQL 等需要标识符引号的场景。
 *   <li>重复字段名检测：同名字段抛 ValidationException。
 * </ul>
 */
public class IndexByName extends TypeUtil.SchemaVisitor<Map<String, Integer>> {
  private static final Joiner DOT = Joiner.on(".");

  private final Deque<String> fieldNames = Lists.newLinkedList();
  private final Deque<String> shortFieldNames = Lists.newLinkedList();
  private final Map<String, Integer> nameToId = Maps.newHashMap();
  private final Map<String, Integer> shortNameToId = Maps.newHashMap();
  private final Function<String, String> quotingFunc;

  public IndexByName() {
    this(Function.identity());
  }

  public IndexByName(Function<String, String> quotingFunc) {
    this.quotingFunc = quotingFunc;
  }

  /**
   * 返回全名→字段 ID 的映射，包含不冲突的短名。
   *
   * <p>逻辑：先放入所有全限定名，再加入不与全名冲突的短名。
   *
   * @return 名→字段 ID 映射
   */
  public Map<String, Integer> byName() {
    ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
    builder.putAll(nameToId);
    // add all short names that do not conflict with canonical names
    shortNameToId.entrySet().stream()
        .filter(entry -> !nameToId.containsKey(entry.getKey()))
        .forEach(builder::put);
    return builder.build();
  }

  /**
   * 返回字段 ID→全限定名的映射（只含规范名，不含短名）。
   *
   * @return 字段 ID→名映射
   */
  public Map<Integer, String> byId() {
    ImmutableMap.Builder<Integer, String> builder = ImmutableMap.builder();
    nameToId.forEach((key, value) -> builder.put(value, key));
    return builder.build();
  }

  @Override
  public void beforeField(Types.NestedField field) {
    fieldNames.push(field.name());
    shortFieldNames.push(field.name());
  }

  @Override
  public void afterField(Types.NestedField field) {
    fieldNames.pop();
    shortFieldNames.pop();
  }

  @Override
  public void beforeListElement(Types.NestedField elementField) {
    fieldNames.push(elementField.name());

    // only add "element" to the short name if the element is not a struct, so that names are more
    // natural
    // for example, locations.latitude instead of locations.element.latitude
    if (!elementField.type().isStructType()) {
      shortFieldNames.push(elementField.name());
    }
  }

  @Override
  public void afterListElement(Types.NestedField elementField) {
    fieldNames.pop();

    // only remove "element" if it was added
    if (!elementField.type().isStructType()) {
      shortFieldNames.pop();
    }
  }

  @Override
  public void beforeMapKey(Types.NestedField keyField) {
    beforeField(keyField);
  }

  @Override
  public void afterMapKey(Types.NestedField keyField) {
    afterField(keyField);
  }

  @Override
  public void beforeMapValue(Types.NestedField valueField) {
    fieldNames.push(valueField.name());

    // only add "value" to the name if the value is not a struct, so that names are more natural
    if (!valueField.type().isStructType()) {
      shortFieldNames.push(valueField.name());
    }
  }

  @Override
  public void afterMapValue(Types.NestedField valueField) {
    fieldNames.pop();

    // only remove "value" if it was added
    if (!valueField.type().isStructType()) {
      shortFieldNames.pop();
    }
  }

  @Override
  public Map<String, Integer> schema(Schema schema, Map<String, Integer> structResult) {
    return nameToId;
  }

  @Override
  public Map<String, Integer> struct(
      Types.StructType struct, List<Map<String, Integer>> fieldResults) {
    return nameToId;
  }

  @Override
  public Map<String, Integer> field(Types.NestedField field, Map<String, Integer> fieldResult) {
    addField(field.name(), field.fieldId());
    return nameToId;
  }

  @Override
  public Map<String, Integer> list(Types.ListType list, Map<String, Integer> elementResult) {
    addField("element", list.elementId());
    return nameToId;
  }

  @Override
  public Map<String, Integer> map(
      Types.MapType map, Map<String, Integer> keyResult, Map<String, Integer> valueResult) {
    addField("key", map.keyId());
    addField("value", map.valueId());
    return nameToId;
  }

  @Override
  public Map<String, Integer> primitive(Type.PrimitiveType primitive) {
    return nameToId;
  }

  private void addField(String name, int fieldId) {
    String quotedName = quotingFunc.apply(name);

    String fullName = quotedName;
    if (!fieldNames.isEmpty()) {
      Iterator<String> quotedFieldNames =
          Iterators.transform(fieldNames.descendingIterator(), quotingFunc::apply);
      fullName = DOT.join(DOT.join(quotedFieldNames), quotedName);
    }

    Integer existingFieldId = nameToId.put(fullName, fieldId);
    ValidationException.check(
        existingFieldId == null,
        "Invalid schema: multiple fields for name %s: %s and %s",
        fullName,
        existingFieldId,
        fieldId);

    // also track the short name, if this is a nested field
    if (!shortFieldNames.isEmpty()) {
      Iterator<String> quotedShortFieldNames =
          Iterators.transform(shortFieldNames.descendingIterator(), quotingFunc::apply);
      String shortName = DOT.join(DOT.join(quotedShortFieldNames), quotedName);
      if (!shortNameToId.containsKey(shortName)) {
        shortNameToId.put(shortName, fieldId);
      }
    }
  }
}
