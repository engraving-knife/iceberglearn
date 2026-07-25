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
package org.apache.iceberg.dell.ecs;

import com.emc.object.s3.S3Client;
import org.apache.iceberg.dell.DellProperties;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * 基于 ECS S3 的 {@link InputFile} 实现，提供只读文件的长度查询与流式读取能力。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过缓存的对象元数据返回文件内容长度。
 *   <li>创建 {@link EcsSeekableInputStream} 以支持按位置读取对象内容。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseEcsFile} 复用元数据缓存，使 {@link #getLength()} 走 HEAD 请求并缓存， 避免每次读长度都发请求。
 *   <li>工厂方法重载：提供带/不带 {@link DellProperties}、{@link MetricsContext} 的多个 {@code fromLocation}
 *       重载，兼顾简洁调用与完整配置场景。
 * </ul>
 *
 * <p>上下游关系：由 {@link EcsFileIO#newInputFile(String)} 创建；内部使用 {@link EcsSeekableInputStream} 提供读取流；被
 * Iceberg 读取侧（如扫描、元数据解析）调用。
 */
class EcsInputFile extends BaseEcsFile implements InputFile {

  /**
   * 由 location 创建输入文件，使用空配置与空指标上下文。
   *
   * @param location ECS 对象 location
   * @param client S3 客户端
   * @return 输入文件实例
   */
  public static EcsInputFile fromLocation(String location, S3Client client) {
    return new EcsInputFile(
        client, new EcsURI(location), new DellProperties(), MetricsContext.nullMetrics());
  }

  /**
   * 由 location 创建输入文件，使用指定 Dell 配置与空指标上下文。
   *
   * @param location ECS 对象 location
   * @param client S3 客户端
   * @param dellProperties Dell 连接配置
   * @return 输入文件实例
   */
  public static EcsInputFile fromLocation(
      String location, S3Client client, DellProperties dellProperties) {
    return new EcsInputFile(
        client, new EcsURI(location), dellProperties, MetricsContext.nullMetrics());
  }

  /**
   * 由 location 创建输入文件，使用指定 Dell 配置与指标上下文（包级可见）。
   *
   * @param location ECS 对象 location
   * @param client S3 客户端
   * @param dellProperties Dell 连接配置
   * @param metrics 指标上下文
   * @return 输入文件实例
   */
  static EcsInputFile fromLocation(
      String location, S3Client client, DellProperties dellProperties, MetricsContext metrics) {
    return new EcsInputFile(client, new EcsURI(location), dellProperties, metrics);
  }

  EcsInputFile(S3Client client, EcsURI uri, DellProperties dellProperties, MetricsContext metrics) {
    super(client, uri, dellProperties, metrics);
  }

  /**
   * 返回文件内容长度。
   *
   * <p>逻辑：从缓存的 {@link com.emc.object.s3.S3ObjectMetadata} 中取 contentLength。
   *
   * <p>注意：元数据被缓存，若文件被外部删除可能返回过时长度。
   *
   * @return 文件字节数
   */
  @Override
  public long getLength() {
    return getObjectMetadata().getContentLength();
  }

  /**
   * 创建一个新的可定位读取流。
   *
   * <p>每次调用返回独立的 {@link EcsSeekableInputStream}，流内部不缓存字节， 仅维护读取位置，按需发起 range 请求。
   *
   * @return 可定位输入流
   */
  @Override
  public SeekableInputStream newStream() {
    return new EcsSeekableInputStream(client(), uri(), metrics());
  }
}
