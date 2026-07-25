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
package org.apache.iceberg.inmemory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：内存版 InputFile 实现，把文件内容保存在字节缓冲区中。
 *
 * <p>所属模块：iceberg-core（inmemory 子包）。职责：实现 {@link org.apache.iceberg.io.InputFile}
 * 接口，从内存字节缓冲区读取文件内容，提供长度查询和输入流创建。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不依赖磁盘 IO，所有读取在内存完成，适合测试。
 *   <li>内部用 InMemorySeekableInputStream 支持随机定位读取。
 * </ul>
 *
 * <p>上下游关系：由 {@link InMemoryFileIO} 创建；被读取链路当作普通 InputFile 使用。
 */
public class InMemoryInputFile implements InputFile {

  private final String location;
  private final byte[] contents;

  public InMemoryInputFile(byte[] contents) {
    this("memory:" + UUID.randomUUID(), contents);
  }

  public InMemoryInputFile(String location, byte[] contents) {
    Preconditions.checkNotNull(location, "location is null");
    Preconditions.checkNotNull(contents, "contents is null");
    this.location = location;
    this.contents = contents.clone();
  }

  @Override
  /**
   * 返回文件内容长度。
   *
   * @return 文件字节数
   */
  public long getLength() {
    return contents.length;
  }

  @Override
  /**
   * 创建支持随机定位的输入流。
   *
   * <p>设计要点：返回 InMemorySeekableInputStream，包装字节数组提供 seek 能力。
   *
   * @return 可定位输入流
   */
  public SeekableInputStream newStream() {
    return new InMemorySeekableInputStream(contents);
  }

  @Override
  /**
   * 返回文件路径标识。
   *
   * @return 文件路径字符串
   */
  public String location() {
    return location;
  }

  @Override
  /**
   * 检查文件是否存在（内存中始终存在）。
   *
   * @return true
   */
  public boolean exists() {
    return true;
  }

  private static class InMemorySeekableInputStream extends SeekableInputStream {

    private final long length;
    private final ByteArrayInputStream delegate;
    private boolean closed = false;

    InMemorySeekableInputStream(byte[] contents) {
      this.length = contents.length;
      this.delegate = new ByteArrayInputStream(contents);
    }

    @Override
    public long getPos() throws IOException {
      checkOpen();
      return length - delegate.available();
    }

    @Override
    public void seek(long newPos) throws IOException {
      checkOpen();
      delegate.reset(); // resets to a marked position
      Preconditions.checkState(
          delegate.skip(newPos) == newPos,
          "Invalid position %s within stream of length %s",
          newPos,
          length);
    }

    @Override
    public int read() {
      checkOpen();
      return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
      checkOpen();
      return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) {
      checkOpen();
      return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) {
      checkOpen();
      return delegate.skip(n);
    }

    @Override
    public int available() {
      checkOpen();
      return delegate.available();
    }

    @Override
    public boolean markSupported() {
      return false;
    }

    @Override
    public void mark(int readAheadLimit) {
      // The delegate's mark is used to implement seek
      throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
      checkOpen();
      delegate.reset();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
      closed = true;
    }

    private void checkOpen() {
      // ByteArrayInputStream can be used even after close, so for test purposes disallow such use
      // explicitly
      Preconditions.checkState(!closed, "Stream is closed");
    }
  }
}
