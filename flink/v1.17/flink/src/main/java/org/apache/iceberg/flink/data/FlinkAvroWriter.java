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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.Encoder;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.avro.MetricsAwareDatumWriter;
import org.apache.iceberg.avro.ValueWriter;
import org.apache.iceberg.avro.ValueWriters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：将 Flink {@link RowData} 写入 Avro 格式的写入器。
 *
 * <p>所属模块：iceberg-flink（数据写入子包 data），负责 Flink 行数据与 Avro 编码之间的适配。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link MetricsAwareDatumWriter}，在 Avro 写入过程中收集字段级指标。
 *   <li>通过 {@link AvroWithFlinkSchemaVisitor} 同时遍历 Flink {@link RowType} 与 Avro {@link Schema}， 构建
 *       {@link ValueWriter} 树来逐字段编码 RowData。
 * </ul>
 *
 * <p>设计意图：Flink 的 RowData 是二进制优化的行存储格式，与 Avro 的编码模型不同。 本类通过 Visitor 模式将两种 schema 对齐，生成与 Avro 类型对应的
 * ValueWriter 链， 由 {@link FlinkValueWriters} 提供针对 Flink 类型的具体编码实现。
 *
 * <p>上下游关系：被 Iceberg 的 Avro 写入流程（如 Appender）调用；委托 {@link AvroWithFlinkSchemaVisitor} 构建 writer 树。
 */
public class FlinkAvroWriter implements MetricsAwareDatumWriter<RowData> {
  private final RowType rowType;
  private ValueWriter<RowData> writer = null;

  public FlinkAvroWriter(RowType rowType) {
    this.rowType = rowType;
  }

  /**
   * 设置 Avro 写入 schema，并基于该 schema 与构造时传入的 Flink {@link RowType} 构建 writer 树。
   *
   * @param schema Avro schema
   */
  @Override
  @SuppressWarnings("unchecked")
  public void setSchema(Schema schema) {
    this.writer =
        (ValueWriter<RowData>)
            AvroWithFlinkSchemaVisitor.visit(rowType, schema, new WriteBuilder());
  }

  /**
   * 将一条 RowData 写入 Avro {@link Encoder}。
   *
   * @param datum Flink 行数据
   * @param out Avro 编码器
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(RowData datum, Encoder out) throws IOException {
    writer.write(datum, out);
  }

  /**
   * 返回当前 writer 收集的字段级指标流。
   *
   * @return 字段指标流
   */
  @Override
  public Stream<FieldMetrics> metrics() {
    return writer.metrics();
  }

  /**
   * Avro schema 访问器：将 Avro 类型节点映射为对应的 Flink {@link ValueWriter}。
   *
   * <p>设计意图：通过 Visitor 模式递归遍历 Avro schema，在每种类型节点上选择合适的 ValueWriter 实现（来自 {@link FlinkValueWriters}
   * 或通用 {@link ValueWriters}）。
   */
  private static class WriteBuilder extends AvroWithFlinkSchemaVisitor<ValueWriter<?>> {
    /**
     * 构建 struct/record 类型的 writer，将各字段 writer 与 Flink 类型配对。
     *
     * @param struct Flink 行类型
     * @param record Avro record schema
     * @param names 字段名列表
     * @param fields 各字段 writer
     * @return 行 writer
     */
    @Override
    public ValueWriter<?> record(
        LogicalType struct, Schema record, List<String> names, List<ValueWriter<?>> fields) {
      return FlinkValueWriters.row(
          fields,
          IntStream.range(0, names.size())
              .mapToObj(i -> fieldNameAndType(struct, i).second())
              .collect(Collectors.toList()));
    }

    /**
     * 构建 union（可选类型）的 writer，仅支持 null + 一个非 null 分支的 Avro union。
     *
     * @param type Flink 逻辑类型
     * @param union Avro union schema
     * @param options 各分支 writer
     * @return 可选 writer
     */
    @Override
    public ValueWriter<?> union(LogicalType type, Schema union, List<ValueWriter<?>> options) {
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
     * 构建数组类型的 writer。
     *
     * @param sArray Flink 数组类型
     * @param array Avro array schema
     * @param elementWriter 元素 writer
     * @return 数组 writer
     */
    @Override
    public ValueWriter<?> array(LogicalType sArray, Schema array, ValueWriter<?> elementWriter) {
      return FlinkValueWriters.array(elementWriter, arrayElementType(sArray));
    }

    /**
     * 构建非数组 Map 类型的 writer，key 固定为字符串。
     *
     * @param sMap Flink Map 类型
     * @param map Avro map schema
     * @param valueReader 值 writer
     * @return map writer
     */
    @Override
    public ValueWriter<?> map(LogicalType sMap, Schema map, ValueWriter<?> valueReader) {
      return FlinkValueWriters.map(
          FlinkValueWriters.strings(), mapKeyType(sMap), valueReader, mapValueType(sMap));
    }

    /**
     * 构建数组 Map（arrayMap）类型的 writer，支持任意 key 类型。
     *
     * @param sMap Flink Map 类型
     * @param map Avro map schema
     * @param keyWriter key writer
     * @param valueWriter value writer
     * @return arrayMap writer
     */
    @Override
    public ValueWriter<?> map(
        LogicalType sMap, Schema map, ValueWriter<?> keyWriter, ValueWriter<?> valueWriter) {
      return FlinkValueWriters.arrayMap(
          keyWriter, mapKeyType(sMap), valueWriter, mapValueType(sMap));
    }

    /**
     * 构建基础类型的 writer。
     *
     * <p>逻辑：先检查 Avro logicalType（date/time-micros/timestamp-micros/decimal/uuid）， 选择对应的 Flink 感知
     * writer；无 logicalType 时按 Avro primitive 类型选择 writer。 对于 INT 类型，额外区分 Flink 的 TINYINT/SMALLINT
     * 以选择正确的窄化 writer。
     *
     * @param type Flink 逻辑类型
     * @param primitive Avro primitive schema
     * @return 基础类型 writer
     */
    @Override
    public ValueWriter<?> primitive(LogicalType type, Schema primitive) {
      org.apache.avro.LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            return ValueWriters.ints();

          case "time-micros":
            return FlinkValueWriters.timeMicros();

          case "timestamp-micros":
            return FlinkValueWriters.timestampMicros();

          case "decimal":
            LogicalTypes.Decimal decimal = (LogicalTypes.Decimal) logicalType;
            return FlinkValueWriters.decimal(decimal.getPrecision(), decimal.getScale());

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
          switch (type.getTypeRoot()) {
            case TINYINT:
              return ValueWriters.tinyints();
            case SMALLINT:
              return ValueWriters.shorts();
            default:
              return ValueWriters.ints();
          }
        case LONG:
          return ValueWriters.longs();
        case FLOAT:
          return ValueWriters.floats();
        case DOUBLE:
          return ValueWriters.doubles();
        case STRING:
          return FlinkValueWriters.strings();
        case FIXED:
          return ValueWriters.fixed(primitive.getFixedSize());
        case BYTES:
          return ValueWriters.bytes();
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
