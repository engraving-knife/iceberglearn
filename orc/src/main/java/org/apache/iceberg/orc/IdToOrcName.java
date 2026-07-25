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
package org.apache.iceberg.orc;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 生成 Iceberg 字段 id → ORC 限定列名映射的 Schema 访问器。
 *
 * <p>所属模块：iceberg-orc。用于把 Iceberg 的字段 id 映射为 ORC 读取/谓词下推所需的列路径名。
 *
 * <p>职责：遍历 Iceberg Schema 树，维护当前字段路径栈，为每个有 id 的节点生成 ORC 限定名 （用 "." 连接，各段用反引号包裹以支持特殊字符），存入 idToName
 * 映射。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>列名用反引号包裹（{@code `field`}），ORC 可正确解析含特殊字符的列名； 列名内含反引号则用双反引号转义。
 *   <li>list 元素名统一为 {@code _elem}，map 的 key/value 名为 {@code _key}/{@code _value}， 与 ORC 内部命名约定一致。
 *   <li>继承 {@link TypeUtil.SchemaVisitor}，利用 before/after 钩子维护路径栈， 无需手动传递上下文。
 * </ul>
 *
 * <p>命名对照（Iceberg vs ORC）：
 *
 * <pre>{@code
 *                                      Iceberg           ORC
 * field                                field             field
 * struct -> field                      struct.field      struct.field
 * list -> element                      list.element      list._elem
 * list -> struct element -> field      list.field        list._elem.field
 * map -> key                           map.key            map._key
 * map -> value                         map.value         map._value
 * map -> struct key -> field           map.key.field     map._key.field
 * map -> struct value -> field         map.field         map._value.field
 * }</pre>
 *
 * <p>上下游关系：被 {@link ORCSchemaUtil#idToOrcName} 调用；结果用于 {@link ExpressionToSearchArgument} 的字段
 * id→列名映射。
 */
class IdToOrcName extends TypeUtil.SchemaVisitor<Map<Integer, String>> {
  private static final Joiner DOT = Joiner.on(".");

  private final Deque<String> fieldNames = Lists.newLinkedList();
  private final Map<Integer, String> idToName = Maps.newHashMap();

  @Override
  public void beforeField(Types.NestedField field) {
    fieldNames.push(field.name());
  }

  @Override
  public void afterField(Types.NestedField field) {
    fieldNames.pop();
  }

  @Override
  public void beforeListElement(Types.NestedField elementField) {
    fieldNames.push("_elem");
  }

  @Override
  public void afterListElement(Types.NestedField elementField) {
    fieldNames.pop();
  }

  @Override
  public void beforeMapKey(Types.NestedField keyField) {
    fieldNames.push("_key");
  }

  @Override
  public void afterMapKey(Types.NestedField keyField) {
    fieldNames.pop();
  }

  @Override
  public void beforeMapValue(Types.NestedField valueField) {
    fieldNames.push("_value");
  }

  @Override
  public void afterMapValue(Types.NestedField valueField) {
    fieldNames.pop();
  }

  @Override
  public Map<Integer, String> schema(Schema schema, Map<Integer, String> structResult) {
    return structResult;
  }

  @Override
  public Map<Integer, String> struct(
      Types.StructType struct, List<Map<Integer, String>> fieldResults) {
    return idToName;
  }

  @Override
  public Map<Integer, String> field(Types.NestedField field, Map<Integer, String> fieldResult) {
    addField(field.name(), field.fieldId());
    return idToName;
  }

  @Override
  public Map<Integer, String> list(Types.ListType list, Map<Integer, String> elementResult) {
    addField("_elem", list.elementId());
    return idToName;
  }

  @Override
  public Map<Integer, String> map(
      Types.MapType map, Map<Integer, String> keyResult, Map<Integer, String> valueResult) {
    addField("_key", map.keyId());
    addField("_value", map.valueId());
    return idToName;
  }

  @Override
  public Map<Integer, String> primitive(Type.PrimitiveType primitive) {
    return idToName;
  }

  /**
   * 把当前路径 + name 拼接为 ORC 限定名，加入 idToName 映射。
   *
   * <p>逻辑：从 fieldNames 栈的 descendingIterator 取出从根到当前的路径段， 追加本节点 name，各段用 {@link #quoteName} 包裹反引号后用
   * "." 连接。
   */
  private void addField(String name, int fieldId) {
    List<String> fullName = Lists.newArrayList(fieldNames.descendingIterator());
    fullName.add(name);
    idToName.put(fieldId, DOT.join(Iterables.transform(fullName, this::quoteName)));
  }

  /**
   * 用反引号包裹列名，内部反引号用双反引号转义。
   *
   * <p>设计要点：ORC 使用反引号界定含特殊字符的列名，与 SQL 的标识符引用规则一致。
   */
  private String quoteName(String name) {
    String escapedName =
        name.replace("`", "``"); // if the column name contains ` then escape it with another `
    return "`" + escapedName + "`";
  }
}
