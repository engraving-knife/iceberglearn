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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.RangeReadable;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.puffin.PuffinFormat.Flag;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.io.ByteStreams;
import org.apache.iceberg.util.Pair;

/**
 * 文件级说明：Puffin 文件读取器，负责解析 footer 并按需读取 Blob。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取并解析 Puffin 文件 footer，得到 {@link FileMetadata}（含所有 Blob 元信息）。
 *   <li>按 {@link BlobMetadata} 的偏移与长度读取指定 Blob 字节，并按其压缩编码解压。
 * </ul>
 *
 * <p>设计意图：footer 大小可由调用方传入以省去一次 seek；底层输入流若实现 {@link RangeReadable} 则优先走范围读取，避免顺序 seek；解析后的
 * FileMetadata 缓存， 多次调用 {@link #fileMetadata()} 不重复解析。
 *
 * <p>上下游关系：由 {@link Puffin.ReadBuilder} 构造；上游为统计信息读取、删除向量加载 等业务，下游依赖 {@link PuffinFormat}
 * 解码二进制结构、{@link FileMetadataParser} 解析 footer JSON。
 */
public class PuffinReader implements Closeable {
  // Must not be modified
  private static final byte[] MAGIC = PuffinFormat.getMagic();

  private final long fileSize;
  private final SeekableInputStream input;
  private Integer knownFooterSize;
  private FileMetadata knownFileMetadata;

  /**
   * 构造 Puffin 读取器。
   *
   * <p>逻辑：校验输入非空；fileSize 为 null 时回退到 {@link InputFile#getLength()}； footerSize 为 null 时后续按 footer
   * 结构反推；否则校验其取值范围并缓存。
   *
   * @param inputFile 待读取的 Puffin 文件
   * @param fileSize 已知文件大小，null 表示由 InputFile 提供
   * @param footerSize 已知 footer 大小，null 表示由 reader 反推
   */
  PuffinReader(InputFile inputFile, @Nullable Long fileSize, @Nullable Long footerSize) {
    Preconditions.checkNotNull(inputFile, "inputFile is null");
    this.fileSize = fileSize == null ? inputFile.getLength() : fileSize;
    this.input = inputFile.newStream();
    if (footerSize != null) {
      Preconditions.checkArgument(
          0 < footerSize && footerSize <= this.fileSize - MAGIC.length,
          "Invalid footer size: %s",
          footerSize);
      this.knownFooterSize = Math.toIntExact(footerSize);
    }
  }

  /**
   * 读取并解析 footer，返回 {@link FileMetadata}（结果会被缓存）。
   *
   * <p>逻辑：确定 footer 大小 -> 读取整段 footer -> 校验首尾 magic -> 解码 flags 得到 footer 压缩 codec -> 读取 payload
   * 长度并校验 footer 总长一致性 -> 取出 payload 并解压 -> 解析 JSON 得到 FileMetadata 并缓存。
   *
   * @return 文件元数据
   * @throws IOException 读取失败
   * @throws IllegalStateException 文件 magic 或 footer 结构不合法
   */
  public FileMetadata fileMetadata() throws IOException {
    if (knownFileMetadata == null) {
      int footerSize = footerSize();
      byte[] footer = readInput(fileSize - footerSize, footerSize);

      checkMagic(footer, PuffinFormat.FOOTER_START_MAGIC_OFFSET);
      int footerStructOffset = footerSize - PuffinFormat.FOOTER_STRUCT_LENGTH;
      checkMagic(footer, footerStructOffset + PuffinFormat.FOOTER_STRUCT_MAGIC_OFFSET);

      PuffinCompressionCodec footerCompression = PuffinCompressionCodec.NONE;
      for (Flag flag : decodeFlags(footer, footerStructOffset)) {
        switch (flag) {
          case FOOTER_PAYLOAD_COMPRESSED:
            footerCompression = PuffinFormat.FOOTER_COMPRESSION_CODEC;
            break;
          default:
            throw new IllegalStateException("Unsupported flag: " + flag);
        }
      }

      int footerPayloadSize =
          PuffinFormat.readIntegerLittleEndian(
              footer, footerStructOffset + PuffinFormat.FOOTER_STRUCT_PAYLOAD_SIZE_OFFSET);
      Preconditions.checkState(
          footerSize
              == PuffinFormat.FOOTER_START_MAGIC_LENGTH
                  + footerPayloadSize
                  + PuffinFormat.FOOTER_STRUCT_LENGTH,
          "Unexpected footer payload size value %s for footer size %s",
          footerPayloadSize,
          footerSize);

      ByteBuffer footerPayload = ByteBuffer.wrap(footer, 4, footerPayloadSize);
      ByteBuffer footerJson = PuffinFormat.decompress(footerCompression, footerPayload);
      this.knownFileMetadata = parseFileMetadata(footerJson);
    }
    return knownFileMetadata;
  }

  /**
   * 从 footer 中解码 flags 区，返回已置位的 {@link Flag} 集合。
   *
   * <p>逻辑：遍历 4 字节 flags 区，对每个字节按位扫描，遇置位则通过 {@link Flag#fromBit(int, int)} 解析为 Flag 并加入集合；未知位抛出异常。
   *
   * @param footer footer 字节数组
   * @param footerStructOffset footer 结构在 footer 数组中的起始偏移
   * @return 已置位的标志位集合
   */
  private Set<Flag> decodeFlags(byte[] footer, int footerStructOffset) {
    EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
    for (int byteNumber = 0; byteNumber < PuffinFormat.FOOTER_STRUCT_FLAGS_LENGTH; byteNumber++) {
      int flagByte =
          Byte.toUnsignedInt(
              footer[footerStructOffset + PuffinFormat.FOOTER_STRUCT_FLAGS_OFFSET + byteNumber]);
      int bitNumber = 0;
      while (flagByte != 0) {
        if ((flagByte & 0x1) != 0) {
          Flag flag = Flag.fromBit(byteNumber, bitNumber);
          Preconditions.checkState(
              flag != null, "Unknown flag byte %s and bit %s set", byteNumber, bitNumber);
          flags.add(flag);
        }
        flagByte = flagByte >> 1;
        bitNumber++;
      }
    }
    return flags;
  }

