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
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;
import org.apache.avro.io.Encoder;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.avro.ValueWriter;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.DecimalUtil;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark 值写入器集合（Avro）。
 *
 * <p>所属模块：iceberg-spark（data 子包）。提供面向 Spark {@link InternalRow}/{@link ArrayData}/ {@link
 * MapData}/{@link UTF8String}/{@link Decimal} 等内部类型的 Avro {@link ValueWriter} 实现 与工厂方法，供 {@link
 * SparkAvroWriter} 在构建写入器树时使用。
 *
 * <p>设计意图：将 Iceberg 通用 Avro 写入逻辑与 Spark 内部类型适配分离；字符串/UUID/decimal 等 使用 ThreadLocal 缓冲减少分配，提升写入性能。
 *
 * <p>上下游关系：被 {@link SparkAvroWriter.WriteBuilder} 调用；底层依赖 Iceberg avro 的 {@link ValueWriter} 编码能力。
 */
public class SparkValueWriters {

  private SparkValueWriters() {}

  /** 返回 UTF8String 写入器（单例）。 */
  static ValueWriter<UTF8String> strings() {
    return StringWriter.INSTANCE;
  }

  /** 返回 UUID（以 UTF8String 表示）写入器（单例）。 */
  static ValueWriter<UTF8String> uuids() {
    return UUIDWriter.INSTANCE;
  }

  /** 返回指定精度/标度的 Decimal 写入器。 */
  static ValueWriter<Decimal> decimal(int precision, int scale) {
    return new DecimalWriter(precision, scale);
  }

  /** 返回数组写入器，按元素类型从 ArrayData 取值并写入。 */
  static <T> ValueWriter<ArrayData> array(ValueWriter<T> elementWriter, DataType elementType) {
    return new ArrayWriter<>(elementWriter, elementType);
  }

  /** 返回以数组形式编码的 map 写入器。 */
  static <K, V> ValueWriter<MapData> arrayMap(
      ValueWriter<K> keyWriter, DataType keyType, ValueWriter<V> valueWriter, DataType valueType) {
    return new ArrayMapWriter<>(keyWriter, keyType, valueWriter, valueType);
  }

  /** 返回以 Avro map 编码的 map 写入器。 */
  static <K, V> ValueWriter<MapData> map(
      ValueWriter<K> keyWriter, DataType keyType, ValueWriter<V> valueWriter, DataType valueType) {
    return new MapWriter<>(keyWriter, keyType, valueWriter, valueType);
  }

  /** 返回 struct 写入器，按各列类型从 InternalRow 取值并写入。 */
  static ValueWriter<InternalRow> struct(List<ValueWriter<?>> writers, List<DataType> types) {
    return new StructWriter(writers, types);
  }

  /** UTF8String 写入器：复用底层字节数组以避免 toString 编码开销。 */
  private static class StringWriter implements ValueWriter<UTF8String> {
    private static final StringWriter INSTANCE = new StringWriter();

    private StringWriter() {}

    @Override
    public void write(UTF8String s, Encoder encoder) throws IOException {
      // use getBytes because it may return the backing byte array if available.
      // otherwise, it copies to a new byte array, which is still cheaper than Avro
      // calling toString, which incurs encoding costs
      encoder.writeString(new Utf8(s.getBytes()));
    }
  }

  /** UUID 写入器：用 ThreadLocal 的 16 字节大端缓冲转换 UUID 后写出。 */
  private static class UUIDWriter implements ValueWriter<UTF8String> {
    private static final ThreadLocal<ByteBuffer> BUFFER =
        ThreadLocal.withInitial(
            () -> {
              ByteBuffer buffer = ByteBuffer.allocate(16);
              buffer.order(ByteOrder.BIG_ENDIAN);
              return buffer;
            });

    private static final UUIDWriter INSTANCE = new UUIDWriter();

    private UUIDWriter() {}

    @Override
    @SuppressWarnings("ByteBufferBackingArray")
    public void write(UTF8String s, Encoder encoder) throws IOException {
      // TODO: direct conversion from string to byte buffer
      UUID uuid = UUID.fromString(s.toString());
      // calling array() is safe because the buffer is always allocated by the thread-local
      encoder.writeFixed(UUIDUtil.convertToByteBuffer(uuid, BUFFER.get()).array());
    }
  }

  /** Decimal 写入器：用 ThreadLocal 字节数组以定长二进制编码写出。 */
  private static class DecimalWriter implements ValueWriter<Decimal> {
    private final int precision;
    private final int scale;
    private final ThreadLocal<byte[]> bytes;

