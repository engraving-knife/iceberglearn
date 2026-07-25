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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.DecimalData;
import org.apache.flink.table.data.GenericArrayData;
import org.apache.flink.table.data.GenericMapData;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.iceberg.orc.OrcValueReader;
import org.apache.iceberg.orc.OrcValueReaders;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;
import org.apache.orc.storage.ql.exec.vector.DecimalColumnVector;
import org.apache.orc.storage.ql.exec.vector.ListColumnVector;
import org.apache.orc.storage.ql.exec.vector.LongColumnVector;
import org.apache.orc.storage.ql.exec.vector.MapColumnVector;
import org.apache.orc.storage.ql.exec.vector.TimestampColumnVector;
import org.apache.orc.storage.serde2.io.HiveDecimalWritable;

/**
 * Flink 专用的 ORC 值读取器工厂与内部实现集合。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：为各种 Iceberg 类型提供对应的 ORC 列向量读取器， 把 ORC 向量化数据转换为 Flink {@link
 * RowData}/{@link ArrayData}/{@link MapData} 等。
 *
 * <p>设计意图：工厂模式 + 单例，按类型提供 reader 实例；被 {@link FlinkOrcReader} 调用。
 */
class FlinkOrcReaders {
  private FlinkOrcReaders() {}

  /** 返回字符串读取器单例。 */
  static OrcValueReader<StringData> strings() {
    return StringReader.INSTANCE;
  }

  /** 返回日期读取器单例。 */
  static OrcValueReader<Integer> dates() {
    return DateReader.INSTANCE;
  }

  /** 按精度选择 Decimal18 或 Decimal38 读取器。 */
  static OrcValueReader<DecimalData> decimals(int precision, int scale) {
    if (precision <= 18) {
      return new Decimal18Reader(precision, scale);
    } else if (precision <= 38) {
      return new Decimal38Reader(precision, scale);
    } else {
      throw new IllegalArgumentException("Invalid precision: " + precision);
    }
  }

  /** 返回时间读取器单例。 */
  static OrcValueReader<Integer> times() {
    return TimeReader.INSTANCE;
  }

  /** 返回不带时区的时间戳读取器单例。 */
  static OrcValueReader<TimestampData> timestamps() {
    return TimestampReader.INSTANCE;
  }

  /** 返回带时区的时间戳读取器单例。 */
  static OrcValueReader<TimestampData> timestampTzs() {
    return TimestampTzReader.INSTANCE;
  }

  /** 构造数组读取器。 */
  static <T> OrcValueReader<ArrayData> array(OrcValueReader<T> elementReader) {
    return new ArrayReader<>(elementReader);
  }

  /** 构造 map 读取器。 */
  public static <K, V> OrcValueReader<MapData> map(
      OrcValueReader<K> keyReader, OrcValueReader<V> valueReader) {
    return new MapReader<>(keyReader, valueReader);
  }

  /** 构造 struct 读取器，携带字段常量。 */
  public static OrcValueReader<RowData> struct(
      List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
    return new StructReader(readers, struct, idToConstant);
  }

  /** 字符串读取器：把 ORC 字节向量转为 Flink StringData。 */
  private static class StringReader implements OrcValueReader<StringData> {
    private static final StringReader INSTANCE = new StringReader();

    @Override
    public StringData nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      return StringData.fromBytes(
          bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
    }
  }

  /** 日期读取器：从 LongColumnVector 读取 int 值。 */
  private static class DateReader implements OrcValueReader<Integer> {
    private static final DateReader INSTANCE = new DateReader();

    @Override
    public Integer nonNullRead(ColumnVector vector, int row) {
      return (int) ((LongColumnVector) vector).vector[row];
    }
  }

  /** 精度 <=18 的 Decimal 读取器，使用 unscaled long 表示。 */
  private static class Decimal18Reader implements OrcValueReader<DecimalData> {
    private final int precision;
    private final int scale;

