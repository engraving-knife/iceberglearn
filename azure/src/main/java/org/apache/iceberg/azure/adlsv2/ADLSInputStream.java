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
package org.apache.iceberg.azure.adlsv2;

import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.models.DataLakeFileOpenInputStreamResult;
import com.azure.storage.file.datalake.models.FileRange;
import com.azure.storage.file.datalake.options.DataLakeFileInputStreamOptions;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.apache.iceberg.azure.AzureProperties;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.IOUtil;
import org.apache.iceberg.io.RangeReadable;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ADLS 可寻址输入流，支持随机范围读取。
 *
 * <p>所属模块：iceberg-azure（Azure 存储后端 FileIO 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link SeekableInputStream} 与 {@link RangeReadable}，提供对 ADLS 文件的 顺序读取、随机寻址和范围读取能力。
 *   <li>维护当前读取位置（pos / next），在小范围前向跳转时优先 skip 复用已有流， 大跳转时重新打开流以避免无效读取。
 *   <li>通过 {@link MetricsContext} 记录读取字节数和读取操作数。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>双位置追踪：{@code pos} 表示底层流当前实际位置，{@code next} 表示上层期望的 读取位置。两者分离使得 seek 操作只需更新 next，真正定位延迟到下次
 *       read 时 （惰性定位），减少不必要的远程调用。
 *   <li>Skip 优化：当前向跳转距离不超过 {@link #SKIP_SIZE}（1MB）或流中已有缓冲数据时， 通过 skip 跳过而非重开流，避免重新建立 HTTP 连接的开销。
 *   <li>{@code finalize} 兜底：捕获创建时的调用栈，若流未被关闭则 GC 时告警并释放资源， 防止资源泄漏。
 * </ul>
 *
 * <p>上下游关系：由 {@link ADLSInputFile#newStream} 创建，被 Iceberg core 的读取层 （如 Parquet/ORC/Avro
 * reader）使用进行数据读取。
 */
class ADLSInputStream extends SeekableInputStream implements RangeReadable {
  private static final Logger LOG = LoggerFactory.getLogger(ADLSInputStream.class);

  // 跳过阈值：若前向 skip 超过此值（1MB），则直接重新打开流而非 skip
  private static final int SKIP_SIZE = 1024 * 1024;

  private final StackTraceElement[] createStack;
  private final DataLakeFileClient fileClient;
  private Long fileSize;
  private final AzureProperties azureProperties;

  private InputStream stream;
  private long pos;
  private long next;
  private boolean closed;

  private final Counter readBytes;
  private final Counter readOperations;

  /**
   * 构造 ADLS 输入流并立即打开底层流。
   *
   * <p>初始位置为 0。同时记录创建调用栈，用于 {@link #finalize()} 中检测未关闭泄漏。
   *
   * @param fileClient 文件客户端
   * @param fileSize 已知文件大小；为 null 时从服务端获取
   * @param azureProperties Azure 配置（用于读取块大小）
   * @param metrics 指标上下文
   */
  ADLSInputStream(
      DataLakeFileClient fileClient,
      Long fileSize,
      AzureProperties azureProperties,
      MetricsContext metrics) {
    this.fileClient = fileClient;
    this.fileSize = fileSize;
    this.azureProperties = azureProperties;

    this.readBytes = metrics.counter(FileIOMetricsContext.READ_BYTES, Unit.BYTES);
    this.readOperations = metrics.counter(FileIOMetricsContext.READ_OPERATIONS);

    this.createStack = Thread.currentThread().getStackTrace();

    openStream();
  }

  /**
   * 在当前 {@code pos} 位置打开底层 ADLS 输入流。
   *
   * <p>逻辑：构建 {@link DataLakeFileInputStreamOptions}（设置读取块大小和起始范围）， 调用 {@link
   * DataLakeFileClient#openInputStream} 获取流，同时更新 fileSize。
   */
  private void openStream() {
    DataLakeFileOpenInputStreamResult result =
        fileClient.openInputStream(getInputOptions(new FileRange(pos)));
    this.fileSize = result.getProperties().getFileSize();
    this.stream = result.getInputStream();
  }

  /**
   * 构建输入流选项。
   *
   * @param range 读取范围
   * @return 配置好块大小和范围的 {@link DataLakeFileInputStreamOptions}
   */
  private DataLakeFileInputStreamOptions getInputOptions(FileRange range) {
    DataLakeFileInputStreamOptions options = new DataLakeFileInputStreamOptions();
    azureProperties.adlsReadBlockSize().ifPresent(options::setBlockSize);
    options.setRange(range);
    return options;
  }

  /** 返回上层期望的下一个读取位置（即 seek 目标位置）。 */
  @Override
  public long getPos() {
    return next;
  }

  /**
   * 寻址到指定位置。
   *
   * <p>仅更新 {@code next}，不立即移动底层流。实际定位在下次 {@link #read} 时由 {@link #positionStream} 完成（惰性定位）。允许 seek
   * 到流末尾之后，但后续 read 会失败。
   *
   * @param newPos 目标位置
   * @throws IllegalStateException 流已关闭
   * @throws IllegalArgumentException newPos 为负
   */
  @Override
  public void seek(long newPos) {
    Preconditions.checkState(!closed, "Cannot seek: already closed");
    Preconditions.checkArgument(newPos >= 0, "Cannot seek: position %s is negative", newPos);

    // this allows a seek beyond the end of the stream but the next read will fail
    this.next = newPos;
  }

  /**
   * 读取单个字节。
   *
   * @return 0~255 的字节值；到达流末尾返回 -1
   * @throws IOException 读取失败或流已关闭
   */
  @Override
  public int read() throws IOException {
    Preconditions.checkState(!closed, "Cannot read: already closed");
    positionStream();

    pos += 1;
    next += 1;
    readBytes.increment();
    readOperations.increment();

    return stream.read();
  }

  /**
   * 读取字节到指定缓冲区。
   *
   * @param b 目标缓冲区
   * @param off 起始偏移
   * @param len 读取长度
   * @return 实际读取的字节数；到达流末尾返回 -1
   * @throws IOException 读取失败或流已关闭
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    Preconditions.checkState(!closed, "Cannot read: already closed");
    positionStream();

    int bytesRead = stream.read(b, off, len);
    pos += bytesRead;
    next += bytesRead;
    readBytes.increment(bytesRead);
    readOperations.increment();

    return bytesRead;
  }

  /**
   * 将底层流定位到 {@code next} 位置。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若 {@code next == pos}，流已在正确位置，直接返回。
   *   <li>若为前向跳转且跳转距离不超过 max(available, SKIP_SIZE)，则尝试 skip； skip 失败则回退到重开流。
   *   <li>其他情况（大跳转或后向跳转）：关闭当前流，在 {@code next} 位置重新打开。
   * </ul>
   *
   * @throws IOException skip 或重开流失败
   */
  private void positionStream() throws IOException {
    if ((stream != null) && (next == pos)) {
      // already at specified position
      return;
    }

    if ((stream != null) && (next > pos)) {
      // seeking forwards
      long skip = next - pos;
      if (skip <= Math.max(stream.available(), SKIP_SIZE)) {
        // already buffered or seek is small enough
        try {
          ByteStreams.skipFully(stream, skip);
          this.pos = next;
          return;
        } catch (IOException ignored) {
          // will retry by re-opening the stream
        }
      }
    }

    // close the stream and open at desired position
    this.pos = next;
    openStream();
  }

  /**
   * 从指定绝对位置读取完整的一段数据填满缓冲区。
   *
   * <p>实现 {@link RangeReadable#readFully}，独立于当前流位置，直接打开指定范围读取。
   *
   * @param position 文件中的绝对起始位置
   * @param buffer 目标缓冲区
   * @param offset 缓冲区偏移
   * @param length 读取长度
   * @throws IOException 读取失败或数据不足
   */
  @Override
  public void readFully(long position, byte[] buffer, int offset, int length) throws IOException {
    Preconditions.checkPositionIndexes(offset, offset + length, buffer.length);

    FileRange range = new FileRange(position, position + length);

    IOUtil.readFully(openRange(range), buffer, offset, length);
  }

  /**
   * 读取文件尾部指定长度的数据。
   *
   * <p>逻辑：先确定文件总大小（若未知则从服务端获取），计算起始位置 = fileSize - length， 然后打开对应范围读取剩余数据。
   *
   * @param buffer 目标缓冲区
   * @param offset 缓冲区偏移
   * @param length 尾部读取长度
   * @return 实际读取的字节数
   * @throws IOException 读取失败
   */
  @Override
  public int readTail(byte[] buffer, int offset, int length) throws IOException {
    Preconditions.checkPositionIndexes(offset, offset + length, buffer.length);

    if (this.fileSize == null) {
      this.fileSize = fileClient.getProperties().getFileSize();
    }
    long readStart = fileSize - length;

    return IOUtil.readRemaining(openRange(new FileRange(readStart)), buffer, offset, length);
  }

  /**
   * 打开指定范围的独立输入流（不影响当前主流位置）。
   *
   * @param range 读取范围
   * @return 范围对应的输入流
   */
  private InputStream openRange(FileRange range) {
    return fileClient.openInputStream(getInputOptions(range)).getInputStream();
  }

  /**
   * 关闭输入流并标记为已关闭。
   *
   * @throws IOException 关闭底层流失败
   */
  @Override
  public void close() throws IOException {
    super.close();
    this.closed = true;
    if (stream != null) {
      stream.close();
    }
  }

  /**
   * GC 兜底：若流未被显式关闭，则在此释放资源并记录警告日志（含创建调用栈）。
   *
   * <p>资源释放优先于日志打印，避免在 finalize 中因日志异常导致资源泄漏。
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
