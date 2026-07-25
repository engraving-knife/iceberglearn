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
package org.apache.iceberg.aws.s3;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.apache.iceberg.exceptions.NotFoundException;
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
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.Abortable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * 基于 S3 的可寻址输入流：支持随机读取和范围读取。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link org.apache.iceberg.io.SeekableInputStream}，支持 seek 和顺序读取。
 *   <li>实现 {@link org.apache.iceberg.io.RangeReadable}，支持 readFully（指定位置）和 readTail（尾部读取）。
 *   <li>通过 S3 GetObject range 请求实现按需定位读取，避免下载整个对象。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>惰性打开流：仅在真正读取时才发起 GetObject 请求，减少不必要的网络开销。
 *   <li>读穿缓存（read-through seek）：向前 seek 较小距离时，直接跳过流中已缓冲的数据， 避免重新发起新的 GetObject 请求；仅在大距离 seek 或向后
 *       seek 时重新打开流。
 *   <li>范围读取优化：readFully 和 readTail 直接发起带 range 的 GetObject 请求， 不影响当前流的位置，适合 Iceberg 的列式读取场景。
 *   <li>通过 finalize 兜底检测未关闭的流，记录创建栈帮助排查资源泄漏。
 * </ul>
 *
 * <p>上下游关系：由 {@link S3InputFile} 创建，被 Iceberg 的列式读取器（Parquet/ORC）使用 进行随机范围读取。
 */
class S3InputStream extends SeekableInputStream implements RangeReadable {
  private static final Logger LOG = LoggerFactory.getLogger(S3InputStream.class);

  private final StackTraceElement[] createStack;
  private final S3Client s3;
  private final S3URI location;
  private final S3FileIOProperties s3FileIOProperties;

  private InputStream stream;
  private long pos = 0;
  private long next = 0;
  private boolean closed = false;

  private final Counter readBytes;
  private final Counter readOperations;

  private int skipSize = 1024 * 1024;

  /** 简化构造器（使用默认属性和空指标）。 */
  S3InputStream(S3Client s3, S3URI location) {
    this(s3, location, new S3FileIOProperties(), MetricsContext.nullMetrics());
  }

  /**
   * 构造 S3 输入流。
   *
   * @param s3 S3 客户端
   * @param location S3 URI
   * @param s3FileIOProperties S3 FileIO 属性
   * @param metrics 指标上下文
   */
  S3InputStream(
      S3Client s3, S3URI location, S3FileIOProperties s3FileIOProperties, MetricsContext metrics) {
    this.s3 = s3;
    this.location = location;
    this.s3FileIOProperties = s3FileIOProperties;

    this.readBytes = metrics.counter(FileIOMetricsContext.READ_BYTES, Unit.BYTES);
    this.readOperations = metrics.counter(FileIOMetricsContext.READ_OPERATIONS);

    this.createStack = Thread.currentThread().getStackTrace();
  }

  @Override
  public long getPos() {
    return next;
  }

  @Override
  public void seek(long newPos) {
    Preconditions.checkState(!closed, "already closed");
    Preconditions.checkArgument(newPos >= 0, "position is negative: %s", newPos);

    // this allows a seek beyond the end of the stream but the next read will fail
    next = newPos;
  }

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

  @Override
  public void readFully(long position, byte[] buffer, int offset, int length) throws IOException {
    Preconditions.checkPositionIndexes(offset, offset + length, buffer.length);

    String range = String.format("bytes=%s-%s", position, position + length - 1);

    IOUtil.readFully(readRange(range), buffer, offset, length);
  }

  @Override
  public int readTail(byte[] buffer, int offset, int length) throws IOException {
    Preconditions.checkPositionIndexes(offset, offset + length, buffer.length);

    String range = String.format("bytes=-%s", length);

    return IOUtil.readRemaining(readRange(range), buffer, offset, length);
  }

