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

import java.util.List;
import org.apache.iceberg.RewriteJobOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.expressions.Expression;

/**
 * 重写位置删除文件（position delete files）的动作。
 *
 * <p>所属模块：iceberg-api。继承自 {@link SnapshotUpdate}，重写过程会产出新快照。
 *
 * <p>职责：优化表中位置删除文件的大小与布局，通过文件组粒度并发重写，支持部分进度提交与作业 顺序控制。
 *
 * <p>设计意图：与 {@link RewriteDataFiles} 类似，将大任务按文件组拆分以提升可扩展性与容错； 专用于位置删除文件的压缩重组，改善后续读取时应用删除的效率。
 *
 * <p>上下游关系：由引擎模块实现；结果通过 {@link Result} 返回每个文件组的重写统计。
 */
public interface RewritePositionDeleteFiles
    extends SnapshotUpdate<RewritePositionDeleteFiles, RewritePositionDeleteFiles.Result> {

  /**
   * 是否在整体重写完成前，按文件组逐组提前提交。开启后会产生多次提交，但即使部分组提交失败也能 保留进度。文件组可独立压缩，故不影响正确性。
   *
   * <p>默认 false，即整体作业完成后单次提交。
   */
  String PARTIAL_PROGRESS_ENABLED = "partial-progress.enabled";

  boolean PARTIAL_PROGRESS_ENABLED_DEFAULT = false;

  /** 开启部分进度时本重写允许产生的最大提交次数。部分进度关闭时本设置无效。 */
  String PARTIAL_PROGRESS_MAX_COMMITS = "partial-progress.max-commits";

  int PARTIAL_PROGRESS_MAX_COMMITS_DEFAULT = 10;

  /** 重写策略可同时重写的最大文件组数。组的结构与内容由策略决定，每个文件组独立且异步重写。 */
  String MAX_CONCURRENT_FILE_GROUP_REWRITES = "max-concurrent-file-group-rewrites";

  int MAX_CONCURRENT_FILE_GROUP_REWRITES_DEFAULT = 5;

  /**
   * 强制指定重写作业的执行顺序。
   *
   * <ul>
   *   <li>bytes-asc：先重写最小的作业组。
   *   <li>bytes-desc：先重写最大的作业组。
   *   <li>files-asc：先重写文件数最少的作业组。
   *   <li>files-desc：先重写文件数最多的作业组。
   *   <li>none：按规划顺序重写（无特定排序）。
   * </ul>
   *
   * <p>默认 none。
   */
  String REWRITE_JOB_ORDER = "rewrite-job-order";

  String REWRITE_JOB_ORDER_DEFAULT = RewriteJobOrder.NONE.orderName();

  /**
   * 设置用于筛选待重写位置删除文件的过滤器。
   *
   * <p>逻辑：该过滤器会被转换为分区过滤器（采用 inclusive projection）。任何可能包含匹配行的 文件都会被本动作处理，其匹配的删除文件将被重写。
   *
   * @param expression 用于定位删除文件的 Iceberg 表达式
   * @return this，便于链式调用
   */
  RewritePositionDeleteFiles filter(Expression expression);

  /** 动作执行结果，包含执行摘要统计。 */
  interface Result {
    /**
     * 返回各文件组的重写结果列表。
     *
     * @return 文件组重写结果列表
     */
    List<FileGroupRewriteResult> rewriteResults();

    /**
     * 返回被重写（替换）的位置删除文件总数（聚合各成功组）。
     *
     * @return 被重写位置删除文件数
     */
    default int rewrittenDeleteFilesCount() {
      return rewriteResults().stream()
          .mapToInt(FileGroupRewriteResult::rewrittenDeleteFilesCount)
          .sum();
    }

    /**
     * 返回新增的位置删除文件总数（聚合各成功组）。
     *
     * @return 新增位置删除文件数
     */
    default int addedDeleteFilesCount() {
      return rewriteResults().stream()
          .mapToInt(FileGroupRewriteResult::addedDeleteFilesCount)
          .sum();
    }

    /**
     * 返回被重写的位置删除文件字节总数（聚合各成功组）。
     *
     * @return 被重写字节数
     */
    default long rewrittenBytesCount() {
      return rewriteResults().stream().mapToLong(FileGroupRewriteResult::rewrittenBytesCount).sum();
    }

    /**
     * 返回新增位置删除文件的字节总数（聚合各成功组）。
     *
     * @return 新增字节数
     */
    default long addedBytesCount() {
      return rewriteResults().stream().mapToLong(FileGroupRewriteResult::addedBytesCount).sum();
    }
  }

  /** 单个位置删除文件组的重写结果：包含新增文件数与被替换的原有文件数。 */
  interface FileGroupRewriteResult {
    /**
     * 返回该位置删除文件组的描述信息。
     *
     * @return 文件组信息
     */
    FileGroupInfo info();

    /**
     * 返回本组被重写（替换）的位置删除文件数。
     *
     * @return 被重写位置删除文件数
     */
    int rewrittenDeleteFilesCount();

    /**
     * 返回本组新增的位置删除文件数。
     *
     * @return 新增位置删除文件数
     */
    int addedDeleteFilesCount();

    /**
     * 返回本组被重写的位置删除文件字节数。
     *
     * @return 被重写字节数
     */
    long rewrittenBytesCount();

    /**
     * 返回本组新增位置删除文件的字节数。
     *
     * @return 新增字节数
     */
    long addedBytesCount();
  }

  /** 位置删除文件组的描述信息：记录处理时机与所属分区，用于跟踪重写操作并返回结果。 */
  interface FileGroupInfo {
    /**
     * 返回本组在本次重写全部文件组中的全局序号。
     *
     * @return 全局序号
     */
    int globalIndex();

    /**
     * 返回本组在所属分区文件组集合中的序号。
     *
     * @return 分区内序号
     */
    int partitionIndex();

    /**
     * 返回本组文件所属的分区。其类型为表的统一分区类型（综合表中所有 spec）。
     *
     * @return 分区值
     */
    StructLike partition();
  }
}
