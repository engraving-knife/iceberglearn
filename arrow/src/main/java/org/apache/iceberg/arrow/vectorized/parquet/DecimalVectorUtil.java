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
package org.apache.iceberg.arrow.vectorized.parquet;

import java.util.Arrays;
import org.apache.arrow.vector.DecimalVector;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;

/** 文件级说明：Decimal 向量写入工具，处理 Parquet 大端字节与 Arrow 原生字节序的转换与填充。所属模块：iceberg-arrow 的 parquet 子包。 */
public class DecimalVectorUtil {

  private DecimalVectorUtil() {}

  /**
   * 以大端字节序将 Decimal 值写入向量指定位置，写入前先填充到 16 字节。
   *
   * <p>设计意图：预先填充可避免 Arrow 内部 setBigEndian 调用 Unsafe.setMemory 的开销。
   *
   * @param vector 目标 Decimal 向量
   * @param idx 行下标
   * @param value 大端字节
   */
  public static void setBigEndian(DecimalVector vector, int idx, byte[] value) {
    byte[] paddedBytes = DecimalVectorUtil.padBigEndianBytes(value, DecimalVector.TYPE_WIDTH);
    vector.setBigEndian(idx, paddedBytes);
  }

  /**
   * Parquet stores decimal values in big-endian byte order, and Arrow stores them in native byte
   * order. When setting the value in Arrow, we call setBigEndian(), and the byte order is reversed
   * if needed. Also, the byte array is padded to fill 16 bytes in length by calling
   * Unsafe.setMemory(). The padding operation can be slow, so by using this utility method, we can
   * pad before calling setBigEndian() and avoid the call to Unsafe.setMemory().
   *
   * @param bigEndianBytes The big endian bytes
   * @param newLength The length of the byte array to return
   * @return The new byte array
   */
  @VisibleForTesting
  /**
   * 将大端字节数组填充/截断到指定长度，保持符号扩展。
   *
   * <p>逻辑：长度相等直接返回；短于目标则在高位补 0x00（正数）或 0xFF（负数，依据首字节 符号位）做符号扩展；长于目标抛出异常。避免 Arrow 内部
   * Unsafe.setMemory 的填充开销。
   *
   * @param bigEndianBytes 原始大端字节
   * @param newLength 目标长度
   * @return 填充后的字节数组
   * @throws IllegalArgumentException 若原数组长度大于目标长度
   */
  static byte[] padBigEndianBytes(byte[] bigEndianBytes, int newLength) {
    if (bigEndianBytes.length == newLength) {
      return bigEndianBytes;
    } else if (bigEndianBytes.length < newLength) {
      byte[] result = new byte[newLength];
      if (bigEndianBytes.length == 0) {
        return result;
      }

      int start = newLength - bigEndianBytes.length;
      if (bigEndianBytes[0] < 0) {
        Arrays.fill(result, 0, start, (byte) 0xFF);
      }
      System.arraycopy(bigEndianBytes, 0, result, start, bigEndianBytes.length);

      return result;
    }
    throw new IllegalArgumentException(
        String.format(
            "Buffer size of %d is larger than requested size of %d",
            bigEndianBytes.length, newLength));
  }
}
