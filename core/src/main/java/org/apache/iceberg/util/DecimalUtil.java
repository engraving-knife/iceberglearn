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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Decimal 类型工具类，提供 {@link BigDecimal} 与定长字节数组之间的转换。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：将 Decimal 值序列化为固定长度的二进制表示，便于写入 Parquet/ORC 等列存格式， 并支持复用缓冲区以减少对象分配。
 *
 * <p>设计意图：BigDecimal 的 unscaledValue 字节数组长度随数值大小变化，但列存格式要求定长存储。 本类通过按符号位扩展（负数填 0xFF，正数填
 * 0x00）把变长字节补齐到 precision 对应的固定长度， 保证数值顺序与字节序一致，便于排序与范围比较。
 *
 * <p>上下游关系：被 core 的写入器（如 Parquet/ORC value writer）与表达式求值层调用； 依赖 relocated guava 做参数校验。
 */
public class DecimalUtil {
  private DecimalUtil() {}

  /**
   * 将 {@link BigDecimal} 转换为定长字节数组，多余字节按符号位填充。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 scale 与 precision 与传入 decimal 一致，否则抛 IllegalArgumentException；
   *   <li>取 unscaledValue 的字节数组；若长度恰好等于复用缓冲区长度，直接返回该数组；
   *   <li>否则按符号位计算填充字节（负数 0xFF，正数/零 0x00），把高位补齐，低位放原字节， 写入复用缓冲区并返回。
   * </ol>
   *
   * @param precision Decimal 精度（总位数）
   * @param scale Decimal 标度（小数位数）
   * @param decimal 待序列化的 Decimal 值
   * @param reuseBuf 可复用的目标缓冲区，长度需匹配 precision 对应的定长字节数
   * @return 序列化后的字节数组（即 reuseBuf 本身，或 unscaled 原数组）
   */
  public static byte[] toReusedFixLengthBytes(
      int precision, int scale, BigDecimal decimal, byte[] reuseBuf) {
    Preconditions.checkArgument(
        decimal.scale() == scale,
        "Cannot write value as decimal(%s,%s), wrong scale: %s",
        precision,
        scale,
        decimal);
    Preconditions.checkArgument(
        decimal.precision() <= precision,
        "Cannot write value as decimal(%s,%s), too large: %s",
        precision,
        scale,
        decimal);

    byte[] unscaled = decimal.unscaledValue().toByteArray();
    if (unscaled.length == reuseBuf.length) {
      return unscaled;
    }

    byte fillByte = (byte) (decimal.signum() < 0 ? 0xFF : 0x00);
    int offset = reuseBuf.length - unscaled.length;

    for (int i = 0; i < reuseBuf.length; i += 1) {
      if (i < offset) {
        reuseBuf[i] = fillByte;
      } else {
        reuseBuf[i] = unscaled[i - offset];
      }
    }

    return reuseBuf;
  }
}
