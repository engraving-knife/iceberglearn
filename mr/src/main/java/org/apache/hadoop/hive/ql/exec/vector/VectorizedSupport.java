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
package org.apache.hadoop.hive.ql.exec.vector;

import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：Hive 向量化执行支持能力声明（从 Hive 源码复制以兼容不同 Hive 版本）。
 *
 * <p>所属模块：iceberg-mr（Iceberg 与 Hive/MapReduce 集成模块，位于 iceberg-api/iceberg-core 之上，提供 Hive
 * InputFormat/OutputFormat/SerDe 等适配实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 Hive 向量化执行时支持的能力枚举（如 DECIMAL_64），供向量化 reader 查询使用。
 *   <li>维护“名称 -> 支持项”映射，便于通过字符串查找对应能力。
 * </ul>
 *
 * <p>设计意图：Hive 自身的同名类在不同版本中可能不存在或字段不同，这里复制一份避免 编译期/运行期依赖问题；字段公开（{@code VisibilityModifier} 抑制告警）以匹配
 * Hive 内部约定。
 *
 * <p>上下游关系：被 HiveIcebergInputFormat 等向量化读取相关类引用，用于声明 Iceberg 在向量化模式下能提供哪些支持能力。
 */
@SuppressWarnings("VisibilityModifier")
public class VectorizedSupport {
  /**
   * Hive 向量化执行支持的能力枚举。
   *
   * <p>每个枚举值在构造时计算小写名称，并注册到 {@link #nameToSupportMap} 中， 便于外部通过不区分大小写的字符串查询。
   */
  public enum Support {
    /** 支持 DECIMAL_64 编码（Hive 将 decimal 压缩为 long 的向量化优化）。 */
    DECIMAL_64;

    final String lowerCaseName;

    Support() {
      this.lowerCaseName = name().toLowerCase();
    }

    /** 名称到支持项的映射，在类初始化时填充，用于字符串查找能力。 */
    public static final Map<String, Support> nameToSupportMap = Maps.newHashMap();

    static {
      for (Support support : values()) {
        nameToSupportMap.put(support.lowerCaseName, support);
      }
    }
  }
}
