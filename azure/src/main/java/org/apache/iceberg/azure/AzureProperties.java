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
package org.apache.iceberg.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.file.datalake.DataLakeFileSystemClientBuilder;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.iceberg.util.PropertyUtil;

/**
 * Azure 存储配置属性集合。
 *
 * <p>所属模块：iceberg-azure（Azure 存储后端 FileIO 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中管理 ADLS 相关配置：按 storage account 维度的 SAS token、连接字符串， 以及全局的读/写块大小。
 *   <li>提供 {@link #applyClientConfiguration} 方法，将认证与端点配置应用到 {@link
 *       DataLakeFileSystemClientBuilder}，统一客户端构建逻辑。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable}：因为 {@link org.apache.iceberg.io.FileIO} 实例
 *       需要在分布式引擎（Spark/Flink）中序列化分发到各节点，配置必须可序列化。
 *   <li>SAS token 与连接字符串按 account 维度存储（Map），支持一个作业同时访问 多个 storage account 的场景。
 *   <li>认证优先级：显式 SAS token > DefaultAzureCredential（托管身份/环境变量等）； 端点优先级：显式连接字符串 > 默认
 *       https://{account}。连接字符串最后应用， 使其参数可覆盖 SAS token 配置。
 * </ul>
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.azure.adlsv2.ADLSFileIO} 在 initialize 时构造，被所有 ADLS
 * 文件类（{@link org.apache.iceberg.azure.adlsv2.BaseADLSFile} 及其子类）共享使用。
 */
public class AzureProperties implements Serializable {
  /** SAS token 配置键前缀，后接 storage account 名，如 {@code adls.sas-token.myaccount}。 */
  public static final String ADLS_SAS_TOKEN_PREFIX = "adls.sas-token.";

  /** 连接字符串配置键前缀，后接 storage account 名，如 {@code adls.connection-string.myaccount}。 */
  public static final String ADLS_CONNECTION_STRING_PREFIX = "adls.connection-string.";

  /** ADLS 读取块大小（字节）配置键。 */
  public static final String ADLS_READ_BLOCK_SIZE = "adls.read.block-size-bytes";

  /** ADLS 写入块大小（字节）配置键。 */
  public static final String ADLS_WRITE_BLOCK_SIZE = "adls.write.block-size-bytes";

  private Map<String, String> adlsSasTokens = Collections.emptyMap();
  private Map<String, String> adlsConnectionStrings = Collections.emptyMap();
  private Integer adlsReadBlockSize;
  private Long adlsWriteBlockSize;

  /** 无参构造，创建一个所有配置为空的实例（用于动态加载场景）。 */
  public AzureProperties() {}

  /**
   * 根据属性 Map 构造 AzureProperties。
   *
   * <p>逻辑：使用 {@link PropertyUtil#propertiesWithPrefix} 提取以 {@code adls.sas-token.} 和 {@code
   * adls.connection-string.} 为前缀的子 Map （key 为去掉前缀后的 account 名）；若配置中包含读/写块大小则解析为对应数值。
   *
   * @param properties Iceberg 配置属性
   */
  public AzureProperties(Map<String, String> properties) {
    this.adlsSasTokens = PropertyUtil.propertiesWithPrefix(properties, ADLS_SAS_TOKEN_PREFIX);
    this.adlsConnectionStrings =
        PropertyUtil.propertiesWithPrefix(properties, ADLS_CONNECTION_STRING_PREFIX);

    if (properties.containsKey(ADLS_READ_BLOCK_SIZE)) {
      this.adlsReadBlockSize = Integer.parseInt(properties.get(ADLS_READ_BLOCK_SIZE));
    }
    if (properties.containsKey(ADLS_WRITE_BLOCK_SIZE)) {
      this.adlsWriteBlockSize = Long.parseLong(properties.get(ADLS_WRITE_BLOCK_SIZE));
    }
  }

  /**
   * 返回 ADLS 读取块大小。
   *
   * @return 读取块大小；未配置时返回 {@link Optional#empty()}
   */
  public Optional<Integer> adlsReadBlockSize() {
    return Optional.ofNullable(adlsReadBlockSize);
  }

  /**
   * 返回 ADLS 写入块大小。
   *
   * @return 写入块大小；未配置时返回 {@link Optional#empty()}
   */
  public Optional<Long> adlsWriteBlockSize() {
    return Optional.ofNullable(adlsWriteBlockSize);
  }

  /**
   * 将认证与端点配置应用到 {@link DataLakeFileSystemClientBuilder}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>查找该 account 对应的 SAS token：若存在且非空，则用 SAS token 认证； 否则使用 {@link
   *       DefaultAzureCredentialBuilder} 构建 DefaultAzureCredential （覆盖托管身份、环境变量、CLI 登录等多种凭据来源）。
   *   <li>查找该 account 对应的连接字符串：若存在且非空，则将其作为 endpoint（连接字符串 最后应用，使其参数可覆盖前述 SAS token 配置）；否则使用默认
   *       endpoint {@code https://{account}}。
   * </ol>
   *
   * @param account Azure storage account 名
   * @param builder 待配置的客户端构建器
   */
  public void applyClientConfiguration(String account, DataLakeFileSystemClientBuilder builder) {
    String sasToken = adlsSasTokens.get(account);
    if (sasToken != null && !sasToken.isEmpty()) {
      builder.sasToken(sasToken);
    } else {
      builder.credential(new DefaultAzureCredentialBuilder().build());
    }

    // apply connection string last so its parameters take precedence, e.g. SAS token
    String connectionString = adlsConnectionStrings.get(account);
    if (connectionString != null && !connectionString.isEmpty()) {
      builder.endpoint(connectionString);
    } else {
      builder.endpoint("https://" + account);
    }
  }
}
