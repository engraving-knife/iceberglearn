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
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobWriteOption;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.Arrays;
import java.util.List;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GCS 输出流：基于 GCS 原生流式通道实现写入。
 *
 * <p>所属模块：iceberg-gcp（GCP 集成模块，处于 Iceberg 存储访问层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link PositionOutputStream}，提供可追踪写入位置的输出流。
 *   <li>利用 GCS {@link WriteChannel} 进行流式上传，支持配置 chunk 大小、加密 Key、 用户项目等选项。
 *   <li>记录写入字节数和写入操作数指标。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>利用 GCS 原生流式上传能力，参见 <a href="https://cloud.google.com/storage/docs/streaming">Streaming
 *       Transfers</a>， 将 OutputStream 适配为基于 Channel 的写入。
 *   <li>通过 {@code finalize} 兜底检测未关闭的流并记录告警，帮助排查资源泄漏； {@code createStack} 在构造时捕获调用栈用于告警定位。
 *   <li>PositionOutputStream 维护 pos 计数器，供 Iceberg 上层获取已写入字节数。
 * </ul>
 *
 * <p>上下游关系：由 {@link GCSOutputFile#create()} / {@link GCSOutputFile#createOrOverwrite()} 创建；被
 * Iceberg 写路径（Parquet/ORC writer 等）使用。
 */
class GCSOutputStream extends PositionOutputStream {
  private static final Logger LOG = LoggerFactory.getLogger(GCSOutputStream.class);

  private final StackTraceElement[] createStack;
  private final Storage storage;
  private final BlobId blobId;
  private final GCPProperties gcpProperties;

  private OutputStream stream;

  private final Counter writeBytes;
  private final Counter writeOperations;

  private long pos = 0;
  private boolean closed = false;

  /**
   * 构造 GCSOutputStream 并立即打开写入通道。
   *
   * <p>逻辑：初始化各字段、创建写入字节/操作数计数器、捕获构造调用栈（用于 finalize 告警）， 然后调用 {@link #openStream()} 打开 GCS
   * WriteChannel 并包装为 OutputStream。
   *
   * @param storage GCS Storage 客户端
   * @param blobId 目标对象 BlobId
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文
   * @throws IOException 若打开写入通道失败
   */
  GCSOutputStream(
      Storage storage, BlobId blobId, GCPProperties gcpProperties, MetricsContext metrics)
      throws IOException {
    this.storage = storage;
    this.blobId = blobId;
    this.gcpProperties = gcpProperties;

    createStack = Thread.currentThread().getStackTrace();

    this.writeBytes = metrics.counter(FileIOMetricsContext.WRITE_BYTES, Unit.BYTES);
    this.writeOperations = metrics.counter(FileIOMetricsContext.WRITE_OPERATIONS);

    openStream();
  }

  /** 返回当前已写入的字节位置。 */
  @Override
  public long getPos() {
    return pos;
  }

  /** 刷新底层输出流。 */
  @Override
  public void flush() throws IOException {
    stream.flush();
  }

  /**
   * 写入单个字节。
   *
   * <p>逻辑：委托底层 stream 写入，pos 加 1，更新写入字节/操作计数器。
   *
   * @param b 要写入的字节（低 8 位）
   * @throws IOException 若写入失败
   */
  @Override
  public void write(int b) throws IOException {
    stream.write(b);
    pos += 1;
    writeBytes.increment();
    writeOperations.increment();
  }

  /**
   * 写入字节数组的指定区间。
   *
   * <p>逻辑：委托底层 stream 写入，pos 增加 len，按写入字节数更新计数器。
   *
   * @param b 源字节数组
   * @param off 起始偏移
   * @param len 写入长度
   * @throws IOException 若写入失败
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    stream.write(b, off, len);
    pos += len;
    writeBytes.increment(len);
    writeOperations.increment();
  }

  /**
   * 打开写入流：创建并配置 GCS WriteChannel。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>构建 {@link BlobWriteOption} 列表：若配置了加密 Key 则添加 {@link
   *       BlobWriteOption#encryptionKey(String)}；若配置了 userProject 则添加 {@link
   *       BlobWriteOption#userProject(String)}。
   *   <li>用 {@link BlobInfo#newBuilder(BlobId)} 构建 BlobInfo 并调用 {@link Storage#writer(BlobInfo,
   *       BlobWriteOption...)} 创建 WriteChannel。
   *   <li>若配置了写通道 chunk 大小则设置到 channel。
   *   <li>通过 {@link Channels#newOutputStream} 将 WriteChannel 包装为 OutputStream。
   * </ol>
   */
  private void openStream() {
    List<BlobWriteOption> writeOptions = Lists.newArrayList();

    gcpProperties
        .encryptionKey()
        .ifPresent(key -> writeOptions.add(BlobWriteOption.encryptionKey(key)));
    gcpProperties
        .userProject()
        .ifPresent(userProject -> writeOptions.add(BlobWriteOption.userProject(userProject)));

    WriteChannel channel =
        storage.writer(
            BlobInfo.newBuilder(blobId).build(), writeOptions.toArray(new BlobWriteOption[0]));

    gcpProperties.channelWriteChunkSize().ifPresent(channel::setChunkSize);

    stream = Channels.newOutputStream(channel);
  }

  /**
   * 关闭输出流并释放资源。
   *
   * <p>逻辑：若已关闭则直接返回；否则调用父类 close、标记 closed、关闭底层 stream。 关闭 WriteChannel 会触发 GCS 上传完成。
   *
   * @throws IOException 若关闭失败
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

    super.close();
    closed = true;
    stream.close();
  }

  /**
   * finalize 兜底：若流未被显式关闭则关闭并记录告警。
   *
   * <p>设计意图：资源释放优先于告警。通过构造时捕获的 createStack 定位未关闭流的 创建位置，帮助开发者排查资源泄漏。suppressed checkstyle
   * NoFinalizer 警告。
   */
  @SuppressWarnings("checkstyle:NoFinalizer")
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!closed) {
      close(); // releasing resources is more important than printing the warning
      String trace = Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
      LOG.warn("Unclosed output stream created by:\n\t{}", trace);
    }
  }
}
