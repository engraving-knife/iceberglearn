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
package org.apache.iceberg.puffin;

import io.airlift.compress.Compressor;
import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.Pair;

/**
 * 文件级说明：Puffin 文件二进制格式的底层常量与编解码工具。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 Puffin 文件 magic 字节、footer 定长结构（payload-size、flags、magic）的偏移与长度。
 *   <li>定义 footer 标志位 {@link Flag}（如 footer payload 是否被压缩）的位布局。
 *   <li>提供小端整数的读写、以及 Blob/footer payload 的压缩/解压缩实现（ZSTD 等）。
 * </ul>
 *
 * <p>设计意图：将格式细节与 {@link PuffinReader}/{@link PuffinWriter} 解耦，使读写器 只关注流程；标志位用 byte+bit
 * 二维定位，便于按位扩展；footer 默认压缩 codec 固定为 LZ4。
 *
 * <p>上下游关系：包级别私有，仅供 {@link PuffinReader}、{@link PuffinWriter} 使用。
 */
final class PuffinFormat {
  private PuffinFormat() {}

  /**
   * Puffin footer 中标志位枚举，按"字节序号 + 位序号"定位。
   *
   * <p>设计意图：footer 头部固定 4 字节作为 flags 区，每个 bit 表示一个开关； 当前仅定义"footer payload 是否被压缩"标志。
   */
  enum Flag {
    /** 表示 footer payload 已被压缩（使用 {@link #FOOTER_COMPRESSION_CODEC}）。 */
    FOOTER_PAYLOAD_COMPRESSED(0, 0),
  /**/ ;

    private static final Map<Pair<Integer, Integer>, Flag> BY_BYTE_AND_BIT =
        Stream.of(values())
            .collect(
                ImmutableMap.toImmutableMap(
                    flag -> Pair.of(flag.byteNumber(), flag.bitNumber()), Function.identity()));

    private final int byteNumber;
    private final int bitNumber;

    Flag(int byteNumber, int bitNumber) {
      Preconditions.checkArgument(
          0 <= byteNumber && byteNumber < PuffinFormat.FOOTER_STRUCT_FLAGS_LENGTH,
          "Invalid byteNumber");
      Preconditions.checkArgument(0 <= bitNumber && bitNumber < Byte.SIZE, "Invalid bitNumber");
      this.byteNumber = byteNumber;
      this.bitNumber = bitNumber;
    }

    /** 按字节号与位号查找标志位；未注册返回 null。 */
    @Nullable
    static Flag fromBit(int byteNumber, int bitNumber) {
      return BY_BYTE_AND_BIT.get(Pair.of(byteNumber, bitNumber));
    }

    /** 返回该标志位所在的字节序号。 */
    public int byteNumber() {
      return byteNumber;
    }

    /** 返回该标志位在字节中的位序号。 */
    public int bitNumber() {
      return bitNumber;
    }
  }

  static final int FOOTER_START_MAGIC_OFFSET = 0;
  static final int FOOTER_START_MAGIC_LENGTH = getMagic().length;

  // "Footer struct" denotes the fixed-length portion of the Footer
  static final int FOOTER_STRUCT_PAYLOAD_SIZE_OFFSET = 0;
  static final int FOOTER_STRUCT_FLAGS_OFFSET = FOOTER_STRUCT_PAYLOAD_SIZE_OFFSET + 4;
  static final int FOOTER_STRUCT_FLAGS_LENGTH = 4;
  static final int FOOTER_STRUCT_MAGIC_OFFSET =
      FOOTER_STRUCT_FLAGS_OFFSET + FOOTER_STRUCT_FLAGS_LENGTH;
  static final int FOOTER_STRUCT_LENGTH = FOOTER_STRUCT_MAGIC_OFFSET + getMagic().length;

  /** footer payload 压缩时使用的默认 codec。 */
  static final PuffinCompressionCodec FOOTER_COMPRESSION_CODEC = PuffinCompressionCodec.LZ4;

  /** 返回 Puffin 文件 magic 字节 {@code 0x50 0x46 0x41 0x31}（"PFA1"）。 */
  static byte[] getMagic() {
    return new byte[] {0x50, 0x46, 0x41, 0x31};
  }

  /**
   * 以小端序将 4 字节整数写入输出流。
   *
   * @param outputStream 目标输出流
   * @param value 待写入的 32 位整数
   * @throws IOException 写入失败
   */
  static void writeIntegerLittleEndian(OutputStream outputStream, int value) throws IOException {
    outputStream.write(0xFF & value);
    outputStream.write(0xFF & (value >> 8));
    outputStream.write(0xFF & (value >> 16));
    outputStream.write(0xFF & (value >> 24));
  }

