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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 合并追加实现：在追加文件的同时触发 manifest 合并，产出尽量少的 manifest 文件。
 *
 * <p>所属模块：iceberg-core（快照生产层，实现 {@link AppendFiles} 接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>追加数据文件或已有 manifest 到本次提交；
 *   <li>在提交时按分区大小阈值触发 manifest 合并（compaction），减少 manifest 数量。
 * </ul>
 *
 * <p>设计意图：与 {@link FastAppend} 相对，MergeAppend 适合大批量追加，通过合并 manifest 避免 manifest 数量膨胀。合并逻辑由父类 {@link
 * MergingSnapshotProducer} 提供。提交会重试最多 5 次。
 *
 * <p>上下游关系：由表 API（{@code table.newAppend()}）构造；底层依赖 {@link MergingSnapshotProducer} 的合并与提交框架。
 */
class MergeAppend extends MergingSnapshotProducer<AppendFiles> implements AppendFiles {
  /**
   * 构造一个合并追加操作。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   */
  MergeAppend(String tableName, TableOperations ops) {
    super(tableName, ops);
  }

  /** 返回自身，用于泛型 self() 模式。 */
  @Override
  protected AppendFiles self() {
    return this;
  }

  /** 返回该操作对应的快照操作类型，追加为 APPEND。 */
  @Override
  protected String operation() {
    return DataOperations.APPEND;
  }

  /**
   * 追加一个数据文件到本次提交。
   *
   * @param file 待追加的数据文件
   * @return 当前操作
   */
  @Override
  public MergeAppend appendFile(DataFile file) {
    add(file);
    return this;
  }

  /**
   * 指定提交目标分支。
   *
   * @param branch 分支名
   * @return 当前操作
   */
  @Override
  public MergeAppend toBranch(String branch) {
    targetBranch(branch);
    return this;
  }

  /**
   * 追加一个已有 manifest 到本次提交。
   *
   * <p>逻辑：校验 manifest 不含 existing/deleted 文件、snapshotId 与 sequenceNumber 未分配， 然后委托父类 {@code add}
   * 处理（会在提交时参与合并）。
   *
   * @param manifest 待追加的 manifest
   * @return 当前操作
   */
  @Override
  public AppendFiles appendManifest(ManifestFile manifest) {
    Preconditions.checkArgument(
        !manifest.hasExistingFiles(), "Cannot append manifest with existing files");
    Preconditions.checkArgument(
        !manifest.hasDeletedFiles(), "Cannot append manifest with deleted files");
    Preconditions.checkArgument(
        manifest.snapshotId() == null || manifest.snapshotId() == -1,
        "Snapshot id must be assigned during commit");
    Preconditions.checkArgument(
        manifest.sequenceNumber() == -1, "Sequence must be assigned during commit");
    add(manifest);
    return this;
  }
}
