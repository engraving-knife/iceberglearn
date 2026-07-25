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
package org.apache.iceberg.hadoop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FSInputStream;
import org.apache.iceberg.io.DelegatingInputStream;
import org.apache.iceberg.io.DelegatingOutputStream;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Hadoop 数据流与 Iceberg 抽象流之间的适配工具类。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：提供 {@link FSDataInputStream} ↔ {@link SeekableInputStream}、 {@link FSDataOutputStream} ↔
 * {@link PositionOutputStream}、 以及 {@link SeekableInputStream} → {@link FSInputStream} 的双向包装方法。
 *
 * <p>设计意图：基于 Parquet 的 HadoopStreams 实现。Iceberg 上层读写 API 仅依赖 {@link SeekableInputStream}/{@link
 * PositionOutputStream} 抽象，本类负责把它们与 Hadoop 原生流互转。包装类在 {@code finalize} 中检测未关闭的流并打印创建堆栈告警， 帮助排查资源泄漏。
 *
 * <p>上下游关系：被 {@link HadoopInputFile}、{@link HadoopOutputFile} 等同包类用于 创建读写流；上层 Parquet/ORC/Avro
 * 读写器通过这些抽象流访问 Hadoop 文件系统。
 */
public class HadoopStreams {

  private HadoopStreams() {}

  private static final Logger LOG = LoggerFactory.getLogger(HadoopStreams.class);

  /**
   * 把 Hadoop {@link FSDataInputStream} 包装为 Iceberg {@link SeekableInputStream}，供读取器使用。
   *
   * @param stream Hadoop 数据输入流
   * @return 可定位的输入流
   */
  static SeekableInputStream wrap(FSDataInputStream stream) {
    return new HadoopSeekableInputStream(stream);
  }

  /**
   * 把 Hadoop {@link FSDataOutputStream} 包装为 Iceberg {@link PositionOutputStream}，供写入器使用。
   *
   * @param stream Hadoop 数据输出流
   * @return 可定位的输出流
   */
  static PositionOutputStream wrap(FSDataOutputStream stream) {
    return new HadoopPositionOutputStream(stream);
  }

  /**
   * 把 Iceberg {@link SeekableInputStream} 包装为 Hadoop {@link FSInputStream}。
   *
   * <p>用途：当某些 Hadoop 内置组件需要 {@link FSInputStream} 时，可借此桥接回 Iceberg 流。
   *
   * @param stream Iceberg 可定位输入流
   * @return 适配为 Hadoop {@link FSInputStream} 的视图
   */
  public static FSInputStream wrap(SeekableInputStream stream) {
    return new WrappedSeekableInputStream(stream);
  }

  /**
   * 内部类：将 Hadoop {@link FSDataInputStream} 适配为 Iceberg {@link SeekableInputStream}。
   *
   * <p>设计要点：记录构造时的堆栈用于泄漏告警；{@code finalize} 检测未关闭时打印告警并尽力释放资源。 在 Hadoop 2 上还提供 {@code
   * read(ByteBuffer)} 能力。
   */
  private static class HadoopSeekableInputStream extends SeekableInputStream
      implements DelegatingInputStream {
    private final FSDataInputStream stream;
    private final StackTraceElement[] createStack;
    private boolean closed;

    HadoopSeekableInputStream(FSDataInputStream stream) {
      this.stream = stream;
      this.createStack = Thread.currentThread().getStackTrace();
      this.closed = false;
    }

    @Override
    public InputStream getDelegate() {
      return stream;
    }

    @Override
    public void close() throws IOException {
      stream.close();
      this.closed = true;
    }

    @Override
    public long getPos() throws IOException {
      return stream.getPos();
    }

    @Override
    public void seek(long newPos) throws IOException {
      stream.seek(newPos);
    }

    @Override
    public int read() throws IOException {
      return stream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return stream.read(b, off, len);
    }

    public int read(ByteBuffer buf) throws IOException {
      return stream.read(buf);
    }

    /**
     * GC 回收时若仍未关闭，则尝试关闭并打印创建堆栈，用于排查流泄漏。
     *
     * <p>设计意图：资源释放优先于告警，避免因告警失败而泄漏句柄。
     */
    @SuppressWarnings("checkstyle:NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
      super.finalize();
      if (!closed) {
        close(); // releasing resources is more important than printing the warning
        String trace =
            Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
        LOG.warn("Unclosed input stream created by:\n\t{}", trace);
      }
    }
  }

  /**
   * 内部类：将 Hadoop {@link FSDataOutputStream} 适配为 Iceberg {@link PositionOutputStream}。
   *
   * <p>与 {@link HadoopSeekableInputStream} 类似，记录创建堆栈并在 finalize 中检测未关闭情况。
   */
  private static class HadoopPositionOutputStream extends PositionOutputStream
      implements DelegatingOutputStream {
    private final FSDataOutputStream stream;
    private final StackTraceElement[] createStack;
    private boolean closed;

    HadoopPositionOutputStream(FSDataOutputStream stream) {
      this.stream = stream;
      this.createStack = Thread.currentThread().getStackTrace();
      this.closed = false;
    }

    @Override
    public OutputStream getDelegate() {
      return stream;
    }

    @Override
    public long getPos() throws IOException {
      return stream.getPos();
    }

    @Override
    public void write(int b) throws IOException {
      stream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      stream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      stream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      stream.flush();
    }

    @Override
    public void close() throws IOException {
      stream.close();
      this.closed = true;
    }

    /** GC 回收时若仍未关闭，则尝试关闭并打印创建堆栈，用于排查流泄漏。 */
    @SuppressWarnings("checkstyle:NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
      super.finalize();
      if (!closed) {
        close(); // releasing resources is more important than printing the warning
        String trace =
            Joiner.on("\n\t").join(Arrays.copyOfRange(createStack, 1, createStack.length));
        LOG.warn("Unclosed output stream created by:\n\t{}", trace);
      }
    }
  }

  /**
   * 内部类：把 Iceberg {@link SeekableInputStream} 包装为 Hadoop {@link FSInputStream}， 用于反向桥接给需要 Hadoop
   * 流的组件。
   *
   * <p>{@code seekToNewSource} 不支持，抛出 {@link UnsupportedOperationException}。
   */
  private static class WrappedSeekableInputStream extends FSInputStream
      implements DelegatingInputStream {
    private final SeekableInputStream inputStream;

    private WrappedSeekableInputStream(SeekableInputStream inputStream) {
      this.inputStream = inputStream;
    }

    @Override
    public void seek(long pos) throws IOException {
      inputStream.seek(pos);
    }

    @Override
    public long getPos() throws IOException {
      return inputStream.getPos();
    }

    @Override
    public boolean seekToNewSource(long targetPos) throws IOException {
      throw new UnsupportedOperationException("seekToNewSource not supported");
    }

    @Override
    public int read() throws IOException {
      return inputStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return inputStream.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
      inputStream.close();
    }

    @Override
    public InputStream getDelegate() {
      return inputStream;
    }
  }
}
