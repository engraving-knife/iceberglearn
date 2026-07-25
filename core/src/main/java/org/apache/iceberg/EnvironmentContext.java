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
package org.apache.iceberg;

import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 运行环境上下文：维护引擎名称/版本等全局属性，用于快照 summary 标识提交来源。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护一组全局键值对属性（如引擎名称、引擎版本、Iceberg 版本）。
 *   <li>在提交快照时把这些属性写入 summary，便于追踪提交来源。
 * </ul>
 *
 * <p>设计意图：使用并发安全的 {@code ConcurrentHashMap} 存储属性，支持多线程读写。 Iceberg 版本在类加载时自动注入。
 *
 * <p>上下游关系：被各引擎集成模块在初始化时设置引擎信息；被 {@link SnapshotProducer} 等在构建快照 summary 时读取。
 */
public class EnvironmentContext {
  /** 引擎名称属性键。 */
  public static final String ENGINE_NAME = "engine-name";
  /** 引擎版本属性键。 */
  public static final String ENGINE_VERSION = "engine-version";

  private EnvironmentContext() {}

  private static final Map<String, String> PROPERTIES = Maps.newConcurrentMap();

  static {
    PROPERTIES.put("iceberg-version", IcebergBuild.fullVersion());
  }

  /**
   * 返回所有环境属性的不可变副本。
   *
   * @return 属性的不可变映射
   */
  public static Map<String, String> get() {
    return ImmutableMap.copyOf(PROPERTIES);
  }

  /**
   * 向全局属性映射中添加键值对。
   *
   * @param key 属性键
   * @param value 属性值
   */
  public static void put(String key, String value) {
    PROPERTIES.put(key, value);
  }
}
