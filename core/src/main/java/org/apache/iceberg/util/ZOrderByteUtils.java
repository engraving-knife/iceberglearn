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
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Z-Order（Z 曲线）排序工具类，将各类型值转换为可按字典序（无符号大端）比较的字节表示。
 *
 * <p>所属模块：iceberg-core；层次定位：通用字节编码/排序工具层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将基础类型（int/long/short/byte/float/double/String）转换为字典序可比较的字节表示
 *   <li>对字符串与字节数组做定长截断或补零，保证 Z-Order 各列贡献等长字节
 *   <li>将多列已转换字节数组按位交错（interleave）为单一 Z-Order 字节序列
 * </ul>
 *
 * <p>设计意图：Z-Order 要求参与排序的字节序列必须按字典序与原值顺序一致，因此对有符号类型需要翻转 符号位、对浮点数需要按 IEEE 754 符号-幅值规则变换；除 String
 * 外所有类型统一写入 8 字节缓冲区以 简化交错逻辑。String 与变长字节通过定长截断/补零保证每列每次贡献相同字节数，确保交错结果可比较。
 *
 * <p>上下游关系：被 Z-Order 排序规则（sort order）实现调用，将多列值编码为单字节序以构建排序键； 依赖 {@link ByteBuffers} 复用缓冲区。算法参考 AWS
 * DynamoDB Z-Order 博客与 HBase OrderedBytes。
 */
public class ZOrderByteUtils {

  /** 基础类型（非 String）转换后占用的字节数。 */
  public static final int PRIMITIVE_BUFFER_SIZE = 8;

  /** 工具类私有构造器，禁止实例化。 */
  private ZOrderByteUtils() {}

  /**
   * 分配一个基础类型缓冲区。
   *
   * @return 新的 {@link ByteBuffer}，容量为 {@link #PRIMITIVE_BUFFER_SIZE}
   */
  static ByteBuffer allocatePrimitiveBuffer() {
    return ByteBuffer.allocate(PRIMITIVE_BUFFER_SIZE);
  }

  /**
   * 将有符号 int 转换为字典序可比较的 8 字节表示。
   *
   * <p>逻辑：有符号整数的字节因符号位导致负数排在正数之后，与字典序不一致；翻转最高符号位 （异或
   * 0x8000000000000000L）即可将负数整体前移到正数之前，使字节序列字典序与数值顺序一致。
   *
   * @param val 待转换的 int 值
   * @param reuse 可复用的缓冲区，为 null 或容量不足时分配新缓冲区
   * @return 写入转换后字节的 {@link ByteBuffer}
   */
  public static ByteBuffer intToOrderedBytes(int val, ByteBuffer reuse) {
    ByteBuffer bytes = ByteBuffers.reuse(reuse, PRIMITIVE_BUFFER_SIZE);
    bytes.putLong(((long) val) ^ 0x8000000000000000L);
    return bytes;
  }

  /**
   * 将有符号 long 转换为字典序可比较的 8 字节表示，处理方式同 {@link #intToOrderedBytes(int, ByteBuffer)}。
   *
   * @param val 待转换的 long 值
   * @param reuse 可复用的缓冲区
   * @return 写入转换后字节的 {@link ByteBuffer}
   */
  public static ByteBuffer longToOrderedBytes(long val, ByteBuffer reuse) {
    ByteBuffer bytes = ByteBuffers.reuse(reuse, PRIMITIVE_BUFFER_SIZE);
    bytes.putLong(val ^ 0x8000000000000000L);
    return bytes;
  }

  /**
   * 将有符号 short 转换为字典序可比较的 8 字节表示，处理方式同 {@link #intToOrderedBytes(int, ByteBuffer)}。
   *
   * @param val 待转换的 short 值
   * @param reuse 可复用的缓冲区
   * @return 写入转换后字节的 {@link ByteBuffer}
   */
  public static ByteBuffer shortToOrderedBytes(short val, ByteBuffer reuse) {
    ByteBuffer bytes = ByteBuffers.reuse(reuse, PRIMITIVE_BUFFER_SIZE);
    bytes.putLong(((long) val) ^ 0x8000000000000000L);
    return bytes;
  }

