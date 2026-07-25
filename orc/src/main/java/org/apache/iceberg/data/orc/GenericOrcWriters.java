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
package org.apache.iceberg.data.orc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.iceberg.DoubleFieldMetrics;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.FloatFieldMetrics;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.orc.OrcRowWriter;
import org.apache.iceberg.orc.OrcValueWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.orc.storage.common.type.HiveDecimal;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;
import org.apache.orc.storage.ql.exec.vector.DecimalColumnVector;
import org.apache.orc.storage.ql.exec.vector.DoubleColumnVector;
import org.apache.orc.storage.ql.exec.vector.ListColumnVector;
import org.apache.orc.storage.ql.exec.vector.LongColumnVector;
import org.apache.orc.storage.ql.exec.vector.MapColumnVector;
import org.apache.orc.storage.ql.exec.vector.StructColumnVector;
import org.apache.orc.storage.ql.exec.vector.TimestampColumnVector;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * 通用 ORC 字段写入器集合：为 Iceberg 各种类型提供从 Java 对象到 ORC 列向量的具体编码实现。
 *
 * <p>所属模块：iceberg-orc（data/orc 子包）。本类是 {@link GenericOrcWriter} 的底层依赖， 把 Iceberg 的 Java 值逐类型写入 ORC
 * 的列向量。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 boolean/byte/short/int/long/float/double/string/uuid/decimal/date/time/timestamp 等基础类型的
 *       writer 工厂方法。
 *   <li>提供 list/map/struct 复合类型的 writer，处理子向量的偏移与扩容。
 *   <li>提供 PositionDelete 行的写入器，用于删除文件中位置删除行。
 *   <li>float/double 的 writer 内建 {@link FieldMetrics} 收集器，统计 nan 值与上下界。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>无状态基础类型 writer 使用单例（INSTANCE），减少对象创建开销。
 *   <li>float/double writer 需要字段 id 与 metrics，按字段实例化。
 *   <li>Decimal 按 precision 分为 Decimal18Writer（≤18，用 long 存储）和 Decimal38Writer（≤38，用 HiveDecimal），
 *       以匹配 ORC 的存储优化。
 *   <li>StructWriter 抽象出 get() 供子类实现从不同行模型取值，复用列写入逻辑。
 * </ul>
 *
 * <p>上下游关系：被 {@link GenericOrcWriter.WriteBuilder} 调用；底层依赖 ORC 的各类 ColumnVector。
 */
public class GenericOrcWriters {
  private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  private static final LocalDate EPOCH_DAY = EPOCH.toLocalDate();

  private GenericOrcWriters() {}

  /** 返回 boolean writer（单例），写入 LongColumnVector 的 0/1。 */
  public static OrcValueWriter<Boolean> booleans() {
    return BooleanWriter.INSTANCE;
  }

  /** 返回 byte writer（单例），写入 LongColumnVector。 */
  public static OrcValueWriter<Byte> bytes() {
    return ByteWriter.INSTANCE;
  }

  /** 返回 short writer（单例），写入 LongColumnVector。 */
  public static OrcValueWriter<Short> shorts() {
    return ShortWriter.INSTANCE;
  }

  /** 返回 int writer（单例），写入 LongColumnVector。 */
  public static OrcValueWriter<Integer> ints() {
    return IntWriter.INSTANCE;
  }

  /** 返回 time writer（单例），将 LocalTime 转微秒写入 LongColumnVector。 */
  public static OrcValueWriter<LocalTime> times() {
    return TimeWriter.INSTANCE;
  }

  /** 返回 long writer（单例），写入 LongColumnVector。 */
  public static OrcValueWriter<Long> longs() {
    return LongWriter.INSTANCE;
  }

  /**
   * 返回 float writer，内建 {@link FloatFieldMetrics} 收集器。
   *
   * @param id 字段 id，用于 metrics 标识
   */
  public static OrcValueWriter<Float> floats(int id) {
    return new FloatWriter(id);
  }

  /**
   * 返回 double writer，内建 {@link DoubleFieldMetrics} 收集器。
   *
   * @param id 字段 id，用于 metrics 标识
   */
  public static OrcValueWriter<Double> doubles(int id) {
    return new DoubleWriter(id);
  }

