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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.orc.TypeDescription;

/**
 * ORC Schema 树的通用访问器基类。
 *
 * <p>所属模块：iceberg-orc。提供对 ORC {@link TypeDescription} 树的自底向上遍历框架， 子类只需实现 record/list/map/primitive
 * 四个方法即可完成对整棵树的访问。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #visit}：递归遍历 ORC TypeDescription，按 category 分派到对应方法。
 *   <li>{@link #visitSchema}：从根 struct 的子字段开始遍历（返回列表）。
 *   <li>维护字段名路径栈（fieldNames），子类可通过 {@link #currentPath()} 获取当前路径。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>访问者模式：把树遍历逻辑与节点处理逻辑解耦，子类无需关心遍历细节。
 *   <li>before/after 钩子维护路径栈：进入字段时 push 名字，退出时 pop， 使子类在 record/list/map/primitive 中可通过
 *       currentFieldName()/currentPath() 苿到上下文。
 *   <li>list 元素名默认 _elem，map 的 key/value 名默认 _key/_value，与 ORC 命名约定一致。
 * </ul>
 *
 * <p>上下游关系：被 {@link ApplyNameMapping}、{@link HasIds}、{@link RemoveIds}、 {@link
 * EstimateOrcAvgWidthVisitor}、{@link OrcToIcebergVisitor}、{@link OrcMetrics} 等继承。
 */
public abstract class OrcSchemaVisitor<T> {

  private final Deque<String> fieldNames = Lists.newLinkedList();

  /**
   * 从根 schema 的子字段开始遍历，返回各字段访问结果的列表。
   *
   * <p>设计要点：要求 schema.getId() == 0（根节点），不访问根 struct 本身。
   */
  public static <T> List<T> visitSchema(TypeDescription schema, OrcSchemaVisitor<T> visitor) {
    Preconditions.checkArgument(schema.getId() == 0, "TypeDescription must be root schema.");

    List<TypeDescription> fields = schema.getChildren();
    List<String> names = schema.getFieldNames();

    return visitFields(fields, names, visitor);
  }

  /**
   * 递归遍历 ORC TypeDescription 树。
   *
   * <p>逻辑：按 category 分派——STRUCT 走 visitRecord，LIST 先访问元素再调 visitor.list， MAP 先访问 key/value 再调
   * visitor.map，其余走 visitor.primitive。 访问子节点前后调用 before/after 钩子维护路径栈。
   *
   * @throws UNION 类型不支持
   */
  public static <T> T visit(TypeDescription schema, OrcSchemaVisitor<T> visitor) {
    switch (schema.getCategory()) {
      case STRUCT:
        return visitRecord(schema, visitor);

      case UNION:
        throw new UnsupportedOperationException("Cannot handle " + schema);

      case LIST:
        final T elementResult;

        TypeDescription element = schema.getChildren().get(0);
        visitor.beforeElementField(element);
        try {
          elementResult = visit(element, visitor);
        } finally {
          visitor.afterElementField(element);
        }
        return visitor.list(schema, elementResult);

      case MAP:
        final T keyResult;
        final T valueResult;

        TypeDescription key = schema.getChildren().get(0);
        visitor.beforeKeyField(key);
        try {
          keyResult = visit(key, visitor);
        } finally {
          visitor.afterKeyField(key);
        }

        TypeDescription value = schema.getChildren().get(1);
        visitor.beforeValueField(value);
        try {
          valueResult = visit(value, visitor);
        } finally {
          visitor.afterValueField(value);
        }
        return visitor.map(schema, keyResult, valueResult);

      default:
        return visitor.primitive(schema);
    }
  }

  private static <T> List<T> visitFields(
      List<TypeDescription> fields, List<String> names, OrcSchemaVisitor<T> visitor) {
    Preconditions.checkArgument(
        fields.size() == names.size(), "Not all fields have names in ORC struct");

    List<T> results = Lists.newArrayListWithExpectedSize(fields.size());
    for (int i = 0; i < fields.size(); i++) {
      TypeDescription field = fields.get(i);
      String name = names.get(i);
      visitor.beforeField(name, field);
      try {
        results.add(visit(field, visitor));
      } finally {
        visitor.afterField(name, field);
      }
    }
    return results;
  }

  private static <T> T visitRecord(TypeDescription record, OrcSchemaVisitor<T> visitor) {
    List<TypeDescription> fields = record.getChildren();
    List<String> names = record.getFieldNames();

    return visitor.record(record, names, visitFields(fields, names, visitor));
  }

  public String elementName() {
    return "_elem";
  }

  public String keyName() {
    return "_key";
  }

  public String valueName() {
    return "_value";
  }

  public String currentFieldName() {
    return fieldNames.peek();
  }

  public void beforeField(String name, TypeDescription type) {
    fieldNames.push(name);
  }

  public void afterField(String name, TypeDescription type) {
    fieldNames.pop();
  }

  public void beforeElementField(TypeDescription element) {
    beforeField(elementName(), element);
  }

  public void afterElementField(TypeDescription element) {
    afterField(elementName(), element);
  }

  public void beforeKeyField(TypeDescription key) {
    beforeField(keyName(), key);
  }

  public void afterKeyField(TypeDescription key) {
    afterField(keyName(), key);
  }

  public void beforeValueField(TypeDescription value) {
    beforeField(valueName(), value);
  }

  public void afterValueField(TypeDescription value) {
    afterField(valueName(), value);
  }

  public T record(TypeDescription record, List<String> names, List<T> fields) {
    return null;
  }

  public T list(TypeDescription array, T element) {
    return null;
  }

  public T map(TypeDescription map, T key, T value) {
    return null;
  }

  public T primitive(TypeDescription primitive) {
    return null;
  }

  /** 返回当前字段名路径（从根到当前节点），基于 fieldNames 栈的逆序。 */
  protected String[] currentPath() {
    return Lists.newArrayList(fieldNames.descendingIterator()).toArray(new String[0]);
  }

  /** 返回当前路径追加 name 后的完整路径。 */
  protected String[] path(String name) {
    List<String> list = Lists.newArrayList(fieldNames.descendingIterator());
    list.add(name);
    return list.toArray(new String[0]);
  }
}
