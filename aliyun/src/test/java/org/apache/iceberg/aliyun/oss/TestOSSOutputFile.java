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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.iceberg.aliyun.AliyunProperties;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.io.ByteStreams;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestOSSOutputFile 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 TestOSSOutputFile 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestOSSOutputFile extends AliyunOSSTestBase {

  private final OSS ossClient = ossClient().get();
  private final Random random = ThreadLocalRandom.current();
  private final AliyunProperties aliyunProperties = new AliyunProperties();

  /**
   * 测试场景：Write File。
   *
   * <p>验证该方法在 Write File 条件下的行为是否符合预期。
   */
  @Test
  public void testWriteFile() throws IOException {
    OSSURI uri = randomURI();
    int dataSize = 1024 * 10;
    byte[] data = randomData(dataSize);

    OutputFile out = OSSOutputFile.fromLocation(ossClient, uri.location(), aliyunProperties);
    try (OutputStream os = out.create();
        InputStream is = new ByteArrayInputStream(data)) {
      ByteStreams.copy(is, os);
    }

    Assert.assertTrue("OSS file should exist", ossClient.doesObjectExist(uri.bucket(), uri.key()));
    Assert.assertEquals("Object length should match", ossDataLength(uri), dataSize);

    byte[] actual = ossDataContent(uri, dataSize);
    Assert.assertArrayEquals("Object content should match", data, actual);
  }

  /**
   * 测试场景：From Location。
   *
   * <p>验证该方法在 From Location 条件下的行为是否符合预期。
   */
  @Test
  public void testFromLocation() {
    Assertions.assertThatThrownBy(
            () -> OSSOutputFile.fromLocation(ossClient, null, aliyunProperties))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("location cannot be null");
  }

  /**
   * 测试场景：Create。
   *
   * <p>验证该方法在 Create 条件下的行为是否符合预期。
   */
  @Test
  public void testCreate() {
    OSSURI uri = randomURI();
    int dataSize = 8;
    byte[] data = randomData(dataSize);

    writeOSSData(uri, data);

    OutputFile out = OSSOutputFile.fromLocation(ossClient, uri.location(), aliyunProperties);

    Assertions.assertThatThrownBy(out::create)
        .isInstanceOf(AlreadyExistsException.class)
        .hasMessageContaining("Location already exists");
  }

  /**
   * 测试场景：Create Or Overwrite。
   *
   * <p>验证该方法在 Create Or Overwrite 条件下的行为是否符合预期。
   */
  @Test
  public void testCreateOrOverwrite() throws IOException {
    OSSURI uri = randomURI();
    int dataSize = 8;
    byte[] data = randomData(dataSize);

    writeOSSData(uri, data);

    int expectSize = 1024;
    byte[] expect = randomData(expectSize);

    OutputFile out = OSSOutputFile.fromLocation(ossClient, uri.location(), aliyunProperties);
    try (OutputStream os = out.createOrOverwrite();
        InputStream is = new ByteArrayInputStream(expect)) {
      ByteStreams.copy(is, os);
    }

    Assert.assertEquals(
        String.format("Should overwrite object length from %d to %d", dataSize, expectSize),
        expectSize,
        ossDataLength(uri));

    byte[] actual = ossDataContent(uri, expectSize);
    Assert.assertArrayEquals("Should overwrite object content", expect, actual);
  }

  /**
   * 测试场景：Location。
   *
   * <p>验证该方法在 Location 条件下的行为是否符合预期。
   */
  @Test
  public void testLocation() {
    OSSURI uri = randomURI();
    OutputFile out =
        new OSSOutputFile(ossClient, uri, aliyunProperties, MetricsContext.nullMetrics());
    Assert.assertEquals("Location should match", uri.location(), out.location());
  }

  /**
   * 测试场景：To Input File。
   *
   * <p>验证该方法在 To Input File 条件下的行为是否符合预期。
   */
  @Test
  public void testToInputFile() throws IOException {
    int dataSize = 1024 * 10;
    byte[] data = randomData(dataSize);

    OutputFile out =
        new OSSOutputFile(ossClient, randomURI(), aliyunProperties, MetricsContext.nullMetrics());
    try (OutputStream os = out.create();
        InputStream is = new ByteArrayInputStream(data)) {
      ByteStreams.copy(is, os);
    }

    InputFile in = out.toInputFile();
    Assert.assertTrue("Should be an instance of OSSInputFile", in instanceof OSSInputFile);
    Assert.assertTrue("OSS file should exist", in.exists());
    Assert.assertEquals("Should have expected location", out.location(), in.location());
    Assert.assertEquals("Should have expected length", dataSize, in.getLength());

    byte[] actual = new byte[dataSize];
    try (InputStream as = in.newStream()) {
      ByteStreams.readFully(as, actual);
    }
    Assert.assertArrayEquals("Should have expected content", data, actual);
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

  /** 辅助方法：ossDataLength。 */
  private long ossDataLength(OSSURI uri) {
    return ossClient.getObject(uri.bucket(), uri.key()).getObjectMetadata().getContentLength();
  }

  /** 辅助方法：ossDataContent。 */
  private byte[] ossDataContent(OSSURI uri, int dataSize) throws IOException {
    try (InputStream is = ossClient.getObject(uri.bucket(), uri.key()).getObjectContent()) {
      byte[] actual = new byte[dataSize];
      ByteStreams.readFully(is, actual);
      return actual;
    }
  }
}
