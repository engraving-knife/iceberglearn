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
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.flink.annotation.Internal;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.DecimalData;
import org.apache.flink.table.data.GenericArrayData;
import org.apache.flink.table.data.GenericMapData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RawValueData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.types.RowKind;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;

/**
 * 将 Iceberg {@link StructLike} 适配为 Flink {@link RowData} 的包装器。
 *
 * <p>所属模块：iceberg-flink，位于数据读取/转换层。
 *
 * <p>职责：把 Iceberg 内部基于 {@link StructLike} 的行数据按 Flink {@link RowData} 接口暴露，
 * 支持各类基本类型与复合类型（list/map/struct）的取值，并做必要的类型/单位转换。
 *
 * <p>设计意图：避免数据拷贝，通过包装复用底层 StructLike；针对 Iceberg 与 Flink 在日期/时间/时间戳 表示上的差异（如 LocalTime 纳秒 vs 毫秒、时间戳
 * epoch 微秒）在 getter 中就地转换。
 *
 * <p>上下游关系：被 Flink 读取链路中需要把 Iceberg 内部行转为 RowData 的场景使用。
 */
@Internal
public class StructRowData implements RowData {
  private final Types.StructType type;
  private RowKind kind;
  private StructLike struct;

  /** 构造包装器，默认行类型为 {@link RowKind#INSERT}。 */
  public StructRowData(Types.StructType type) {
    this(type, RowKind.INSERT);
  }

  /** 构造包装器并指定行类型（用于 changelog 场景）。 */
  public StructRowData(Types.StructType type, RowKind kind) {
    this(type, null, kind);
  }

  private StructRowData(Types.StructType type, StructLike struct) {
    this(type, struct, RowKind.INSERT);
  }

  private StructRowData(Types.StructType type, StructLike struct, RowKind kind) {
    this.type = type;
    this.struct = struct;
    this.kind = kind;
  }

  /**
   * 设置底层 StructLike 并返回自身，便于链式调用。
   *
   * @param newStruct 新的底层行数据
   * @return 当前对象
   */
  public StructRowData setStruct(StructLike newStruct) {
    this.struct = newStruct;
    return this;
  }

  /** 返回字段数量。 */
  @Override
  public int getArity() {
    return struct.size();
  }

  /** 返回行类型（INSERT/UPDATE_BEFORE/UPDATE_AFTER/DELETE）。 */
  @Override
  public RowKind getRowKind() {
    return kind;
  }

  /** 设置行类型，不允许为 null。 */
  @Override
  public void setRowKind(RowKind newKind) {
    Preconditions.checkNotNull(newKind, "kind can not be null");
    this.kind = newKind;
  }

  /** 判断指定位置字段是否为 null。 */
  @Override
  public boolean isNullAt(int pos) {
    return struct.get(pos, Object.class) == null;
  }

  /** 读取布尔字段。 */
  @Override
  public boolean getBoolean(int pos) {
    return struct.get(pos, Boolean.class);
  }

  /** 读取字节字段（底层以 Integer 存储，强转）。 */
  @Override
  public byte getByte(int pos) {
    return (byte) (int) struct.get(pos, Integer.class);
  }

  /** 读取短整数字段（底层以 Integer 存储，强转）。 */
  @Override
  public short getShort(int pos) {
    return (short) (int) struct.get(pos, Integer.class);
  }

  /**
   * 读取整数字段。
   *
   * <p>逻辑：底层值可能为 Integer、LocalDate（日期，转 epoch 天数）或 LocalTime（时间，转毫秒）， 按运行时类型分发转换；其余类型抛出异常。
   */
  @Override
  public int getInt(int pos) {
    Object integer = struct.get(pos, Object.class);

    if (integer instanceof Integer) {
      return (int) integer;
    } else if (integer instanceof LocalDate) {
      return (int) ((LocalDate) integer).toEpochDay();
    } else if (integer instanceof LocalTime) {
      return (int) (((LocalTime) integer).toNanoOfDay() / 1000_000);
    } else {
      throw new IllegalStateException(
          "Unknown type for int field. Type name: " + integer.getClass().getName());
    }
  }

