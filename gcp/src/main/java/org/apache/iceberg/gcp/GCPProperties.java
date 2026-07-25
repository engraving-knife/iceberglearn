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
package org.apache.iceberg.gcp;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.apache.iceberg.util.PropertyUtil;

/**
 * GCP/GCS 配置属性集合。
 *
 * <p>所属模块：iceberg-gcp（GCP 集成模块，基于 Google Cloud Storage 提供 Iceberg 的 FileIO 实现，处于 Iceberg 存储访问层，被
 * iceberg-core / 各 catalog / 引擎集成模块调用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中定义 GCS 相关的配置项 key（项目 ID、服务地址、加解密 Key、OAuth2 令牌、 读写通道 chunk 大小、批量删除批次大小等）。
 *   <li>从属性 Map 中解析并持有这些配置值，向 GCS FileIO 及其输入输出流提供类型安全的 访问器。
 *   <li>实现 {@link Serializable}，以便在 Spark/Flink 等分布式引擎中随 FileIO 序列化 到各执行节点。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>配置项以字符串 key 暴露为常量，便于引擎层通过统一 properties 传递；解析后以 Optional 形式返回，让调用方显式处理缺失场景，避免 null 散落。
 *   <li>数值类配置仅在 key 存在时解析，避免对未设置项抛出异常。
 *   <li>批量删除批次大小默认 50（GCS 上限 100），在批量删除性能与单请求体大小之间取折中。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.gcp.gcs.GCSFileIO}、 {@link
 * org.apache.iceberg.gcp.gcs.GCSInputStream}、 {@link org.apache.iceberg.gcp.gcs.GCSOutputStream}、
 * {@link org.apache.iceberg.gcp.gcs.BaseGCSFile} 等用于构造 GCS 客户端及读写流。
 */
public class GCPProperties implements Serializable {
  // 服务选项（Service Options）
  public static final String GCS_PROJECT_ID = "gcs.project-id";
  public static final String GCS_CLIENT_LIB_TOKEN = "gcs.client-lib-token";
  public static final String GCS_SERVICE_HOST = "gcs.service.host";

  // GCS 配置属性（Configuration Properties）
  public static final String GCS_DECRYPTION_KEY = "gcs.decryption-key";
  public static final String GCS_ENCRYPTION_KEY = "gcs.encryption-key";
  public static final String GCS_USER_PROJECT = "gcs.user-project";

  public static final String GCS_CHANNEL_READ_CHUNK_SIZE = "gcs.channel.read.chunk-size-bytes";
  public static final String GCS_CHANNEL_WRITE_CHUNK_SIZE = "gcs.channel.write.chunk-size-bytes";

  public static final String GCS_OAUTH2_TOKEN = "gcs.oauth2.token";
  public static final String GCS_OAUTH2_TOKEN_EXPIRES_AT = "gcs.oauth2.token-expires-at";

  /** 配置从某个 GCS bucket 批量删除文件时使用的批次大小。 */
  public static final String GCS_DELETE_BATCH_SIZE = "gcs.delete.batch-size";
  /**
   * 删除操作的批次大小上限。GCS 建议单批次最多 100 个 key，因此默认取一个低于该上限的值。
   * 参考：https://cloud.google.com/storage/docs/batch
   */
  public static final int GCS_DELETE_BATCH_SIZE_DEFAULT = 50;

  private String projectId;
  private String clientLibToken;
  private String serviceHost;

  private String gcsDecryptionKey;
  private String gcsEncryptionKey;
  private String gcsUserProject;

  private Integer gcsChannelReadChunkSize;
  private Integer gcsChannelWriteChunkSize;

  private String gcsOAuth2Token;
  private Date gcsOAuth2TokenExpiresAt;

  private int gcsDeleteBatchSize = GCS_DELETE_BATCH_SIZE_DEFAULT;

  /** 无参构造器，所有字段使用默认值；属性后续通过显式赋值或反序列化注入。 */
  public GCPProperties() {}

