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
import java.lang.reflect.Array;
import java.util.List;
import org.apache.avro.io.Encoder;
import org.apache.avro.util.Utf8;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.DecimalData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.iceberg.avro.ValueWriter;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.DecimalUtil;

/**
 * 文件级说明：Avro {@link ValueWriter} 的 Flink 专用实现集合。
 *
 * <p>所属模块：iceberg-flink（数据写入子包 data），为 {@link FlinkAvroWriter} 提供 针对 Flink
 * 数据类型（StringData、DecimalData、TimestampData、ArrayData、MapData、RowData）的 Avro 编码器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供工厂方法创建各类 Flink 类型的 ValueWriter（strings/decimal/array/map/row 等）。
 *   <li>实现具体的 Avro 编码逻辑，将 Flink 内部数据结构写入 Avro Encoder。
 * </ul>
 *
 * <p>设计意图：Flink 使用 StringData/DecimalData/TimestampData 等紧凑二进制表示， 与 Avro 的 UTF8/fixed/long
 * 编码模型不同。本类为每种类型提供专用 writer 做高效转换， 避免中间字符串创建等开销。DecimalWriter 使用 ThreadLocal 字节缓冲区避免频繁分配。
 *
 * <p>上下游关系：被 {@link FlinkAvroWriter.WriteBuilder} 调用以构建 writer 树。
 */
public class FlinkValueWriters {

  private FlinkValueWriters() {}

  /** 创建 StringData 的 Avro writer（单例）。 */
  static ValueWriter<StringData> strings() {
    return StringWriter.INSTANCE;
  }

  /** 创建 TIME（微秒）的 Avro writer（单例），将毫秒值转为微秒。 */
  static ValueWriter<Integer> timeMicros() {
    return TimeMicrosWriter.INSTANCE;
  }

  /** 创建 TIMESTAMP（微秒）的 Avro writer（单例）。 */
  static ValueWriter<TimestampData> timestampMicros() {
    return TimestampMicrosWriter.INSTANCE;
  }

  /**
   * 创建 DecimalData 的 Avro writer。
   *
   * @param precision 精度
   * @param scale 标度
   * @return DecimalWriter 实例
   */
  static ValueWriter<DecimalData> decimal(int precision, int scale) {
    return new DecimalWriter(precision, scale);
  }

  /**
   * 创建 ArrayData 的 Avro writer。
   *
   * @param elementWriter 元素 writer
   * @param elementType Flink 元素类型
   * @return ArrayWriter 实例
   */
  static <T> ValueWriter<ArrayData> array(ValueWriter<T> elementWriter, LogicalType elementType) {
    return new ArrayWriter<>(elementWriter, elementType);
  }

  /**
   * 创建数组形式 Map（arrayMap）的 Avro writer，key/value 以数组形式逐对写入。
   *
   * @param keyWriter key writer
   * @param keyType Flink key 类型
   * @param valueWriter value writer
   * @param valueType Flink value 类型
   * @return ArrayMapWriter 实例
   */
  static <K, V> ValueWriter<MapData> arrayMap(
      ValueWriter<K> keyWriter,
      LogicalType keyType,
      ValueWriter<V> valueWriter,
      LogicalType valueType) {
    return new ArrayMapWriter<>(keyWriter, keyType, valueWriter, valueType);
  }

  /**
   * 创建标准 Map 的 Avro writer，使用 Avro 的 map 编码。
   *
   * @param keyWriter key writer
   * @param keyType Flink key 类型
   * @param valueWriter value writer
   * @param valueType Flink value 类型
   * @return MapWriter 实例
   */
  static <K, V> ValueWriter<MapData> map(
      ValueWriter<K> keyWriter,
      LogicalType keyType,
      ValueWriter<V> valueWriter,
      LogicalType valueType) {
    return new MapWriter<>(keyWriter, keyType, valueWriter, valueType);
  }

  /**
   * 创建 RowData 的 Avro writer。
   *
   * @param writers 各字段 writer
   * @param types 各字段 Flink 类型
   * @return RowWriter 实例
   */
  static ValueWriter<RowData> row(List<ValueWriter<?>> writers, List<LogicalType> types) {
    return new RowWriter(writers, types);
  }

  /** StringData 写入器：将 Flink StringData 转为 Avro UTF8 写入。 */
  private static class StringWriter implements ValueWriter<StringData> {
    private static final StringWriter INSTANCE = new StringWriter();

    private StringWriter() {}

    @Override
    public void write(StringData s, Encoder encoder) throws IOException {
      // toBytes is cheaper than Avro calling toString, which incurs encoding costs
      encoder.writeString(new Utf8(s.toBytes()));
    }
  }

  /**
   * DecimalData 写入器：将 DecimalData 转为固定长度字节数组写入。
   *
   * <p>设计要点：使用 ThreadLocal 字节缓冲区避免每次写入时分配。
   */
  private static class DecimalWriter implements ValueWriter<DecimalData> {
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
    public void write(DecimalData d, Encoder encoder) throws IOException {
      encoder.writeFixed(
          DecimalUtil.toReusedFixLengthBytes(precision, scale, d.toBigDecimal(), bytes.get()));
    }
  }

  /** TIME 写入器：将毫秒整数值乘以 1000 转为微秒写入。 */
  private static class TimeMicrosWriter implements ValueWriter<Integer> {
    private static final TimeMicrosWriter INSTANCE = new TimeMicrosWriter();

