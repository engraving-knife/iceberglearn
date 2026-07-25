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
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.apache.iceberg.aliyun.AliyunProperties;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：基于阿里云 OSS 的可定位输出流。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本类继承 iceberg-api 的 {@link PositionOutputStream}，为 {@link
 * OSSOutputFile} 提供底层字节写入能力。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将写入数据先缓存到本地 staging 临时文件，关闭时整体上传到 OSS。
 *   <li>提供 {@link #getPos()} 返回当前写入位置，支持 Iceberg 写入位置追踪。
 *   <li>上报写入字节数与写入操作数指标。
 *   <li>资源泄漏防护：通过 {@link #finalize()} 兜底关闭未关闭的流并告警。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>暂存再上传策略：OSS SDK 的 {@code putObject} 不支持高效的随机写/追加写， 故采用“先写本地临时文件，close 时一次性 putObject
 *       上传”的方式，保证写入语义正确 且避免多次小对象上传开销。
 *   <li>staging 目录可配：通过 {@link AliyunProperties#ossStagingDirectory()} 指定本地暂存目录， 默认使用 {@code
 *       java.io.tmpdir}。
 *   <li>finalize 防泄漏：捕获构造时调用栈，若流未被显式关闭则在 GC 时关闭并记录警告， 防止本地临时文件与连接泄漏。
 * </ul>
 *
 * <p>上下游关系：被 {@link OSSOutputFile#create} / {@link OSSOutputFile#createOrOverwrite} 创建；
 * 下游依赖本地文件系统（staging）与阿里云 OSS SDK 的 {@code putObject} 接口。
 */
public class OSSOutputStream extends PositionOutputStream {
  private static final Logger LOG = LoggerFactory.getLogger(OSSOutputStream.class);
  private final StackTraceElement[] createStack;

  private final OSS client;
  private final OSSURI uri;

  private final File currentStagingFile;
  private final OutputStream stream;
  private long pos = 0;
  private boolean closed = false;

  private final Counter writeBytes;
  private final Counter writeOperations;

  /**
   * 构造 OSS 输出流。
   *
   * <p>逻辑：保存客户端与 URI，捕获当前调用栈（用于 finalize 定位），在 staging 目录创建 临时文件并打开缓冲输出流，最后从 {@link MetricsContext}
   * 获取写入指标计数器。
   *
   * @param client OSS 客户端
   * @param uri 目标对象的 OSS URI
   * @param aliyunProperties 阿里云配置属性（用于获取 staging 目录）
   * @param metrics 度量上下文
   */
  OSSOutputStream(
      OSS client, OSSURI uri, AliyunProperties aliyunProperties, MetricsContext metrics) {
    this.client = client;
    this.uri = uri;
    this.createStack = Thread.currentThread().getStackTrace();

    this.currentStagingFile = newStagingFile(aliyunProperties.ossStagingDirectory());
    this.stream = newStream(currentStagingFile);
    this.writeBytes = metrics.counter(FileIOMetricsContext.WRITE_BYTES, Unit.BYTES);
    this.writeOperations = metrics.counter(FileIOMetricsContext.WRITE_OPERATIONS);
  }

  /**
   * 在 staging 目录创建临时文件，并标记 JVM 退出时删除。
   *
   * @param ossStagingDirectory staging 目录路径
   * @return 创建好的临时文件
   * @throws UncheckedIOException 若创建临时文件时发生 IO 错误
   */
  private static File newStagingFile(String ossStagingDirectory) {
    try {
      File stagingFile = File.createTempFile("oss-file-io-", ".tmp", new File(ossStagingDirectory));
      stagingFile.deleteOnExit();
      return stagingFile;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 为指定 staging 文件打开缓冲输出流。
   *
   * @param currentStagingFile staging 文件
   * @return 包装了 {@link BufferedOutputStream} 的输出流
   * @throws NotFoundException 若文件无法创建（如路径不存在）
   */
  private static OutputStream newStream(File currentStagingFile) {
    try {
      return new BufferedOutputStream(new FileOutputStream(currentStagingFile));
    } catch (FileNotFoundException e) {
      throw new NotFoundException(e, "Failed to create file: %s", currentStagingFile);
    }
  }

  /**
   * 打开 staging 文件的输入流（用于上传时读取），异常包装为非受检异常。
   *
   * @param file staging 文件
   * @return 文件输入流
   * @throws UncheckedIOException 若打开时发生 IO 错误
   */
  private static InputStream uncheckedInputStream(File file) {
    try {
      return new FileInputStream(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 返回当前已写入的字节位置。 */
  @Override
  public long getPos() {
    return pos;
  }

  /**
   * 刷新缓冲流，将数据刷到本地 staging 文件。
   *
   * @throws IOException 若刷新时发生 IO 错误
   * @throws IllegalStateException 若流已关闭
   */
  @Override
  public void flush() throws IOException {
    Preconditions.checkState(!closed, "Already closed.");
    stream.flush();
  }

  /**
   * 写入单个字节。
   *
   * <p>逻辑：校验未关闭后写入 staging 流，同步更新 pos 与写入指标。
   *
   * @param b 待写入的字节
   * @throws IOException 若写入时发生 IO 错误
   * @throws IllegalStateException 若流已关闭
   */
  @Override
  public void write(int b) throws IOException {
    Preconditions.checkState(!closed, "Already closed.");
    stream.write(b);
    pos += 1;
    writeBytes.increment();
    writeOperations.increment();
  }

  /**
   * 将字节数组指定区间写入 staging 流。
   *
   * <p>逻辑：校验未关闭后委托底层流写入，按 len 同步更新 pos 与写入指标。
   *
   * @param b 源字节数组
   * @param off 起始偏移
   * @param len 写入长度
   * @throws IOException 若写入时发生 IO 错误
   * @throws IllegalStateException 若流已关闭
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    Preconditions.checkState(!closed, "Already closed.");
    stream.write(b, off, len);
    pos += len;
    writeBytes.increment(len);
    writeOperations.increment();
  }

  /**
   * 关闭输出流并完成上传。
   *
   * <p>逻辑：若已关闭则直接返回；否则调用父类 close、标记 closed，然后关闭 staging 流并 {@link #completeUploads()} 上传到
   * OSS，最后无论上传成功与否都 {@link #cleanUpStagingFiles()} 清理本地临时文件。
   *
   * @throws IOException 若关闭或上传时发生 IO 错误
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }

    super.close();
    closed = true;

    try {
      stream.close();
      completeUploads();
    } finally {
      cleanUpStagingFiles();
    }
  }

  /**
   * 将 staging 文件内容上传到 OSS。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若 staging 文件为空（contentLength == 0），跳过上传并记录 debug 日志。
   *   <li>否则打开 staging 文件输入流，构造 {@link ObjectMetadata} 设置 ContentLength， 通过 {@link
   *       PutObjectRequest} + {@link OSS#putObject} 上传到 OSS。
   * </ul>
   */
  private void completeUploads() {
    long contentLength = currentStagingFile.length();
    if (contentLength == 0) {
      LOG.debug("Skipping empty upload to OSS");
      return;
    }

    LOG.debug("Uploading {} staged bytes to OSS", contentLength);
    InputStream contentStream = uncheckedInputStream(currentStagingFile);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentLength(contentLength);

    PutObjectRequest request =
        new PutObjectRequest(uri.bucket(), uri.key(), contentStream, metadata);
    client.putObject(request);
  }

  /**
   * 清理本地 staging 临时文件。
   *
   * <p>删除失败时仅记录警告日志，不抛出异常。
   */
  private void cleanUpStagingFiles() {
    if (!currentStagingFile.delete()) {
      LOG.warn("Failed to delete staging file: {}", currentStagingFile);
    }
  }

  /**
   * GC 兜底：若流未被显式关闭，则在此关闭并记录警告日志（含创建时调用栈）。
   *
   * <p>设计意图：防止因调用方忘记 close 导致本地 staging 文件与 OSS 连接泄漏。 优先释放资源而非仅打印警告。
   */
  @SuppressWarnings("checkstyle:NoFinalizer")
  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    if (!closed) {
      close(); // releasing resources is more important than printing the warning.
      String trace = Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
      LOG.warn("Unclosed output stream created by:\n\t{}", trace);
    }
  }
}
