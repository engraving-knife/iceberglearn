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
package org.apache.iceberg.arrow.vectorized;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.ValueVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.StructVector;
import org.apache.arrow.vector.util.DecimalUtility;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.PrimitiveType;

/**
 * 文件级说明：根据 {@link VectorHolder} 构造类型化的 {@link ArrowVectorAccessor} 的通用工厂。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路中向量值访问器的构造中枢）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>依据向量是否字典编码，分发到字典访问器或普通访问器。
 *   <li>覆盖各类 Arrow 向量类型（布尔、整型、长整型、浮点、双精度、Decimal、字符串、 二进制、日期、时间戳、时间、定长二进制、List、Struct），通过内部访问器子类
 *       实现按行读取。
 *   <li>通过可注入的 Decimal/String/Array/Struct 工厂适配不同引擎的类型表示 （如 Spark 的 UTF8String/Decimal、Hive 的
 *       HiveDecimal）。
 * </ul>
 *
 * <p>设计意图：采用工厂 + 模板方法模式，把“读取向量值”与“目标类型表示”解耦。基类 {@link ArrowVectorAccessor} 定义接口，本工厂按 Parquet
 * 原始类型/逻辑类型选择具体子类； Decimal/String 等类型的具体产出形态由工厂接口注入，避免与引擎类型硬绑定。字典编码访问器 对字符串/Decimal 做了缓存以减少重复解码开销。
 *
 * <p>上下游关系：上游被 {@link ArrowVectorAccessors}（默认实现）及各引擎集成子类调用； 下游产出 {@link ArrowVectorAccessor} 供
 * {@link ColumnVector}、{@link DictEncodedArrowConverter} 使用。
 *
 * @param <DecimalT> Decimal 的具体表示类型
 * @param <Utf8StringT> UTF8 字符串的具体表示类型
 * @param <ArrayT> 数组的具体表示类型（如 Spark 的 ColumnarArray）
 * @param <ChildVectorT> 子列向量的具体表示类型（如 Spark 的 ArrowColumnVector）
 */
