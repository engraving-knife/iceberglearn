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

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aliyun.oss.OSS;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.iceberg.aliyun.AliyunProperties;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.io.ByteStreams;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestOSSInputFile 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 TestOSSInputFile 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestOSSInputFile extends AliyunOSSTestBase {
  private final OSS ossClient = ossClient().get();
  private final OSS ossMock = mock(OSS.class, delegatesTo(ossClient));

  private final AliyunProperties aliyunProperties = new AliyunProperties();
  private final Random random = ThreadLocalRandom.current();

  /**
   * 测试场景：Read File。
   *
   * <p>验证该方法在 Read File 条件下的行为是否符合预期。
   */
  @Test
  public void testReadFile() throws Exception {
    OSSURI uri = randomURI();

    int dataSize = 1024 * 1024 * 10;
    byte[] data = randomData(dataSize);
    writeOSSData(uri, data);

    readAndVerify(uri, data);
  }

  /**
   * 测试场景：OSS Input File。
   *
   * <p>验证该方法在 OSS Input File 条件下的行为是否符合预期。
   */
  @Test
  public void testOSSInputFile() {
    OSSURI uri = randomURI();
    Assertions.assertThatThrownBy(
            () ->
                new OSSInputFile(
                    ossClient().get(), uri, aliyunProperties, -1, MetricsContext.nullMetrics()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Invalid file length");
  }

  /**
   * 测试场景：Exists。
   *
   * <p>验证该方法在 Exists 条件下的行为是否符合预期。
   */
  @Test
  public void testExists() {
    OSSURI uri = randomURI();

    InputFile inputFile =
        new OSSInputFile(ossMock, uri, aliyunProperties, MetricsContext.nullMetrics());
    Assert.assertFalse("OSS file should not exist", inputFile.exists());
    verify(ossMock, times(1)).getSimplifiedObjectMeta(uri.bucket(), uri.key());
    reset(ossMock);

    int dataSize = 1024;
    byte[] data = randomData(dataSize);
    writeOSSData(uri, data);

    Assert.assertTrue("OSS file should exist", inputFile.exists());
    inputFile.exists();
    verify(ossMock, times(1)).getSimplifiedObjectMeta(uri.bucket(), uri.key());
    reset(ossMock);
  }

  /**
   * 测试场景：Get Length。
   *
   * <p>验证该方法在 Get Length 条件下的行为是否符合预期。
   */
  @Test
  public void testGetLength() {
    OSSURI uri = randomURI();

    int dataSize = 8;
    byte[] data = randomData(dataSize);
    writeOSSData(uri, data);

    verifyLength(ossMock, uri, data, true);
    verify(ossMock, times(0)).getSimplifiedObjectMeta(uri.bucket(), uri.key());
    reset(ossMock);

    verifyLength(ossMock, uri, data, false);
    verify(ossMock, times(1)).getSimplifiedObjectMeta(uri.bucket(), uri.key());
    reset(ossMock);
  }

  /** 辅助方法：readAndVerify。 */
  private void readAndVerify(OSSURI uri, byte[] data) throws IOException {
    InputFile inputFile =
        new OSSInputFile(ossClient().get(), uri, aliyunProperties, MetricsContext.nullMetrics());
    Assert.assertTrue("OSS file should exist", inputFile.exists());
    Assert.assertEquals("Should have expected file length", data.length, inputFile.getLength());

    byte[] actual = new byte[data.length];
    try (SeekableInputStream in = inputFile.newStream()) {
      ByteStreams.readFully(in, actual);
    }
    Assert.assertArrayEquals("Should have same object content", data, actual);
  }

  /** 辅助方法：verifyLength。 */
  private void verifyLength(OSS ossClientMock, OSSURI uri, byte[] data, boolean isCache) {
    InputFile inputFile;
    if (isCache) {
      inputFile =
          new OSSInputFile(
              ossClientMock, uri, aliyunProperties, data.length, MetricsContext.nullMetrics());
    } else {
      inputFile =
          new OSSInputFile(ossClientMock, uri, aliyunProperties, MetricsContext.nullMetrics());
    }
    inputFile.getLength();
    Assert.assertEquals("Should have expected file length", data.length, inputFile.getLength());
  }

  /** 辅助方法：randomURI。 */
  private OSSURI randomURI() {
    return new OSSURI(location(String.format("%s.dat", UUID.randomUUID())));
  }

  /** 辅助方法：randomData。 */
  private byte[] randomData(int size) {
    byte[] data = new byte[size];
    random.nextBytes(data);
    return data;
  }

  /** 辅助方法：writeOSSData。 */
  private void writeOSSData(OSSURI uri, byte[] data) {
    ossClient.putObject(uri.bucket(), uri.key(), new ByteArrayInputStream(data));
  }
}
