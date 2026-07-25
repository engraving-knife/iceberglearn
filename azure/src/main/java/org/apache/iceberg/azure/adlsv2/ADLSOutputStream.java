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

import com.azure.storage.common.ParallelTransferOptions;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.options.DataLakeFileOutputStreamOptions;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import org.apache.iceberg.azure.AzureProperties;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ADLS 输出流，支持位置追踪。
 *
 * <p>所属模块：iceberg-azure（Azure 存储后端 FileIO 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link PositionOutputStream}，向 ADLS 文件写入数据并追踪写入位置。
 *   <li>通过 {@link BufferedOutputStream} 包装 ADLS 原始输出流，减少远程写入次数。
 *   <li>通过 {@link MetricsContext} 记录写入字节数和写入操作数。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>缓冲写入：ADLS 每次写都是一次 HTTP 请求，直接逐字节写会有巨大网络开销。 通过 {@link BufferedOutputStream} 在内存中累积数据，批量发送到
 *       ADLS。
 *   <li>位置追踪：{@code pos} 字段记录已写入字节数，供 Iceberg 上层（如 Parquet writer） 获取当前位置用于写入文件尾部的元数据偏移。
 *   <li>{@code finalize} 兜底：捕获创建调用栈，若流未被关闭则 GC 时告警并释放资源。
 * </ul>
 *
 * <p>上下游关系：由 {@link ADLSOutputFile#createOrOverwrite} 创建，被 Iceberg core 的 写入层（如 Parquet/ORC/Avro
 * writer）使用进行数据写入。
 */
class ADLSOutputStream extends PositionOutputStream {
  private static final Logger LOG = LoggerFactory.getLogger(ADLSOutputStream.class);

  private final StackTraceElement[] createStack;
  private final DataLakeFileClient fileClient;
  private final AzureProperties azureProperties;

  private OutputStream stream;

  private final Counter writeBytes;
  private final Counter writeOperations;

  private long pos;
  private boolean closed;

  /**
   * 构造 ADLS 输出流并立即打开底层流。
   *
   * <p>记录创建调用栈用于 {@link #finalize()} 泄漏检测。
   *
   * @param fileClient 文件客户端
   * @param azureProperties Azure 配置（用于写入块大小）
   * @param metrics 指标上下文
   * @throws IOException 打开底层流失败
   */
  ADLSOutputStream(
      DataLakeFileClient fileClient, AzureProperties azureProperties, MetricsContext metrics)
      throws IOException {
    this.fileClient = fileClient;
    this.azureProperties = azureProperties;

    this.createStack = Thread.currentThread().getStackTrace();

    this.writeBytes = metrics.counter(FileIOMetricsContext.WRITE_BYTES, Unit.BYTES);
    this.writeOperations = metrics.counter(FileIOMetricsContext.WRITE_OPERATIONS);

    openStream();
  }

  /** 返回当前写入位置（已写入的字节数）。 */
  @Override
  public long getPos() {
    return pos;
  }

  /** 刷新缓冲区，将未写出的数据推送到 ADLS。 */
  @Override
  public void flush() throws IOException {
    stream.flush();
  }

  /**
   * 写入单个字节。
   *
   * @param b 要写入的字节（0~255）
   * @throws IOException 写入失败
   */
  @Override
  public void write(int b) throws IOException {
    stream.write(b);
    pos += 1;
    writeBytes.increment();
    writeOperations.increment();
  }

  /**
   * 写入字节数组的指定范围。
   *
   * @param b 源缓冲区
   * @param off 起始偏移
   * @param len 写入长度
   * @throws IOException 写入失败
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    stream.write(b, off, len);
    pos += len;
    writeBytes.increment(len);
    writeOperations.increment();
  }

  /**
   * 打开底层 ADLS 输出流并包装为缓冲流。
   *
   * <p>逻辑：构建 {@link DataLakeFileOutputStreamOptions}，若配置了写入块大小则设置到 {@link
   * ParallelTransferOptions}，然后通过 {@link DataLakeFileClient#getOutputStream} 获取原始流，再用 {@link
   * BufferedOutputStream} 包装。
   */
  private void openStream() {
    DataLakeFileOutputStreamOptions options = new DataLakeFileOutputStreamOptions();
    ParallelTransferOptions transferOptions = new ParallelTransferOptions();
    azureProperties.adlsWriteBlockSize().ifPresent(transferOptions::setBlockSizeLong);
    this.stream = new BufferedOutputStream(fileClient.getOutputStream(options));
  }

  /**
   * 关闭输出流并标记为已关闭。
   *
   * <p>若已关闭则直接返回，避免重复关闭。
   *
   * @throws IOException 关闭底层流失败
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

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
      LOG.warn("Unclosed output stream created by:\n\t{}", trace);
    }
  }
}