public class GenericArrowVectorAccessorFactory<
    DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable> {

  private final Supplier<DecimalFactory<DecimalT>> decimalFactorySupplier;
  private final Supplier<StringFactory<Utf8StringT>> stringFactorySupplier;
  private final Supplier<StructChildFactory<ChildVectorT>> structChildFactorySupplier;
  private final Supplier<ArrayFactory<ChildVectorT, ArrayT>> arrayFactorySupplier;

  /**
   * 构造工厂实例，注入 Decimal/String/Struct/Array 四种工厂的供应商。
   *
   * <p>若某类型不支持，对应供应商可抛出 {@link UnsupportedOperationException}。
   *
   * @param decimalFactorySupplier Decimal 工厂供应商
   * @param stringFactorySupplier 字符串工厂供应商
   * @param structChildFactorySupplier 结构体子列工厂供应商
   * @param arrayFactorySupplier 数组工厂供应商
   */
  protected GenericArrowVectorAccessorFactory(
      Supplier<DecimalFactory<DecimalT>> decimalFactorySupplier,
      Supplier<StringFactory<Utf8StringT>> stringFactorySupplier,
      Supplier<StructChildFactory<ChildVectorT>> structChildFactorySupplier,
      Supplier<ArrayFactory<ChildVectorT, ArrayT>> arrayFactorySupplier) {
    this.decimalFactorySupplier = decimalFactorySupplier;
    this.stringFactorySupplier = stringFactorySupplier;
    this.structChildFactorySupplier = structChildFactorySupplier;
    this.arrayFactorySupplier = arrayFactorySupplier;
  }

  /**
   * 根据向量持有者构造对应的访问器。
   *
   * <p>逻辑：若向量字典编码则委托 {@link #getDictionaryVectorAccessor}，否则委托 {@link #getPlainVectorAccessor}。desc
   * 为 null（常量/位置向量持有者）时 primitive 视为 null。
   *
   * @param holder 向量持有者
   * @return 类型合适的 {@link ArrowVectorAccessor}
   */
  public ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> getVectorAccessor(
      VectorHolder holder) {
    Dictionary dictionary = holder.dictionary();
    boolean isVectorDictEncoded = holder.isDictionaryEncoded();
    FieldVector vector = holder.vector();
    ColumnDescriptor desc = holder.descriptor();
    // desc could be null when the holder is ConstantVectorHolder/PositionVectorHolder
    PrimitiveType primitive = desc == null ? null : desc.getPrimitiveType();
    if (isVectorDictEncoded) {
      return getDictionaryVectorAccessor(dictionary, desc, vector, primitive);
    } else {
      return getPlainVectorAccessor(vector, primitive);
    }
  }

  /**
   * 为字典编码向量构造访问器。
   *
   * <p>逻辑：校验字典 id 必须存储在 {@link IntVector} 中；按 Parquet 逻辑类型（原始类型） 分发到字符串/long/Decimal
   * 等字典访问器；若无逻辑类型则按原始类型名（BINARY/FLOAT/INT64/ INT96/DOUBLE）分发。INT96 兼容旧版 Impala/Spark 的时间戳写入。
   *
   * @param dictionary Parquet 字典
   * @param desc 列描述符
   * @param vector 存储字典 id 的向量
   * @param primitive Parquet 原始类型
   * @return 字典访问器
   */
  private ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT>
      getDictionaryVectorAccessor(
          Dictionary dictionary,
          ColumnDescriptor desc,
          FieldVector vector,
          PrimitiveType primitive) {
    Preconditions.checkState(
        vector instanceof IntVector, "Dictionary ids should be stored in IntVectors only");
    if (primitive.getOriginalType() != null) {
      switch (desc.getPrimitiveType().getOriginalType()) {
        case ENUM:
        case JSON:
        case UTF8:
        case BSON:
          return new DictionaryStringAccessor<>(
              (IntVector) vector, dictionary, stringFactorySupplier.get());
        case INT_64:
        case TIME_MICROS:
        case TIMESTAMP_MILLIS:
        case TIMESTAMP_MICROS:
          return new DictionaryLongAccessor<>((IntVector) vector, dictionary);
        case DECIMAL:
          switch (primitive.getPrimitiveTypeName()) {
            case BINARY:
            case FIXED_LEN_BYTE_ARRAY:
              return new DictionaryDecimalBinaryAccessor<>(
                  (IntVector) vector, dictionary, decimalFactorySupplier.get());
            case INT64:
              return new DictionaryDecimalLongAccessor<>(
                  (IntVector) vector, dictionary, decimalFactorySupplier.get());
            case INT32:
              return new DictionaryDecimalIntAccessor<>(
                  (IntVector) vector, dictionary, decimalFactorySupplier.get());
            default:
              throw new UnsupportedOperationException(
                  "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
          }
        default:
          throw new UnsupportedOperationException(
              "Unsupported logical type: " + primitive.getOriginalType());
      }
    } else {
      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
        case BINARY:
          return new DictionaryBinaryAccessor<>((IntVector) vector, dictionary);
        case FLOAT:
          return new DictionaryFloatAccessor<>((IntVector) vector, dictionary);
        case INT64:
          return new DictionaryLongAccessor<>((IntVector) vector, dictionary);
        case INT96:
          // Impala & Spark used to write timestamps as INT96 by default. For backwards
          // compatibility we try to read INT96 as timestamps. But INT96 is not recommended
          // and deprecated (see https://issues.apache.org/jira/browse/PARQUET-323)
          return new DictionaryTimestampInt96Accessor<>((IntVector) vector, dictionary);
        case DOUBLE:
          return new DictionaryDoubleAccessor<>((IntVector) vector, dictionary);
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }
  }

  /**
   * 为非字典编码向量构造访问器。
   *
   * <p>逻辑：按 Arrow 向量具体类型（BitVector/IntVector/BigIntVector/Float4Vector/...） 分发到对应访问器；对
   * Int/Long/FixedSizeBinary 还会判断是否为 Decimal 底层存储并选择 对应的 Decimal 访问器。不支持则抛出 {@link
   * UnsupportedOperationException}。
   *
   * @param vector Arrow 向量
   * @param primitive Parquet 原始类型（用于判断 Decimal）
   * @return 普通访问器
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  private ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> getPlainVectorAccessor(
      FieldVector vector, PrimitiveType primitive) {
    if (vector instanceof BitVector) {
      return new BooleanAccessor<>((BitVector) vector);
    } else if (vector instanceof IntVector) {
      if (isDecimal(primitive)) {
        return new IntBackedDecimalAccessor<>((IntVector) vector, decimalFactorySupplier.get());
      }
      return new IntAccessor<>((IntVector) vector);
    } else if (vector instanceof BigIntVector) {
      if (isDecimal(primitive)) {
        return new LongBackedDecimalAccessor<>((BigIntVector) vector, decimalFactorySupplier.get());
      }
      return new LongAccessor<>((BigIntVector) vector);
    } else if (vector instanceof Float4Vector) {
      return new FloatAccessor<>((Float4Vector) vector);
    } else if (vector instanceof Float8Vector) {
      return new DoubleAccessor<>((Float8Vector) vector);
    } else if (vector instanceof DecimalVector) {
      return new DecimalAccessor<>((DecimalVector) vector, decimalFactorySupplier.get());
    } else if (vector instanceof VarCharVector) {
      return new StringAccessor<>((VarCharVector) vector, stringFactorySupplier.get());
    } else if (vector instanceof VarBinaryVector) {
      return new BinaryAccessor<>((VarBinaryVector) vector);
    } else if (vector instanceof DateDayVector) {
      return new DateAccessor<>((DateDayVector) vector);
    } else if (vector instanceof TimeStampMicroTZVector) {
      return new TimestampMicroTzAccessor<>((TimeStampMicroTZVector) vector);
    } else if (vector instanceof TimeStampMicroVector) {
      return new TimestampMicroAccessor<>((TimeStampMicroVector) vector);
    } else if (vector instanceof ListVector) {
      ListVector listVector = (ListVector) vector;
      return new ArrayAccessor<>(listVector, arrayFactorySupplier.get());
    } else if (vector instanceof StructVector) {
      StructVector structVector = (StructVector) vector;
      return new StructAccessor<>(structVector, structChildFactorySupplier.get());
    } else if (vector instanceof TimeMicroVector) {
      return new TimeMicroAccessor<>((TimeMicroVector) vector);
    } else if (vector instanceof FixedSizeBinaryVector) {
      if (isDecimal(primitive)) {
        return new FixedSizeBinaryBackedDecimalAccessor<>(
            (FixedSizeBinaryVector) vector, decimalFactorySupplier.get());
      }
      return new FixedSizeBinaryAccessor<>(
          (FixedSizeBinaryVector) vector, stringFactorySupplier.get());
    }
    throw new UnsupportedOperationException("Unsupported vector: " + vector.getClass());
  }

  /**
   * 判断 Parquet 原始类型是否为 Decimal 逻辑类型。
   *
   * @param primitive Parquet 原始类型
   * @return 为 Decimal 返回 true
   */
  private static boolean isDecimal(PrimitiveType primitive) {
    return primitive != null && OriginalType.DECIMAL.equals(primitive.getOriginalType());
  }

  /** 包装 {@link BitVector} 的布尔值访问器。 */
  private static class BooleanAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final BitVector vector;

    BooleanAccessor(BitVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final boolean getBoolean(int rowId) {
      return vector.get(rowId) == 1;
    }
  }

  /** 包装 {@link IntVector} 的整型访问器，支持以 int 表示的 Decimal。 */
  private static class IntAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final IntVector vector;

    IntAccessor(IntVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final int getInt(int rowId) {
      return vector.get(rowId);
    }

    @Override
    public final long getLong(int rowId) {
      return getInt(rowId);
    }
  }

  /** 包装 {@link BigIntVector} 的长整型访问器。 */
  private static class LongAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final BigIntVector vector;

    LongAccessor(BigIntVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final long getLong(int rowId) {
      return vector.get(rowId);
    }
  }

  /** 基于字典解码 long 值的访问器。 */
  private static class DictionaryLongAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final IntVector offsetVector;
    private final Dictionary dictionary;

    DictionaryLongAccessor(IntVector vector, Dictionary dictionary) {
      super(vector);
      this.offsetVector = vector;
      this.dictionary = dictionary;
    }

    @Override
    public final long getLong(int rowId) {
      return dictionary.decodeToLong(offsetVector.get(rowId));
    }
  }

  /** 包装 {@link Float4Vector} 的单精度浮点访问器。 */
  private static class FloatAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final Float4Vector vector;

    FloatAccessor(Float4Vector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final float getFloat(int rowId) {
      return vector.get(rowId);
    }

    @Override
    public final double getDouble(int rowId) {
      return getFloat(rowId);
    }
  }

  /** 基于字典解码 float 值的访问器。 */
  private static class DictionaryFloatAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final IntVector offsetVector;
    private final Dictionary dictionary;

    DictionaryFloatAccessor(IntVector vector, Dictionary dictionary) {
      super(vector);
      this.offsetVector = vector;
      this.dictionary = dictionary;
    }

    @Override
    public final float getFloat(int rowId) {
      return dictionary.decodeToFloat(offsetVector.get(rowId));
    }

    @Override
    public final double getDouble(int rowId) {
      return getFloat(rowId);
    }
  }

  /** 包装 {@link Float8Vector} 的双精度浮点访问器。 */
  private static class DoubleAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final Float8Vector vector;

    DoubleAccessor(Float8Vector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final double getDouble(int rowId) {
      return vector.get(rowId);
    }
  }

  /** 基于字典解码 double 值的访问器。 */
  private static class DictionaryDoubleAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final IntVector offsetVector;
    private final Dictionary dictionary;

    DictionaryDoubleAccessor(IntVector vector, Dictionary dictionary) {
      super(vector);
      this.offsetVector = vector;
      this.dictionary = dictionary;
    }

    @Override
    public final double getDouble(int rowId) {
      return dictionary.decodeToDouble(offsetVector.get(rowId));
    }
  }

  /** 包装 {@link VarCharVector} 的 UTF8 字符串访问器。 */
  private static class StringAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final VarCharVector vector;
    private final StringFactory<Utf8StringT> stringFactory;

    StringAccessor(VarCharVector vector, StringFactory<Utf8StringT> stringFactory) {
      super(vector);
      this.vector = vector;
      this.stringFactory = stringFactory;
    }

    @Override
    public final Utf8StringT getUTF8String(int rowId) {
      return stringFactory.ofRow(vector, rowId);
    }
  }

  /** 基于字典解码字符串的访问器，带按字典 id 的结果缓存。 */
  private static class DictionaryStringAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final Dictionary dictionary;
    private final StringFactory<Utf8StringT> stringFactory;
    private final IntVector offsetVector;
    private final Utf8StringT[] cache;

    DictionaryStringAccessor(
        IntVector vector, Dictionary dictionary, StringFactory<Utf8StringT> stringFactory) {
      super(vector);
      this.offsetVector = vector;
      this.dictionary = dictionary;
      this.stringFactory = stringFactory;
      this.cache = genericArray(stringFactory.getGenericClass(), dictionary.getMaxId() + 1);
    }

    @Override
    public final Utf8StringT getUTF8String(int rowId) {
      int offset = offsetVector.get(rowId);
      if (cache[offset] == null) {
        cache[offset] =
            stringFactory.ofByteBuffer(dictionary.decodeToBinary(offset).toByteBuffer());
      }
      return cache[offset];
    }
  }

  /** 包装 {@link VarBinaryVector} 的二进制访问器。 */
  private static class BinaryAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final VarBinaryVector vector;

    BinaryAccessor(VarBinaryVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final byte[] getBinary(int rowId) {
      return vector.get(rowId);
    }
  }

  /** 基于字典解码二进制值的访问器。 */
  private static class DictionaryBinaryAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final IntVector offsetVector;
    private final Dictionary dictionary;

    DictionaryBinaryAccessor(IntVector vector, Dictionary dictionary) {
      super(vector);
      this.offsetVector = vector;
      this.dictionary = dictionary;
    }

    @Override
    public final byte[] getBinary(int rowId) {
      return dictionary.decodeToBinary(offsetVector.get(rowId)).getBytes();
    }
  }

  /** 基于字典解码 INT96 时间戳的访问器（兼容旧版 Impala/Spark）。 */
  private static class DictionaryTimestampInt96Accessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final IntVector offsetVector;
    private final Dictionary dictionary;

    DictionaryTimestampInt96Accessor(IntVector vector, Dictionary dictionary) {
      super(vector);
      this.offsetVector = vector;
      this.dictionary = dictionary;
    }

    @Override
    public final long getLong(int rowId) {
      ByteBuffer byteBuffer =
          dictionary
              .decodeToBinary(offsetVector.get(rowId))
              .toByteBuffer()
              .order(ByteOrder.LITTLE_ENDIAN);
      return ParquetUtil.extractTimestampInt96(byteBuffer);
    }
  }

  /** 包装 {@link DateDayVector} 的日期访问器。 */
  private static class DateAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final DateDayVector vector;

    DateAccessor(DateDayVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final int getInt(int rowId) {
      return vector.get(rowId);
    }
  }

  /** 包装 {@link TimeStampMicroTZVector} 的带时区时间戳访问器。 */
  private static class TimestampMicroTzAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final TimeStampMicroTZVector vector;

    TimestampMicroTzAccessor(TimeStampMicroTZVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final long getLong(int rowId) {
      return vector.get(rowId);
    }
  }

  /** 包装 {@link TimeStampMicroVector} 的无时区时间戳访问器。 */
  private static class TimestampMicroAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final TimeStampMicroVector vector;

    TimestampMicroAccessor(TimeStampMicroVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final long getLong(int rowId) {
      return vector.get(rowId);
    }
  }

  /** 包装 {@link TimeMicroVector} 的时间访问器。 */
  private static class TimeMicroAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final TimeMicroVector vector;

    TimeMicroAccessor(TimeMicroVector vector) {
      super(vector);
      this.vector = vector;
    }

    @Override
    public final long getLong(int rowId) {
      return vector.get(rowId);
    }
  }

  /** 包装 {@link FixedSizeBinaryVector} 的访问器（UUID/固定二进制）。 */
  private static class FixedSizeBinaryAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final FixedSizeBinaryVector vector;
    private final StringFactory<Utf8StringT> stringFactory;

    FixedSizeBinaryAccessor(FixedSizeBinaryVector vector) {
      super(vector);
      this.vector = vector;
      this.stringFactory = null;
    }

    FixedSizeBinaryAccessor(
        FixedSizeBinaryVector vector, StringFactory<Utf8StringT> stringFactory) {
      super(vector);
      this.vector = vector;
      this.stringFactory = stringFactory;
    }

    @Override
    public byte[] getBinary(int rowId) {
      return vector.get(rowId);
    }

    @Override
    public Utf8StringT getUTF8String(int rowId) {
      return null == stringFactory
          ? super.getUTF8String(rowId)
          : stringFactory.ofRow(vector, rowId);
    }
  }

  /** 包装 {@link ListVector} 的数组访问器。 */
  private static class ArrayAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final ListVector vector;
    private final ChildVectorT arrayData;
    private final ArrayFactory<ChildVectorT, ArrayT> arrayFactory;

    ArrayAccessor(ListVector vector, ArrayFactory<ChildVectorT, ArrayT> arrayFactory) {
      super(vector);
      this.vector = vector;
      this.arrayFactory = arrayFactory;
      this.arrayData = arrayFactory.ofChild(vector.getDataVector());
    }

    @Override
    public final ArrayT getArray(int rowId) {
      return arrayFactory.ofRow(vector, arrayData, rowId);
    }
  }

  /** 包装 {@link StructVector} 的结构体访问器。 */
  private static class StructAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    StructAccessor(StructVector structVector, StructChildFactory<ChildVectorT> structChildFactory) {
      super(
          structVector,
          IntStream.range(0, structVector.size())
              .mapToObj(structVector::getVectorById)
              .map(structChildFactory::of)
              .toArray(genericArray(structChildFactory.getGenericClass())));
    }
  }

  /** 包装 {@link DecimalVector} 的 Decimal 访问器。 */
  private static class DecimalAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final DecimalVector vector;
    private final DecimalFactory<DecimalT> decimalFactory;

    DecimalAccessor(DecimalVector vector, DecimalFactory<DecimalT> decimalFactory) {
      super(vector);
      this.vector = vector;
      this.decimalFactory = decimalFactory;
    }

    @Override
    public final DecimalT getDecimal(int rowId, int precision, int scale) {
      return decimalFactory.ofBigDecimal(
          DecimalUtility.getBigDecimalFromArrowBuf(
              vector.getDataBuffer(), rowId, scale, DecimalVector.TYPE_WIDTH),
          precision,
          scale);
    }
  }

  /** 以 int 底层存储的 Decimal 访问器。 */
  private static class IntBackedDecimalAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final IntVector vector;
    private final DecimalFactory<DecimalT> decimalFactory;

    IntBackedDecimalAccessor(IntVector vector, DecimalFactory<DecimalT> decimalFactory) {
      super(vector);
      this.vector = vector;
      this.decimalFactory = decimalFactory;
    }

    @Override
    public final DecimalT getDecimal(int rowId, int precision, int scale) {
      return decimalFactory.ofLong(vector.get(rowId), precision, scale);
    }
  }

  /** 以 long 底层存储的 Decimal 访问器。 */
  private static class LongBackedDecimalAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final BigIntVector vector;
    private final DecimalFactory<DecimalT> decimalFactory;

    LongBackedDecimalAccessor(BigIntVector vector, DecimalFactory<DecimalT> decimalFactory) {
      super(vector);
      this.vector = vector;
      this.decimalFactory = decimalFactory;
    }

    @Override
    public final DecimalT getDecimal(int rowId, int precision, int scale) {
      return decimalFactory.ofLong(vector.get(rowId), precision, scale);
    }
  }

  /** 以定长二进制底层存储的 Decimal 访问器。 */
  private static class FixedSizeBinaryBackedDecimalAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    private final FixedSizeBinaryVector vector;
    private final DecimalFactory<DecimalT> decimalFactory;

    FixedSizeBinaryBackedDecimalAccessor(
        FixedSizeBinaryVector vector, DecimalFactory<DecimalT> decimalFactory) {
      super(vector);
      this.vector = vector;
      this.decimalFactory = decimalFactory;
    }

    @Override
    public final DecimalT getDecimal(int rowId, int precision, int scale) {
      byte[] bytes = vector.get(rowId);
      BigInteger bigInteger = new BigInteger(bytes);
      BigDecimal javaDecimal = new BigDecimal(bigInteger, scale);
      return decimalFactory.ofBigDecimal(javaDecimal, precision, scale);
    }
  }

  @SuppressWarnings("checkstyle:VisibilityModifier")
  /** 基于字典解码 Decimal 的抽象访问器，带按字典 id 的缓存。 */
  private abstract static class DictionaryDecimalAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends ArrowVectorAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {
    private final DecimalT[] cache;
    private final IntVector offsetVector;
    protected final DecimalFactory<DecimalT> decimalFactory;
    protected final Dictionary parquetDictionary;

    private DictionaryDecimalAccessor(
        IntVector vector, Dictionary dictionary, DecimalFactory<DecimalT> decimalFactory) {
      super(vector);
      this.offsetVector = vector;
      this.parquetDictionary = dictionary;
      this.decimalFactory = decimalFactory;
      this.cache = genericArray(decimalFactory.getGenericClass(), dictionary.getMaxId() + 1);
    }

    @Override
    public final DecimalT getDecimal(int rowId, int precision, int scale) {
      int offset = offsetVector.get(rowId);
      if (cache[offset] == null) {
        cache[offset] = decode(offset, precision, scale);
      }
      return cache[offset];
    }

    protected abstract DecimalT decode(int dictId, int precision, int scale);
  }

  /** 字典解码、二进制底层的 Decimal 访问器。 */
  private static class DictionaryDecimalBinaryAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends DictionaryDecimalAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    DictionaryDecimalBinaryAccessor(
        IntVector vector, Dictionary dictionary, DecimalFactory<DecimalT> decimalFactory) {
      super(vector, dictionary, decimalFactory);
    }

    @Override
    protected DecimalT decode(int dictId, int precision, int scale) {
      ByteBuffer byteBuffer = parquetDictionary.decodeToBinary(dictId).toByteBuffer();
      BigDecimal value =
          DecimalUtility.getBigDecimalFromByteBuffer(byteBuffer, scale, byteBuffer.remaining());
      return decimalFactory.ofBigDecimal(value, precision, scale);
    }
  }

  /** 字典解码、long 底层的 Decimal 访问器。 */
  private static class DictionaryDecimalLongAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends DictionaryDecimalAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    DictionaryDecimalLongAccessor(
        IntVector vector, Dictionary dictionary, DecimalFactory<DecimalT> decimalFactory) {
      super(vector, dictionary, decimalFactory);
    }

    @Override
    protected DecimalT decode(int dictId, int precision, int scale) {
      return decimalFactory.ofLong(parquetDictionary.decodeToLong(dictId), precision, scale);
    }
  }

  /** 字典解码、int 底层的 Decimal 访问器。 */
  private static class DictionaryDecimalIntAccessor<
          DecimalT, Utf8StringT, ArrayT, ChildVectorT extends AutoCloseable>
      extends DictionaryDecimalAccessor<DecimalT, Utf8StringT, ArrayT, ChildVectorT> {

    DictionaryDecimalIntAccessor(
        IntVector vector, Dictionary dictionary, DecimalFactory<DecimalT> decimalFactory) {
      super(vector, dictionary, decimalFactory);
    }

    @Override
    protected DecimalT decode(int dictId, int precision, int scale) {
      return decimalFactory.ofLong(parquetDictionary.decodeToInt(dictId), precision, scale);
    }
  }

  /**
   * Decimal 工厂接口：将 Arrow 向量值转换为 {@code DecimalT} 类型的 Decimal。
   *
   * @param <DecimalT> Decimal 的具体表示类型
   */
  protected interface DecimalFactory<DecimalT> {
    /** Class of concrete decimal type. */
    Class<DecimalT> getGenericClass();

    /** Create a decimal from the given long value, precision and scale. */
    DecimalT ofLong(long value, int precision, int scale);

    /** Create a decimal from the given {@link BigDecimal} value, precision and scale. */
    DecimalT ofBigDecimal(BigDecimal value, int precision, int scale);
  }

  /**
   * UTF8 字符串工厂接口：将 Arrow 向量值转换为 {@code Utf8StringT} 类型的字符串。
   *
   * @param <Utf8StringT> UTF8 字符串的具体表示类型
   */
  protected interface StringFactory<Utf8StringT> {
    /** Class of concrete UTF8 String type. */
    Class<Utf8StringT> getGenericClass();

    /** Create a UTF8 String from the row value in the arrow vector. */
    Utf8StringT ofRow(VarCharVector vector, int rowId);

    /** Create a UTF8 String from the row value in the FixedSizeBinaryVector vector. */
    default Utf8StringT ofRow(FixedSizeBinaryVector vector, int rowId) {
      throw new UnsupportedOperationException(
          String.format(
              "Creating %s from a FixedSizeBinaryVector is not supported",
              getGenericClass().getSimpleName()));
    }

    /** Create a UTF8 String from the byte array. */
    Utf8StringT ofBytes(byte[] bytes);

    /** Create a UTF8 String from the byte buffer. */
    Utf8StringT ofByteBuffer(ByteBuffer byteBuffer);
  }

  /**
   * 数组工厂接口：将 List 向量转换为 {@code ArrayT} 类型的数组。
   *
   * @param <ChildVectorT> 子列向量的具体表示类型
   * @param <ArrayT> 数值数组的具体表示类型
   */
  protected interface ArrayFactory<ChildVectorT, ArrayT> {
    /** Create a child vector of type {@code ChildVectorT} from the arrow child vector. */
    ChildVectorT ofChild(ValueVector childVector);

    /** Create an Arrow of type {@code ArrayT} from the row value in the arrow child vector. */
    ArrayT ofRow(ValueVector vector, ChildVectorT childData, int rowId);
  }

  /**
   * 结构体子列工厂接口：将 Struct 子向量转换为 {@code ChildVectorT} 类型。
   *
   * @param <ChildVectorT> 子列向量的具体表示类型
   */
  protected interface StructChildFactory<ChildVectorT> {
    /** Class of concrete child vector type. */
    Class<ChildVectorT> getGenericClass();

    /**
     * Create the child vector of type such as Spark's ArrowColumnVector from the arrow child
     * vector.
     */
    ChildVectorT of(ValueVector childVector);
  }

  /**
   * 返回按长度创建泛型数组的 {@link IntFunction}。
   *
   * @param genericClass 元素类型
   * @param <T> 元素类型
   * @return 数组构造函数
   */
  private static <T> IntFunction<T[]> genericArray(Class<T> genericClass) {
    return length -> genericArray(genericClass, length);
  }

  @SuppressWarnings("unchecked")
  /**
   * 通过反射创建指定长度的泛型数组。
   *
   * @param genericClass 元素类型
   * @param length 数组长度
   * @param <T> 元素类型
   * @return 新建的泛型数组
   */
  private static <T> T[] genericArray(Class<T> genericClass, int length) {
    return (T[]) Array.newInstance(genericClass, length);
  }
}
