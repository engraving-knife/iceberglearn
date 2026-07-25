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
package org.apache.iceberg.expressions;

import java.io.ObjectStreamException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.io.BaseEncoding;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.NaNUtil;

/**
 * 字面量实现集合：为 Iceberg 各数据类型提供 {@link Literal} 的具体实现与工厂方法。
 *
 * <p>所属模块：iceberg-api（表达式体系“值/字面量”分支的实现汇总，包级可见，对外通过 {@link Literal} 接口与 {@link Expressions} 工厂使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #from(Object)}：按 Java 类型分发，构造对应类型的字面量。
 *   <li>提供各类字面量实现（Boolean/Integer/Long/Float/Double/Date/Time/Timestamp/Decimal/
 *       String/UUID/Fixed/Binary），每个实现支持 {@code to(Type)} 向目标类型转换， 并提供比较器与二进制序列化。
 *   <li>提供 {@link AboveMax}/{@link BelowMin} 哨兵字面量，表示值越界（用于绑定时常量折叠）。
 * </ul>
 *
 * <p>设计意图：每个字面量是不可变对象，{@code to(Type)} 把“类型转换 + 越界检测”集中在此处， 使 {@link UnboundPredicate#bind}
 * 能在绑定阶段做常量折叠（如 Long 转 Int 越界返回 aboveMax）。 {@link BaseLiteral} 用双重检查锁缓存 {@code toByteBuffer}
 * 结果，避免重复序列化。 二进制字面量通过 {@link SerializationProxies} 做序列化替换，保证 ByteBuffer 的可序列化。
 *
 * <p>上下游关系：由 {@link Expressions} 工厂与 {@link UnboundPredicate} 构造； 在 {@link UnboundPredicate#bind}
 * 中经 {@code to(Type)} 转换为目标类型； 被各求值器（{@link Evaluator} 等）通过 {@link Literal#comparator()} 与 {@link
 * Literal#value()} 使用。
 */
class Literals {
  private Literals() {}

  private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  private static final LocalDate EPOCH_DAY = EPOCH.toLocalDate();

  /**
   * 由任意对象构造对应类型的字面量。
   *
   * <p>逻辑：拒绝 null 与 NaN；按 Java 类型分发到对应字面量实现
   * （Boolean/Integer/Long/Float/Double/CharSequence/UUID/byte[]/ByteBuffer/BigDecimal）； 其余类型抛
   * {@link IllegalArgumentException}。
   *
   * @param value 值
   * @param <T> 值的 Java 类型
   * @return 对应字面量
   */
  @SuppressWarnings("unchecked")
  static <T> Literal<T> from(T value) {
    Preconditions.checkNotNull(value, "Cannot create expression literal from null");
    Preconditions.checkArgument(!NaNUtil.isNaN(value), "Cannot create expression literal from NaN");

    if (value instanceof Boolean) {
      return (Literal<T>) new Literals.BooleanLiteral((Boolean) value);
    } else if (value instanceof Integer) {
      return (Literal<T>) new Literals.IntegerLiteral((Integer) value);
    } else if (value instanceof Long) {
      return (Literal<T>) new Literals.LongLiteral((Long) value);
    } else if (value instanceof Float) {
      return (Literal<T>) new Literals.FloatLiteral((Float) value);
    } else if (value instanceof Double) {
      return (Literal<T>) new Literals.DoubleLiteral((Double) value);
    } else if (value instanceof CharSequence) {
      return (Literal<T>) new Literals.StringLiteral((CharSequence) value);
    } else if (value instanceof UUID) {
      return (Literal<T>) new Literals.UUIDLiteral((UUID) value);
    } else if (value instanceof byte[]) {
      return (Literal<T>) new Literals.FixedLiteral(ByteBuffer.wrap((byte[]) value));
    } else if (value instanceof ByteBuffer) {
      return (Literal<T>) new Literals.BinaryLiteral((ByteBuffer) value);
    } else if (value instanceof BigDecimal) {
      return (Literal<T>) new Literals.DecimalLiteral((BigDecimal) value);
    }

    throw new IllegalArgumentException(
        String.format(
            "Cannot create expression literal from %s: %s", value.getClass().getName(), value));
  }

