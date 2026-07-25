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
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * 文件级说明：测试 AwsClientPropertiesTest 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 AwsClientPropertiesTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class AwsClientPropertiesTest {

  /**
   * 测试场景：Apply Client Region。
   *
   * <p>验证该方法在 Apply Client Region 条件下的行为是否符合预期。
   */
  @Test
  public void testApplyClientRegion() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AwsClientProperties.CLIENT_REGION, "us-east-1");
    AwsClientProperties awsClientProperties = new AwsClientProperties(properties);

    S3ClientBuilder mockS3ClientBuilder = Mockito.mock(S3ClientBuilder.class);
    ArgumentCaptor<Region> regionArgumentCaptor = ArgumentCaptor.forClass(Region.class);

    awsClientProperties.applyClientRegionConfiguration(mockS3ClientBuilder);
    Mockito.verify(mockS3ClientBuilder).region(regionArgumentCaptor.capture());
    Region region = regionArgumentCaptor.getValue();
    Assertions.assertThat(region.id())
        .withFailMessage("region parameter should match what is set in CLIENT_REGION")
        .isEqualTo("us-east-1");
  }

  /**
   * 测试场景：Default Credentials Configuration。
   *
   * <p>验证该方法在 Default Credentials Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testDefaultCredentialsConfiguration() {
    AwsClientProperties awsClientProperties = new AwsClientProperties();
    AwsCredentialsProvider credentialsProvider =
        awsClientProperties.credentialsProvider(null, null, null);

    Assertions.assertThat(credentialsProvider instanceof DefaultCredentialsProvider)
        .withFailMessage("Should use default credentials if nothing is set")
        .isTrue();
  }

  /**
   * 测试场景：Creates New Instance Of Default Credentials Configuration。
   *
   * <p>验证该方法在 Creates New Instance Of Default Credentials Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testCreatesNewInstanceOfDefaultCredentialsConfiguration() {
    AwsClientProperties awsClientProperties = new AwsClientProperties();
    AwsCredentialsProvider credentialsProvider =
        awsClientProperties.credentialsProvider(null, null, null);
    AwsCredentialsProvider credentialsProvider2 =
        awsClientProperties.credentialsProvider(null, null, null);

    Assertions.assertThat(credentialsProvider)
        .withFailMessage("Should create a new instance in each call")
        .isNotSameAs(credentialsProvider2);
  }

  /**
   * 测试场景：Basic Credentials Configuration。
   *
   * <p>验证该方法在 Basic Credentials Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testBasicCredentialsConfiguration() {
    AwsClientProperties awsClientProperties = new AwsClientProperties();
    // set access key id and secret access key
    AwsCredentialsProvider credentialsProvider =
        awsClientProperties.credentialsProvider("key", "secret", null);

    Assertions.assertThat(credentialsProvider.resolveCredentials() instanceof AwsBasicCredentials)
        .withFailMessage(
            "Should use basic credentials if access key ID and secret access key are set")
        .isTrue();
    Assertions.assertThat(credentialsProvider.resolveCredentials().accessKeyId())
        .withFailMessage("The access key id should be the same as the one set by tag ACCESS_KEY_ID")
        .isEqualTo("key");

    Assertions.assertThat(credentialsProvider.resolveCredentials().secretAccessKey())
        .withFailMessage(
            "The secret access key should be the same as the one set by tag SECRET_ACCESS_KEY")
        .isEqualTo("secret");
  }

  /**
   * 测试场景：Session Credentials Configuration。
   *
   * <p>验证该方法在 Session Credentials Configuration 条件下的行为是否符合预期。
   */
  @Test
  public void testSessionCredentialsConfiguration() {
    // set access key id, secret access key, and session token
    AwsClientProperties awsClientProperties = new AwsClientProperties();
    AwsCredentialsProvider credentialsProvider =
        awsClientProperties.credentialsProvider("key", "secret", "token");

    Assertions.assertThat(credentialsProvider.resolveCredentials() instanceof AwsSessionCredentials)
        .withFailMessage("Should use session credentials if session token is set")
        .isTrue();
    Assertions.assertThat(credentialsProvider.resolveCredentials().accessKeyId())
        .withFailMessage("The access key id should be the same as the one set by tag ACCESS_KEY_ID")
        .isEqualTo("key");
    Assertions.assertThat(credentialsProvider.resolveCredentials().secretAccessKey())
        .withFailMessage(
            "The secret access key should be the same as the one set by tag SECRET_ACCESS_KEY")
        .isEqualTo("secret");
  }
}
