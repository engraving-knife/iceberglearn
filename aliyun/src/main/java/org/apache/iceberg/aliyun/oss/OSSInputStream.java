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
package org.apache.iceberg.aliyun.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GetObjectRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.apache.iceberg.io.FileIOMetricsContext;
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
 * 文件级说明：基于阿里云 OSS 的可定位读取流。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本类实现 iceberg-api 的 {@link SeekableInputStream}，为 {@link
 * OSSInputFile} 提供底层字节读取能力，支持 随机定位（seek）后的按需读取。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装 OSS {@code GetObject} 范围读请求，对外暴露标准 {@code read} 接口。
 *   <li>支持 {@link #seek(long)} 跳转读取位置，小范围跳转优先“读透（read-through）”避免重开流。
 *   <li>上报读取字节数与读取操作数指标。
 *   <li>资源泄漏防护：通过 {@link #finalize()} 兜底关闭未关闭的流并告警。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>双位置变量：{@code pos} 表示底层流当前实际位置，{@code next} 表示上层期望的下一个读取位置。 两者分离使 seek 仅记录目标位置，真正发生 read
 *       时才决定是否重开流，避免无谓的远程请求。
 *   <li>读透优化：向前小范围 seek（&lt;= 流已缓冲量或 {@link #SKIP_SIZE}）时直接跳过字节， 避免关闭并重开 OSS 流带来的网络开销。
 *   <li>范围读：每次重开流时通过 {@code GetObjectRequest.withRange(pos, -1)} 发起 Range 请求， 从指定偏移开始读取。
 *   <li>finalize 防泄漏：捕获构造时调用栈，若流未被显式关闭则在 GC 时关闭并记录警告， 防止 OSS 连接泄漏。
 * </ul>
 *
 * <p>上下游关系：被 {@link OSSInputFile#newStream} 创建；下游依赖阿里云 OSS SDK 的 {@code getObject} 范围读接口。
 */
class OSSInputStream extends SeekableInputStream {
  private static final Logger LOG = LoggerFactory.getLogger(OSSInputStream.class);
  private static final int SKIP_SIZE = 1024 * 1024;

  private final StackTraceElement[] createStack;
  private final OSS client;
  private final OSSURI uri;

  private InputStream stream = null;
  private long pos = 0;
  private long next = 0;
  private boolean closed = false;

  private final Counter readBytes;
  private final Counter readOperations;

  /**
   * 构造读取流，使用空指标上下文。
   *
   * @param client OSS 客户端
   * @param uri 读取对象的 OSS URI
   */
  OSSInputStream(OSS client, OSSURI uri) {
    this(client, uri, MetricsContext.nullMetrics());
  }

  /**
   * 构造读取流。
   *
   * <p>逻辑：保存客户端与 URI，捕获当前调用栈（用于 finalize 时定位未关闭流的创建位置）， 并从 {@link MetricsContext} 获取读取字节数与读取操作数计数器。
   *
   * @param client OSS 客户端
   * @param uri 读取对象的 OSS URI
   * @param metrics 度量上下文
   */
  OSSInputStream(OSS client, OSSURI uri, MetricsContext metrics) {
    this.client = client;
    this.uri = uri;
    this.createStack = Thread.currentThread().getStackTrace();

    this.readBytes = metrics.counter(FileIOMetricsContext.READ_BYTES, Unit.BYTES);
    this.readOperations = metrics.counter(FileIOMetricsContext.READ_OPERATIONS);
  }

  /** 返回上层期望的下一个读取位置（{@code next}）。 */
  @Override
  public long getPos() {
    return next;
  }

  /**
   * 将读取位置跳转到指定偏移。
   *
   * <p>逻辑：仅更新 {@code next}，不立即重开流；允许 seek 超过流末尾，但下一次 read 会失败。
   *
   * @param newPos 新的读取位置（必须 &gt;= 0）
   * @throws IllegalStateException 若流已关闭
   * @throws IllegalArgumentException 若 newPos 为负数
   */
  @Override
  public void seek(long newPos) {
    Preconditions.checkState(!closed, "Cannot seek: already closed");
    Preconditions.checkArgument(newPos >= 0, "Position is negative: %s", newPos);

    // this allows a seek beyond the end of the stream but the next read will fail
    next = newPos;
  }

  /**
   * 读取单个字节。
   *
   * <p>逻辑：先 {@link #positionStream()} 确保底层流定位到 {@code next}，再读取一字节， 同步更新 {@code pos} 与 {@code
   * next}，并累加读取指标。
   *
   * @return 读取到的字节值（0-255），到达流末尾返回 -1
   * @throws IOException 若发生 IO 错误
   * @throws IllegalStateException 若流已关闭
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
   * 将数据读入字节数组的指定区间。
   *
   * <p>逻辑：先 {@link #positionStream()} 定位底层流，再委托 {@link InputStream#read(byte[], int, int)}
   * 读取，按实际读取字节数同步更新 {@code pos}/{@code next} 与指标。
   *
   * @param b 目标缓冲区
   * @param off 起始偏移
   * @param len 最大读取长度
   * @return 实际读取字节数，到达流末尾返回 -1
   * @throws IOException 若发生 IO 错误
   * @throws IllegalStateException 若流已关闭
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
   * 关闭读取流。
   *
   * <p>逻辑：若已关闭则直接返回；否则调用父类 close、关闭底层流并标记 closed。
   *
   * @throws IOException 若关闭底层流时发生 IO 错误
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

    super.close();
    closeStream();
    closed = true;
  }

  /**
   * 将底层流定位到 {@code next} 指定位置。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若流已存在且 {@code next == pos}（已定位），直接返回。
   *   <li>若向前 seek 且跳过量较小（&lt;= 流已缓冲量或 {@link #SKIP_SIZE}），则尝试 {@link ByteStreams#skipFully}
   *       读透跳过，成功后更新 pos 并返回。
   *   <li>否则关闭当前流并按 {@code next} 偏移重新打开 Range 请求。
   * </ul>
   *
   * @throws IOException 若重开流时发生 IO 错误
   */
  private void positionStream() throws IOException {
    if ((stream != null) && (next == pos)) {
      // already at specified position.
      return;
    }

    if ((stream != null) && (next > pos)) {
      // seeking forwards
      long skip = next - pos;
      if (skip <= Math.max(stream.available(), SKIP_SIZE)) {
        // already buffered or seek is small enough
        LOG.debug("Read-through seek for {} from {} to offset {}", uri, pos, next);
        try {
          ByteStreams.skipFully(stream, skip);
          pos = next;
          return;
        } catch (IOException ignored) {
          // will retry by re-opening the stream.
        }
      }
    }

    // close the stream and open at desired position.
    LOG.debug("Seek with new stream for {} to offset {}", uri, next);
    pos = next;
    openStream();
  }

  /**
   * 关闭当前底层流并从 {@code pos} 偏移重新打开 Range 读取流。
   *
   * <p>逻辑：先 {@link #closeStream()}，再构造 {@link GetObjectRequest} 设置 Range {@code [pos, -1]}（从 pos
   * 读到末尾），通过 {@link OSS#getObject} 获取内容流。
   *
   * @throws IOException 若获取对象流时发生 IO 错误
   */
  private void openStream() throws IOException {
    closeStream();

    GetObjectRequest request = new GetObjectRequest(uri.bucket(), uri.key()).withRange(pos, -1);
    stream = client.getObject(request).getObjectContent();
  }

  /**
   * 关闭底层流并置空。
   *
   * @throws IOException 若关闭时发生 IO 错误
   */
  private void closeStream() throws IOException {
    if (stream != null) {
      stream.close();
      stream = null;
    }
  }

  /**
   * GC 兜底：若流未被显式关闭，则在此关闭并记录警告日志（含创建时调用栈）。
   *
   * <p>设计意图：防止因调用方忘记 close 导致 OSS 连接泄漏。优先释放资源而非仅打印警告。
   */
  @SuppressWarnings("checkstyle:NoFinalizer")
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!closed) {
      close(); // releasing resources is more important than printing the warning
      String trace = Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
      LOG.warn("Unclosed input stream created by: \n\t{}", trace);
    }
  }
}