  /**
   * 以小端序从字节数组读取 4 字节整数。
   *
   * @param data 数据源
   * @param offset 起始字节偏移
   * @return 读取的 32 位整数（无符号扩展后写入 int）
   */
  static int readIntegerLittleEndian(byte[] data, int offset) {
    return Byte.toUnsignedInt(data[offset])
        | (Byte.toUnsignedInt(data[offset + 1]) << 8)
        | (Byte.toUnsignedInt(data[offset + 2]) << 16)
        | (Byte.toUnsignedInt(data[offset + 3]) << 24);
  }

  /**
   * 按指定 codec 压缩输入缓冲。
   *
   * <p>逻辑：NONE 直接返回副本；ZSTD 委托给 ZstdCompressor；LZ4 暂未实现（待 aircompressor 提供 frame 压缩器后接入）。未知 codec 抛出
   * UnsupportedOperationException。
   *
   * @param codec 压缩方式
   * @param input 待压缩的字节缓冲
   * @return 压缩后的字节缓冲
   */
  static ByteBuffer compress(PuffinCompressionCodec codec, ByteBuffer input) {
    switch (codec) {
      case NONE:
        return input.duplicate();
      case LZ4:
        // TODO requires LZ4 frame compressor, e.g.
        // https://github.com/airlift/aircompressor/pull/142
        break;
      case ZSTD:
        return compress(new ZstdCompressor(), input);
    }
    throw new UnsupportedOperationException("Unsupported codec: " + codec);
  }

  /**
   * 使用通用 {@link Compressor} 压缩输入缓冲。
   *
   * <p>逻辑：分配 maxCompressedLength 大小的输出缓冲，压缩后 flip 截断至实际长度返回。
   *
   * @param compressor 压缩器实例
   * @param input 待压缩缓冲
   * @return 压缩后缓冲
   */
  private static ByteBuffer compress(Compressor compressor, ByteBuffer input) {
    ByteBuffer output = ByteBuffer.allocate(compressor.maxCompressedLength(input.remaining()));
    compressor.compress(input.duplicate(), output);
    output.flip();
    return output;
  }

  /**
   * 按指定 codec 解压输入缓冲。
   *
   * <p>逻辑：NONE 直接返回副本；ZSTD 委托 {@link #decompressZstd(ByteBuffer)}；LZ4 暂未实现。 未知 codec 抛出
   * UnsupportedOperationException。
   *
   * @param codec 压缩方式
   * @param input 待解压的字节缓冲
   * @return 解压后的字节缓冲
   */
  static ByteBuffer decompress(PuffinCompressionCodec codec, ByteBuffer input) {
    switch (codec) {
      case NONE:
        return input.duplicate();

      case LZ4:
        // TODO requires LZ4 frame decompressor, e.g.
        // https://github.com/airlift/aircompressor/pull/142
        break;

      case ZSTD:
        return decompressZstd(input);
    }

    throw new UnsupportedOperationException("Unsupported codec: " + codec);
  }

  /**
   * 使用 ZSTD 解压输入缓冲。
   *
   * <p>逻辑：优先使用底层字节数组（避免拷贝），否则将 ByteBuffer 转为字节数组； 通过 {@link ZstdDecompressor#getDecompressedSize}
   * 获取原始长度并分配输出数组， 调用 {@link ZstdDecompressor#decompress} 解压，校验解压长度后包装为 ByteBuffer 返回。
   *
   * @param input ZSTD 压缩的字节缓冲
   * @return 解压后的字节缓冲
   */
  private static ByteBuffer decompressZstd(ByteBuffer input) {
    byte[] inputBytes;
    int inputOffset;
    int inputLength;
    if (input.hasArray()) {
      inputBytes = input.array();
      inputOffset = input.arrayOffset();
      inputLength = input.remaining();
    } else {
      // TODO implement ZstdDecompressor.getDecompressedSize for ByteBuffer to avoid copying
      inputBytes = ByteBuffers.toByteArray(input);
      inputOffset = 0;
      inputLength = inputBytes.length;
    }

    byte[] decompressed =
        new byte
            [Math.toIntExact(
                ZstdDecompressor.getDecompressedSize(inputBytes, inputOffset, inputLength))];
    int decompressedLength =
        new ZstdDecompressor()
            .decompress(inputBytes, inputOffset, inputLength, decompressed, 0, decompressed.length);
    Preconditions.checkState(
        decompressedLength == decompressed.length, "Invalid decompressed length");
    return ByteBuffer.wrap(decompressed);
  }
}