    @Override
    public void write(Integer timeMills, Encoder encoder) throws IOException {
      encoder.writeLong(timeMills * 1000L);
    }
  }

  /** TIMESTAMP 写入器：将 TimestampData 的毫秒+纳秒部分合成为微秒写入。 */
  private static class TimestampMicrosWriter implements ValueWriter<TimestampData> {
    private static final TimestampMicrosWriter INSTANCE = new TimestampMicrosWriter();

    @Override
    public void write(TimestampData timestampData, Encoder encoder) throws IOException {
      long micros =
          timestampData.getMillisecond() * 1000 + timestampData.getNanoOfMillisecond() / 1000;
      encoder.writeLong(micros);
    }
  }

  /**
   * ArrayData 写入器：遍历数组元素逐个写入 Avro array。
   *
   * <p>设计要点：使用预创建的 ElementGetter 避免逐元素类型判断开销。
   */
  private static class ArrayWriter<T> implements ValueWriter<ArrayData> {
    private final ValueWriter<T> elementWriter;
    private final ArrayData.ElementGetter elementGetter;

    private ArrayWriter(ValueWriter<T> elementWriter, LogicalType elementType) {
      this.elementWriter = elementWriter;
      this.elementGetter = ArrayData.createElementGetter(elementType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(ArrayData array, Encoder encoder) throws IOException {
      encoder.writeArrayStart();
      int numElements = array.size();
      encoder.setItemCount(numElements);
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        elementWriter.write((T) elementGetter.getElementOrNull(array, i), encoder);
      }
      encoder.writeArrayEnd();
    }
  }

  /** 数组 Map 写入器：以 Avro array 形式逐对写入 key/value，支持任意 key 类型。 */
  private static class ArrayMapWriter<K, V> implements ValueWriter<MapData> {
    private final ValueWriter<K> keyWriter;
    private final ValueWriter<V> valueWriter;
    private final ArrayData.ElementGetter keyGetter;
    private final ArrayData.ElementGetter valueGetter;

    private ArrayMapWriter(
        ValueWriter<K> keyWriter,
        LogicalType keyType,
        ValueWriter<V> valueWriter,
        LogicalType valueType) {
      this.keyWriter = keyWriter;
      this.keyGetter = ArrayData.createElementGetter(keyType);
      this.valueWriter = valueWriter;
      this.valueGetter = ArrayData.createElementGetter(valueType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(MapData map, Encoder encoder) throws IOException {
      encoder.writeArrayStart();
      int numElements = map.size();
      encoder.setItemCount(numElements);
      ArrayData keyArray = map.keyArray();
      ArrayData valueArray = map.valueArray();
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        keyWriter.write((K) keyGetter.getElementOrNull(keyArray, i), encoder);
        valueWriter.write((V) valueGetter.getElementOrNull(valueArray, i), encoder);
      }
      encoder.writeArrayEnd();
    }
  }

  /** 标准 Map 写入器：使用 Avro map 编码写入 key/value 对。 */
  private static class MapWriter<K, V> implements ValueWriter<MapData> {
    private final ValueWriter<K> keyWriter;
    private final ValueWriter<V> valueWriter;
    private final ArrayData.ElementGetter keyGetter;
    private final ArrayData.ElementGetter valueGetter;

    private MapWriter(
        ValueWriter<K> keyWriter,
        LogicalType keyType,
        ValueWriter<V> valueWriter,
        LogicalType valueType) {
      this.keyWriter = keyWriter;
      this.keyGetter = ArrayData.createElementGetter(keyType);
      this.valueWriter = valueWriter;
      this.valueGetter = ArrayData.createElementGetter(valueType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(MapData map, Encoder encoder) throws IOException {
      encoder.writeMapStart();
      int numElements = map.size();
      encoder.setItemCount(numElements);
      ArrayData keyArray = map.keyArray();
      ArrayData valueArray = map.valueArray();
      for (int i = 0; i < numElements; i += 1) {
        encoder.startItem();
        keyWriter.write((K) keyGetter.getElementOrNull(keyArray, i), encoder);
        valueWriter.write((V) valueGetter.getElementOrNull(valueArray, i), encoder);
      }
      encoder.writeMapEnd();
    }
  }

  /**
   * RowData 写入器：逐字段写入 Avro record。
   *
   * <p>设计要点：预创建 FieldGetter 数组避免逐字段类型判断；null 字段直接写 null。
   */
  static class RowWriter implements ValueWriter<RowData> {
    private final ValueWriter<?>[] writers;
    private final RowData.FieldGetter[] getters;

    private RowWriter(List<ValueWriter<?>> writers, List<LogicalType> types) {
      this.writers = (ValueWriter<?>[]) Array.newInstance(ValueWriter.class, writers.size());
      this.getters = new RowData.FieldGetter[writers.size()];
      for (int i = 0; i < writers.size(); i += 1) {
        this.writers[i] = writers.get(i);
        this.getters[i] = RowData.createFieldGetter(types.get(i), i);
      }
    }

    @Override
    public void write(RowData row, Encoder encoder) throws IOException {
      for (int i = 0; i < writers.length; i += 1) {
        if (row.isNullAt(i)) {
          writers[i].write(null, encoder);
        } else {
          write(row, i, writers[i], encoder);
        }
      }
    }

    @SuppressWarnings("unchecked")
    private <T> void write(RowData row, int pos, ValueWriter<T> writer, Encoder encoder)
        throws IOException {
      writer.write((T) getters[pos].getFieldOrNull(row), encoder);
    }
  }
}
