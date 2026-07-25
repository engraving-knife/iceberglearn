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

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.Encoder;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：通用 Avro 数据写入器，将内存对象写入 Avro 编码流。
 *
 * <p>所属模块：iceberg-core（avro 子包，提供 Iceberg 与 Avro 编解码的桥接）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link MetricsAwareDatumWriter}，按 Avro Schema 把对象树序列化到 {@link Encoder}。
 *   <li>基于访问者模式遍历 Avro Schema，为每个节点构建对应的 {@link ValueWriter}，组合成写管线。
 *   <li>收集写入过程中的字段级指标（min/max/count 等），供 Manifest 文件统计使用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Schema 驱动：写入器结构完全由 Avro Schema 决定，{@link #setSchema} 时一次性构建整棵 ValueWriter 树，后续 {@link
 *       #write} 调用零反射开销。
 *   <li>逻辑类型适配：在 {@link WriteBuilder#primitive} 中按 Iceberg 逻辑类型（date/decimal/uuid 等） 路由到专用
 *       writer，保证 Iceberg 类型语义正确编码。
 *   <li>LogicalMap 特殊处理：Iceberg 把 map 编码为 array-of-struct（key+value）以保留插入顺序， 此处在 array 访问中识别
 *       LogicalMap 并转用 arrayMap writer。
 * </ul>
 *
 * <p>上下游关系：由 Iceberg Avro 写入链路（如 Manifest 文件写入、元数据序列化）调用； 依赖 {@link AvroSchemaVisitor} 做 Schema
 * 遍历，依赖 {@link ValueWriters} 提供基础类型 writer。
 *
 * @param <T> 待写入数据类型
 */
public class GenericAvroWriter<T> implements MetricsAwareDatumWriter<T> {
  private ValueWriter<T> writer = null;

  /**
   * 工厂方法，按给定 Schema 创建写入器实例。
   *
   * @param schema Avro 数据 schema
   * @param <D> 数据类型
   * @return 已绑定 schema 的 {@link GenericAvroWriter}
   */
  public static <D> GenericAvroWriter<D> create(Schema schema) {
    return new GenericAvroWriter<>(schema);
  }

  /**
   * 构造器，立即按 schema 构建写管线。
   *
   * @param schema Avro 数据 schema
   */
  GenericAvroWriter(Schema schema) {
    setSchema(schema);
  }

  /**
   * 切换/重置写入 schema，重建整棵 ValueWriter 树。
   *
   * <p>逻辑步骤：通过 {@link AvroSchemaVisitor#visit} 遍历 schema，由 {@link WriteBuilder} 为每种节点类型构造
   * writer，最终返回根 writer。
   *
   * @param schema 新的 Avro schema
   */
  @Override
  @SuppressWarnings("unchecked")
  public void setSchema(Schema schema) {
    this.writer = (ValueWriter<T>) AvroSchemaVisitor.visit(schema, new WriteBuilder());
  }

  /**
   * 把单个数据写入编码器。
   *
   * @param datum 待写入数据
   * @param out Avro 编码器
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(T datum, Encoder out) throws IOException {
    writer.write(datum, out);
  }

  /**
   * 返回本次写入过程中累积的字段指标流。
   *
   * @return 字段指标流（min/max/count 等）
   */
  @Override
  public Stream<FieldMetrics> metrics() {
    return writer.metrics();
  }

  /**
   * Schema 访问者，按 Avro Schema 节点类型构建 {@link ValueWriter} 树。
   *
   * <p>设计意图：把"按 schema 构造 writer"的逻辑与具体 writer 实现解耦，每种结构类型 （record/union/array/map/primitive）独立处理。
   */
  private static class WriteBuilder extends AvroSchemaVisitor<ValueWriter<?>> {
    private WriteBuilder() {}

    /**
     * 为 record 类型构建 writer，按字段顺序组合子 writer。
     *
     * @param record record schema
     * @param names 字段名列表
     * @param fields 各字段 writer
     * @return record writer
     */
    @Override
    public ValueWriter<?> record(Schema record, List<String> names, List<ValueWriter<?>> fields) {
      return ValueWriters.record(fields);
    }

    /**
     * 为 union（Iceberg 仅允许 nullable union）构建 writer。
     *
     * <p>逻辑步骤：
     *
     * <ol>
     *   <li>校验 union 必须包含 null 且仅有两个分支（Iceberg 不支持多分支 union）。
     *   <li>根据 null 是在 index 0 还是 1，返回对应分支的 option writer。
     * </ol>
     *
     * @param union union schema
     * @param options 各分支 writer
     * @return option writer
     */
    @Override
    public ValueWriter<?> union(Schema union, List<ValueWriter<?>> options) {
      Preconditions.checkArgument(
          options.contains(ValueWriters.nulls()),
          "Cannot create writer for non-option union: %s",
          union);
      Preconditions.checkArgument(
          options.size() == 2, "Cannot create writer for non-option union: %s", union);
      if (union.getTypes().get(0).getType() == Schema.Type.NULL) {
        return ValueWriters.option(0, options.get(1));
      } else {
        return ValueWriters.option(1, options.get(0));
      }
    }

    /**
     * 为 array 类型构建 writer；若携带 {@link LogicalMap} 标记则按 map-as-array 编码。
     *
     * @param array array schema
     * @param elementWriter 元素 writer
     * @return array 或 arrayMap writer
     */
    @Override
    public ValueWriter<?> array(Schema array, ValueWriter<?> elementWriter) {
      if (array.getLogicalType() instanceof LogicalMap) {
        ValueWriters.StructWriter<?> keyValueWriter = (ValueWriters.StructWriter<?>) elementWriter;
        return ValueWriters.arrayMap(keyValueWriter.writer(0), keyValueWriter.writer(1));
      }

      return ValueWriters.array(elementWriter);
    }

    /**
     * 为 map 类型构建 writer，键固定为字符串。
     *
     * @param map map schema
     * @param valueWriter 值 writer
     * @return map writer
     */
    @Override
    public ValueWriter<?> map(Schema map, ValueWriter<?> valueWriter) {
      return ValueWriters.map(ValueWriters.strings(), valueWriter);
    }

    /**
     * 为基本类型构建 writer。
     *
     * <p>逻辑步骤：先按逻辑类型（date/time/decimal/uuid 等）匹配专用 writer； 若无逻辑类型则按 Avro 基础类型（INT/LONG/STRING
     * 等）匹配。不支持的类型抛出异常。
     *
     * @param primitive 基本 schema
     * @return 对应的 ValueWriter
     */
    @Override
    public ValueWriter<?> primitive(Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            return ValueWriters.ints();

          case "time-micros":
            return ValueWriters.longs();

          case "timestamp-micros":
            return ValueWriters.longs();

          case "decimal":
            LogicalTypes.Decimal decimal = (LogicalTypes.Decimal) logicalType;
            return ValueWriters.decimal(decimal.getPrecision(), decimal.getScale());

          case "uuid":
            return ValueWriters.uuids();

          default:
            throw new IllegalArgumentException("Unsupported logical type: " + logicalType);
        }
      }

      switch (primitive.getType()) {
        case NULL:
          return ValueWriters.nulls();
        case BOOLEAN:
          return ValueWriters.booleans();
        case INT:
          return ValueWriters.ints();
        case LONG:
          return ValueWriters.longs();
        case FLOAT:
          return ValueWriters.floats();
        case DOUBLE:
          return ValueWriters.doubles();
        case STRING:
          return ValueWriters.strings();
        case FIXED:
          return ValueWriters.genericFixed(primitive.getFixedSize());
        case BYTES:
          return ValueWriters.byteBuffers();
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
