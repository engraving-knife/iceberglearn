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
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * 文件级说明：基于阿里云 OSS 的 {@link OutputFile} 实现。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本类继承 {@link BaseOSSFile} 并实现 iceberg-api 的 {@link
 * OutputFile} 接口，负责向 OSS 写入文件数据。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link #create()}（仅在对象不存在时创建）与 {@link #createOrOverwrite()}（覆盖写） 两种写入语义。
 *   <li>创建底层 {@link OSSOutputStream} 执行实际写入。
 *   <li>支持将输出文件视图转为输入文件视图（{@link #toInputFile()}）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>创建前存在性检查：{@link #create()} 先判断对象是否已存在，存在则抛出 {@link AlreadyExistsException}，保证 Iceberg
 *       数据文件不被意外覆盖。
 *   <li>复用基类能力：exists / 元数据缓存等公共行为由 {@link BaseOSSFile} 提供。
 *   <li>视图转换：{@link #toInputFile()} 复用同一客户端与 URI，便于写完后立即读取校验。
 * </ul>
 *
 * <p>上下游关系：被 {@link OSSFileIO#newOutputFile} 创建；下游创建 {@link OSSOutputStream} 执行实际写入。
 */
class OSSOutputFile extends BaseOSSFile implements OutputFile {

  /**
   * 构造 OSS 输出文件。
   *
   * @param client OSS 客户端
   * @param uri 文件对应的 OSS URI
   * @param aliyunProperties 阿里云配置属性
   * @param metrics 度量上下文
   */
  OSSOutputFile(OSS client, OSSURI uri, AliyunProperties aliyunProperties, MetricsContext metrics) {
    super(client, uri, aliyunProperties, metrics);
  }

  /**
   * 根据 location 字符串快速构造 OSS 输出文件（使用空指标）。
   *
   * @param client OSS 客户端
   * @param location OSS 路径字符串
   * @param aliyunProperties 阿里云配置属性
   * @return 构造好的 {@link OSSOutputFile}
   */
  static OSSOutputFile fromLocation(
      OSS client, String location, AliyunProperties aliyunProperties) {
    return new OSSOutputFile(
        client, new OSSURI(location), aliyunProperties, MetricsContext.nullMetrics());
  }

  /**
   * 创建输出流；若对象已存在则抛出异常。
   *
   * <p>逻辑：先 {@link #exists()} 检查，不存在则委托 {@link #createOrOverwrite()} 创建； 已存在则抛出 {@link
   * AlreadyExistsException}。
   *
   * @return 新的 {@link PositionOutputStream}
   * @throws AlreadyExistsException 若目标 location 已存在
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
   * 创建输出流（覆盖模式，不检查存在性）。
   *
   * @return 新的 {@link OSSOutputStream}
   */
  @Override
  public PositionOutputStream createOrOverwrite() {
    return new OSSOutputStream(client(), uri(), aliyunProperties(), metrics());
  }

  /**
   * 将当前输出文件转为输入文件视图。
   *
   * <p>复用同一客户端、URI、属性与指标，便于写入完成后立即读取。
   *
   * @return 对应的 {@link InputFile}
   */
  @Override
  public InputFile toInputFile() {
    return new OSSInputFile(client(), uri(), aliyunProperties(), metrics());
  }
}
