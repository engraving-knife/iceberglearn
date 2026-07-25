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
package org.apache.iceberg.aws;

import java.util.Map;
import org.apache.iceberg.aws.s3.S3FileIOAwsClientFactory;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 文件级说明：S3FileIO AWS 客户端工厂加载器。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：根据配置加载 S3FileIO 专用的 {@link S3FileIOAwsClientFactory} 实例， 未配置时回退到通用 {@link
 * AwsClientFactories#from(Map)}。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>双轨加载：S3FileIO 既支持专属工厂接口 {@link S3FileIOAwsClientFactory}（仅构造 S3）， 也兼容通用 {@link
 *       AwsClientFactory}（同时构造 S3/Glue/KMS/DynamoDB）。本类按配置 优先选用专属工厂，未配置时回退到通用工厂，保证向后兼容。
 *   <li>反射加载：通过 {@link DynConstructors} 调用工厂类的无参构造器， 再调用 initialize(properties) 注入配置，与 Iceberg
 *       catalog 加载模式一致。
 * </ul>
 *
 * <p>上下游关系：由 S3FileIO 初始化时调用；产出工厂实例提供 S3Client 给 S3InputFile/S3OutputFile。
 */
public class S3FileIOAwsClientFactories {

  private S3FileIOAwsClientFactories() {}

  /**
   * 加载 S3FileIO 客户端工厂：若配置了 {@link S3FileIOProperties#CLIENT_FACTORY} 则反射加载该类，否则回退到 {@link
   * AwsClientFactories#from(Map)} 初始化通用 AWS 客户端工厂。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>读取 s3.client-factory-impl 配置；
   *   <li>非空：反射加载并调用 initialize；
   *   <li>为空：委托 {@link AwsClientFactories#from(Map)}。
   * </ol>
   *
   * @param properties catalog properties
   * @return an instance of a factory class
   */
  @SuppressWarnings("unchecked")
  public static <T> T initialize(Map<String, String> properties) {
    String factoryImpl =
        PropertyUtil.propertyAsString(properties, S3FileIOProperties.CLIENT_FACTORY, null);
    if (Strings.isNullOrEmpty(factoryImpl)) {
      return (T) AwsClientFactories.from(properties);
    }
    return (T) loadClientFactory(factoryImpl, properties);
  }

  /**
   * 反射加载 S3FileIOAwsClientFactory 实现类：调用无参构造器实例化，再调用 initialize 注入 properties。
   *
   * @param impl 实现类全限定名
   * @param properties catalog 配置
   * @return 已初始化的工厂实例
   * @throws IllegalArgumentException 当缺少无参构造器或类型不匹配时
   */
  private static S3FileIOAwsClientFactory loadClientFactory(
      String impl, Map<String, String> properties) {
    DynConstructors.Ctor<S3FileIOAwsClientFactory> ctor;
    try {
      ctor =
          DynConstructors.builder(S3FileIOAwsClientFactory.class)
              .loader(S3FileIOAwsClientFactories.class.getClassLoader())
              .hiddenImpl(impl)
              .buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize S3FileIOAwsClientFactory, missing no-arg constructor: %s", impl),
          e);
    }

    S3FileIOAwsClientFactory factory;
    try {
      factory = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize S3FileIOAwsClientFactory, %s does not implement S3FileIOAwsClientFactory.",
              impl),
          e);
    }

    factory.initialize(properties);
    return factory;
  }
}
