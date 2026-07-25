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

import java.io.Serializable;
import java.util.Map;
import org.apache.iceberg.common.DynClasses;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.apache.iceberg.util.PropertyUtil;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

public class AwsClientProperties implements Serializable {
  /**
   * 配置 AWS 客户端使用的凭证提供者。值为实现 {@link AwsCredentialsProvider} 接口的 全限定类名。
   *
   * <p>此外，该实现类还必须提供 create() 或 create(Map) 静态工厂方法，返回凭证提供者实例。
   *
   * <p>示例：
   * client.credentials-provider=software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider
   *
   * <p>设置后，默认客户端工厂 {@link org.apache.iceberg.aws.AwsClientFactories#defaultFactory()} 及其他 AWS 客户端工厂
   * 将使用此提供者获取凭证，而非走 AWS 默认凭证链。
   */
  public static final String CLIENT_CREDENTIALS_PROVIDER = "client.credentials-provider";

  /**
   * 凭证提供者专属属性前缀。以 {@code client.credentials-provider.} 开头的属性会被提取出来， 在通过 create(Map)
   * 创建凭证提供者时传入，用于向特定提供者传递自定义参数。
   */
  protected static final String CLIENT_CREDENTIAL_PROVIDER_PREFIX = "client.credentials-provider.";

  /** AWS 客户端区域。设置后，除 STS 客户端外的所有 AWS 客户端将使用此区域， 而非走 AWS 默认区域解析链。 */
  public static final String CLIENT_REGION = "client.region";

  private String clientRegion;
  private String clientCredentialsProvider;
  private final Map<String, String> clientCredentialsProviderProperties;

  /** 无参构造器：创建一个所有属性为 null 的实例，用于后续手动设置。 */
  public AwsClientProperties() {
    this.clientRegion = null;
    this.clientCredentialsProvider = null;
    this.clientCredentialsProviderProperties = null;
  }

  /**
   * 从配置 Map 构造实例，解析区域、凭证提供者类名及凭证提供者专属属性。
   *
   * @param properties Iceberg catalog 属性
   */
  public AwsClientProperties(Map<String, String> properties) {
    this.clientRegion = properties.get(CLIENT_REGION);
    this.clientCredentialsProvider = properties.get(CLIENT_CREDENTIALS_PROVIDER);
    this.clientCredentialsProviderProperties =
        PropertyUtil.propertiesWithPrefix(properties, CLIENT_CREDENTIAL_PROVIDER_PREFIX);
  }

  /** 返回配置的客户端区域，未设置时为 null。 */
  public String clientRegion() {
    return clientRegion;
  }

  /** 设置客户端区域。 */
  public void setClientRegion(String clientRegion) {
    this.clientRegion = clientRegion;
  }

  /**
   * 将区域配置应用到 AWS 客户端构建器上。
   *
   * <p>逻辑：仅当 clientRegion 非空时，调用 builder.region(Region.of(clientRegion))。
   *
   * <p>示例用法：
   *
   * <pre>
   *     S3Client.builder().applyMutation(awsClientProperties::applyClientRegionConfiguration)
   * </pre>
   *
   * @param builder AWS 客户端构建器
   * @param <T> 构建器类型
   */
  public <T extends AwsClientBuilder> void applyClientRegionConfiguration(T builder) {
    if (clientRegion != null) {
      builder.region(Region.of(clientRegion));
    }
  }

  /**
   * 将凭证提供者配置应用到 AWS 客户端构建器上。
   *
   * <p>逻辑：仅当 clientCredentialsProvider 非空时，通过反射加载该类并创建凭证提供者实例， 再调用 builder.credentialsProvider()
   * 设置。
   *
   * <p>示例用法：
   *
   * <pre>
   *     DynamoDbClient.builder().applyMutation(awsClientProperties::applyClientCredentialConfigurations)
   * </pre>
   *
   * @param builder AWS 客户端构建器
   * @param <T> 构建器类型
   */
  public <T extends AwsClientBuilder> void applyClientCredentialConfigurations(T builder) {
    if (!Strings.isNullOrEmpty(this.clientCredentialsProvider)) {
      builder.credentialsProvider(credentialsProvider(this.clientCredentialsProvider));
    }
  }

