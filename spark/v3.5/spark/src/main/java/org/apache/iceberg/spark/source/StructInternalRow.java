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
package org.apache.iceberg.spark.source;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.util.ArrayBasedMapData;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.GenericArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.BooleanType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.DoubleType;
import org.apache.spark.sql.types.FloatType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.TimestampType;
import org.apache.spark.unsafe.types.CalendarInterval;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 只读的 {@link InternalRow} 适配器：把 Iceberg {@link StructLike} 包装为 Spark 内部行。
 *
 * <p>所属模块：iceberg-spark（source 子包，Iceberg 与 Spark 行模型的桥接）。
 *
 * <p>职责：基于 Iceberg 结构类型把 {@link StructLike} 的字段按 Spark {@link InternalRow}
 * 接口暴露，支持各类基本类型与嵌套结构/数组/Map 的取值，供 Spark 读取直接消费。
 *
 * <p>设计意图：Iceberg 内部以 {@link StructLike} 表示行，Spark 以 {@link InternalRow} 表示行，
 * 本类做零拷贝适配，避免数据复制。所有写操作（setNullAt 等）均抛出异常，因为本行只读。 通过 {@link #setStruct} 复用同一实例包装不同
 * StructLike，减少对象分配。
 *
 * <p>上下游关系：被 {@link RowDataReader}、数据任务读取等用于把 Iceberg 行转为 Spark 行。
 */
class StructInternalRow extends InternalRow {
  private final Types.StructType type;
  private StructLike struct;

  StructInternalRow(Types.StructType type) {
    this.type = type;
  }

  private StructInternalRow(Types.StructType type, StructLike struct) {
    this.type = type;
    this.struct = struct;
  }

  /**
   * 替换当前包装的 {@link StructLike} 并返回自身，便于复用实例。
   *
   * @param newStruct 新的结构数据
   * @return 当前实例
   */
  public StructInternalRow setStruct(StructLike newStruct) {
    this.struct = newStruct;
    return this;
  }

  /** 返回字段数。 */
  @Override
  public int numFields() {
    return struct.size();
  }

  /** 本行为只读，写操作一律抛出异常。 */
  @Override
  public void setNullAt(int i) {
    throw new UnsupportedOperationException("StructInternalRow is read-only");
  }
  /** 执行 update 相关操作。 */
  @Override
  public void update(int i, Object value) {
    throw new UnsupportedOperationException("StructInternalRow is read-only");
  }
  /** 返回副本。 */
  @Override
  public InternalRow copy() {
    return this;
  }
  /** 判断是否 NullAt。 */
  @Override
  public boolean isNullAt(int ordinal) {
    return struct.get(ordinal, Object.class) == null;
  }
  /** 返回 Boolean 属性。 */
  @Override
  public boolean getBoolean(int ordinal) {
    return struct.get(ordinal, Boolean.class);
  }
  /** 返回 Byte 属性。 */
  @Override
  public byte getByte(int ordinal) {
    return (byte) (int) struct.get(ordinal, Integer.class);
  }
  /** 返回 Short 属性。 */
  @Override
  public short getShort(int ordinal) {
    return (short) (int) struct.get(ordinal, Integer.class);
  }
  /** 返回 Int 属性。 */
  @Override
  public int getInt(int ordinal) {
    Object integer = struct.get(ordinal, Object.class);

    if (integer instanceof Integer) {
      return (int) integer;
    } else if (integer instanceof LocalDate) {
      return (int) ((LocalDate) integer).toEpochDay();
    } else {
      throw new IllegalStateException(
          "Unknown type for int field. Type name: " + integer.getClass().getName());
    }
  }
  /** 返回 Long 属性。 */
  @Override
  public long getLong(int ordinal) {
    Object longVal = struct.get(ordinal, Object.class);

    if (longVal instanceof Long) {
      return (long) longVal;
    } else if (longVal instanceof OffsetDateTime) {
      return Duration.between(Instant.EPOCH, (OffsetDateTime) longVal).toNanos() / 1000;
    } else if (longVal instanceof LocalDate) {
      return ((LocalDate) longVal).toEpochDay();
    } else {
      throw new IllegalStateException(
          "Unknown type for long field. Type name: " + longVal.getClass().getName());
    }
  }
  /** 返回 Float 属性。 */
  @Override
  public float getFloat(int ordinal) {
    return struct.get(ordinal, Float.class);
  }
  /** 返回 Double 属性。 */
  @Override
  public double getDouble(int ordinal) {
    return struct.get(ordinal, Double.class);
  }
  /** 返回 Decimal 属性。 */
  @Override
  public Decimal getDecimal(int ordinal, int precision, int scale) {
    return isNullAt(ordinal) ? null : getDecimalInternal(ordinal, precision, scale);
  }
  /** 返回 DecimalInternal 属性。 */
  private Decimal getDecimalInternal(int ordinal, int precision, int scale) {
    return Decimal.apply(struct.get(ordinal, BigDecimal.class));
  }
  /** 返回 UTF8String 属性。 */
  @Override
  public UTF8String getUTF8String(int ordinal) {
    return isNullAt(ordinal) ? null : getUTF8StringInternal(ordinal);
  }
  /** 返回 UTF8StringInternal 属性。 */
  private UTF8String getUTF8StringInternal(int ordinal) {
    CharSequence seq = struct.get(ordinal, CharSequence.class);
    return UTF8String.fromString(seq.toString());
  }
  /** 返回 Binary 属性。 */
  @Override
  public byte[] getBinary(int ordinal) {
    return isNullAt(ordinal) ? null : getBinaryInternal(ordinal);
  }
  /** 返回 BinaryInternal 属性。 */
  private byte[] getBinaryInternal(int ordinal) {
    Object bytes = struct.get(ordinal, Object.class);

    // should only be either ByteBuffer or byte[]
    if (bytes instanceof ByteBuffer) {
      return ByteBuffers.toByteArray((ByteBuffer) bytes);
    } else if (bytes instanceof byte[]) {
      return (byte[]) bytes;
    } else {
      throw new IllegalStateException(
          "Unknown type for binary field. Type name: " + bytes.getClass().getName());
    }
  }
  /** 返回 Interval 属性。 */
  @Override
  public CalendarInterval getInterval(int ordinal) {
    throw new UnsupportedOperationException("Unsupported type: interval");
  }
  /** 返回 Struct 属性。 */
  @Override
  public InternalRow getStruct(int ordinal, int numFields) {
    return isNullAt(ordinal) ? null : getStructInternal(ordinal, numFields);
  }
  /** 返回 StructInternal 属性。 */
  private InternalRow getStructInternal(int ordinal, int numFields) {
    return new StructInternalRow(
        type.fields().get(ordinal).type().asStructType(), struct.get(ordinal, StructLike.class));
  }
  /** 返回 Array 属性。 */
  @Override
  public ArrayData getArray(int ordinal) {
    return isNullAt(ordinal) ? null : getArrayInternal(ordinal);
  }
  /** 返回 ArrayInternal 属性。 */
  private ArrayData getArrayInternal(int ordinal) {
    return collectionToArrayData(
        type.fields().get(ordinal).type().asListType().elementType(),
        struct.get(ordinal, Collection.class));
  }
  /** 返回 Map 属性。 */
  @Override
  public MapData getMap(int ordinal) {
    return isNullAt(ordinal) ? null : getMapInternal(ordinal);
  }
  /** 返回 MapInternal 属性。 */
  private MapData getMapInternal(int ordinal) {
    return mapToMapData(
        type.fields().get(ordinal).type().asMapType(), struct.get(ordinal, Map.class));
  }
  /** 返回值。 */
  @Override
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public Object get(int ordinal, DataType dataType) {
    if (isNullAt(ordinal)) {
      return null;
    }

    if (dataType instanceof IntegerType) {
      return getInt(ordinal);
    } else if (dataType instanceof LongType) {
      return getLong(ordinal);
    } else if (dataType instanceof StringType) {
      return getUTF8StringInternal(ordinal);
    } else if (dataType instanceof FloatType) {
      return getFloat(ordinal);
    } else if (dataType instanceof DoubleType) {
      return getDouble(ordinal);
    } else if (dataType instanceof DecimalType) {
      DecimalType decimalType = (DecimalType) dataType;
      return getDecimalInternal(ordinal, decimalType.precision(), decimalType.scale());
    } else if (dataType instanceof BinaryType) {
      return getBinaryInternal(ordinal);
    } else if (dataType instanceof StructType) {
      return getStructInternal(ordinal, ((StructType) dataType).size());
    } else if (dataType instanceof ArrayType) {
      return getArrayInternal(ordinal);
    } else if (dataType instanceof MapType) {
      return getMapInternal(ordinal);
    } else if (dataType instanceof BooleanType) {
      return getBoolean(ordinal);
    } else if (dataType instanceof ByteType) {
      return getByte(ordinal);
    } else if (dataType instanceof ShortType) {
      return getShort(ordinal);
    } else if (dataType instanceof DateType) {
      return getInt(ordinal);
    } else if (dataType instanceof TimestampType) {
      return getLong(ordinal);
    }
    return null;
  }
  /** 执行 mapToMapData 相关操作。 */
  private MapData mapToMapData(Types.MapType mapType, Map<?, ?> map) {
    // make a defensive copy to ensure entries do not change
    List<Map.Entry<?, ?>> entries = ImmutableList.copyOf(map.entrySet());
    return new ArrayBasedMapData(
        collectionToArrayData(mapType.keyType(), Lists.transform(entries, Map.Entry::getKey)),
        collectionToArrayData(mapType.valueType(), Lists.transform(entries, Map.Entry::getValue)));
  }
  /** 执行 collectionToArrayData 相关操作。 */
  private ArrayData collectionToArrayData(Type elementType, Collection<?> values) {
    switch (elementType.typeId()) {
      case BOOLEAN:
      case INTEGER:
      case DATE:
      case TIME:
      case LONG:
      case TIMESTAMP:
      case FLOAT:
      case DOUBLE:
        return fillArray(values, array -> (pos, value) -> array[pos] = value);
      case STRING:
        return fillArray(
            values,
            array ->
                (BiConsumer<Integer, CharSequence>)
                    (pos, seq) -> array[pos] = UTF8String.fromString(seq.toString()));
      case FIXED:
      case BINARY:
        return fillArray(
            values,
            array ->
                (BiConsumer<Integer, ByteBuffer>)
                    (pos, buf) -> array[pos] = ByteBuffers.toByteArray(buf));
      case DECIMAL:
        return fillArray(
            values,
            array ->
                (BiConsumer<Integer, BigDecimal>) (pos, dec) -> array[pos] = Decimal.apply(dec));
      case STRUCT:
        return fillArray(
            values,
            array ->
                (BiConsumer<Integer, StructLike>)
                    (pos, tuple) ->
                        array[pos] = new StructInternalRow(elementType.asStructType(), tuple));
      case LIST:
        return fillArray(
            values,
            array ->
                (BiConsumer<Integer, Collection<?>>)
                    (pos, list) ->
                        array[pos] =
                            collectionToArrayData(elementType.asListType().elementType(), list));
      case MAP:
        return fillArray(
            values,
            array ->
                (BiConsumer<Integer, Map<?, ?>>)
                    (pos, map) -> array[pos] = mapToMapData(elementType.asMapType(), map));
      default:
        throw new UnsupportedOperationException("Unsupported array element type: " + elementType);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> GenericArrayData fillArray(
      Collection<?> values, Function<Object[], BiConsumer<Integer, T>> makeSetter) {
    Object[] array = new Object[values.size()];
    BiConsumer<Integer, T> setter = makeSetter.apply(array);

    int index = 0;
    for (Object value : values) {
      if (value == null) {
        array[index] = null;
      } else {
        setter.accept(index, (T) value);
      }

      index += 1;
    }

    return new GenericArrayData(array);
  }
  /** 判断是否相等。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    StructInternalRow that = (StructInternalRow) other;
    return type.equals(that.type) && struct.equals(that.struct);
  }
  /** 返回哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(type, struct);
  }
}
