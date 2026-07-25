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
 * 把 Avro 数据解码为 Spark {@link InternalRow} 的 DatumReader。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），data 子包。用于读取 Iceberg Avro 数据文件 （如 equality delete 文件）并产出
 * Spark 内部行。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Avro {@link DatumReader} 接口，按 Iceberg Schema 与 Avro 读 schema 构造 {@link ValueReader}。
 *   <li>支持常量列注入（idToConstant）与行位置供应商（用于 row-level 操作定位）。
 *   <li>通过 {@link ReadBuilder} 访问者把 Iceberg 类型 + Avro schema 映射为对应的 Spark ValueReader。
 * </ul>
 *
 * <p>设计意图：复用 iceberg-core 的 {@link AvroSchemaWithTypeVisitor} 与 {@link ValueReaders} 体系， 仅在
 * primitive 与 struct 层替换为 Spark 专用实现（{@link SparkValueReaders}）， 保持与 Iceberg Avro 读取链一致；通过 {@link
 * DecoderResolver} 兼容读 schema 与文件 schema 差异。
 *
 * <p>上下游关系：被 Spark 读取路径用于读取 Avro 格式的删除文件等；依赖 iceberg-avro 与 spark catalyst。
 */
public class SparkAvroReader implements DatumReader<InternalRow>, SupportsRowPosition {

  private final Schema readSchema;
  private final ValueReader<InternalRow> reader;
  private Schema fileSchema = null;

  /** 构造 reader，无常量列。 */
  public SparkAvroReader(org.apache.iceberg.Schema expectedSchema, Schema readSchema) {
    this(expectedSchema, readSchema, ImmutableMap.of());
  }

  /**
   * 构造 reader。
   *
   * <p>逻辑：保存 readSchema，用 {@link AvroSchemaWithTypeVisitor} 访问 expectedSchema + readSchema， 通过
   * ReadBuilder 构造根 ValueReader&lt;InternalRow&gt;。constants 提供 field id -> 常量值映射。
   *
   * @param expectedSchema Iceberg 期望 schema
   * @param readSchema Avro 读 schema
   * @param constants 字段 ID 到常量值的映射
   */
  @SuppressWarnings("unchecked")
  public SparkAvroReader(
      org.apache.iceberg.Schema expectedSchema, Schema readSchema, Map<Integer, ?> constants) {
    this.readSchema = readSchema;
    this.reader =
        (ValueReader<InternalRow>)
            AvroSchemaWithTypeVisitor.visit(expectedSchema, readSchema, new ReadBuilder(constants));
  }

  /** 设置文件 schema，应用别名映射到读 schema。 */
  @Override
  public void setSchema(Schema newFileSchema) {
    this.fileSchema = Schema.applyAliases(newFileSchema, readSchema);
  }

  /** 读取一条记录，复用 DecoderResolver 兼容 schema 差异。 */
  @Override
  public InternalRow read(InternalRow reuse, Decoder decoder) throws IOException {
    return DecoderResolver.resolveAndRead(decoder, readSchema, fileSchema, reader, reuse);
  }

  /** 设置行位置供应商，透传给支持行位置的内部 reader。 */
  @Override
  public void setRowPositionSupplier(Supplier<Long> posSupplier) {
    if (reader instanceof SupportsRowPosition) {
      ((SupportsRowPosition) reader).setRowPositionSupplier(posSupplier);
    }
  }

  /**
   * Avro schema 访问者：把 Iceberg 类型 + Avro schema 转换为 Spark ValueReader 树。
   *
   * <p>设计意图：复用 iceberg-avro 的访问者骨架，仅在 struct/array/map/primitive 节点返回 Spark 专用 reader（{@link
   * SparkValueReaders}），其余复用通用 {@link ValueReaders}。 primitive 节点根据 Avro
   * logicalType（date/timestamp/decimal/uuid）做语义适配。
   */
  private static class ReadBuilder extends AvroSchemaWithTypeVisitor<ValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /** 构造 struct reader，传入字段 reader 列表、期望类型与常量映射。 */
    @Override
    public ValueReader<?> record(
        Types.StructType expected, Schema record, List<String> names, List<ValueReader<?>> fields) {
      return SparkValueReaders.struct(fields, expected, idToConstant);
    }

    /** 构造 union reader，按选项列表选择。 */
    @Override
    public ValueReader<?> union(Type expected, Schema union, List<ValueReader<?>> options) {
      return ValueReaders.union(options);
    }

    /** 构造 array reader，元素用 elementReader。 */
    @Override
    public ValueReader<?> array(
        Types.ListType expected, Schema array, ValueReader<?> elementReader) {
      return SparkValueReaders.array(elementReader);
    }

    /** 构造 array-backed map reader（key/value 平行数组），分别用 keyReader 与 valueReader。 */
    @Override
    public ValueReader<?> map(
        Types.MapType expected, Schema map, ValueReader<?> keyReader, ValueReader<?> valueReader) {
      return SparkValueReaders.arrayMap(keyReader, valueReader);
    }

    /** 构造 map reader，key 用字符串 reader，value 用 valueReader。 */
    @Override
    public ValueReader<?> map(Types.MapType expected, Schema map, ValueReader<?> valueReader) {
      return SparkValueReaders.map(SparkValueReaders.strings(), valueReader);
    }

    /**
     * 构造 primitive reader。
     *
     * <p>逻辑：先看 Avro logicalType，date/timestamp-micros 直接复用，timestamp-millis 转微秒， decimal 用
     * SparkValueReaders.decimal，uuid 用 SparkValueReaders.uuids； 无 logicalType 时按 Avro 原始类型分支选择对应
     * reader。
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
