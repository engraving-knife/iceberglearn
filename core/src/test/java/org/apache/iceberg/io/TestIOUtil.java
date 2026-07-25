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
package org.apache.iceberg.io;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.iceberg.inmemory.InMemoryOutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestIOUtil，用于验证 IO Util 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 IO Util 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestIOUtil {
  /**
   * 测试场景：read fully。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadFully() throws Exception {
    byte[] buffer = new byte[5];

    MockInputStream stream = new MockInputStream();
    IOUtil.readFully(stream, buffer, 0, buffer.length);

    Assertions.assertThat(buffer)
        .as("Byte array contents should match")
        .isEqualTo(Arrays.copyOfRange(MockInputStream.TEST_ARRAY, 0, 5));

    Assertions.assertThat(stream.getPos())
        .as("Stream position should reflect bytes read")
        .isEqualTo(5);
  }

  /**
   * 测试场景：read fully small reads。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadFullySmallReads() throws Exception {
    byte[] buffer = new byte[5];

    MockInputStream stream = new MockInputStream(2, 3, 3);
    IOUtil.readFully(stream, buffer, 0, buffer.length);

    Assertions.assertThat(buffer)
        .as("Byte array contents should match")
        .containsExactly(Arrays.copyOfRange(MockInputStream.TEST_ARRAY, 0, 5));

    Assertions.assertThat(stream.getPos())
        .as("Stream position should reflect bytes read")
        .isEqualTo(5);
  }

  /**
   * 测试场景：read fully just right。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadFullyJustRight() throws Exception {
    final byte[] buffer = new byte[10];

    final MockInputStream stream = new MockInputStream(2, 3, 3);
    IOUtil.readFully(stream, buffer, 0, buffer.length);

    Assertions.assertThat(buffer)
        .as("Byte array contents should match")
        .isEqualTo(MockInputStream.TEST_ARRAY);

    Assertions.assertThat(stream.getPos())
        .as("Stream position should reflect bytes read")
        .isEqualTo(10);

    Assertions.assertThatThrownBy(() -> IOUtil.readFully(stream, buffer, 0, 1))
        .isInstanceOf(EOFException.class)
        .hasMessage("Reached the end of stream with 1 bytes left to read");
  }

  /**
   * 测试场景：read fully underflow。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadFullyUnderflow() {
    final byte[] buffer = new byte[11];

    final MockInputStream stream = new MockInputStream(2, 3, 3);

    Assertions.assertThatThrownBy(() -> IOUtil.readFully(stream, buffer, 0, buffer.length))
        .isInstanceOf(EOFException.class)
        .hasMessage("Reached the end of stream with 1 bytes left to read");

    Assertions.assertThat(Arrays.copyOfRange(buffer, 0, 10))
        .as("Should have consumed bytes")
        .isEqualTo(MockInputStream.TEST_ARRAY);

    Assertions.assertThat(stream.getPos())
        .as("Stream position should reflect bytes read")
        .isEqualTo(10);
  }

  /**
   * 测试场景：read fully start and length。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadFullyStartAndLength() throws IOException {
    byte[] buffer = new byte[10];

    MockInputStream stream = new MockInputStream();
    IOUtil.readFully(stream, buffer, 2, 5);

    Assertions.assertThat(Arrays.copyOfRange(buffer, 2, 7))
        .as("Byte array contents should match")
        .isEqualTo(Arrays.copyOfRange(MockInputStream.TEST_ARRAY, 0, 5));

    Assertions.assertThat(stream.getPos())
        .as("Stream position should reflect bytes read")
        .isEqualTo(5);
  }

  /**
   * 测试场景：read fully zero byte read。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadFullyZeroByteRead() throws IOException {
    byte[] buffer = new byte[0];

    MockInputStream stream = new MockInputStream();
    IOUtil.readFully(stream, buffer, 0, buffer.length);

    Assertions.assertThat(stream.getPos())
        .as("Stream position should reflect bytes read")
        .isEqualTo(0);
  }

  /**
   * 测试场景：read fully small reads with start and length。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReadFullySmallReadsWithStartAndLength() throws IOException {
    byte[] buffer = new byte[10];

    MockInputStream stream = new MockInputStream(2, 2, 3);
    IOUtil.readFully(stream, buffer, 2, 5);

    Assertions.assertThat(Arrays.copyOfRange(buffer, 2, 7))
        .as("Byte array contents should match")
        .isEqualTo(Arrays.copyOfRange(MockInputStream.TEST_ARRAY, 0, 5));

    Assertions.assertThat(stream.getPos())
        .as("Stream position should reflect bytes read")
        .isEqualTo(5);
  }

  /**
   * 测试场景：write fully。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testWriteFully() throws Exception {
    byte[] input = Strings.repeat("Welcome to Warsaw!\n", 12345).getBytes(StandardCharsets.UTF_8);
    InMemoryOutputFile outputFile = new InMemoryOutputFile();
    try (PositionOutputStream outputStream = outputFile.create()) {
      IOUtil.writeFully(outputStream, ByteBuffer.wrap(input.clone()));
    }
    Assertions.assertThat(outputFile.toByteArray()).isEqualTo(input);
  }
}
