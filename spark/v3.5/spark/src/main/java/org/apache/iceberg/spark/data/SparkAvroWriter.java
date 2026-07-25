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
package org.apache.iceberg.spark.data;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.Encoder;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.avro.MetricsAwareDatumWriter;
import org.apache.iceberg.avro.ValueWriter;
import org.apache.iceberg.avro.ValueWriters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StructType;

/**
 * 将 Spark {@link InternalRow} 写为 Avro 记录的写入器。
 *
 * <p>所属模块：iceberg-spark（data 子包）。实现 {@link MetricsAwareDatumWriter}，结合 Spark StructType 与 Avro
 * Schema 构建类型化的 ValueWriter 树，把 Spark 内部行按 Avro 编码写出， 并在写入过程中收集字段级指标（metrics）。
 *
 * <p>设计意图：通过 {@link AvroWithSparkSchemaVisitor} 同时遍历 Spark 与 Avro 类型，由 {@link WriteBuilder}
 * 为每种类型生成对应的 {@link ValueWriter}，实现类型安全且可度量。
 *
 * <p>上下游关系：由 Iceberg Avro 文件 appender 在 Spark 写路径中使用；依赖 {@link SparkValueWriters} 提供 Spark
 * 特有的值写入实现。
 */
public class SparkAvroWriter implements MetricsAwareDatumWriter<InternalRow> {
  private final StructType dsSchema;
  private ValueWriter<InternalRow> writer = null;

  /** 以数据集 Spark StructType 构造。 */
  public SparkAvroWriter(StructType dsSchema) {
    this.dsSchema = dsSchema;
  }

  /** 设置 Avro Schema，并据此与 dsSchema 一起构建 ValueWriter 树。 */
  @Override
  @SuppressWarnings("unchecked")
  public void setSchema(Schema schema) {
    this.writer =
        (ValueWriter<InternalRow>)
            AvroWithSparkSchemaVisitor.visit(dsSchema, schema, new WriteBuilder());
  }

  /** 将一行写入 Avro 编码器。 */
  @Override
  public void write(InternalRow datum, Encoder out) throws IOException {
    writer.write(datum, out);
  }

  /** 返回写入过程中收集的字段指标流。 */
  @Override
  public Stream<FieldMetrics> metrics() {
    return writer.metrics();
  }

  /** Spark+Avro 类型访问构建器：为 struct/union/array/map/primitive 生成对应 ValueWriter。 */
  private static class WriteBuilder extends AvroWithSparkSchemaVisitor<ValueWriter<?>> {
    /** 构造 struct 写入器，携带各字段类型。 */
    @Override
    public ValueWriter<?> record(
        DataType struct, Schema record, List<String> names, List<ValueWriter<?>> fields) {
      return SparkValueWriters.struct(
          fields,
          IntStream.range(0, names.size())
              .mapToObj(i -> fieldNameAndType(struct, i).second())
              .collect(Collectors.toList()));
    }

    /** 构造可选（union[null,T]）写入器，校验必须为两元素且含 null。 */
    @Override
    public ValueWriter<?> union(DataType type, Schema union, List<ValueWriter<?>> options) {
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

    /** 构造数组写入器。 */
    @Override
    public ValueWriter<?> array(DataType sArray, Schema array, ValueWriter<?> elementWriter) {
      return SparkValueWriters.array(elementWriter, arrayElementType(sArray));
    }

    /** 构造以字符串为键的 map 写入器。 */
    @Override
    public ValueWriter<?> map(DataType sMap, Schema map, ValueWriter<?> valueReader) {
      return SparkValueWriters.map(
          SparkValueWriters.strings(), mapKeyType(sMap), valueReader, mapValueType(sMap));
    }

    /** 构造以任意类型为键的 arrayMap 写入器。 */
    @Override
    public ValueWriter<?> map(
        DataType sMap, Schema map, ValueWriter<?> keyWriter, ValueWriter<?> valueWriter) {
      return SparkValueWriters.arrayMap(
          keyWriter, mapKeyType(sMap), valueWriter, mapValueType(sMap));
    }

    /**
     * 构造基本类型写入器。
     *
     * <p>逻辑：优先按 Avro 逻辑类型（date/timestamp-micros/decimal/uuid）选择；否则按 Avro 基本类型 选择，并对 INT 区分 Spark 的
     * Byte/Short。
     */
    @Override
    public ValueWriter<?> primitive(DataType type, Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            // Spark uses the same representation
            return ValueWriters.ints();

          case "timestamp-micros":
            // Spark uses the same representation
            return ValueWriters.longs();

          case "decimal":
            LogicalTypes.Decimal decimal = (LogicalTypes.Decimal) logicalType;
            return SparkValueWriters.decimal(decimal.getPrecision(), decimal.getScale());

          case "uuid":
            return SparkValueWriters.uuids();

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
          if (type instanceof ByteType) {
            return ValueWriters.tinyints();
          } else if (type instanceof ShortType) {
            return ValueWriters.shorts();
          }
          return ValueWriters.ints();
        case LONG:
          return ValueWriters.longs();
        case FLOAT:
          return ValueWriters.floats();
        case DOUBLE:
          return ValueWriters.doubles();
        case STRING:
          return SparkValueWriters.strings();
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
