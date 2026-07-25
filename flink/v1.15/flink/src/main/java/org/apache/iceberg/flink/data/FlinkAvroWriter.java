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
 * 把 Flink {@link RowData} 序列化为 Avro 编码数据的 DatumWriter 实现。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：按 Avro schema 构造 {@link ValueWriter}， 并在写入时收集字段级 metrics（用于
 * Iceberg 统计信息）。
 *
 * <p>设计意图：适配器模式，把 Iceberg Avro 写入能力适配到 Flink RowData； 上下游：被 Flink 写入算子（如 IcebergStreamWriter）调用。
 */
public class FlinkAvroWriter implements MetricsAwareDatumWriter<RowData> {
  private final RowType rowType;
  private ValueWriter<RowData> writer = null;

  /** 构造函数，携带 Flink 行类型。 */
  public FlinkAvroWriter(RowType rowType) {
    this.rowType = rowType;
  }

  /** 设置 Avro schema，并通过 {@link AvroWithFlinkSchemaVisitor} 构造写入器。 */
  @Override
  @SuppressWarnings("unchecked")
  public void setSchema(Schema schema) {
    this.writer =
        (ValueWriter<RowData>)
            AvroWithFlinkSchemaVisitor.visit(rowType, schema, new WriteBuilder());
  }

  /** 写入一行数据到 Avro Encoder。 */
  @Override
  public void write(RowData datum, Encoder out) throws IOException {
    writer.write(datum, out);
  }

  /** 返回字段级 metrics 流。 */
  @Override
  public Stream<FieldMetrics> metrics() {
    return writer.metrics();
  }

  /** Avro schema 访问者实现，按 Iceberg 类型构造 Flink RowData 的 ValueWriter。 */
  private static class WriteBuilder extends AvroWithFlinkSchemaVisitor<ValueWriter<?>> {
    /** 构造 struct 类型的 ValueWriter。 */
    @Override
    public ValueWriter<?> record(
        LogicalType struct, Schema record, List<String> names, List<ValueWriter<?>> fields) {
      return FlinkValueWriters.row(
          fields,
          IntStream.range(0, names.size())
              .mapToObj(i -> fieldNameAndType(struct, i).second())
              .collect(Collectors.toList()));
    }

    /** 构造 union（可选类型）的 ValueWriter，只支持两元素 nullable union。 */
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

    /** 构造数组类型的 ValueWriter。 */
    @Override
    public ValueWriter<?> array(LogicalType sArray, Schema array, ValueWriter<?> elementWriter) {
      return FlinkValueWriters.array(elementWriter, arrayElementType(sArray));
    }

    /** 构造 map（key 为字符串）的 ValueWriter。 */
    @Override
    public ValueWriter<?> map(LogicalType sMap, Schema map, ValueWriter<?> valueReader) {
      return FlinkValueWriters.map(
          FlinkValueWriters.strings(), mapKeyType(sMap), valueReader, mapValueType(sMap));
    }

    /** 构造 map（key 为数组形式）的 ValueWriter。 */
    @Override
    public ValueWriter<?> map(
        LogicalType sMap, Schema map, ValueWriter<?> keyWriter, ValueWriter<?> valueWriter) {
      return FlinkValueWriters.arrayMap(
          keyWriter, mapKeyType(sMap), valueWriter, mapValueType(sMap));
    }

    /**
     * 构造基本类型的 ValueWriter。
     *
     * <p>逻辑：先按 Avro logicalType 处理 date/time/decimal/uuid 等； 再按 Avro primitive 类型分派到对应写入器，并参考 Flink
     * 类型根区分 tinyint/smallint/int。
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
