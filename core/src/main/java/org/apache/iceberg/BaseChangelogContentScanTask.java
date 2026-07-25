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

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * 变更日志内容扫描任务的抽象基类，承载一个 {@link ContentFile} 及其变更序号与提交快照信息。
 *
 * <p>所属模块：iceberg-core（扫描任务实现层，扩展 api 中定义的 {@link ChangelogScanTask} 接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在 {@link BaseContentScanTask} 基础上扩展 changelog 相关字段：{@code changeOrdinal}
 *       （变更序号，区分同一提交内的多次变更）与 {@code commitSnapshotId}（提交该变更的快照 id）。
 *   <li>提供 {@link SplitScanTask} 内部类，支持将一个任务按字节区间切分为多个可合并子任务， 用于分布式扫描时按区间并行执行后再合并回原任务。
 * </ul>
 *
 * <p>设计意图：把 changelog 特有信息与基础内容扫描任务的字段统一管理，并通过泛型约束 让子类型同时满足 {@link ContentScanTask} 与 {@link
 * ChangelogScanTask} 接口契约。
 *
 * <p>上下游关系：被增量式 changelog 扫描（如 position deletes 增量扫描）构造，由扫描引擎消费。
 *
 * @param <ThisT> 实际的扫描任务子类型，需同时为 {@link ContentScanTask} 与 {@link ChangelogScanTask}
 * @param <F> 内容文件类型（{@link ContentFile}）
 */
abstract class BaseChangelogContentScanTask<
        ThisT extends ContentScanTask<F> & ChangelogScanTask, F extends ContentFile<F>>
    extends BaseContentScanTask<ThisT, F> implements ChangelogScanTask {

  private final int changeOrdinal;
  private final long commitSnapshotId;

  /**
   * 构造一个 changelog 内容扫描任务。
   *
   * @param changeOrdinal 变更序号，标识同一提交内的变更顺序（0 表示第一次）
   * @param commitSnapshotId 提交该变更的快照 id
   * @param file 关联的内容文件
   * @param schemaString 序列化后的 schema JSON
   * @param specString 序列化后的分区规格 JSON
   * @param residuals 残留表达式求值器
   */
  BaseChangelogContentScanTask(
      int changeOrdinal,
      long commitSnapshotId,
      F file,
      String schemaString,
      String specString,
      ResidualEvaluator residuals) {
    super(file, schemaString, specString, residuals);
    this.changeOrdinal = changeOrdinal;
    this.commitSnapshotId = commitSnapshotId;
  }

  /** 返回变更序号，用于区分同一提交内的多次变更。 */
  @Override
  public int changeOrdinal() {
    return changeOrdinal;
  }

  /** 返回提交该变更的快照 id。 */
  @Override
  public long commitSnapshotId() {
    return commitSnapshotId;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("change_ordinal", changeOrdinal)
        .add("commit_snapshot_id", commitSnapshotId)
        .add("file", file().path())
        .add("partition_data", file().partition())
        .add("residual", residual())
        .toString();
  }

  /**
   * 切分子任务：表示从父任务中按字节区间切出的一个子扫描任务，可在分布式执行后合并回父任务。
   *
   * <p>设计意图：changelog 扫描结果需要支持按 offset/length 切分以并行执行；同时通过 {@link MergeableScanTask}
   * 接口，相邻且属于同一文件/同一变更的子任务可在收集阶段合并， 减少下游任务的调度开销。所有 changelog 相关字段（changeOrdinal、commitSnapshotId）
   * 直接委托给父任务，确保切分前后语义一致。
   *
   * @param <ThisT> 子任务自身类型
   * @param <ParentT> 父任务类型
   * @param <F> 内容文件类型
   */
  abstract static class SplitScanTask<
          ThisT, ParentT extends ContentScanTask<F> & ChangelogScanTask, F extends ContentFile<F>>
      implements ContentScanTask<F>, ChangelogScanTask, MergeableScanTask<ThisT> {

    private final ParentT parentTask;
    private final long offset;
    private final long length;

    /**
     * 构造一个切分子任务。
     *
     * @param parentTask 父任务，提供 changelog 字段、文件、分区、残留表达式等
     * @param offset 子任务在文件中的起始字节偏移
     * @param length 子任务覆盖的字节长度
     */
    protected SplitScanTask(ParentT parentTask, long offset, long length) {
      this.parentTask = parentTask;
      this.offset = offset;
      this.length = length;
    }

    /**
     * 子类实现：基于新的长度复制一个同类型子任务，用于 {@link #merge} 合并时构造合并后任务。
     *
     * @param newLength 合并后的新长度
     * @return 新长度的子任务
     */
    protected abstract ThisT copyWithNewLength(long newLength);

    /** 返回该子任务对应的父任务。 */
    protected ParentT parentTask() {
      return parentTask;
    }

    @Override
    public int changeOrdinal() {
      return parentTask.changeOrdinal();
    }

    @Override
    public long commitSnapshotId() {
      return parentTask.commitSnapshotId();
    }

    @Override
    public F file() {
      return parentTask.file();
    }

    @Override
    public PartitionSpec spec() {
      return parentTask.spec();
    }

    /** 返回子任务在文件中的起始字节偏移。 */
    @Override
    public long start() {
      return offset;
    }

    /** 返回子任务覆盖的字节长度。 */
    @Override
    public long length() {
      return length;
    }

    @Override
    public Expression residual() {
      return parentTask.residual();
    }

    /**
     * 判断当前子任务是否可与另一个任务合并。
     *
     * <p>逻辑：仅当两个任务类相同、变更序号一致、提交快照一致、文件相同，且当前任务结尾正好等于 另一任务起始（即两段字节相邻）时方可合并。
     *
     * @param other 待判定的另一任务
     * @return 可合并返回 true
     */
    @Override
    public boolean canMerge(ScanTask other) {
      if (getClass().equals(other.getClass())) {
        SplitScanTask<?, ?, ?> that = (SplitScanTask<?, ?, ?>) other;
        return changeOrdinal() == that.changeOrdinal()
            && commitSnapshotId() == that.commitSnapshotId()
            && file().equals(that.file())
            && start() + length() == that.start();

      } else {
        return false;
      }
    }

    /**
     * 合并另一个相邻子任务，返回长度为两者之和的新子任务。
     *
     * <p>调用前需先通过 {@link #canMerge} 判定可合并性。
     *
     * @param other 相邻且可合并的子任务
     * @return 合并后的新子任务
     */
    @Override
    public ThisT merge(ScanTask other) {
      SplitScanTask<?, ?, ?> that = (SplitScanTask<?, ?, ?>) other;
      return copyWithNewLength(length() + that.length());
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("change_ordinal", changeOrdinal())
          .add("commit_snapshot_id", commitSnapshotId())
          .add("file", file().path())
          .add("partition_data", file().partition())
          .add("offset", offset)
          .add("length", length)
          .add("residual", residual())
          .toString();
    }
  }
}
