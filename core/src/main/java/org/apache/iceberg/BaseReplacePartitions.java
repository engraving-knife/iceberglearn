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
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.util.PartitionSet;

/**
 * 动态分区覆盖（dynamic partition overwrite）的 core 实现：用新数据文件替换指定分区中的既有数据。
 *
 * <p>所属模块：iceberg-core，实现 {@link ReplacePartitions} 接口，是 Iceberg "INSERT OVERWRITE" 语义的底层执行器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>收集待替换的分区集合 {@code replacedPartitions}，提交时按分区删除旧数据。
 *   <li>支持可选的数据/删除冲突校验，确保覆盖不会破坏并发提交的兼容性。
 *   <li>对未分区表，将覆盖转换为"删除全部数据 + 追加新文件"。
 *   <li>支持分支提交（{@link #toBranch(String)}）。
 * </ul>
 *
 * <p>设计意图：继承 {@link MergingSnapshotProducer} 复用"合并 manifest + 删除/追加"的能力， 通过 {@link #operation()} 返回
 * {@link DataOperations#OVERWRITE} 标识操作类型。 {@code validateConflictingData}/{@code
 * validateConflictingDeletes} 默认关闭， 调用方按需开启以在 commit 前做冲突校验，遵循 Iceberg 的乐观提交 + 校验模式。
 *
 * <p>上下游关系：由 {@link BaseTransaction#newReplacePartitions()} 或表 API 创建； 提交结果经 {@link
 * SnapshotProducer} 写入新快照。
 */
public class BaseReplacePartitions extends MergingSnapshotProducer<ReplacePartitions>
    implements ReplacePartitions {

  private final PartitionSet replacedPartitions;
  private Long startingSnapshotId;
  private boolean validateConflictingData = false;
  private boolean validateConflictingDeletes = false;

  /**
   * 构造分区覆盖操作。
   *
   * <p>逻辑：在快照摘要中标记 {@code replace-partitions=true}，并按当前表所有 spec 创建 {@link PartitionSet} 用于记录被替换的分区。
   *
   * @param tableName 表名
   * @param ops 表操作句柄
   */
  BaseReplacePartitions(String tableName, TableOperations ops) {
    super(tableName, ops);
    set(SnapshotSummary.REPLACE_PARTITIONS_PROP, "true");
    replacedPartitions = PartitionSet.create(ops.current().specsById());
  }

  /** 自类型返回自身。 */
  @Override
  protected ReplacePartitions self() {
    return this;
  }

  /** 返回操作类型标识，覆盖场景为 {@link DataOperations#OVERWRITE}。 */
  @Override
  protected String operation() {
    return DataOperations.OVERWRITE;
  }

  /**
   * 添加一个数据文件，并标记其所在分区为待替换。
   *
   * <p>逻辑：先 {@link #dropPartition(int, StructLike)} 删除该分区旧数据，再把分区加入 {@code replacedPartitions}，最后
   * {@link #add(DataFile)} 加入新文件。
   *
   * @param file 待添加的数据文件
   * @return this，便于链式调用
   */
  @Override
  public ReplacePartitions addFile(DataFile file) {
    dropPartition(file.specId(), file.partition());
    replacedPartitions.add(file.specId(), file.partition());
    add(file);
    return this;
  }

  /**
   * 声明本次覆盖为仅追加校验模式：任何删除均视为冲突。
   *
   * <p>逻辑：调用 {@link #failAnyDelete()}，使校验阶段一旦发现删除即抛出异常。
   *
   * @return this
   */
  @Override
  public ReplacePartitions validateAppendOnly() {
    failAnyDelete();
    return this;
  }

  /**
   * 设置校验基准快照 ID，冲突校验将以此为起点比较后续变更。
   *
   * @param newStartingSnapshotId 起始快照 ID
   * @return this
   */
  @Override
  public ReplacePartitions validateFromSnapshot(long newStartingSnapshotId) {
    this.startingSnapshotId = newStartingSnapshotId;
    return this;
  }

  /**
   * 开启"无冲突删除"校验：提交前检查是否存在并发删除导致冲突。
   *
   * @return this
   */
  @Override
  public ReplacePartitions validateNoConflictingDeletes() {
    this.validateConflictingDeletes = true;
    return this;
  }

  /**
   * 开启"无冲突数据"校验：提交前检查是否存在并发追加/删除导致数据冲突。
   *
   * @return this
   */
  @Override
  public ReplacePartitions validateNoConflictingData() {
    this.validateConflictingData = true;
    return this;
  }

  /**
   * 将本次操作切换到指定分支提交。
   *
   * @param branch 目标分支名
   * @return this
   */
  @Override
  public BaseReplacePartitions toBranch(String branch) {
    targetBranch(branch);
    return this;
  }

  /**
   * 提交前校验：依据开启的校验项，针对被替换分区检查数据/删除冲突。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若开启 {@code validateConflictingData}：未分区表用 {@link Expressions#alwaysTrue()} 全表校验，分区表按
   *       {@code replacedPartitions} 校验新增数据文件冲突。
   *   <li>若开启 {@code validateConflictingDeletes}：分别校验已删除数据文件冲突与新增删除文件冲突， 同样区分未分区/分区两种情况。
   * </ul>
   *
   * @param currentMetadata 当前表元数据
   * @param parent 父快照
   */
  @Override
  public void validate(TableMetadata currentMetadata, Snapshot parent) {
    if (validateConflictingData) {
      if (dataSpec().isUnpartitioned()) {
        validateAddedDataFiles(
            currentMetadata, startingSnapshotId, Expressions.alwaysTrue(), parent);
      } else {
        validateAddedDataFiles(currentMetadata, startingSnapshotId, replacedPartitions, parent);
      }
    }

    if (validateConflictingDeletes) {
      if (dataSpec().isUnpartitioned()) {
        validateDeletedDataFiles(
            currentMetadata, startingSnapshotId, Expressions.alwaysTrue(), parent);
        validateNoNewDeleteFiles(
            currentMetadata, startingSnapshotId, Expressions.alwaysTrue(), parent);
      } else {
        validateDeletedDataFiles(currentMetadata, startingSnapshotId, replacedPartitions, parent);
        validateNoNewDeleteFiles(currentMetadata, startingSnapshotId, replacedPartitions, parent);
      }
    }
  }

  /**
   * 计算应用后的 manifest 列表：未分区表先标记删除全部数据，再委托父类合并。
   *
   * <p>逻辑：当分区 spec 字段数为 0（未分区表）时，调用 {@link #deleteByRowFilter(Expression)} 以 {@code alwaysTrue()}
   * 删除所有行；随后调用 {@code super.apply} 执行 manifest 合并。 若删除过程中抛出 {@link
   * ManifestFilterManager.DeleteException}（删除了被替换分区以外的数据）， 包装为 {@link ValidationException} 提示分区冲突。
   *
   * @param base 基线元数据
   * @param snapshot 当前快照
   * @return 提交所需的 manifest 文件列表
   * @throws ValidationException 当文件与既有分区冲突时
   */
  @Override
  public List<ManifestFile> apply(TableMetadata base, Snapshot snapshot) {
    if (dataSpec().fields().size() <= 0) {
      // replace all data in an unpartitioned table
      deleteByRowFilter(Expressions.alwaysTrue());
    }

    try {
      return super.apply(base, snapshot);
    } catch (ManifestFilterManager.DeleteException e) {
      throw new ValidationException(
          "Cannot commit file that conflicts with existing partition: %s", e.partition());
    }
  }
}