    private DecimalWriter(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
      this.bytes =
          ThreadLocal.withInitial(() -> new byte[TypeUtil.decimalRequiredBytes(precision)]);
    }

    @Override
    public void write(Decimal d, Encoder encoder) throws IOException {
      encoder.writeFixed(
          DecimalUtil.toReusedFixLengthBytes(precision, scale, d.toJavaBigDecimal(), bytes.get()));
    }
  }

  /** 数组写入器：按 Avro 数组编码写出各元素。 */
  private static class ArrayWriter<T> implements ValueWriter<ArrayData> {
    private final ValueWriter<T> elementWriter;
    private final DataType elementType;

    private ArrayWriter(ValueWriter<T> elementWriter, DataType elementType) {
      this.elementWriter = elementWriter;
      this.elementType = elementType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(ArrayData array, Encoder encoder) throws IOException {
      encoder.writeArrayStart();
      int numElements = array.numElements();
      encoder.setItemCount(numElements);
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        elementWriter.write((T) array.get(i, elementType), encoder);
      }
      encoder.writeArrayEnd();
    }
  }

  /** 以数组形式编码的 map 写入器：键值成对作为数组元素写出。 */
  private static class ArrayMapWriter<K, V> implements ValueWriter<MapData> {
    private final ValueWriter<K> keyWriter;
    private final ValueWriter<V> valueWriter;
    private final DataType keyType;
    private final DataType valueType;

    private ArrayMapWriter(
        ValueWriter<K> keyWriter,
        DataType keyType,
        ValueWriter<V> valueWriter,
        DataType valueType) {
      this.keyWriter = keyWriter;
      this.keyType = keyType;
      this.valueWriter = valueWriter;
      this.valueType = valueType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(MapData map, Encoder encoder) throws IOException {
      encoder.writeArrayStart();
      int numElements = map.numElements();
      encoder.setItemCount(numElements);
      ArrayData keyArray = map.keyArray();
      ArrayData valueArray = map.valueArray();
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        keyWriter.write((K) keyArray.get(i, keyType), encoder);
        valueWriter.write((V) valueArray.get(i, valueType), encoder);
      }
      encoder.writeArrayEnd();
    }
  }

  /** 以 Avro map 编码的 map 写入器：键值成对写出。 */
  private static class MapWriter<K, V> implements ValueWriter<MapData> {
    private final ValueWriter<K> keyWriter;
    private final ValueWriter<V> valueWriter;
    private final DataType keyType;
    private final DataType valueType;

    private MapWriter(
        ValueWriter<K> keyWriter,
        DataType keyType,
        ValueWriter<V> valueWriter,
        DataType valueType) {
      this.keyWriter = keyWriter;
      this.keyType = keyType;
      this.valueWriter = valueWriter;
      this.valueType = valueType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(MapData map, Encoder encoder) throws IOException {
      encoder.writeMapStart();
      int numElements = map.numElements();
      encoder.setItemCount(numElements);
      ArrayData keyArray = map.keyArray();
      ArrayData valueArray = map.valueArray();
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        keyWriter.write((K) keyArray.get(i, keyType), encoder);
        valueWriter.write((V) valueArray.get(i, valueType), encoder);
      }
      encoder.writeMapEnd();
    }
  }

  /** struct 写入器：按列类型取值，空值直接写 null，非空委托子写入器。 */
  static class StructWriter implements ValueWriter<InternalRow> {
    private final ValueWriter<?>[] writers;
    private final DataType[] types;

    @SuppressWarnings("unchecked")
    private StructWriter(List<ValueWriter<?>> writers, List<DataType> types) {
      this.writers = (ValueWriter<?>[]) Array.newInstance(ValueWriter.class, writers.size());
      this.types = new DataType[writers.size()];
      for (int i = 0; i < writers.size(); i += 1) {
        this.writers[i] = writers.get(i);
        this.types[i] = types.get(i);
      }
    }

    ValueWriter<?>[] writers() {
      return writers;
    }

    @Override
    public void write(InternalRow row, Encoder encoder) throws IOException {
      for (int i = 0; i < types.length; i += 1) {
        if (row.isNullAt(i)) {
          writers[i].write(null, encoder);
        } else {
          write(row, i, writers[i], encoder);
        }
      }
    }

    @SuppressWarnings("unchecked")
    private <T> void write(InternalRow row, int pos, ValueWriter<T> writer, Encoder encoder)
        throws IOException {
      writer.write((T) row.get(pos, types[pos]), encoder);
    }
  }
}
