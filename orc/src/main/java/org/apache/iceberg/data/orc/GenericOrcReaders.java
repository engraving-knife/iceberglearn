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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.orc.OrcValueReader;
import org.apache.iceberg.orc.OrcValueReaders;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;
import org.apache.orc.storage.ql.exec.vector.DecimalColumnVector;
import org.apache.orc.storage.ql.exec.vector.ListColumnVector;
import org.apache.orc.storage.ql.exec.vector.LongColumnVector;
import org.apache.orc.storage.ql.exec.vector.MapColumnVector;
import org.apache.orc.storage.ql.exec.vector.TimestampColumnVector;

/**
 * 通用 ORC 字段读取器集合：为 Iceberg 各种类型提供从 ORC 列向量到 Java 对象的具体解码实现。
 *
 * <p>所属模块：iceberg-orc（data/orc 子包）。本类是 {@link GenericOrcReader} 的底层依赖， 把 ORC 的列向量逐类型转成 Iceberg 期望的
 * Java 值。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 struct/list/map 复合类型的 reader 工厂方法。
 *   <li>提供时间戳、时间、日期、Decimal、字符串、UUID、字节等基础类型的单例 reader。
 *   <li>对带时区时间戳、不带时区时间戳做语义区分（前者返回 OffsetDateTime，后者 LocalDateTime）。
 * </ul>
 *
 * <p>设计意图：基础类型 reader 全部以单例（INSTANCE）形式存在，避免重复构造； 复合类型（struct/list/map）由于携带子 reader，需要按 schema
 * 动态构造。StructReader 使用预创建的 {@link GenericRecord} 模板并调用 {@code copy()} 创建新行，省去 NAME_MAP_CACHE
 * 查询，提升读性能。
 *
 * <p>上下游关系：被 {@link GenericOrcReader.ReadBuilder} 调用；继承 {@link OrcValueReaders.StructReader} 复用
 * struct 行的统一构建逻辑；底层依赖 Iceberg 的 {@link DateTimeUtil}/{@link UUIDUtil} 完成数值到 Java 时间的转换。
 */
public class GenericOrcReaders {

  private GenericOrcReaders() {}

  /**
   * 创建 struct 类型 reader。
   *
   * @param readers 各字段子 reader
   * @param struct struct 类型定义（可能为 null，如根节点投影）
   * @param idToConstant 字段 id 到常量值的映射
   * @return struct reader 实例
   */
  public static OrcValueReader<Record> struct(
      List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
    return new StructReader(readers, struct, idToConstant);
  }

  /** 创建数组 reader，包装元素 reader。 */
  public static OrcValueReader<List<?>> array(OrcValueReader<?> elementReader) {
    return new ListReader(elementReader);
  }

  /** 创建 map reader，组合 key/value reader。 */
  public static OrcValueReader<Map<?, ?>> map(
      OrcValueReader<?> keyReader, OrcValueReader<?> valueReader) {
    return new MapReader(keyReader, valueReader);
  }

  /** 返回带时区时间戳 reader（ORC TIMESTAMP_INSTANT → {@link OffsetDateTime}）。 */
  public static OrcValueReader<OffsetDateTime> timestampTzs() {
    return TimestampTzReader.INSTANCE;
  }

  /** 返回 Decimal reader（ORC DECIMAL → {@link BigDecimal}）。 */
  public static OrcValueReader<BigDecimal> decimals() {
    return DecimalReader.INSTANCE;
  }

  /** 返回字符串 reader（ORC STRING/CHAR/VARCHAR → {@link String}）。 */
  public static OrcValueReader<String> strings() {
    return StringReader.INSTANCE;
  }

  /** 返回 UUID reader（ORC BINARY → {@link UUID}）。 */
  public static OrcValueReader<UUID> uuids() {
    return UUIDReader.INSTANCE;
  }

  /** 返回字节缓冲 reader（ORC BINARY → {@link ByteBuffer}）。 */
  public static OrcValueReader<ByteBuffer> bytes() {
    return BytesReader.INSTANCE;
  }

  /** 返回时间 reader（ORC LONG 存储微秒 → {@link LocalTime}）。 */
  public static OrcValueReader<LocalTime> times() {
    return TimeReader.INSTANCE;
  }

  /** 返回日期 reader（ORC DATE 存储距纪元天数 → {@link LocalDate}）。 */
  public static OrcValueReader<LocalDate> dates() {
    return DateReader.INSTANCE;
  }

