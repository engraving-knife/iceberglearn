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
import java.util.Map;
import java.util.function.Supplier;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.avro.AvroSchemaWithTypeVisitor;
import org.apache.iceberg.avro.SupportsRowPosition;
import org.apache.iceberg.avro.ValueReader;
import org.apache.iceberg.avro.ValueReaders;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * Avro 数据读取器：基于 Iceberg schema 与 Avro schema 构建 ValueReader 树来读取 Avro 数据。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的 DatumReader 实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以 Iceberg 期望 schema 与 Avro 读取 schema 为输入，通过 {@link AvroSchemaWithTypeVisitor} 遍历构建字段级的
 *       {@link ValueReader} 树。
 *   <li>实现 {@link DatumReader} 接口，在 read 时通过 {@link DecoderResolver} 做读写 schema 解析后读取记录。
 *   <li>支持行位置注入（{@link SupportsRowPosition}），供扫描读取使用。
 * </ul>
 *
 * <p>设计意图：将 Iceberg 类型系统与 Avro 读取解耦——通过 visitor 模式按类型分发到对应读取器，
 * 逻辑类型（date/time/timestamp/decimal/uuid）走 {@link GenericReaders}，基本类型走 {@link ValueReaders}。 子类可覆盖
 * {@link #createStructReader} 以自定义结构体读取行为。
 *
 * <p>上下游关系：被 Avro 文件读取器（如 {@code GenericAvroReader}）使用；依赖 {@link DecoderResolver} 做 schema 解析，依赖
 * {@link GenericReaders}、{@link ValueReaders} 构建读取器。
 *
 * @param <T> 读取结果的数据类型
 */
public class DataReader<T> implements DatumReader<T>, SupportsRowPosition {

  /**
   * 创建数据读取器（无常量注入）。
   *
   * @param expectedSchema Iceberg 期望 schema
   * @param readSchema Avro 读取 schema
   * @param <D> 读取结果的数据类型
   * @return 数据读取器实例
   */
  public static <D> DataReader<D> create(
      org.apache.iceberg.Schema expectedSchema, Schema readSchema) {
    return create(expectedSchema, readSchema, ImmutableMap.of());
  }

  /**
   * 创建数据读取器（支持常量注入）。
   *
   * @param expectedSchema Iceberg 期望 schema
   * @param readSchema Avro 读取 schema
   * @param idToConstant 字段 ID 到常量值的映射
   * @param <D> 读取结果的数据类型
   * @return 数据读取器实例
   */
  public static <D> DataReader<D> create(
      org.apache.iceberg.Schema expectedSchema, Schema readSchema, Map<Integer, ?> idToConstant) {
    return new DataReader<>(expectedSchema, readSchema, idToConstant);
  }

  private final Schema readSchema;
  private final ValueReader<T> reader;
  private Schema fileSchema = null;

  @SuppressWarnings("unchecked")
  protected DataReader(
      org.apache.iceberg.Schema expectedSchema, Schema readSchema, Map<Integer, ?> idToConstant) {
    this.readSchema = readSchema;
    this.reader =
        (ValueReader<T>)
            AvroSchemaWithTypeVisitor.visit(
                expectedSchema, readSchema, new ReadBuilder(idToConstant));
  }

  /**
   * 设置文件 schema，并应用别名映射得到实际读取 schema。
   *
   * @param newFileSchema 文件实际写入的 schema
   */
  @Override
  public void setSchema(Schema newFileSchema) {
    this.fileSchema = Schema.applyAliases(newFileSchema, readSchema);
  }

  /**
   * 读取一条记录。
   *
   * <p>逻辑：委托 {@link DecoderResolver#resolveAndRead} 做读写 schema 解析后读取记录。
   *
   * @param reuse 可复用的对象
   * @param decoder Avro 解码器
   * @return 读取到的记录
   * @throws IOException 读取时发生 IO 异常
   */
  @Override
  public T read(T reuse, Decoder decoder) throws IOException {
    return DecoderResolver.resolveAndRead(decoder, readSchema, fileSchema, reader, reuse);
  }

  /**
   * 设置行位置供给器，传递给支持行位置的读取器。
   *
   * @param posSupplier 行位置供给器
   */
  @Override
  public void setRowPositionSupplier(Supplier<Long> posSupplier) {
    if (reader instanceof SupportsRowPosition) {
      ((SupportsRowPosition) reader).setRowPositionSupplier(posSupplier);
    }
  }

  /**
   * 创建结构体读取器，子类可覆盖以自定义结构体读取行为。
   *
   * @param struct 结构类型
   * @param fields 各字段读取器
   * @param idToConstant 字段 ID 到常量的映射
   * @return 结构体读取器
   */
  protected ValueReader<?> createStructReader(
      Types.StructType struct, List<ValueReader<?>> fields, Map<Integer, ?> idToConstant) {
    return GenericReaders.struct(struct, fields, idToConstant);
  }

  /** 读取器构建 visitor：遍历 Iceberg 类型与 Avro schema，按类型构造对应的 ValueReader。 */
  private class ReadBuilder extends AvroSchemaWithTypeVisitor<ValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    @Override
    public ValueReader<?> record(
        Types.StructType struct, Schema record, List<String> names, List<ValueReader<?>> fields) {
      return createStructReader(struct, fields, idToConstant);
    }

    @Override
    public ValueReader<?> union(Type ignored, Schema union, List<ValueReader<?>> options) {
      return ValueReaders.union(options);
    }

    @Override
    public ValueReader<?> array(
        Types.ListType ignored, Schema array, ValueReader<?> elementReader) {
      return ValueReaders.array(elementReader);
    }

    @Override
    public ValueReader<?> map(
        Types.MapType iMap, Schema map, ValueReader<?> keyReader, ValueReader<?> valueReader) {
      return ValueReaders.arrayMap(keyReader, valueReader);
    }

    @Override
    public ValueReader<?> map(Types.MapType ignored, Schema map, ValueReader<?> valueReader) {
      return ValueReaders.map(ValueReaders.strings(), valueReader);
    }

    @Override
    public ValueReader<?> primitive(Type.PrimitiveType ignored, Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            return GenericReaders.dates();

          case "time-micros":
            return GenericReaders.times();

          case "timestamp-micros":
            if (AvroSchemaUtil.isTimestamptz(primitive)) {
              return GenericReaders.timestamptz();
            }
            return GenericReaders.timestamps();

          case "decimal":
            return ValueReaders.decimal(
                ValueReaders.decimalBytesReader(primitive),
                ((LogicalTypes.Decimal) logicalType).getScale());

          case "uuid":
            return ValueReaders.uuids();

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
          // might want to use a binary-backed container like Utf8
          return ValueReaders.strings();
        case FIXED:
          return ValueReaders.fixed(primitive.getFixedSize());
        case BYTES:
          return ValueReaders.byteBuffers();
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
