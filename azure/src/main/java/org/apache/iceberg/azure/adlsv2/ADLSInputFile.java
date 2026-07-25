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
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * ADLS 文件输入实现（读取侧）。
 *
 * <p>所属模块：iceberg-azure（Azure 存储后端 FileIO 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link InputFile} 接口，为 Iceberg 提供从 ADLS 读取文件的能力。
 *   <li>提供文件长度查询（懒加载，首次调用时从服务端获取）。
 *   <li>创建 {@link ADLSInputStream} 用于实际的字节流读取。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseADLSFile} 复用 location/client/properties/metrics 的公共逻辑。
 *   <li>fileSize 可在构造时传入（已知长度的场景，如元数据中记录了大小），也可延迟到 {@link #getLength()} 首次调用时从服务端拉取，减少不必要的远程调用。
 *   <li>fileSize 仅在大于 0 时保留，避免传入 0 或负值导致后续逻辑异常。
 * </ul>
 *
 * <p>上下游关系：由 {@link ADLSFileIO#newInputFile} 创建，被 Iceberg core 的读取层 （如读取 data file / metadata
 * file）调用。
 */
class ADLSInputFile extends BaseADLSFile implements InputFile {
  private Long fileSize;

  /**
   * 构造一个未指定文件长度的 ADLSInputFile。
   *
   * <p>文件长度将在首次调用 {@link #getLength()} 时从 ADLS 服务端获取。
   *
   * @param location 文件完整 URI
   * @param fileClient 文件客户端
   * @param azureProperties Azure 配置
   * @param metrics 指标上下文
   */
  ADLSInputFile(
      String location,
      DataLakeFileClient fileClient,
      AzureProperties azureProperties,
      MetricsContext metrics) {
    this(location, null, fileClient, azureProperties, metrics);
  }

  /**
   * 构造一个指定文件长度的 ADLSInputFile。
   *
   * @param location 文件完整 URI
   * @param fileSize 已知文件长度；为 null 或非正数时将被忽略（延迟获取）
   * @param fileClient 文件客户端
   * @param azureProperties Azure 配置
   * @param metrics 指标上下文
   */
  ADLSInputFile(
      String location,
      Long fileSize,
      DataLakeFileClient fileClient,
      AzureProperties azureProperties,
      MetricsContext metrics) {
    super(location, fileClient, azureProperties, metrics);
    this.fileSize = fileSize != null && fileSize > 0 ? fileSize : null;
  }

  /**
   * 返回文件长度（字节）。
   *
   * <p>若构造时未传入有效长度，则首次调用时通过 {@link DataLakeFileClient#getProperties()} 从服务端获取并缓存。
   *
   * @return 文件长度
   */
  @Override
  public long getLength() {
    if (fileSize == null) {
      this.fileSize = fileClient().getProperties().getFileSize();
    }
    return fileSize;
  }

  /**
   * 创建一个新的可寻址输入流用于读取该文件。
   *
   * @return 基于 ADLS 的 {@link SeekableInputStream}
   */
  @Override
  public SeekableInputStream newStream() {
    return new ADLSInputStream(fileClient(), fileSize, azureProperties(), metrics());
  }
}