  /**
   * 返回凭证提供者实例，按优先级降级选择。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若 accessKeyId 与 secretAccessKey 均非空：有 sessionToken 则构建会话凭证， 否则构建基本凭证，均包装为
   *       StaticCredentialsProvider。
   *   <li>否则若 clientCredentialsProvider 非空：通过反射加载该类并调用其 create(Map) 或 create() 静态方法创建实例。
   *   <li>以上均不满足：返回 DefaultCredentialsProvider，走 AWS 默认凭证链。
   * </ol>
   *
   * @param accessKeyId AWS 访问密钥 ID
   * @param secretAccessKey AWS 秘密访问密钥
   * @param sessionToken AWS 会话令牌（临时凭证时使用）
   * @return 凭证提供者实例
   */
  @SuppressWarnings("checkstyle:HiddenField")
  public AwsCredentialsProvider credentialsProvider(
      String accessKeyId, String secretAccessKey, String sessionToken) {
    if (!Strings.isNullOrEmpty(accessKeyId) && !Strings.isNullOrEmpty(secretAccessKey)) {
      if (Strings.isNullOrEmpty(sessionToken)) {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey));
      } else {
        return StaticCredentialsProvider.create(
            AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken));
      }
    }

    if (!Strings.isNullOrEmpty(this.clientCredentialsProvider)) {
      return credentialsProvider(this.clientCredentialsProvider);
    }

    // Create a new credential provider for each client
    return DefaultCredentialsProvider.builder().build();
  }

  /**
   * 通过反射加载凭证提供者类并创建实例。
   *
   * <p>逻辑：使用 DynClasses 加载指定类，校验其实现了 AwsCredentialsProvider 接口， 然后委托 {@link
   * #createCredentialsProvider(Class)} 创建实例。类找不到或无 create 方法 时抛 IllegalArgumentException。
   *
   * @param credentialsProviderClass 凭证提供者全限定类名
   * @return 凭证提供者实例
   * @throws IllegalArgumentException 类不存在、未实现接口或缺少 create 方法
   */
  private AwsCredentialsProvider credentialsProvider(String credentialsProviderClass) {
    Class<?> providerClass;
    try {
      providerClass = DynClasses.builder().impl(credentialsProviderClass).buildChecked();
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot load class %s, it does not exist in the classpath", credentialsProviderClass),
          e);
    }

    Preconditions.checkArgument(
        AwsCredentialsProvider.class.isAssignableFrom(providerClass),
        String.format(
            "Cannot initialize %s, it does not implement %s.",
            credentialsProviderClass, AwsCredentialsProvider.class.getName()));

    try {
      return createCredentialsProvider(providerClass);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot create an instance of %s, it does not contain a static 'create' or 'create(Map<String, String>)' method",
              credentialsProviderClass),
          e);
    }
  }

  /**
   * 调用凭证提供者类的静态工厂方法创建实例。
   *
   * <p>逻辑：优先尝试调用 create(Map) 静态方法（传入凭证提供者专属属性）； 若不存在则降级调用无参 create() 静态方法。
   *
   * @param providerClass 凭证提供者类
   * @return 凭证提供者实例
   * @throws NoSuchMethodException 类既无 create(Map) 也无 create() 静态方法
   */
  private AwsCredentialsProvider createCredentialsProvider(Class<?> providerClass)
      throws NoSuchMethodException {
    AwsCredentialsProvider provider;
    try {
      provider =
          DynMethods.builder("create")
              .hiddenImpl(providerClass, Map.class)
              .buildStaticChecked()
              .invoke(clientCredentialsProviderProperties);
    } catch (NoSuchMethodException e) {
      provider =
          DynMethods.builder("create").hiddenImpl(providerClass).buildStaticChecked().invoke();
    }
    return provider;
  }
}
