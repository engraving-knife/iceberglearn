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
package org.apache.iceberg.dell;

import com.emc.object.s3.S3Client;
import java.io.Serializable;
import java.util.Map;

/**
 * Dell EMC ECS S3 客户端工厂接口。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块的存储适配层，为 Iceberg 提供 基于 ECS 的 FileIO 与 Catalog 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义创建 Dell EMC ECS S3 客户端（{@link S3Client}）的契约。
 *   <li>定义从 catalog 属性初始化工厂自身的契约。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>解耦客户端构造与 FileIO/Catalog 使用方：将 S3 客户端的连接、鉴权、HTTP 客户端 选型等细节从 Iceberg 主体逻辑中剥离，便于用户按需替换（如复用连接池、
 *       接入自定义鉴权链路）。
 *   <li>实现 {@link Serializable}：工厂实例需要随 FileIO/Catalog 序列化到引擎的 Executor 端执行（如 Spark task
 *       序列化），因此要求可序列化；真正不可序列化的 {@link S3Client} 通过 {@code SerializableSupplier} 在使用时懒构造。
 *   <li>两阶段初始化：先无参构造（便于反射加载），再 {@link #initialize(Map)} 注入配置， 兼容动态加载场景。
 * </ul>
 *
 * <p>上下游关系：由 {@code DellClientFactories} 反射加载并初始化；被 {@code EcsFileIO} 与 {@code EcsCatalog} 调用以获取 S3
 * 客户端。
 */
public interface DellClientFactory extends Serializable {

  /**
   * 创建一个 Dell EMC ECS S3 客户端。
   *
   * <p>调用方负责管理返回客户端的生命周期（如适时调用 {@code destroy}）。
   *
   * @return Dell EMC ECS S3 客户端
   */
  S3Client ecsS3();

  /**
   * 从 catalog 属性初始化本工厂，提取 ECS 连接所需配置。
   *
   * <p>在反射实例化之后调用，用于注入 endpoint、密钥等属性。
   *
   * @param properties catalog 配置属性
   */
  void initialize(Map<String, String> properties);
}
