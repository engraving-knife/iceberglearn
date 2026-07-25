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
package org.apache.iceberg.gcp.gcs;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * GCS 输出文件实现（写入用）。
 *
 * <p>所属模块：iceberg-gcp（GCP 集成模块，处于 Iceberg 存储访问层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link OutputFile} 接口，代表一个可写入 GCS 的文件。
 *   <li>提供 create（仅当对象不存在时创建）和 createOrOverwrite（强制覆盖）两种写入模式。
 *   <li>支持将输出文件转换为输入文件（toInputFile），便于写入后立即读取。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>create 方法先检查对象是否存在，存在则抛 {@link AlreadyExistsException}， 保证 Iceberg 写入语义的幂等性（避免覆盖已有数据文件）。
 *   <li>createOrOverwrite 直接创建新流，GCS 的写入本身就是覆盖语义。
 * </ul>
 *
 * <p>上下游关系：由 {@link GCSFileIO#newOutputFile(String)} 创建；被 Iceberg 写路径 （如写入 manifest、数据文件）使用。创建的
 * {@link GCSOutputStream} 负责实际写入。
 */
class GCSOutputFile extends BaseGCSFile implements OutputFile {

  /**
   * 工厂方法：从 location 字符串构造 GCSOutputFile。
   *
   * @param location GCS 路径，形如 gs://bucket/object
   * @param storage GCS Storage 客户端
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文
   * @return GCSOutputFile 实例
   */
  static GCSOutputFile fromLocation(
      String location, Storage storage, GCPProperties gcpProperties, MetricsContext metrics) {
    return new GCSOutputFile(storage, BlobId.fromGsUtilUri(location), gcpProperties, metrics);
  }

  /**
   * 构造 GCSOutputFile。
   *
   * @param storage GCS Storage 客户端
   * @param blobId 目标对象 BlobId
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文
   */
  GCSOutputFile(
      Storage storage, BlobId blobId, GCPProperties gcpProperties, MetricsContext metrics) {
    super(storage, blobId, gcpProperties, metrics);
  }

  /**
   * 创建输出流：仅当目标 GCS 对象不存在时创建。
   *
   * <p>逻辑：先通过 {@link #exists()} 检查对象是否存在；不存在则委托 {@link #createOrOverwrite()} 创建；已存在则抛出 {@link
   * AlreadyExistsException}。
   *
   * @return 输出流
   * @throws AlreadyExistsException 若目标位置已存在
   */
  @Override
  public PositionOutputStream create() {
    if (!exists()) {
      return createOrOverwrite();
    } else {
      throw new AlreadyExistsException("Location already exists: %s", uri());
    }
  }

  /**
   * 创建输出流（覆盖模式）：无论目标对象是否存在都创建新流。
   *
   * <p>逻辑：构造 {@link GCSOutputStream}，IOException 包装为 {@link UncheckedIOException} 抛出。
   *
   * @return 输出流
   */
  @Override
  public PositionOutputStream createOrOverwrite() {
    try {
      return new GCSOutputStream(storage(), blobId(), gcpProperties(), metrics());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create output stream for location: " + uri(), e);
    }
  }

  /**
   * 将此输出文件转换为输入文件（写入后读取场景）。
   *
   * @return 对应的 {@link GCSInputFile}，blobSize 传 null（需远程查询）
   */
  @Override
  public InputFile toInputFile() {
    return new GCSInputFile(storage(), blobId(), null, gcpProperties(), metrics());
  }
}
