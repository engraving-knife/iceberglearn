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

/**
 * 文件级说明：表属性更新 API 接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供表属性（key-value）的增删改能力。
 *   <li>{@link #apply()} 返回更新后的完整属性 map 供校验。
 *   <li>提供 {@link #defaultFormat(FileFormat)} 便捷设置默认文件格式。
 * </ul>
 *
 * <p>设计意图：表属性是 Iceberg 运行时配置的主要载体（如写入分布模式、文件格式、提交重试等）。 本接口继承 {@link PendingUpdate}，遵循两阶段（apply →
 * commit）模式。提交时变更应用到当前 表元数据；冲突时将挂起变更应用到新元数据并重试。
 *
 * <p>上下游关系：由 {@link Table#updateProperties()} 创建；被用户或维护工具用于修改表配置。
 */
public interface UpdateProperties extends PendingUpdate<Map<String, String>> {

  /**
   * 向表中添加一个键值对属性。
   *
   * @param key 属性键
   * @param value 属性值
   * @return this，便于链式调用
   * @throws NullPointerException 若 key 或 value 为 null
   */
  UpdateProperties set(String key, String value);

  /**
   * 从表中移除指定键的属性。
   *
   * @param key 属性键
   * @return this，便于链式调用
   * @throws NullPointerException 若 key 为 null
   */
  UpdateProperties remove(String key);

  /**
   * 设置表的默认文件格式。
   *
   * @param format 文件格式
   * @return this，便于链式调用
   */
  UpdateProperties defaultFormat(FileFormat format);
}
