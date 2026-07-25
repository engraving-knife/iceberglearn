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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.avro.io.Decoder;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.avro.ValueReader;
import org.apache.iceberg.avro.ValueReaders;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.util.ArrayBasedMapData;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.GenericArrayData;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark 专用的 Avro 值读取器集合。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），data 子包。提供把 Avro 解码数据 转为 Spark
 * 内部类型（UTF8String、Decimal、ArrayData、InternalRow 等）的 {@link ValueReader} 实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 string/enum/uuid/decimal/array/map/struct 等 Spark 专用 reader。
 *   <li>struct reader 支持常量列注入与行复用。
 *   <li>array/map reader 复用内部 List 减少分配。
 * </ul>
 *
 * <p>设计意图：复用 iceberg-avro 的 {@link ValueReaders} 基类与读取骨架，仅替换产出类型为 Spark 类型； UUIDReader 用
 * ThreadLocal ByteBuffer 缓冲避免每行分配；StructReader 继承 {@link ValueReaders.StructReader} 复用常量列与字段 ID
 * 映射逻辑。
 *
 * <p>上下游关系：被 {@link SparkAvroReader.ReadBuilder} 调用构造 reader 树；依赖 iceberg-avro 与 spark catalyst。
 */
public class SparkValueReaders {

  private SparkValueReaders() {}

  /** 返回读取 Avro 字符串为 Spark UTF8String 的 reader 单例。 */
  static ValueReader<UTF8String> strings() {
    return StringReader.INSTANCE;
  }

  /** 返回读取 Avro enum 为对应 UTF8String 符号的 reader。 */
  static ValueReader<UTF8String> enums(List<String> symbols) {
    return new EnumReader(symbols);
  }

  /** 返回读取 Avro fixed(16) UUID 为 UTF8String 的 reader 单例。 */
  static ValueReader<UTF8String> uuids() {
    return UUIDReader.INSTANCE;
  }

  /** 返回读取 unscaled 字节为 Spark Decimal（指定 scale）的 reader。 */
  static ValueReader<Decimal> decimal(ValueReader<byte[]> unscaledReader, int scale) {
    return new DecimalReader(unscaledReader, scale);
  }

  /** 返回读取 Avro array 为 Spark ArrayData 的 reader。 */
  static ValueReader<ArrayData> array(ValueReader<?> elementReader) {
    return new ArrayReader(elementReader);
  }

  /** 返回读取平行数组形式 map（key 数组 + value 数组）为 Spark ArrayBasedMapData 的 reader。 */
  static ValueReader<ArrayBasedMapData> arrayMap(
      ValueReader<?> keyReader, ValueReader<?> valueReader) {
    return new ArrayMapReader(keyReader, valueReader);
  }

  /** 返回读取 Avro map（键值对序列）为 Spark ArrayBasedMapData 的 reader。 */
  static ValueReader<ArrayBasedMapData> map(ValueReader<?> keyReader, ValueReader<?> valueReader) {
    return new MapReader(keyReader, valueReader);
  }

  /** 返回读取 Avro record 为 Spark InternalRow 的 StructReader，支持常量列注入。 */
  static ValueReader<InternalRow> struct(
      List<ValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
    return new StructReader(readers, struct, idToConstant);
  }

  /** 把 Avro 字符串读取为 Spark UTF8String，支持 reuse 复用。 */
  private static class StringReader implements ValueReader<UTF8String> {
    private static final StringReader INSTANCE = new StringReader();

    private StringReader() {}

    @Override
    public UTF8String read(Decoder decoder, Object reuse) throws IOException {
      // use the decoder's readString(Utf8) method because it may be a resolving decoder
      Utf8 utf8 = null;
      if (reuse instanceof UTF8String) {
        utf8 = new Utf8(((UTF8String) reuse).getBytes());
      }

      Utf8 string = decoder.readString(utf8);
      return UTF8String.fromBytes(string.getBytes(), 0, string.getByteLength());
    }
  }

  /** 把 Avro enum 索引映射为预缓存的 UTF8String 符号。 */
  private static class EnumReader implements ValueReader<UTF8String> {
    private final UTF8String[] symbols;

    private EnumReader(List<String> symbols) {
      this.symbols = new UTF8String[symbols.size()];
      for (int i = 0; i < this.symbols.length; i += 1) {
        this.symbols[i] = UTF8String.fromBytes(symbols.get(i).getBytes(StandardCharsets.UTF_8));
      }
    }

    @Override
    public UTF8String read(Decoder decoder, Object ignore) throws IOException {
      int index = decoder.readEnum();
      return symbols[index];
    }
  }

  /** 读取 16 字节 fixed 为 UUID 并转为 UTF8String，用 ThreadLocal ByteBuffer 缓冲。 */
  private static class UUIDReader implements ValueReader<UTF8String> {
    private static final ThreadLocal<ByteBuffer> BUFFER =
        ThreadLocal.withInitial(
            () -> {
              ByteBuffer buffer = ByteBuffer.allocate(16);
              buffer.order(ByteOrder.BIG_ENDIAN);
              return buffer;
            });

    private static final UUIDReader INSTANCE = new UUIDReader();

    private UUIDReader() {}