  /**
   * 读取长整数字段。
   *
   * <p>逻辑：底层值可能为 Long、OffsetDateTime、LocalDate、LocalTime、LocalDateTime， 分别转换为 epoch
   * 微秒/天数/纳秒等长整型表示；其余类型抛出异常。
   */
  @Override
  public long getLong(int pos) {
    Object longVal = struct.get(pos, Object.class);

    if (longVal instanceof Long) {
      return (long) longVal;
    } else if (longVal instanceof OffsetDateTime) {
      return Duration.between(Instant.EPOCH, (OffsetDateTime) longVal).toNanos() / 1000;
    } else if (longVal instanceof LocalDate) {
      return ((LocalDate) longVal).toEpochDay();
    } else if (longVal instanceof LocalTime) {
      return ((LocalTime) longVal).toNanoOfDay();
    } else if (longVal instanceof LocalDateTime) {
      return Duration.between(Instant.EPOCH, ((LocalDateTime) longVal).atOffset(ZoneOffset.UTC))
              .toNanos()
          / 1000;
    } else {
      throw new IllegalStateException(
          "Unknown type for long field. Type name: " + longVal.getClass().getName());
    }
  }

  /** 读取浮点字段。 */
  @Override
  public float getFloat(int pos) {
    return struct.get(pos, Float.class);
  }

  /** 读取双精度字段。 */
  @Override
  public double getDouble(int pos) {
    return struct.get(pos, Double.class);
  }

  /** 读取字符串字段，null 时返回 null。 */
  @Override
  public StringData getString(int pos) {
    return isNullAt(pos) ? null : getStringDataInternal(pos);
  }

  /** 内部读取字符串：将 CharSequence 转为 Flink {@link StringData}。 */
  private StringData getStringDataInternal(int pos) {
    CharSequence seq = struct.get(pos, CharSequence.class);
    return StringData.fromString(seq.toString());
  }

  /** 读取十进制字段，按指定精度与标度从 BigDecimal 转换。 */
  @Override
  public DecimalData getDecimal(int pos, int precision, int scale) {
    return isNullAt(pos)
        ? null
        : DecimalData.fromBigDecimal(getDecimalInternal(pos), precision, scale);
  }

  /** 内部读取十进制底层 BigDecimal。 */
  private BigDecimal getDecimalInternal(int pos) {
    return struct.get(pos, BigDecimal.class);
  }

  /**
   * 读取时间戳字段。
   *
   * <p>逻辑：底层以 epoch 微秒（long）存储，拆分为毫秒与微秒余量构造 {@link TimestampData}。
   */
  @Override
  public TimestampData getTimestamp(int pos, int precision) {
    long timeLong = getLong(pos);
    return TimestampData.fromEpochMillis(timeLong / 1000, (int) (timeLong % 1000) * 1000);
  }

  /** 读取原始值字段，当前不支持。 */
  @Override
  public <T> RawValueData<T> getRawValue(int pos) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  /** 读取二进制字段，null 时返回 null。 */
  @Override
  public byte[] getBinary(int pos) {
    return isNullAt(pos) ? null : getBinaryInternal(pos);
  }

  /**
   * 内部读取二进制。
   *
   * <p>逻辑：底层值可能为 ByteBuffer、byte[] 或 UUID（转为 16 字节），按类型分发转换； 其余类型抛出异常。
   */
  private byte[] getBinaryInternal(int pos) {
    Object bytes = struct.get(pos, Object.class);

    // should only be either ByteBuffer or byte[]
    if (bytes instanceof ByteBuffer) {
      return ByteBuffers.toByteArray((ByteBuffer) bytes);
    } else if (bytes instanceof byte[]) {
      return (byte[]) bytes;
    } else if (bytes instanceof UUID) {
      UUID uuid = (UUID) bytes;
      ByteBuffer bb = ByteBuffer.allocate(16);
      bb.putLong(uuid.getMostSignificantBits());
      bb.putLong(uuid.getLeastSignificantBits());
      return bb.array();
    } else {
      throw new IllegalStateException(
          "Unknown type for binary field. Type name: " + bytes.getClass().getName());
    }
  }

