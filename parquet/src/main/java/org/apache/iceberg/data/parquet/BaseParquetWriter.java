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
package org.apache.iceberg.data.parquet;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.apache.iceberg.parquet.ParquetTypeVisitor;
import org.apache.iceberg.parquet.ParquetValueWriter;
import org.apache.iceberg.parquet.ParquetValueWriters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：Parquet 写入器的抽象基类，负责按 Parquet Schema 构造对应的值写入器树。
 *
 * <p>所属模块：iceberg-parquet（Parquet 列式读写底座，向 data 模块的 Record/RecordWriter 提供具体写入实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历 Parquet {@link MessageType}，递归构造 struct/list/map/primitive 各层 {@link
 *       ParquetValueWriter}，输出统一的写入器根节点。
 *   <li>处理 Parquet 的定义级别（definition level）与重复级别（repetition level）， 用 {@link
 *       ParquetValueWriters#option} 包装可选字段，正确编码 null。
 *   <li>针对 LogicalTypeAnnotation（字符串、Decimal、Date、Time、Timestamp 等）选择 合适的 Java 类型到 Parquet
 *       物理类型的转换器。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>访问者模式：复用 {@link ParquetTypeVisitor} 遍历逻辑，把 schema 走树与具体 writer 构造解耦，新增类型只改 visitor 方法。
 *   <li>泛型抽象：T 代表上层记录类型（如 Record），子类通过 {@link #createStructWriter(List)} 提供字段取数方式，使本类可复用于多种记录模型。
 *   <li>时间戳统一 MICROS：Iceberg 规范要求时间戳以微秒存储，故对 Timestamp logical type 强制校验单位。
 * </ul>
 *
 * <p>上下游关系：依赖 parquet schema 与 {@link ParquetValueWriters} 工厂；被 {@code
 * GenericParquetWriter}、Spark/Flink 等子类继承以构造具体 Record 的写入器。
 */
public abstract class BaseParquetWriter<T> {

  /**
   * 根据 Parquet 消息类型构造写入器根节点。
   *
   * <p>逻辑：以 {@link WriteBuilder} 访问者遍历 {@code type}，递归为 struct/list/map/primitive 构造对应 {@link
   * ParquetValueWriter}，最终返回根 struct 的写入器。
   *
   * @param type Parquet 文件的消息类型（root schema）
   * @return 适配泛型 T 的写入器根节点
   */
  @SuppressWarnings("unchecked")
  protected ParquetValueWriter<T> createWriter(MessageType type) {
    return (ParquetValueWriter<T>) ParquetTypeVisitor.visit(type, new WriteBuilder(type));
  }

  /**
   * 子类提供 struct 写入器，定义如何从上层记录对象按字段索引取值。
   *
   * @param writers struct 各字段子写入器列表
   * @return 适配泛型 T 的 struct 写入器
   */
  protected abstract ParquetValueWriters.StructWriter<T> createStructWriter(
      List<ParquetValueWriter<?>> writers);

  /**
   * 内部访问者：在遍历 Parquet schema 过程中按节点类型构造对应写入器。
   *
   * <p>设计意图：持有当前 {@link MessageType}，便于在构造每个字段写入器时查询其 最大定义级别/最大重复级别，确保 null 与重复元素的编码符合 Parquet 规范。
   */
  private class WriteBuilder extends ParquetTypeVisitor<ParquetValueWriter<?>> {
    private final MessageType type;

    private WriteBuilder(MessageType type) {
      this.type = type;
    }

    /**
     * 处理顶层 message 节点，等价于 struct。
     *
     * @param message Parquet 消息类型
     * @param fieldWriters 各字段子写入器
     * @return struct 写入器
     */
    @Override
    public ParquetValueWriter<?> message(
        MessageType message, List<ParquetValueWriter<?>> fieldWriters) {

      return struct(message.asGroupType(), fieldWriters);
    }

    /**
     * 构造 struct 写入器，逐字段计算定义级别并用 {@code option} 包装可选字段。
     *
     * <p>逻辑：遍历字段，依据字段名查询其在 schema 路径上的最大定义级别， 通过 {@link ParquetValueWriters#option} 包装以处理字段为 null
     * 的情形， 最后委托 {@link #createStructWriter(List)} 生成具体 struct writer。
     *
     * @param struct struct group 类型
     * @param fieldWriters 各字段子写入器
     * @return struct 写入器
     */
    @Override
    public ParquetValueWriter<?> struct(
        GroupType struct, List<ParquetValueWriter<?>> fieldWriters) {
      List<Type> fields = struct.getFields();
      List<ParquetValueWriter<?>> writers = Lists.newArrayListWithExpectedSize(fieldWriters.size());
      for (int i = 0; i < fields.size(); i += 1) {
        Type fieldType = struct.getType(i);
        int fieldD = type.getMaxDefinitionLevel(path(fieldType.getName()));
        writers.add(ParquetValueWriters.option(fieldType, fieldD, fieldWriters.get(i)));
      }

      return createStructWriter(writers);
    }

    /**
     * 构造 list（数组）写入器。
     *
     * <p>逻辑：定位到重复元素所在 group，分别计算 list 重复层级与元素本身的 定义级别/重复级别，使用 {@link
     * ParquetValueWriters#collections} 包装元素写入器。
     *
     * @param array list group 类型
     * @param elementWriter 元素写入器
     * @return list 写入器
     */
    @Override
    public ParquetValueWriter<?> list(GroupType array, ParquetValueWriter<?> elementWriter) {
      GroupType repeated = array.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath);
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath);

      Type elementType = repeated.getType(0);
      int elementD = type.getMaxDefinitionLevel(path(elementType.getName()));

      return ParquetValueWriters.collections(
          repeatedD, repeatedR, ParquetValueWriters.option(elementType, elementD, elementWriter));
    }

    /**
     * 构造 map 写入器。
     *
     * <p>逻辑：定位到 map 的 key-value 重复 group，分别计算 key、value 各自的定义级别， 调用 {@link
     * ParquetValueWriters#maps} 组合 key/value 写入器。
     *
     * @param map map group 类型
     * @param keyWriter key 写入器
     * @param valueWriter value 写入器
     * @return map 写入器
     */
    @Override
    public ParquetValueWriter<?> map(
        GroupType map, ParquetValueWriter<?> keyWriter, ParquetValueWriter<?> valueWriter) {
      GroupType repeatedKeyValue = map.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath);
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath);

      Type keyType = repeatedKeyValue.getType(0);
      int keyD = type.getMaxDefinitionLevel(path(keyType.getName()));
      Type valueType = repeatedKeyValue.getType(1);
      int valueD = type.getMaxDefinitionLevel(path(valueType.getName()));

      return ParquetValueWriters.maps(
          repeatedD,
          repeatedR,
          ParquetValueWriters.option(keyType, keyD, keyWriter),
          ParquetValueWriters.option(valueType, valueD, valueWriter));
    }

    /**
     * 构造原始类型写入器，优先按 LogicalTypeAnnotation 选择语义化写入器。
     *
     * <p>逻辑：先获取当前路径对应的 {@link ColumnDescriptor}；若存在 logical type， 委托 {@link
     * LogicalTypeWriterVisitor} 选择专用 writer（如 String/Decimal/Date 等）， 命中则直接返回；否则按物理类型 fallback 到
     * int/long/float/double/boolean/binary 等 通用 writer。
     *
     * @param primitive Parquet 原始类型节点
     * @return 原始类型写入器
     * @throws UnsupportedOperationException 若物理类型不被支持
     */
    @Override
    public ParquetValueWriter<?> primitive(PrimitiveType primitive) {
      ColumnDescriptor desc = type.getColumnDescription(currentPath());
      LogicalTypeAnnotation logicalType = primitive.getLogicalTypeAnnotation();
      if (logicalType != null) {
        Optional<ParquetValueWriters.PrimitiveWriter<?>> writer =
            logicalType.accept(new LogicalTypeWriterVisitor(desc));
        if (writer.isPresent()) {
          return writer.get();
        }
      }

      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
          return new FixedWriter(desc);
        case BINARY:
          return ParquetValueWriters.byteBuffers(desc);
        case BOOLEAN:
          return ParquetValueWriters.booleans(desc);
        case INT32:
          return ParquetValueWriters.ints(desc);
        case INT64:
          return ParquetValueWriters.longs(desc);
        case FLOAT:
          return ParquetValueWriters.floats(desc);
        case DOUBLE:
          return ParquetValueWriters.doubles(desc);
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }
  }

  /**
   * LogicalTypeAnnotation 访问者：按 Parquet logical type 选择对应语义化原始类型写入器。
   *
   * <p>设计意图：把“物理类型 + logical type 注解”到“Java 类型写入器”的映射集中在此， 便于扩展新的 logical type；每个 visit 方法返回
   * Optional.empty 表示该 logical type 无专用 writer，调用方将退回按物理类型选择通用 writer。
   */
  private static class LogicalTypeWriterVisitor
      implements LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<
          ParquetValueWriters.PrimitiveWriter<?>> {
    private final ColumnDescriptor desc;

    private LogicalTypeWriterVisitor(ColumnDescriptor desc) {
      this.desc = desc;
    }

    /** String logical type：以 UTF-8 字符串写入 BINARY 列。 */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.StringLogicalTypeAnnotation stringType) {
      return Optional.of(ParquetValueWriters.strings(desc));
    }

    /** Enum logical type：底层按字符串写入。 */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.EnumLogicalTypeAnnotation enumType) {
      return Optional.of(ParquetValueWriters.strings(desc));
    }

    /**
     * Decimal logical type：根据底层物理类型（INT32/INT64/BINARY/FIXED_LEN_BYTE_ARRAY） 选择对应精度的 Decimal 写入器。
     *
     * @param decimalType 包含 precision 与 scale 的注解
     * @return 对应 decimal 写入器；若物理类型不匹配则返回 empty
     */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.DecimalLogicalTypeAnnotation decimalType) {
      switch (desc.getPrimitiveType().getPrimitiveTypeName()) {
        case INT32:
          return Optional.of(
              ParquetValueWriters.decimalAsInteger(
                  desc, decimalType.getPrecision(), decimalType.getScale()));
        case INT64:
          return Optional.of(
              ParquetValueWriters.decimalAsLong(
                  desc, decimalType.getPrecision(), decimalType.getScale()));
        case BINARY:
        case FIXED_LEN_BYTE_ARRAY:
          return Optional.of(
              ParquetValueWriters.decimalAsFixed(
                  desc, decimalType.getPrecision(), decimalType.getScale()));
      }
      return Optional.empty();
    }

    /** Date logical type：以 INT32 存储距 epoch 的天数。 */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.DateLogicalTypeAnnotation dateType) {
      return Optional.of(new DateWriter(desc));
    }

    /** Time logical type：以 INT64 存储一天内微秒数。 */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.TimeLogicalTypeAnnotation timeType) {
      return Optional.of(new TimeWriter(desc));
    }

    /**
     * Timestamp logical type：强制要求 MICROS 单位，否则抛出异常。
     *
     * <p>逻辑：校验时间单位为 MICROS；根据 isAdjustedToUTC 选择带时区 （{@link TimestamptzWriter}）或不带时区（{@link
     * TimestampWriter}）写入器。
     *
     * @throws IllegalArgumentException 时间单位非 MICROS
     */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.TimestampLogicalTypeAnnotation timestampType) {
      Preconditions.checkArgument(
          LogicalTypeAnnotation.TimeUnit.MICROS.equals(timestampType.getUnit()),
          "Cannot write timestamp in %s, only MICROS is supported",
          timestampType.getUnit());
      if (timestampType.isAdjustedToUTC()) {
        return Optional.of(new TimestamptzWriter(desc));
      } else {
        return Optional.of(new TimestampWriter(desc));
      }
    }

    /**
     * Int logical type：依据位宽选择 INT32 或 INT64。
     *
     * <p>逻辑：禁止 uint64（Java 无对应类型）；位宽小于 64 时按 INT32 写入， 否则按 INT64 写入。
     *
     * @throws IllegalArgumentException 出现无符号 64 位整数
     */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.IntLogicalTypeAnnotation intType) {
      Preconditions.checkArgument(
          intType.isSigned() || intType.getBitWidth() < 64,
          "Cannot read uint64: not a supported Java type");
      if (intType.getBitWidth() < 64) {
        return Optional.of(ParquetValueWriters.ints(desc));
      } else {
        return Optional.of(ParquetValueWriters.longs(desc));
      }
    }

    /** JSON logical type：按字符串写入。 */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.JsonLogicalTypeAnnotation jsonLogicalType) {
      return Optional.of(ParquetValueWriters.strings(desc));
    }

    /** BSON logical type：按字节缓冲写入。 */
    @Override
    public Optional<ParquetValueWriters.PrimitiveWriter<?>> visit(
        LogicalTypeAnnotation.BsonLogicalTypeAnnotation bsonType) {
      return Optional.of(ParquetValueWriters.byteBuffers(desc));
    }
  }

  // UTC 时区的 epoch 时刻与日期，作为 Date/Time/Timestamp 写入的基准点
  private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  private static final LocalDate EPOCH_DAY = EPOCH.toLocalDate();

  /** 将 {@link LocalDate} 写为 INT32 的距 epoch 天数。 */
  private static class DateWriter extends ParquetValueWriters.PrimitiveWriter<LocalDate> {
    private DateWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, LocalDate value) {
      column.writeInteger(repetitionLevel, (int) ChronoUnit.DAYS.between(EPOCH_DAY, value));
    }
  }

  /** 将 {@link LocalTime} 写为 INT64 的一天内微秒数。 */
  private static class TimeWriter extends ParquetValueWriters.PrimitiveWriter<LocalTime> {
    private TimeWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, LocalTime value) {
      column.writeLong(repetitionLevel, value.toNanoOfDay() / 1000);
    }
  }

  /** 将无时区 {@link LocalDateTime} 写为 INT64 的距 epoch 微秒数（按 UTC 解释）。 */
  private static class TimestampWriter extends ParquetValueWriters.PrimitiveWriter<LocalDateTime> {
    private TimestampWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, LocalDateTime value) {
      column.writeLong(
          repetitionLevel, ChronoUnit.MICROS.between(EPOCH, value.atOffset(ZoneOffset.UTC)));
    }
  }

  /** 将带时区 {@link OffsetDateTime} 写为 INT64 的距 epoch 微秒数。 */
  private static class TimestamptzWriter
      extends ParquetValueWriters.PrimitiveWriter<OffsetDateTime> {
    private TimestamptzWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, OffsetDateTime value) {
      column.writeLong(repetitionLevel, ChronoUnit.MICROS.between(EPOCH, value));
    }
  }

  /** 将固定长度字节数组写为 Parquet BINARY（FIXED_LEN_BYTE_ARRAY 物理类型）。 */
  private static class FixedWriter extends ParquetValueWriters.PrimitiveWriter<byte[]> {
    private FixedWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, byte[] value) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(value));
    }
  }
}
