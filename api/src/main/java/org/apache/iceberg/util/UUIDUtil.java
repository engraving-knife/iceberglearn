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
package org.apache.iceberg.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * UUID 工具类：在 {@link UUID} 与 16 字节（大端序）表示之间进行转换。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 16 字节数组或 {@link ByteBuffer} 转换为 {@link UUID}。
 *   <li>将 {@link UUID} 转换为 16 字节数组或 {@link ByteBuffer}（支持复用已有 buffer）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>统一使用大端序（{@link ByteOrder#BIG_ENDIAN}）：保证 UUID 的字节表示跨平台一致， 这是 Iceberg 存储 UUID 类型时的序列化约定。
 *   <li>提供 {@code reuse} 重载允许复用 ByteBuffer，减少高频率场景下的对象分配。
 *   <li>带 offset 的转换支持从更大字节数组中就地解析，避免切片拷贝。
 * </ul>
 *
 * <p>上下游关系：被 core 模块的 UUID 类型读写、字面量解析等逻辑调用。
 */
public class UUIDUtil {
  private UUIDUtil() {}

  /**
   * 将 16 字节数组转换为 {@link UUID}（大端序）。
   *
   * @param buf 16 字节数组
   * @return 对应的 UUID
   * @throws IllegalArgumentException 若数组长度不为 16
   */
  public static UUID convert(byte[] buf) {
    Preconditions.checkArgument(buf.length == 16, "UUID require 16 bytes");
    ByteBuffer bb = ByteBuffer.wrap(buf);
    bb.order(ByteOrder.BIG_ENDIAN);
    return convert(bb);
  }

  /**
   * 将字节数组中从指定偏移起的 16 字节转换为 {@link UUID}（大端序）。
   *
   * @param buf 字节数组
   * @param offset 起始偏移
   * @return 对应的 UUID
   * @throws IllegalArgumentException 若 offset 越界或剩余字节不足 16
   */
  public static UUID convert(byte[] buf, int offset) {
    Preconditions.checkArgument(
        offset >= 0 && offset < buf.length,
        "Offset overflow, offset=%s, length=%s",
        offset,
        buf.length);
    Preconditions.checkArgument(
        offset + 16 <= buf.length,
        "UUID require 16 bytes, offset=%s, length=%s",
        offset,
        buf.length);

    ByteBuffer bb = ByteBuffer.wrap(buf, offset, 16);
    bb.order(ByteOrder.BIG_ENDIAN);
    return convert(bb);
  }

  /**
   * 将 {@link ByteBuffer} 中的 16 字节转换为 {@link UUID}（按当前 buffer 字节序读取，调用方 需保证为大端序）。
   *
   * @param buf 字节缓冲，需至少剩余 16 字节
   * @return 对应的 UUID
   */
  public static UUID convert(ByteBuffer buf) {
    long mostSigBits = buf.getLong();
    long leastSigBits = buf.getLong();

    return new UUID(mostSigBits, leastSigBits);
  }

  /**
   * 将 {@link UUID} 转换为 16 字节数组（大端序）。
   *
   * @param value 待转换的 UUID
   * @return 16 字节数组
   */
  public static byte[] convert(UUID value) {
    return convertToByteBuffer(value).array();
  }

  /**
   * 将 {@link UUID} 转换为新分配的 16 字节 {@link ByteBuffer}（大端序）。
   *
   * @param value 待转换的 UUID
   * @return 包含 16 字节的堆 buffer
   */
  public static ByteBuffer convertToByteBuffer(UUID value) {
    return convertToByteBuffer(value, null);
  }

  /**
   * 将 {@link UUID} 写入 {@link ByteBuffer}（大端序），可复用已有 buffer。
   *
   * <p>逻辑：若 reuse 非 null 则直接使用，否则新分配 16 字节堆 buffer；设置大端序后， 将 UUID 的最高 64 位写入偏移 0、最低 64 位写入偏移 8。
   *
   * @param value 待转换的 UUID
   * @param reuse 可复用的 buffer，为 null 则新建
   * @return 写入后的 buffer
   */
  public static ByteBuffer convertToByteBuffer(UUID value, ByteBuffer reuse) {
    ByteBuffer buffer;
    if (reuse != null) {
      buffer = reuse;
    } else {
      buffer = ByteBuffer.allocate(16);
    }

    buffer.order(ByteOrder.BIG_ENDIAN);
    buffer.putLong(0, value.getMostSignificantBits());
    buffer.putLong(8, value.getLeastSignificantBits());
    return buffer;
  }
}
