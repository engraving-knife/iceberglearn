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
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 文件级说明：TestDefaultAwsClientFactory 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 默认aws客户端工厂 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class TestDefaultAwsClientFactory {

  /**
   * 测试场景：Glueendpointoverride。
   *
   * <p>验证该方法在 Glueendpointoverride 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testGlueEndpointOverride() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AwsProperties.GLUE_CATALOG_ENDPOINT, "https://unknown:1234");
    AwsClientFactory factory = AwsClientFactories.from(properties);
    GlueClient glueClient = factory.glue();
    AssertHelpers.assertThrowsCause(
        "Should refuse connection to unknown endpoint",
        SdkClientException.class,
        "Unable to execute HTTP request: unknown",
        () -> glueClient.getDatabase(GetDatabaseRequest.builder().name("TEST").build()));
  }

  /**
   * 测试场景：s3文件ioendpointoverride。
   *
   * <p>验证该方法在 s3文件ioendpointoverride 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testS3FileIoEndpointOverride() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(S3FileIOProperties.ENDPOINT, "https://unknown:1234");
    AwsClientFactory factory = AwsClientFactories.from(properties);
    S3Client s3Client = factory.s3();
    AssertHelpers.assertThrowsCause(
        "Should refuse connection to unknown endpoint",
        SdkClientException.class,
        "Unable to execute HTTP request: bucket.unknown",
        () -> s3Client.getObject(GetObjectRequest.builder().bucket("bucket").key("key").build()));
  }

  /**
   * 测试场景：s3文件iocredentialsoverride。
   *
   * <p>验证该方法在 s3文件iocredentialsoverride 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testS3FileIoCredentialsOverride() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(S3FileIOProperties.ACCESS_KEY_ID, "unknown");
    properties.put(S3FileIOProperties.SECRET_ACCESS_KEY, "unknown");
    AwsClientFactory factory = AwsClientFactories.from(properties);
    S3Client s3Client = factory.s3();
    AssertHelpers.assertThrows(
        "Should fail request because of bad access key",
        S3Exception.class,
        "The AWS Access Key Id you provided does not exist in our records",
        () ->
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(AwsIntegTestUtil.testBucketName())
                    .key("key")
                    .build()));
  }

  /**
   * 测试场景：测试dynamodbendpointoverride。
   *
   * <p>验证该方法在对应输入下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDynamoDbEndpointOverride() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AwsProperties.DYNAMODB_ENDPOINT, "https://unknown:1234");
    AwsClientFactory factory = AwsClientFactories.from(properties);
    DynamoDbClient dynamoDbClient = factory.dynamo();
    AssertHelpers.assertThrowsCause(
        "Should refuse connection to unknown endpoint",
        SdkClientException.class,
        "Unable to execute HTTP request: unknown",
        dynamoDbClient::listTables);
  }
}