  /**
   * 基于属性 Map 构造 GCP 配置对象。
   *
   * <p>逻辑：逐项从 properties 中读取并解析对应的 GCS 配置项（项目 ID、客户端令牌、服务地址、 加解密 Key、用户项目、读写通道 chunk 大小、OAuth2
   * 令牌及过期时间、批量删除批次大小等）。 数值类配置仅在 key 存在时解析，避免对未设置项抛 NPE；过期时间以毫秒时间戳字符串转 {@link Date}。OAuth2 过期时间使用
   * {@code java.util.Date} 是因 GCP SDK 直接消费该类型。
   *
   * @param properties 来自引擎或 catalog 的字符串型配置键值对
   */
  @SuppressWarnings("JavaUtilDate") // GCP API uses java.util.Date
  public GCPProperties(Map<String, String> properties) {
    projectId = properties.get(GCS_PROJECT_ID);
    clientLibToken = properties.get(GCS_CLIENT_LIB_TOKEN);
    serviceHost = properties.get(GCS_SERVICE_HOST);

    gcsDecryptionKey = properties.get(GCS_DECRYPTION_KEY);
    gcsEncryptionKey = properties.get(GCS_ENCRYPTION_KEY);
    gcsUserProject = properties.get(GCS_USER_PROJECT);

    if (properties.containsKey(GCS_CHANNEL_READ_CHUNK_SIZE)) {
      gcsChannelReadChunkSize = Integer.parseInt(properties.get(GCS_CHANNEL_READ_CHUNK_SIZE));
    }

    if (properties.containsKey(GCS_CHANNEL_WRITE_CHUNK_SIZE)) {
      gcsChannelWriteChunkSize = Integer.parseInt(properties.get(GCS_CHANNEL_WRITE_CHUNK_SIZE));
    }

    gcsOAuth2Token = properties.get(GCS_OAUTH2_TOKEN);
    if (properties.containsKey(GCS_OAUTH2_TOKEN_EXPIRES_AT)) {
      gcsOAuth2TokenExpiresAt =
          new Date(Long.parseLong(properties.get(GCS_OAUTH2_TOKEN_EXPIRES_AT)));
    }

    gcsDeleteBatchSize =
        PropertyUtil.propertyAsInt(
            properties, GCS_DELETE_BATCH_SIZE, GCS_DELETE_BATCH_SIZE_DEFAULT);
  }

  /** 返回读通道的 chunk 大小（未配置则返回 {@link Optional#empty()}）。 */
  public Optional<Integer> channelReadChunkSize() {
    return Optional.ofNullable(gcsChannelReadChunkSize);
  }

  /** 返回写通道的 chunk 大小（未配置则返回 {@link Optional#empty()}）。 */
  public Optional<Integer> channelWriteChunkSize() {
    return Optional.ofNullable(gcsChannelWriteChunkSize);
  }

  /** 返回客户端库令牌（用于在 GCS 请求中标识客户端来源）。 */
  public Optional<String> clientLibToken() {
    return Optional.ofNullable(clientLibToken);
  }

  /** 返回读取时使用的解密 Key（用于客户自管理加密密钥场景）。 */
  public Optional<String> decryptionKey() {
    return Optional.ofNullable(gcsDecryptionKey);
  }

  /** 返回写入时使用的加密 Key（用于客户自管理加密密钥场景）。 */
  public Optional<String> encryptionKey() {
    return Optional.ofNullable(gcsEncryptionKey);
  }

  /** 返回 GCP 项目 ID。 */
  public Optional<String> projectId() {
    return Optional.ofNullable(projectId);
  }

  /** 返回 GCS 服务地址（自定义 host，常用于测试或私有化部署）。 */
  public Optional<String> serviceHost() {
    return Optional.ofNullable(serviceHost);
  }

  /** 返回请求计费所归属的项目（user-project，用于跨项目访问 bucket 时计费）。 */
  public Optional<String> userProject() {
    return Optional.ofNullable(gcsUserProject);
  }

  /** 返回 OAuth2 访问令牌。 */
  public Optional<String> oauth2Token() {
    return Optional.ofNullable(gcsOAuth2Token);
  }

  /** 返回 OAuth2 令牌的过期时间。 */
  public Optional<Date> oauth2TokenExpiresAt() {
    return Optional.ofNullable(gcsOAuth2TokenExpiresAt);
  }

  /** 返回批量删除文件时的批次大小。 */
  public int deleteBatchSize() {
    return gcsDeleteBatchSize;
  }
}
