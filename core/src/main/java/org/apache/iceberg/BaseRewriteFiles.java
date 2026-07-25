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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 文件重写（rewrite）操作的实现：原子性地删除旧文件并添加新文件。
 *
 * <p>所属模块：iceberg-core（表变更操作核心实现层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link RewriteFiles} 接口，支持同时删除数据文件/删除文件并新增等价文件。
 *   <li>对替换的数据文件做并发校验：确保替换期间没有其他提交对这些文件新增行级删除。
 *   <li>支持指定数据序列号、目标分支、校验起点快照等高级选项。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>rewrite 本质是 delete + add 的原子组合，复用 {@link MergingSnapshotProducer} 的合并 快照机制，避免重复实现 manifest
 *       写入与提交逻辑。
 *   <li>构造时调用 {@link #failMissingDeletePaths()} 强制要求被删文件必须存在，防止 rewrite 把已不存在的文件"删除"，掩盖潜在的一致性问题。
 *   <li>区分 replacedDataFiles（被替换的数据文件）与普通删除，便于在 validate 阶段对前者 做更严格的并发删除校验。
 * </ul>
 *
 * <p>上下游关系：继承 {@link MergingSnapshotProducer}；被 {@code RewriteFilesAction} 等 维护类操作调用，常用于
 * compaction、sort、加密转换等场景。
 */
class BaseRewriteFiles extends MergingSnapshotProducer<RewriteFiles> implements RewriteFiles {
  private final Set<DataFile> replacedDataFiles = Sets.newHashSet();
  private Long startingSnapshotId = null;

  /**
   * 构造 rewrite 操作实例。
   *
   * <p>设计要点：调用 {@link #failMissingDeletePaths()} 启用"删除路径必须存在"的强制校验， 因为 rewrite
   * 场景下被删文件本应存在，若不存在则说明元数据与底层存储不一致。
   *
   * @param tableName 表名
   * @param ops 表操作对象
   */
  BaseRewriteFiles(String tableName, TableOperations ops) {
    super(tableName, ops);

    // replace files must fail if any of the deleted paths is missing and cannot be deleted
    failMissingDeletePaths();
  }

  @Override
  protected RewriteFiles self() {
    return this;
  }

  /** 返回操作类型标识，rewrite 用 {@link DataOperations#REPLACE}。 */
  @Override
  protected String operation() {
    return DataOperations.REPLACE;
  }

  /**
   * 标记一个待替换的数据文件（删除 + 记录到 replacedDataFiles 集合用于后续校验）。
   *
   * @param dataFile 被替换的数据文件
   * @return 当前 builder，便于链式调用
   */
  @Override
  public RewriteFiles deleteFile(DataFile dataFile) {
    replacedDataFiles.add(dataFile);
    delete(dataFile);
    return self();
  }

  /**
   * 标记一个待删除的删除文件（仅删除，不计入 replacedDataFiles）。
   *
   * @param deleteFile 被删除的删除文件
   * @return 当前 builder
   */
  @Override
  public RewriteFiles deleteFile(DeleteFile deleteFile) {
    delete(deleteFile);
    return self();
  }

  /**
   * 添加一个新数据文件。
   *
   * @param dataFile 新数据文件
   * @return 当前 builder
   */
  @Override
  public RewriteFiles addFile(DataFile dataFile) {
    add(dataFile);
    return self();
  }

  /**
   * 添加一个新删除文件。
   *
   * @param deleteFile 新删除文件
   * @return 当前 builder
   */
  @Override
  public RewriteFiles addFile(DeleteFile deleteFile) {
    add(deleteFile);
    return self();
  }

  /**
   * 添加一个新删除文件并显式指定其数据序列号。
   *
   * <p>使用场景：rewrite 产生的删除文件需要绑定到某个特定的数据序列号（例如来源数据文件的 序列号），以便在读取时正确决定删除文件的可见性。
   *
   * @param deleteFile 新删除文件
   * @param dataSequenceNumber 数据序列号
   * @return 当前 builder
   */
  @Override
  public RewriteFiles addFile(DeleteFile deleteFile, long dataSequenceNumber) {
    add(deleteFile, dataSequenceNumber);
    return self();
  }

  /**
   * 设置新增数据文件的数据序列号。
   *
   * @param sequenceNumber 数据序列号
   * @return 当前 builder
   */
  @Override
  public RewriteFiles dataSequenceNumber(long sequenceNumber) {
    setNewDataFilesDataSequenceNumber(sequenceNumber);
    return self();
  }

  /**
   * 便捷方法：仅替换数据文件（不涉及删除文件），并指定新文件的数据序列号。
   *
   * @param filesToDelete 待删除数据文件集合
   * @param filesToAdd 待添加数据文件集合
   * @param sequenceNumber 新文件数据序列号
   * @return 当前 builder
   */
  @Override
  public RewriteFiles rewriteFiles(
      Set<DataFile> filesToDelete, Set<DataFile> filesToAdd, long sequenceNumber) {
    setNewDataFilesDataSequenceNumber(sequenceNumber);
    return rewriteFiles(filesToDelete, ImmutableSet.of(), filesToAdd, ImmutableSet.of());
  }

  /**
   * 通用 rewrite 入口：同时替换数据文件和删除文件。
   *
   * <p>逻辑：校验四个集合非 null，然后依次把待删文件加入删除集合、待加文件加入新增集合。 不直接构造删除/新增关系，由 manifest 合并阶段处理。
   *
   * @param dataFilesToReplace 待替换的数据文件集合
   * @param deleteFilesToReplace 待替换的删除文件集合
   * @param dataFilesToAdd 新增的数据文件集合
   * @param deleteFilesToAdd 新增的删除文件集合
   * @return 当前 builder
   */
  @Override
  public RewriteFiles rewriteFiles(
      Set<DataFile> dataFilesToReplace,
      Set<DeleteFile> deleteFilesToReplace,
      Set<DataFile> dataFilesToAdd,
      Set<DeleteFile> deleteFilesToAdd) {

    Preconditions.checkNotNull(dataFilesToReplace, "Replaced data files can't be null");
    Preconditions.checkNotNull(deleteFilesToReplace, "Replaced delete files can't be null");
    Preconditions.checkNotNull(dataFilesToAdd, "Added data files can't be null");
    Preconditions.checkNotNull(deleteFilesToAdd, "Added delete files can't be null");

    for (DataFile dataFile : dataFilesToReplace) {
      deleteFile(dataFile);
    }

    for (DeleteFile deleteFile : deleteFilesToReplace) {
      deleteFile(deleteFile);
    }

    for (DataFile dataFile : dataFilesToAdd) {
      addFile(dataFile);
    }

    for (DeleteFile deleteFile : deleteFilesToAdd) {
      addFile(deleteFile);
    }

    return this;
  }

  /**
   * 设置校验起点快照：基于该快照之后的提交做并发删除校验。
   *
   * @param snapshotId 起点快照 id
   * @return 当前 builder
   */
  @Override
  public RewriteFiles validateFromSnapshot(long snapshotId) {
    this.startingSnapshotId = snapshotId;
    return this;
  }

  /**
   * 指定目标分支，将 rewrite 应用到该分支而非主分支。
   *
   * @param branch 分支名
   * @return 当前 builder
   */
  @Override
  public BaseRewriteFiles toBranch(String branch) {
    targetBranch(branch);
    return this;
  }

  /**
   * 提交前校验：保证替换语义合法且无并发冲突。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>调用 {@link #validateReplacedAndAddedFiles()} 检查删除/新增文件集合的语义一致性。
   *   <li>若替换了数据文件，调用 {@link #validateNoNewDeletesForDataFiles} 校验自起点快照以来 没有其他提交对这些数据文件新增行级删除；若有则本次
   *       rewrite 已过期，需重试。
   * </ol>
   *
   * @param base 提交前的表元数据
   * @param parent 父快照
   */
  @Override
  protected void validate(TableMetadata base, Snapshot parent) {
    validateReplacedAndAddedFiles();
    if (replacedDataFiles.size() > 0) {
      // if there are replaced data files, there cannot be any new row-level deletes for those data
      // files
      validateNoNewDeletesForDataFiles(base, startingSnapshotId, replacedDataFiles, parent);
    }
  }

  /**
   * 校验替换与新增文件集合的语义一致性。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>必须至少删除一个数据文件或删除文件（rewrite 不能为空操作）。
   *   <li>若未删除数据文件，则不能新增数据文件（没有数据文件被 rewrite 时新增数据文件无意义）。
   *   <li>若未删除删除文件，则不能新增删除文件。
   * </ul>
   */
  private void validateReplacedAndAddedFiles() {
    Preconditions.checkArgument(
        deletesDataFiles() || deletesDeleteFiles(), "Files to delete cannot be empty");

    Preconditions.checkArgument(
        deletesDataFiles() || !addsDataFiles(),
        "Data files to add must be empty because there's no data file to be rewritten");

    Preconditions.checkArgument(
        deletesDeleteFiles() || !addsDeleteFiles(),
        "Delete files to add must be empty because there's no delete file to be rewritten");
  }
}
