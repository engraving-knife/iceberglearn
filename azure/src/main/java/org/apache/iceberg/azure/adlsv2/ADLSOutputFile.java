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
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.iceberg.azure.AzureProperties;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * ADLS 文件输出实现（写入侧）。
 *
 * <p>所属模块：iceberg-azure（Azure 存储后端 FileIO 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link OutputFile} 接口，为 Iceberg 提供向 ADLS 写入文件的能力。
 *   <li>提供 {@code create}（文件不存在时创建）和 {@code createOrOverwrite}（覆盖写入） 两种语义。
 *   <li>支持将输出文件转换为输入文件，便于写入后立即读取。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseADLSFile} 复用公共属性。
 *   <li>{@code create} 先检查存在性再写入，保证 Iceberg 的"写不覆盖"语义， 避免意外覆盖已有数据文件。已存在时抛 {@link
 *       AlreadyExistsException}。
 *   <li>{@code createOrOverwrite} 直接创建输出流，由 ADLS 服务端处理覆盖语义。
 * </ul>
 *
 * <p>上下游关系：由 {@link ADLSFileIO#newOutputFile} 创建，被 Iceberg core 的写入层 （如写 data file / manifest /
 * metadata）调用。
 */
class ADLSOutputFile extends BaseADLSFile implements OutputFile {

  /**
   * 构造 ADLS 输出文件。
   *
   * @param location 文件完整 URI
   * @param fileClient 文件客户端
   * @param azureProperties Azure 配置
   * @param metrics 指标上下文
   */
  ADLSOutputFile(
      String location,
      DataLakeFileClient fileClient,
      AzureProperties azureProperties,
      MetricsContext metrics) {
    super(location, fileClient, azureProperties, metrics);
  }

  /**
   * 当目标文件不存在时创建输出流。
   *
   * <p>若文件已存在则抛出 {@link AlreadyExistsException}，保证不会意外覆盖已有文件。
   *
   * @return 输出流
   * @throws AlreadyExistsException 文件已存在
   */
  @Override
  public PositionOutputStream create() {
    if (!exists()) {
      return createOrOverwrite();
    } else {
      throw new AlreadyExistsException("Location already exists: %s", location());
    }
  }

  /**
   * 创建输出流，无论目标文件是否存在都会覆盖。
   *
   * <p>底层创建 {@link ADLSOutputStream}，IO 异常包装为 {@link UncheckedIOException} 抛出。
   *
   * @return 输出流
   */
  @Override
  public PositionOutputStream createOrOverwrite() {
    try {
      return new ADLSOutputStream(fileClient(), azureProperties(), metrics());
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to create output stream for location: " + location(), e);
    }
  }

  /**
   * 将此输出文件转换为输入文件，复用同一 location 和客户端。
   *
   * <p>典型场景：写入完成后立即读取（如写入 metadata 后读取校验）。
   *
   * @return 对应的 {@link ADLSInputFile}
   */
  @Override
  public InputFile toInputFile() {
    return new ADLSInputFile(location(), fileClient(), azureProperties(), metrics());
  }
}
