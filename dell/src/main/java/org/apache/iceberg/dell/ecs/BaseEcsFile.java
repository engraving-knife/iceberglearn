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
import com.emc.object.s3.S3Exception;
import com.emc.object.s3.S3ObjectMetadata;
import org.apache.iceberg.dell.DellProperties;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * ECS 文件抽象基类，为 {@link EcsInputFile} 与 {@link EcsOutputFile} 提供公共状态与行为。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包负责基于 ECS S3 的 文件 IO 与 Catalog 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link S3Client}、{@link EcsURI}、{@link DellProperties}、{@link MetricsContext}
 *       等文件操作所需的共享上下文。
 *   <li>缓存对象元数据（{@link S3ObjectMetadata}），为存在性判断与文件长度查询提供 统一入口，避免重复 HEAD 请求。
 *   <li>提供 {@link #exists()} 等公共方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>元数据缓存：通过 {@link #getObjectMetadata()} 懒加载并缓存 S3 对象元数据， 多次调用 exists/length 时复用同一份 HEAD
 *       结果，减少网络往返。代价是缓存可能与 远端实际状态短暂不一致（如对象被外部删除）。
 *   <li>抽象基类复用：将输入/输出文件共享的字段与方法上提，避免重复代码。
 * </ul>
 *
 * <p>上下游关系：被 {@link EcsInputFile}、{@link EcsOutputFile} 继承； 依赖 {@link S3Client} 执行远端操作。
 */
abstract class BaseEcsFile {

  private final S3Client client;
  private final EcsURI uri;
  private final DellProperties dellProperties;
  private S3ObjectMetadata metadata;
  private final MetricsContext metrics;

  BaseEcsFile(S3Client client, EcsURI uri, DellProperties dellProperties, MetricsContext metrics) {
    this.client = client;
    this.uri = uri;
    this.dellProperties = dellProperties;
    this.metrics = metrics;
  }

  /** 返回该文件对应的 location 字符串。 */
  public String location() {
    return uri.location();
  }

  /** 返回绑定的 S3 客户端（包级可见，供子类使用）。 */
  S3Client client() {
    return client;
  }

  /** 返回文件对应的 {@link EcsURI}（包级可见，供子类使用）。 */
  EcsURI uri() {
    return uri;
  }

  /** 返回 Dell 连接配置。 */
  public DellProperties dellProperties() {
    return dellProperties;
  }

  /** 返回指标上下文，用于记录读写字节/操作计数。 */
  protected MetricsContext metrics() {
    return metrics;
  }

  /**
   * 判断对象在 ECS 中是否存在。
   *
   * <p>逻辑：尝试获取对象元数据，成功即存在；若抛出 HTTP 404 则视为不存在； 其他 {@link S3Exception} 向上抛出。
   *
   * <p>注意：由于元数据被缓存，结果可能因外部删除而过时。
   *
   * @return 存在返回 true，不存在返回 false
   */
  public boolean exists() {
    try {
      getObjectMetadata();
      return true;
    } catch (S3Exception e) {
      if (e.getHttpCode() == 404) {
        return false;
      } else {
        throw e;
      }
    }
  }

  /**
   * 获取（并按需缓存）S3 对象元数据。
   *
   * <p>逻辑：首次调用时通过 {@link S3Client#getObjectMetadata(String, String)} 发起 HEAD 请求并缓存结果，后续调用直接复用缓存。
   *
   * @return S3 对象元数据
   * @throws S3Exception 当远端返回非 404 错误时抛出
   */
  protected S3ObjectMetadata getObjectMetadata() throws S3Exception {
    if (metadata == null) {
      metadata = client().getObjectMetadata(uri.bucket(), uri.name());
    }

    return metadata;
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}
