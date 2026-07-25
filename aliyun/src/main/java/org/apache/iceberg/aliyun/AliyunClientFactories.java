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
import com.aliyun.oss.OSSClientBuilder;
import java.util.Map;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 文件级说明：阿里云 OSS 客户端工厂的入口与默认实现集合。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块，位于 iceberg-api 之下、由 OSSFileIO 等具体实现调用，提供对阿里云 OSS 的客户端创建能力）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供默认的 {@link AliyunClientFactory} 实现，使用 accessKey + endpoint 直连 OSS。
 *   <li>支持通过配置项 {@link AliyunProperties#CLIENT_FACTORY} 指定自定义工厂实现类，
 *       并以反射方式加载、初始化该工厂，便于业务方接入自定义鉴权、代理、STS 等场景。
 *   <li>对外屏蔽 OSS SDK 构造细节，统一从 catalog/引擎属性出发生成客户端。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>工厂可插拔：通过反射加载用户自定义类，使 OSS 接入方式不绑定到具体鉴权链路， 兼容 RAM Role、STS Token、自定义 endpoint 等场景。
 *   <li>单实例缓存：默认工厂以静态单例提供，避免重复构造造成的资源浪费。
 *   <li>错误信息友好：反射加载失败时包装为 IllegalArgumentException，并给出可读提示。
 * </ul>
 *
 * <p>上下游关系：被 {@code OSSFileIO#initialize} 调用以获得 OSS 客户端；上游为 Iceberg 引擎层 传入的 catalog 属性 map；下游依赖阿里云
 * OSS SDK 的 {@code OSSClientBuilder}。
 */
public class AliyunClientFactories {

  private static final AliyunClientFactory ALIYUN_CLIENT_FACTORY_DEFAULT =
      new DefaultAliyunClientFactory();

  private AliyunClientFactories() {}

  /**
   * 返回默认的 {@link AliyunClientFactory} 单例。
   *
   * <p>该工厂使用静态构造的默认实现，未读取任何配置；适用于不需要自定义鉴权的场景。
   *
   * @return 默认 OSS 客户端工厂实例
   */
  public static AliyunClientFactory defaultFactory() {
    return ALIYUN_CLIENT_FACTORY_DEFAULT;
  }

  /**
   * 根据属性 map 解析并构造 {@link AliyunClientFactory}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从属性中读取 {@link AliyunProperties#CLIENT_FACTORY} 指定的实现类名， 未设置时默认使用 {@link
   *       DefaultAliyunClientFactory}。
   *   <li>委托 {@link #loadClientFactory(String, Map)} 反射加载并初始化工厂实例。
   * </ol>
   *
   * @param properties catalog / 引擎传入的属性集合
   * @return 已初始化的 {@link AliyunClientFactory}
   */
  public static AliyunClientFactory from(Map<String, String> properties) {
    String factoryImpl =
        PropertyUtil.propertyAsString(
            properties,
            AliyunProperties.CLIENT_FACTORY,
            DefaultAliyunClientFactory.class.getName());
    return loadClientFactory(factoryImpl, properties);
  }

  /**
   * 通过反射加载并初始化指定类名的 {@link AliyunClientFactory} 实现。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>使用 {@link DynConstructors} 查找目标类的无参构造器（含非公开构造器）。
   *   <li>实例化工厂对象；若类型与 {@link AliyunClientFactory} 不兼容则抛出 IllegalArgumentException。
   *   <li>调用 {@link AliyunClientFactory#initialize(Map)} 完成属性注入。
   * </ol>
   *
   * @param impl 工厂实现类全限定名
   * @param properties 用于初始化工厂的属性集合
   * @return 已初始化的 {@link AliyunClientFactory}
   * @throws IllegalArgumentException 若类无无参构造器，或类型不兼容
   */
  private static AliyunClientFactory loadClientFactory(
      String impl, Map<String, String> properties) {
    DynConstructors.Ctor<AliyunClientFactory> ctor;
    try {
      ctor = DynConstructors.builder(AliyunClientFactory.class).hiddenImpl(impl).buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize AliyunClientFactory, missing no-arg constructor: %s", impl),
          e);
    }

    AliyunClientFactory factory;
    try {
      factory = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize AliyunClientFactory, %s does not implement AliyunClientFactory.",
              impl),
          e);
    }

    factory.initialize(properties);
    return factory;
  }

  /**
   * 默认的 {@link AliyunClientFactory} 实现。
   *
   * <p>设计意图：直接基于 accessKeyId/accessKeySecret + endpoint 创建 OSS 客户端， 适用于无需自定义鉴权的简单场景。所有字段在 {@link
   * #initialize(Map)} 时一次性解析， 之后 {@link #newOSSClient()} 每次都新建一个客户端实例。
   */
  static class DefaultAliyunClientFactory implements AliyunClientFactory {
    private AliyunProperties aliyunProperties;

    DefaultAliyunClientFactory() {}

    /**
     * 创建新的阿里云 OSS 客户端。
     *
     * <p>逻辑：使用 {@link OSSClientBuilder} 基于 endpoint、accessKeyId、accessKeySecret 构建客户端实例。要求工厂已通过
     * {@link #initialize(Map)} 完成初始化。
     *
     * @return 新的 OSS 客户端实例
     * @throws NullPointerException 若工厂尚未初始化
     */
    @Override
    public OSS newOSSClient() {
      Preconditions.checkNotNull(
          aliyunProperties,
          "Cannot create aliyun oss client before initializing the AliyunClientFactory.");

      return new OSSClientBuilder()
          .build(
              aliyunProperties.ossEndpoint(),
              aliyunProperties.accessKeyId(),
              aliyunProperties.accessKeySecret());
    }

    /**
     * 用 catalog 属性初始化工厂，解析出 {@link AliyunProperties}。
     *
     * @param properties catalog / 引擎传入的属性集合
     */
    @Override
    public void initialize(Map<String, String> properties) {
      this.aliyunProperties = new AliyunProperties(properties);
    }

    /**
     * 返回当前工厂持有的 {@link AliyunProperties}。
     *
     * @return 已解析的阿里云属性对象
     */
    @Override
    public AliyunProperties aliyunProperties() {
      return aliyunProperties;
    }
  }
}
