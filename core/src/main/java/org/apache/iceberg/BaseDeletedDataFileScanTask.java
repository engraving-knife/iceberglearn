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
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 已删除数据文件的变更日志扫描任务实现。
 *
 * <p>所属模块：iceberg-core。属于增量变更日志（changelog）扫描体系，专门用于表达"在某次提交中 被删除的数据文件"这一变更内容。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载被删除的数据文件 {@link DataFile} 及其关联的删除文件（{@link DeleteFile}）。
 *   <li>记录 changeOrdinal 与 commitSnapshotId，用于在变更日志流中定位本次删除的位置。
 *   <li>支持按字节范围切分（split），切分后的子任务共享父任务的删除文件集合。
 * </ul>
 *
 * <p>设计意图：通过继承 {@link BaseChangelogContentScanTask} 复用变更日志任务的通用字段 与切分逻辑；{@link
 * SplitDeletedDataFileScanTask} 是其可切分视图，分离职责以避免 修改父任务状态。{@code existingDeletes}
 * 在切分任务中委托给父任务，保证子任务与父任务 看到的删除文件集合一致。
 *
 * <p>上下游关系：由 {@link BaseIncrementalChangelogScan} 在解析增量删除事件时构造， 被引擎层用于消费 changelog。
 */
class BaseDeletedDataFileScanTask
    extends BaseChangelogContentScanTask<DeletedDataFileScanTask, DataFile>
    implements DeletedDataFileScanTask {

  private final DeleteFile[] deletes;

  /**
   * 构造一个已删除数据文件扫描任务。
   *
   * @param changeOrdinal 变更序号，标识该删除在变更流中的顺序
   * @param commitSnapshotId 产生该删除的提交快照 ID
   * @param file 被删除的数据文件
   * @param deletes 关联的 equality 删除文件集合，可为 null（将转为空数组）
   * @param schemaString 表 schema 的序列化字符串
   * @param specString 分区规则的序列化字符串
   * @param residuals 残留谓词评估器
   */
  BaseDeletedDataFileScanTask(
      int changeOrdinal,
      long commitSnapshotId,
      DataFile file,
      DeleteFile[] deletes,
      String schemaString,
      String specString,
      ResidualEvaluator residuals) {
    super(changeOrdinal, commitSnapshotId, file, schemaString, specString, residuals);
    this.deletes = deletes != null ? deletes : new DeleteFile[0];
  }

  @Override
  protected DeletedDataFileScanTask self() {
    return this;
  }

  /**
   * 创建一个切分子任务，覆盖父任务中指定偏移与长度的字节区间。
   *
   * @param parentTask 父任务
   * @param offset 切分起始字节偏移
   * @param length 切分长度
   * @return 新的子任务实例
   */
  @Override
  protected DeletedDataFileScanTask newSplitTask(
      DeletedDataFileScanTask parentTask, long offset, long length) {
    return new SplitDeletedDataFileScanTask(parentTask, offset, length);
  }

  /**
   * 返回与该数据文件关联的删除文件（已删除文件本身的 equality deletes）。
   *
   * @return 不可变的删除文件列表
   */
  @Override
  public List<DeleteFile> existingDeletes() {
    return ImmutableList.copyOf(deletes);
  }

  /**
   * 切分后的已删除数据文件扫描任务：仅记录字节范围，删除文件集合委托父任务。
   *
   * <p>设计要点：切分任务不复制删除文件集合，避免内存浪费；所有访问都通过 {@link #parentTask()} 回到原任务，保证一致性。
   */
  private static class SplitDeletedDataFileScanTask
      extends SplitScanTask<SplitDeletedDataFileScanTask, DeletedDataFileScanTask, DataFile>
      implements DeletedDataFileScanTask {

    SplitDeletedDataFileScanTask(DeletedDataFileScanTask parentTask, long offset, long length) {
      super(parentTask, offset, length);
    }

    @Override
    protected SplitDeletedDataFileScanTask copyWithNewLength(long newLength) {
      return new SplitDeletedDataFileScanTask(parentTask(), start(), newLength);
    }

    @Override
    public List<DeleteFile> existingDeletes() {
      return parentTask().existingDeletes();
    }
  }
}
