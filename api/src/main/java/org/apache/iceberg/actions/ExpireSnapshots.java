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
package org.apache.iceberg.actions;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.apache.iceberg.io.SupportsBulkOperations;

/**
 * 过期（expire）表中快照的动作。
 *
 * <p>所属模块：iceberg-api。继承自 {@link Action}，用于表的时间旅行与存储清理。
 *
 * <p>职责：按快照 ID、时间戳或保留最近 N 个祖先等策略过期快照，并删除不再被有效快照引用的 清单与内容文件。
 *
 * <p>设计意图：与核心层 {@link org.apache.iceberg.ExpireSnapshots} 语义一致，但本 Action 版本
 * 可借助查询引擎分布式执行删除工作，适用于大规模表的高效清理。
 *
 * <p>上下游关系：由引擎模块实现；删除执行依赖 {@link org.apache.iceberg.io.FileIO} 或自定义 deleteFunc；结果通过 {@link Result}
 * 返回各类文件删除计数。
 */
public interface ExpireSnapshots extends Action<ExpireSnapshots, ExpireSnapshots.Result> {
  /**
   * 过期指定 ID 的快照。
   *
   * <p>语义同 {@link org.apache.iceberg.ExpireSnapshots#expireSnapshotId(long)}。
   *
   * @param snapshotId 待过期快照的 ID
   * @return this，便于链式调用
   */
  ExpireSnapshots expireSnapshotId(long snapshotId);

  /**
   * 过期所有早于给定时间戳的快照。
   *
   * <p>语义同 {@link org.apache.iceberg.ExpireSnapshots#expireOlderThan(long)}。
   *
   * @param timestampMillis 时间戳，单位毫秒，由 {@link System#currentTimeMillis()} 返回
   * @return this，便于链式调用
   */
  ExpireSnapshots expireOlderThan(long timestampMillis);

  /**
   * 保留当前快照最近的若干个祖先快照。
   *
   * <p>若某快照因早于过期时间戳本应被过期，但属于当前状态最近 {@code numSnapshots} 个祖先之一， 则予以保留。该策略不会阻止通过 ID 显式指定过期的快照。
   *
   * <p>语义同 {@link org.apache.iceberg.ExpireSnapshots#retainLast(int)}。
   *
   * @param numSnapshots 保留的快照数量
   * @return this，便于链式调用
   */
  ExpireSnapshots retainLast(int numSnapshots);

  /**
   * 指定用于删除清单、数据文件和删除文件的自定义删除函数。
   *
   * <p>不再被有效快照使用的清单文件将被删除；被过期快照逻辑删除的内容文件也会被物理删除。 即使不调用本方法，冗余清单与内容文件仍会被删除。
   *
   * <p>语义同 {@link org.apache.iceberg.ExpireSnapshots#deleteWith(Consumer)}。
   *
   * @param deleteFunc 接收文件路径的删除函数
   * @return this，便于链式调用
   */
  ExpireSnapshots deleteWith(Consumer<String> deleteFunc);

  /**
   * 指定用于删除文件的替代执行器服务。
   *
   * <p>仅当通过 {@link #deleteWith(Consumer)} 提供自定义删除函数、或 FileIO 不 {@link SupportsBulkOperations
   * 支持批量删除}时才会使用该执行器；否则并行度由 IO 专属的 {@link SupportsBulkOperations#deleteFiles(Iterable) deleteFiles}
   * 控制。若未调用且不支持 批量删除，冗余清单与内容文件仍会在当前线程被删除。
   *
   * <p>语义同 {@link org.apache.iceberg.ExpireSnapshots#executeDeleteWith(ExecutorService)}。
   *
   * @param executorService 使用的执行器服务
   * @return this，便于链式调用
   */
  ExpireSnapshots executeDeleteWith(ExecutorService executorService);

  /** 动作执行结果，包含执行摘要统计。 */
  interface Result {
    /**
     * 返回已删除的数据文件数量。
     *
     * @return 已删除数据文件数
     */
    long deletedDataFilesCount();

    /**
     * 返回已删除的等值删除文件数量。
     *
     * @return 已删除等值删除文件数
     */
    long deletedEqualityDeleteFilesCount();

    /**
     * 返回已删除的位置删除文件数量。
     *
     * @return 已删除位置删除文件数
     */
    long deletedPositionDeleteFilesCount();

    /**
     * 返回已删除的清单（manifest）文件数量。
     *
     * @return 已删除清单文件数
     */
    long deletedManifestsCount();

    /**
     * 返回已删除的清单列表（manifest list）数量。
     *
     * @return 已删除清单列表数
     */
    long deletedManifestListsCount();

    /**
     * 返回已删除的统计文件数量。默认返回 0，兼容未实现该统计的实现。
     *
     * @return 已删除统计文件数
     */
    default long deletedStatisticsFilesCount() {
      return 0L;
    }
  }
}
