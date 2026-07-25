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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.iceberg.relocated.com.google.common.hash.HashFunction;
import org.apache.iceberg.relocated.com.google.common.hash.Hashing;

/**
 * 桶分（bucket）分区转换的哈希工具类：为各类 Iceberg 支持的数据类型提供统一的 32 位哈希实现， 用于 {@code bucket} 分区转换计算分桶号。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对 int/long/float/double/CharSequence/ByteBuffer/UUID/BigDecimal 等类型计算 Murmur3_32 哈希。
 *   <li>保证同一逻辑值的不同表示（如 -0.0 与 0.0）产生相同哈希，确保分桶稳定。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>统一使用 {@link Hashing#murmur3_32_fixed()}（定点 Murmur3_32）：分布均匀且跨平台一致，
 *       保证不同引擎/环境下分桶结果可重现，这是分布式系统正确性的关键。
 *   <li>浮点数先经 {@link #doubleToLongBits} 规范化：将 -0.0 的位模式（Long.MIN_VALUE）映射为 0，使 IEEE 754 中相等的
 *       0.0/-0.0 哈希一致。
 *   <li>ByteBuffer 哈希优先走 {@code hasArray()} 零拷贝路径，否则只读拷贝并恢复 position， 避免副作用。
 *   <li>UUID 采用大端字节序（Long.reverseBytes）写入，保证跨端序一致性。
 * </ul>
 *
 * <p>上下游关系：被 core 模块的 bucket 转换实现（{@code Bucket}）调用，根据字段值计算分桶号。
 */
public class BucketUtil {

  private static final HashFunction MURMUR3 = Hashing.murmur3_32_fixed();

  private BucketUtil() {}

  /**
   * 计算 int 值的 Murmur3_32 哈希。
   *
   * @param value 待哈希的 int 值
   * @return 32 位哈希值
   */
  public static int hash(int value) {
    return MURMUR3.hashLong((long) value).asInt();
  }

  /**
   * 计算 long 值的 Murmur3_32 哈希。
   *
   * @param value 待哈希的 long 值
   * @return 32 位哈希值
   */
  public static int hash(long value) {
    return MURMUR3.hashLong(value).asInt();
  }

  /**
   * 将 double 规范化为 long 位模式，处理 -0.0 与 0.0 等价问题。
   *
   * <p>逻辑：取 double 的 long 位模式；若为 Long.MIN_VALUE（即 -0.0 的位模式）则改为 0， 使 IEEE 754 中相等的 0.0 与 -0.0
   * 产生相同哈希。
   *
   * @param value 待规范化的 double
   * @return 规范化后的 long 位模式
   */
  private static long doubleToLongBits(double value) {
    long bits = Double.doubleToLongBits(value);

    // Change negative zero (-0.0) to positive zero (0.0). As IEEE 754
    // mandates 0.0 == -0.0, both should also produce the same hash value.
    if (bits == Long.MIN_VALUE) {
      bits = 0L;
    }

    return bits;
  }

  /**
   * 计算 float 值的 Murmur3_32 哈希（先提升为 double 并规范化）。
   *
   * @param value 待哈希的 float 值
   * @return 32 位哈希值
   */
  public static int hash(float value) {
    return MURMUR3.hashLong(doubleToLongBits((double) value)).asInt();
  }

  /**
   * 计算 double 值的 Murmur3_32 哈希（经 -0.0 规范化）。
   *
   * @param value 待哈希的 double 值
   * @return 32 位哈希值
   */
  public static int hash(double value) {
    return MURMUR3.hashLong(doubleToLongBits(value)).asInt();
  }

  /**
   * 计算 {@link CharSequence} 的 Murmur3_32 哈希（UTF-8 编码）。
   *
   * @param value 待哈希的字符序列
   * @return 32 位哈希值
   */
  public static int hash(CharSequence value) {
    return MURMUR3.hashString(value, StandardCharsets.UTF_8).asInt();
  }

  /**
   * 计算 {@link ByteBuffer} 的 Murmur3_32 哈希。
   *
   * <p>逻辑：若 buffer 有底层数组，则直接基于数组段计算（零拷贝）；否则只读拷贝剩余字节到 新数组计算，并在 finally 中恢复原 buffer 的 position，避免副作用。
   *
   * @param value 待哈希的字节缓冲
   * @return 32 位哈希值
   */
  public static int hash(ByteBuffer value) {
    if (value.hasArray()) {
      return MURMUR3
          .hashBytes(value.array(), value.arrayOffset() + value.position(), value.remaining())
          .asInt();
    } else {
      int position = value.position();
      byte[] copy = new byte[value.remaining()];
      try {
        value.get(copy);
      } finally {
        // make sure the buffer position is unchanged
        value.position(position);
      }
      return MURMUR3.hashBytes(copy).asInt();
    }
  }

  /**
   * 计算 {@link UUID} 的 Murmur3_32 哈希。
   *
   * <p>逻辑：将 UUID 的最高 64 位与最低 64 位按大端序（{@link Long#reverseBytes}）写入 16 字节 哈希器，确保跨端序一致性。
   *
   * @param value 待哈希的 UUID
   * @return 32 位哈希值
   */
  public static int hash(UUID value) {
    return MURMUR3
        .newHasher(16)
        .putLong(Long.reverseBytes(value.getMostSignificantBits()))
        .putLong(Long.reverseBytes(value.getLeastSignificantBits()))
        .hash()
        .asInt();
  }

  /**
   * 计算 {@link BigDecimal} 的 Murmur3_32 哈希。
   *
   * <p>逻辑：基于 BigDecimal 的未缩放值（unscaledValue）的字节数组计算哈希，使数值相等但 scale 不同的 BigDecimal
   * 仍可能产生不同哈希——这是有意为之，因为 Iceberg 中 BigDecimal 的精度/scale 是类型的一部分。
   *
   * @param value 待哈希的 BigDecimal
   * @return 32 位哈希值
   */
  public static int hash(BigDecimal value) {
    return MURMUR3.hashBytes(value.unscaledValue().toByteArray()).asInt();
  }
}
