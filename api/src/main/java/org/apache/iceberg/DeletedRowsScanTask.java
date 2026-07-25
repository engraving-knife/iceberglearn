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

/**
 * 文件级说明：表示“删除数据行”的变更日志扫描任务。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>描述由“向表新增删除文件”所产生的删除（DELETE）变更，供增量变更日志扫描消费。
 *   <li>携带被删除行所在的数据文件，以及“新增删除文件”和“既有删除文件”两个列表。
 *   <li>固化变更操作类型为 {@link ChangelogOperation#DELETE}。
 * </ul>
 *
 * <p>设计意图：增量 CDC 读取时需区分“新增删除文件产生的删除行”与“既有删除文件已删除的行”。
 * 前者（addedDeletes）对应的记录应作为删除事件出现在变更日志中；后者（existingDeletes）只是 用于在判断 addedDeletes
 * 实际删除了哪些行之前先过滤已有删除，不应出现在变更日志中。
 *
 * <p>例如快照 S1 含数据文件 F1、F2、F3；快照 S2 新增位置删除文件 D1（删除 F2 中记录）， 快照 S3 新增等值删除文件 D2（删除 F1、F2、F3 中记录）。则 S2 到
 * S3（含）的变更扫描应产出：
 *
 * <ul>
 *   <li>DeletedRowsScanTask(file=F2, added-deletes=[D1], existing-deletes=[], snapshot=S2)
 *   <li>DeletedRowsScanTask(file=F1, added-deletes=[D2], existing-deletes=[], snapshot=S3)
 *   <li>DeletedRowsScanTask(file=F2, added-deletes=[D2], existing-deletes=[D1], snapshot=S3)
 *   <li>DeletedRowsScanTask(file=F3, added-deletes=[D2], existing-deletes=[], snapshot=S3)
 * </ul>
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.IncrementalChangelogScan} 规划产出， 被各引擎 CDC 读取器消费。
 */
public interface DeletedRowsScanTask extends ChangelogScanTask, ContentScanTask<DataFile> {
  /**
   * 返回应用于本任务数据文件的“新增” {@link DeleteFile 删除文件}列表。
   *
   * <p>这些删除文件所删除的记录应作为删除事件出现在变更日志中。
   *
   * @return 新增的删除文件列表
   */
  List<DeleteFile> addedDeletes();

  /**
   * 返回“既有”的 {@link DeleteFile 删除文件}列表。
   *
   * <p>这些删除文件在判断 {@link #addedDeletes()} 实际删除了哪些行之前必须先应用。 它们所删除的记录不应出现在变更日志中（因为这些删除在变更区间之前已发生）。
   *
   * @return 既有的删除文件列表
   */
  List<DeleteFile> existingDeletes();

  /** 固化本任务的变更操作类型为 {@link ChangelogOperation#DELETE}（删除）。 */
  @Override
  default ChangelogOperation operation() {
    return ChangelogOperation.DELETE;
  }

  /**
   * 返回本任务的总字节数：数据文件长度加上新增删除文件与既有删除文件大小之和。
   *
   * @return 任务涉及的字节总数
   */
  @Override
  default long sizeBytes() {
    return length()
        + addedDeletes().stream().mapToLong(ContentFile::fileSizeInBytes).sum()
        + existingDeletes().stream().mapToLong(ContentFile::fileSizeInBytes).sum();
  }

  /** 返回本任务涉及的文件数：1 个数据文件加上新增与既有删除文件数量。 */
  @Override
  default int filesCount() {
    return 1 + addedDeletes().size() + existingDeletes().size();
  }
}
