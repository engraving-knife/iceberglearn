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
package org.apache.iceberg.orc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.apache.iceberg.hadoop.HadoopStreams;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 将 Iceberg {@link InputFile}/{@link OutputFile} 适配为 Hadoop {@link FileSystem} 的工具类。
 *
 * <p>所属模块：iceberg-orc。ORC 的 Reader/Writer API 需要 Hadoop FileSystem， 而 Iceberg 抽象 IO 为
 * InputFile/OutputFile，本类在两者之间架桥。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link InputFileSystem}：包装 InputFile 为只读 FileSystem，支持 open()。
 *   <li>{@link OutputFileSystem}：包装 OutputFile 为只写 FileSystem，支持 create()。
 * </ul>
 *
 * <p>设计意图：NullFileSystem 把所有不需要的方法抛 UnsupportedOperationException， 仅覆写实际用到的 open/create，避免实现完整的
 * FileSystem 接口。ORC 读写仅通过 这两个方法访问文件，故此最小适配即可满足需求。
 *
 * <p>上下游关系：被 {@link ORC} 的读写入口内部使用。
 */
class FileIOFSUtil {
  private FileIOFSUtil() {}

  /**
   * 空实现 FileSystem：所有方法抛 UnsupportedOperationException， 作为 InputFileSystem/OutputFileSystem
   * 的基类，仅覆写需要的方法。
   */
  private static class NullFileSystem extends FileSystem {

    @Override
    public URI getUri() {
      throw new UnsupportedOperationException();
    }

    @Override
    public FSDataInputStream open(Path f) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FSDataInputStream open(Path f, int bufferSize) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FSDataOutputStream create(
        Path f,
        FsPermission permission,
        boolean overwrite,
        int bufferSize,
        short replication,
        long blockSize,
        Progressable progress)
        throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FSDataOutputStream append(Path f, int bufferSize, Progressable progress)
        throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean rename(Path src, Path dst) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete(Path f, boolean recursive) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileStatus[] listStatus(Path f) throws FileNotFoundException, IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setWorkingDirectory(Path new_dir) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Path getWorkingDirectory() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean mkdirs(Path f, FsPermission permission) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileStatus getFileStatus(Path f) throws IOException {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * 只读 FileSystem 适配器：把 {@link InputFile} 包装为 Hadoop FileSystem。
   *
   * <p>设计要点：open() 校验路径与 inputFile.location() 一致后，用 {@link HadoopStreams#wrap} 把 Iceberg 输入流转为
   * Hadoop FSDataInputStream。
   */
  static class InputFileSystem extends NullFileSystem {
    private final InputFile inputFile;
    private final Path inputPath;

    InputFileSystem(InputFile inputFile) {
      this.inputFile = inputFile;
      this.inputPath = new Path(inputFile.location());
    }

    @Override
    public FSDataInputStream open(Path f) throws IOException {
      Preconditions.checkArgument(
          f.equals(inputPath), String.format("Input %s does not equal expected %s", f, inputPath));
      return new FSDataInputStream(HadoopStreams.wrap(inputFile.newStream()));
    }

    @Override
    public FSDataInputStream open(Path f, int bufferSize) throws IOException {
      return open(f);
    }
  }

  /**
   * 只写 FileSystem 适配器：把 {@link OutputFile} 包装为 Hadoop FileSystem。
   *
   * <p>设计要点：create() 校验路径一致后，按 overwrite 选择 createOrOverwrite()/create()， 包装为 FSDataOutputStream
   * 返回。
   */
  static class OutputFileSystem extends NullFileSystem {
    private final OutputFile outputFile;
    private final Path outPath;

    OutputFileSystem(OutputFile outputFile) {
      this.outputFile = outputFile;
      this.outPath = new Path(outputFile.location());
    }

    @Override
    public FSDataOutputStream create(Path f, boolean overwrite) throws IOException {
      Preconditions.checkArgument(
          f.equals(outPath), String.format("Input %s does not equal expected %s", f, outPath));
      OutputStream outputStream = overwrite ? outputFile.createOrOverwrite() : outputFile.create();
      return new FSDataOutputStream(outputStream, null);
    }

    @Override
    public FSDataOutputStream create(
        Path f,
        FsPermission permission,
        boolean overwrite,
        int bufferSize,
        short replication,
        long blockSize,
        Progressable progress)
        throws IOException {
      return create(f, overwrite);
    }
  }
}
