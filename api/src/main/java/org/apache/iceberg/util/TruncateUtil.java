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
import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * 数值截断工具类：为各类数值类型提供 {@code truncate} 分区转换的核心计算逻辑。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：对 byte/short/int/long/BigDecimal 等数值按指定宽度向下取整到分段边界。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>复用核心算法：本类供 {@code org.apache.iceberg.transforms.Truncate} 与各引擎定义的 SQL
 *       函数复用，因不同调用方对输入校验时机不同，本类<b>不做输入校验</b>，由调用方保证 width 为正、value 非 null。
 *   <li>统一公式 {@code value - (((value % width) + width) % width)}：通过两次取模把 Java 的
 *       "向零取整"余数转换为"向下取整"余数，从而正确处理负值，使截断结果始终落在分段边界上。
 *   <li>BigDecimal 使用未缩放值（unscaledValue）做 BigInteger 取模，保持 scale 不变。
 * </ul>
 *
 * <p>上下游关系：被 core 模块的 Truncate 转换及引擎 SQL 截断函数调用。另见 {@link UnicodeUtil#truncateString(CharSequence,
 * int)} 与 {@link BinaryUtil#truncateBinaryUnsafe(ByteBuffer, int)} 用于字符串与字节缓冲的截断。
 */
public class TruncateUtil {

  private TruncateUtil() {}

  /**
   * 按 width 将 byte 值向下截断到分段边界。
   *
   * @param width 分段宽度，调用方保证为正
   * @param value 待截断值，调用方保证非 null
   * @return 截断后的值
   */
  public static byte truncateByte(int width, byte value) {
    return (byte) (value - (((value % width) + width) % width));
  }

  /**
   * 按 width 将 short 值向下截断到分段边界。
   *
   * @param width 分段宽度，调用方保证为正
   * @param value 待截断值，调用方保证非 null
   * @return 截断后的值
   */
  public static short truncateShort(int width, short value) {
    return (short) (value - (((value % width) + width) % width));
  }

  /**
   * 按 width 将 int 值向下截断到分段边界。
   *
   * @param width 分段宽度，调用方保证为正
   * @param value 待截断值，调用方保证非 null
   * @return 截断后的值
   */
  public static int truncateInt(int width, int value) {
    return value - (((value % width) + width) % width);
  }

  /**
   * 按 width 将 long 值向下截断到分段边界。
   *
   * @param width 分段宽度，调用方保证为正
   * @param value 待截断值，调用方保证非 null
   * @return 截断后的值
   */
  public static long truncateLong(int width, long value) {
    return value - (((value % width) + width) % width);
  }

  /**
   * 按 unscaledWidth 将 BigDecimal 向下截断到分段边界。
   *
   * <p>逻辑：对未缩放值做 BigInteger 取模（同样用两次取模处理负值），构造同 scale 的余数 BigDecimal，再用原值减去余数得到截断结果。
   *
   * @param unscaledWidth 未缩放的宽度（BigInteger）
   * @param value 待截断的 BigDecimal，调用方保证非 null
   * @return 截断后的 BigDecimal，scale 与原值相同
   */
  public static BigDecimal truncateDecimal(BigInteger unscaledWidth, BigDecimal value) {
    BigDecimal remainder =
        new BigDecimal(
            value
                .unscaledValue()
                .remainder(unscaledWidth)
                .add(unscaledWidth)
                .remainder(unscaledWidth),
            value.scale());

    return value.subtract(remainder);
  }
}
