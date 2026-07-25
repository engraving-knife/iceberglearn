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
package org.apache.iceberg.types;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.util.UUIDUtil;

/**
 * 类型转换工具类：在 Iceberg 类型的 Java 值与字节缓冲区之间相互转换。
 *
 * <p>所属模块：iceberg-api（被 core 的序列化/反序列化、表达式字面量解析、分区值读写等使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 Java 值序列化为 {@link ByteBuffer}（{@link #toByteBuffer}）。
 *   <li>把 {@link ByteBuffer} 反序列化为 Java 值（{@link #fromByteBuffer}）。
 *   <li>把分区字符串解析为 Java 值（{@link #fromPartitionString}），兼容 Hive 的 null 占位符。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>序列化采用小端字节序（除 UUID 与 Decimal 用大端），与 Iceberg 存储规范一致。
 *   <li>字符串编解码使用 ThreadLocal 缓存的 CharsetEncoder/CharsetDecoder，避免反复创建。
 *   <li>反序列化时处理类型提升场景：若 long 类型存储空间不足 8 字节，退化为读取 int 再提升； double 类型同理退化为 float。
 * </ul>
 *
 * <p>上下游关系：被 core 的 Manifest 读写、表达式 {@link Literal}、各引擎的数据读写使用。
 */
public class Conversions {

  private Conversions() {}

  private static final String HIVE_NULL = "__HIVE_DEFAULT_PARTITION__";

  /**
   * 把分区字符串解析为对应类型的 Java 值。
   *
   * <p>逻辑：null 或 Hive 的 "__HIVE_DEFAULT_PARTITION__" 视为 null；其余按 typeId 分派
   * （Boolean/Integer/Long/Float/Double/UUID/Decimal 直接 parse；String 原样返回； Fixed 截断到指定长度；Binary 转
   * UTF-8 字节；Date 通过 Literal 解析）。
   *
   * @param type 目标类型
   * @param asString 分区字符串
   * @return 对应类型的 Java 值；null 返回 null
   * @throws UnsupportedOperationException 不支持的类型
   */
  public static Object fromPartitionString(Type type, String asString) {
    if (asString == null || HIVE_NULL.equals(asString)) {
      return null;
    }

    switch (type.typeId()) {
      case BOOLEAN:
        return Boolean.valueOf(asString);
      case INTEGER:
        return Integer.valueOf(asString);
      case LONG:
        return Long.valueOf(asString);
      case FLOAT:
        return Float.valueOf(asString);
      case DOUBLE:
        return Double.valueOf(asString);
      case STRING:
        return asString;
      case UUID:
        return UUID.fromString(asString);
      case FIXED:
        Types.FixedType fixed = (Types.FixedType) type;
        return Arrays.copyOf(asString.getBytes(StandardCharsets.UTF_8), fixed.length());
      case BINARY:
        return asString.getBytes(StandardCharsets.UTF_8);
      case DECIMAL:
        return new BigDecimal(asString);
      case DATE:
        return Literal.of(asString).to(Types.DateType.get()).value();
      default:
        throw new UnsupportedOperationException(
            "Unsupported type for fromPartitionString: " + type);
    }
  }

  private static final ThreadLocal<CharsetEncoder> ENCODER =
      ThreadLocal.withInitial(StandardCharsets.UTF_8::newEncoder);
  private static final ThreadLocal<CharsetDecoder> DECODER =
      ThreadLocal.withInitial(StandardCharsets.UTF_8::newDecoder);

  /** 按类型把 Java 值序列化为 ByteBuffer。 */
  public static ByteBuffer toByteBuffer(Type type, Object value) {
    return toByteBuffer(type.typeId(), value);
  }

