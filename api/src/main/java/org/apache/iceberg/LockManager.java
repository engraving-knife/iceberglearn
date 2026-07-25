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
 * 文件级说明：锁管理器接口，用于保障提交隔离性（commit isolation）。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 或各 catalog 实现提供具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供基于实体 ID 的锁获取（acquire）与释放（release）能力。
 *   <li>支持从 catalog 属性初始化锁管理器配置。
 *   <li>继承 {@link AutoCloseable}，支持资源清理。
 * </ul>
 *
 * <p>设计意图：在乐观并发提交冲突频发的场景下，可通过锁管理器实现悲观并发控制， 确保同一表/实体的提交串行化。接口不绑定具体实现（可基于内存、数据库、Zookeeper 等）， 通过
 * catalog 属性注入配置。
 *
 * <p>上下游关系：由 catalog 在提交时调用；具体实现由 core 模块或外部集成提供。
 */
public interface LockManager extends AutoCloseable {

  /**
   * 尝试获取指定实体的锁。
   *
   * @param entityId 被锁定的实体 ID（如表名）
   * @param ownerId 锁持有者 ID（用于标识谁持有锁）
   * @return 若锁被 ownerId 成功获取则返回 true，否则返回 false
   */
  boolean acquire(String entityId, String ownerId);

  /**
   * 释放指定实体的锁。
   *
   * <p>本方法不得抛出异常（设计约束），以避免锁释放失败影响后续流程。
   *
   * @param entityId 被锁定的实体 ID
   * @param ownerId 锁持有者 ID
   * @return 若 ownerId 原本持有锁且成功释放则返回 true，否则返回 false
   */
  boolean release(String entityId, String ownerId);

  /**
   * 从 catalog 属性初始化锁管理器配置。
   *
   * @param properties catalog 属性键值对
   */
  void initialize(Map<String, String> properties);
}
