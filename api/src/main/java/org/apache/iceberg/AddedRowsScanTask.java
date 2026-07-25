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
 * 文件级说明：表示“新增数据行”的变更日志扫描任务。
 *
 * <p>所属模块：iceberg-api（最核心的接口层，定义表/快照/扫描/更新等抽象契约，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>描述由“向表新增一个数据文件”所产生的插入（INSERT）变更，供增量变更日志扫描消费。
 *   <li>携带该新增数据文件，以及可选的、需在读取时一并应用的删除文件列表。
 *   <li>固化变更操作类型为 {@link ChangelogOperation#INSERT}，便于下游统一识别插入事件。
 * </ul>
 *
 * <p>设计意图：增量 CDC 读取时，需要区分“插入行”与“删除行”两类事件，分别由 {@code AddedRowsScanTask} 与 {@code
 * DeletedRowsScanTask} 承载，从而让读取器能够产出 带有 change ordinal、commit snapshot ID 等元数据的变更记录。
 *
 * <p>注意：新增的数据文件可能同时存在匹配的删除文件。这种情况出现在同一快照内同时提交了 位置删除文件，或在压缩多个快照变更时被合并到一起。例如快照 S1 新增数据文件 F1、F2、F3，
 * 并新增一个位置删除文件 D1（删除 F1 中部分记录），则该快照的变更扫描应产出如下任务：
 *
 * <ul>
 *   <li>AddedRowsScanTask(file=F1, deletes=[D1], snapshot=S1)
 *   <li>AddedRowsScanTask(file=F2, deletes=[], snapshot=S1)
 *   <li>AddedRowsScanTask(file=F3, deletes=[], snapshot=S1)
 * </ul>
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.IncrementalChangelogScan} 规划产出， 被各引擎（Spark/Flink 等）的 CDC
 * 读取器消费。
 */
public interface AddedRowsScanTask extends ChangelogScanTask, ContentScanTask<DataFile> {
  /**
   * 返回读取本任务数据文件时需要应用的 {@link DeleteFile 删除文件}列表。
   *
   * <p>这些删除文件可能在同一快照内新增（例如位置删除文件），或由多快照变更压缩而来。 读取器应在产出插入记录前先应用这些删除文件，以保证不输出已被删除的行。
   *
   * @return 需应用的删除文件列表，可能为空
   */
  List<DeleteFile> deletes();

  /** 固化本任务的变更操作类型为 {@link ChangelogOperation#INSERT}（插入）。 */
  @Override
  default ChangelogOperation operation() {
    return ChangelogOperation.INSERT;
  }

  /**
   * 返回本任务的总字节数：数据文件长度加上所有需应用的删除文件大小之和。
   *
   * @return 任务涉及的字节总数
   */
  @Override
  default long sizeBytes() {
    return length() + deletes().stream().mapToLong(ContentFile::fileSizeInBytes).sum();
  }

  /** 返回本任务涉及的文件数：1 个数据文件加上需应用的删除文件数量。 */
  @Override
  default int filesCount() {
    return 1 + deletes().size();
  }
}