  /**
   * 将有符号 byte（tinyint）转换为字典序可比较的 8 字节表示，处理方式同 {@link #intToOrderedBytes(int, ByteBuffer)}。
   *
   * @param val 待转换的 byte 值
   * @param reuse 可复用的缓冲区
   * @return 写入转换后字节的 {@link ByteBuffer}
   */
  public static ByteBuffer tinyintToOrderedBytes(byte val, ByteBuffer reuse) {
    ByteBuffer bytes = ByteBuffers.reuse(reuse, PRIMITIVE_BUFFER_SIZE);
    bytes.putLong(((long) val) ^ 0x8000000000000000L);
    return bytes;
  }

  /**
   * 将 float 转换为字典序可比较的 8 字节表示。
   *
   * <p>逻辑：依据 IEEE 754，同格式浮点数按值排序后其比特按符号-幅值整数排序结果一致；据此将浮点比特 视为符号-幅值整数并变换为补码式字典序：正数翻转符号位，负数全部取反。最终写入
   * 8 字节缓冲区。
   *
   * @param val 待转换的 float 值
   * @param reuse 可复用的缓冲区
   * @return 写入转换后字节的 {@link ByteBuffer}
   */
  public static ByteBuffer floatToOrderedBytes(float val, ByteBuffer reuse) {
    ByteBuffer bytes = ByteBuffers.reuse(reuse, PRIMITIVE_BUFFER_SIZE);
    long lval = Double.doubleToLongBits(val);
    lval ^= ((lval >> (Integer.SIZE - 1)) | Long.MIN_VALUE);
    bytes.putLong(lval);
    return bytes;
  }

  /**
   * 将 double 转换为字典序可比较的 8 字节表示，处理方式同 {@link #floatToOrderedBytes(float, ByteBuffer)}。
   *
   * @param val 待转换的 double 值
   * @param reuse 可复用的缓冲区
   * @return 写入转换后字节的 {@link ByteBuffer}
   */
  public static ByteBuffer doubleToOrderedBytes(double val, ByteBuffer reuse) {
    ByteBuffer bytes = ByteBuffers.reuse(reuse, PRIMITIVE_BUFFER_SIZE);
    long lval = Double.doubleToLongBits(val);
    lval ^= ((lval >> (Integer.SIZE - 1)) | Long.MIN_VALUE);
    bytes.putLong(lval);
    return bytes;
  }

  /**
   * 将 String 转换为定长字典序可比较的字节表示。
   *
   * <p>逻辑：String 本身字典序可排序，但 Z-Order 要求每列每次贡献相同字节数。本方法将字符串 UTF-8 编码到指定长度的缓冲区：长串截断、短串右侧补
   * 0x00，从而保证定长输出。
   *
   * @param val 待转换的字符串，可为 null（输出全 0）
   * @param length 输出字节长度
   * @param reuse 可复用的缓冲区
   * @param encoder UTF-8 字符集编码器，必须使用 {@link StandardCharsets#UTF_8}
   * @return 写入转换后字节的 {@link ByteBuffer}
   */
  @SuppressWarnings("ByteBufferBackingArray")
  public static ByteBuffer stringToOrderedBytes(
      String val, int length, ByteBuffer reuse, CharsetEncoder encoder) {
    Preconditions.checkArgument(
        encoder.charset().equals(StandardCharsets.UTF_8),
        "Cannot use an encoder not using UTF_8 as it's Charset");

    ByteBuffer bytes = ByteBuffers.reuse(reuse, length);
    Arrays.fill(bytes.array(), 0, length, (byte) 0x00);
    if (val != null) {
      CharBuffer inputBuffer = CharBuffer.wrap(val);
      encoder.encode(inputBuffer, bytes, true);
    }
    return bytes;
  }

