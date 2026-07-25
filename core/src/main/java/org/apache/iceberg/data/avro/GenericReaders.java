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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.avro.io.Decoder;
import org.apache.iceberg.avro.ValueReader;
import org.apache.iceberg.avro.ValueReaders;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.DateTimeUtil;

/**
 * Avro 读取器工厂：为 Iceberg 逻辑类型提供基于 {@link GenericRecord} 的 {@link ValueReader} 实现。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的读取器构造工具。
 *
 * <p>职责：提供日期、时间、时间戳（带/不带时区）以及结构体（Record）的 ValueReader 实例， 供 {@link DataReader} 在按 Avro schema
 * 遍历时按逻辑类型选用。
 *
 * <p>设计意图：各读取器采用单例（INSTANCE）以减少对象创建开销；时间类型统一通过 {@link DateTimeUtil} 从 Avro 存储的微秒/天数转换为 Java 时间 API
 * 类型。
 *
 * <p>上下游关系：被 {@link DataReader.ReadBuilder} 调用以构建各字段的读取器。
 */
class GenericReaders {
  private GenericReaders() {}

  /** 返回日期读取器单例（Avro int 天数 -> {@link LocalDate}）。 */
  static ValueReader<LocalDate> dates() {
    return DateReader.INSTANCE;
  }

  /** 返回时间读取器单例（Avro long 微秒 -> {@link LocalTime}）。 */
  static ValueReader<LocalTime> times() {
    return TimeReader.INSTANCE;
  }

  /** 返回不带时区时间戳读取器单例（Avro long 微秒 -> {@link LocalDateTime}）。 */
  static ValueReader<LocalDateTime> timestamps() {
    return TimestampReader.INSTANCE;
  }

  /** 返回带时区时间戳读取器单例（Avro long 微秒 -> {@link OffsetDateTime}）。 */
  static ValueReader<OffsetDateTime> timestamptz() {
    return TimestamptzReader.INSTANCE;
  }

  /**
   * 构造结构体读取器，读取结果为 {@link GenericRecord}。
   *
   * @param struct 结构类型定义
   * @param readers 各字段的读取器列表
   * @param idToConstant 字段 ID 到常量值的映射（用于注入常量列）
   * @return 结构体读取器
   */
  static ValueReader<Record> struct(
      StructType struct, List<ValueReader<?>> readers, Map<Integer, ?> idToConstant) {
    return new GenericRecordReader(readers, struct, idToConstant);
  }

  /** 日期读取器：从 Avro int（自纪元的天数）读取并转换为 {@link LocalDate}。 */
  private static class DateReader implements ValueReader<LocalDate> {
    private static final DateReader INSTANCE = new DateReader();

    private DateReader() {}

    @Override
    public LocalDate read(Decoder decoder, Object reuse) throws IOException {
      return DateTimeUtil.dateFromDays(decoder.readInt());
    }
  }

  /** 时间读取器：从 Avro long（微秒）读取并转换为 {@link LocalTime}。 */
  private static class TimeReader implements ValueReader<LocalTime> {
    private static final TimeReader INSTANCE = new TimeReader();

    private TimeReader() {}

    @Override
    public LocalTime read(Decoder decoder, Object reuse) throws IOException {
      return DateTimeUtil.timeFromMicros(decoder.readLong());
    }
  }

  /** 时间戳读取器：从 Avro long（微秒）读取并转换为 {@link LocalDateTime}。 */
  private static class TimestampReader implements ValueReader<LocalDateTime> {
    private static final TimestampReader INSTANCE = new TimestampReader();

    private TimestampReader() {}

    @Override
    public LocalDateTime read(Decoder decoder, Object reuse) throws IOException {
      return DateTimeUtil.timestampFromMicros(decoder.readLong());
    }
  }

  /** 带时区时间戳读取器：从 Avro long（微秒）读取并转换为 {@link OffsetDateTime}。 */
  private static class TimestamptzReader implements ValueReader<OffsetDateTime> {
    private static final TimestamptzReader INSTANCE = new TimestamptzReader();

    private TimestamptzReader() {}

    @Override
    public OffsetDateTime read(Decoder decoder, Object reuse) throws IOException {
      return DateTimeUtil.timestamptzFromMicros(decoder.readLong());
    }
  }

  /**
   * 结构体读取器：将 Avro 记录读取为 {@link GenericRecord}。
   *
   * <p>设计意图：继承 {@link ValueReaders.StructReader} 复用字段级读取与常量注入逻辑， 仅覆盖复用/创建对象与 get/set 方法以适配 {@link
   * Record} 接口。
   */
  private static class GenericRecordReader extends ValueReaders.StructReader<Record> {
    private final StructType structType;

    private GenericRecordReader(
        List<ValueReader<?>> readers, StructType struct, Map<Integer, ?> idToConstant) {
      super(readers, struct, idToConstant);
      this.structType = struct;
    }

    /**
     * 复用传入对象或创建新的 GenericRecord。
     *
     * @param reuse 可复用的对象，若为 Record 则直接复用
     * @return 可用的 Record 实例
     */
    @Override
    protected Record reuseOrCreate(Object reuse) {
      if (reuse instanceof Record) {
        return (Record) reuse;
      } else {
        return GenericRecord.create(structType);
      }
    }

    @Override
    protected Object get(Record struct, int pos) {
      return struct.get(pos);
    }

    @Override
    protected void set(Record struct, int pos, Object value) {
      struct.set(pos, value);
    }
  }
}
