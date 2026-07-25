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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * 文件级说明：测试 TestS3RequestUtil 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestS3RequestUtil 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestS3RequestUtil {

  private ServerSideEncryption serverSideEncryption = null;
  private String kmsKeyId = null;
  private String customAlgorithm = null;
  private String customKey = null;
  private String customMd5 = null;

  /**
   * 测试场景：Configure Server Side Custom Encryption。
   *
   * <p>验证该方法在 Configure Server Side Custom Encryption 条件下的行为是否符合预期。
   */
  @Test
  public void testConfigureServerSideCustomEncryption() {
    S3FileIOProperties s3FileIOProperties = new S3FileIOProperties();
    s3FileIOProperties.setSseType(S3FileIOProperties.SSE_TYPE_CUSTOM);
    s3FileIOProperties.setSseKey("key");
    s3FileIOProperties.setSseMd5("md5");
    S3RequestUtil.configureEncryption(
        s3FileIOProperties,
        this::setServerSideEncryption,
        this::setKmsKeyId,
        this::setCustomAlgorithm,
        this::setCustomKey,
        this::setCustomMd5);
    Assertions.assertThat(serverSideEncryption).isNull();
    Assertions.assertThat(kmsKeyId).isNull();
    Assertions.assertThat(customAlgorithm).isEqualTo(ServerSideEncryption.AES256.name());
    Assertions.assertThat(customKey).isEqualTo("key");
    Assertions.assertThat(customMd5).isEqualTo("md5");
  }

  /**
   * 测试场景：Configure Server Side 3 Encryption。
   *
   * <p>验证该方法在 Configure Server Side 3 Encryption 条件下的行为是否符合预期。
   */
  @Test
  public void testConfigureServerSideS3Encryption() {
    S3FileIOProperties s3FileIOProperties = new S3FileIOProperties();
    s3FileIOProperties.setSseType(S3FileIOProperties.SSE_TYPE_S3);
    S3RequestUtil.configureEncryption(
        s3FileIOProperties,
        this::setServerSideEncryption,
        this::setKmsKeyId,
        this::setCustomAlgorithm,
        this::setCustomKey,
        this::setCustomMd5);
    Assertions.assertThat(serverSideEncryption).isEqualTo(ServerSideEncryption.AES256);
    Assertions.assertThat(kmsKeyId).isNull();
    Assertions.assertThat(customAlgorithm).isNull();
    Assertions.assertThat(customKey).isNull();
    Assertions.assertThat(customMd5).isNull();
  }

  /**
   * 测试场景：Configure Server Side Kms Encryption。
   *
   * <p>验证该方法在 Configure Server Side Kms Encryption 条件下的行为是否符合预期。
   */
  @Test
  public void testConfigureServerSideKmsEncryption() {
    S3FileIOProperties s3FileIOProperties = new S3FileIOProperties();
    s3FileIOProperties.setSseType(S3FileIOProperties.SSE_TYPE_KMS);
    s3FileIOProperties.setSseKey("key");
    S3RequestUtil.configureEncryption(
        s3FileIOProperties,
        this::setServerSideEncryption,
        this::setKmsKeyId,
        this::setCustomAlgorithm,
        this::setCustomKey,
        this::setCustomMd5);
    Assertions.assertThat(serverSideEncryption).isEqualTo(ServerSideEncryption.AWS_KMS);
    Assertions.assertThat(kmsKeyId).isEqualTo("key");
    Assertions.assertThat(customAlgorithm).isNull();
    Assertions.assertThat(customKey).isNull();
    Assertions.assertThat(customMd5).isNull();
  }

  /**
   * 测试场景：Configure Encryption Skip Null Setters。
   *
   * <p>验证该方法在 Configure Encryption Skip Null Setters 条件下的行为是否符合预期。
   */
  @Test
  public void testConfigureEncryptionSkipNullSetters() {
    S3FileIOProperties s3FileIOProperties = new S3FileIOProperties();
    s3FileIOProperties.setSseType(S3FileIOProperties.SSE_TYPE_KMS);
    s3FileIOProperties.setSseKey("key");
    S3RequestUtil.configureEncryption(
        s3FileIOProperties,
        v -> null,
        v -> null,
        this::setCustomAlgorithm,
        this::setCustomKey,
        this::setCustomMd5);
    Assertions.assertThat(serverSideEncryption).isNull();
    Assertions.assertThat(kmsKeyId).isNull();
    Assertions.assertThat(customAlgorithm).isNull();
    Assertions.assertThat(customKey).isNull();
    Assertions.assertThat(customMd5).isNull();
  }

  /** 辅助方法：setCustomAlgorithm。 */
  public S3Request.Builder setCustomAlgorithm(String algorithm) {
    this.customAlgorithm = algorithm;
    return null;
  }

  /** 辅助方法：setCustomKey。 */
  public S3Request.Builder setCustomKey(String key) {
    this.customKey = key;
    return null;
  }

  /** 辅助方法：setCustomMd5。 */
  public S3Request.Builder setCustomMd5(String md5) {
    this.customMd5 = md5;
    return null;
  }

  /** 辅助方法：setKmsKeyId。 */
  public S3Request.Builder setKmsKeyId(String keyId) {
    this.kmsKeyId = keyId;
    return null;
  }

  /** 辅助方法：setServerSideEncryption。 */
  public S3Request.Builder setServerSideEncryption(ServerSideEncryption sse) {
    this.serverSideEncryption = sse;
    return null;
  }
}