  /** 返回“超过最大值”哨兵字面量单例（用于绑定时常量折叠）。 */
  @SuppressWarnings("unchecked")
  static <T> AboveMax<T> aboveMax() {
    return AboveMax.INSTANCE;
  }

  /** 返回“低于最小值”哨兵字面量单例（用于绑定时常量折叠）。 */
  @SuppressWarnings("unchecked")
  static <T> BelowMin<T> belowMin() {
    return BelowMin.INSTANCE;
  }

  /**
   * 字面量抽象基类：持有值并缓存其二进制表示。
   *
   * <p>设计意图：{@code byteBuffer} 用 transient volatile + 双重检查锁惰性初始化， 避免每次调用都序列化；equals/hashCode
   * 基于比较器而非原始 equals，保证 CharSequence 等 类型按值相等。
   */
  private abstract static class BaseLiteral<T> implements Literal<T> {
    private final T value;
    private transient volatile ByteBuffer byteBuffer = null;

    BaseLiteral(T value) {
      Preconditions.checkNotNull(value, "Literal values cannot be null");
      this.value = value;
    }

    /** 返回字面量值。 */
    @Override
    public T value() {
      return value;
    }

    /**
     * 返回值的二进制表示（带双重检查锁的惰性缓存）。
     *
     * <p>逻辑：首次调用时按 {@link #typeId()} 与值经 {@link Conversions#toByteBuffer} 序列化并缓存。
     *
     * @return 值的 ByteBuffer 表示
     */
    @Override
    public final ByteBuffer toByteBuffer() {
      if (byteBuffer == null) {
        synchronized (this) {
          if (byteBuffer == null) {
            byteBuffer = Conversions.toByteBuffer(typeId(), value());
          }
        }
      }
      return byteBuffer;
    }

    /** 子类提供字面量的类型 id，用于二进制序列化。 */
    protected abstract Type.TypeID typeId();

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      BaseLiteral<T> that = (BaseLiteral<T>) other;

