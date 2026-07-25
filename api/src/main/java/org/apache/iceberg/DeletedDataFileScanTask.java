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
 * 因数据文件被移除而产生的删除类变更日志扫描任务。
 *
 * <p>所属模块：iceberg-api（变更日志扫描任务抽象层）。
 *
 * <p>职责：在增量/变更日志扫描中表示"某个数据文件被整体删除"这一事件，并附带读取该数据 文件时需应用的历史删除文件，使消费方能够还原出"文件被移除瞬间仍存活的记录"。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>所有更早添加的 delete 文件必须一并应用。这是为了只输出在该数据文件被移除时仍然 存活的记录。
 *   <li>示例：快照 S1 含数据文件 F1、F2、F3；S2 添加位置删除文件 D1（删除 F2 中部分记录）； S3 完全移除 F2。则对 S3 产生的变更进行扫描时应包含任务
 *       DeletedDataFileScanTask(file=F2, existing-deletes=[D1], snapshot=S3)。
 * </ul>
 *
 * <p>上下游关系：实现 {@link ChangelogScanTask} 与 {@link ContentScanTask}；由增量扫描规划
 * 阶段生成，被引擎消费方读取以产出带有变更序号、提交快照 ID 等元数据的删除记录。
 */
public interface DeletedDataFileScanTask extends ChangelogScanTask, ContentScanTask<DataFile> {
  /**
   * 返回读取本任务数据文件时需要应用的历史 {@link DeleteFile} 列表。
   *
   * @return 需应用的删除文件列表
   */
  List<DeleteFile> existingDeletes();

  /** 默认实现：本任务对应的变更操作恒为 {@link ChangelogOperation#DELETE}。 */
  @Override
  default ChangelogOperation operation() {
    return ChangelogOperation.DELETE;
  }

  /**
   * 默认实现：本任务字节数 = 数据文件长度 + 所有 existingDeletes 文件大小之和。
   *
   * @return 任务总字节数
   */
  @Override
  default long sizeBytes() {
    return length() + existingDeletes().stream().mapToLong(ContentFile::fileSizeInBytes).sum();
  }

  /**
   * 默认实现：本任务文件数 = 1（数据文件）+ existingDeletes 数量。
   *
   * @return 任务总文件数
   */
  @Override
  default int filesCount() {
    return 1 + existingDeletes().size();
  }
}
