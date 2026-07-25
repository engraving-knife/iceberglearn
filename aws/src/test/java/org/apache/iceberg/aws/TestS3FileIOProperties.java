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
package org.apache.iceberg.aws;

import java.util.Map;
import java.util.Optional;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.aws.s3.signer.S3V4RestSignerClient;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.signer.Signer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

/**
 * 文件级说明：测试 TestS3FileIOProperties 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestS3FileIOProperties 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestS3FileIOProperties {

  /**
   * 测试场景：3 File Io Sse Custom must Have Custom Key。
   *
   * <p>验证该方法在 3 File Io Sse Custom must Have Custom Key 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoSseCustom_mustHaveCustomKey() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.SSE_TYPE, S3FileIOProperties.SSE_TYPE_CUSTOM);

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot initialize SSE-C S3FileIO with null encryption key");
  }

  /**
   * 测试场景：3 File Io Sse Custom must Have Custom Md 5。
   *
   * <p>验证该方法在 3 File Io Sse Custom must Have Custom Md 5 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoSseCustom_mustHaveCustomMd5() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.SSE_TYPE, S3FileIOProperties.SSE_TYPE_CUSTOM);
    map.put(S3FileIOProperties.SSE_KEY, "something");

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot initialize SSE-C S3FileIO with null encryption key MD5");
  }

  /**
   * 测试场景：3 File Io Acl。
   *
   * <p>验证该方法在 3 File Io Acl 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoAcl() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.ACL, ObjectCannedACL.AUTHENTICATED_READ.toString());
    S3FileIOProperties properties = new S3FileIOProperties(map);
    Assertions.assertThat(properties.acl()).isEqualTo(ObjectCannedACL.AUTHENTICATED_READ);
  }

  /**
   * 测试场景：3 File Io Acl unknown Type。
   *
   * <p>验证该方法在 3 File Io Acl unknown Type 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoAcl_unknownType() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.ACL, "bad-input");

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot support S3 CannedACL bad-input");
  }

  /**
   * 测试场景：3 Multipart Size Too Small。
   *
   * <p>验证该方法在 3 Multipart Size Too Small 条件下的行为是否符合预期。
   */
  @Test
  public void testS3MultipartSizeTooSmall() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.MULTIPART_SIZE, "1");

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Minimum multipart upload object size must be larger than 5 MB.");
  }

  /**
   * 测试场景：3 Multipart Size Too Large。
   *
   * <p>验证该方法在 3 Multipart Size Too Large 条件下的行为是否符合预期。
   */
  @Test
  public void testS3MultipartSizeTooLarge() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.MULTIPART_SIZE, "5368709120"); // 5GB

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Input malformed or exceeded maximum multipart upload size 5GB: 5368709120");
  }

  /**
   * 测试场景：3 Multipart Threshold Factor Less Than One。
   *
   * <p>验证该方法在 3 Multipart Threshold Factor Less Than One 条件下的行为是否符合预期。
   */
  @Test
  public void testS3MultipartThresholdFactorLessThanOne() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.MULTIPART_THRESHOLD_FACTOR, "0.9");

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Multipart threshold factor must be >= to 1.0");
  }

  /**
   * 测试场景：3 File Io Delete Batch Size Too Large。
   *
   * <p>验证该方法在 3 File Io Delete Batch Size Too Large 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoDeleteBatchSizeTooLarge() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.DELETE_BATCH_SIZE, "2000");

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Deletion batch size must be between 1 and 1000");
  }

  /**
   * 测试场景：3 File Io Delete Batch Size Too Small。
   *
   * <p>验证该方法在 3 File Io Delete Batch Size Too Small 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoDeleteBatchSizeTooSmall() {
    Map<String, String> map = Maps.newHashMap();
    map.put(S3FileIOProperties.DELETE_BATCH_SIZE, "0");

    Assertions.assertThatThrownBy(() -> new S3FileIOProperties(map))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Deletion batch size must be between 1 and 1000");
  }

  /**
   * 测试场景：3 File Io Default Credentials Configuration。
   *
   * <p>验证该方法在 3 File Io Default Credentials Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoDefaultCredentialsConfiguration() {
    // set nothing
    Map<String, String> properties = Maps.newHashMap();
    S3FileIOProperties s3FileIOProperties = new S3FileIOProperties(properties);
    AwsClientProperties awsClientProperties = new AwsClientProperties(properties);
    S3ClientBuilder mockS3ClientBuilder = Mockito.mock(S3ClientBuilder.class);
    ArgumentCaptor<AwsCredentialsProvider> awsCredentialsProviderCaptor =
        ArgumentCaptor.forClass(AwsCredentialsProvider.class);

    s3FileIOProperties.applyCredentialConfigurations(awsClientProperties, mockS3ClientBuilder);
    Mockito.verify(mockS3ClientBuilder).credentialsProvider(awsCredentialsProviderCaptor.capture());
    AwsCredentialsProvider capturedAwsCredentialsProvider = awsCredentialsProviderCaptor.getValue();

    Assertions.assertThat(capturedAwsCredentialsProvider)
        .as("Should use default credentials if nothing is set")
        .isInstanceOf(DefaultCredentialsProvider.class);
  }

  /**
   * 测试场景：3 File Io Basic Credentials Configuration。
   *
   * <p>验证该方法在 3 File Io Basic Credentials Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoBasicCredentialsConfiguration() {
    // set access key id and secret access key
    Map<String, String> properties = Maps.newHashMap();
    properties.put(S3FileIOProperties.ACCESS_KEY_ID, "key");
    properties.put(S3FileIOProperties.SECRET_ACCESS_KEY, "secret");
    S3FileIOProperties s3PropertiesTwoSet = new S3FileIOProperties(properties);
    AwsClientProperties awsClientProperties = new AwsClientProperties(properties);
    S3ClientBuilder mockS3ClientBuilder = Mockito.mock(S3ClientBuilder.class);
    ArgumentCaptor<AwsCredentialsProvider> awsCredentialsProviderCaptor =
        ArgumentCaptor.forClass(AwsCredentialsProvider.class);

    s3PropertiesTwoSet.applyCredentialConfigurations(awsClientProperties, mockS3ClientBuilder);
    Mockito.verify(mockS3ClientBuilder).credentialsProvider(awsCredentialsProviderCaptor.capture());
    AwsCredentialsProvider capturedAwsCredentialsProvider = awsCredentialsProviderCaptor.getValue();

    Assertions.assertThat(capturedAwsCredentialsProvider.resolveCredentials())
        .as("Should use basic credentials if access key ID and secret access key are set")
        .isInstanceOf(AwsBasicCredentials.class);
    Assertions.assertThat(capturedAwsCredentialsProvider.resolveCredentials().accessKeyId())
        .as("The access key id should be the same as the one set by tag S3FILEIO_ACCESS_KEY_ID")
        .isEqualTo("key");
    Assertions.assertThat(capturedAwsCredentialsProvider.resolveCredentials().secretAccessKey())
        .as(
            "The secret access key should be the same as the one set by tag S3FILEIO_SECRET_ACCESS_KEY")
        .isEqualTo("secret");
  }

  /**
   * 测试场景：3 File Io Session Credentials Configuration。
   *
   * <p>验证该方法在 3 File Io Session Credentials Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoSessionCredentialsConfiguration() {
    // set access key id, secret access key, and session token
    Map<String, String> properties = Maps.newHashMap();
    properties.put(S3FileIOProperties.ACCESS_KEY_ID, "key");
    properties.put(S3FileIOProperties.SECRET_ACCESS_KEY, "secret");
    properties.put(S3FileIOProperties.SESSION_TOKEN, "token");
    S3FileIOProperties s3Properties = new S3FileIOProperties(properties);
    AwsClientProperties awsClientProperties = new AwsClientProperties(properties);
    S3ClientBuilder mockS3ClientBuilder = Mockito.mock(S3ClientBuilder.class);
    ArgumentCaptor<AwsCredentialsProvider> awsCredentialsProviderCaptor =
        ArgumentCaptor.forClass(AwsCredentialsProvider.class);

    s3Properties.applyCredentialConfigurations(awsClientProperties, mockS3ClientBuilder);
    Mockito.verify(mockS3ClientBuilder).credentialsProvider(awsCredentialsProviderCaptor.capture());
    AwsCredentialsProvider capturedAwsCredentialsProvider = awsCredentialsProviderCaptor.getValue();

    Assertions.assertThat(capturedAwsCredentialsProvider.resolveCredentials())
        .as("Should use session credentials if session token is set")
        .isInstanceOf(AwsSessionCredentials.class);
    Assertions.assertThat(capturedAwsCredentialsProvider.resolveCredentials().accessKeyId())
        .as("The access key id should be the same as the one set by tag S3FILEIO_ACCESS_KEY_ID")
        .isEqualTo("key");
    Assertions.assertThat(capturedAwsCredentialsProvider.resolveCredentials().secretAccessKey())
        .as(
            "The secret access key should be the same as the one set by tag S3FILEIO_SECRET_ACCESS_KEY")
        .isEqualTo("secret");
  }

  /**
   * 测试场景：3 Remote Signer Without Uri。
   *
   * <p>验证该方法在 3 Remote Signer Without Uri 条件下的行为是否符合预期。
   */
  @Test
  public void testS3RemoteSignerWithoutUri() {
    Map<String, String> properties =
        ImmutableMap.of(S3FileIOProperties.REMOTE_SIGNING_ENABLED, "true");
    S3FileIOProperties s3Properties = new S3FileIOProperties(properties);

    Assertions.assertThatThrownBy(() -> s3Properties.applySignerConfiguration(S3Client.builder()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("S3 signer service URI is required");
  }

  /**
   * 测试场景：3 Remote Signing Enabled。
   *
   * <p>验证该方法在 3 Remote Signing Enabled 条件下的行为是否符合预期。
   */
  @Test
  public void testS3RemoteSigningEnabled() {
    String uri = "http://localhost:12345";
    Map<String, String> properties =
        ImmutableMap.of(
            S3FileIOProperties.REMOTE_SIGNING_ENABLED, "true", CatalogProperties.URI, uri);
    S3FileIOProperties s3Properties = new S3FileIOProperties(properties);
    S3ClientBuilder builder = S3Client.builder();

    s3Properties.applySignerConfiguration(builder);

    Optional<Signer> signer =
        builder.overrideConfiguration().advancedOption(SdkAdvancedClientOption.SIGNER);
    Assertions.assertThat(signer).isPresent().get().isInstanceOf(S3V4RestSignerClient.class);
    S3V4RestSignerClient signerClient = (S3V4RestSignerClient) signer.get();
    Assertions.assertThat(signerClient.baseSignerUri()).isEqualTo(uri);
    Assertions.assertThat(signerClient.properties()).isEqualTo(properties);
  }

  /**
   * 测试场景：3 Remote Signing Disabled。
   *
   * <p>验证该方法在 3 Remote Signing Disabled 条件下的行为是否符合预期。
   */
  @Test
  public void testS3RemoteSigningDisabled() {
    Map<String, String> properties =
        ImmutableMap.of(S3FileIOProperties.REMOTE_SIGNING_ENABLED, "false");
    S3FileIOProperties s3Properties = new S3FileIOProperties(properties);
    S3ClientBuilder builder = S3Client.builder();

    s3Properties.applySignerConfiguration(builder);

    Optional<Signer> signer =
        builder.overrideConfiguration().advancedOption(SdkAdvancedClientOption.SIGNER);
    Assertions.assertThat(signer).isNotPresent();
  }
}
