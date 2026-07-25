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
package org.apache.iceberg.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

/**
 * 文件级说明：REST Catalog 客户端共享的 Jackson {@link ObjectMapper} 单例工厂。
 *
 * <p>所属模块：iceberg-core（REST Catalog 序列化基础设施）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供一个全局唯一的、配置好的 {@link ObjectMapper} 实例，供 REST 请求/响应的 JSON 序列化使用。
 *   <li>统一配置可见性（字段级别）、命名策略（kebab-case）、忽略未知属性，并注册 {@link RESTSerializers} 中的自定义序列化器。
 * </ul>
 *
 * <p>设计意图：使用双重检查锁定（double-checked locking）实现惰性初始化，保证线程安全的同时 避免每次调用都加锁带来的性能开销。{@code volatile}
 * 关键字确保初始化结果对其他线程可见。 kebab-case 命名策略对齐 REST Catalog 规范的字段命名约定。
 *
 * <p>上下游关系：被 {@link HTTPClient}、{@link RESTSerializers} 以及各类 REST 请求/响应对象使用。
 */
class RESTObjectMapper {
  private static final JsonFactory FACTORY = new JsonFactory();
  private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);
  private static volatile boolean isInitialized = false;

  /** 私有构造函数，禁止实例化工具类。 */
  private RESTObjectMapper() {}

  /**
   * 获取已配置好的 {@link ObjectMapper} 单例，首次调用时进行惰性初始化。
   *
   * <p>逻辑：使用双重检查锁定，外层判断避免已初始化后重复加锁，内层同步块保证只有一个线程完成初始化。 初始化内容包括：设置字段可见性、关闭未知属性失败、采用 kebab-case
   * 命名策略、注册自定义序列化器。
   *
   * @return 配置好的 ObjectMapper 单例
   */
  static ObjectMapper mapper() {
    if (!isInitialized) {
      synchronized (RESTObjectMapper.class) {
        if (!isInitialized) {
          MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
          MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
          MAPPER.setPropertyNamingStrategy(new PropertyNamingStrategy.KebabCaseStrategy());
          RESTSerializers.registerAll(MAPPER);
          isInitialized = true;
        }
      }
    }

    return MAPPER;
  }
}
