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

import java.io.Serializable;
import java.util.Map;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：S3FileIO 专用的 AWS 客户端工厂 SPI 接口。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：定义 S3FileIO 创建 S3 客户端的契约，允许用户通过 {@link S3FileIOProperties#CLIENT_FACTORY} 配置自定义工厂实现，例如注入
 * STS 凭证、 自定义端点、S3 Access Point、自定义 HTTP 客户端等。
 *
 * <p>设计意图：与 {@link org.apache.iceberg.aws.AwsClientFactory} 类似但仅聚焦 S3， 因为 S3FileIO 是 Iceberg 中最常见的
 * IO 实现，需要更细粒度的扩展能力； 实现 Serializable 以支持分布式引擎分发。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.aws.S3FileIOAwsClientFactories} 反射加载； 默认实现 {@link
 * DefaultS3FileIOAwsClientFactory}，其他实现可由用户自定义。
 */
public interface S3FileIOAwsClientFactory extends Serializable {
  /**
   * 创建并返回一个 Amazon S3 客户端。
   *
   * @return s3 client
   */
  S3Client s3();
  /**
   * 从 catalog/FileIO properties 初始化工厂内部状态。
   *
   * @param properties catalog properties
   */
  void initialize(Map<String, String> properties);
}