  /** 返回 string writer（单例），UTF-8 编码后写入 BytesColumnVector。 */
  public static OrcValueWriter<String> strings() {
    return StringWriter.INSTANCE;
  }

  /** 返回 ByteBuffer writer（单例），写入 BytesColumnVector。 */
  public static OrcValueWriter<ByteBuffer> byteBuffers() {
    return ByteBufferWriter.INSTANCE;
  }

  /** 返回 UUID writer（单例），序列化为 16 字节写入 BytesColumnVector。 */
  public static OrcValueWriter<UUID> uuids() {
    return UUIDWriter.INSTANCE;
  }

  /** 返回 byte[] writer（单例），写入 BytesColumnVector。 */
  public static OrcValueWriter<byte[]> byteArrays() {
    return ByteArrayWriter.INSTANCE;
  }

  /** 返回 date writer（单例），距纪元天数写入 LongColumnVector。 */
  public static OrcValueWriter<LocalDate> dates() {
    return DateWriter.INSTANCE;
  }

  /** 返回带时区时间戳 writer（单例），毫秒+纳秒写入 TimestampColumnVector。 */
  public static OrcValueWriter<OffsetDateTime> timestampTz() {
    return TimestampTzWriter.INSTANCE;
  }

  /** 返回无时区时间戳 writer（单例），以 UTC 解释后写入 TimestampColumnVector。 */
  public static OrcValueWriter<LocalDateTime> timestamp() {
    return TimestampWriter.INSTANCE;
  }

  /**
   * 返回 decimal writer，按精度选择实现。
   *
   * <p>逻辑：precision ≤ 18 用 {@link Decimal18Writer}（long 存储，性能更优）； precision ≤ 38 用 {@link
   * Decimal38Writer}（HiveDecimal 存储）；否则抛出异常。
   *
   * @param precision Decimal 精度
   * @param scale Decimal 标度
   * @return 对应的 decimal writer
   * @throws IllegalArgumentException precision > 38
   */
  public static OrcValueWriter<BigDecimal> decimal(int precision, int scale) {
    if (precision <= 18) {
      return new Decimal18Writer(precision, scale);
    } else if (precision <= 38) {
      return new Decimal38Writer(precision, scale);
    } else {
      throw new IllegalArgumentException("Invalid precision: " + precision);
    }
  }

  /** 创建 list writer，包装元素 writer，处理子向量的偏移与扩容。 */
  public static <T> OrcValueWriter<List<T>> list(OrcValueWriter<T> element) {
    return new ListWriter<>(element);
  }

  /** 创建 map writer，组合 key/value writer，处理子向量的偏移与扩容。 */
  public static <K, V> OrcValueWriter<Map<K, V>> map(
      OrcValueWriter<K> key, OrcValueWriter<V> value) {
    return new MapWriter<>(key, value);
  }

  /**
   * 创建位置删除行的 writer。
   *
   * <p>设计意图：位置删除文件包含 (file_path, position[, row]) 三列，本 writer 复用 被替换行的 writer，并通过 pathTransformFunc
   * 对路径做转换（如相对路径→绝对路径）。
   *
   * @param writer 被删除行原有的 writer
   * @param pathTransformFunc 文件路径转换函数
   * @return 位置删除行 writer
   */
  public static <T> OrcRowWriter<PositionDelete<T>> positionDelete(
      OrcRowWriter<T> writer, Function<CharSequence, ?> pathTransformFunc) {
    return new PositionDeleteStructWriter<>(writer, pathTransformFunc);
  }

  private static class BooleanWriter implements OrcValueWriter<Boolean> {
    private static final OrcValueWriter<Boolean> INSTANCE = new BooleanWriter();

    @Override
    public void nonNullWrite(int rowId, Boolean data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data ? 1 : 0;
    }
  }

  private static class ByteWriter implements OrcValueWriter<Byte> {
    private static final OrcValueWriter<Byte> INSTANCE = new ByteWriter();

    @Override
    public void nonNullWrite(int rowId, Byte data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data;
    }
  }

  private static class ShortWriter implements OrcValueWriter<Short> {
    private static final OrcValueWriter<Short> INSTANCE = new ShortWriter();

