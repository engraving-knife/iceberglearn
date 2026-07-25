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
package org.apache.iceberg.util;

import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 环境变量解析工具类，用于把配置项中引用的环境变量替换为实际值。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：扫描配置 Map，对以 {@code env:} 前缀开头的值，从进程环境变量中取对应变量值替换； 未找到环境变量则跳过该项（不放入结果），其余值原样保留。
 *
 * <p>设计意图：Iceberg 的部分配置（如 catalog 凭据、仓库路径）可能需要从运行环境注入， 而非硬编码在配置文件中。通过 {@code env:VAR_NAME}
 * 占位符，可在部署时由环境变量灵活提供， 避免敏感信息落盘。
 *
 * <p>上下游关系：被 catalog/表属性加载逻辑调用；依赖 JDK {@link System#getenv()} 与 relocated guava 的 {@link
 * ImmutableMap}。
 */
public class EnvironmentUtil {
  private EnvironmentUtil() {}

  /** 环境变量引用前缀，配置值以此开头表示需要从环境变量解析。 */
  private static final String ENVIRONMENT_VARIABLE_PREFIX = "env:";

  /**
   * 解析配置 Map 中所有引用环境变量的项，使用当前进程环境变量作为来源。
   *
   * @param properties 原始配置，值可能含 {@code env:} 前缀
   * @return 解析后的不可变配置 Map
   */
  public static Map<String, String> resolveAll(Map<String, String> properties) {
    return resolveAll(System.getenv(), properties);
  }

  /**
   * 解析配置 Map 中所有引用环境变量的项，使用指定环境变量 Map 作为来源（便于测试注入）。
   *
   * <p>逻辑：遍历每个配置项，若值以 {@code env:} 开头，则截取变量名从 env 中查找； 找到则写入结果，找不到则丢弃该项；不以该前缀开头的值原样写入。
   *
   * @param env 环境变量来源
   * @param properties 原始配置
   * @return 解析后的不可变配置 Map
   */
  @VisibleForTesting
  static Map<String, String> resolveAll(Map<String, String> env, Map<String, String> properties) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    properties.forEach(
        (name, value) -> {
          if (value.startsWith(ENVIRONMENT_VARIABLE_PREFIX)) {
            String resolved = env.get(value.substring(ENVIRONMENT_VARIABLE_PREFIX.length()));
            if (resolved != null) {
              builder.put(name, resolved);
            }
          } else {
            builder.put(name, value);
          }
        });

    return builder.build();
  }
}