  /**
   * 将字节数组截断或补零到指定长度。
   *
   * <p>逻辑：若输入字节数短于目标长度，则写入后用 0x00 右侧填充；若长于目标长度，则截取前 {@code length} 字节。
   *
   * @param val 输入字节数组
   * @param length 目标字节长度
   * @param reuse 可复用的缓冲区
   * @return 写入定长字节后的 {@link ByteBuffer}
   */
  @SuppressWarnings("ByteBufferBackingArray")
  public static ByteBuffer byteTruncateOrFill(byte[] val, int length, ByteBuffer reuse) {
    ByteBuffer bytes = ByteBuffers.reuse(reuse, length);
    if (val.length < length) {
      bytes.put(val, 0, val.length);
      Arrays.fill(bytes.array(), val.length, length, (byte) 0x00);
    } else {
      bytes.put(val, 0, length);
    }
    return bytes;
  }

  /**
   * 将多列已转换字节数组按位交错为 Z-Order 字节序列，使用新分配的输出缓冲区。
   *
   * @param columnsBinary 各列已转换的字节表示
   * @param interleavedSize 输出交错字节数
   * @return 交错后的 Z-Order 字节数组
   */
  static byte[] interleaveBits(byte[][] columnsBinary, int interleavedSize) {
    return interleaveBits(columnsBinary, interleavedSize, ByteBuffer.allocate(interleavedSize));
  }

  /**
   * 将多列已转换字节数组按位交错为 Z-Order 字节序列。
   *
   * <p>逻辑：朴素循环逐位交错——按输出位顺序，依次从每列同位位置取 1 bit 写入输出。要求每列每次贡献 相同字节数以保证排序一致；某列在当前位位置已无字节可取时跳过该列。最终返回长度为
   * {@code interleavedSize} 的字节数组。
   *
   * @param columnsBinary 各列已转换的字节表示数组
   * @param interleavedSize 输出交错字节数
   * @param reuse 可复用的输出缓冲区
   * @return 交错后的 Z-Order 字节数组
   */
  // NarrowingCompoundAssignment is intended here. See
  // https://github.com/apache/iceberg/pull/5200#issuecomment-1176226163
  @SuppressWarnings({"ByteBufferBackingArray", "NarrowingCompoundAssignment"})
  public static byte[] interleaveBits(
      byte[][] columnsBinary, int interleavedSize, ByteBuffer reuse) {
    byte[] interleavedBytes = reuse.array();
    Arrays.fill(interleavedBytes, 0, interleavedSize, (byte) 0x00);

    int sourceColumn = 0;
    int sourceByte = 0;
    int sourceBit = 7;
    int interleaveByte = 0;
    int interleaveBit = 7;

    while (interleaveByte < interleavedSize) {
      // Take the source bit from source byte and move it to the output bit position
      interleavedBytes[interleaveByte] |=
          (columnsBinary[sourceColumn][sourceByte] & 1 << sourceBit) >>> sourceBit << interleaveBit;
      --interleaveBit;

      // Check if an output byte has been completed
      if (interleaveBit == -1) {
        // Move to the next output byte
        interleaveByte++;
        // Move to the highest order bit of the new output byte
        interleaveBit = 7;
      }

      // Check if the last output byte has been completed
      if (interleaveByte == interleavedSize) {
        break;
      }

      // Find the next source bit to interleave
      do {
        // Move to next column
        ++sourceColumn;
        if (sourceColumn == columnsBinary.length) {
          // If the last source column was used, reset to next bit of first column
          sourceColumn = 0;
          --sourceBit;
          if (sourceBit == -1) {
            // If the last bit of the source byte was used, reset to the highest bit of the next
            // byte
            sourceByte++;
            sourceBit = 7;
          }
        }
      } while (columnsBinary[sourceColumn].length <= sourceByte);
    }
    return interleavedBytes;
  }
}
