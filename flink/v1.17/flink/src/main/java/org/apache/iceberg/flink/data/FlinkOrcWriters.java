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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.DecimalData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.data.orc.GenericOrcWriters;
import org.apache.iceberg.orc.OrcValueWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.orc.storage.common.type.HiveDecimal;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;
import org.apache.orc.storage.ql.exec.vector.DecimalColumnVector;
import org.apache.orc.storage.ql.exec.vector.ListColumnVector;
import org.apache.orc.storage.ql.exec.vector.LongColumnVector;
import org.apache.orc.storage.ql.exec.vector.MapColumnVector;
import org.apache.orc.storage.ql.exec.vector.TimestampColumnVector;

/**
 * Flink {@link RowData} 写入 ORC 列式格式的 value writer 工具集。
 *
 * <p>所属模块：iceberg-flink，基于 iceberg-orc 的 {@link OrcValueWriter} 接口实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为各 Iceberg 基本类型（字符串、日期、时间、时间戳、十进制等）提供 Flink 专用的 ORC 写入器。
 *   <li>提供 list/map/struct 复合类型的写入器，将 Flink ArrayData/MapData/RowData 写入 ORC 列向量。
 *   <li>处理 Flink 与 Iceberg/ORC 之间的时间单位、精度等差异（如 Flink 时间为毫秒，Iceberg 为微秒）。
 * </ul>
 *
 * <p>设计意图：采用枚举式单例（INSTANCE）与参数化实例相结合的方式，避免重复创建无状态 writer； 十进制按精度分两档（≤18 走 long，≤38 走
 * HiveDecimal），兼顾性能与精度上限。
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.flink.data.FlinkOrcReader} 的对称写入侧及 ORC 写入流程调用； 复合类型 writer
 * 内部递归复用元素/字段 writer。
 */
class FlinkOrcWriters {

  /** 工具类私有构造，禁止实例化。 */
  private FlinkOrcWriters() {}

  /** 返回将 Flink {@link StringData} 写入 ORC 字节列的 writer（单例）。 */
  static OrcValueWriter<StringData> strings() {
    return StringWriter.INSTANCE;
  }

  /** 返回将日期（int，自 epoch 的天数）写入 ORC long 列的 writer（单例）。 */
  static OrcValueWriter<Integer> dates() {
    return DateWriter.INSTANCE;
  }

  /** 返回将时间（int，毫秒）写入 ORC long 列的 writer（单例），内部转为微秒。 */
  static OrcValueWriter<Integer> times() {
    return TimeWriter.INSTANCE;
  }

  /** 返回将不带时区的 {@link TimestampData} 写入 ORC 时间戳列的 writer（单例，UTC）。 */
  static OrcValueWriter<TimestampData> timestamps() {
    return TimestampWriter.INSTANCE;
  }

  /** 返回将带时区的 {@link TimestampData} 写入 ORC 时间戳列的 writer（单例）。 */
  static OrcValueWriter<TimestampData> timestampTzs() {
    return TimestampTzWriter.INSTANCE;
  }

  /**
   * 按精度返回十进制 writer：精度 ≤18 用 long 承载，≤38 用 HiveDecimal 承载，否则抛出异常。
   *
   * @param precision 十进制精度
   * @param scale 十进制标度
   * @return 对应的十进制 writer
   */
  static OrcValueWriter<DecimalData> decimals(int precision, int scale) {
    if (precision <= 18) {
      return new Decimal18Writer(precision, scale);
    } else if (precision <= 38) {
      return new Decimal38Writer(precision, scale);
    } else {
      throw new IllegalArgumentException("Invalid precision: " + precision);
    }
  }

  /**
   * 构造 list 类型 writer，复用元素 writer 写入 ORC ListColumnVector。
   *
   * @param elementWriter 元素 writer
   * @param elementType 元素的 Flink LogicalType
   * @param <T> 元素值类型
   * @return list writer
   */
  static <T> OrcValueWriter<ArrayData> list(
      OrcValueWriter<T> elementWriter, LogicalType elementType) {
    return new ListWriter<>(elementWriter, elementType);
  }

