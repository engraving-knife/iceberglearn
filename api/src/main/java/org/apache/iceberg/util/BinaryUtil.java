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
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 二进制（字节缓冲）截断工具类：为 binary/string 类型的截断转换（truncate transform）和 统计信息裁剪提供底层字节操作。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按指定长度截断 {@link ByteBuffer}，支持安全拷贝与零拷贝两种模式。
 *   <li>计算截断后用于"下界/上界"判定的最小/最大字节字面量，支撑分区裁剪时的范围推导。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>允许 length=0：用于评估空字符串行；但分区规范不允许 length=0（由 MetricsModes 解析时约束）。
 *   <li>{@link #truncateBinaryUnsafe} 不拷贝数据，仅通过调整 limit 复用原 buffer 视图， 用于性能敏感且调用方保证不修改原 buffer 的场景。
 *   <li>{@link #truncateBinaryMax} 通过从末尾逐字节自增寻找上界，处理全 0xFF 溢出返回 null 表示无有效上界。
 * </ul>
 *
 * <p>上下游关系：被 core 模块的 truncate 转换实现及统计信息裁剪逻辑调用。
 */
public class BinaryUtil {
  // not meant to be instantiated
  private BinaryUtil() {}

  private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

  /**
   * 将输入字节缓冲截断到指定长度（含拷贝）。
   *
   * <p>逻辑：length=0 返回空 buffer；length 大于等于剩余字节数时原样返回；否则复制前 {@code length} 字节到新数组并包装为 ByteBuffer
   * 返回。允许 length=0 以支持空字符串行评估， 但分区规范不允许 length=0（受 {@code org.apache.iceberg.MetricsModes} 解析约束）。
   *
   * @param input 待截断的字节缓冲
   * @param length 非负的截断长度
   * @return 截断后的字节缓冲
   */
  public static ByteBuffer truncateBinary(ByteBuffer input, int length) {
    Preconditions.checkArgument(length >= 0, "Truncate length should be non-negative");
    if (length == 0) {
      return EMPTY_BYTE_BUFFER;
    } else if (length >= input.remaining()) {
      return input;
    }
    byte[] array = new byte[length];
    input.duplicate().get(array);
    return ByteBuffer.wrap(array);
  }

  /**
   * 将输入字节缓冲截断到指定长度（零拷贝，不复制数据）。
   *
   * <p>逻辑：复制 buffer 视图（duplicate 共享底层数据），调整 limit 为 {@code min(原limit, position+width)}，返回该视图。与
   * {@link #truncateBinary} 不同， 本方法不拷贝数据，性能更高但要求调用方不修改底层内容。
   *
   * @param value 待截断的字节缓冲
   * @param width 非负的截断长度
   * @return 共享底层数据的截断视图
   */
  public static ByteBuffer truncateBinaryUnsafe(ByteBuffer value, int width) {
    ByteBuffer ret = value.duplicate();
    ret.limit(Math.min(value.limit(), value.position() + width));
    return ret;
  }

  /**
   * 返回一个长度不超过 {@code length} 且小于等于输入的字节字面量，作为截断后的最小下界。
   *
   * <p>逻辑：若 length 大于等于输入剩余字节数，直接返回输入；否则返回截断后的字面量。
   *
   * @param input 输入字节字面量
   * @param length 截断长度
   * @return 截断后的下界字面量
   */
  public static Literal<ByteBuffer> truncateBinaryMin(Literal<ByteBuffer> input, int length) {
    ByteBuffer inputBuffer = input.value();
    if (length >= inputBuffer.remaining()) {
      return input;
    }
    return Literal.of(truncateBinary(inputBuffer, length));
  }

  /**
   * 返回一个长度不超过 {@code length} 且大于输入的字节字面量，作为截断后的最大上界。
   *
   * <p>逻辑：先截断输入到 length；从末尾逐字节自增，若自增后未溢出（不为 0，即未回绕到 0） 则把该字节及之前部分作为上界返回；若所有字节均溢出则返回 null（表示无有效上界，例如
   * 全 0xFF 的情况）。
   *
   * @param input 输入字节字面量
   * @param length 截断长度
   * @return 截断后的上界字面量；若无有效上界则返回 null
   */
  public static Literal<ByteBuffer> truncateBinaryMax(Literal<ByteBuffer> input, int length) {
    ByteBuffer inputBuffer = input.value();
    if (length >= inputBuffer.remaining()) {
      return input;
    }

    // Truncate the input to the specified truncate length.
    ByteBuffer truncatedInput = truncateBinary(inputBuffer, length);

    // Try incrementing the bytes from the end. If all bytes overflow after incrementing, then
    // return null
    for (int i = length - 1; i >= 0; --i) {
      byte element = truncatedInput.get(i);
      element = (byte) (element + 1);
      if (element != 0) { // No overflow
        truncatedInput.put(i, element);
        // Return a byte buffer whose position is zero and limit is i + 1
        truncatedInput.position(0);
        truncatedInput.limit(i + 1);
        return Literal.of(truncatedInput);
      }
    }
    return null; // Cannot find a valid upper bound
  }
}
