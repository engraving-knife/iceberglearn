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

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.parquet.bytes.ByteBufferInputStream;
import org.apache.parquet.bytes.BytesUtils;
import org.apache.parquet.column.values.ValuesReader;
import org.apache.parquet.column.values.bitpacking.BytePacker;
import org.apache.parquet.column.values.bitpacking.Packer;
import org.apache.parquet.io.ParquetDecodingException;

/**
 * 文件级说明：Parquet 行程长度编码（RLE）/位打包（bit-packed）数据的向量化批量读取器。
 *
 * <p>所属模块：iceberg-arrow 的 parquet 子包（Parquet 数据页向量化解码的底层基础）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>批量解码 Parquet 的 RLE/位打包编码数据，一次读取一组值而非逐值读取（基于 Apache Spark 的 VectorizedRleValuesReader 改造）。
 *   <li>支持按 RLE 与 PACKED 两种模式切换解码，维护当前组的计数值与缓冲区。
 *   <li>子类负责将解码值写入 Arrow 向量；若列在行组内非全字典编码，则将字典 id 提前解码为实际值后再写入。
 * </ul>
 *
 * <p>设计意图：批量解码显著减少方法调用与分支开销；fixedWidth/readLength 区分该读取器 用于 definition/repetition level（定宽）还是
 * values（变宽，需从流首读 bitWidth）。 字段以包级可见暴露给同包子类以减少访问开销。
 *
 * <p>上下游关系：继承 Parquet {@link ValuesReader}；被 {@link VectorizedPageIterator}、 {@link
 * VectorizedDictionaryEncodedParquetValuesReader} 等同包子类使用。
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public class BaseVectorizedParquetValuesReader extends ValuesReader {
  // Current decoding mode. The encoded data contains groups of either run length encoded data
  // (RLE) or bit packed data. Each group contains a header that indicates which group it is and
  // the number of values in the group.
  /** 当前解码模式：RLE（行程长度编码）或 PACKED（位打包）。 */
  enum Mode {
    RLE,
    PACKED
  }

  // Encoded data.
  private ByteBufferInputStream inputStream;

  // bit/byte width of decoded data and utility to batch unpack them.
  private int bitWidth;
  private int bytesWidth;
  private BytePacker packer;

  // Current decoding mode and values
  Mode mode;
  int currentCount;
  int currentValue;

  // Buffer of decoded values if the values are PACKED.
  int[] packedValuesBuffer = new int[16];
  int packedValuesBufferIdx = 0;

  // If true, the bit width is fixed. This decoder is used in different places and this also
  // controls if we need to read the bitwidth from the beginning of the data stream.
  private final boolean fixedWidth;
  private final boolean readLength;
  final int maxDefLevel;

  final boolean setArrowValidityVector;

  /**
   * 构造用于读取 values 的读取器（变宽，bitWidth 从数据流首字节读取）。
   *
   * @param maxDefLevel 最大定义级别
   * @param setValidityVector 是否设置 Arrow 有效性向量
   */
  public BaseVectorizedParquetValuesReader(int maxDefLevel, boolean setValidityVector) {
    this.maxDefLevel = maxDefLevel;
    this.fixedWidth = false;
    this.readLength = false;
    this.setArrowValidityVector = setValidityVector;
  }

  /**
   * 构造用于读取 definition/repetition level 的读取器（定宽）。
   *
   * @param bitWidth 位宽
   * @param maxDefLevel 最大定义级别
   * @param setValidityVector 是否设置 Arrow 有效性向量
   */
  public BaseVectorizedParquetValuesReader(
      int bitWidth, int maxDefLevel, boolean setValidityVector) {
    this(bitWidth, maxDefLevel, bitWidth != 0, setValidityVector);
  }

  /**
   * 构造定宽读取器，可指定是否从流首读取长度前缀。
   *
   * @param bitWidth 位宽
   * @param maxDefLevel 最大定义级别
   * @param readLength 是否读取长度前缀并切片
   * @param setValidityVector 是否设置 Arrow 有效性向量
   */
  public BaseVectorizedParquetValuesReader(
      int bitWidth, int maxDefLevel, boolean readLength, boolean setValidityVector) {
    this.fixedWidth = true;
    this.readLength = readLength;
    this.maxDefLevel = maxDefLevel;
    this.setArrowValidityVector = setValidityVector;
    init(bitWidth);
  }

  /**
   * 从数据页初始化读取器。
   *
   * <p>逻辑：定宽模式下按 readLength 决定是否读取长度并切片；变宽模式下从流首字节读取 bitWidth 并初始化。bitWidth 为 0 时按 valueCount 个 0 的
   * RLE 段处理。
   *
   * @param valueCount 页内值数
   * @param in 数据输入流
   * @throws IOException 读取异常
   */
  @Override
  public void initFromPage(int valueCount, ByteBufferInputStream in) throws IOException {
    this.inputStream = in;
    if (fixedWidth) {
      // initialize for repetition and definition levels
      if (readLength) {
        int length = readIntLittleEndian();
        this.inputStream = in.sliceStream(length);
      }
    } else {
      // initialize for values
      if (in.available() > 0) {
        init(in.read());
      }
    }
    if (bitWidth == 0) {
      // 0 bit width, treat this as an RLE run of valueCount number of 0's.
      this.mode = Mode.RLE;
      this.currentCount = valueCount;
      this.currentValue = 0;
    } else {
      this.currentCount = 0;
    }
  }

  /**
   * 按位宽初始化内部解码状态（位宽、字节宽、packer）。
   *
   * @param bw 位宽，须在 0~32 之间
   */
  private void init(int bw) {
    Preconditions.checkArgument(bw >= 0 && bw <= 32, "bitWidth must be >= 0 and <= 32");
    this.bitWidth = bw;
    this.bytesWidth = BytesUtils.paddedByteCountFromBits(bw);
    this.packer = Packer.LITTLE_ENDIAN.newBytePacker(bw);
  }

  /**
   * 读取下一个变长无符号整数（varint）。
   *
   * @return 解码后的 int 值
   * @throws IOException 读取异常
   */
  private int readUnsignedVarInt() throws IOException {
    int value = 0;
    int shift = 0;
    int byteRead;
    do {
      byteRead = inputStream.read();
      value |= (byteRead & 0x7F) << shift;
      shift += 7;
    } while ((byteRead & 0x80) != 0);
    return value;
  }

  /**
   * 读取 4 字节小端序整数。
   *
   * @return int 值
   * @throws IOException 读取异常
   */
  private int readIntLittleEndian() throws IOException {
    int ch4 = inputStream.read();
    int ch3 = inputStream.read();
    int ch2 = inputStream.read();
    int ch1 = inputStream.read();
    return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0);
  }

  /**
   * 按当前 bytesWidth 读取小端序整数（0~4 字节）。
   *
   * @return int 值
   * @throws IOException 读取异常
   */
  private int readIntLittleEndianPaddedOnBitWidth() throws IOException {
    switch (bytesWidth) {
      case 0:
        return 0;
      case 1:
        return inputStream.read();
      case 2:
        {
          int ch2 = inputStream.read();
          int ch1 = inputStream.read();
          return (ch1 << 8) + ch2;
        }
      case 3:
        {
          int ch3 = inputStream.read();
          int ch2 = inputStream.read();
          int ch1 = inputStream.read();
          return (ch1 << 16) + (ch2 << 8) + (ch3 << 0);
        }
      case 4:
        {
          return readIntLittleEndian();
        }
    }
    throw new RuntimeException("Non-supported bytesWidth: " + bytesWidth);
  }

  /**
   * 读取下一个 RLE/位打包分组，更新模式、计数与当前值/缓冲区。
   *
   * <p>逻辑：读取 varint 头，最低位决定模式；RLE 模式取计数值与重复值； PACKED 模式按 8 个一组位解包到 packedValuesBuffer。IO 异常包装为
   * {@link ParquetDecodingException}。
   */
  void readNextGroup() {
    try {
      int header = readUnsignedVarInt();
      this.mode = (header & 1) == 0 ? Mode.RLE : Mode.PACKED;
      switch (mode) {
        case RLE:
          this.currentCount = header >>> 1;
          this.currentValue = readIntLittleEndianPaddedOnBitWidth();
          return;
        case PACKED:
          int numGroups = header >>> 1;
          this.currentCount = numGroups * 8;
          if (this.packedValuesBuffer.length < this.currentCount) {
            this.packedValuesBuffer = new int[this.currentCount];
          }
          packedValuesBufferIdx = 0;
          int valueIndex = 0;
          while (valueIndex < this.currentCount) {
            // values are bit packed 8 at a time, so reading bitWidth will always work
            ByteBuffer buffer = inputStream.slice(bitWidth);
            this.packer.unpack8Values(
                buffer, buffer.position(), this.packedValuesBuffer, valueIndex);
            valueIndex += 8;
          }
          return;
        default:
          throw new ParquetDecodingException("not a valid mode " + this.mode);
      }
    } catch (IOException e) {
      throw new ParquetDecodingException("Failed to read from input stream", e);
    }
  }

  /**
   * 读取下一个布尔值（按整数非 0 判断）。
   *
   * @return 布尔值
   */
  @Override
  public boolean readBoolean() {
    return this.readInteger() != 0;
  }

  /**
   * 跳过下一个值，本实现不支持。
   *
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public void skip() {
    throw new UnsupportedOperationException();
  }

  /**
   * 读取下一个字典 id（等同 readInteger）。
   *
   * @return 字典 id
   */
  @Override
  public int readValueDictionaryId() {
    return readInteger();
  }

  /**
   * 读取下一个整数值。
   *
   * <p>逻辑：当前计数为 0 时读取下一分组；计数递减后按模式返回 RLE 当前值或 PACKED 缓冲区下一个值。
   *
   * @return int 值
   */
  @Override
  public int readInteger() {
    if (this.currentCount == 0) {
      this.readNextGroup();
    }

    this.currentCount--;
    switch (mode) {
      case RLE:
        return this.currentValue;
      case PACKED:
        return this.packedValuesBuffer[packedValuesBufferIdx++];
    }
    throw new RuntimeException("Unrecognized mode: " + mode);
  }
}
