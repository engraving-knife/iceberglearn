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
 * 增量变更日志扫描任务：表示一次 INSERT 操作中"新增行"对应的 {@link DataFile} 扫描单元。
 *
 * <p>所属模块：iceberg-core（表元数据与扫描计划核心实现层，向 iceberg-api 提供具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载单次 append 提交中新增数据文件及其关联的位置删除（position delete）文件。
 *   <li>实现 {@link AddedRowsScanTask} 接口，向下游读取引擎暴露新增数据与删除文件的视图。
 *   <li>支持文件切分（split），生成可独立下发的 {@link SplitAddedRowsScanTask} 子任务。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>变更日志读取场景下，引擎关心的是"哪些行是新加的"。但同一数据文件可能存在更晚提交的 删除文件，需要把这些删除文件附在任务上以便读取时过滤掉已被删除的行，保证变更语义正确。
 *   <li>沿用 {@link BaseChangelogContentScanTask} 的通用骨架，复用变更序号、提交快照、 schema/spec 序列化等公共字段，避免重复实现。
 * </ul>
 *
 * <p>上下游关系：由 {@code BaseIncrementalChangelogScan} 的扫描计划生成；被引擎层 （Spark/Flink 等）消费用于增量读取 CDC 数据。
 */
class BaseAddedRowsScanTask extends BaseChangelogContentScanTask<AddedRowsScanTask, DataFile>
    implements AddedRowsScanTask {

  private final DeleteFile[] deletes;

  /**
   * 构造一个新增行扫描任务。
   *
   * @param changeOrdinal 该任务在变更序列中的序号（从 1 开始），用于区分不同提交
   * @param commitSnapshotId 产生该数据文件的提交快照 id
   * @param file 新增的数据文件
   * @param deletes 关联的位置删除文件数组；可为 null，内部会转为空数组
   * @param schemaString 任务读取使用的 schema 序列化字符串
   * @param specString 任务对应的分区 spec 序列化字符串
   * @param residuals 残余表达式求值器，用于在读取侧进一步过滤
   */
  BaseAddedRowsScanTask(
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
  protected AddedRowsScanTask self() {
    return this;
  }

  /**
   * 创建一个切分子任务，用于将本任务对应的文件按 (offset, length) 切分下发。
   *
   * @param parentTask 父任务（即本任务）
   * @param offset 切分起始偏移
   * @param length 切分长度
   * @return 切分后的 {@link SplitAddedRowsScanTask}
   */
  @Override
  protected AddedRowsScanTask newSplitTask(AddedRowsScanTask parentTask, long offset, long length) {
    return new SplitAddedRowsScanTask(parentTask, offset, length);
  }

  /**
   * 返回该任务关联的删除文件列表。
   *
   * <p>设计要点：返回不可变副本，避免外部修改内部数组。
   *
   * @return 删除文件不可变列表
   */
  @Override
  public List<DeleteFile> deletes() {
    return ImmutableList.copyOf(deletes);
  }

  /**
   * 切分版新增行扫描任务：携带父任务引用与切分区间，转发删除文件查询。
   *
   * <p>设计意图：避免在切分时复制父任务全部字段，按需委托给父任务，节省内存并保证语义一致。
   */
  private static class SplitAddedRowsScanTask
      extends SplitScanTask<SplitAddedRowsScanTask, AddedRowsScanTask, DataFile>
      implements AddedRowsScanTask {

    SplitAddedRowsScanTask(AddedRowsScanTask parentTask, long offset, long length) {
      super(parentTask, offset, length);
    }

    @Override
    protected SplitAddedRowsScanTask copyWithNewLength(long newLength) {
      return new SplitAddedRowsScanTask(parentTask(), start(), newLength);
    }

    @Override
    public List<DeleteFile> deletes() {
      return parentTask().deletes();
    }
  }
}
