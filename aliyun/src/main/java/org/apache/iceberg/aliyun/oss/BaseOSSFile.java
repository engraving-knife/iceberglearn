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
package org.apache.iceberg.aliyun.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSErrorCode;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.SimplifiedObjectMeta;
import org.apache.iceberg.aliyun.AliyunProperties;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * 文件级说明：OSS 文件抽象基类，为 {@link OSSInputFile} 与 {@link OSSOutputFile} 提供公共字段与行为。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本类位于 OSSFileIO 之下， 是 InputFile / OutputFile 实现的共享父类，封装 OSS
 * 客户端、URI、属性、metrics 等公共状态。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 OSS 客户端、{@link OSSURI}、{@link AliyunProperties}、{@link MetricsContext} 等公共字段。
 *   <li>提供 {@link #exists()} 判断对象是否存在（基于元数据查询）。
 *   <li>缓存对象元数据（{@link SimplifiedObjectMeta}），避免重复请求 OSS。
 *   <li>提供 location / client / uri / aliyunProperties / metrics 的只读访问。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法模式：将公共逻辑下沉到基类，子类只需关注输入/输出流的创建差异。
 *   <li>元数据懒加载与缓存：首次调用 {@link #objectMetadata()} 时才请求 OSS， 之后复用缓存结果，减少网络往返。
 *   <li>exists 容错：将 OSS SDK 的 NoSuchBucket/NoSuchKey 异常归一化为 false 返回， 其他异常继续向上抛出。
 * </ul>
 *
 * <p>上下游关系：被 {@link OSSInputFile}、{@link OSSOutputFile} 继承；上游为 {@link OSSFileIO} 创建实例；下游依赖阿里云 OSS
 * SDK 的元数据查询接口。
 */
abstract class BaseOSSFile {
  private final OSS client;
  private final OSSURI uri;
  private AliyunProperties aliyunProperties;
  private SimplifiedObjectMeta metadata;
  private final MetricsContext metrics;

  /**
   * 构造 OSS 文件基类实例。
   *
   * @param client OSS 客户端
   * @param uri 文件对应的 OSS URI
   * @param aliyunProperties 阿里云配置属性
   * @param metrics 度量上下文，用于统计 IO 指标
   */
  BaseOSSFile(OSS client, OSSURI uri, AliyunProperties aliyunProperties, MetricsContext metrics) {
    this.client = client;
    this.uri = uri;
    this.aliyunProperties = aliyunProperties;
    this.metrics = metrics;
  }

  /** 返回文件的原始 location 字符串。 */
  public String location() {
    return uri.location();
  }

  /** 返回当前使用的 OSS 客户端。 */
  public OSS client() {
    return client;
  }

  /** 返回文件对应的 {@link OSSURI}。 */
  public OSSURI uri() {
    return uri;
  }

  /** 返回当前文件关联的 {@link AliyunProperties}。 */
  public AliyunProperties aliyunProperties() {
    return aliyunProperties;
  }

  /**
   * 判断 OSS 对象是否存在。
   *
   * <p>逻辑：通过 {@link #objectMetadata()} 查询元数据，能获取到则存在；若 OSS SDK 抛出 NoSuchBucket 或 NoSuchKey
   * 异常则视为不存在返回 false；其他异常继续向上抛出。
   *
   * @return true 表示对象存在
   */
  public boolean exists() {
    try {
      return objectMetadata() != null;
    } catch (OSSException e) {

      if (e.getErrorCode().equals(OSSErrorCode.NO_SUCH_BUCKET)
          || e.getErrorCode().equals(OSSErrorCode.NO_SUCH_KEY)) {
        return false;
      }

      throw e;
    }
  }

  /**
   * 返回 OSS 对象的简化元数据，首次调用时远程拉取并缓存。
   *
   * <p>设计要点：使用 {@link OSS#getSimplifiedObjectMeta(String, String)} 查询，结果缓存在 {@code metadata}
   * 字段，后续调用直接复用，避免重复网络请求。
   *
   * @return 对象的简化元数据
   */
  protected SimplifiedObjectMeta objectMetadata() {
    if (metadata == null) {
      metadata = client.getSimplifiedObjectMeta(uri().bucket(), uri().key());
    }

    return metadata;
  }

  /** 返回当前文件关联的 {@link MetricsContext}。 */
  protected MetricsContext metrics() {
    return metrics;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("file", uri).toString();
  }
}
