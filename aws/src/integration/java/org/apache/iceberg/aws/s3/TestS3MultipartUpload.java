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
package org.apache.iceberg.aws.s3;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.iceberg.aws.AwsClientFactories;
import org.apache.iceberg.aws.AwsIntegTestUtil;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.io.SeekableInputStream;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：TestS3MultipartUpload 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 s3分片上传 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class TestS3MultipartUpload {

  private final Random random = new Random(1);
  private static S3Client s3;
  private static String bucketName;
  private static String prefix;
  private static S3FileIOProperties properties;
  private static S3FileIO io;
  private String objectUri;

  /** 初始化：beforeClass，在测试类加载时准备共享的测试环境与数据。 */
  @BeforeClass
  public static void beforeClass() {
    s3 = AwsClientFactories.defaultFactory().s3();
    bucketName = AwsIntegTestUtil.testBucketName();
    prefix = UUID.randomUUID().toString();
    properties = new S3FileIOProperties();
    properties.setMultiPartSize(S3FileIOProperties.MULTIPART_SIZE_MIN);
    properties.setChecksumEnabled(true);
    io = new S3FileIO(() -> s3, properties);
  }

  /** 清理：afterClass，在所有测试方法执行完毕后释放共享资源。 */
  @AfterClass
  public static void afterClass() {
    AwsIntegTestUtil.cleanS3Bucket(s3, bucketName, prefix);
  }

  /** 初始化：before，在每个测试方法执行前准备测试环境与数据。 */
  @Before
  public void before() {
    String objectKey = String.format("%s/%s", prefix, UUID.randomUUID().toString());
    objectUri = String.format("s3://%s/%s", bucketName, objectKey);
  }

  /**
   * 测试场景：manyparts写入带int。
   *
   * <p>验证该方法在 manyparts写入带int 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testManyPartsWriteWithInt() throws IOException {
    int parts = 200;
    writeInts(objectUri, parts, random::nextInt);
    Assert.assertEquals(
        parts * (long) S3FileIOProperties.MULTIPART_SIZE_MIN,
        io.newInputFile(objectUri).getLength());
  }

  /**
   * 测试场景：manyparts写入带bytes。
   *
   * <p>验证该方法在 manyparts写入带bytes 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testManyPartsWriteWithBytes() throws IOException {
    int parts = 200;
    byte[] bytes = new byte[S3FileIOProperties.MULTIPART_SIZE_MIN];
    writeBytes(
        objectUri,
        parts,
        () -> {
          random.nextBytes(bytes);
          return bytes;
        });
    Assert.assertEquals(
        parts * (long) S3FileIOProperties.MULTIPART_SIZE_MIN,
        io.newInputFile(objectUri).getLength());
  }

  /**
   * 测试场景：contents写入带int。
   *
   * <p>验证该方法在 contents写入带int 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testContentsWriteWithInt() throws IOException {
    writeInts(objectUri, 10, () -> 6);
    verifyInts(objectUri, () -> 6);
  }

  /**
   * 测试场景：contents写入带bytes。
   *
   * <p>验证该方法在 contents写入带bytes 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testContentsWriteWithBytes() throws IOException {
    byte[] bytes = new byte[S3FileIOProperties.MULTIPART_SIZE_MIN];
    for (int i = 0; i < S3FileIOProperties.MULTIPART_SIZE_MIN; i++) {
      bytes[i] = 6;
    }
    writeBytes(objectUri, 10, () -> bytes);
    verifyInts(objectUri, () -> 6);
  }

  /**
   * 测试场景：上传remainder。
   *
   * <p>验证该方法在 上传remainder 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testUploadRemainder() throws IOException {
    long length = 3 * S3FileIOProperties.MULTIPART_SIZE_MIN + 2 * 1024 * 1024;
    writeInts(objectUri, 1, length, random::nextInt);
    Assert.assertEquals(length, io.newInputFile(objectUri).getLength());
  }

  /**
   * 测试场景：并行上传。
   *
   * <p>验证该方法在 并行上传 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testParallelUpload() throws IOException {
    int threads = 16;
    IntStream.range(0, threads).parallel().forEach(d -> writeInts(objectUri + d, 3, () -> d));

    for (int i = 0; i < threads; i++) {
      final int d = i;
      verifyInts(objectUri + d, () -> d);
    }
  }

  /** 辅助方法：写入ints。 */
  private void writeInts(String fileUri, int parts, Supplier<Integer> writer) {
    writeInts(fileUri, parts, S3FileIOProperties.MULTIPART_SIZE_MIN, writer);
  }

  /** 辅助方法：写入ints。 */
  private void writeInts(String fileUri, int parts, long partSize, Supplier<Integer> writer) {
    try (PositionOutputStream outputStream = io.newOutputFile(fileUri).create()) {
      for (int i = 0; i < parts; i++) {
        for (long j = 0; j < partSize; j++) {
          outputStream.write(writer.get());
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** 辅助方法：验证ints。 */
  private void verifyInts(String fileUri, Supplier<Integer> verifier) {
    try (SeekableInputStream inputStream = io.newInputFile(fileUri).newStream()) {
      int cur;
      while ((cur = inputStream.read()) != -1) {
        Assert.assertEquals(verifier.get().intValue(), cur);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** 辅助方法：写入bytes。 */
  private void writeBytes(String fileUri, int parts, Supplier<byte[]> writer) {
    try (PositionOutputStream outputStream = io.newOutputFile(fileUri).create()) {
      for (int i = 0; i < parts; i++) {
        outputStream.write(writer.get());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
