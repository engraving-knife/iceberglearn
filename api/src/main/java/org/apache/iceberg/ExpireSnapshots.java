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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 从表中过期（移除）旧 {@link Snapshot} 的 API。
 *
 * <p>所属模块：iceberg-api（表维护操作接口层）。
 *
 * <p>职责：累积快照删除操作并提交新的快照列表到表元数据。本 API 不允许删除当前快照。 同时会清理不再被有效快照引用的 manifest 文件，以及被过期快照所删除的数据文件。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>提交时将变更应用到最新表元数据；若发生冲突，则应用到新的最新元数据并重试。
 *   <li>支持自定义删除函数（{@link #deleteWith(Consumer)}）与并行执行器 （{@link
 *       #executeDeleteWith(ExecutorService)}），便于接入分布式清理框架。
 *   <li>支持仅过期快照但不清理底层文件（{@link #cleanExpiredFiles(boolean)}），交给 actions API 在分布式框架中更高效地删除。
 * </ul>
 *
 * <p>上下游关系：继承 {@link PendingUpdate}；由 core 模块实现，被维护作业/引擎调用。 {@link #apply()} 返回将被移除的快照列表。
 */
public interface ExpireSnapshots extends PendingUpdate<List<Snapshot>> {

  /**
   * 按 ID 过期指定 {@link Snapshot}。
   *
   * @param snapshotId 待过期快照的 ID
   * @return this，便于链式调用
   */
  ExpireSnapshots expireSnapshotId(long snapshotId);

  /**
   * 过期所有早于给定时间戳的快照。
   *
   * @param timestampMillis 时间戳（毫秒），由 {@link System#currentTimeMillis()} 返回
   * @return this，便于链式调用
   */
  ExpireSnapshots expireOlderThan(long timestampMillis);

  /**
   * 保留当前快照最近的若干个祖先快照。
   *
   * <p>逻辑：若某快照因早于过期时间戳本应被过期，但属于当前状态的最近 {@code numSnapshots} 个祖先之一，则予以保留。此规则不会影响通过 ID 显式指定的过期操作。
   *
   * <p>注意：并发添加快照时可能保留多于 {@code numSnapshots} 个祖先；当前表状态不足时 也可能少于该数。
   *
   * @param numSnapshots 需保留的最近祖先快照数
   * @return this，便于链式调用
   */
  ExpireSnapshots retainLast(int numSnapshots);

  /**
   * 传入自定义删除函数，用于删除 manifest 与数据文件。
   *
   * <p>设计要点：未被调用时仍会执行默认的 manifest/数据文件删除；调用本方法可替换为 自定义实现（如转发到分布式删除服务）。
   *
   * @param deleteFunc 用于删除文件路径的回调
   * @return this，便于链式调用
   */
  ExpireSnapshots deleteWith(Consumer<String> deleteFunc);

  /**
   * 传入自定义执行器，用于并行删除 manifest 与数据文件。
   *
   * <p>设计要点：未被调用时使用单线程执行器执行删除。
   *
   * @param executorService 用于并行删除的执行器
   * @return this，便于链式调用
   */
  ExpireSnapshots executeDeleteWith(ExecutorService executorService);

  /**
   * 传入自定义执行器，用于规划阶段。未被调用时使用默认 worker 池。
   *
   * @param executorService 用于规划的执行器
   * @return this，便于链式调用
   */
  ExpireSnapshots planWith(ExecutorService executorService);

  /**
   * 控制是否在过期快照时清理底层 manifest 与数据文件。
   *
   * <p>设计意图：设为 false 可跳过文件删除，把清理工作交给 actions API 在分布式框架中 更高效地执行。
   *
   * @param clean false 表示跳过删除过期 manifest 与文件
   * @return this，便于链式调用
   */
  ExpireSnapshots cleanExpiredFiles(boolean clean);
}
