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
 * 文件级说明：将 Avro 数据读取为 Flink {@link RowData} 的读取器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 data 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Avro {@link DatumReader} 接口，把 Avro 编码的字节流解码为 Flink RowData。
 *   <li>结合 Iceberg 期望 schema 与 Avro 文件 schema 处理列重命名与字段投影。
 *   <li>支持把字段 ID 到常量的映射注入读取结果，常用于分区列填充。
 *   <li>支持行位置信息注入（实现 {@link SupportsRowPosition}）。
 * </ul>
 *
 * <p>设计意图：通过访问器模式构建 Iceberg 类型到 ValueReader 的映射， 复用 Iceberg 的
 * AvroSchemaWithTypeVisitor，避免在每个数据类型处重复样板代码。
 *
 * <p>上下游关系：上游为 Iceberg 的 Avro 文件读取入口，下游为 {@link FlinkValueReaders} 提供的具体字段读取器。
 */
public class FlinkAvroReader implements DatumReader<RowData>, SupportsRowPosition {

  private final Schema readSchema;
  private final ValueReader<RowData> reader;
  private Schema fileSchema = null;

  /** 构造读取器，使用空常量映射。 */
  public FlinkAvroReader(org.apache.iceberg.Schema expectedSchema, Schema readSchema) {
    this(expectedSchema, readSchema, ImmutableMap.of());
  }

  /**
   * 构造读取器，可传入字段 ID 到常量值的映射。
   *
   * <p>逻辑：通过 {@link AvroSchemaWithTypeVisitor} 访问 Iceberg 期望 schema 与 Avro schema， 使用内部 {@link
   * ReadBuilder} 构造 ValueReader 树。
   */
  @SuppressWarnings("unchecked")
  public FlinkAvroReader(
      org.apache.iceberg.Schema expectedSchema, Schema readSchema, Map<Integer, ?> constants) {
    this.readSchema = readSchema;
    this.reader =
        (ValueReader<RowData>)
            AvroSchemaWithTypeVisitor.visit(expectedSchema, readSchema, new ReadBuilder(constants));
  }

  /** 设置当前文件的 Avro schema，并应用列别名重写以匹配读取 schema。 */
  @Override
  public void setSchema(Schema newFileSchema) {
    this.fileSchema = Schema.applyAliases(newFileSchema, readSchema);
  }

  /**
   * 从解码器读取一条记录并转为 RowData。
   *
   * @param reuse 可复用的 RowData（本实现未使用）
   * @param decoder Avro 解码器
   * @return 解码后的 RowData
   * @throws IOException 读取失败时抛出
   */
  @Override
  public RowData read(RowData reuse, Decoder decoder) throws IOException {
    return DecoderResolver.resolveAndRead(decoder, readSchema, fileSchema, reader, reuse);
  }

  /** 注入行位置供应器，仅当内部 reader 支持 {@link SupportsRowPosition} 时生效。 */
  @Override
  public void setRowPositionSupplier(Supplier<Long> posSupplier) {
    if (reader instanceof SupportsRowPosition) {
      ((SupportsRowPosition) reader).setRowPositionSupplier(posSupplier);
    }
  }

  /** 内部访问器：根据 Iceberg 类型与 Avro schema 构造对应的 ValueReader。 */
  private static class ReadBuilder extends AvroSchemaWithTypeVisitor<ValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    @Override
    public ValueReader<?> record(
        Types.StructType expected, Schema record, List<String> names, List<ValueReader<?>> fields) {
      return FlinkValueReaders.struct(fields, expected.asStructType(), idToConstant);
    }

    @Override
    public ValueReader<?> union(Type expected, Schema union, List<ValueReader<?>> options) {
      return ValueReaders.union(options);
    }

    @Override
    public ValueReader<?> array(
        Types.ListType expected, Schema array, ValueReader<?> elementReader) {
      return FlinkValueReaders.array(elementReader);
    }

    @Override
    public ValueReader<?> map(
        Types.MapType expected, Schema map, ValueReader<?> keyReader, ValueReader<?> valueReader) {
      return FlinkValueReaders.arrayMap(keyReader, valueReader);
    }

    @Override
    public ValueReader<?> map(Types.MapType expected, Schema map, ValueReader<?> valueReader) {
      return FlinkValueReaders.map(FlinkValueReaders.strings(), valueReader);
    }

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
