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
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * GCS 输入文件实现（读取用）。
 *
 * <p>所属模块：iceberg-gcp（GCP 集成模块，处于 Iceberg 存储访问层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link InputFile} 接口，代表一个可从 GCS 读取的文件。
 *   <li>提供文件长度查询（懒加载）和可定位输入流创建能力。
 *   <li>支持在已知文件长度时跳过远程 metadata 查询以减少延迟。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>文件长度（blobSize）可为 null，首次调用 {@link #getLength()} 时才通过 {@link BaseGCSFile#getBlob()} 拉取 GCS
 *       metadata 获取，避免不必要的远程调用。
 *   <li>提供两个工厂方法 {@link #fromLocation} 简化构造：一个不传长度（需远程查询）， 一个传入长度（调用方已知时避免查询）。
 * </ul>
 *
 * <p>上下游关系：由 {@link GCSFileIO#newInputFile(String)} 创建；被 Iceberg 读路径 （如读取 manifest、数据文件）使用。创建的
 * {@link GCSInputStream} 负责 actual 读取。
 */
class GCSInputFile extends BaseGCSFile implements InputFile {
  private Long blobSize;

  /**
   * 工厂方法：从 location 字符串构造 GCSInputFile（文件长度未知，需远程查询）。
   *
   * @param location GCS 路径，形如 gs://bucket/object
   * @param storage GCS Storage 客户端
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文
   * @return GCSInputFile 实例
   */
  static GCSInputFile fromLocation(
      String location, Storage storage, GCPProperties gcpProperties, MetricsContext metrics) {
    return new GCSInputFile(storage, BlobId.fromGsUtilUri(location), null, gcpProperties, metrics);
  }

  /**
   * 工厂方法：从 location 字符串构造 GCSInputFile，并传入已知文件长度。
   *
   * @param location GCS 路径
   * @param length 文件长度（字节），若 <= 0 则视为未知
   * @param storage GCS Storage 客户端
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文
   * @return GCSInputFile 实例
   */
  static GCSInputFile fromLocation(
      String location,
      long length,
      Storage storage,
      GCPProperties gcpProperties,
      MetricsContext metrics) {
    return new GCSInputFile(
        storage,
        BlobId.fromGsUtilUri(location),
        length > 0 ? length : null,
        gcpProperties,
        metrics);
  }

  /**
   * 构造 GCSInputFile。
   *
   * @param storage GCS Storage 客户端
   * @param blobId 目标对象 BlobId
   * @param blobSize 已知文件长度，null 表示未知
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文
   */
  GCSInputFile(
      Storage storage,
      BlobId blobId,
      Long blobSize,
      GCPProperties gcpProperties,
      MetricsContext metrics) {
    super(storage, blobId, gcpProperties, metrics);
    this.blobSize = blobSize;
  }

  /**
   * 返回文件长度（字节）。
   *
   * <p>逻辑：若 blobSize 为 null（调用方未提供），则通过 {@link BaseGCSFile#getBlob()} 从 GCS 获取元数据并读取其 size
   * 字段，缓存后返回。
   *
   * @return 文件长度（字节）
   */
  @Override
  public long getLength() {
    if (blobSize == null) {
      this.blobSize = getBlob().getSize();
    }

    return blobSize;
  }

  /**
   * 创建一个新的可定位输入流。
   *
   * @return {@link GCSInputStream} 实例
   */
  @Override
  public SeekableInputStream newStream() {
    return new GCSInputStream(storage(), blobId(), blobSize, gcpProperties(), metrics());
  }
}