  /** 读取数组字段，按 Iceberg list 类型转换。 */
  @Override
  public ArrayData getArray(int pos) {
    return isNullAt(pos)
        ? null
        : (ArrayData)
            convertValue(type.fields().get(pos).type().asListType(), struct.get(pos, List.class));
  }

  /** 读取 map 字段，按 Iceberg map 类型转换。 */
  @Override
  public MapData getMap(int pos) {
    return isNullAt(pos)
        ? null
        : (MapData)
            convertValue(type.fields().get(pos).type().asMapType(), struct.get(pos, Map.class));
  }

  /** 读取嵌套 struct 字段，包装为新的 StructRowData。 */
  @Override
  public RowData getRow(int pos, int numFields) {
    return isNullAt(pos) ? null : getStructRowData(pos, numFields);
  }

  /** 内部构造嵌套 StructRowData。 */
  private StructRowData getStructRowData(int pos, int numFields) {
    return new StructRowData(
        type.fields().get(pos).type().asStructType(), struct.get(pos, StructLike.class));
  }

  /**
   * 将 Iceberg 内部值按目标元素类型转换为 Flink 数据结构。
   *
   * <p>逻辑：按 {@code elementType.typeId()} 分发——基本类型直接返回或简单包装； TIMESTAMP 拆分毫秒/微秒；STRING 转
   * StringData；FIXED/BINARY 转 byte[]； STRUCT 包装为 StructRowData；LIST 逐元素递归转 GenericArrayData； MAP
   * 逐键值递归转 GenericMapData；其余类型抛出异常。
   *
   * @param elementType Iceberg 元素类型
   * @param value 底层值
   * @return Flink 数据结构
   */
  private Object convertValue(Type elementType, Object value) {
    switch (elementType.typeId()) {
      case BOOLEAN:
      case INTEGER:
      case DATE:
      case TIME:
      case LONG:
      case FLOAT:
      case DOUBLE:
      case DECIMAL:
        return value;
      case TIMESTAMP:
        long millisecond = (long) value / 1000;
        int nanoOfMillisecond = (int) ((Long) value % 1000) * 1000;
        return TimestampData.fromEpochMillis(millisecond, nanoOfMillisecond);
      case STRING:
        return StringData.fromString(value.toString());
      case FIXED:
      case BINARY:
        return ByteBuffers.toByteArray((ByteBuffer) value);
      case STRUCT:
        return new StructRowData(elementType.asStructType(), (StructLike) value);
      case LIST:
        List<Object> list = (List<Object>) value;
        Object[] array = new Object[list.size()];

        int index = 0;
        for (Object element : list) {
          if (element == null) {
            array[index] = null;
          } else {
            array[index] = convertValue(elementType.asListType().elementType(), element);
          }

          index += 1;
        }
        return new GenericArrayData(array);
      case MAP:
        Types.MapType mapType = elementType.asMapType();
        Set<? extends Map.Entry<?, ?>> entries = ((Map<?, ?>) value).entrySet();
        Map<Object, Object> result = Maps.newHashMap();
        for (Map.Entry<?, ?> entry : entries) {
          final Object keyValue = convertValue(mapType.keyType(), entry.getKey());
          final Object valueValue = convertValue(mapType.valueType(), entry.getValue());
          result.put(keyValue, valueValue);
        }

        return new GenericMapData(result);
      default:
        throw new UnsupportedOperationException("Unsupported element type: " + elementType);
    }
  }
}
