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

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import java.net.URI;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * GCS 文件抽象基类。
 *
 * <p>所属模块：iceberg-gcp（GCP 集成模块，处于 Iceberg 存储访问层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装 GCS 中一个对象（Blob）的通用属性：{@link Storage} 客户端、{@link BlobId}、 {@link GCPProperties} 配置、{@link
 *       MetricsContext} 指标上下文。
 *   <li>提供 location/URI 转换、对象存在性检查、Blob 元数据懒加载等公共能力。
 *   <li>作为 {@link GCSInputFile}（读）和 {@link GCSOutputFile}（写）的父类， 统一两者共享的字段与方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>抽象出公共部分以避免输入/输出文件类的代码重复。
 *   <li>Blob 元数据采用懒加载（首次调用 {@link #getBlob()} 时才请求 GCS），减少不必要的 远程调用；加载后缓存到 {@code metadata}
 *       字段，同一实例后续访问直接复用。
 * </ul>
 *
 * <p>上下游关系：被 {@link GCSInputFile} 和 {@link GCSOutputFile} 继承；由 {@link GCSFileIO} 创建实例。依赖 GCP
 * Storage SDK。
 */
abstract class BaseGCSFile {
  private final Storage storage;
  private final GCPProperties gcpProperties;
  private final BlobId blobId;
  private Blob metadata;
  private final MetricsContext metrics;

  /**
   * 构造 GCS 文件基类实例。
   *
   * @param storage GCS Storage 客户端
   * @param blobId 目标对象的 BlobId（bucket + object name）
   * @param gcpProperties GCP 配置属性
   * @param metrics 指标上下文，用于记录读写统计
   */
  BaseGCSFile(Storage storage, BlobId blobId, GCPProperties gcpProperties, MetricsContext metrics) {
    this.storage = storage;
    this.blobId = blobId;
    this.gcpProperties = gcpProperties;
    this.metrics = metrics;
  }

  /** 返回该文件的标准 GCS URI 形式（gs://bucket/object）。 */
  public String location() {
    return blobId.toGsUtilUri();
  }

  /** 返回 GCS Storage 客户端（包级可见，供子类使用）。 */
  Storage storage() {
    return storage;
  }

  /** 返回该文件的 {@link URI} 表示。 */
  URI uri() {
    return URI.create(blobId.toGsUtilUri());
  }

  /** 返回该文件的 {@link BlobId}。 */
  BlobId blobId() {
    return blobId;
  }

  /** 返回 GCP 配置属性（ protected，供子类访问）。 */
  protected GCPProperties gcpProperties() {
    return gcpProperties;
  }

  /** 返回指标上下文（protected，供子类访问）。 */
  protected MetricsContext metrics() {
    return metrics;
  }

  /**
   * 判断该 GCS 对象是否存在。
   *
   * <p>逻辑：通过 {@link #getBlob()} 获取元数据，若返回非 null 则存在。
   *
   * @return true 表示对象存在
   */
  public boolean exists() {
    return getBlob() != null;
  }

  /**
   * 获取该对象的 Blob 元数据（懒加载 + 缓存）。
   *
   * <p>逻辑：若 {@code metadata} 为 null，则调用 {@link Storage#get(BlobId)} 从 GCS 拉取
   * 元数据并缓存；后续调用直接返回缓存值，避免重复远程请求。
   *
   * @return Blob 元数据，若对象不存在则返回 null
   */
  protected Blob getBlob() {
    if (metadata == null) {
      metadata = storage.get(blobId);
    }

    return metadata;
  }

  @Override
  public String toString() {
    return blobId.toString();
  }
}
