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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.storage.file.datalake.DataLakeFileClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.apache.iceberg.azure.AzureProperties;
import org.apache.iceberg.io.IOUtil;
import org.apache.iceberg.io.RangeReadable;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 ADLSInputStreamTest 的功能。
 *
 * <p>所属模块：iceberg-azure。职责：验证 ADLSInputStreamTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class ADLSInputStreamTest extends BaseAzuriteTest {

  private static final String FILE_PATH = "path/to/file";

  private final Random random = new Random(1);
  private final AzureProperties azureProperties = new AzureProperties();

  /** 辅助方法：fileClient。 */
  private DataLakeFileClient fileClient() {
    return AZURITE_CONTAINER.fileClient(FILE_PATH);
  }

  /** 辅助方法：setupData。 */
  private void setupData(byte[] data) {
    AZURITE_CONTAINER.createFile(FILE_PATH, data);
  }

  /**
   * 测试场景：Read。
   *
   * <p>验证该方法在 Read 条件下的行为是否符合预期。
   */
  @Test
  public void testRead() throws Exception {
    int dataSize = 1024 * 1024 * 10;
    byte[] data = randomData(dataSize);

    setupData(data);

    try (SeekableInputStream in =
        new ADLSInputStream(fileClient(), null, azureProperties, MetricsContext.nullMetrics())) {
      int readSize = 1024;

      readAndCheck(in, in.getPos(), readSize, data, false);
      readAndCheck(in, in.getPos(), readSize, data, true);

      // Seek forward in current stream
      int seekSize = 1024;
      readAndCheck(in, in.getPos() + seekSize, readSize, data, false);
      readAndCheck(in, in.getPos() + seekSize, readSize, data, true);

      // Buffered read
      readAndCheck(in, in.getPos(), readSize, data, true);
      readAndCheck(in, in.getPos(), readSize, data, false);

      // Seek with new stream
      long seekNewStreamPosition = 2 * 1024 * 1024;
      readAndCheck(in, in.getPos() + seekNewStreamPosition, readSize, data, true);
      readAndCheck(in, in.getPos() + seekNewStreamPosition, readSize, data, false);

      // Backseek and read
      readAndCheck(in, 0, readSize, data, true);
      readAndCheck(in, 0, readSize, data, false);
    }
  }

  /**
   * 测试场景：Read Single。
   *
   * <p>验证该方法在 Read Single 条件下的行为是否符合预期。
   */
  @Test
  public void testReadSingle() throws Exception {
    int i0 = 1;
    int i1 = 255;
    byte[] data = {(byte) i0, (byte) i1};

    setupData(data);

    try (SeekableInputStream in =
        new ADLSInputStream(fileClient(), null, azureProperties, MetricsContext.nullMetrics())) {
      assertThat(in.read()).isEqualTo(i0);
      assertThat(in.read()).isEqualTo(i1);
    }
  }

  /** 辅助方法：readAndCheck。 */
  private void readAndCheck(
      SeekableInputStream in, long rangeStart, int size, byte[] original, boolean buffered)
      throws IOException {
    in.seek(rangeStart);
    assertThat(rangeStart).isEqualTo(in.getPos());

    long rangeEnd = rangeStart + size;
    byte[] actual = new byte[size];

    if (buffered) {
      IOUtil.readFully(in, actual, 0, actual.length);
    } else {
      int read = 0;
      while (read < size) {
        actual[read++] = (byte) in.read();
      }
    }

    assertThat(in.getPos()).isEqualTo(rangeEnd);
    assertThat(actual).isEqualTo(Arrays.copyOfRange(original, (int) rangeStart, (int) rangeEnd));
  }

  /**
   * 测试场景：Range Read。
   *
   * <p>验证该方法在 Range Read 条件下的行为是否符合预期。
   */
  @Test
  public void testRangeRead() throws Exception {
    int dataSize = 1024 * 1024 * 10;
    byte[] expected = randomData(dataSize);
    byte[] actual = new byte[dataSize];

    long position;
    int offset;
    int length;

    setupData(expected);

    try (RangeReadable in =
        new ADLSInputStream(fileClient(), null, azureProperties, MetricsContext.nullMetrics())) {
      // first 1k
      position = 0;
      offset = 0;
      length = 1024;
      readAndCheckRanges(in, expected, position, actual, offset, length);

      // last 1k
      position = dataSize - 1024;
      offset = dataSize - 1024;
      readAndCheckRanges(in, expected, position, actual, offset, length);

      // middle 2k
      position = dataSize / 2 - 1024;
      offset = dataSize / 2 - 1024;
      length = 1024 * 2;
      readAndCheckRanges(in, expected, position, actual, offset, length);
    }
  }

  /** 辅助方法：readAndCheckRanges。 */
  private void readAndCheckRanges(
      RangeReadable in, byte[] original, long position, byte[] buffer, int offset, int length)
      throws IOException {
    in.readFully(position, buffer, offset, length);

    assertThat(Arrays.copyOfRange(buffer, offset, offset + length))
        .isEqualTo(Arrays.copyOfRange(original, offset, offset + length));
  }

  /**
   * 测试场景：Close。
   *
   * <p>验证该方法在 Close 条件下的行为是否符合预期。
   */
  @Test
  public void testClose() throws Exception {
    setupData(randomData(2));
    SeekableInputStream closed =
        new ADLSInputStream(fileClient(), null, azureProperties, MetricsContext.nullMetrics());
    closed.close();
    assertThatThrownBy(() -> closed.seek(0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot seek: already closed");
  }

  /**
   * 测试场景：Seek。
   *
   * <p>验证该方法在 Seek 条件下的行为是否符合预期。
   */
  @Test
  public void testSeek() throws Exception {
    byte[] data = randomData(1024 * 1024);

    setupData(data);

    try (SeekableInputStream in =
        new ADLSInputStream(fileClient(), null, azureProperties, MetricsContext.nullMetrics())) {
      in.seek(data.length / 2);
      byte[] actual = new byte[data.length / 2];

      IOUtil.readFully(in, actual, 0, data.length / 2);

      byte[] expected = Arrays.copyOfRange(data, data.length / 2, data.length);
      assertThat(actual).isEqualTo(expected);
    }
  }

  /**
   * 测试场景：Seek Negative。
   *
   * <p>验证该方法在 Seek Negative 条件下的行为是否符合预期。
   */
  @Test
  public void testSeekNegative() throws Exception {
    setupData(randomData(2));
    SeekableInputStream in =
        new ADLSInputStream(fileClient(), null, azureProperties, MetricsContext.nullMetrics());
    assertThatThrownBy(() -> in.seek(-3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot seek: position -3 is negative");
    in.close();
  }

  /** 辅助方法：randomData。 */
  private byte[] randomData(int size) {
    byte[] data = new byte[size];
    random.nextBytes(data);
    return data;
  }
}
