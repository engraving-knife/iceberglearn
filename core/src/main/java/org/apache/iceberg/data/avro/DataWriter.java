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
package org.apache.iceberg.data.avro;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.Encoder;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.avro.AvroSchemaVisitor;
import org.apache.iceberg.avro.LogicalMap;
import org.apache.iceberg.avro.MetricsAwareDatumWriter;
import org.apache.iceberg.avro.ValueWriter;
import org.apache.iceberg.avro.ValueWriters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Avro 数据写入器：基于 Avro schema 构建 ValueWriter 树来写入 Avro 数据，并收集字段指标。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的 MetricsAwareDatumWriter 实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以 Avro schema 为输入，通过 {@link AvroSchemaVisitor} 遍历构建字段级的 {@link ValueWriter} 树。
 *   <li>实现 {@link MetricsAwareDatumWriter} 接口，在写入时收集字段级指标（用于文件 metrics）。
 * </ul>
 *
 * <p>设计意图：将 Iceberg 数据写入与 Avro 编码解耦——通过 visitor 模式按类型分发到对应写入器，
 * 逻辑类型（date/time/timestamp/decimal/uuid）走 {@link GenericWriters}，基本类型走 {@link ValueWriters}。 子类可覆盖
 * {@link #createStructWriter} 以自定义结构体写入行为。union 类型仅支持 option（含 null）。
 *
 * <p>上下游关系：被 Avro 文件写入器（如 {@code GenericAvroWriter}）使用；依赖 {@link GenericWriters}、{@link
 * ValueWriters} 构建写入器。
 *
 * @param <T> 写入数据的类型
 */
public class DataWriter<T> implements MetricsAwareDatumWriter<T> {
  private ValueWriter<T> writer = null;

  /**
   * 创建数据写入器。
   *
   * @param schema Avro 写入 schema
   * @param <D> 写入数据的类型
   * @return 数据写入器实例
   */
  public static <D> DataWriter<D> create(Schema schema) {
    return new DataWriter<>(schema);
  }

  protected DataWriter(Schema schema) {
    setSchema(schema);
  }

  /**
   * 设置 schema 并构建 ValueWriter 树。
   *
   * @param schema Avro 写入 schema
   */
  @Override
  @SuppressWarnings("unchecked")
  public void setSchema(Schema schema) {
    this.writer = (ValueWriter<T>) AvroSchemaVisitor.visit(schema, new WriteBuilder());
  }

  /**
   * 写入一条记录。
   *
   * @param datum 待写入的数据
   * @param out Avro 编码器
   * @throws IOException 写入时发生 IO 异常
   */
  @Override
  public void write(T datum, Encoder out) throws IOException {
    writer.write(datum, out);
  }

  /**
   * 创建结构体写入器，子类可覆盖以自定义结构体写入行为。
   *
   * @param fields 各字段写入器
   * @return 结构体写入器
   */
  protected ValueWriter<?> createStructWriter(List<ValueWriter<?>> fields) {
    return GenericWriters.struct(fields);
  }

  /** 返回字段级指标流，用于生成文件 metrics。 */
  @Override
  public Stream<FieldMetrics> metrics() {
    return writer.metrics();
  }

  /** 写入器构建 visitor：遍历 Avro schema，按类型构造对应的 ValueWriter。 */
  private class WriteBuilder extends AvroSchemaVisitor<ValueWriter<?>> {
    @Override
    public ValueWriter<?> record(Schema record, List<String> names, List<ValueWriter<?>> fields) {
      return createStructWriter(fields);
    }

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

    @Override
    public ValueWriter<?> array(Schema array, ValueWriter<?> elementWriter) {
      if (array.getLogicalType() instanceof LogicalMap) {
        ValueWriters.StructWriter<?> keyValueWriter = (ValueWriters.StructWriter<?>) elementWriter;
        return ValueWriters.arrayMap(keyValueWriter.writer(0), keyValueWriter.writer(1));
      }

      return ValueWriters.array(elementWriter);
    }

    @Override
    public ValueWriter<?> map(Schema map, ValueWriter<?> valueWriter) {
      return ValueWriters.map(ValueWriters.strings(), valueWriter);
    }

    @Override
    public ValueWriter<?> primitive(Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            return GenericWriters.dates();

          case "time-micros":
            return GenericWriters.times();

          case "timestamp-micros":
            if (AvroSchemaUtil.isTimestamptz(primitive)) {
              return GenericWriters.timestamptz();
            }
            return GenericWriters.timestamps();

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
          return ValueWriters.fixed(primitive.getFixedSize());
        case BYTES:
          return ValueWriters.byteBuffers();
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
