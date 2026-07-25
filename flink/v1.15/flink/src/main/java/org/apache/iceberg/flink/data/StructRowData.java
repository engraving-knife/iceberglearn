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
 * 把 Iceberg {@link StructLike} 包装为 Flink {@link RowData} 的适配器。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：让 Iceberg 内部 StructLike 数据可被 Flink Table API 直接读取， 在各 getter
 * 方法中按类型完成 Java 对象到 Flink 数据类型的转换。
 *
 * <p>设计意图：适配器模式，把 StructLike 适配为 RowData；上下游：被读取算子/投影逻辑调用。
 */
@Internal
public class StructRowData implements RowData {
  private final Types.StructType type;
  private RowKind kind;
  private StructLike struct;

  /** 构造函数，默认 INSERT 行类型。 */
  public StructRowData(Types.StructType type) {
    this(type, RowKind.INSERT);
  }

  /** 构造函数，指定行类型。 */
  public StructRowData(Types.StructType type, RowKind kind) {
    this(type, null, kind);
  }

  /** 私有构造函数，携带 StructLike。 */
  private StructRowData(Types.StructType type, StructLike struct) {
    this(type, struct, RowKind.INSERT);
  }

  /** 私有构造函数，完整参数。 */
  private StructRowData(Types.StructType type, StructLike struct, RowKind kind) {
    this.type = type;
    this.struct = struct;
    this.kind = kind;
  }

  /** 设置底层 StructLike 并返回自身。 */
  public StructRowData setStruct(StructLike newStruct) {
    this.struct = newStruct;
    return this;
  }

  /** 返回字段数。 */
  @Override
  public int getArity() {
    return struct.size();
  }

  /** 返回行类型。 */
  @Override
  public RowKind getRowKind() {
    return kind;
  }

  /** 设置行类型。 */
  @Override
  public void setRowKind(RowKind newKind) {
    Preconditions.checkNotNull(newKind, "kind can not be null");
    this.kind = newKind;
  }

  /** 判断指定位置是否为 null。 */
  @Override
  public boolean isNullAt(int pos) {
    return struct.get(pos, Object.class) == null;
  }

  /** 读取 boolean。 */
  @Override
  public boolean getBoolean(int pos) {
    return struct.get(pos, Boolean.class);
  }

  /** 读取 byte（Integer 强转）。 */
  @Override
  public byte getByte(int pos) {
    return (byte) (int) struct.get(pos, Integer.class);
  }

  /** 读取 short（Integer 强转）。 */
  @Override
  public short getShort(int pos) {
    return (short) (int) struct.get(pos, Integer.class);
  }

  /** 读取 int，支持 Integer/LocalDate/LocalTime 类型转换。 */
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

  /** 读取 long，支持 Long/OffsetDateTime/LocalDate/LocalTime/LocalDateTime 类型转换。 */
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

  /** 读取 float。 */
  @Override
  public float getFloat(int pos) {
    return struct.get(pos, Float.class);
  }

  /** 读取 double。 */
  @Override
  public double getDouble(int pos) {
    return struct.get(pos, Double.class);
  }

  /** 读取字符串（先判 null）。 */
  @Override
  public StringData getString(int pos) {
    return isNullAt(pos) ? null : getStringDataInternal(pos);
  }

  /** 内部方法：从 CharSequence 构造 StringData。 */
  private StringData getStringDataInternal(int pos) {
    CharSequence seq = struct.get(pos, CharSequence.class);
    return StringData.fromString(seq.toString());
  }

  /** 读取 Decimal（先判 null）。 */
  @Override
  public DecimalData getDecimal(int pos, int precision, int scale) {
    return isNullAt(pos)
        ? null
        : DecimalData.fromBigDecimal(getDecimalInternal(pos), precision, scale);
  }

  /** 内部方法：读取 BigDecimal。 */
  private BigDecimal getDecimalInternal(int pos) {
    return struct.get(pos, BigDecimal.class);
  }

  /** 读取时间戳，从 long 微秒转 TimestampData。 */
  @Override
  public TimestampData getTimestamp(int pos, int precision) {
    long timeLong = getLong(pos);
    return TimestampData.fromEpochMillis(timeLong / 1000, (int) (timeLong % 1000) * 1000);
  }

  /** 未实现。 */
  @Override
  public <T> RawValueData<T> getRawValue(int pos) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  /** 读取二进制（先判 null），支持 ByteBuffer/byte[]/UUID。 */
  @Override
  public byte[] getBinary(int pos) {
    return isNullAt(pos) ? null : getBinaryInternal(pos);
  }

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

  /** 读取数组（先判 null），按 list 类型转换。 */
  @Override
  public ArrayData getArray(int pos) {
    return isNullAt(pos)
        ? null
        : (ArrayData)
            convertValue(type.fields().get(pos).type().asListType(), struct.get(pos, List.class));
  }

  /** 读取 map（先判 null），按 map 类型转换。 */
  @Override
  public MapData getMap(int pos) {
    return isNullAt(pos)
        ? null
        : (MapData)
            convertValue(type.fields().get(pos).type().asMapType(), struct.get(pos, Map.class));
  }

  /** 读取嵌套 struct（先判 null）。 */
  @Override
  public RowData getRow(int pos, int numFields) {
    return isNullAt(pos) ? null : getStructRowData(pos, numFields);
  }

  /** 内部方法：构造嵌套 StructRowData。 */
  private StructRowData getStructRowData(int pos, int numFields) {
    return new StructRowData(
        type.fields().get(pos).type().asStructType(), struct.get(pos, StructLike.class));
  }

  /**
   * 按 Iceberg 类型把 Java 对象转换为 Flink 数据类型。
   *
   * <p>逻辑：按 elementType.typeId() 分派到 boolean/int/date/string/binary/struct/list/map 等转换逻辑。
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
