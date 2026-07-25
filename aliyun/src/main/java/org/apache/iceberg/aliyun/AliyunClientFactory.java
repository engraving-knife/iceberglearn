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
package org.apache.iceberg.aliyun;

import com.aliyun.oss.OSS;
import java.io.Serializable;
import java.util.Map;

/**
 * 文件级说明：阿里云 OSS 客户端工厂的 SPI 接口。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本接口是 iceberg-aliyun 中 对外暴露的扩展点，位于 {@code OSSFileIO} 之下、阿里云
 * OSS SDK 之上，承担“客户端创建” 这一职责的抽象。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义创建阿里云 OSS 客户端（{@link OSS}）的契约，供 {@link OSSFileIO} 调用。
 *   <li>定义从 catalog 属性初始化工厂自身的契约，使工厂可基于配置构造客户端。
 *   <li>暴露已解析的 {@link AliyunProperties}，便于上层复用配置。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>接口抽象：将“如何创建 OSS 客户端”这一可变点抽离为接口，使默认实现 （{@link
 *       AliyunClientFactories.DefaultAliyunClientFactory}）与用户自定义实现 （如基于 STS/RAM Role 的鉴权）可互换。
 *   <li>实现 Serializable：工厂需在分布式引擎（Spark/Flink）中随作业序列化分发， 故继承 {@link Serializable}。
 *   <li>两阶段初始化：先无参构造（便于反射加载），再通过 {@link #initialize(Map)} 注入属性，符合动态加载框架的惯例。
 * </ul>
 *
 * <p>上下游关系：被 {@link AliyunClientFactories#from(Map)} 反射加载、被 {@code OSSFileIO#initialize} 调用；下游依赖阿里云
 * OSS SDK 的 {@link OSS} 接口。
 */
public interface AliyunClientFactory extends Serializable {

  /**
   * 创建一个新的阿里云 OSS 客户端实例。
   *
   * <p>调用前应确保已通过 {@link #initialize(Map)} 完成初始化，否则具体实现可能抛出 NullPointerException 等异常。
   *
   * @return 已配置好的 OSS 客户端
   */
  OSS newOSSClient();

  /**
   * 用 catalog 属性初始化工厂。
   *
   * <p>实现应在此方法中解析 endpoint、accessKey 等配置，并保存在工厂实例上， 供后续 {@link #newOSSClient()} 使用。
   *
   * @param properties catalog / 引擎传入的属性集合
   */
  void initialize(Map<String, String> properties);

  /**
   * 返回工厂在 {@link #initialize(Map)} 阶段解析出的 {@link AliyunProperties}。
   *
   * @return 已解析的阿里云属性对象
   */
  AliyunProperties aliyunProperties();
}
