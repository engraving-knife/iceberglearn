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

import java.util.Map;
import org.apache.iceberg.aws.AwsClientProperties;
import org.apache.iceberg.aws.HttpClientProperties;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：S3FileIO 默认 AWS 客户端工厂实现。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：实现 {@link S3FileIOAwsClientFactory}，使用默认凭证链与默认区域链构造 {@link
 * S3Client}，按需叠加区域、HTTP、端点、服务、凭证、签名器配置。
 *
 * <p>设计意图：作为 S3FileIO 在未配置自定义工厂时的默认实现，按 properties 中可选项配置 各项能力，未配置时全部回退到 AWS SDK 默认行为，保证开箱即用。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.aws.S3FileIOAwsClientFactories} 在未配置 {@code
 * s3.client-factory-impl} 时通过 {@link org.apache.iceberg.aws.AwsClientFactories#from(Map)} 间接选用；产出的
 * S3Client 被 S3FileIO 用于读写数据与元数据文件。
 */
class DefaultS3FileIOAwsClientFactory implements S3FileIOAwsClientFactory {
  private S3FileIOProperties s3FileIOProperties;
  private HttpClientProperties httpClientProperties;
  private AwsClientProperties awsClientProperties;

  /** 构造默认实例，三个 properties 均使用默认空配置。 */
  DefaultS3FileIOAwsClientFactory() {
    this.s3FileIOProperties = new S3FileIOProperties();
    this.httpClientProperties = new HttpClientProperties();
    this.awsClientProperties = new AwsClientProperties();
  }

  /**
   * 从 catalog/FileIO properties 重新解析三个 properties，覆盖默认空配置。
   *
   * @param properties catalog/FileIO 配置键值
   */
  @Override
  public void initialize(Map<String, String> properties) {
    this.s3FileIOProperties = new S3FileIOProperties(properties);
    this.awsClientProperties = new AwsClientProperties(properties);
    this.httpClientProperties = new HttpClientProperties(properties);
  }

  /**
   * 构造并返回 S3Client，依次注入区域、HTTP、S3 端点、S3 服务、凭证与签名器配置。
   *
   * <p>逻辑：通过 applyMutation 链式叠加，由 SDK build() 完成最终实例化；凭证由 {@link
   * S3FileIOProperties#applyCredentialConfigurations} 与 AwsClientProperties 协作注入。
   *
   * @return 已配置好的 S3 客户端
   */
  @Override
  public S3Client s3() {
    return S3Client.builder()
        .applyMutation(awsClientProperties::applyClientRegionConfiguration)
        .applyMutation(httpClientProperties::applyHttpClientConfigurations)
        .applyMutation(s3FileIOProperties::applyEndpointConfigurations)
        .applyMutation(s3FileIOProperties::applyServiceConfigurations)
        .applyMutation(
            s3ClientBuilder ->
                s3FileIOProperties.applyCredentialConfigurations(
                    awsClientProperties, s3ClientBuilder))
        .applyMutation(s3FileIOProperties::applySignerConfiguration)
        .build();
  }
}