    @Override
    public void nonNullWrite(int rowId, Short data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data;
    }
  }

  private static class IntWriter implements OrcValueWriter<Integer> {
    private static final OrcValueWriter<Integer> INSTANCE = new IntWriter();

    @Override
    public void nonNullWrite(int rowId, Integer data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data;
    }
  }

  /** Time writer：将 LocalTime 转为微秒写入 LongColumnVector。 */
  private static class TimeWriter implements OrcValueWriter<LocalTime> {
    private static final OrcValueWriter<LocalTime> INSTANCE = new TimeWriter();

    @Override
    public void nonNullWrite(int rowId, LocalTime data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data.toNanoOfDay() / 1_000;
    }
  }

  private static class LongWriter implements OrcValueWriter<Long> {
    private static final OrcValueWriter<Long> INSTANCE = new LongWriter();

    @Override
    public void nonNullWrite(int rowId, Long data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = data;
    }
  }

  /**
   * Float writer：写入 DoubleColumnVector 并收集 {@link FloatFieldMetrics}。
   *
   * <p>设计意图：ORC 的 float 列实际用 DoubleColumnVector 存储；同时独立统计 null 计数， 在 metrics() 中合并到 FieldMetrics（因
   * builder 不统计 null）。
   */
  private static class FloatWriter implements OrcValueWriter<Float> {
    private final FloatFieldMetrics.Builder floatFieldMetricsBuilder;
    private long nullValueCount = 0;

    private FloatWriter(int id) {
      this.floatFieldMetricsBuilder = new FloatFieldMetrics.Builder(id);
    }

    @Override
    public void nonNullWrite(int rowId, Float data, ColumnVector output) {
      ((DoubleColumnVector) output).vector[rowId] = data;
      floatFieldMetricsBuilder.addValue(data);
    }

    @Override
    public void nullWrite() {
      nullValueCount++;
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      FieldMetrics<Float> metricsWithoutNullCount = floatFieldMetricsBuilder.build();
      return Stream.of(
          new FieldMetrics<>(
              metricsWithoutNullCount.id(),
              metricsWithoutNullCount.valueCount() + nullValueCount,
              nullValueCount,
              metricsWithoutNullCount.nanValueCount(),
              metricsWithoutNullCount.lowerBound(),
              metricsWithoutNullCount.upperBound()));
    }
  }

  /**
   * Double writer：写入 DoubleColumnVector 并收集 {@link DoubleFieldMetrics}。
   *
   * <p>设计意图：同 FloatWriter，独立统计 null 计数并在 metrics() 中合并。
   */
  private static class DoubleWriter implements OrcValueWriter<Double> {
    private final DoubleFieldMetrics.Builder doubleFieldMetricsBuilder;
    private long nullValueCount = 0;

    private DoubleWriter(Integer id) {
      this.doubleFieldMetricsBuilder = new DoubleFieldMetrics.Builder(id);
    }

    @Override
    public void nonNullWrite(int rowId, Double data, ColumnVector output) {
      ((DoubleColumnVector) output).vector[rowId] = data;
      doubleFieldMetricsBuilder.addValue(data);
    }

    @Override
    public void nullWrite() {
      nullValueCount++;
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      FieldMetrics<Double> metricsWithoutNullCount = doubleFieldMetricsBuilder.build();
      return Stream.of(
          new FieldMetrics<>(
              metricsWithoutNullCount.id(),
              metricsWithoutNullCount.valueCount() + nullValueCount,
              nullValueCount,
              metricsWithoutNullCount.nanValueCount(),
              metricsWithoutNullCount.lowerBound(),
              metricsWithoutNullCount.upperBound()));
    }
  }

  /** String writer：UTF-8 编码后以 setRef 引用写入 BytesColumnVector。 */
  private static class StringWriter implements OrcValueWriter<String> {
    private static final OrcValueWriter<String> INSTANCE = new StringWriter();

    @Override
    public void nonNullWrite(int rowId, String data, ColumnVector output) {
      byte[] value = data.getBytes(StandardCharsets.UTF_8);
      ((BytesColumnVector) output).setRef(rowId, value, 0, value.length);
    }
  }

