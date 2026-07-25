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
package org.apache.iceberg.gcp.gcs;

import com.google.api.client.util.Lists;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobSourceOption;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.RangeReadable;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GCS 可定位输入流：基于 GCS 原生流式通道实现读取。
 *
 * <p>所属模块：iceberg-gcp（GCP 集成模块，处于 Iceberg 存储访问层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link SeekableInputStream} 和 {@link RangeReadable}，支持随机定位读取和 按区间/尾部读取。
 *   <li>利用 GCS {@link ReadChannel} 进行流式传输，支持配置 chunk 大小、解密 Key、 用户项目等选项。
 *   <li>记录读取字节数和读取操作数指标。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>利用 GCS 原生流式上传能力，参见 <a href="https://cloud.google.com/storage/docs/streaming">Streaming
 *       Transfers</a>。
 *   <li>RangeReadable 接口允许 Iceberg 按需读取文件的特定区间（如 footer），避免全量下载， 对 Parquet/ORC 等列存格式尤其重要。
 *   <li>通过 {@code finalize} 兜底检测未关闭的流并记录告警，帮助排查资源泄漏； {@code createStack} 在构造时捕获调用栈用于告警定位。
 * </ul>
 *
 * <p>上下游关系：由 {@link GCSInputFile#newStream()} 创建；被 Iceberg 读路径 （Parquet/ORC reader 等）使用。
 */
class GCSInputStream extends SeekableInputStream implements RangeReadable {
  private static final Logger LOG = LoggerFactory.getLogger(GCSInputStream.class);

  private final StackTraceElement[] createStack;
  private final Storage storage;
  private final BlobId blobId;
  private Long blobSize;
  private final GCPProperties gcpProperties;

  private ReadChannel channel;
  private long pos = 0;
  private boolean closed = false;
  private final ByteBuffer singleByteBuffer = ByteBuffer.wrap(new byte[1]);
  private ByteBuffer byteBuffer;

  private final Counter readBytes;
  private final Counter readOperations;

  /**
   * 构造 GCSInputStream 并立即打开读取通道。
   *
   * <p>逻辑：初始化各字段、创建读取字节/操作数计数器、捕获构造调用栈（用于 finalize 告警）， 然后调用 {@link #openStream()} 打开 GCS
   * ReadChannel。
   *
   * @param storage GCS Storage 客户端
   * @param blobId 目标对象 BlobId
   * @param blobSize 文件大小，null 表示未知（readTail 时按需查询）
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文
   */
  GCSInputStream(
      Storage storage,
      BlobId blobId,
      Long blobSize,
      GCPProperties gcpProperties,
      MetricsContext metrics) {
    this.storage = storage;
    this.blobId = blobId;
    this.blobSize = blobSize;
    this.gcpProperties = gcpProperties;

    this.readBytes = metrics.counter(FileIOMetricsContext.READ_BYTES, Unit.BYTES);
    this.readOperations = metrics.counter(FileIOMetricsContext.READ_OPERATIONS);

    createStack = Thread.currentThread().getStackTrace();

    openStream();
  }

  /** 打开读取流（创建新的 ReadChannel）。 */
  private void openStream() {
    channel = openChannel();
  }

  /**
   * 创建并配置一个 GCS ReadChannel。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>构建 {@link BlobSourceOption} 列表：若配置了解密 Key 则添加 {@link
   *       BlobSourceOption#decryptionKey(String)}；若配置了 userProject 则添加 {@link
   *       BlobSourceOption#userProject(String)}。
   *   <li>调用 {@link Storage#reader(BlobId, BlobSourceOption...)} 创建 ReadChannel。
   *   <li>若配置了读通道 chunk 大小则设置到 channel。
   * </ol>
   *
   * @return 配置好的 ReadChannel
   */
  private ReadChannel openChannel() {
    List<BlobSourceOption> sourceOptions = Lists.newArrayList();

    gcpProperties
        .decryptionKey()
        .ifPresent(key -> sourceOptions.add(BlobSourceOption.decryptionKey(key)));
    gcpProperties
        .userProject()
        .ifPresent(userProject -> sourceOptions.add(BlobSourceOption.userProject(userProject)));

    ReadChannel result = storage.reader(blobId, sourceOptions.toArray(new BlobSourceOption[0]));

    gcpProperties.channelReadChunkSize().ifPresent(result::setChunkSize);

    return result;
  }

  /** 返回当前流的位置（字节偏移）。 */
  @Override
  public long getPos() {
    return pos;
  }

  /**
   * 将流定位到指定位置。
   *
   * <p>逻辑：校验流未关闭且目标位置非负，更新 pos 并调用 {@link ReadChannel#seek(long)} 定位底层通道。seek 失败的 IOException 包装为
   * UncheckedIOException 抛出。
   *
   * @param newPos 目标位置（字节偏移）
   * @throws IllegalStateException 若流已关闭
   * @throws IllegalArgumentException 若 newPos 为负
   */
  @Override
  public void seek(long newPos) {
    Preconditions.checkState(!closed, "already closed");
    Preconditions.checkArgument(newPos >= 0, "position is negative: %s", newPos);

    pos = newPos;
    try {
      channel.seek(newPos);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 读取单个字节。
   *
   * <p>逻辑：校验流未关闭，利用单字节缓冲区 {@code singleByteBuffer} 从 channel 读取一个 字节，pos 加
   * 1，更新读字节/操作计数器，返回无符号字节值（0~255）。
   *
   * @return 读取的字节值（0~255），若到达流末尾返回 -1
   * @throws IOException 若读取失败
   * @throws IllegalStateException 若流已关闭
   */
  @Override
  public int read() throws IOException {
    Preconditions.checkState(!closed, "Cannot read: already closed");
    singleByteBuffer.position(0);

    pos += 1;
    channel.read(singleByteBuffer);
    readBytes.increment();
    readOperations.increment();

    return singleByteBuffer.array()[0] & 0xFF;
  }

  /**
   * 将数据读入字节数组的指定区间。
   *
   * <p>逻辑：校验流未关闭，复用或创建 {@link ByteBuffer} 包装目标数组，委托 {@link #read(ReadChannel, ByteBuffer, int,
   * int)} 读取，更新 pos 和计数器。
   *
   * @param b 目标字节数组
   * @param off 起始偏移
   * @param len 读取长度
   * @return 实际读取的字节数，-1 表示到达末尾
   * @throws IOException 若读取失败
   * @throws IllegalStateException 若流已关闭
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    Preconditions.checkState(!closed, "Cannot read: already closed");
    byteBuffer = byteBuffer != null && byteBuffer.array() == b ? byteBuffer : ByteBuffer.wrap(b);
    int bytesRead = read(channel, byteBuffer, off, len);
    pos += bytesRead;
    readBytes.increment(bytesRead);
    readOperations.increment();
    return bytesRead;
  }

  /**
   * 从指定位置读取完整数据填满缓冲区（RangeReadable 接口）。
   *
   * <p>逻辑：打开新的 ReadChannel（不影响主通道），seek 到 position 并设置 limit 为 position+length，读取指定长度数据。若实际读取不足
   * length 则抛出 {@link EOFException}。 使用 try-with-resources 确保通道关闭。
   *
   * @param position 起始字节位置
   * @param buffer 目标缓冲区
   * @param offset 缓冲区偏移
   * @param length 读取长度
   * @throws IOException 若读取失败或数据不足
   */
  @Override
  public void readFully(long position, byte[] buffer, int offset, int length) throws IOException {
    try (ReadChannel readChannel = openChannel()) {
      readChannel.seek(position);
      readChannel.limit(position + length);
      int bytesRead = read(readChannel, ByteBuffer.wrap(buffer), offset, length);
      if (bytesRead < length) {
        throw new EOFException(
            "Reached the end of stream with " + (length - bytesRead) + " bytes left to read");
      }
    }
  }

  /**
   * 读取文件尾部数据（RangeReadable 接口）。
   *
   * <p>逻辑：若 blobSize 未知则先从 GCS 查询；计算起始位置 = max(0, blobSize - length)； 打开新通道 seek 到起始位置后读取 length
   * 字节。用于读取文件 footer（如 Parquet 的 FileMagic 和 footer length）。
   *
   * @param buffer 目标缓冲区
   * @param offset 缓冲区偏移
   * @param length 读取长度
   * @return 实际读取的字节数
   * @throws IOException 若读取失败
   */
  @Override
  public int readTail(byte[] buffer, int offset, int length) throws IOException {
    if (blobSize == null) {
      blobSize = storage.get(blobId).getSize();
    }
    long startPosition = Math.max(0, blobSize - length);
    try (ReadChannel readChannel = openChannel()) {
      readChannel.seek(startPosition);
      return read(readChannel, ByteBuffer.wrap(buffer), offset, length);
    }
  }

  /**
   * 内部读取辅助：设置 buffer 的 position 和 limit 后从 ReadChannel 读取。
   *
   * @param readChannel GCS 读通道
   * @param buffer 目标缓冲区
   * @param off 缓冲区偏移
   * @param len 读取长度
   * @return 实际读取的字节数
   * @throws IOException 若读取失败
   */
  private int read(ReadChannel readChannel, ByteBuffer buffer, int off, int len)
      throws IOException {
    buffer.position(off);
    buffer.limit(Math.min(off + len, buffer.capacity()));
    return readChannel.read(buffer);
  }

  /**
   * 关闭输入流并释放 GCS ReadChannel 资源。
   *
   * @throws IOException 若关闭通道失败
   */
  @Override
  public void close() throws IOException {
    super.close();
    closed = true;
    if (channel != null) {
      channel.close();
    }
  }

  /**
   * finalize 兜底：若流未被显式关闭则关闭并记录告警。
   *
   * <p>设计意图：资源释放优先于告警。通过构造时捕获的 createStack 定位未关闭流的 创建位置，帮助开发者排查资源泄漏。 suppressed checkstyle
   * NoFinalizer 警告。
   */
  @SuppressWarnings("checkstyle:NoFinalizer")
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!closed) {
      close(); // releasing resources is more important than printing the warning
      String trace = Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
      LOG.warn("Unclosed input stream created by:\n\t{}", trace);
    }
  }
}