  /**
   * 按类型 ID 把 Java 值序列化为 ByteBuffer。
   *
   * <p>逻辑：null 返回 null；按 typeId 分派——BOOLEAN 编码为 1 字节；INTEGER/DATE 编码为 4 字节小端 int；
   * LONG/TIME/TIMESTAMP 编码为 8 字节小端 long；FLOAT/DOUBLE 编码为小端浮点；STRING 用 UTF-8 编码； UUID 用 UUIDUtil
   * 转换；FIXED/BINARY 直接返回；DECIMAL 用 unscaledValue 的字节数组。
   *
   * @param typeId 类型 ID
   * @param value Java 值
   * @return 序列化后的 ByteBuffer
   * @throws UnsupportedOperationException 不支持的类型
   */
  public static ByteBuffer toByteBuffer(Type.TypeID typeId, Object value) {
    if (value == null) {
      return null;
    }

    switch (typeId) {
      case BOOLEAN:
        return ByteBuffer.allocate(1).put(0, (Boolean) value ? (byte) 0x01 : (byte) 0x00);
      case INTEGER:
      case DATE:
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, (int) value);
      case LONG:
      case TIME:
      case TIMESTAMP:
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(0, (long) value);
      case FLOAT:
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(0, (float) value);
      case DOUBLE:
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(0, (double) value);
      case STRING:
        CharBuffer buffer = CharBuffer.wrap((CharSequence) value);
        try {
          return ENCODER.get().encode(buffer);
        } catch (CharacterCodingException e) {
          throw new RuntimeIOException(e, "Failed to encode value as UTF-8: %s", value);
        }
      case UUID:
        return UUIDUtil.convertToByteBuffer((UUID) value);
      case FIXED:
      case BINARY:
        return (ByteBuffer) value;
      case DECIMAL:
        return ByteBuffer.wrap(((BigDecimal) value).unscaledValue().toByteArray());
      default:
        throw new UnsupportedOperationException("Cannot serialize type: " + typeId);
    }
  }

  /**
   * 把 ByteBuffer 反序列化为对应类型的 Java 值。
   *
   * @param type 目标类型
   * @param buffer 字节缓冲区
   * @param <T> 返回值类型
   * @return 反序列化后的 Java 值；buffer 为 null 返回 null
   */
  @SuppressWarnings("unchecked")
  public static <T> T fromByteBuffer(Type type, ByteBuffer buffer) {
    return (T) internalFromByteBuffer(type, buffer);
  }

  /**
   * ByteBuffer 反序列化的内部实现。
   *
   * <p>逻辑：null 返回 null；复制 buffer 后按类型设置字节序（UUID/Decimal 大端，其余小端）； 按 typeId 分派读取。处理类型提升：LONG 若不足 8
   * 字节则读 int 提升；DOUBLE 若不足 8 字节则读 float 提升。
   *
   * @param type 目标类型
   * @param buffer 字节缓冲区
   * @return 反序列化后的 Java 值
   * @throws UnsupportedOperationException 不支持的类型
   */
  private static Object internalFromByteBuffer(Type type, ByteBuffer buffer) {
    if (buffer == null) {
      return null;
    }

    ByteBuffer tmp = buffer.duplicate();
    if (type == Types.UUIDType.get() || type instanceof Types.DecimalType) {
      tmp.order(ByteOrder.BIG_ENDIAN);
    } else {
      tmp.order(ByteOrder.LITTLE_ENDIAN);
    }
    switch (type.typeId()) {
      case BOOLEAN:
        return tmp.get() != 0x00;
      case INTEGER:
      case DATE:
        return tmp.getInt();
      case LONG:
      case TIME:
      case TIMESTAMP:
        if (tmp.remaining() < 8) {
          // type was later promoted to long
          return (long) tmp.getInt();
        }
        return tmp.getLong();
      case FLOAT:
        return tmp.getFloat();
      case DOUBLE:
        if (tmp.remaining() < 8) {
          // type was later promoted to long
          return (double) tmp.getFloat();
        }
        return tmp.getDouble();
      case STRING:
        try {
          return DECODER.get().decode(tmp);
        } catch (CharacterCodingException e) {
          throw new RuntimeIOException(e, "Failed to decode value as UTF-8: %s", buffer);
        }
      case UUID:
        return UUIDUtil.convert(tmp);
      case FIXED:
      case BINARY:
        return tmp;
      case DECIMAL:
        Types.DecimalType decimal = (Types.DecimalType) type;
        byte[] unscaledBytes = new byte[buffer.remaining()];
        tmp.get(unscaledBytes);
        return new BigDecimal(new BigInteger(unscaledBytes), decimal.scale());
      default:
        throw new UnsupportedOperationException("Cannot deserialize type: " + type);
    }
  }
}
