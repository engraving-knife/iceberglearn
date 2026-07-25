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
package org.apache.iceberg;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Paths;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.io.SeekableInputStream;

/**
 * 本地文件系统 {@link InputFile}/{@link OutputFile} 工厂与实现。
 *
 * <p>所属模块：iceberg-api（IO 抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为本地 {@link File} 创建 {@link OutputFile} 与 {@link InputFile} 实例；
 *   <li>提供基于 {@link RandomAccessFile} 的可定位输入/输出流实现，支持按位置读写。
 * </ul>
 *
 * <p>设计意图：Iceberg 通过 {@link InputFile}/{@link OutputFile} 抽象屏蔽底层存储差异，
 * 本类是本地文件系统的参考实现，常用于测试与单机场景。其他存储（HDFS/S3 等）由各自模块 提供等价实现。
 *
 * <p>上下游关系：被 core 模块及测试代码用于读写本地 manifest/数据文件。
 */
public class Files {

  private Files() {}

  /** 为本地 {@link File} 创建 {@link OutputFile}。 */
  public static OutputFile localOutput(File file) {
    return new LocalOutputFile(file);
  }

  /** 为本地路径字符串创建 {@link OutputFile}。 */
  public static OutputFile localOutput(String file) {
    return localOutput(Paths.get(file).toAbsolutePath().toFile());
  }

  /**
   * 本地文件系统的 {@link OutputFile} 实现。
   *
   * <p>设计要点：创建文件前会确保父目录存在；{@link #create()} 不允许覆盖已有文件， {@link #createOrOverwrite()} 会先删除再创建。
   */
  private static class LocalOutputFile implements OutputFile {
    private final File file;

    private LocalOutputFile(File file) {
      this.file = file;
    }

    /**
     * 创建新文件并返回可定位输出流。
     *
     * <p>逻辑：文件已存在则抛 {@link AlreadyExistsException}；父目录不存在则创建；最后用 {@link RandomAccessFile} 以 "rw"
     * 模式打开并包装为 PositionFileOutputStream。
     *
     * @throws AlreadyExistsException 文件已存在
     * @throws RuntimeIOException 父目录创建失败
     * @throws NotFoundException 文件创建失败
     */
    @Override
    public PositionOutputStream create() {
      if (file.exists()) {
        throw new AlreadyExistsException("File already exists: %s", file);
      }

      if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) {
        throw new RuntimeIOException(
            "Failed to create the file's directory at %s.", file.getParentFile().getAbsolutePath());
      }

      try {
        return new PositionFileOutputStream(file, new RandomAccessFile(file, "rw"));
      } catch (FileNotFoundException e) {
        throw new NotFoundException(e, "Failed to create file: %s", file);
      }
    }

    /** 创建或覆盖文件：若已存在则先删除，再调用 {@link #create()}。 */
    @Override
    public PositionOutputStream createOrOverwrite() {
      if (file.exists()) {
        if (!file.delete()) {
          throw new RuntimeIOException("Failed to delete: %s", file);
        }
      }
      return create();
    }

    @Override
    public String location() {
      return file.toString();
    }

    @Override
    public InputFile toInputFile() {
      return localInput(file);
    }

    @Override
    public String toString() {
      return location();
    }
  }

  /** 为本地 {@link File} 创建 {@link InputFile}。 */
  public static InputFile localInput(File file) {
    return new LocalInputFile(file);
  }

  /**
   * 为本地路径字符串创建 {@link InputFile}。
   *
   * <p>设计要点：若路径以 {@code file:} 前缀开头，会先剥离该前缀再构造 File，避免 URI scheme 影响本地路径解析。
   */
  public static InputFile localInput(String file) {
    if (file.startsWith("file:")) {
      return localInput(new File(file.replaceFirst("file:", "")));
    }
    return localInput(new File(file));
  }

  /**
   * 本地文件系统的 {@link InputFile} 实现。
   *
   * <p>设计要点：通过 {@link RandomAccessFile} 以只读模式打开，支持按位置 seek。
   */
  private static class LocalInputFile implements InputFile {
    private final File file;

    private LocalInputFile(File file) {
      this.file = file;
    }

    @Override
    public long getLength() {
      return file.length();
    }

    /**
     * 打开新的可定位输入流。
     *
     * <p>逻辑：以 "r" 只读模式打开 {@link RandomAccessFile}，包装为 {@link SeekableFileInputStream}；文件不存在则抛
     * {@link NotFoundException}。
     *
     * @throws NotFoundException 文件不存在
     */
    @Override
    public SeekableInputStream newStream() {
      try {
        return new SeekableFileInputStream(new RandomAccessFile(file, "r"));
      } catch (FileNotFoundException e) {
        throw new NotFoundException(e, "Failed to read file: %s", file);
      }
    }

    @Override
    public String location() {
      return file.toString();
    }

    @Override
    public boolean exists() {
      return file.exists();
    }

    @Override
    public String toString() {
      return location();
    }
  }

  /** 基于 {@link RandomAccessFile} 的可定位输入流实现，支持 {@link #getPos()} 与 {@link #seek(long)}。 */
  private static class SeekableFileInputStream extends SeekableInputStream {
    private final RandomAccessFile stream;

    private SeekableFileInputStream(RandomAccessFile stream) {
      this.stream = stream;
    }

    @Override
    public long getPos() throws IOException {
      return stream.getFilePointer();
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
    public int read(byte[] b) throws IOException {
      return stream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return stream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
      if (n > Integer.MAX_VALUE) {
        return stream.skipBytes(Integer.MAX_VALUE);
      } else {
        return stream.skipBytes((int) n);
      }
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }
  }

  /**
   * 基于 {@link RandomAccessFile} 的可定位输出流实现。
   *
   * <p>设计要点：{@link #getPos()} 在流已关闭时返回文件长度，避免对已关闭流读取位置时 抛异常；通过 {@code isClosed} 标志区分。
   */
  private static class PositionFileOutputStream extends PositionOutputStream {
    private final File file;
    private final RandomAccessFile stream;
    private boolean isClosed = false;

    private PositionFileOutputStream(File file, RandomAccessFile stream) {
      this.file = file;
      this.stream = stream;
    }

    @Override
    public long getPos() throws IOException {
      if (isClosed) {
        return file.length();
      }
      return stream.getFilePointer();
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
    public void write(int b) throws IOException {
      stream.write(b);
    }

    @Override
    public void close() throws IOException {
      stream.close();
      this.isClosed = true;
    }
  }
}
