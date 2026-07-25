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
 * Flink 专用的 ORC 值写入器工厂与内部实现集合。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：为各种 Iceberg 类型提供对应的 ORC 列向量写入器， 把 Flink {@link RowData}/{@link
 * ArrayData}/{@link MapData} 写入 ORC VectorizedRowBatch。
 *
 * <p>设计意图：工厂模式 + 单例，按类型提供 writer 实例；被 {@link FlinkOrcWriter} 调用。
 */
class FlinkOrcWriters {

  private FlinkOrcWriters() {}

  /** 返回字符串写入器单例。 */
  static OrcValueWriter<StringData> strings() {
    return StringWriter.INSTANCE;
  }

  /** 返回日期写入器单例。 */
  static OrcValueWriter<Integer> dates() {
    return DateWriter.INSTANCE;
  }

  /** 返回时间写入器单例。 */
  static OrcValueWriter<Integer> times() {
    return TimeWriter.INSTANCE;
  }

  /** 返回不带时区的时间戳写入器单例。 */
  static OrcValueWriter<TimestampData> timestamps() {
    return TimestampWriter.INSTANCE;
  }

  /** 返回带时区的时间戳写入器单例。 */
  static OrcValueWriter<TimestampData> timestampTzs() {
    return TimestampTzWriter.INSTANCE;
  }

  /** 按精度选择 Decimal18 或 Decimal38 写入器。 */
  static OrcValueWriter<DecimalData> decimals(int precision, int scale) {
    if (precision <= 18) {
      return new Decimal18Writer(precision, scale);
    } else if (precision <= 38) {
      return new Decimal38Writer(precision, scale);
    } else {
      throw new IllegalArgumentException("Invalid precision: " + precision);
    }
  }

  /** 构造 list 写入器。 */
  static <T> OrcValueWriter<ArrayData> list(
      OrcValueWriter<T> elementWriter, LogicalType elementType) {
    return new ListWriter<>(elementWriter, elementType);
  }

  /** 构造 map 写入器。 */
  static <K, V> OrcValueWriter<MapData> map(
      OrcValueWriter<K> keyWriter,
      OrcValueWriter<V> valueWriter,
      LogicalType keyType,
      LogicalType valueType) {
    return new MapWriter<>(keyWriter, valueWriter, keyType, valueType);
  }

  /** 构造 struct（RowData）写入器。 */
  static OrcValueWriter<RowData> struct(List<OrcValueWriter<?>> writers, List<LogicalType> types) {
    return new RowDataWriter(writers, types);
  }

  /** 字符串写入器：把 Flink StringData 字节引用写入 BytesColumnVector。 */
  private static class StringWriter implements OrcValueWriter<StringData> {
    private static final StringWriter INSTANCE = new StringWriter();

    @Override
    public void nonNullWrite(int rowId, StringData data, ColumnVector output) {
      byte[] value = data.toBytes();
      ((BytesColumnVector) output).setRef(rowId, value, 0, value.length);
    }
  }

  /** 日期写入器：写入 int 到 LongColumnVector。 */
  private static class DateWriter implements OrcValueWriter<Integer> {
    private static final DateWriter INSTANCE = new DateWriter();

    @Override
    public void nonNullWrite(int rowId, Integer data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data;
    }
  }

  /** 时间写入器：毫秒转微秒（Iceberg 标准为微秒）。 */
  private static class TimeWriter implements OrcValueWriter<Integer> {
    private static final TimeWriter INSTANCE = new TimeWriter();

    @Override
    public void nonNullWrite(int rowId, Integer millis, ColumnVector output) {
      // The time in flink is in millisecond, while the standard time in iceberg is microsecond.
      // So we need to transform it to microsecond.
      ((LongColumnVector) output).vector[rowId] = millis * 1000L;
    }
  }

  /** 不带时区的时间戳写入器，转 UTC OffsetDateTime。 */
  private static class TimestampWriter implements OrcValueWriter<TimestampData> {
    private static final TimestampWriter INSTANCE = new TimestampWriter();

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

  /** 带时区的时间戳写入器，转 Instant。 */
  private static class TimestampTzWriter implements OrcValueWriter<TimestampData> {
    private static final TimestampTzWriter INSTANCE = new TimestampTzWriter();

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

  /** 精度 <=18 的 Decimal 写入器，使用 unscaled long。 */
  private static class Decimal18Writer implements OrcValueWriter<DecimalData> {
    private final int precision;
    private final int scale;

    Decimal18Writer(int precision, int scale) {
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
          .vector[rowId].setFromLongAndScale(data.toUnscaledLong(), data.scale());
    }
  }

  /** 精度 19-38 的 Decimal 写入器，使用 BigDecimal。 */
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

  /** 数组写入器：按偏移写入元素到 ListColumnVector。 */
  static class ListWriter<T> implements OrcValueWriter<ArrayData> {
    private final OrcValueWriter<T> elementWriter;
    private final ArrayData.ElementGetter elementGetter;

    ListWriter(OrcValueWriter<T> elementWriter, LogicalType elementType) {
      this.elementWriter = elementWriter;
      this.elementGetter = ArrayData.createElementGetter(elementType);
    }

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

  /** map 写入器：按偏移写入 key/value 到 MapColumnVector。 */
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

  /** struct（RowData）写入器：通过 FieldGetter 按字段索引取值后委托子写入器。 */
  static class RowDataWriter extends GenericOrcWriters.StructWriter<RowData> {
    private final List<RowData.FieldGetter> fieldGetters;

    RowDataWriter(List<OrcValueWriter<?>> writers, List<LogicalType> types) {
      super(writers);

      this.fieldGetters = Lists.newArrayListWithExpectedSize(types.size());
      for (int i = 0; i < types.size(); i++) {
        fieldGetters.add(RowData.createFieldGetter(types.get(i), i));
      }
    }

    @Override
    protected Object get(RowData struct, int index) {
      return fieldGetters.get(index).getFieldOrNull(struct);
    }
  }

  /** 扩容列向量，使用 3 倍增长因子避免频繁分配。 */
  private static void growColumnVector(ColumnVector cv, int requestedSize) {
    if (cv.isNull.length < requestedSize) {
      // Use growth factor of 3 to avoid frequent array allocations
      cv.ensureSize(requestedSize * 3, true);
    }
  }
}
