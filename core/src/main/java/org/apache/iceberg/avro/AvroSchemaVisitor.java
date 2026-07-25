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
package org.apache.iceberg.avro;

import java.util.Deque;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * Avro Schema 访问器（Visitor 模式）：按声明顺序同步递归遍历 Avro {@link Schema}。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro schema 处理的基础工具，被多个 schema 转换/ 投影/裁剪算法复用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供统一的 Avro schema 遍历框架，支持 record/union/array/map/primitive 五种节点。
 *   <li>维护当前字段的“全名路径”（{@link #fieldNames()} 栈），供子类定位字段位置。
 *   <li>对递归 record 做环路检测，避免无限递归。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@link AvroCustomOrderSchemaVisitor} 不同，本类按字段声明顺序同步递归， 适用于“需要访问全部节点”的算法（如 schema 转
 *       Iceberg type）。
 *   <li>对 {@link LogicalMap}（Iceberg 用 array&lt;entry&gt; 表示 map）做特殊处理： array 节点若带 LogicalMap
 *       逻辑类型，则按 map 语义访问元素，不附加 "element" 字段名。
 *   <li>{@code recordLevels} 栈检测递归 record，因为 Iceberg schema 不支持递归类型。
 * </ul>
 *
 * <p>上下游关系：被 {@link SchemaToType}、{@link AvroSchemaUtil#convert(Schema)} 等调用； 是 Avro schema 与
 * Iceberg type 之间转换的遍历骨架。
 *
 * @param <T> 访问结果类型
 */
public abstract class AvroSchemaVisitor<T> {
  /**
   * 从给定 schema 开始同步遍历，按节点类型分派到 visitor 对应方法。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>RECORD：检测递归后压栈，按字段声明顺序递归每个字段（通过 {@code visitWithName} 维护字段名栈），最终调用 {@link #record(Schema,
   *       List, List)}。
   *   <li>UNION：递归每个分支，调用 {@link #union(Schema, List)}。
   *   <li>ARRAY：若为 {@link LogicalMap} 则不带名访问元素，否则带 "element" 名访问， 调用 {@link #array(Schema,
   *       Object)}。
   *   <li>MAP：带 "value" 名访问值类型，调用 {@link #map(Schema, Object)}。
   *   <li>其他：调用 {@link #primitive(Schema)}。
   * </ul>
   *
   * @param schema 待访问 schema
   * @param visitor 访问器
   * @param <T> 结果类型
   * @return 访问结果
   * @throws IllegalStateException 若遇到递归 record
   */
  public static <T> T visit(Schema schema, AvroSchemaVisitor<T> visitor) {
    switch (schema.getType()) {
      case RECORD:
        // check to make sure this hasn't been visited before
        String name = schema.getFullName();
        Preconditions.checkState(
            !visitor.recordLevels.contains(name), "Cannot process recursive Avro record %s", name);

        visitor.recordLevels.push(name);

        List<Schema.Field> fields = schema.getFields();
        List<String> names = Lists.newArrayListWithExpectedSize(fields.size());
        List<T> results = Lists.newArrayListWithExpectedSize(fields.size());
        for (Schema.Field field : schema.getFields()) {
          names.add(field.name());
          T result = visitWithName(field.name(), field.schema(), visitor);
          results.add(result);
        }

        visitor.recordLevels.pop();

        return visitor.record(schema, names, results);

      case UNION:
        List<Schema> types = schema.getTypes();
        List<T> options = Lists.newArrayListWithExpectedSize(types.size());
        for (Schema type : types) {
          options.add(visit(type, visitor));
        }
        return visitor.union(schema, options);

      case ARRAY:
        if (schema.getLogicalType() instanceof LogicalMap) {
          return visitor.array(schema, visit(schema.getElementType(), visitor));
        } else {
          return visitor.array(schema, visitWithName("element", schema.getElementType(), visitor));
        }

      case MAP:
        return visitor.map(schema, visitWithName("value", schema.getValueType(), visitor));

      default:
        return visitor.primitive(schema);
    }
  }

  private Deque<String> recordLevels = Lists.newLinkedList();
  private Deque<String> fieldNames = Lists.newLinkedList();

  /**
   * 返回当前字段名路径栈，子类可据此获取当前访问字段的完整路径名。
   *
   * @return 字段名栈（从根到当前字段）
   */
  protected Deque<String> fieldNames() {
    return fieldNames;
  }

  /**
   * 在访问字段 schema 前把字段名压栈，访问后弹栈，保证 {@link #fieldNames()} 始终反映 当前访问路径。
   *
   * @param name 字段名
   * @param schema 字段 schema
   * @param visitor 访问器
   * @param <T> 结果类型
   * @return 字段 schema 的访问结果
   */
  private static <T> T visitWithName(String name, Schema schema, AvroSchemaVisitor<T> visitor) {
    try {
      visitor.fieldNames.addLast(name);
      return visit(schema, visitor);
    } finally {
      visitor.fieldNames.removeLast();
    }
  }

  /**
   * 访问 record 节点，默认返回 null，子类按需覆写。
   *
   * @param record Avro record schema
   * @param names 字段名列表
   * @param fields 各字段访问结果
   * @return 访问结果
   */
  public T record(Schema record, List<String> names, List<T> fields) {
    return null;
  }

  /**
   * 访问 union 节点，默认返回 null，子类按需覆写。
   *
   * @param union Avro union schema
   * @param options 各分支访问结果
   * @return 访问结果
   */
  public T union(Schema union, List<T> options) {
    return null;
  }

  /**
   * 访问 array 节点，默认返回 null，子类按需覆写。
   *
   * @param array Avro array schema
   * @param element 元素类型访问结果
   * @return 访问结果
   */
  public T array(Schema array, T element) {
    return null;
  }

  /**
   * 访问 map 节点，默认返回 null，子类按需覆写。
   *
   * @param map Avro map schema
   * @param value 值类型访问结果
   * @return 访问结果
   */
  public T map(Schema map, T value) {
    return null;
  }

  /**
   * 访问原始类型节点，默认返回 null，子类按需覆写。
   *
   * @param primitive Avro 原始类型 schema
   * @return 访问结果
   */
  public T primitive(Schema primitive) {
    return null;
  }
}
