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
import org.apache.iceberg.aliyun.AliyunProperties;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * 文件级说明：基于阿里云 OSS 的 {@link InputFile} 实现。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本类继承 {@link BaseOSSFile} 并实现 iceberg-api 的 {@link
 * InputFile} 接口，负责从 OSS 读取文件数据。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 OSS 对象的长度查询（{@link #getLength()}）。
 *   <li>创建可随机定位的读取流 {@link OSSInputStream}。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>长度懒求值：{@code length} 初始为 null，首次访问时才从 OSS 元数据拉取； 若构造时已传入长度（已知文件大小场景）则直接使用，避免额外网络请求。
 *   <li>复用基类能力：exists / 元数据缓存等公共行为由 {@link BaseOSSFile} 提供。
 * </ul>
 *
 * <p>上下游关系：被 {@link OSSFileIO#newInputFile} 创建；下游创建 {@link OSSInputStream} 执行实际读取。
 */
class OSSInputFile extends BaseOSSFile implements InputFile {

  private Long length = null;

  /**
   * 构造 OSS 输入文件，长度在首次访问时从 OSS 元数据获取。
   *
   * @param client OSS 客户端
   * @param uri 文件对应的 OSS URI
   * @param aliyunProperties 阿里云配置属性
   * @param metrics 度量上下文
   */
  OSSInputFile(OSS client, OSSURI uri, AliyunProperties aliyunProperties, MetricsContext metrics) {
    super(client, uri, aliyunProperties, metrics);
  }

  /**
   * 构造 OSS 输入文件并指定已知长度。
   *
   * <p>适用于上层已知文件大小（如 manifest 中记录的长度）的场景，避免额外元数据请求。
   *
   * @param client OSS 客户端
   * @param uri 文件对应的 OSS URI
   * @param aliyunProperties 阿里云配置属性
   * @param length 已知文件长度（必须 &gt;= 0）
   * @param metrics 度量上下文
   * @throws ValidationException 若 length 为负数
   */
  OSSInputFile(
      OSS client,
      OSSURI uri,
      AliyunProperties aliyunProperties,
      long length,
      MetricsContext metrics) {
    super(client, uri, aliyunProperties, metrics);
    ValidationException.check(length >= 0, "Invalid file length: %s", length);
    this.length = length;
  }

  /**
   * 返回文件长度（字节）。
   *
   * <p>逻辑：若长度未知（null），则从 OSS 元数据拉取并缓存；否则直接返回已知长度。
   *
   * @return 文件长度
   */
  @Override
  public long getLength() {
    if (length == null) {
      length = objectMetadata().getSize();
    }
    return length;
  }

  /**
   * 创建新的读取流。
   *
   * @return 包装为 {@link SeekableInputStream} 的 {@link OSSInputStream}
   */
  @Override
  public SeekableInputStream newStream() {
    return new OSSInputStream(client(), uri(), metrics());
  }
}
