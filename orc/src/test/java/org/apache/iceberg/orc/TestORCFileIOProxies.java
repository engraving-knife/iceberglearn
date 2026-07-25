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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.Files;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestORCFileIOProxies 的功能。
 *
 * <p>所属模块：iceberg-orc。职责：验证 TestORCFileIOProxies 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestORCFileIOProxies {
  /**
   * 测试场景：Input File System。
   *
   * <p>验证该方法在 Input File System 条件下的行为是否符合预期。
   */
  @Test
  public void testInputFileSystem() throws IOException {
    File inputFile = File.createTempFile("read", ".orc");
    inputFile.deleteOnExit();

    InputFile localFile = Files.localInput(inputFile);
    FileIOFSUtil.InputFileSystem ifs = new FileIOFSUtil.InputFileSystem(localFile);
    InputStream is = ifs.open(new Path(localFile.location()));
    Assertions.assertThat(is).isNotNull();

    // Cannot use the filesystem for any other operation
    Assertions.assertThatThrownBy(() -> ifs.getFileStatus(new Path(localFile.location())))
        .isInstanceOf(UnsupportedOperationException.class);

    // Cannot use the filesystem for any other path
    Assertions.assertThatThrownBy(() -> ifs.open(new Path("/tmp/dummy")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Input /tmp/dummy does not equal expected");
  }

  /**
   * 测试场景：Output File System。
   *
   * <p>验证该方法在 Output File System 条件下的行为是否符合预期。
   */
  @Test
  public void testOutputFileSystem() throws IOException {
    File localFile = File.createTempFile("write", ".orc");
    localFile.deleteOnExit();

    OutputFile outputFile = Files.localOutput(localFile);
    FileSystem ofs = new FileIOFSUtil.OutputFileSystem(outputFile);
    try (OutputStream os = ofs.create(new Path(outputFile.location()))) {
      os.write('O');
      os.write('R');
      os.write('C');
    }
    // No other operation is supported
    Assertions.assertThatThrownBy(() -> ofs.open(new Path(outputFile.location())))
        .isInstanceOf(UnsupportedOperationException.class);
    // No other path is supported
    Assertions.assertThatThrownBy(() -> ofs.create(new Path("/tmp/dummy")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Input /tmp/dummy does not equal expected");

    FileSystem ifs = new FileIOFSUtil.InputFileSystem(outputFile.toInputFile());
    try (InputStream is = ifs.open(new Path(outputFile.location()))) {
      Assertions.assertThat(is.read()).isEqualTo('O');
      Assertions.assertThat(is.read()).isEqualTo('R');
      Assertions.assertThat(is.read()).isEqualTo('C');
      Assertions.assertThat(is.read()).isEqualTo(-1);
    }
  }
}