  /** 返回无时区时间戳 reader（ORC TIMESTAMP → {@link LocalDateTime}）。 */
  public static OrcValueReader<LocalDateTime> timestamps() {
    return TimestampReader.INSTANCE;
  }

  /** 带时区时间戳 reader：将 ORC 的毫秒+纳秒还原为 UTC 偏移的 {@link OffsetDateTime}。 */
  private static class TimestampTzReader implements OrcValueReader<OffsetDateTime> {
    public static final OrcValueReader<OffsetDateTime> INSTANCE = new TimestampTzReader();

    private TimestampTzReader() {}

    /**
     * 读取非空时间戳值。
     *
     * <p>逻辑：ORC 的 {@code time} 字段为毫秒，{@code nanos} 为纳秒部分； 用 {@link Math#floorDiv(long, long)}
     * 取整秒以兼容负值，再附加纳秒得到 Instant， 最后转 UTC 偏移返回。
     */
    @Override
    public OffsetDateTime nonNullRead(ColumnVector vector, int row) {
      TimestampColumnVector tcv = (TimestampColumnVector) vector;
      return Instant.ofEpochSecond(Math.floorDiv(tcv.time[row], 1_000), tcv.nanos[row])
          .atOffset(ZoneOffset.UTC);
    }
  }

  /** 时间 reader：ORC LONG 列中存储微秒值，通过 {@link DateTimeUtil} 转为 {@link LocalTime}。 */
  private static class TimeReader implements OrcValueReader<LocalTime> {
    public static final OrcValueReader<LocalTime> INSTANCE = new TimeReader();

    private TimeReader() {}

    @Override
    public LocalTime nonNullRead(ColumnVector vector, int row) {
      return DateTimeUtil.timeFromMicros(((LongColumnVector) vector).vector[row]);
    }
  }

  /** 日期 reader：ORC DATE 列存距纪元的天数，转为 {@link LocalDate}。 */
  private static class DateReader implements OrcValueReader<LocalDate> {
    public static final OrcValueReader<LocalDate> INSTANCE = new DateReader();

    private DateReader() {}

    @Override
    public LocalDate nonNullRead(ColumnVector vector, int row) {
      return DateTimeUtil.dateFromDays((int) ((LongColumnVector) vector).vector[row]);
    }
  }

  /**
   * 无时区时间戳 reader：把 ORC 毫秒+纳秒解析为 UTC 时刻后转 {@link LocalDateTime}。
   *
   * <p>设计意图：Iceberg 的无时区 TIMESTAMP 内部以 UTC 存储，读取时通过 atOffset(UTC).toLocalDateTime()
   * 还原为本地时间表示，保持与写入端一致。
   */
  private static class TimestampReader implements OrcValueReader<LocalDateTime> {
    public static final OrcValueReader<LocalDateTime> INSTANCE = new TimestampReader();

    private TimestampReader() {}

    @Override
    public LocalDateTime nonNullRead(ColumnVector vector, int row) {
      TimestampColumnVector tcv = (TimestampColumnVector) vector;
      return Instant.ofEpochSecond(Math.floorDiv(tcv.time[row], 1_000), tcv.nanos[row])
          .atOffset(ZoneOffset.UTC)
          .toLocalDateTime();
    }
  }

  /** Decimal reader：从 ORC 的 {@link DecimalColumnVector} 取 HiveDecimal 并还原 scale。 */
  private static class DecimalReader implements OrcValueReader<BigDecimal> {
    public static final OrcValueReader<BigDecimal> INSTANCE = new DecimalReader();

    private DecimalReader() {}

    @Override
    public BigDecimal nonNullRead(ColumnVector vector, int row) {
      DecimalColumnVector cv = (DecimalColumnVector) vector;
      return cv.vector[row].getHiveDecimal().bigDecimalValue().setScale(cv.scale);
    }
  }

  /** 字符串 reader：直接从 BytesColumnVector 按 UTF-8 解码。 */
  private static class StringReader implements OrcValueReader<String> {
    public static final OrcValueReader<String> INSTANCE = new StringReader();

    private StringReader() {}

