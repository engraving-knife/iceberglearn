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
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 按某种重写策略重写数据文件的动作。通常用于优化表内数据文件的大小与布局。
 *
 * <p>所属模块：iceberg-api。继承自 {@link SnapshotUpdate}，重写过程会产出新快照。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 BINPACK / SORT / Z-ORDER 等多种重写策略选择，优化数据文件大小与聚类布局。
 *   <li>通过文件组（file group）将大分区拆分为可独立处理的子单元，支持部分进度提交与并发重写。
 *   <li>提供过滤器、目标文件大小、作业顺序等可配置项。
 * </ul>
 *
 * <p>设计意图：大分区直接整体重写可能因集群资源受限而失败，故引入文件组粒度，使每个组可独立、 异步、甚至部分失败可提交。常量以 String key + 默认值形式定义，便于通过 option
 * 统一配置。
 *
 * <p>上下游关系：由引擎模块实现；结果通过 {@link Result} 返回每个文件组的重写统计。
 */
public interface RewriteDataFiles
    extends SnapshotUpdate<RewriteDataFiles, RewriteDataFiles.Result> {

  /**
   * 是否在整体重写完成前，按文件组（见 max-file-group-size-bytes）逐组提前提交。开启后会产生
   * 多次提交，但即使部分组提交失败也能保留进度。文件组可独立压缩，故不影响正确性。
   *
   * <p>默认 false，即整体作业完成后单次提交。
   */
  String PARTIAL_PROGRESS_ENABLED = "partial-progress.enabled";

  boolean PARTIAL_PROGRESS_ENABLED_DEFAULT = false;

  /** 开启部分进度时本重写允许产生的最大提交次数。部分进度关闭时本设置无效。 */
  String PARTIAL_PROGRESS_MAX_COMMITS = "partial-progress.max-commits";

  int PARTIAL_PROGRESS_MAX_COMMITS_DEFAULT = 10;

  /**
   * 整个重写作业按分区切分，并在分区内按大小进一步切分为"文件组"。单个组最多压缩的数据量由 {@link #MAX_FILE_GROUP_SIZE_BYTES}
   * 控制，用于拆分超大分区，避免因集群资源受限无法整体重写 （例如基于排序的重写难以扩展到 TB 级分区，需分小段处理）。
   *
   * <p>分组时，重写策略以该值为上限决定单个文件组包含的文件；每个组由单个框架"动作"处理 （如 Spark 中每个组在独立的 Spark action 中重写）。一个组不会跨越多个输出分区。
   */
  String MAX_FILE_GROUP_SIZE_BYTES = "max-file-group-size-bytes";

  long MAX_FILE_GROUP_SIZE_BYTES_DEFAULT = 1024L * 1024L * 1024L * 100L; // 100 Gigabytes

  /** 重写策略可同时重写的最大文件组数。组的结构与内容由策略决定，每个文件组独立且异步重写。 */
  String MAX_CONCURRENT_FILE_GROUP_REWRITES = "max-concurrent-file-group-rewrites";

  int MAX_CONCURRENT_FILE_GROUP_REWRITES_DEFAULT = 5;

  /** 重写时试图生成的目标输出文件大小。默认使用被更新表属性中的 "write.target-file-size-bytes"。 */
  String TARGET_FILE_SIZE_BYTES = "target-file-size-bytes";

  /**
   * 压缩产生的新数据文件是否使用压缩开始时刻快照的序列号，而非新产出快照的序列号。
   *
   * <p>这样可避免与在更高序列号上追加新等值删除的更新发生提交冲突。
   *
   * <p>默认 true。
   */
  String USE_STARTING_SEQUENCE_NUMBER = "use-starting-sequence-number";

  boolean USE_STARTING_SEQUENCE_NUMBER_DEFAULT = true;

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
   * 为本次重写选择 BINPACK（装箱）策略。默认即该策略，故直接返回 this。
   *
   * @return this，便于链式调用
   */
  default RewriteDataFiles binPack() {
    return this;
  }

  /**
   * 为本次重写选择 SORT（排序）策略，使用表自身的 sortOrder。
   *
   * @return this，便于链式调用
   */
  default RewriteDataFiles sort() {
    throw new UnsupportedOperationException(
        "SORT Rewrite Strategy not implemented for this framework");
  }

  /**
   * 为本次重写选择 SORT（排序）策略，并手动指定使用的 sortOrder。
   *
   * @param sortOrder 用户自定义排序规则
   * @return this，便于链式调用
   */
  default RewriteDataFiles sort(SortOrder sortOrder) {
    throw new UnsupportedOperationException(
        "SORT Rewrite Strategy not implemented for this framework");
  }

  /**
   * 为本次重写选择 Z-ORDER 策略，并指定用于生成 Z 值的列。
   *
   * @param columns 用于生成 Z 值的列
   * @return this，便于链式调用
   */
  default RewriteDataFiles zOrder(String... columns) {
    throw new UnsupportedOperationException(
        "Z-ORDER Rewrite Strategy not implemented for this framework");
  }

  /**
   * 设置用户提供的过滤器，决定哪些文件会被重写策略考虑。该过滤器会与策略自身规则叠加使用， 例如用于限定仅在某个分区执行重写。
   *
   * @param expression 用于筛选待重写文件的 Iceberg 表达式
   * @return this，便于链式调用
   */
  RewriteDataFiles filter(Expression expression);

  /** 重写动作结果：文件组信息到该组重写结果的映射。若某组结果为 null 表示该组失败。仅当开启 部分进度时才会出现失败组，否则整个作业视为失败。 */
  interface Result {
    /**
     * 返回各文件组的重写结果列表。
     *
     * @return 文件组重写结果列表
     */
    List<FileGroupRewriteResult> rewriteResults();

    /**
     * 返回失败文件组的结果列表，默认空。
     *
     * @return 失败文件组结果列表
     */
    default List<FileGroupFailureResult> rewriteFailures() {
      return ImmutableList.of();
    }

    /**
     * 返回新增数据文件总数（聚合各成功组）。
     *
     * @return 新增数据文件数
     */
    default int addedDataFilesCount() {
      return rewriteResults().stream().mapToInt(FileGroupRewriteResult::addedDataFilesCount).sum();
    }

    /**
     * 返回被重写（替换）的数据文件总数（聚合各成功组）。
     *
     * @return 被重写数据文件数
     */
    default int rewrittenDataFilesCount() {
      return rewriteResults().stream()
          .mapToInt(FileGroupRewriteResult::rewrittenDataFilesCount)
          .sum();
    }

    /**
     * 返回被重写的字节总数（聚合各成功组）。
     *
     * @return 被重写字节数
     */
    default long rewrittenBytesCount() {
      return rewriteResults().stream().mapToLong(FileGroupRewriteResult::rewrittenBytesCount).sum();
    }

    /**
     * 返回失败组涉及的数据文件总数（聚合各失败组）。
     *
     * @return 失败数据文件数
     */
    default int failedDataFilesCount() {
      return rewriteFailures().stream().mapToInt(FileGroupFailureResult::dataFilesCount).sum();
    }
  }

  /** 单个文件组的重写结果：包含新增文件数与被替换的原有文件数。 */
  interface FileGroupRewriteResult {
    /**
     * 返回该结果对应的文件组描述信息。
     *
     * @return 文件组信息
     */
    FileGroupInfo info();

    /**
     * 返回本组新增的数据文件数。
     *
     * @return 新增数据文件数
     */
    int addedDataFilesCount();

    /**
     * 返回本组被重写（替换）的原有数据文件数。
     *
     * @return 被重写数据文件数
     */
    int rewrittenDataFilesCount();

    /**
     * 返回本组被重写的字节数，默认 0。
     *
     * @return 被重写字节数
     */
    default long rewrittenBytesCount() {
      return 0L;
    }
  }

  /** 重写失败的文件组结果。 */
  interface FileGroupFailureResult {
    /**
     * 返回该失败结果对应的文件组描述信息。
     *
     * @return 文件组信息
     */
    FileGroupInfo info();

    /**
     * 返回该失败组涉及的数据文件数。
     *
     * @return 数据文件数
     */
    int dataFilesCount();
  }

  /** 文件组描述信息：记录处理时机与所属分区，用于跟踪重写操作并返回结果。 */
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
     * 返回本组文件所属的分区。
     *
     * @return 分区值
     */
    StructLike partition();
  }
}