  /**
   * 发起带 range 头的 S3 GetObject 请求并返回输入流。
   *
   * @param range HTTP Range 头值（如 "bytes=0-99"）
   * @return S3 响应输入流
   */
  private InputStream readRange(String range) {
    GetObjectRequest.Builder requestBuilder =
        GetObjectRequest.builder().bucket(location.bucket()).key(location.key()).range(range);

    S3RequestUtil.configureEncryption(s3FileIOProperties, requestBuilder);

    return s3.getObject(requestBuilder.build(), ResponseTransformer.toInputStream());
  }

  @Override
  public void close() throws IOException {
    super.close();
    closed = true;
    closeStream();
  }

  /**
   * 将流定位到 next 指定的位置。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若流已存在且 next == pos，已在目标位置，直接返回。
   *   <li>若向前 seek 且距离不超过 max(available, skipSize)，尝试跳过缓冲数据（读穿缓存）。
   *   <li>否则关闭当前流并重新打开（openStream），从 pos 位置开始读取。
   * </ul>
   *
   * @throws IOException IO 异常
   */
  private void positionStream() throws IOException {
    if ((stream != null) && (next == pos)) {
      // already at specified position
      return;
    }

    if ((stream != null) && (next > pos)) {
      // seeking forwards
      long skip = next - pos;
      if (skip <= Math.max(stream.available(), skipSize)) {
        // already buffered or seek is small enough
        LOG.debug("Read-through seek for {} to offset {}", location, next);
        try {
          ByteStreams.skipFully(stream, skip);
          pos = next;
          return;
        } catch (IOException ignored) {
          // will retry by re-opening the stream
        }
      }
    }

    // close the stream and open at desired position
    LOG.debug("Seek with new stream for {} to offset {}", location, next);
    pos = next;
    openStream();
  }

  /**
   * 从当前位置 pos 打开新的 S3 GetObject 流（使用 range 请求）。
   *
   * <p>逻辑：构建带 range 的 GetObject 请求（bytes=pos-），配置加密参数， 关闭旧流后发起新请求。对象不存在时抛 NotFoundException。
   *
   * @throws IOException 对象不存在或 IO 异常
   */
  private void openStream() throws IOException {
    GetObjectRequest.Builder requestBuilder =
        GetObjectRequest.builder()
            .bucket(location.bucket())
            .key(location.key())
            .range(String.format("bytes=%s-", pos));

    S3RequestUtil.configureEncryption(s3FileIOProperties, requestBuilder);

    closeStream();

    try {
      stream = s3.getObject(requestBuilder.build(), ResponseTransformer.toInputStream());
    } catch (NoSuchKeyException e) {
      throw new NotFoundException(e, "Location does not exist: %s", location);
    }
  }

  /**
   * 关闭当前流：先 abort（避免 Apache HTTP client 读取剩余数据），再 close。 Apache HTTP client 关闭 aborted 流时抛
   * ConnectionClosedException 属正常行为，被忽略。
   *
   * @throws IOException 关闭异常（ConnectionClosedException 除外）
   */
  private void closeStream() throws IOException {
    if (stream != null) {
      // if we aren't at the end of the stream, and the stream is abortable, then
      // call abort() so we don't read the remaining data with the Apache HTTP client
      abortStream();
      try {
        stream.close();
      } catch (IOException e) {
        // the Apache HTTP client will throw a ConnectionClosedException
        // when closing an aborted stream, which is expected
        if (!e.getClass().getSimpleName().equals("ConnectionClosedException")) {
          throw e;
        }
      }
      stream = null;
    }
  }

  private void abortStream() {
    try {
      if (stream instanceof Abortable && stream.read() != -1) {
        ((Abortable) stream).abort();
      }
    } catch (Exception e) {
      LOG.warn("An error occurred while aborting the stream", e);
    }
  }

  public void setSkipSize(int skipSize) {
    this.skipSize = skipSize;
  }

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
