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
import java.util.Map;
import java.util.function.Supplier;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.avro.AvroSchemaWithTypeVisitor;
import org.apache.iceberg.avro.SupportsRowPosition;
import org.apache.iceberg.avro.ValueReader;
import org.apache.iceberg.avro.ValueReaders;
import org.apache.iceberg.data.avro.DecoderResolver;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 把 Avro 编码数据读取为 Flink {@link RowData} 的 DatumReader 实现。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：按 expectedSchema 与 Avro 读 schema 构造 {@link ValueReader}，并在读取时通过
 * {@link DecoderResolver} 处理读写 schema 不一致问题。
 *
 * <p>设计意图：适配器模式，把 Iceberg Avro 读取能力适配到 Flink RowData； 上下游：被 Flink 读取算子（如
 * IcebergSourceSplitReader）调用。
 */
public class FlinkAvroReader implements DatumReader<RowData>, SupportsRowPosition {

  private final Schema readSchema;
  private final ValueReader<RowData> reader;
  private Schema fileSchema = null;

  /** 构造函数，使用空常量 Map。 */
  public FlinkAvroReader(org.apache.iceberg.Schema expectedSchema, Schema readSchema) {
    this(expectedSchema, readSchema, ImmutableMap.of());
  }

  /**
   * 构造函数，携带字段常量（如隐藏分区列常量）。
   *
   * <p>逻辑：用 {@link AvroSchemaWithTypeVisitor} 按 expectedSchema 与 readSchema 构造 ValueReader。
   */
  @SuppressWarnings("unchecked")
  public FlinkAvroReader(
      org.apache.iceberg.Schema expectedSchema, Schema readSchema, Map<Integer, ?> constants) {
    this.readSchema = readSchema;
    this.reader =
        (ValueReader<RowData>)
            AvroSchemaWithTypeVisitor.visit(expectedSchema, readSchema, new ReadBuilder(constants));
  }

  /** 设置文件实际 schema，并应用字段别名重映射。 */
  @Override
  public void setSchema(Schema newFileSchema) {
    this.fileSchema = Schema.applyAliases(newFileSchema, readSchema);
  }

  /** 读取一行数据，处理读 schema 与文件 schema 的差异。 */
  @Override
  public RowData read(RowData reuse, Decoder decoder) throws IOException {
    return DecoderResolver.resolveAndRead(decoder, readSchema, fileSchema, reader, reuse);
  }

  /** 注入行位置提供者，用于读取 position 信息。 */
  @Override
  public void setRowPositionSupplier(Supplier<Long> posSupplier) {
    if (reader instanceof SupportsRowPosition) {
      ((SupportsRowPosition) reader).setRowPositionSupplier(posSupplier);
    }
  }

  /** Avro schema 访问者实现，按 Iceberg 类型构造 Flink RowData 的 ValueReader。 */
  private static class ReadBuilder extends AvroSchemaWithTypeVisitor<ValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /** 构造 struct 类型的 ValueReader。 */
    @Override
    public ValueReader<?> record(
        Types.StructType expected, Schema record, List<String> names, List<ValueReader<?>> fields) {
      return FlinkValueReaders.struct(fields, expected.asStructType(), idToConstant);
    }

    /** 构造 union（可选类型）的 ValueReader。 */
    @Override
    public ValueReader<?> union(Type expected, Schema union, List<ValueReader<?>> options) {
      return ValueReaders.union(options);
    }

    /** 构造数组类型的 ValueReader。 */
    @Override
    public ValueReader<?> array(
        Types.ListType expected, Schema array, ValueReader<?> elementReader) {
      return FlinkValueReaders.array(elementReader);
    }

    /** 构造 map（key 为数组形式）的 ValueReader。 */
    @Override
    public ValueReader<?> map(
        Types.MapType expected, Schema map, ValueReader<?> keyReader, ValueReader<?> valueReader) {
      return FlinkValueReaders.arrayMap(keyReader, valueReader);
    }

    /** 构造 map（key 为字符串）的 ValueReader。 */
    @Override
    public ValueReader<?> map(Types.MapType expected, Schema map, ValueReader<?> valueReader) {
      return FlinkValueReaders.map(FlinkValueReaders.strings(), valueReader);
    }

    /**
     * 构造基本类型的 ValueReader。
     *
     * <p>逻辑：先按 Avro logicalType 处理 date/time/decimal/uuid 等； 再按 Avro primitive 类型分派到对应读取器。
     */
    @Override
    public ValueReader<?> primitive(Type.PrimitiveType expected, Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            return ValueReaders.ints();

          case "time-micros":
            return FlinkValueReaders.timeMicros();

          case "timestamp-millis":
            return FlinkValueReaders.timestampMills();

          case "timestamp-micros":
            return FlinkValueReaders.timestampMicros();

          case "decimal":
            LogicalTypes.Decimal decimal = (LogicalTypes.Decimal) logicalType;
            return FlinkValueReaders.decimal(
                ValueReaders.decimalBytesReader(primitive),
                decimal.getPrecision(),
                decimal.getScale());

          case "uuid":
            return FlinkValueReaders.uuids();

          default:
            throw new IllegalArgumentException("Unknown logical type: " + logicalType);
        }
      }

      switch (primitive.getType()) {
        case NULL:
          return ValueReaders.nulls();
        case BOOLEAN:
          return ValueReaders.booleans();
        case INT:
          return ValueReaders.ints();
        case LONG:
          return ValueReaders.longs();
        case FLOAT:
          return ValueReaders.floats();
        case DOUBLE:
          return ValueReaders.doubles();
        case STRING:
          return FlinkValueReaders.strings();
        case FIXED:
          return ValueReaders.fixed(primitive.getFixedSize());
        case BYTES:
          return ValueReaders.bytes();
        case ENUM:
          return FlinkValueReaders.enums(primitive.getEnumSymbols());
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