      return comparator().compare(value(), that.value()) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }
  }

  private abstract static class ComparableLiteral<C extends Comparable<C>> extends BaseLiteral<C> {
    @SuppressWarnings("unchecked")
    private static final Comparator<? extends Comparable> CMP =
        Comparators.<Comparable>nullsFirst().thenComparing(Comparator.naturalOrder());

    ComparableLiteral(C value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Comparator<C> comparator() {
      return (Comparator<C>) CMP;
    }
  }

  /**
   * “超过最大值”哨兵字面量：表示在类型转换时值超过目标类型上界。
   *
   * <p>设计意图：作为空对象，使 {@link UnboundPredicate#bind} 能据此做常量折叠 （如 {@code col > aboveMax} 恒假、{@code col
   * < aboveMax} 恒真），无需特殊 null 处理。
   */
  static class AboveMax<T> implements Literal<T> {
    private static final AboveMax INSTANCE = new AboveMax();

    private AboveMax() {}

    @Override
    public T value() {
      throw new UnsupportedOperationException("AboveMax has no value");
    }

    @Override
    public <X> Literal<X> to(Type type) {
      throw new UnsupportedOperationException("Cannot change the type of AboveMax");
    }

    @Override
    public Comparator<T> comparator() {
      throw new UnsupportedOperationException("AboveMax has no comparator");
    }

    @Override
    public String toString() {
      return "aboveMax";
    }
  }

  /**
   * “低于最小值”哨兵字面量：表示在类型转换时值低于目标类型下界。
   *
   * <p>设计意图：与 {@link AboveMax} 对称，用于绑定时常量折叠 （如 {@code col < belowMin} 恒假、{@code col > belowMin}
   * 恒真）。
   */
  static class BelowMin<T> implements Literal<T> {
    private static final BelowMin INSTANCE = new BelowMin();

    private BelowMin() {}

    @Override
    public T value() {
      throw new UnsupportedOperationException("BelowMin has no value");
    }

    @Override
    public <X> Literal<X> to(Type type) {
      throw new UnsupportedOperationException("Cannot change the type of BelowMin");
    }

    @Override
    public Comparator<T> comparator() {
      throw new UnsupportedOperationException("BelowMin has no comparator");
    }

    @Override
    public String toString() {
      return "belowMin";
    }
  }

  static class BooleanLiteral extends ComparableLiteral<Boolean> {
    BooleanLiteral(Boolean value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      if (type.typeId() == Type.TypeID.BOOLEAN) {
        return (Literal<T>) this;
      }
      return null;
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.BOOLEAN;
    }
  }

  static class IntegerLiteral extends ComparableLiteral<Integer> {
    IntegerLiteral(Integer value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case INTEGER:
          return (Literal<T>) this;
        case LONG:
          return (Literal<T>) new LongLiteral(value().longValue());
        case FLOAT:
          return (Literal<T>) new FloatLiteral(value().floatValue());
        case DOUBLE:
          return (Literal<T>) new DoubleLiteral(value().doubleValue());
        case DATE:
          return (Literal<T>) new DateLiteral(value());
        case DECIMAL:
          int scale = ((Types.DecimalType) type).scale();
          // rounding mode isn't necessary, but pass one to avoid warnings
          return (Literal<T>)
              new DecimalLiteral(BigDecimal.valueOf(value()).setScale(scale, RoundingMode.HALF_UP));
        default:
          return null;
      }
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.INTEGER;
    }
  }

  static class LongLiteral extends ComparableLiteral<Long> {
    LongLiteral(Long value) {
      super(value);
    }

    /**
     * 把 Long 字面量转换为目标类型。
     *
     * <p>逻辑：转 INTEGER/DATE 时做越界检测（超 Integer.MAX/MIN 返回 aboveMax/belowMin）； 转 LONG 返回自身；转
     * FLOAT/DOUBLE 做窄化；转 TIME/TIMESTAMP 直接复用值； 转 DECIMAL 按 scale 设置；其余返回 null（不可转换）。
     *
     * @param type 目标类型
     * @param <T> 目标 Java 类型
     * @return 转换后的字面量，或 aboveMax/belowMin，或 null
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case INTEGER:
          if ((long) Integer.MAX_VALUE < value()) {
            return aboveMax();
          } else if ((long) Integer.MIN_VALUE > value()) {
            return belowMin();
          }
          return (Literal<T>) new IntegerLiteral(value().intValue());
        case LONG:
          return (Literal<T>) this;
        case FLOAT:
          return (Literal<T>) new FloatLiteral(value().floatValue());
        case DOUBLE:
          return (Literal<T>) new DoubleLiteral(value().doubleValue());
        case TIME:
          return (Literal<T>) new TimeLiteral(value());
        case TIMESTAMP:
          return (Literal<T>) new TimestampLiteral(value());
        case DATE:
          if ((long) Integer.MAX_VALUE < value()) {
            return aboveMax();
          } else if ((long) Integer.MIN_VALUE > value()) {
            return belowMin();
          }
          return (Literal<T>) new DateLiteral(value().intValue());
        case DECIMAL:
          int scale = ((Types.DecimalType) type).scale();
          // rounding mode isn't necessary, but pass one to avoid warnings
          return (Literal<T>)
              new DecimalLiteral(BigDecimal.valueOf(value()).setScale(scale, RoundingMode.HALF_UP));
        default:
          return null;
      }
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.LONG;
    }
  }

  static class FloatLiteral extends ComparableLiteral<Float> {
    FloatLiteral(Float value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case FLOAT:
          return (Literal<T>) this;
        case DOUBLE:
          return (Literal<T>) new DoubleLiteral(value().doubleValue());
        case DECIMAL:
          int scale = ((Types.DecimalType) type).scale();
          return (Literal<T>)
              new DecimalLiteral(BigDecimal.valueOf(value()).setScale(scale, RoundingMode.HALF_UP));
        default:
          return null;
      }
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.FLOAT;
    }
  }

  static class DoubleLiteral extends ComparableLiteral<Double> {
    DoubleLiteral(Double value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case FLOAT:
          if ((double) Float.MAX_VALUE < value()) {
            return aboveMax();
          } else if ((double) -Float.MAX_VALUE > value()) {
            // Compare with -Float.MAX_VALUE because it is the most negative float value.
            // Float.MIN_VALUE is the smallest non-negative floating point value.
            return belowMin();
          }
          return (Literal<T>) new FloatLiteral(value().floatValue());
        case DOUBLE:
          return (Literal<T>) this;
        case DECIMAL:
          int scale = ((Types.DecimalType) type).scale();
          return (Literal<T>)
              new DecimalLiteral(BigDecimal.valueOf(value()).setScale(scale, RoundingMode.HALF_UP));
        default:
          return null;
      }
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.DOUBLE;
    }
  }

  static class DateLiteral extends ComparableLiteral<Integer> {
    DateLiteral(Integer value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      if (type.typeId() == Type.TypeID.DATE) {
        return (Literal<T>) this;
      }
      return null;
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.DATE;
    }
  }

  static class TimeLiteral extends ComparableLiteral<Long> {
    TimeLiteral(Long value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      if (type.typeId() == Type.TypeID.TIME) {
        return (Literal<T>) this;
      }
      return null;
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.TIME;
    }
  }

  static class TimestampLiteral extends ComparableLiteral<Long> {
    TimestampLiteral(Long value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case TIMESTAMP:
          return (Literal<T>) this;
        case DATE:
          return (Literal<T>)
              new DateLiteral(
                  (int)
                      ChronoUnit.DAYS.between(
                          EPOCH_DAY, EPOCH.plus(value(), ChronoUnit.MICROS).toLocalDate()));
        default:
      }
      return null;
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.TIMESTAMP;
    }
  }

  static class DecimalLiteral extends ComparableLiteral<BigDecimal> {
    DecimalLiteral(BigDecimal value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case DECIMAL:
          // do not change decimal scale
          return (Literal<T>) this;
        default:
          return null;
      }
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.DECIMAL;
    }
  }

  /**
   * 字符串字面量：可解析为目标日期/时间/时间戳/UUID/Decimal 等类型。
   *
   * <p>设计意图：字符串是引擎侧最通用的字面量载体，故 {@code to(Type)} 承担从字符串到 各时间类型的解析职责，使用 ISO 标准格式。
   */
  static class StringLiteral extends BaseLiteral<CharSequence> {
    private static final Comparator<CharSequence> CMP =
        Comparators.<CharSequence>nullsFirst().thenComparing(Comparators.charSequences());

    StringLiteral(CharSequence value) {
      super(value);
    }

    /**
     * 把字符串字面量解析并转换为目标类型（支持从字符串到日期/时间/时间戳/UUID/Decimal 等）。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>DATE：按 ISO 本地日期解析，转为自纪元的天数。
     *   <li>TIME：按 ISO 本地时间解析，转为微秒。
     *   <li>TIMESTAMP：按是否带时区选择 ISO 偏移时间或本地时间解析，转为自纪元的微秒。
     *   <li>STRING：返回自身。
     *   <li>UUID：按 UUID.fromString 解析。
     *   <li>DECIMAL：按字符串构造 BigDecimal（不改 scale）。
     *   <li>其余返回 null（不可转换）。
     * </ul>
     *
     * @param type 目标类型
     * @param <T> 目标 Java 类型
     * @return 转换后的字面量，或 null
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case DATE:
          int date =
              (int)
                  ChronoUnit.DAYS.between(
                      EPOCH_DAY, LocalDate.parse(value(), DateTimeFormatter.ISO_LOCAL_DATE));
          return (Literal<T>) new DateLiteral(date);

        case TIME:
          long timeMicros =
              LocalTime.parse(value(), DateTimeFormatter.ISO_LOCAL_TIME).toNanoOfDay() / 1000;
          return (Literal<T>) new TimeLiteral(timeMicros);

        case TIMESTAMP:
          if (((Types.TimestampType) type).shouldAdjustToUTC()) {
            long timestampMicros =
                ChronoUnit.MICROS.between(
                    EPOCH, OffsetDateTime.parse(value(), DateTimeFormatter.ISO_DATE_TIME));
            return (Literal<T>) new TimestampLiteral(timestampMicros);
          } else {
            long timestampMicros =
                ChronoUnit.MICROS.between(
                    EPOCH,
                    LocalDateTime.parse(value(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atOffset(ZoneOffset.UTC));
            return (Literal<T>) new TimestampLiteral(timestampMicros);
          }

        case STRING:
          return (Literal<T>) this;

        case UUID:
          return (Literal<T>) new UUIDLiteral(UUID.fromString(value().toString()));

        case DECIMAL:
          // do not change decimal scale
          BigDecimal decimal = new BigDecimal(value().toString());
          return (Literal<T>) new DecimalLiteral(decimal);

        default:
          return null;
      }
    }

    @Override
    public Comparator<CharSequence> comparator() {
      return CMP;
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.STRING;
    }

    @Override
    public String toString() {
      return "\"" + value() + "\"";
    }
  }

  static class UUIDLiteral extends ComparableLiteral<UUID> {
    UUIDLiteral(UUID value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      if (type.typeId() == Type.TypeID.UUID) {
        return (Literal<T>) this;
      }
      return null;
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.UUID;
    }
  }

  static class FixedLiteral extends BaseLiteral<ByteBuffer> {
    private static final Comparator<ByteBuffer> CMP =
        Comparators.<ByteBuffer>nullsFirst().thenComparing(Comparators.unsignedBytes());

    FixedLiteral(ByteBuffer value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case FIXED:
          Types.FixedType fixed = (Types.FixedType) type;
          if (value().remaining() == fixed.length()) {
            return (Literal<T>) this;
          }
          return null;
        case BINARY:
          return (Literal<T>) new BinaryLiteral(value());
        default:
          return null;
      }
    }

    @Override
    public Comparator<ByteBuffer> comparator() {
      return CMP;
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.FIXED;
    }

    Object writeReplace() throws ObjectStreamException {
      return new SerializationProxies.FixedLiteralProxy(value());
    }

    @Override
    public String toString() {
      byte[] bytes = ByteBuffers.toByteArray(value());
      return "X'" + BaseEncoding.base16().encode(bytes) + "'";
    }
  }

  static class BinaryLiteral extends BaseLiteral<ByteBuffer> {
    private static final Comparator<ByteBuffer> CMP =
        Comparators.<ByteBuffer>nullsFirst().thenComparing(Comparators.unsignedBytes());

    BinaryLiteral(ByteBuffer value) {
      super(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Literal<T> to(Type type) {
      switch (type.typeId()) {
        case FIXED:
          Types.FixedType fixed = (Types.FixedType) type;
          if (value().remaining() == fixed.length()) {
            return (Literal<T>) new FixedLiteral(value());
          }
          return null;
        case BINARY:
          return (Literal<T>) this;
        default:
          return null;
      }
    }

    @Override
    public Comparator<ByteBuffer> comparator() {
      return CMP;
    }

    Object writeReplace() throws ObjectStreamException {
      return new SerializationProxies.BinaryLiteralProxy(value());
    }

    @Override
    protected Type.TypeID typeId() {
      return Type.TypeID.BINARY;
    }

    @Override
    public String toString() {
      byte[] bytes = ByteBuffers.toByteArray(value());
      return "X'" + BaseEncoding.base16().encode(bytes) + "'";
    }
  }
}