  /** ByteBuffer writer：优先用底层数组引用，否则拷贝后写入 BytesColumnVector。 */
  private static class ByteBufferWriter implements OrcValueWriter<ByteBuffer> {
    private static final OrcValueWriter<ByteBuffer> INSTANCE = new ByteBufferWriter();

    @Override
    public void nonNullWrite(int rowId, ByteBuffer data, ColumnVector output) {
      if (data.hasArray()) {
        ((BytesColumnVector) output)
            .setRef(rowId, data.array(), data.arrayOffset() + data.position(), data.remaining());
      } else {
        byte[] rawData = ByteBuffers.toByteArray(data);
        ((BytesColumnVector) output).setRef(rowId, rawData, 0, rawData.length);
      }
    }
  }

  /** UUID writer：序列化为 16 字节后写入 BytesColumnVector。 */
  private static class UUIDWriter implements OrcValueWriter<UUID> {
    private static final OrcValueWriter<UUID> INSTANCE = new UUIDWriter();

    @Override
    @SuppressWarnings("ByteBufferBackingArray")
    public void nonNullWrite(int rowId, UUID data, ColumnVector output) {
      ByteBuffer buffer = ByteBuffer.allocate(16);
      buffer.putLong(data.getMostSignificantBits());
      buffer.putLong(data.getLeastSignificantBits());
      ((BytesColumnVector) output).setRef(rowId, buffer.array(), 0, buffer.array().length);
    }
  }

  private static class ByteArrayWriter implements OrcValueWriter<byte[]> {
    private static final OrcValueWriter<byte[]> INSTANCE = new ByteArrayWriter();

    @Override
    public void nonNullWrite(int rowId, byte[] data, ColumnVector output) {
      ((BytesColumnVector) output).setRef(rowId, data, 0, data.length);
    }
  }

  /** Date writer：计算距纪元天数写入 LongColumnVector。 */
  private static class DateWriter implements OrcValueWriter<LocalDate> {
    private static final OrcValueWriter<LocalDate> INSTANCE = new DateWriter();

    @Override
    public void nonNullWrite(int rowId, LocalDate data, ColumnVector output) {
      ((LongColumnVector) output).vector[rowId] = ChronoUnit.DAYS.between(EPOCH_DAY, data);
    }
  }

  /** 带时区时间戳 writer：毫秒写入 time，纳秒截断到微秒写入 nanos。 */
  private static class TimestampTzWriter implements OrcValueWriter<OffsetDateTime> {
    private static final OrcValueWriter<OffsetDateTime> INSTANCE = new TimestampTzWriter();

    @Override
    @SuppressWarnings("JavaLocalDateTimeGetNano")
    public void nonNullWrite(int rowId, OffsetDateTime data, ColumnVector output) {
      TimestampColumnVector cv = (TimestampColumnVector) output;
      // millis
      cv.time[rowId] = data.toInstant().toEpochMilli();
      // truncate nanos to only keep microsecond precision
      cv.nanos[rowId] = data.getNano() / 1_000 * 1_000;
    }
  }

  /** 无时区时间戳 writer：以 UTC 解释，标记 setIsUTC 后写入 TimestampColumnVector。 */
  private static class TimestampWriter implements OrcValueWriter<LocalDateTime> {
    private static final OrcValueWriter<LocalDateTime> INSTANCE = new TimestampWriter();

    @Override
    @SuppressWarnings("JavaLocalDateTimeGetNano")
    public void nonNullWrite(int rowId, LocalDateTime data, ColumnVector output) {
      TimestampColumnVector cv = (TimestampColumnVector) output;
      cv.setIsUTC(true);
      cv.time[rowId] = data.toInstant(ZoneOffset.UTC).toEpochMilli(); // millis
      cv.nanos[rowId] =
          (data.getNano() / 1_000) * 1_000; // truncate nanos to only keep microsecond precision
    }
  }

  /**
   * Decimal writer（精度 ≤ 18）：用 long 存储未缩放值，通过 setFromLongAndScale 写入。
   *
   * <p>设计意图：ORC 对 precision ≤ 18 的 Decimal 内部用 long 存储，性能优于 HiveDecimal。
   */
  private static class Decimal18Writer implements OrcValueWriter<BigDecimal> {
    private final int precision;
    private final int scale;

