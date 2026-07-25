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

import static org.apache.iceberg.metrics.MetricsContext.nullMetrics;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.adobe.testing.s3mock.junit5.S3MockExtension;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.utils.BinaryUtils;

@ExtendWith(S3MockExtension.class)
/**
 * 文件级说明：测试 TestS3OutputStream 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestS3OutputStream 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestS3OutputStream {
  private static final Logger LOG = LoggerFactory.getLogger(TestS3OutputStream.class);
  private static final String BUCKET = "test-bucket";
  private static final int FIVE_MBS = 5 * 1024 * 1024;

  @RegisterExtension
  public static final S3MockExtension S3_MOCK = S3MockExtension.builder().silent().build();

  private final S3Client s3 = S3_MOCK.createS3ClientV2();
  private final S3Client s3mock = mock(S3Client.class, delegatesTo(s3));
  private final Random random = new Random(1);
  private final Path tmpDir = Files.createTempDirectory("s3fileio-test-");
  private final String newTmpDirectory = "/tmp/newStagingDirectory";

  private final S3FileIOProperties properties =
      new S3FileIOProperties(
          ImmutableMap.of(
              S3FileIOProperties.MULTIPART_SIZE,
              Integer.toString(5 * 1024 * 1024),
              S3FileIOProperties.STAGING_DIRECTORY,
              tmpDir.toString(),
              "s3.write.tags.abc",
              "123",
              "s3.write.tags.def",
              "789",
              "s3.delete.tags.xyz",
              "456"));

  /** 辅助方法：TestS3OutputStream。 */
  public TestS3OutputStream() throws IOException {}

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() {
    properties.setChecksumEnabled(false);
    createBucket(BUCKET);
  }

  /** 辅助方法：after。 */
  @AfterEach
  public void after() {
    File newStagingDirectory = new File(newTmpDirectory);
    if (newStagingDirectory.exists()) {
      newStagingDirectory.delete();
    }
  }

  /**
   * 测试场景：Write。
   *
   * <p>验证该方法在 Write 条件下的行为是否符合预期。
   */
  @Test
  public void testWrite() {
    writeTest();
  }

  /**
   * 测试场景：Abort After Failed Part Upload。
   *
   * <p>验证该方法在 Abort After Failed Part Upload 条件下的行为是否符合预期。
   */
  @Test
  public void testAbortAfterFailedPartUpload() {
    RuntimeException mockException = new RuntimeException("mock uploadPart failure");
    doThrow(mockException).when(s3mock).uploadPart((UploadPartRequest) any(), (RequestBody) any());

    Assertions.assertThatThrownBy(
            () -> {
              try (S3OutputStream stream =
                  new S3OutputStream(s3mock, randomURI(), properties, nullMetrics())) {
                stream.write(randomData(10 * 1024 * 1024));
              }
            })
        .isInstanceOf(mockException.getClass())
        .hasMessageContaining(mockException.getMessage());

    verify(s3mock, times(1)).abortMultipartUpload((AbortMultipartUploadRequest) any());
  }

  /**
   * 测试场景：Abort Multipart。
   *
   * <p>验证该方法在 Abort Multipart 条件下的行为是否符合预期。
   */
  @Test
  public void testAbortMultipart() {
    RuntimeException mockException = new RuntimeException("mock completeMultipartUpload failure");
    doThrow(mockException)
        .when(s3mock)
        .completeMultipartUpload((CompleteMultipartUploadRequest) any());

    Assertions.assertThatThrownBy(
            () -> {
              try (S3OutputStream stream =
                  new S3OutputStream(s3mock, randomURI(), properties, nullMetrics())) {
                stream.write(randomData(10 * 1024 * 1024));
              }
            })
        .isInstanceOf(mockException.getClass())
        .hasMessageContaining(mockException.getMessage());

    verify(s3mock, times(1)).abortMultipartUpload((AbortMultipartUploadRequest) any());
  }

  /**
   * 测试场景：Multiple Close。
   *
   * <p>验证该方法在 Multiple Close 条件下的行为是否符合预期。
   */
  @Test
  public void testMultipleClose() throws IOException {
    S3OutputStream stream = new S3OutputStream(s3, randomURI(), properties, nullMetrics());
    stream.close();
    stream.close();
  }

  /**
   * 测试场景：Staging Directory Creation。
   *
   * <p>验证该方法在 Staging Directory Creation 条件下的行为是否符合预期。
   */
  @Test
  public void testStagingDirectoryCreation() throws IOException {
    S3FileIOProperties newStagingDirectoryAwsProperties =
        new S3FileIOProperties(
            ImmutableMap.of(S3FileIOProperties.STAGING_DIRECTORY, newTmpDirectory));
    S3OutputStream stream =
        new S3OutputStream(s3, randomURI(), newStagingDirectoryAwsProperties, nullMetrics());
    stream.close();
  }

  /**
   * 测试场景：Write With Checksum Enabled。
   *
   * <p>验证该方法在 Write With Checksum Enabled 条件下的行为是否符合预期。
   */
  @Test
  public void testWriteWithChecksumEnabled() {
    properties.setChecksumEnabled(true);
    writeTest();
  }

  /**
   * 测试场景：Double Close。
   *
   * <p>验证该方法在 Double Close 条件下的行为是否符合预期。
   */
  @Test
  public void testDoubleClose() throws IOException {
    IllegalStateException mockException =
        new IllegalStateException("mock failure to completeUploads on close");
    Mockito.doThrow(mockException)
        .when(s3mock)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    S3OutputStream stream = new S3OutputStream(s3mock, randomURI(), properties, nullMetrics());

    Assertions.assertThatThrownBy(stream::close)
        .isInstanceOf(mockException.getClass())
        .hasMessageContaining(mockException.getMessage());

    Assertions.assertThatNoException().isThrownBy(stream::close);
  }

  /** 辅助方法：writeTest。 */
  private void writeTest() {
    // Run tests for both byte and array write paths
    Stream.of(true, false)
        .forEach(
            arrayWrite -> {
              // Test small file write (less than multipart threshold)
              byte[] data = randomData(1024);
              writeAndVerify(s3mock, randomURI(), data, arrayWrite);
              ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor =
                  ArgumentCaptor.forClass(PutObjectRequest.class);
              verify(s3mock, times(1))
                  .putObject(putObjectRequestArgumentCaptor.capture(), (RequestBody) any());
              checkPutObjectRequestContent(data, putObjectRequestArgumentCaptor);
              checkTags(putObjectRequestArgumentCaptor);
              reset(s3mock);

              // Test file larger than part size but less than multipart threshold
              data = randomData(6 * 1024 * 1024);
              writeAndVerify(s3mock, randomURI(), data, arrayWrite);
              putObjectRequestArgumentCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
              verify(s3mock, times(1))
                  .putObject(putObjectRequestArgumentCaptor.capture(), (RequestBody) any());
              checkPutObjectRequestContent(data, putObjectRequestArgumentCaptor);
              checkTags(putObjectRequestArgumentCaptor);
              reset(s3mock);

              // Test file large enough to trigger multipart upload
              data = randomData(10 * 1024 * 1024);
              writeAndVerify(s3mock, randomURI(), data, arrayWrite);
              ArgumentCaptor<UploadPartRequest> uploadPartRequestArgumentCaptor =
                  ArgumentCaptor.forClass(UploadPartRequest.class);
              verify(s3mock, times(2))
                  .uploadPart(uploadPartRequestArgumentCaptor.capture(), (RequestBody) any());
              checkUploadPartRequestContent(data, uploadPartRequestArgumentCaptor);
              reset(s3mock);

              // Test uploading many parts
              data = randomData(22 * 1024 * 1024);
              writeAndVerify(s3mock, randomURI(), data, arrayWrite);
              uploadPartRequestArgumentCaptor = ArgumentCaptor.forClass(UploadPartRequest.class);
              verify(s3mock, times(5))
                  .uploadPart(uploadPartRequestArgumentCaptor.capture(), (RequestBody) any());
              checkUploadPartRequestContent(data, uploadPartRequestArgumentCaptor);
              reset(s3mock);
            });
  }

  /** 辅助方法：checkUploadPartRequestContent。 */
  private void checkUploadPartRequestContent(
      byte[] data, ArgumentCaptor<UploadPartRequest> uploadPartRequestArgumentCaptor) {
    if (properties.isChecksumEnabled()) {
      List<UploadPartRequest> uploadPartRequests =
          uploadPartRequestArgumentCaptor.getAllValues().stream()
              .sorted(Comparator.comparingInt(UploadPartRequest::partNumber))
              .collect(Collectors.toList());
      for (int i = 0; i < uploadPartRequests.size(); ++i) {
        int offset = i * FIVE_MBS;
        int len = (i + 1) * FIVE_MBS - 1 > data.length ? data.length - offset : FIVE_MBS;
        Assertions.assertThat(uploadPartRequests.get(i).contentMD5())
            .isEqualTo(getDigest(data, offset, len));
      }
    }
  }

  /** 辅助方法：checkPutObjectRequestContent。 */
  private void checkPutObjectRequestContent(
      byte[] data, ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor) {
    if (properties.isChecksumEnabled()) {
      List<PutObjectRequest> putObjectRequests = putObjectRequestArgumentCaptor.getAllValues();
      Assertions.assertThat(putObjectRequests.get(0).contentMD5())
          .isEqualTo(getDigest(data, 0, data.length));
    }
  }

  /** 辅助方法：checkTags。 */
  private void checkTags(ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor) {
    if (properties.isChecksumEnabled()) {
      List<PutObjectRequest> putObjectRequests = putObjectRequestArgumentCaptor.getAllValues();
      String tagging = putObjectRequests.get(0).tagging();
      Assertions.assertThat(getTags(properties.writeTags())).isEqualTo(tagging);
    }
  }

  /** 辅助方法：getTags。 */
  private String getTags(Set<Tag> objectTags) {
    return objectTags.stream().map(e -> e.key() + "=" + e.value()).collect(Collectors.joining("&"));
  }

  /** 辅助方法：getDigest。 */
  private String getDigest(byte[] data, int offset, int length) {
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      md5.update(data, offset, length);
      return BinaryUtils.toBase64(md5.digest());
    } catch (NoSuchAlgorithmException e) {
      Assertions.fail("Failed to get MD5 MessageDigest. %s", e);
    }
    return null;
  }

  /** 辅助方法：writeAndVerify。 */
  private void writeAndVerify(S3Client client, S3URI uri, byte[] data, boolean arrayWrite) {
    try (S3OutputStream stream = new S3OutputStream(client, uri, properties, nullMetrics())) {
      if (arrayWrite) {
        stream.write(data);
        Assertions.assertThat(stream.getPos()).isEqualTo(data.length);
      } else {
        for (int i = 0; i < data.length; i++) {
          stream.write(data[i]);
          Assertions.assertThat(stream.getPos()).isEqualTo(i + 1);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    byte[] actual = readS3Data(uri);
    Assertions.assertThat(actual).isEqualTo(data);

    // Verify all staging files are cleaned up
    try {
      Assertions.assertThat(Files.list(tmpDir)).isEmpty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 辅助方法：readS3Data。 */
  private byte[] readS3Data(S3URI uri) {
    ResponseBytes<GetObjectResponse> data =
        s3.getObject(
            GetObjectRequest.builder().bucket(uri.bucket()).key(uri.key()).build(),
            ResponseTransformer.toBytes());

    return data.asByteArray();
  }

  /** 辅助方法：randomData。 */
  private byte[] randomData(int size) {
    byte[] result = new byte[size];
    random.nextBytes(result);
    return result;
  }

  /** 辅助方法：randomURI。 */
  private S3URI randomURI() {
    return new S3URI(String.format("s3://%s/data/%s.dat", BUCKET, UUID.randomUUID()));
  }

  /** 辅助方法：createBucket。 */
  private void createBucket(String bucketName) {
    try {
      s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    } catch (BucketAlreadyExistsException e) {
      // do nothing
    }
  }
}
