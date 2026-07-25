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
package org.apache.iceberg.dell;

import java.io.Serializable;
import java.util.Map;

/**
 * Dell EMC ECS 连接配置属性集合。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，存储适配层的配置载体）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中定义 ECS S3 访问所需配置项的属性键（endpoint、AccessKey、SecretKey、 客户端工厂类名）。
 *   <li>从 catalog 属性 Map 中提取并持有这些连接配置，供工厂与 FileIO 使用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>类型安全封装：将散落在 Map 中的字符串配置收敛为带类型的字段与访问器，避免 调用方反复拼装属性键。
 *   <li>实现 {@link Serializable}：本对象需随 FileIO/Catalog 序列化到引擎 Executor 端， 因此配置载体本身必须可序列化。
 *   <li>常量键暴露：属性键以 {@code public static final String} 形式公开，既用于读取 也用于文档化配置项。
 * </ul>
 *
 * <p>上下游关系：由 {@code DellClientFactories.DefaultDellClientFactory}、{@code EcsFileIO} 构造使用；其属性来源于上层
 * catalog/fileio 配置。
 */
public class DellProperties implements Serializable {
  /** Dell EMC ECS S3 访问密钥 ID 的属性键。 */
  public static final String ECS_S3_ACCESS_KEY_ID = "ecs.s3.access-key-id";

  /** Dell EMC ECS S3 秘密访问密钥的属性键。 */
  public static final String ECS_S3_SECRET_ACCESS_KEY = "ecs.s3.secret-access-key";

  /** Dell EMC ECS S3 服务端点地址的属性键。 */
  public static final String ECS_S3_ENDPOINT = "ecs.s3.endpoint";

  /**
   * {@link DellClientFactory} 实现类的全限定名属性键，用于自定义 Dell 客户端配置。
   *
   * <p>设置后所有 Dell 客户端都将由该指定工厂初始化；未设置时使用 {@link
   * org.apache.iceberg.dell.DellClientFactories.DefaultDellClientFactory} 作为默认工厂。
   */
  public static final String CLIENT_FACTORY = "client.factory";

  private String ecsS3Endpoint;
  private String ecsS3AccessKeyId;
  private String ecsS3SecretAccessKey;

  /** 构造一个空的属性集合，字段稍后通过 setter 填充。 */
  public DellProperties() {}

  /**
   * 从 catalog 属性 Map 构造属性集合，提取 endpoint、AccessKey、SecretKey。
   *
   * @param properties catalog 配置属性
   */
  public DellProperties(Map<String, String> properties) {
    this.ecsS3AccessKeyId = properties.get(DellProperties.ECS_S3_ACCESS_KEY_ID);
    this.ecsS3SecretAccessKey = properties.get(DellProperties.ECS_S3_SECRET_ACCESS_KEY);
    this.ecsS3Endpoint = properties.get(DellProperties.ECS_S3_ENDPOINT);
  }

  /** 返回 ECS S3 服务端点地址。 */
  public String ecsS3Endpoint() {
    return ecsS3Endpoint;
  }

  /** 设置 ECS S3 服务端点地址。 */
  public void setEcsS3Endpoint(String ecsS3Endpoint) {
    this.ecsS3Endpoint = ecsS3Endpoint;
  }

  /** 返回 ECS S3 访问密钥 ID。 */
  public String ecsS3AccessKeyId() {
    return ecsS3AccessKeyId;
  }

  /** 设置 ECS S3 访问密钥 ID。 */
  public void setEcsS3AccessKeyId(String ecsS3AccessKeyId) {
    this.ecsS3AccessKeyId = ecsS3AccessKeyId;
  }

  /** 返回 ECS S3 秘密访问密钥。 */
  public String ecsS3SecretAccessKey() {
    return ecsS3SecretAccessKey;
  }

  /** 设置 ECS S3 秘密访问密钥。 */
  public void setEcsS3SecretAccessKey(String ecsS3SecretAccessKey) {
    this.ecsS3SecretAccessKey = ecsS3SecretAccessKey;
  }
}
