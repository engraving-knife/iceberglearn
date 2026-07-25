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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.apache.avro.io.Encoder;
import org.apache.iceberg.avro.ValueWriter;
import org.apache.iceberg.avro.ValueWriters;
import org.apache.iceberg.data.Record;

/**
 * Avro 写入器工厂：为 Iceberg 逻辑类型提供基于 {@link Record} 的 {@link ValueWriter} 实现。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的写入器构造工具。
 *
 * <p>职责：提供日期、时间、时间戳（带/不带时区）以及结构体（Record）的 ValueWriter 实例， 供 {@link DataWriter} 在按 Avro schema
 * 遍历时按逻辑类型选用。
 *
 * <p>设计意图：各写入器采用单例（INSTANCE）以减少对象创建开销；时间类型统一通过 {@link ChronoUnit} 计算与纪元（EPOCH）的差值，转为 Avro 存储的微秒/天数。
 *
 * <p>上下游关系：被 {@link DataWriter.WriteBuilder} 调用以构建各字段的写入器。
 */
class GenericWriters {
  private GenericWriters() {}

  /** 返回日期写入器单例（{@link LocalDate} -> Avro int 天数）。 */
  static ValueWriter<LocalDate> dates() {
    return DateWriter.INSTANCE;
  }

  /** 返回时间写入器单例（{@link LocalTime} -> Avro long 微秒）。 */
  static ValueWriter<LocalTime> times() {
    return TimeWriter.INSTANCE;
  }

  /** 返回不带时区时间戳写入器单例（{@link LocalDateTime} -> Avro long 微秒）。 */
  static ValueWriter<LocalDateTime> timestamps() {
    return TimestampWriter.INSTANCE;
  }

  /** 返回带时区时间戳写入器单例（{@link OffsetDateTime} -> Avro long 微秒）。 */
  static ValueWriter<OffsetDateTime> timestamptz() {
    return TimestamptzWriter.INSTANCE;
  }

  /**
   * 构造结构体写入器，将 {@link Record} 写入 Avro 记录。
   *
   * @param writers 各字段的写入器列表
   * @return 结构体写入器
   */
  static ValueWriter<Record> struct(List<ValueWriter<?>> writers) {
    return new GenericRecordWriter(writers);
  }

  private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  private static final LocalDate EPOCH_DAY = EPOCH.toLocalDate();

  /** 日期写入器：将 {@link LocalDate} 转为自纪元的天数写入 Avro int。 */
  private static class DateWriter implements ValueWriter<LocalDate> {
    private static final DateWriter INSTANCE = new DateWriter();

    private DateWriter() {}

    @Override
    public void write(LocalDate date, Encoder encoder) throws IOException {
      encoder.writeInt((int) ChronoUnit.DAYS.between(EPOCH_DAY, date));
    }
  }

  /** 时间写入器：将 {@link LocalTime} 转为微秒写入 Avro long。 */
  private static class TimeWriter implements ValueWriter<LocalTime> {
    private static final TimeWriter INSTANCE = new TimeWriter();

    private TimeWriter() {}

    @Override
    public void write(LocalTime time, Encoder encoder) throws IOException {
      encoder.writeLong(time.toNanoOfDay() / 1000);
    }
  }

  /** 时间戳写入器：将 {@link LocalDateTime}（按 UTC 解释）转为微秒写入 Avro long。 */
  private static class TimestampWriter implements ValueWriter<LocalDateTime> {
    private static final TimestampWriter INSTANCE = new TimestampWriter();

    private TimestampWriter() {}

    @Override
    public void write(LocalDateTime timestamp, Encoder encoder) throws IOException {
      encoder.writeLong(ChronoUnit.MICROS.between(EPOCH, timestamp.atOffset(ZoneOffset.UTC)));
    }
  }

  /** 带时区时间戳写入器：将 {@link OffsetDateTime} 转为微秒写入 Avro long。 */
  private static class TimestamptzWriter implements ValueWriter<OffsetDateTime> {
    private static final TimestamptzWriter INSTANCE = new TimestamptzWriter();

    private TimestamptzWriter() {}

    @Override
    public void write(OffsetDateTime timestamptz, Encoder encoder) throws IOException {
      encoder.writeLong(ChronoUnit.MICROS.between(EPOCH, timestamptz));
    }
  }

  /**
   * 结构体写入器：将 {@link Record} 的字段值写入 Avro 记录。
   *
   * <p>设计意图：继承 {@link ValueWriters.StructWriter} 复用字段遍历逻辑， 仅覆盖 get 方法以从 Record 取字段值。
   */
  private static class GenericRecordWriter extends ValueWriters.StructWriter<Record> {
    private GenericRecordWriter(List<ValueWriter<?>> writers) {
      super(writers);
    }

    @Override
    protected Object get(Record struct, int pos) {
      return struct.get(pos);
    }
  }
}
