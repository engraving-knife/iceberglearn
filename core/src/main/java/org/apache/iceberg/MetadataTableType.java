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

import java.util.Locale;

/**
 * 元数据表类型枚举：标识 Iceberg 提供的各类只读元数据表。
 *
 * <p>所属模块：iceberg-core（元数据表层）。
 *
 * <p>职责：枚举所有内置元数据表类型，便于在 catalog 与扫描层统一识别和创建。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>当前快照范围 vs 全部历史范围（ALL_* 前缀）通过枚举值区分。
 *   <li>{@code from} 方法容忍非法输入返回 null，避免在用户拼接表名场景下抛异常。
 * </ul>
 *
 * <p>上下游关系：被各 {@code BaseMetadataTable} 子类的 {@code metadataTableType()} 返回； 被 catalog
 * 用于根据表名后缀路由到对应元数据表实现。
 */
public enum MetadataTableType {
  ENTRIES,
  FILES,
  DATA_FILES,
  DELETE_FILES,
  HISTORY,
  METADATA_LOG_ENTRIES,
  SNAPSHOTS,
  REFS,
  MANIFESTS,
  PARTITIONS,
  ALL_DATA_FILES,
  ALL_DELETE_FILES,
  ALL_FILES,
  ALL_MANIFESTS,
  ALL_ENTRIES,
  POSITION_DELETES;

  /**
   * 按名称（大小写不敏感）解析元数据表类型。
   *
   * @param name 类型名称
   * @return 对应枚举值；非法名称返回 null
   */
  public static MetadataTableType from(String name) {
    try {
      return MetadataTableType.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