    @Override
    public String nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      return new String(
          bytesVector.vector[row],
          bytesVector.start[row],
          bytesVector.length[row],
          StandardCharsets.UTF_8);
    }
  }

  /** UUID reader：从字节切片构造 {@link ByteBuffer} 后由 {@link UUIDUtil} 还原为 UUID。 */
  private static class UUIDReader implements OrcValueReader<UUID> {
    public static final OrcValueReader<UUID> INSTANCE = new UUIDReader();

    private UUIDReader() {}

    @Override
    public UUID nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      ByteBuffer buf =
          ByteBuffer.wrap(bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
      return UUIDUtil.convert(buf);
    }
  }

  /** 字节 reader：把 ORC BytesColumnVector 切片包装为 {@link ByteBuffer}。 */
  private static class BytesReader implements OrcValueReader<ByteBuffer> {
    public static final OrcValueReader<ByteBuffer> INSTANCE = new BytesReader();

    private BytesReader() {}

    @Override
    public ByteBuffer nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;
      return ByteBuffer.wrap(
          bytesVector.vector[row], bytesVector.start[row], bytesVector.length[row]);
    }
  }

  /**
   * Struct reader：按字段位置调用各子 reader，组装为 {@link GenericRecord}。
   *
   * <p>设计意图：预创建模板并复用 {@code copy()} 创建每行实例，省去 GenericRecord.create() 的 NAME_MAP_CACHE 查询开销，提高读性能。
   */
  private static class StructReader extends OrcValueReaders.StructReader<Record> {
    private final GenericRecord template;

    protected StructReader(
        List<OrcValueReader<?>> readers,
        Types.StructType structType,
        Map<Integer, ?> idToConstant) {
      super(readers, structType, idToConstant);
      this.template = structType != null ? GenericRecord.create(structType) : null;
    }

    /** 创建新的行实例：复用模板 copy 提升性能。 */
    @Override
    protected Record create() {
      // GenericRecord.copy() is more performant then GenericRecord.create(StructType) since
      // NAME_MAP_CACHE access
      // is eliminated. Using copy here to gain performance.
      return template.copy();
    }

    @Override
    protected void set(Record struct, int pos, Object value) {
      struct.set(pos, value);
    }
  }

  /**
   * Map reader：从 ORC MapColumnVector 的偏移与长度区间内逐项读取 key/value 组装 HashMap。
   *
   * <p>设计要点：将 batch 上下文透传到 key/value reader，保证嵌套 reader 也能拿到文件位置。
   */
  private static class MapReader implements OrcValueReader<Map<?, ?>> {
    private final OrcValueReader<?> keyReader;
    private final OrcValueReader<?> valueReader;

    private MapReader(OrcValueReader<?> keyReader, OrcValueReader<?> valueReader) {
      this.keyReader = keyReader;
      this.valueReader = valueReader;
    }

    /**
     * 读取非空 map。
     *
     * <p>逻辑：从 MapColumnVector 取出该行对应的 offset 与 length，按 length 依次调用 keyReader/valueReader 读取
     * keys/values 子向量对应位置，组装为 HashMap 返回。
     */
    @Override
    public Map<?, ?> nonNullRead(ColumnVector vector, int row) {
      MapColumnVector mapVector = (MapColumnVector) vector;
      int offset = (int) mapVector.offsets[row];
      long length = mapVector.lengths[row];
      Map<Object, Object> map = Maps.newHashMapWithExpectedSize((int) length);
      for (int c = 0; c < length; c++) {
        map.put(
            keyReader.read(mapVector.keys, offset + c),
            valueReader.read(mapVector.values, offset + c));
      }
      return map;
    }

    @Override
    public void setBatchContext(long batchOffsetInFile) {
      keyReader.setBatchContext(batchOffsetInFile);
      valueReader.setBatchContext(batchOffsetInFile);
    }
  }

  /**
   * List reader：从 ORC ListColumnVector 的偏移与长度区间逐元素读取，组装为 List。
   *
   * <p>设计要点：透传 batch 上下文到元素 reader。
   */
  private static class ListReader implements OrcValueReader<List<?>> {
    private final OrcValueReader<?> elementReader;

    private ListReader(OrcValueReader<?> elementReader) {
      this.elementReader = elementReader;
    }

    /**
     * 读取非空 list。
     *
     * <p>逻辑：取出该行 offset 与 length，按长度循环调用 elementReader 读取 child 向量， 累积为 ArrayList 返回。
     */
    @Override
    public List<?> nonNullRead(ColumnVector vector, int row) {
      ListColumnVector listVector = (ListColumnVector) vector;
      int offset = (int) listVector.offsets[row];
      int length = (int) listVector.lengths[row];
      List<Object> elements = Lists.newArrayListWithExpectedSize(length);
      for (int c = 0; c < length; ++c) {
        elements.add(elementReader.read(listVector.child, offset + c));
      }
      return elements;
    }

    @Override
    public void setBatchContext(long batchOffsetInFile) {
      elementReader.setBatchContext(batchOffsetInFile);
    }
  }
}