    Decimal18Writer(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public void nonNullWrite(int rowId, BigDecimal data, ColumnVector output) {
      Preconditions.checkArgument(
          data.scale() == scale,
          "Cannot write value as decimal(%s,%s), wrong scale: %s",
          precision,
          scale,
          data);
      Preconditions.checkArgument(
          data.precision() <= precision,
          "Cannot write value as decimal(%s,%s), invalid precision: %s",
          precision,
          scale,
          data);

      ((DecimalColumnVector) output)
          .vector[rowId].setFromLongAndScale(data.unscaledValue().longValueExact(), scale);
    }
  }

  /**
   * Decimal writer（精度 ≤ 38）：用 {@link HiveDecimal} 存储。
   *
   * <p>设计意图：precision > 18 时 long 无法容纳，改用 HiveDecimal 保证精度。
   */
  private static class Decimal38Writer implements OrcValueWriter<BigDecimal> {
    private final int precision;
    private final int scale;

    Decimal38Writer(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    @Override
    public void nonNullWrite(int rowId, BigDecimal data, ColumnVector output) {
      Preconditions.checkArgument(
          data.scale() == scale,
          "Cannot write value as decimal(%s,%s), wrong scale: %s",
          precision,
          scale,
          data);
      Preconditions.checkArgument(
          data.precision() <= precision,
          "Cannot write value as decimal(%s,%s), invalid precision: %s",
          precision,
          scale,
          data);

      ((DecimalColumnVector) output).vector[rowId].set(HiveDecimal.create(data, false));
    }
  }

  /**
   * List writer：记录每个 list 的长度与偏移，按需扩容子向量后逐元素写入。
   *
   * <p>逻辑：设置 lengths[rowId] 和 offsets[rowId]（基于 childCount），更新 childCount， 扩容 child 向量，循环调用 element
   * writer 写入每个元素。
   */
  private static class ListWriter<T> implements OrcValueWriter<List<T>> {
    private final OrcValueWriter<T> element;

    ListWriter(OrcValueWriter<T> element) {
      this.element = element;
    }

    @Override
    public void nonNullWrite(int rowId, List<T> value, ColumnVector output) {
      ListColumnVector cv = (ListColumnVector) output;
      // record the length and start of the list elements
      cv.lengths[rowId] = value.size();
      cv.offsets[rowId] = cv.childCount;
      cv.childCount = (int) (cv.childCount + cv.lengths[rowId]);
      // make sure the child is big enough
      growColumnVector(cv.child, cv.childCount);
      // Add each element
      for (int e = 0; e < cv.lengths[rowId]; ++e) {
        element.write((int) (e + cv.offsets[rowId]), value.get(e), cv.child);
      }
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return element.metrics();
    }
  }

  /**
   * Map writer：把 map 拆为 keys/values 列表后分别写入。
   *
   * <p>逻辑：先展开 map 为两个 list，设置 lengths/offsets，扩容 keys/values 子向量， 再循环调用 keyWriter/valueWriter 写入。
   */
  private static class MapWriter<K, V> implements OrcValueWriter<Map<K, V>> {
    private final OrcValueWriter<K> keyWriter;
    private final OrcValueWriter<V> valueWriter;

    MapWriter(OrcValueWriter<K> keyWriter, OrcValueWriter<V> valueWriter) {
      this.keyWriter = keyWriter;
      this.valueWriter = valueWriter;
    }

    @Override
    public void nonNullWrite(int rowId, Map<K, V> map, ColumnVector output) {
      List<K> keys = Lists.newArrayListWithExpectedSize(map.size());
      List<V> values = Lists.newArrayListWithExpectedSize(map.size());
      for (Map.Entry<K, V> entry : map.entrySet()) {
        keys.add(entry.getKey());
        values.add(entry.getValue());
      }
      MapColumnVector cv = (MapColumnVector) output;
      // record the length and start of the list elements
      cv.lengths[rowId] = map.size();
      cv.offsets[rowId] = cv.childCount;
      cv.childCount = (int) (cv.childCount + cv.lengths[rowId]);
      // make sure the child is big enough
      growColumnVector(cv.keys, cv.childCount);
      growColumnVector(cv.values, cv.childCount);
      // Add each element
      for (int e = 0; e < cv.lengths[rowId]; ++e) {
        int pos = (int) (e + cv.offsets[rowId]);
        keyWriter.write(pos, keys.get(e), cv.keys);
        valueWriter.write(pos, values.get(e), cv.values);
      }
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return Stream.concat(keyWriter.metrics(), valueWriter.metrics());
    }
  }

