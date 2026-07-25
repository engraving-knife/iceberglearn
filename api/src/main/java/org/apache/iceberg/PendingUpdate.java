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

import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.ValidationException;

/**
 * 文件级说明：表元数据变更的挂起更新接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，是所有表更新 API 的基接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义表元数据变更的两阶段操作：先 {@link #apply()} 计算待提交的变更（用于校验）， 再 {@link #commit()} 提交。
 *   <li>提供 {@link #updateEvent()} 生成变更通知事件。
 * </ul>
 *
 * <p>设计意图：将“计算变更”与“提交变更”分离，允许调用方在 apply 后、commit 前对变更进行 校验或预览。commit
 * 时通过底层表的提交方法原子化应用，成功后刷新表元数据。这种两阶段设计 也支撑了乐观并发重试机制。
 *
 * <p>上下游关系：被 {@link AppendFiles}、{@link DeleteFiles}、{@link UpdateProperties}、 {@link
 * ReplacePartitions}、{@link OverwriteFiles}、{@link ReplaceSortOrder} 等所有更新 API 继承。
 *
 * @param <T> apply() 返回的变更对象的 Java 类型，用于校验
 */
public interface PendingUpdate<T> {

  /**
   * 应用挂起的变更，返回尚未提交的变更结果供校验。
   *
   * <p>本方法不会产生持久化更新。
   *
   * @return 调用 {@link #commit()} 时将提交的未提交变更
   * @throws ValidationException 若挂起的变更无法应用到当前元数据
   * @throws IllegalArgumentException 若挂起的变更存在冲突或非法
   */
  T apply();

  /**
   * 应用挂起的变更并提交。
   *
   * <p>变更通过调用底层表的 commit 方法提交。提交成功后，更新的表将被刷新。
   *
   * @throws ValidationException 若更新无法应用到当前表元数据
   * @throws CommitFailedException 若因冲突导致更新无法提交
   * @throws CommitStateUnknownException 若更新成功与否未知，此时不应做任何清理
   */
  void commit();

  /**
   * 生成更新事件，用于通知元数据变更。
   *
   * <p>默认返回 null，由具体实现按需覆写。
   *
   * @return 生成的事件对象
   */
  default Object updateEvent() {
    return null;
  }
}
