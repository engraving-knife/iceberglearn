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

import java.io.IOException;
import java.util.Map;
import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.aws.lakeformation.LakeFormationAwsClientFactory;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.SerializationUtil;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.GetTablesRequest;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：测试 TestAwsClientFactories 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestAwsClientFactories 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestAwsClientFactories {

  /**
   * 测试场景：Load Default。
   *
   * <p>验证该方法在 Load Default 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadDefault() {
    Assertions.assertThat(AwsClientFactories.defaultFactory())
        .as("default client should be singleton")
        .isSameAs(AwsClientFactories.defaultFactory());

    Assertions.assertThat(AwsClientFactories.from(Maps.newHashMap()))
        .as("should load default when not configured")
        .isInstanceOf(AwsClientFactories.DefaultAwsClientFactory.class);
  }

  /**
   * 测试场景：Load Custom。
   *
   * <p>验证该方法在 Load Custom 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCustom() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AwsProperties.CLIENT_FACTORY, CustomFactory.class.getName());
    Assertions.assertThat(AwsClientFactories.from(properties))
        .as("should load custom class")
        .isInstanceOf(CustomFactory.class);
  }

  /**
   * 测试场景：3 File Io Credentials Verification。
   *
   * <p>验证该方法在 3 File Io Credentials Verification 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIoCredentialsVerification() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(S3FileIOProperties.ACCESS_KEY_ID, "key");

    Assertions.assertThatThrownBy(() -> AwsClientFactories.from(properties))
        .isInstanceOf(ValidationException.class)
        .hasMessage("S3 client access key ID and secret access key must be set at the same time");

    properties.remove(S3FileIOProperties.ACCESS_KEY_ID);
    properties.put(S3FileIOProperties.SECRET_ACCESS_KEY, "secret");

    Assertions.assertThatThrownBy(() -> AwsClientFactories.from(properties))
        .isInstanceOf(ValidationException.class)
        .hasMessage("S3 client access key ID and secret access key must be set at the same time");
  }

  /**
   * 测试场景：Default Aws Client Factory Serializable。
   *
   * <p>验证该方法在 Default Aws Client Factory Serializable 条件下的行为是否符合预期。
   */
  @Test
  public void testDefaultAwsClientFactorySerializable() throws IOException {
    Map<String, String> properties = Maps.newHashMap();
    AwsClientFactory defaultAwsClientFactory = AwsClientFactories.from(properties);
    AwsClientFactory roundTripResult =
        TestHelpers.KryoHelpers.roundTripSerialize(defaultAwsClientFactory);
    Assertions.assertThat(roundTripResult)
        .isInstanceOf(AwsClientFactories.DefaultAwsClientFactory.class);

    byte[] serializedFactoryBytes = SerializationUtil.serializeToBytes(defaultAwsClientFactory);
    AwsClientFactory deserializedClientFactory =
        SerializationUtil.deserializeFromBytes(serializedFactoryBytes);
    Assertions.assertThat(deserializedClientFactory)
        .isInstanceOf(AwsClientFactories.DefaultAwsClientFactory.class);
  }

  /**
   * 测试场景：Assume Role Aws Client Factory Serializable。
   *
   * <p>验证该方法在 Assume Role Aws Client Factory Serializable 条件下的行为是否符合预期。
   */
  @Test
  public void testAssumeRoleAwsClientFactorySerializable() throws IOException {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AwsProperties.CLIENT_FACTORY, AssumeRoleAwsClientFactory.class.getName());
    properties.put(AwsProperties.CLIENT_ASSUME_ROLE_ARN, "arn::test");
    properties.put(AwsProperties.CLIENT_ASSUME_ROLE_REGION, "us-east-1");
    AwsClientFactory assumeRoleAwsClientFactory = AwsClientFactories.from(properties);
    AwsClientFactory roundTripResult =
        TestHelpers.KryoHelpers.roundTripSerialize(assumeRoleAwsClientFactory);
    Assertions.assertThat(roundTripResult).isInstanceOf(AssumeRoleAwsClientFactory.class);

    byte[] serializedFactoryBytes = SerializationUtil.serializeToBytes(assumeRoleAwsClientFactory);
    AwsClientFactory deserializedClientFactory =
        SerializationUtil.deserializeFromBytes(serializedFactoryBytes);
    Assertions.assertThat(deserializedClientFactory).isInstanceOf(AssumeRoleAwsClientFactory.class);
  }

  /**
   * 测试场景：Lake Formation Aws Client Factory Serializable。
   *
   * <p>验证该方法在 Lake Formation Aws Client Factory Serializable 条件下的行为是否符合预期。
   */
  @Test
  public void testLakeFormationAwsClientFactorySerializable() throws IOException {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AwsProperties.CLIENT_FACTORY, LakeFormationAwsClientFactory.class.getName());
    properties.put(AwsProperties.CLIENT_ASSUME_ROLE_ARN, "arn::test");
    properties.put(AwsProperties.CLIENT_ASSUME_ROLE_REGION, "us-east-1");
    properties.put(
        AwsProperties.CLIENT_ASSUME_ROLE_TAGS_PREFIX
            + LakeFormationAwsClientFactory.LF_AUTHORIZED_CALLER,
        "emr");
    AwsClientFactory lakeFormationAwsClientFactory = AwsClientFactories.from(properties);
    AwsClientFactory roundTripResult =
        TestHelpers.KryoHelpers.roundTripSerialize(lakeFormationAwsClientFactory);
    Assertions.assertThat(roundTripResult).isInstanceOf(LakeFormationAwsClientFactory.class);

    byte[] serializedFactoryBytes =
        SerializationUtil.serializeToBytes(lakeFormationAwsClientFactory);
    AwsClientFactory deserializedClientFactory =
        SerializationUtil.deserializeFromBytes(serializedFactoryBytes);
    Assertions.assertThat(deserializedClientFactory)
        .isInstanceOf(LakeFormationAwsClientFactory.class);
  }

  /**
   * 测试场景：With Dummy Valid Credentials Provider。
   *
   * <p>验证该方法在 With Dummy Valid Credentials Provider 条件下的行为是否符合预期。
   */
  @Test
  public void testWithDummyValidCredentialsProvider() {
    AwsClientFactory defaultAwsClientFactory =
        getAwsClientFactoryByCredentialsProvider(DummyValidProvider.class.getName());
    assertDefaultAwsClientFactory(defaultAwsClientFactory);
    assertClientObjectsNotNull(defaultAwsClientFactory);
    // Ensuring S3Exception thrown instead exception thrown by resolveCredentials() implemented by
    // test credentials provider
    Assertions.assertThatThrownBy(() -> defaultAwsClientFactory.s3().listBuckets())
        .isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class)
        .hasMessageContaining("The AWS Access Key Id you provided does not exist in our records");
  }

  /**
   * 测试场景：With No Create Method Credentials Provider。
   *
   * <p>验证该方法在 With No Create Method Credentials Provider 条件下的行为是否符合预期。
   */
  @Test
  public void testWithNoCreateMethodCredentialsProvider() {
    String providerClassName = NoCreateMethod.class.getName();
    String containsMessage =
        "it does not contain a static 'create' or 'create(Map<String, String>)' method";
    testProviderAndAssertThrownBy(providerClassName, containsMessage);
  }

  /**
   * 测试场景：With No Arg Create Method Credentials Provider。
   *
   * <p>验证该方法在 With No Arg Create Method Credentials Provider 条件下的行为是否符合预期。
   */
  @Test
  public void testWithNoArgCreateMethodCredentialsProvider() {
    String providerClassName = CreateMethod.class.getName();
    String containsMessage = "Unable to load credentials from " + providerClassName;
    testProviderAndAssertThrownBy(providerClassName, containsMessage);
  }

  /**
   * 测试场景：With Map Arg Create Method Credentials Provider。
   *
   * <p>验证该方法在 With Map Arg Create Method Credentials Provider 条件下的行为是否符合预期。
   */
  @Test
  public void testWithMapArgCreateMethodCredentialsProvider() {
    String providerClassName = CreateMapMethod.class.getName();
    String containsMessage = "Unable to load credentials from " + providerClassName;
    testProviderAndAssertThrownBy(providerClassName, containsMessage);
  }

  /**
   * 测试场景：With Class Does Not Exists Credentials Provider。
   *
   * <p>验证该方法在 With Class Does Not Exists Credentials Provider 条件下的行为是否符合预期。
   */
  @Test
  public void testWithClassDoesNotExistsCredentialsProvider() {
    String providerClassName = "invalidClassName";
    String containsMessage = "it does not exist in the classpath";
    testProviderAndAssertThrownBy(providerClassName, containsMessage);
  }

  /**
   * 测试场景：With Class Does Not Implement Credentials Provider。
   *
   * <p>验证该方法在 With Class Does Not Implement Credentials Provider 条件下的行为是否符合预期。
   */
  @Test
  public void testWithClassDoesNotImplementCredentialsProvider() {
    String providerClassName = NoInterface.class.getName();
    String containsMessage =
        "it does not implement software.amazon.awssdk.auth.credentials.AwsCredentialsProvider";
    testProviderAndAssertThrownBy(providerClassName, containsMessage);
  }

  /**
   * 测试场景：Provider And Assert Thrown By。
   *
   * <p>验证该方法在 Provider And Assert Thrown By 条件下的行为是否符合预期。
   */
  private void testProviderAndAssertThrownBy(String providerClassName, String containsMessage) {
    AwsClientFactory defaultAwsClientFactory =
        getAwsClientFactoryByCredentialsProvider(providerClassName);
    assertDefaultAwsClientFactory(defaultAwsClientFactory);
    assertAllClientObjectsThrownBy(defaultAwsClientFactory, containsMessage);
  }

  /** 辅助方法：assertAllClientObjectsThrownBy。 */
  public void assertAllClientObjectsThrownBy(
      AwsClientFactory defaultAwsClientFactory, String containsMessage) {
    // invoking sdk client apis to ensure resolveCredentials() being called
    assertThatThrownBy(() -> defaultAwsClientFactory.s3().listBuckets(), containsMessage);
    assertThatThrownBy(
        () -> defaultAwsClientFactory.glue().getTables(GetTablesRequest.builder().build()),
        containsMessage);
    assertThatThrownBy(() -> defaultAwsClientFactory.dynamo().listTables(), containsMessage);
    assertThatThrownBy(() -> defaultAwsClientFactory.kms().listAliases(), containsMessage);
  }

  /** 辅助方法：assertClientObjectsNotNull。 */
  private void assertClientObjectsNotNull(AwsClientFactory defaultAwsClientFactory) {
    Assertions.assertThat(defaultAwsClientFactory.s3()).isNotNull();
    Assertions.assertThat(defaultAwsClientFactory.dynamo()).isNotNull();
    Assertions.assertThat(defaultAwsClientFactory.glue()).isNotNull();
    Assertions.assertThat(defaultAwsClientFactory.kms()).isNotNull();
  }

  /** 辅助方法：assertThatThrownBy。 */
  private void assertThatThrownBy(
      ThrowableAssert.ThrowingCallable shouldRaiseThrowable, String containsMessage) {
    Assertions.assertThatThrownBy(shouldRaiseThrowable)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(containsMessage);
  }

  /** 辅助方法：assertDefaultAwsClientFactory。 */
  private void assertDefaultAwsClientFactory(AwsClientFactory awsClientFactory) {
    Assertions.assertThat(awsClientFactory)
        .isInstanceOf(AwsClientFactories.DefaultAwsClientFactory.class);
  }

  /** 辅助方法：getAwsClientFactoryByCredentialsProvider。 */
  private AwsClientFactory getAwsClientFactoryByCredentialsProvider(String providerClass) {
    Map<String, String> properties = getDefaultClientFactoryProperties(providerClass);
    AwsClientFactory defaultAwsClientFactory = AwsClientFactories.from(properties);
    return defaultAwsClientFactory;
  }

  /** 辅助方法：getDefaultClientFactoryProperties。 */
  private Map<String, String> getDefaultClientFactoryProperties(String providerClass) {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AwsClientProperties.CLIENT_CREDENTIALS_PROVIDER + ".param1", "value1");
    properties.put(AwsClientProperties.CLIENT_REGION, Region.AWS_GLOBAL.toString());
    properties.put(AwsClientProperties.CLIENT_CREDENTIALS_PROVIDER, providerClass);
    return properties;
  }

  private static class NoInterface {}

  private static class DummyValidProvider implements AwsCredentialsProvider {

    /** 辅助方法：create。 */
    public static DummyValidProvider create() {
      return new DummyValidProvider();
    }

    /** 辅助方法：resolveCredentials。 */
    @Override
    public AwsCredentials resolveCredentials() {
      return AwsBasicCredentials.create("test-accessKeyId", "test-secretAccessKey");
    }
  }

  private abstract static class ProviderTestBase implements AwsCredentialsProvider {

    /** 辅助方法：resolveCredentials。 */
    @Override
    public AwsCredentials resolveCredentials() {
      throw new IllegalArgumentException(
          "Unable to load credentials from " + this.getClass().getName());
    }
  }

  private static class NoCreateMethod extends ProviderTestBase {}

  private static class CreateMethod extends ProviderTestBase {
    /** 辅助方法：create。 */
    public static CreateMethod create() {
      return new CreateMethod();
    }
  }

  private static class CreateMapMethod extends ProviderTestBase {

    private final Map<String, String> properties;

    CreateMapMethod(Map<String, String> properties) {
      this.properties = Preconditions.checkNotNull(properties, "properties cannot be null");
      Preconditions.checkArgument(properties.get("param1") != null, "param1 value cannot be null");
    }

    /** 辅助方法：create。 */
    public static CreateMapMethod create(Map<String, String> properties) {
      return new CreateMapMethod(properties);
    }
  }

  public static class CustomFactory implements AwsClientFactory {

    /** 辅助方法：CustomFactory。 */
    public CustomFactory() {}

    /** 辅助方法：s3。 */
    @Override
    public S3Client s3() {
      return null;
    }

    /** 辅助方法：glue。 */
    @Override
    public GlueClient glue() {
      return null;
    }

    /** 辅助方法：kms。 */
    @Override
    public KmsClient kms() {
      return null;
    }

    /** 辅助方法：dynamo。 */
    @Override
    public DynamoDbClient dynamo() {
      return null;
    }

    /** 辅助方法：initialize。 */
    @Override
    public void initialize(Map<String, String> properties) {}
  }
}
