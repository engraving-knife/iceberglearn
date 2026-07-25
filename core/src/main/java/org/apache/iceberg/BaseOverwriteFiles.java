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

import java.util.Set;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Projections;
import org.apache.iceberg.expressions.StrictMetricsEvaluator;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 表数据覆写操作的 core 实现：用过滤表达式删除匹配文件，并可追加新文件。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link OverwriteFiles}，按行过滤表达式批量删除匹配数据文件，并支持追加新数据文件。
 *   <li>提供多种冲突校验：校验新增文件匹配覆写过滤、校验无冲突新增数据、无冲突新增删除。
 *   <li>支持指定起始快照与冲突检测过滤，用于并发写入场景下的乐观冲突检测。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link MergingSnapshotProducer} 复用"合并 manifest + 删除/新增文件"的通用提交骨架， 本类只关注覆写语义与校验。
 *   <li>校验采用"严格 vs 包含"两级投影：inclusive 用于快速排除，strict/metrics 用于确认， 兼顾性能与正确性。
 *   <li>冲突检测过滤可独立于行过滤单独指定，支持更精细的冲突范围控制。
 * </ul>
 *
 * <p>上下游关系：被 {@link BaseTransaction#newOverwrite} 等创建；提交时调用 {@link MergingSnapshotProducer} 的
 * commit 流程，{@link #validate} 在提交前执行冲突校验。
 */
public class BaseOverwriteFiles extends MergingSnapshotProducer<OverwriteFiles>
    implements OverwriteFiles {
  private final Set<DataFile> deletedDataFiles = Sets.newHashSet();
  private boolean validateAddedFilesMatchOverwriteFilter = false;
  private Long startingSnapshotId = null;
  private Expression conflictDetectionFilter = null;
  private boolean validateNewDataFiles = false;
  private boolean validateNewDeletes = false;

  /**
   * 构造覆写操作。
   *
   * @param tableName 表名
   * @param ops 表操作句柄
   */
  protected BaseOverwriteFiles(String tableName, TableOperations ops) {
    super(tableName, ops);
  }

  /** 返回自身（用于 fluent API 的 self 类型）。 */
  @Override
  protected OverwriteFiles self() {
    return this;
  }

  /** 返回本操作的数据操作类型字符串。 */
  @Override
  protected String operation() {
    return DataOperations.OVERWRITE;
  }

  /**
   * 按行过滤表达式删除匹配的数据文件。
   *
   * <p>逻辑：委托 {@link MergingSnapshotProducer#deleteByRowFilter} 记录待删除过滤。
   *
   * @param expr 行过滤表达式
   * @return this，便于链式调用
   */
  @Override
  public OverwriteFiles overwriteByRowFilter(Expression expr) {
    deleteByRowFilter(expr);
    return this;
  }

  /**
   * 追加一个数据文件。
   *
   * @param file 待追加的数据文件
   * @return this，便于链式调用
   */
  @Override
  public OverwriteFiles addFile(DataFile file) {
    add(file);
    return this;
  }

  /**
   * 显式删除一个数据文件（与按过滤删除互补）。
   *
   * <p>逻辑：同时加入 {@code deletedDataFiles}（用于后续针对这些文件的冲突校验）与父类的删除集合。
   *
   * @param file 待删除的数据文件
   * @return this，便于链式调用
   */
  @Override
  public OverwriteFiles deleteFile(DataFile file) {
    deletedDataFiles.add(file);
    delete(file);
    return this;
  }

  /**
   * 要求校验：所有新增文件都必须完全匹配覆写过滤（即文件内全部记录都满足过滤）。
   *
   * @return this，便于链式调用
   */
  @Override
  public OverwriteFiles validateAddedFilesMatchOverwriteFilter() {
    this.validateAddedFilesMatchOverwriteFilter = true;
    return this;
  }

  /**
   * 指定冲突检测的起始快照：基于该快照之后的变更做冲突校验。
   *
   * @param snapshotId 起始快照 id
   * @return this，便于链式调用
   */
  @Override
  public OverwriteFiles validateFromSnapshot(long snapshotId) {
    this.startingSnapshotId = snapshotId;
    return this;
  }

  /**
   * 指定冲突检测过滤表达式（可不同于行过滤）。
   *
   * @param newConflictDetectionFilter 冲突检测过滤，不能为 null
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若过滤为 null
   */
  @Override
  public OverwriteFiles conflictDetectionFilter(Expression newConflictDetectionFilter) {
    Preconditions.checkArgument(
        newConflictDetectionFilter != null, "Conflict detection filter cannot be null");
    this.conflictDetectionFilter = newConflictDetectionFilter;
    return this;
  }

  /**
   * 要求校验：起始快照之后无冲突的新增数据文件。
   *
   * <p>逻辑：置位标记，并调用 {@link MergingSnapshotProducer#failMissingDeletePaths} 要求删除路径必须存在（否则无法做冲突检测）。
   *
   * @return this，便于链式调用
   */
  @Override
  public OverwriteFiles validateNoConflictingData() {
    this.validateNewDataFiles = true;
    failMissingDeletePaths();
    return this;
  }

  /**
   * 要求校验：起始快照之后无冲突的新增删除文件。
   *
   * <p>逻辑：置位标记，并调用 {@link MergingSnapshotProducer#failMissingDeletePaths} 要求删除路径必须存在。
   *
   * @return this，便于链式调用
   */
  @Override
  public OverwriteFiles validateNoConflictingDeletes() {
    this.validateNewDeletes = true;
    failMissingDeletePaths();
    return this;
  }

  /**
   * 将本操作目标切换到指定分支。
   *
   * @param branch 分支名
   * @return this，便于链式调用
   */
  @Override
  public BaseOverwriteFiles toBranch(String branch) {
    targetBranch(branch);
    return this;
  }

  /**
   * 提交前校验：根据已配置的校验选项执行相应检查。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若 {@link #validateAddedFilesMatchOverwriteFilter}：对每个新增文件，用 inclusive 投影 快速排除，再用 strict
   *       投影或 {@link StrictMetricsEvaluator} 确认文件全部记录匹配过滤， 不匹配则抛 {@link ValidationException}；
   *   <li>若 {@link #validateNewDataFiles}：校验起始快照之后无新增冲突数据文件；
   *   <li>若 {@link #validateNewDeletes}：校验无新增删除文件、无被删除的数据文件冲突； 对显式删除的文件单独校验无新增删除针对它们。
   * </ul>
   *
   * @param base 提交前的基础表元数据
   * @param parent 父快照
   * @throws ValidationException 校验失败时抛出
   */
  @Override
  protected void validate(TableMetadata base, Snapshot parent) {
    if (validateAddedFilesMatchOverwriteFilter) {
      PartitionSpec spec = dataSpec();
      Expression rowFilter = rowFilter();

      Expression inclusiveExpr = Projections.inclusive(spec).project(rowFilter);
      Evaluator inclusive = new Evaluator(spec.partitionType(), inclusiveExpr);

      Expression strictExpr = Projections.strict(spec).project(rowFilter);
      Evaluator strict = new Evaluator(spec.partitionType(), strictExpr);

      StrictMetricsEvaluator metrics =
          new StrictMetricsEvaluator(base.schema(), rowFilter, isCaseSensitive());

      for (DataFile file : addedDataFiles()) {
        // the real test is that the strict or metrics test matches the file, indicating that all
        // records in the file match the filter. inclusive is used to avoid testing the metrics,
        // which is more complicated
        ValidationException.check(
            inclusive.eval(file.partition())
                && (strict.eval(file.partition()) || metrics.eval(file)),
            "Cannot append file with rows that do not match filter: %s: %s",
            rowFilter,
            file.path());
      }
    }

    if (validateNewDataFiles) {
      validateAddedDataFiles(base, startingSnapshotId, dataConflictDetectionFilter(), parent);
    }

    if (validateNewDeletes) {
      if (rowFilter() != Expressions.alwaysFalse()) {
        Expression filter = conflictDetectionFilter != null ? conflictDetectionFilter : rowFilter();
        validateNoNewDeleteFiles(base, startingSnapshotId, filter, parent);
        validateDeletedDataFiles(base, startingSnapshotId, filter, parent);
      }

      if (deletedDataFiles.size() > 0) {
        validateNoNewDeletesForDataFiles(
            base, startingSnapshotId, conflictDetectionFilter, deletedDataFiles, parent);
      }
    }
  }

  /**
   * 计算用于数据冲突检测的过滤表达式。
   *
   * <p>逻辑：优先用显式指定的 {@code conflictDetectionFilter}；否则若行过滤非 alwaysFalse 且无显式删除文件，则用行过滤；否则用
   * alwaysTrue（即检测全部数据）。
   *
   * @return 数据冲突检测过滤
   */
  private Expression dataConflictDetectionFilter() {
    if (conflictDetectionFilter != null) {
      return conflictDetectionFilter;
    } else if (rowFilter() != Expressions.alwaysFalse() && deletedDataFiles.isEmpty()) {
      return rowFilter();
    } else {
      return Expressions.alwaysTrue();
    }
  }
}