  /**
   * 构造 map 类型 writer，复用键、值 writer 写入 ORC MapColumnVector。
   *
   * @param keyWriter 键 writer
   * @param valueWriter 值 writer
   * @param keyType 键的 Flink LogicalType
   * @param valueType 值的 Flink LogicalType
   * @param <K> 键类型
   * @param <V> 值类型
   * @return map writer
   */
  static <K, V> OrcValueWriter<MapData> map(
      OrcValueWriter<K> keyWriter,
      OrcValueWriter<V> valueWriter,
      LogicalType keyType,
      LogicalType valueType) {
    return new MapWriter<>(keyWriter, valueWriter, keyType, valueType);
  }

  /**
   * 构造 struct（RowData）类型 writer，按字段类型预建 FieldGetter 列表以加速取值。
   *
   * @param writers 各字段 writer
   * @param types 各字段 Flink LogicalType
   * @return struct writer
   */
  static OrcValueWriter<RowData> struct(List<OrcValueWriter<?>> writers, List<LogicalType> types) {
    return new RowDataWriter(writers, types);
  }

  /** 将 Flink {@link StringData} 以字节引用形式写入 ORC BytesColumnVector。 */
  private static class StringWriter implements OrcValueWriter<StringData> {
    private static final StringWriter INSTANCE = new StringWriter();

    /** 将 StringData 的底层字节数组以引用方式设置到 BytesColumnVector。 */
    @Override
    public void nonNullWrite(int rowId, StringData data, ColumnVector output) {
      byte[] value = data.toBytes();
      ((BytesColumnVector) output).setRef(rowId, value, 0, value.length);
    }
  }

  /** 将日期值（int 天数）直接写入 ORC LongColumnVector。 */
  private static class DateWriter implements OrcValueWriter<Integer> {
    private static final DateWriter INSTANCE = new DateWriter();

    /** 将日期整数值写入 LongColumnVector 对应行。 */
    @Override
    public void nonNullWrite(int rowId, Integer data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data;
    }
  }

  /** 将时间值（int 毫秒）转换为微秒后写入 ORC LongColumnVector。 */
  private static class TimeWriter implements OrcValueWriter<Integer> {
    private static final TimeWriter INSTANCE = new TimeWriter();

    /** 将毫秒时间乘以 1000 转为微秒后写入 LongColumnVector。 */
    @Override
    public void nonNullWrite(int rowId, Integer millis, ColumnVector output) {
      // The time in flink is in millisecond, while the standard time in iceberg is microsecond.
      // So we need to transform it to microsecond.
      ((LongColumnVector) output).vector[rowId] = millis * 1000L;
    }
  }

  /** 将不带时区的时间戳写入 ORC TimestampColumnVector，标记为 UTC 并截断到微秒精度。 */
  private static class TimestampWriter implements OrcValueWriter<TimestampData> {
    private static final TimestampWriter INSTANCE = new TimestampWriter();

    /**
     * 写入不带时区的时间戳。
     *
     * <p>逻辑：标记列向量为 UTC；将 TimestampData 转为 UTC OffsetDateTime，毫秒部分写入 cv.time， 纳秒部分截断到微秒后写入 cv.nanos。
     */
    @Override
    public void nonNullWrite(int rowId, TimestampData data, ColumnVector output) {
      TimestampColumnVector cv = (TimestampColumnVector) output;
      cv.setIsUTC(true);
      // millis
      OffsetDateTime offsetDateTime = data.toInstant().atOffset(ZoneOffset.UTC);
      cv.time[rowId] =
          offsetDateTime.toEpochSecond() * 1_000 + offsetDateTime.getNano() / 1_000_000;
      // truncate nanos to only keep microsecond precision.
      cv.nanos[rowId] = (offsetDateTime.getNano() / 1_000) * 1_000;
    }
  }

