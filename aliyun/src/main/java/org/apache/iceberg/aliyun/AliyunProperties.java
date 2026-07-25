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
package org.apache.iceberg.aliyun;

import java.io.Serializable;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 文件级说明：阿里云 OSS 集成模块的配置属性集合。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本类作为 iceberg-aliyun 的配置中心， 集中定义所有可配置项的属性键名，并负责从 catalog 属性
 * map 中解析出运行时所需的具体值。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 OSS endpoint、accessKeyId、accessKeySecret、staging 目录、自定义工厂类等配置键。
 *   <li>在构造时一次性从属性 map 中解析所有配置值，并提供只读访问方法。
 *   <li>为 staging 目录提供默认值（{@code java.io.tmpdir}），降低使用门槛。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不可变配置对象：所有字段为 final，构造后只读，保证线程安全与序列化安全。
 *   <li>属性键集中管理：以 public static final String 形式暴露键名，便于 catalog 层 统一引用，避免拼写错误。
 *   <li>实现 Serializable：配置对象需随 {@link AliyunClientFactory} 序列化到分布式引擎执行端。
 * </ul>
 *
 * <p>上下游关系：被 {@link AliyunClientFactories}、{@link OSSFileIO}、{@code OSSOutputStream} 等读写；上游为
 * Iceberg catalog 属性 map；下游为阿里云 OSS SDK 与本地 staging 目录。
 */
public class AliyunProperties implements Serializable {
  /**
   * OSS 访问域名（endpoint）。
   *
   * <p>OSS 通过 HTTP Restful API 对外提供服务，不同地域使用不同的 endpoint； 同一地域内网访问与公网访问也使用不同 endpoint。详见：
   * https://www.alibabacloud.com/help/doc-detail/31837.htm
   */
  public static final String OSS_ENDPOINT = "oss.endpoint";

  /**
   * 阿里云 AccessKey ID。
   *
   * <p>阿里云使用 AccessKey 对（AccessKey ID + AccessKey Secret）实现对称加密并验证请求者身份。 AccessKey ID 用于标识用户身份。
   *
   * <p>获取 AccessKey 对的方式详见：https://www.alibabacloud.com/help/doc-detail/53045.htm
   */
  public static final String CLIENT_ACCESS_KEY_ID = "client.access-key-id";

  /**
   * 阿里云 AccessKey Secret。
   *
   * <p>AccessKey Secret 用于加密与校验签名串，与 {@link #CLIENT_ACCESS_KEY_ID} 配对使用。
   *
   * <p>获取 AccessKey 对的方式详见：https://www.alibabacloud.com/help/doc-detail/53045.htm
   */
  public static final String CLIENT_ACCESS_KEY_SECRET = "client.access-key-secret";

  /**
   * 自定义 {@link AliyunClientFactory} 实现类的全限定名。
   *
   * <p>若设置，所有 OSS 客户端都由该工厂初始化；若未设置，则使用 {@link AliyunClientFactories#defaultFactory()} 作为默认工厂。
   */
  public static final String CLIENT_FACTORY = "client.factory-impl";

  /**
   * 上传 OSS 前的本地暂存（staging）目录。
   *
   * <p>默认值为 {@code java.io.tmpdir} 系统属性指向的目录。{@code OSSOutputStream} 会先将数据 写入该目录下的临时文件，再整体上传到 OSS。
   */
  public static final String OSS_STAGING_DIRECTORY = "oss.staging-dir";

  private final String ossEndpoint;
  private final String accessKeyId;
  private final String accessKeySecret;
  private final String ossStagingDirectory;

  /**
   * 构造一个空的 {@link AliyunProperties}（所有值为 null 或默认值）。
   *
   * <p>适用于不依赖具体配置的场景，例如仅需工厂对象占位时。
   */
  public AliyunProperties() {
    this(ImmutableMap.of());
  }

  /**
   * 从属性 map 解析并构造 {@link AliyunProperties}。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>从 map 中读取 OSS endpoint、accessKeyId、accessKeySecret。
   *   <li>读取 staging 目录，未配置时回退到 {@code java.io.tmpdir}。
   * </ul>
   *
   * @param properties catalog / 引擎传入的属性集合
   */
  public AliyunProperties(Map<String, String> properties) {
    // OSS endpoint, accessKeyId, accessKeySecret.
    this.ossEndpoint = properties.get(OSS_ENDPOINT);
    this.accessKeyId = properties.get(CLIENT_ACCESS_KEY_ID);
    this.accessKeySecret = properties.get(CLIENT_ACCESS_KEY_SECRET);

    this.ossStagingDirectory =
        PropertyUtil.propertyAsString(
            properties, OSS_STAGING_DIRECTORY, System.getProperty("java.io.tmpdir"));
  }

  /** 返回解析到的 OSS endpoint。 */
  public String ossEndpoint() {
    return ossEndpoint;
  }

  /** 返回解析到的 AccessKey ID。 */
  public String accessKeyId() {
    return accessKeyId;
  }

  /** 返回解析到的 AccessKey Secret。 */
  public String accessKeySecret() {
    return accessKeySecret;
  }

  /** 返回解析到的 OSS staging 目录路径。 */
  public String ossStagingDirectory() {
    return ossStagingDirectory;
  }
}