  /**
   * 读取指定 Blob 列表，返回 (元信息, 解压后字节) 的可迭代对。
   *
   * <p>逻辑：空列表直接返回空；否则按 Blob 在文件中的偏移排序后惰性读取：对每个 Blob seek 到其偏移并读取 length 字节，按其压缩编码解压，配对返回。返回的是惰性迭代器，
   * 仅在被迭代时才实际读取下一个 Blob，避免一次性加载全部数据。
   *
   * @param blobs 待读取的 Blob 元信息列表
   * @return (BlobMetadata, 解压后 ByteBuffer) 的可迭代对象
   */
  public Iterable<Pair<BlobMetadata, ByteBuffer>> readAll(List<BlobMetadata> blobs) {
    if (blobs.isEmpty()) {
      return ImmutableList.of();
    }

    // TODO inspect blob offsets and coalesce read regions close to each other

    return () ->
        blobs.stream()
            .sorted(Comparator.comparingLong(BlobMetadata::offset))
            .map(
                (BlobMetadata blobMetadata) -> {
                  try {
                    input.seek(blobMetadata.offset());
                    byte[] bytes = new byte[Math.toIntExact(blobMetadata.length())];
                    ByteStreams.readFully(input, bytes);
                    ByteBuffer rawData = ByteBuffer.wrap(bytes);
                    PuffinCompressionCodec codec =
                        PuffinCompressionCodec.forName(blobMetadata.compressionCodec());
                    ByteBuffer data = PuffinFormat.decompress(codec, rawData);
                    return Pair.of(blobMetadata, data);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                })
            .iterator();
  }

  /**
   * 校验指定偏移处的字节是否为 Puffin magic。
   *
   * @param data 数据源
   * @param offset magic 起始偏移
   * @throws IllegalStateException 若 magic 不匹配
   */
  private static void checkMagic(byte[] data, int offset) {
    byte[] read = Arrays.copyOfRange(data, offset, offset + MAGIC.length);
    if (!Arrays.equals(read, MAGIC)) {
      throw new IllegalStateException(
          String.format(
              "Invalid file: expected magic at offset %s: %s, but got %s",
              offset, Arrays.toString(MAGIC), Arrays.toString(read)));
    }
  }

  /**
   * 计算 footer 大小（若未传入则从尾部反推）。
   *
   * <p>逻辑：若已知则直接返回；否则从文件尾部读取 FOOTER_STRUCT_LENGTH 字节，校验尾部 magic， 读取 payload 长度，按 magic + payload +
   * struct 计算总大小并缓存。
   *
   * @return footer 字节大小
   * @throws IOException 读取失败
   */
  private int footerSize() throws IOException {
    if (knownFooterSize == null) {
      Preconditions.checkState(
          fileSize >= PuffinFormat.FOOTER_STRUCT_LENGTH,
          "Invalid file: file length %s is less tha minimal length of the footer tail %s",
          fileSize,
          PuffinFormat.FOOTER_STRUCT_LENGTH);
      byte[] footerStruct =
          readInput(
              fileSize - PuffinFormat.FOOTER_STRUCT_LENGTH, PuffinFormat.FOOTER_STRUCT_LENGTH);
      checkMagic(footerStruct, PuffinFormat.FOOTER_STRUCT_MAGIC_OFFSET);

      int footerPayloadSize =
          PuffinFormat.readIntegerLittleEndian(
              footerStruct, PuffinFormat.FOOTER_STRUCT_PAYLOAD_SIZE_OFFSET);
      knownFooterSize =
          PuffinFormat.FOOTER_START_MAGIC_LENGTH
              + footerPayloadSize
              + PuffinFormat.FOOTER_STRUCT_LENGTH;
    }
    return knownFooterSize;
  }

  /**
   * 从文件指定偏移读取指定长度字节。
   *
   * <p>设计要点：若输入流实现 {@link RangeReadable} 则走范围读取，否则 seek + 全量读。
   *
   * @param offset 起始字节偏移
   * @param length 读取长度
   * @return 读取到的字节数组
   * @throws IOException 读取失败
   */
  private byte[] readInput(long offset, int length) throws IOException {
    byte[] data = new byte[length];
    if (input instanceof RangeReadable) {
      ((RangeReadable) input).readFully(offset, data);
    } else {
      input.seek(offset);
      ByteStreams.readFully(input, data);
    }
    return data;
  }

  /**
   * 将 footer payload 字节按 UTF-8 解码为 JSON 并解析为 {@link FileMetadata}。
   *
   * @param data footer payload 字节
   * @return 文件元数据
   */
  private static FileMetadata parseFileMetadata(ByteBuffer data) {
    String footerJson = StandardCharsets.UTF_8.decode(data).toString();
    return FileMetadataParser.fromJson(footerJson);
  }

  @Override
  /**
   * 关闭底层输入流并清空缓存的 footer 信息。
   *
   * @throws IOException 关闭失败
   */
  public void close() throws IOException {
    input.close();
    knownFooterSize = null;
    knownFileMetadata = null;
  }
}
