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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.IOUtil;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.puffin.PuffinFormat.Flag;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：Puffin 文件写入器，负责将 {@link Blob} 序列化为 Puffin 文件二进制格式。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link FileAppender}，逐个写入 {@link Blob}（含压缩）。
 *   <li>在 {@link #finish()} 时写入 footer（含 Blob 元信息列表、文件属性、magic、flags）。
 *   <li>记录已写入 Blob 的 {@link BlobMetadata}，供调用方查询落盘布局。
 * </ul>
 *
 * <p>设计意图：footer 压缩与 Blob 默认压缩均可配置；单个 Blob 可通过 {@link Blob#requestedCompression()} 覆盖默认压缩策略；header
 * 懒写入，首次 add 时才写出 magic，避免空文件产生不完整头部。
 *
 * <p>上下游关系：由 {@link Puffin.WriteBuilder} 构造；上游为统计信息收集、删除向量生成 等业务；下游依赖 {@link PuffinFormat}
 * 做二进制编码与压缩、{@link FileMetadataParser} 序列化 footer JSON。
 */
public class PuffinWriter implements FileAppender<Blob> {
  // Must not be modified
  private static final byte[] MAGIC = PuffinFormat.getMagic();

  private final PositionOutputStream outputStream;
  private final Map<String, String> properties;
  private final PuffinCompressionCodec footerCompression;
  private final PuffinCompressionCodec defaultBlobCompression;

  private final List<BlobMetadata> writtenBlobsMetadata = Lists.newArrayList();
  private boolean headerWritten;
  private boolean finished;
  private Optional<Integer> footerSize = Optional.empty();
  private Optional<Long> fileSize = Optional.empty();

  /**
   * 构造 Puffin 写入器。
   *
   * <p>逻辑：校验入参非空；打开输出流；根据 compressFooter 决定 footer 压缩 codec （LZ4 或 NONE）；缓存默认 Blob 压缩策略。
   *
   * @param outputFile 目标输出文件
   * @param properties 文件级属性
   * @param compressFooter 是否压缩 footer
   * @param defaultBlobCompression Blob 默认压缩方式
   */
  PuffinWriter(
      OutputFile outputFile,
      Map<String, String> properties,
      boolean compressFooter,
      PuffinCompressionCodec defaultBlobCompression) {
    Preconditions.checkNotNull(outputFile, "outputFile is null");
    Preconditions.checkNotNull(properties, "properties is null");
    Preconditions.checkNotNull(defaultBlobCompression, "defaultBlobCompression is null");
    this.outputStream = outputFile.create();
    this.properties = ImmutableMap.copyOf(properties);
    this.footerCompression =
        compressFooter ? PuffinFormat.FOOTER_COMPRESSION_CODEC : PuffinCompressionCodec.NONE;
    this.defaultBlobCompression = defaultBlobCompression;
  }

  /**
   * 写入一个 {@link Blob}。
   *
   * <p>逻辑：校验非空且未完成 -> 必要时写 header magic -> 记录当前文件偏移 -> 确定 Blob 压缩方式（Blob 自带优先，否则用默认） -> 压缩并写出字节 ->
   * 记录该 Blob 的 {@link BlobMetadata}（含偏移、长度、压缩编码等）。
   *
   * @param blob 待写入的 Blob
   * @throws UncheckedIOException 写入失败时包装 IOException 抛出
   */
  @Override
  public void add(Blob blob) {
    Preconditions.checkNotNull(blob, "blob is null");
    checkNotFinished();
    try {
      writeHeaderIfNeeded();
      long fileOffset = outputStream.getPos();
      PuffinCompressionCodec codec =
          MoreObjects.firstNonNull(blob.requestedCompression(), defaultBlobCompression);
      ByteBuffer rawData = PuffinFormat.compress(codec, blob.blobData());
      int length = rawData.remaining();
      IOUtil.writeFully(outputStream, rawData);
      writtenBlobsMetadata.add(
          new BlobMetadata(
              blob.type(),
              blob.inputFields(),
              blob.snapshotId(),
              blob.sequenceNumber(),
              fileOffset,
              length,
              codec.codecName(),
              blob.properties()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 返回文件 metrics；当前 Puffin 不维护文件级 metrics，返回空对象。 */
  @Override
  public Metrics metrics() {
    return new Metrics();
  }

  /** 返回文件总长度，等价于 {@link #fileSize()}。 */
  @Override
  public long length() {
    return fileSize();
  }

  /**
   * 关闭写入器：若未调用 {@link #finish()} 则先完成 footer 写入，再关闭输出流。
   *
   * @throws IOException 写入或关闭失败
   */
  @Override
  public void close() throws IOException {
    if (!finished) {
      finish();
    }

    outputStream.close();
  }

  /**
   * 首次写入时输出文件头 magic（仅写一次）。
   *
   * <p>设计要点：懒写入，避免空文件产生不完整头部。
   *
   * @throws IOException 写入失败
   */
  private void writeHeaderIfNeeded() throws IOException {
    if (headerWritten) {
      return;
    }

    this.outputStream.write(MAGIC);
    this.headerWritten = true;
  }

  /**
   * 完成 Puffin 文件写入：写出 footer 并记录文件大小与 footer 大小。
   *
   * <p>逻辑：校验未完成 -> 必要时写 header -> 记录 footer 起始偏移 -> 写 footer -> 计算 footerSize 与 fileSize 并缓存 -> 标记
   * finished。该方法只可调用一次。
   *
   * @throws IOException 写入失败
   * @throws IllegalStateException 重复调用时抛出
   */
  public void finish() throws IOException {
    checkNotFinished();
    writeHeaderIfNeeded();
    Preconditions.checkState(!footerSize.isPresent(), "footerSize already set");
    long footerOffset = outputStream.getPos();
    writeFooter();
    this.footerSize = Optional.of(Math.toIntExact(outputStream.getPos() - footerOffset));
    this.fileSize = Optional.of(outputStream.getPos());
    this.finished = true;
  }

  /**
   * 写入 footer：magic + 压缩后的 footer JSON payload + payload 长度（小端）+ flags + magic。
   *
   * <p>逻辑：组装 {@link FileMetadata} -> 序列化为 JSON -> 按 footer 压缩策略压缩 -> 依次写出 magic、payload、payload
   * 长度、flags 区、尾部 magic。
   *
   * @throws IOException 写入失败
   */
  private void writeFooter() throws IOException {
    FileMetadata fileMetadata = new FileMetadata(writtenBlobsMetadata, properties);
    ByteBuffer footerJson =
        ByteBuffer.wrap(
            FileMetadataParser.toJson(fileMetadata, false).getBytes(StandardCharsets.UTF_8));
    ByteBuffer footerPayload = PuffinFormat.compress(footerCompression, footerJson);
    outputStream.write(MAGIC);
    int footerPayloadLength = footerPayload.remaining();
    IOUtil.writeFully(outputStream, footerPayload);
    PuffinFormat.writeIntegerLittleEndian(outputStream, footerPayloadLength);
    writeFlags();
    outputStream.write(MAGIC);
  }

  /**
   * 写入 footer 的 flags 区（固定 4 字节）。
   *
   * <p>逻辑：将已置位的 {@link Flag} 按其 byteNumber 分组，逐字节按位 OR 后写出。
   *
   * @throws IOException 写入失败
   */
  private void writeFlags() throws IOException {
    Map<Integer, List<Flag>> flagsByByteNumber =
        fileFlags().stream().collect(Collectors.groupingBy(Flag::byteNumber));
    for (int byteNumber = 0; byteNumber < PuffinFormat.FOOTER_STRUCT_FLAGS_LENGTH; byteNumber++) {
      int byteFlag = 0;
      for (Flag flag : flagsByByteNumber.getOrDefault(byteNumber, ImmutableList.of())) {
        byteFlag |= 0x1 << flag.bitNumber();
      }
      outputStream.write(byteFlag);
    }
  }

  /**
   * 返回 footer 字节大小。
   *
   * @return footer 大小
   * @throws IllegalStateException 若 footer 尚未写入
   */
  public long footerSize() {
    return footerSize.orElseThrow(() -> new IllegalStateException("Footer not written yet"));
  }

  /**
   * 返回文件总字节大小。
   *
   * @return 文件大小
   * @throws IllegalStateException 若文件尚未完成写入
   */
  public long fileSize() {
    return fileSize.orElseThrow(() -> new IllegalStateException("File not written yet"));
  }

  /** 返回已写入 Blob 的元信息列表（不可变副本）。 */
  public List<BlobMetadata> writtenBlobsMetadata() {
    return ImmutableList.copyOf(writtenBlobsMetadata);
  }

  /**
   * 计算文件 footer 应置位的标志位集合。
   *
   * <p>逻辑：若 footer 启用了压缩，则置 {@link Flag#FOOTER_PAYLOAD_COMPRESSED}。
   *
   * @return 标志位集合
   */
  private Set<Flag> fileFlags() {
    EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
    if (footerCompression != PuffinCompressionCodec.NONE) {
      flags.add(Flag.FOOTER_PAYLOAD_COMPRESSED);
    }

    return flags;
  }

  /** 校验 writer 尚未完成，否则抛出 IllegalStateException。 */
  private void checkNotFinished() {
    Preconditions.checkState(!finished, "Writer already finished");
  }
}