    Decimal18Reader(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public DecimalData nonNullRead(ColumnVector vector, int row) {
      HiveDecimalWritable value = ((DecimalColumnVector) vector).vector[row];

      // The hive ORC writer may will adjust the scale of decimal data.
      Preconditions.checkArgument(
          value.precision() <= precision,
          "Cannot read value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          value);

      return DecimalData.fromUnscaledLong(value.serialize64(scale), precision, scale);
    }
  }

  /** 精度 19-38 的 Decimal 读取器，使用 BigDecimal 表示。 */
  private static class Decimal38Reader implements OrcValueReader<DecimalData> {
    private final int precision;
    private final int scale;

    Decimal38Reader(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public DecimalData nonNullRead(ColumnVector vector, int row) {
      BigDecimal value =
          ((DecimalColumnVector) vector).vector[row].getHiveDecimal().bigDecimalValue();

      Preconditions.checkArgument(
          value.precision() <= precision,
          "Cannot read value as decimal(%s,%s), too large: %s",
          precision,
          scale,
          value);

      return DecimalData.fromBigDecimal(value, precision, scale);
    }
  }

  /** 时间读取器：微秒转毫秒（Flink 仅支持毫秒精度）。 */
  private static class TimeReader implements OrcValueReader<Integer> {
    private static final TimeReader INSTANCE = new TimeReader();

    @Override
    public Integer nonNullRead(ColumnVector vector, int row) {
      long micros = ((LongColumnVector) vector).vector[row];
      // Flink only support time mills, just erase micros.
      return (int) (micros / 1000);
    }
  }

  /** 不带时区的时间戳读取器，转 LocalDateTime。 */
  private static class TimestampReader implements OrcValueReader<TimestampData> {
    private static final TimestampReader INSTANCE = new TimestampReader();

    @Override
    public TimestampData nonNullRead(ColumnVector vector, int row) {
      TimestampColumnVector tcv = (TimestampColumnVector) vector;
      LocalDateTime localDate =
          Instant.ofEpochSecond(Math.floorDiv(tcv.time[row], 1_000), tcv.nanos[row])
              .atOffset(ZoneOffset.UTC)
              .toLocalDateTime();
      return TimestampData.fromLocalDateTime(localDate);
    }
  }

  /** 带时区的时间戳读取器，转 Instant。 */
  private static class TimestampTzReader implements OrcValueReader<TimestampData> {
    private static final TimestampTzReader INSTANCE = new TimestampTzReader();

    @Override
    public TimestampData nonNullRead(ColumnVector vector, int row) {
      TimestampColumnVector tcv = (TimestampColumnVector) vector;
      Instant instant =
          Instant.ofEpochSecond(Math.floorDiv(tcv.time[row], 1_000), tcv.nanos[row])
              .atOffset(ZoneOffset.UTC)
              .toInstant();
      return TimestampData.fromInstant(instant);
    }
  }

  /** 数组读取器：按偏移和长度遍历子向量。 */
  private static class ArrayReader<T> implements OrcValueReader<ArrayData> {
    private final OrcValueReader<T> elementReader;

    private ArrayReader(OrcValueReader<T> elementReader) {
      this.elementReader = elementReader;
    }

    @Override
    public ArrayData nonNullRead(ColumnVector vector, int row) {
      ListColumnVector listVector = (ListColumnVector) vector;
      int offset = (int) listVector.offsets[row];
      int length = (int) listVector.lengths[row];
      List<T> elements = Lists.newArrayListWithExpectedSize(length);
      for (int c = 0; c < length; ++c) {
        elements.add(elementReader.read(listVector.child, offset + c));
      }
      return new GenericArrayData(elements.toArray());
    }

    @Override
    public void setBatchContext(long batchOffsetInFile) {
      elementReader.setBatchContext(batchOffsetInFile);
    }
  }

  /** map 读取器：按偏移和长度遍历 key/value 子向量。 */
  private static class MapReader<K, V> implements OrcValueReader<MapData> {
    private final OrcValueReader<K> keyReader;
    private final OrcValueReader<V> valueReader;

    private MapReader(OrcValueReader<K> keyReader, OrcValueReader<V> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }

    @Override
    public MapData nonNullRead(ColumnVector vector, int row) {
      MapColumnVector mapVector = (MapColumnVector) vector;
      int offset = (int) mapVector.offsets[row];
      long length = mapVector.lengths[row];

      Map<K, V> map = Maps.newHashMap();
      for (int c = 0; c < length; c++) {
        K key = keyReader.read(mapVector.keys, offset + c);
        V value = valueReader.read(mapVector.values, offset + c);
        map.put(key, value);
      }

      return new GenericMapData(map);
    }

    @Override
    public void setBatchContext(long batchOffsetInFile) {
      keyReader.setBatchContext(batchOffsetInFile);
      valueReader.setBatchContext(batchOffsetInFile);
    }
  }

  /** struct 读取器：基于 GenericRowData 构造行数据。 */
  private static class StructReader extends OrcValueReaders.StructReader<RowData> {
    private final int numFields;

    StructReader(
        List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
      super(readers, struct, idToConstant);
      this.numFields = struct.fields().size();
    }

    @Override
    protected RowData create() {
      return new GenericRowData(numFields);
    }

    @Override
    protected void set(RowData struct, int pos, Object value) {
      ((GenericRowData) struct).setField(pos, value);
    }
  }
}
