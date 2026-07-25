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
import java.util.Map;
import java.util.function.Supplier;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.iceberg.avro.AvroSchemaWithTypeVisitor;
import org.apache.iceberg.avro.SupportsRowPosition;
import org.apache.iceberg.avro.ValueReader;
import org.apache.iceberg.avro.ValueReaders;
import org.apache.iceberg.data.avro.DecoderResolver;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.catalyst.InternalRow;

/**
 * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkAvroReader。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkAvroReader implements DatumReader<InternalRow>, SupportsRowPosition {

  private final Schema readSchema;
  private final ValueReader<InternalRow> reader;
  private Schema fileSchema = null;

  /** 构造 SparkAvroReader 实例。 */
  public SparkAvroReader(org.apache.iceberg.Schema expectedSchema, Schema readSchema) {
    this(expectedSchema, readSchema, ImmutableMap.of());
  }

  /** 构造 SparkAvroReader 实例。 */
  @SuppressWarnings("unchecked")
  public SparkAvroReader(
      org.apache.iceberg.Schema expectedSchema, Schema readSchema, Map<Integer, ?> constants) {
    this.readSchema = readSchema;
    this.reader =
        (ValueReader<InternalRow>)
            AvroSchemaWithTypeVisitor.visit(expectedSchema, readSchema, new ReadBuilder(constants));
  }

  /** 设置schema。 */
  @Override
  public void setSchema(Schema newFileSchema) {
    this.fileSchema = Schema.applyAliases(newFileSchema, readSchema);
  }

  /**
   * 读取数据。
   *
   * @param reuse 参数
   * @param decoder 参数
   * @return 结果对象
   */
  @Override
  public InternalRow read(InternalRow reuse, Decoder decoder) throws IOException {
    return DecoderResolver.resolveAndRead(decoder, readSchema, fileSchema, reader, reuse);
  }

  /** 设置rowpositionsupplier。 */
  @Override
  public void setRowPositionSupplier(Supplier<Long> posSupplier) {
    if (reader instanceof SupportsRowPosition) {
      ((SupportsRowPosition) reader).setRowPositionSupplier(posSupplier);
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ReadBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ReadBuilder extends AvroSchemaWithTypeVisitor<ValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    /** 构造 ReadBuilder 实例。 */
    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param record 参数
     * @param names 参数
     * @param fields 参数
     * @return 结果对象
     */
    @Override
    public ValueReader<?> record(
        Types.StructType expected, Schema record, List<String> names, List<ValueReader<?>> fields) {
      return SparkValueReaders.struct(fields, expected, idToConstant);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param union 参数
     * @param options 参数
     * @return 结果对象
     */
    @Override
    public ValueReader<?> union(Type expected, Schema union, List<ValueReader<?>> options) {
      return ValueReaders.union(options);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param array 参数
     * @param elementReader 参数
     * @return 结果对象
     */
    @Override
    public ValueReader<?> array(
        Types.ListType expected, Schema array, ValueReader<?> elementReader) {
      return SparkValueReaders.array(elementReader);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param map 参数
     * @param keyReader 参数
     * @param valueReader 参数
     * @return 结果对象
     */
    @Override
    public ValueReader<?> map(
        Types.MapType expected, Schema map, ValueReader<?> keyReader, ValueReader<?> valueReader) {
      return SparkValueReaders.arrayMap(keyReader, valueReader);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param map 参数
     * @param valueReader 参数
     * @return 结果对象
     */
    @Override
    public ValueReader<?> map(Types.MapType expected, Schema map, ValueReader<?> valueReader) {
      return SparkValueReaders.map(SparkValueReaders.strings(), valueReader);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param primitive 参数
     * @return 结果对象
     */
    @Override
    public ValueReader<?> primitive(Type.PrimitiveType expected, Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            // Spark uses the same representation
            return ValueReaders.ints();

          case "timestamp-millis":
            // adjust to microseconds
            ValueReader<Long> longs = ValueReaders.longs();
            return (ValueReader<Long>) (decoder, ignored) -> longs.read(decoder, null) * 1000L;

          case "timestamp-micros":
            // Spark uses the same representation
            return ValueReaders.longs();

          case "decimal":
            return SparkValueReaders.decimal(
                ValueReaders.decimalBytesReader(primitive),
                ((LogicalTypes.Decimal) logicalType).getScale());

          case "uuid":
            return SparkValueReaders.uuids();

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
          return SparkValueReaders.strings();
        case FIXED:
          return ValueReaders.fixed(primitive.getFixedSize());
        case BYTES:
          return ValueReaders.bytes();
        case ENUM:
          return SparkValueReaders.enums(primitive.getEnumSymbols());
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