  /**
   * Struct writer 抽象基类：按字段位置逐列写入。
   *
   * <p>设计意图：把“从行模型取字段值”抽象为 {@link #get(Object, int)} 供子类实现， 复用统一的列向量写入逻辑。writeRow() 专门用于写入根 struct
   * 到 VectorizedRowBatch。
   */
  public abstract static class StructWriter<S> implements OrcValueWriter<S> {
    private final List<OrcValueWriter<?>> writers;

    protected StructWriter(List<OrcValueWriter<?>> writers) {
      this.writers = writers;
    }

    public List<OrcValueWriter<?>> writers() {
      return writers;
    }

    @Override
    public Stream<FieldMetrics<?>> metrics() {
      return writers.stream().flatMap(OrcValueWriter::metrics);
    }

    @Override
    public void nonNullWrite(int rowId, S value, ColumnVector output) {
      StructColumnVector cv = (StructColumnVector) output;
      write(rowId, value, c -> cv.fields[c]);
    }

    // Special case of writing the root struct
    /**
     * 写入根 struct 行：取当前 batch 的 size 作为 rowId，递增 size 后逐列写入。
     *
     * <p>设计要点：与 nonNullWrite 的区别在于直接从 VectorizedRowBatch.cols 取列向量， 而非从 StructColumnVector.fields
     * 取。
     */
    public void writeRow(S value, VectorizedRowBatch output) {
      int rowId = output.size;
      output.size += 1;
      write(rowId, value, c -> output.cols[c]);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void write(int rowId, S value, Function<Integer, ColumnVector> colVectorAtFunc) {
      for (int c = 0; c < writers.size(); ++c) {
        OrcValueWriter writer = writers.get(c);
        writer.write(rowId, get(value, c), colVectorAtFunc.apply(c));
      }
    }

    protected abstract Object get(S struct, int index);
  }

  /**
   * 位置删除行 writer：把 PositionDelete 映射为 (path, pos, row?) 三列。
   *
   * <p>设计意图：复用被替换行的 writer.writers()；当 schema 包含 row 列（index=2）时 要求 row 不为 null。path 列通过
   * pathTransformFunc 转换。
   */
  private static class PositionDeleteStructWriter<T> extends StructWriter<PositionDelete<T>>
      implements OrcRowWriter<PositionDelete<T>> {
    private final Function<CharSequence, ?> pathTransformFunc;

    PositionDeleteStructWriter(
        OrcRowWriter<T> replacedWriter, Function<CharSequence, ?> pathTransformFunc) {
      super(replacedWriter.writers());
      this.pathTransformFunc = pathTransformFunc;
    }

    @Override
    protected Object get(PositionDelete<T> delete, int index) {
      switch (index) {
        case 0:
          return pathTransformFunc.apply(delete.path());
        case 1:
          return delete.pos();
        case 2:
          return delete.row();
      }
      throw new IllegalArgumentException("Cannot get value for invalid index: " + index);
    }

    @Override
    public void write(PositionDelete<T> row, VectorizedRowBatch output) throws IOException {
      Preconditions.checkArgument(row != null, "value must not be null");
      Preconditions.checkArgument(
          writers().size() == 2 || row.row() != null,
          "The row in PositionDelete must not be null because it was set row schema in position delete.");
      writeRow(row, output);
    }
  }

  /**
   * 按需扩容列向量。
   *
   * <p>设计意图：使用 3 倍增长因子避免频繁数组分配，减少 GC 压力。
   *
   * @param cv 待扩容的列向量
   * @param requestedSize 需要的最小容量
   */
  private static void growColumnVector(ColumnVector cv, int requestedSize) {
    if (cv.isNull.length < requestedSize) {
      // Use growth factor of 3 to avoid frequent array allocations
      cv.ensureSize(requestedSize * 3, true);
    }
  }
}
