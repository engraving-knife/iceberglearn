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
import com.emc.object.s3.S3Config;
import com.emc.object.s3.jersey.S3JerseyClient;
import java.net.URI;
import java.util.Map;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.util.PropertyUtil;

/**
 * Dell ECS S3 客户端工厂入口。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，为 Iceberg 提供基于 ECS 的 FileIO 与 Catalog
 * 实现，属于存储适配层，被引擎侧通过 Catalog/FileIO 接口调用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据 catalog 配置属性解析并加载 {@link DellClientFactory} 实现类。
 *   <li>提供默认实现 {@link DefaultDellClientFactory}，基于 Jersey 构造 ECS S3 客户端。
 *   <li>将反射加载过程中的受检异常统一包装为 {@link IllegalArgumentException}，简化调用方处理。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>可插拔工厂：通过 {@code client.factory} 属性允许用户自定义客户端构建逻辑 （例如复用连接池、注入鉴权链路等），未设置时回退到默认实现。
 *   <li>反射加载：使用 {@link DynConstructors} 调用无参构造实例化工厂，再通过 {@link DellClientFactory#initialize(Map)}
 *       注入属性，分离构造与配置两阶段， 兼容序列化场景下“先构造后初始化”的约定。
 * </ul>
 *
 * <p>上下游关系：被 {@code EcsCatalog} 和 {@code EcsFileIO} 在初始化时调用； 依赖 {@link DellProperties}（配置）与 {@code
 * common} 模块的 {@link DynConstructors}。
 */
public class DellClientFactories {

  private DellClientFactories() {}

  /**
   * 根据属性创建 {@link DellClientFactory} 实例。
   *
   * <p>逻辑：读取 {@code client.factory} 属性，未配置时使用 {@link DefaultDellClientFactory} 的类名作为默认值，再委托 {@link
   * #loadClientFactory(String, Map)} 完成反射加载与初始化。
   *
   * @param properties catalog 配置属性
   * @return 已初始化的 DellClientFactory 实例
   */
  public static DellClientFactory from(Map<String, String> properties) {
    String factoryImpl =
        PropertyUtil.propertyAsString(
            properties, DellProperties.CLIENT_FACTORY, DefaultDellClientFactory.class.getName());
    return loadClientFactory(factoryImpl, properties);
  }

  /**
   * 通过反射加载并初始化 {@link DellClientFactory} 实现类。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>用 {@link DynConstructors} 查找实现类的无参构造，缺失时抛出 {@link IllegalArgumentException}。
   *   <li>实例化工厂；若类型不匹配（未实现 {@link DellClientFactory}）抛出 {@link IllegalArgumentException}。
   *   <li>调用 {@link DellClientFactory#initialize(Map)} 注入配置并返回。
   * </ol>
   *
   * @param impl 实现类全限定名
   * @param properties catalog 配置属性
   * @return 已初始化的工厂实例
   * @throws IllegalArgumentException 当实现类缺少无参构造或类型不匹配时抛出
   */
  private static DellClientFactory loadClientFactory(String impl, Map<String, String> properties) {
    DynConstructors.Ctor<DellClientFactory> ctor;
    try {
      ctor = DynConstructors.builder(DellClientFactory.class).hiddenImpl(impl).buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize DellClientFactory, missing no-arg constructor: %s", impl),
          e);
    }

    DellClientFactory factory;
    try {
      factory = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize DellClientFactory, %s does not implement DellClientFactory.",
              impl),
          e);
    }

    factory.initialize(properties);
    return factory;
  }

  /**
   * 默认的 {@link DellClientFactory} 实现，使用 ECS Jersey S3 客户端。
   *
   * <p>设计要点：仅依据 {@link DellProperties} 中的 endpoint、AccessKey、SecretKey 三项 构造 {@link
   * S3JerseyClient}；若需更复杂的客户端配置，用户应实现自定义工厂替换之。
   */
  static class DefaultDellClientFactory implements DellClientFactory {
    private DellProperties dellProperties;

    DefaultDellClientFactory() {}

    /**
     * 创建并返回一个 ECS S3 客户端。
     *
     * <p>每次调用都会构造新的 {@link S3JerseyClient}，调用方需自行管理其生命周期。
     *
     * @return 已配置好鉴权与 endpoint 的 S3 客户端
     */
    @Override
    public S3Client ecsS3() {
      S3Config config = new S3Config(URI.create(dellProperties.ecsS3Endpoint()));

      config
          .withIdentity(dellProperties.ecsS3AccessKeyId())
          .withSecretKey(dellProperties.ecsS3SecretAccessKey());

      return new S3JerseyClient(config);
    }

    /**
     * 用 catalog 属性初始化工厂，提取 ECS 连接配置。
     *
     * @param properties catalog 配置属性
     */
    @Override
    public void initialize(Map<String, String> properties) {
      this.dellProperties = new DellProperties(properties);
    }
  }
}
