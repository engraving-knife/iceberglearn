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
package org.apache.iceberg.azure.adlsv2;

import com.azure.storage.file.datalake.DataLakeFileClient;
import org.apache.iceberg.azure.AzureProperties;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * ADLS（Azure Data Lake Storage Gen2）文件抽象基类。
 *
 * <p>所属模块：iceberg-azure（Azure 存储后端 FileIO 实现，位于 iceberg-core 之下， 实现 {@link
 * org.apache.iceberg.io.FileIO} 接口对接 Azure ADLS Gen2）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装 ADLS 文件通用属性：location（完整 URI）、{@link DataLakeFileClient} （文件级客户端）、{@link
 *       AzureProperties}（Azure 配置）、{@link MetricsContext} （指标上下文）。
 *   <li>向子类 {@link ADLSInputFile} / {@link ADLSOutputFile} 提供统一的访问器与 存在性判断能力，避免重复代码。
 * </ul>
 *
 * <p>设计意图：将"文件元数据 + 客户端句柄"这类与读写方向无关的公共部分提取到抽象基类， 输入/输出文件子类只关注各自 IO 语义。客户端句柄在构造时一次性注入并被 final 字段持有，
 * 保证线程安全与不可变性。
 *
 * <p>上下游关系：由 {@link ADLSFileIO} 在创建输入/输出文件时构造，子类被 Iceberg core 的 IO 层调用进行实际读写。
 */
abstract class BaseADLSFile {
  private final String location;
  private final DataLakeFileClient fileClient;
  private final AzureProperties azureProperties;
  private final MetricsContext metrics;

  /**
   * 构造一个 ADLS 文件抽象基类实例。
   *
   * <p>对所有传入参数进行非空校验，保证后续子类调用时这些依赖始终可用。
   *
   * @param location 文件在 ADLS 上的完整 URI
   * @param fileClient 已绑定到具体文件的 {@link DataLakeFileClient}，负责实际 IO 调用
   * @param azureProperties Azure 配置（SAS token、连接字符串、块大小等）
   * @param metrics 指标上下文，用于记录读写字节/操作数
   */
  BaseADLSFile(
      String location,
      DataLakeFileClient fileClient,
      AzureProperties azureProperties,
      MetricsContext metrics) {
    Preconditions.checkArgument(location != null, "Cannot initialize ADLS file with null location");
    Preconditions.checkArgument(
        fileClient != null, "Cannot initialize ADLS file with null file client");
    Preconditions.checkArgument(
        azureProperties != null, "Cannot initialize ADLS file with null properties");
    Preconditions.checkArgument(metrics != null, "Cannot initialize ADLS file with null metrics");
    this.location = location;
    this.fileClient = fileClient;
    this.azureProperties = azureProperties;
    this.metrics = metrics;
  }

  /** 返回当前文件使用的 Azure 配置。 */
  protected AzureProperties azureProperties() {
    return azureProperties;
  }

  /** 返回当前文件使用的指标上下文。 */
  protected MetricsContext metrics() {
    return metrics;
  }

  /** 返回与该文件绑定的 {@link DataLakeFileClient}，子类通过它执行实际存储操作。 */
  protected DataLakeFileClient fileClient() {
    return fileClient;
  }

  /** 返回文件在 ADLS 上的完整 URI。 */
  public String location() {
    return location;
  }

  /**
   * 判断该文件在 ADLS 上是否已存在。
   *
   * @return 存在返回 true，否则 false
   */
  public boolean exists() {
    return fileClient().exists();
  }

  /** 返回文件 location 字符串，便于日志输出。 */
  @Override
  public String toString() {
    return location;
  }
}
