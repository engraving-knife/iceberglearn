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
 * 增量变更日志扫描任务：表示一次 DELETE 操作中"被删除行"对应的 {@link DataFile} 扫描单元。
 *
 * <p>所属模块：iceberg-core（表元数据与扫描计划核心实现层，向 iceberg-api 提供具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载一次提交中"对历史数据文件执行删除"所涉及的删除文件。
 *   <li>区分"本次新增的删除文件"（addedDeletes）和"历史已存在的删除文件"（existingDeletes）， 以便下游精确还原删除操作发生时机。
 *   <li>支持文件切分（split），生成 {@link SplitDeletedRowsScanTask} 子任务。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>变更日志语义下，引擎需要知道哪些行的删除是"本次提交产生的"。但同一个数据文件可能 既被本次新增的删除文件影响，又受历史删除文件影响，因此必须把两组删除文件分别返回。
 *   <li>addedDeletes 用于确定"本次删除了哪些行"（CDC 输出的 delete 事件）； existingDeletes 用于读取时保证剩余行视图正确（即不被已删行干扰）。
 * </ul>
 *
 * <p>上下游关系：由 {@code BaseIncrementalChangelogScan} 扫描计划生成；被引擎层消费， 用于增量读取并输出删除事件。
 */
class BaseDeletedRowsScanTask extends BaseChangelogContentScanTask<DeletedRowsScanTask, DataFile>
    implements DeletedRowsScanTask {

  private final DeleteFile[] addedDeletes;
  private final DeleteFile[] existingDeletes;

  /**
   * 构造一个删除行扫描任务。
   *
   * @param changeOrdinal 该任务在变更序列中的序号（从 1 开始）
   * @param commitSnapshotId 产生该任务的提交快照 id
   * @param file 被删除行所属的数据文件
   * @param addedDeletes 本次提交新增的删除文件数组；可为 null，内部会转为空数组
   * @param existingDeletes 历史已存在的删除文件数组；可为 null，内部会转为空数组
   * @param schemaString 任务读取使用的 schema 序列化字符串
   * @param specString 任务对应的分区 spec 序列化字符串
   * @param residuals 残余表达式求值器
   */
  BaseDeletedRowsScanTask(
      int changeOrdinal,
      long commitSnapshotId,
      DataFile file,
      DeleteFile[] addedDeletes,
      DeleteFile[] existingDeletes,
      String schemaString,
      String specString,
      ResidualEvaluator residuals) {
    super(changeOrdinal, commitSnapshotId, file, schemaString, specString, residuals);
    this.addedDeletes = addedDeletes != null ? addedDeletes : new DeleteFile[0];
    this.existingDeletes = existingDeletes != null ? existingDeletes : new DeleteFile[0];
  }

  @Override
  protected DeletedRowsScanTask self() {
    return this;
  }

  /**
   * 创建切分子任务，按 (offset, length) 切分本任务对应的数据文件。
   *
   * @param parentTask 父任务（即本任务）
   * @param offset 切分起始偏移
   * @param length 切分长度
   * @return 切分后的 {@link SplitDeletedRowsScanTask}
   */
  @Override
  protected DeletedRowsScanTask newSplitTask(
      DeletedRowsScanTask parentTask, long offset, long length) {
    return new SplitDeletedRowsScanTask(parentTask, offset, length);
  }

  /**
   * 返回本次提交新增的删除文件列表。
   *
   * <p>设计要点：返回不可变副本，避免外部修改内部数组。
   *
   * @return 新增删除文件不可变列表
   */
  @Override
  public List<DeleteFile> addedDeletes() {
    return ImmutableList.copyOf(addedDeletes);
  }

  /**
   * 返回历史已存在的删除文件列表。
   *
   * @return 历史删除文件不可变列表
   */
  @Override
  public List<DeleteFile> existingDeletes() {
    return ImmutableList.copyOf(existingDeletes);
  }

  /**
   * 切分版删除行扫描任务：携带父任务引用与切分区间，转发删除文件查询。
   *
   * <p>设计意图：与新增行切分任务对称，按需委托给父任务以避免字段重复复制。
   */
  private static class SplitDeletedRowsScanTask
      extends SplitScanTask<SplitDeletedRowsScanTask, DeletedRowsScanTask, DataFile>
      implements DeletedRowsScanTask {

    SplitDeletedRowsScanTask(DeletedRowsScanTask parentTask, long offset, long length) {
      super(parentTask, offset, length);
    }

    @Override
    protected SplitDeletedRowsScanTask copyWithNewLength(long newLength) {
      return new SplitDeletedRowsScanTask(parentTask(), start(), newLength);
    }

    @Override
    public List<DeleteFile> addedDeletes() {
      return parentTask().addedDeletes();
    }

    @Override
    public List<DeleteFile> existingDeletes() {
      return parentTask().existingDeletes();
    }
  }
}
