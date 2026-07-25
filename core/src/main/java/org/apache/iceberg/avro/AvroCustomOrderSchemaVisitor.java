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
import java.util.function.Supplier;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 自定义遍历顺序的 Avro Schema 访问器（Visitor 模式）。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 读写实现的 schema 处理基础工具）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以与字段声明顺序不同的自定义顺序遍历 Avro {@link Schema}（典型场景：先访问需要的字段， 暂缓其他字段），支持
 *       record/union/array/map/primitive 五种节点类型。
 *   <li>通过 {@link Supplier} 延迟求值机制，让子类决定何时真正递归子节点，从而支持 “按需访问/短路”等优化策略。
 *   <li>对递归 record 做环路检测，避免无限递归。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@link AvroSchemaVisitor} 的差异在于“访问顺序可定制”：默认 Visitor 按声明顺序 同步递归，而本类用 {@code Supplier<F>}
 *       包装字段结果，把递归时机交给调用方， 便于实现列裁剪、投影等需要选择性访问子树的算法。
 *   <li>{@code recordLevels} 栈用于检测递归 record（Avro 允许 record 自引用），命中即抛出 异常，因为 Iceberg schema 不支持递归类型。
 * </ul>
 *
 * <p>上下游关系：被 {@link PruneColumns}、{@link BuildAvroProjection} 等基于 schema 的 投影/裁剪算法继承使用；上游由 core 的
 * avro 读取/写入流程触发。
 *
 * @param <T> 非字段节点（record/union/array/map/primitive）的访问结果类型
 * @param <F> 字段（field）节点的访问结果类型
 */
abstract class AvroCustomOrderSchemaVisitor<T, F> {
  /**
   * 从给定 schema 开始遍历，把结果收集并交给 visitor 的对应方法。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>RECORD：先检查该 record 全名是否已在 {@code recordLevels} 栈中（防递归），压栈后 为每个字段构造一个 {@link
   *       VisitFieldFuture}（延迟求值），随后弹栈，最终调用 {@link #record(Schema, List, Iterable)}。
   *   <li>UNION：为每个分支构造 {@link VisitFuture}，调用 {@link #union(Schema, Iterable)}。
   *   <li>ARRAY/MAP：对元素/值类型构造 {@link VisitFuture}，调用对应方法。
   *   <li>其他：调用 {@link #primitive(Schema)}。
   * </ul>
   *
   * @param schema 待访问的 Avro schema
   * @param visitor 访问器实例
   * @param <T> 非字段节点结果类型
   * @param <F> 字段节点结果类型
   * @return 访问结果
   * @throws IllegalStateException 若遇到递归 record
   */
  public static <T, F> T visit(Schema schema, AvroCustomOrderSchemaVisitor<T, F> visitor) {
    switch (schema.getType()) {
      case RECORD:
        // check to make sure this hasn't been visited before
        String name = schema.getFullName();
        Preconditions.checkState(
            !visitor.recordLevels.contains(name), "Cannot process recursive Avro record %s", name);

        visitor.recordLevels.push(name);

        List<Schema.Field> fields = schema.getFields();
        List<String> names = Lists.newArrayListWithExpectedSize(fields.size());
        List<Supplier<F>> results = Lists.newArrayListWithExpectedSize(fields.size());
        for (Schema.Field field : schema.getFields()) {
          names.add(field.name());
          results.add(new VisitFieldFuture<>(field, visitor));
        }

        visitor.recordLevels.pop();

        return visitor.record(schema, names, Iterables.transform(results, Supplier::get));

      case UNION:
        List<Schema> types = schema.getTypes();
        List<Supplier<T>> options = Lists.newArrayListWithExpectedSize(types.size());
        for (Schema type : types) {
          options.add(new VisitFuture<>(type, visitor));
        }
        return visitor.union(schema, Iterables.transform(options, Supplier::get));

      case ARRAY:
        return visitor.array(schema, new VisitFuture<>(schema.getElementType(), visitor));

      case MAP:
        return visitor.map(schema, new VisitFuture<>(schema.getValueType(), visitor));

      default:
        return visitor.primitive(schema);
    }
  }

  private Deque<String> recordLevels = Lists.newLinkedList();

  /**
   * 访问 record 节点，默认返回 null，子类按需覆写。
   *
   * @param record Avro record schema
   * @param names 字段名列表
   * @param fields 各字段的访问结果（已按 {@link #field(Schema.Field, Supplier)} 求值）
   * @return 访问结果
   */
  public T record(Schema record, List<String> names, Iterable<F> fields) {
    return null;
  }

  /**
   * 访问字段节点，默认返回 null，子类按需覆写。
   *
   * <p>设计要点：传入 {@code Supplier<T>} 而非已求值结果，子类可决定是否真正递归 该字段的 schema（用于短路/裁剪）。
   *
   * @param field Avro 字段
   * @param fieldResult 字段 schema 的延迟访问结果
   * @return 字段访问结果
   */
  public F field(Schema.Field field, Supplier<T> fieldResult) {
    return null;
  }

  /**
   * 访问 union 节点，默认返回 null，子类按需覆写。
   *
   * @param union Avro union schema
   * @param options 各分支的访问结果
   * @return 访问结果
   */
  public T union(Schema union, Iterable<T> options) {
    return null;
  }

  /**
   * 访问 array 节点，默认返回 null，子类按需覆写。
   *
   * @param array Avro array schema
   * @param element 元素类型的延迟访问结果
   * @return 访问结果
   */
  public T array(Schema array, Supplier<T> element) {
    return null;
  }

  /**
   * 访问 map 节点，默认返回 null，子类按需覆写。
   *
   * @param map Avro map schema
   * @param value 值类型的延迟访问结果
   * @return 访问结果
   */
  public T map(Schema map, Supplier<T> value) {
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

  /** 对一个 schema 节点的延迟访问包装：在 {@link #get()} 时才真正递归调用 {@link #visit}。 */
  private static class VisitFuture<T, F> implements Supplier<T> {
    private final Schema schema;
    private final AvroCustomOrderSchemaVisitor<T, F> visitor;

    private VisitFuture(Schema schema, AvroCustomOrderSchemaVisitor<T, F> visitor) {
      this.schema = schema;
      this.visitor = visitor;
    }

    @Override
    public T get() {
      return visit(schema, visitor);
    }
  }

  /**
   * 对一个字段的延迟访问包装：在 {@link #get()} 时才调用 {@link #field(Schema.Field, Supplier)}，并把字段 schema 包装为
   * {@link VisitFuture} 传入，从而把“是否递归字段 schema”的决定权交给 field 方法。
   */
  private static class VisitFieldFuture<T, F> implements Supplier<F> {
    private final Schema.Field field;
    private final AvroCustomOrderSchemaVisitor<T, F> visitor;

    private VisitFieldFuture(Schema.Field field, AvroCustomOrderSchemaVisitor<T, F> visitor) {
      this.field = field;
      this.visitor = visitor;
    }

    @Override
    public F get() {
      return visitor.field(field, new VisitFuture<>(field.schema(), visitor));
    }
  }
}
