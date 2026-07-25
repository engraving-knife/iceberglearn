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
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.CharSequenceSet;
import org.apache.iceberg.util.SnapshotUtil;

/**
 * {@link RowDelta} 接口的核心实现：用于一次性提交"新增数据文件 + 新增删除文件"的行级增量更新。
 *
 * <p>所属模块：iceberg-core（事务/快照生产层，扩展 {@link MergingSnapshotProducer}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>支持向表中追加 data files（{@code addRows}）与 delete files（{@code addDeletes}）， 通常用于引擎把一批 INSERT +
 *       DELETE 的增量结果一次性提交。
 *   <li>提供 commit 前的冲突校验：可校验引用的数据文件仍存在、校验并发新增数据/删除文件冲突、 指定冲突检测过滤等，保障并发写入下的正确性。
 * </ul>
 *
 * <p>设计意图：RowDelta 用于"行级增量"语义（不同于 overwrite 的整批替换），需要同时管理 data 与 delete
 * 两类文件，并在提交时基于"起始快照"做并发冲突检测。校验逻辑统一放在 {@link #validate} 中，由父类 {@link MergingSnapshotProducer} 在
 * commit 前调用。
 *
 * <p>上下游关系：被引擎（如 Spark/Flink）在执行 MERGE/DELETE 等行级操作时调用；底层依赖 {@link MergingSnapshotProducer}
 * 提供的文件合并与冲突校验基础设施。
 */
class BaseRowDelta extends MergingSnapshotProducer<RowDelta> implements RowDelta {
  private Long startingSnapshotId = null; // check all versions by default
  private final CharSequenceSet referencedDataFiles = CharSequenceSet.empty();
  private boolean validateDeletes = false;
  private Expression conflictDetectionFilter = Expressions.alwaysTrue();
  private boolean validateNewDataFiles = false;
  private boolean validateNewDeleteFiles = false;

  /**
   * 构造一个 RowDelta 操作。
   *
   * @param tableName 表名
   * @param ops 表操作接口（提供元数据读写与 commit）
   */
  BaseRowDelta(String tableName, TableOperations ops) {
    super(tableName, ops);
  }

  /** 返回自身，用于泛型 self() 模式。 */
  @Override
  protected BaseRowDelta self() {
    return this;
  }

  /** 返回该操作对应的快照操作类型，RowDelta 视为 OVERWRITE。 */
  @Override
  protected String operation() {
    return DataOperations.OVERWRITE;
  }

  /**
   * 追加一个数据文件（INSERT 的产物）。
   *
   * @param inserts 待追加的数据文件
   * @return 当前操作
   */
  @Override
  public RowDelta addRows(DataFile inserts) {
    add(inserts);
    return this;
  }

  /**
   * 追加一个删除文件（位置删除或等值删除）。
   *
   * @param deletes 待追加的删除文件
   * @return 当前操作
   */
  @Override
  public RowDelta addDeletes(DeleteFile deletes) {
    add(deletes);
    return this;
  }

  /**
   * 设置冲突校验的起始快照：仅校验该快照之后发生的并发变更。
   *
   * @param snapshotId 起始快照 id
   * @return 当前操作
   */
  @Override
  public RowDelta validateFromSnapshot(long snapshotId) {
    this.startingSnapshotId = snapshotId;
    return this;
  }

  /**
   * 标记在提交前需要校验被引用的数据文件是否已被删除。
   *
   * @return 当前操作
   */
  @Override
  public RowDelta validateDeletedFiles() {
    this.validateDeletes = true;
    return this;
  }

  /**
   * 登记需要在提交时校验"仍然存在"的数据文件（通常是行级删除操作引用的输入数据文件）。
   *
   * @param referencedFiles 引用的数据文件路径集合
   * @return 当前操作
   */
  @Override
  public RowDelta validateDataFilesExist(Iterable<? extends CharSequence> referencedFiles) {
    referencedFiles.forEach(referencedDataFiles::add);
    return this;
  }

  /**
   * 设置冲突检测过滤表达式，用于在并发场景下判断其他提交是否与本次变更可能冲突。
   *
   * @param newConflictDetectionFilter 冲突检测过滤，不可为 null
   * @return 当前操作
   */
  @Override
  public RowDelta conflictDetectionFilter(Expression newConflictDetectionFilter) {
    Preconditions.checkArgument(
        newConflictDetectionFilter != null, "Conflict detection filter cannot be null");
    this.conflictDetectionFilter = newConflictDetectionFilter;
    return this;
  }

  /**
   * 标记需要在提交时校验：自起始快照以来没有其他并发新增的数据文件（与本次冲突检测过滤相交）。
   *
   * @return 当前操作
   */
  @Override
  public RowDelta validateNoConflictingDataFiles() {
    this.validateNewDataFiles = true;
    return this;
  }

  /**
   * 标记需要在提交时校验：自起始快照以来没有其他并发新增的删除文件（与本次冲突检测过滤相交）。
   *
   * @return 当前操作
   */
  @Override
  public RowDelta validateNoConflictingDeleteFiles() {
    this.validateNewDeleteFiles = true;
    return this;
  }

  /**
   * 指定本次提交的目标分支。
   *
   * @param branch 分支名
   * @return 当前操作
   */
  @Override
  public RowDelta toBranch(String branch) {
    targetBranch(branch);
    return this;
  }

  /**
   * 提交前的冲突校验逻辑，由父类在 commit 时调用。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若有父快照，且设置了起始快照，校验起始快照是父快照的祖先；
   *   <li>若登记了引用数据文件，调用 {@code validateDataFilesExist} 校验这些文件仍存在 （根据 {@code validateDeletes}
   *       决定是否同时校验删除文件）；
   *   <li>若开启 {@code validateNewDataFiles}，校验没有并发新增的数据文件冲突；
   *   <li>若开启 {@code validateNewDeleteFiles}，校验没有并发新增的删除文件冲突。
   * </ul>
   *
   * @param base 当前表元数据基线
   * @param parent 父快照
   */
  @Override
  protected void validate(TableMetadata base, Snapshot parent) {
    if (parent != null) {
      if (startingSnapshotId != null) {
        Preconditions.checkArgument(
            SnapshotUtil.isAncestorOf(parent.snapshotId(), startingSnapshotId, base::snapshot),
            "Snapshot %s is not an ancestor of %s",
            startingSnapshotId,
            parent.snapshotId());
      }
      if (!referencedDataFiles.isEmpty()) {
        validateDataFilesExist(
            base,
            startingSnapshotId,
            referencedDataFiles,
            !validateDeletes,
            conflictDetectionFilter,
            parent);
      }

      if (validateNewDataFiles) {
        validateAddedDataFiles(base, startingSnapshotId, conflictDetectionFilter, parent);
      }

      if (validateNewDeleteFiles) {
        validateNoNewDeleteFiles(base, startingSnapshotId, conflictDetectionFilter, parent);
      }
    }
  }
}