  /** 将带时区的时间戳写入 ORC TimestampColumnVector，截断到微秒精度。 */
  private static class TimestampTzWriter implements OrcValueWriter<TimestampData> {
    private static final TimestampTzWriter INSTANCE = new TimestampTzWriter();

    /**
     * 写入带时区的时间戳。
     *
     * <p>逻辑：将 TimestampData 转为 Instant，毫秒部分写入 cv.time，纳秒截断到微秒后写入 cv.nanos。 抑制 Instant.getNano 相关告警。
     */
    @SuppressWarnings("JavaInstantGetSecondsGetNano")
    @Override
    public void nonNullWrite(int rowId, TimestampData data, ColumnVector output) {
      TimestampColumnVector cv = (TimestampColumnVector) output;
      // millis
      Instant instant = data.toInstant();
      cv.time[rowId] = instant.toEpochMilli();
      // truncate nanos to only keep microsecond precision.
      cv.nanos[rowId] = (instant.getNano() / 1_000) * 1_000;
    }
  }

  /** 将精度 ≤18 的十进制以 unscaled long 写入 ORC DecimalColumnVector。 */
  private static class Decimal18Writer implements OrcValueWriter<DecimalData> {
    private final int precision;
    private final int scale;

    Decimal18Writer(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    /**
     * 写入精度 ≤18 的十进制。
     *
     * <p>逻辑：先校验 scale 与 precision 合法性，再以 unscaled long + scale 写入 DecimalColumnVector。
     */
    @Override
    public void nonNullWrite(int rowId, DecimalData data, ColumnVector output) {
      Preconditions.checkArgument(
          scale == data.scale(),
          "Cannot write value as decimal(%s,%s), wrong scale: %s",
          precision,
          scale,
          data);
      Preconditions.checkArgument(
          data.precision() <= precision,
          "Cannot write value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          data);

      ((DecimalColumnVector) output)
          .vector[rowId].setFromLongAndScale(data.toUnscaledLong(), data.scale());
    }
  }

  /** 将精度 19~38 的十进制以 HiveDecimal 写入 ORC DecimalColumnVector。 */
  private static class Decimal38Writer implements OrcValueWriter<DecimalData> {
    private final int precision;
    private final int scale;

    Decimal38Writer(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public void nonNullWrite(int rowId, DecimalData data, ColumnVector output) {
      Preconditions.checkArgument(
          scale == data.scale(),
          "Cannot write value as decimal(%s,%s), wrong scale: %s",
          precision,
          scale,
          data);
      Preconditions.checkArgument(
          data.precision() <= precision,
          "Cannot write value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          data);

      ((DecimalColumnVector) output)
          .vector[rowId].set(HiveDecimal.create(data.toBigDecimal(), false));
    }
  }

  /**
   * list 写入器：将 Flink {@link ArrayData} 的元素依次写入 ORC {@link ListColumnVector} 的 child 向量。 复用元素 writer
   * 与按元素类型生成的 ElementGetter。
   */
  static class ListWriter<T> implements OrcValueWriter<ArrayData> {
    private final OrcValueWriter<T> elementWriter;
    private final ArrayData.ElementGetter elementGetter;

    ListWriter(OrcValueWriter<T> elementWriter, LogicalType elementType) {
      this.elementWriter = elementWriter;
      this.elementGetter = ArrayData.createElementGetter(elementType);
    }

    /**
     * 写入数组。
     *
     * <p>逻辑：设置 ListColumnVector 的 length/offset，累加 childCount，必要时扩容 child 向量， 然后逐元素取出并委托元素 writer
     * 写入。
     */
    @Override
    @SuppressWarnings("unchecked")
    public void nonNullWrite(int rowId, ArrayData data, ColumnVector output) {
      ListColumnVector cv = (ListColumnVector) output;
      cv.lengths[rowId] = data.size();
      cv.offsets[rowId] = cv.childCount;
      cv.childCount = (int) (cv.childCount + cv.lengths[rowId]);
      // make sure the child is big enough.
      growColumnVector(cv.child, cv.childCount);

      for (int e = 0; e < cv.lengths[rowId]; ++e) {
        Object value = elementGetter.getElementOrNull(data, e);
        elementWriter.write((int) (e + cv.offsets[rowId]), (T) value, cv.child);
      }
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return elementWriter.metrics();
    }
  }

  /**
   * map 写入器：将 Flink {@link MapData} 的键值依次写入 ORC {@link MapColumnVector} 的 keys/values 子向量。 复用键、值
   * writer 与对应的 ElementGetter。
   */
  static class MapWriter<K, V> implements OrcValueWriter<MapData> {
    private final OrcValueWriter<K> keyWriter;
    private final OrcValueWriter<V> valueWriter;
    private final ArrayData.ElementGetter keyGetter;
    private final ArrayData.ElementGetter valueGetter;

    MapWriter(
        OrcValueWriter<K> keyWriter,
        OrcValueWriter<V> valueWriter,
        LogicalType keyType,
        LogicalType valueType) {
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
      this.keyGetter = ArrayData.createElementGetter(keyType);
      this.valueGetter = ArrayData.createElementGetter(valueType);
    }

    /**
     * 写入 map。
     *
     * <p>逻辑：设置 MapColumnVector 的 length/offset，扩容 keys/values 子向量， 然后逐条取出键值并分别委托键、值 writer 写入。
     */
    @Override
    @SuppressWarnings("unchecked")
    public void nonNullWrite(int rowId, MapData data, ColumnVector output) {
      MapColumnVector cv = (MapColumnVector) output;
      ArrayData keyArray = data.keyArray();
      ArrayData valArray = data.valueArray();

      // record the length and start of the list elements
      cv.lengths[rowId] = data.size();
      cv.offsets[rowId] = cv.childCount;
      cv.childCount = (int) (cv.childCount + cv.lengths[rowId]);
      // make sure the child is big enough
      growColumnVector(cv.keys, cv.childCount);
      growColumnVector(cv.values, cv.childCount);
      // Add each element
      for (int e = 0; e < cv.lengths[rowId]; ++e) {
        int pos = (int) (e + cv.offsets[rowId]);
        keyWriter.write(pos, (K) keyGetter.getElementOrNull(keyArray, e), cv.keys);
        valueWriter.write(pos, (V) valueGetter.getElementOrNull(valArray, e), cv.values);
      }
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return Stream.concat(keyWriter.metrics(), valueWriter.metrics());
    }
  }

  /**
   * struct 写入器：基于父类 {@link GenericOrcWriters.StructWriter}，按字段类型预建 {@link RowData.FieldGetter}
   * 列表，通过 {@link #get(RowData, int)} 取字段值后交由各字段 writer 写入。
   */
  static class RowDataWriter extends GenericOrcWriters.StructWriter<RowData> {
    private final List<RowData.FieldGetter> fieldGetters;

    RowDataWriter(List<OrcValueWriter<?>> writers, List<LogicalType> types) {
      super(writers);

      this.fieldGetters = Lists.newArrayListWithExpectedSize(types.size());
      for (int i = 0; i < types.size(); i++) {
        fieldGetters.add(RowData.createFieldGetter(types.get(i), i));
      }
    }

    /** 按字段索引从 RowData 取值，委托预建的 FieldGetter。 */
    @Override
    protected Object get(RowData struct, int index) {
      return fieldGetters.get(index).getFieldOrNull(struct);
    }
  }

  /**
   * 按需扩容 ORC 列向量，使用 3 倍增长因子以减少频繁分配。
   *
   * @param cv 待扩容的列向量
   * @param requestedSize 所需容量
   */
  private static void growColumnVector(ColumnVector cv, int requestedSize) {
    if (cv.isNull.length < requestedSize) {
      // Use growth factor of 3 to avoid frequent array allocations
      cv.ensureSize(requestedSize * 3, true);
    }
  }
}
