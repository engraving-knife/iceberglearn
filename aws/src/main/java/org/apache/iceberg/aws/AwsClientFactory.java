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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：AWS 客户端工厂 SPI 接口。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 Iceberg 访问 AWS 各项托管服务（S3、Glue、KMS、DynamoDB）时所需客户端的统一创建入口。
 *   <li>作为可插拔扩展点，允许用户通过 {@code client.factory} 配置自定义客户端构造行为， 例如注入 AssumeRole、自定义凭证、代理、端点覆盖等。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>面向接口编程：Iceberg 不直接依赖具体 AWS SDK 客户端实例化逻辑，而是通过本接口解耦， 便于在不同部署环境（EMR、Lambda、本地、跨账号）下复用
 *       catalog/FileIO 主体逻辑。
 *   <li>Serializable：实现序列化接口以支持 Spark/Flink 等引擎在分布式环境下分发客户端工厂。
 *   <li>契约要求：自定义实现必须提供无参构造器，由 {@link #initialize(Map)} 完成初始化， 保证反射加载与配置注入分离。
 * </ul>
 *
 * <p>上下游关系：由 {@link S3FileIOAwsClientFactories} 通过反射加载与实例化； 实现类如 {@link
 * AssumeRoleAwsClientFactory}、{@code DefaultAwsClientFactory} 等； 产出的客户端被
 * S3FileIO、GlueCatalog、DynamoDbLockManager、KMS 元数据加密等组件使用。
 */
public interface AwsClientFactory extends Serializable {

  /**
   * 创建并返回一个 Amazon S3 客户端，用于读写 S3 上的数据文件与元数据文件。
   *
   * @return 已配置好的 S3 客户端
   */
  S3Client s3();

  /**
   * 创建并返回一个 AWS Glue 客户端，用于访问 Glue 数据目录（库/表/分区）。
   *
   * @return 已配置好的 Glue 客户端
   */
  GlueClient glue();

  /**
   * 创建并返回一个 AWS KMS 客户端，用于 SSE-KMS 元数据加解密。
   *
   * @return 已配置好的 KMS 客户端
   */
  KmsClient kms();

  /**
   * 创建并返回一个 Amazon DynamoDB 客户端，用于 DynamoDB 锁管理器与表目录实现。
   *
   * @return 已配置好的 DynamoDB 客户端
   */
  DynamoDbClient dynamo();

  /**
   * 根据 catalog/FileIO 配置 properties 初始化工厂内部状态。
   *
   * <p>调用时机：反射构造实例后立即调用一次，此后再调用 s3()/glue()/kms()/dynamo()。
   *
   * @param properties catalog/FileIO 全部配置键值
   */
  void initialize(Map<String, String> properties);
}