    @Override
    @SuppressWarnings("ByteBufferBackingArray")
    public UTF8String read(Decoder decoder, Object reuse) throws IOException {
      ByteBuffer buffer = BUFFER.get();
      buffer.rewind();

      decoder.readFixed(buffer.array(), 0, 16);

      return UTF8String.fromString(UUIDUtil.convert(buffer).toString());
    }
  }

  /** 读取 unscaled 字节为 BigInteger，构造 BigDecimal 后转为 Spark Decimal。 */
  private static class DecimalReader implements ValueReader<Decimal> {
    private final ValueReader<byte[]> bytesReader;
    private final int scale;

    private DecimalReader(ValueReader<byte[]> bytesReader, int scale) {
      this.bytesReader = bytesReader;
      this.scale = scale;
    }

    @Override
    public Decimal read(Decoder decoder, Object reuse) throws IOException {
      byte[] bytes = bytesReader.read(decoder, null);
      return Decimal.apply(new BigDecimal(new BigInteger(bytes), scale));
    }
  }

  /** 读取 Avro array（分块）为 Spark GenericArrayData，复用内部 List。 */
  private static class ArrayReader implements ValueReader<ArrayData> {
    private final ValueReader<?> elementReader;
    private final List<Object> reusedList = Lists.newArrayList();

    private ArrayReader(ValueReader<?> elementReader) {
      this.elementReader = elementReader;
    }

    @Override
    public GenericArrayData read(Decoder decoder, Object reuse) throws IOException {
      reusedList.clear();
      long chunkLength = decoder.readArrayStart();

      while (chunkLength > 0) {
        for (int i = 0; i < chunkLength; i += 1) {
          reusedList.add(elementReader.read(decoder, null));
        }

        chunkLength = decoder.arrayNext();
      }

      // this will convert the list to an array so it is okay to reuse the list
      return new GenericArrayData(reusedList.toArray());
    }
  }

  /** 读取平行数组形式 map 为 Spark ArrayBasedMapData，复用 key/value List。 */
  private static class ArrayMapReader implements ValueReader<ArrayBasedMapData> {
    private final ValueReader<?> keyReader;
    private final ValueReader<?> valueReader;

    private final List<Object> reusedKeyList = Lists.newArrayList();
    private final List<Object> reusedValueList = Lists.newArrayList();

    private ArrayMapReader(ValueReader<?> keyReader, ValueReader<?> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }

    @Override
    public ArrayBasedMapData read(Decoder decoder, Object reuse) throws IOException {
      reusedKeyList.clear();
      reusedValueList.clear();

      long chunkLength = decoder.readArrayStart();

      while (chunkLength > 0) {
        for (int i = 0; i < chunkLength; i += 1) {
          reusedKeyList.add(keyReader.read(decoder, null));
          reusedValueList.add(valueReader.read(decoder, null));
        }

        chunkLength = decoder.arrayNext();
      }

      return new ArrayBasedMapData(
          new GenericArrayData(reusedKeyList.toArray()),
          new GenericArrayData(reusedValueList.toArray()));
    }
  }

  /** 读取 Avro map（分块键值对）为 Spark ArrayBasedMapData，复用 key/value List。 */
  private static class MapReader implements ValueReader<ArrayBasedMapData> {
    private final ValueReader<?> keyReader;
    private final ValueReader<?> valueReader;

    private final List<Object> reusedKeyList = Lists.newArrayList();
    private final List<Object> reusedValueList = Lists.newArrayList();

    private MapReader(ValueReader<?> keyReader, ValueReader<?> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }

    @Override
    public ArrayBasedMapData read(Decoder decoder, Object reuse) throws IOException {
      reusedKeyList.clear();
      reusedValueList.clear();

      long chunkLength = decoder.readMapStart();

      while (chunkLength > 0) {
        for (int i = 0; i < chunkLength; i += 1) {
          reusedKeyList.add(keyReader.read(decoder, null));
          reusedValueList.add(valueReader.read(decoder, null));
        }

        chunkLength = decoder.mapNext();
      }

      return new ArrayBasedMapData(
          new GenericArrayData(reusedKeyList.toArray()),
          new GenericArrayData(reusedValueList.toArray()));
    }
  }

  /**
   * 读取 Avro record 为 Spark {@link InternalRow}。
   *
   * <p>设计意图：继承 {@link ValueReaders.StructReader} 复用常量列与字段 ID 逻辑； 支持行复用（ reuse 为同长度
   * GenericInternalRow 时直接复用），set 时 null 与非 null 分别处理。
   */
  static class StructReader extends ValueReaders.StructReader<InternalRow> {
    private final int numFields;

    /** 构造 StructReader，记录字段数。 */
    protected StructReader(
        List<ValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
      super(readers, struct, idToConstant);
      this.numFields = readers.size();
    }
    /** 复用同长度 GenericInternalRow，否则新建。 */
    @Override
    protected InternalRow reuseOrCreate(Object reuse) {
      if (reuse instanceof GenericInternalRow
          && ((GenericInternalRow) reuse).numFields() == numFields) {
        return (InternalRow) reuse;
      }
      return new GenericInternalRow(numFields);
    }
    /** 返回值。 */
    @Override
    protected Object get(InternalRow struct, int pos) {
      return null;
    }
    /** 执行 set 相关操作。 */
    @Override
    protected void set(InternalRow struct, int pos, Object value) {
      if (value != null) {
        struct.update(pos, value);
      } else {
        struct.setNullAt(pos);
      }
    }
  }
}
