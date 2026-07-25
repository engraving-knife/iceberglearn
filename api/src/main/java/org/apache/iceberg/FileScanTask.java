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
 * 文件级说明：针对单个数据文件中一段字节范围的扫描任务。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>表示对一个数据文件的扫描任务，可被拆分为更小的字节范围子任务（实现 {@link SplittableScanTask}）。
 *   <li>携带读取该数据文件时需应用的删除文件列表。
 *   <li>提供扫描所用的 {@link Schema}（可选）。
 * </ul>
 *
 * <p>设计意图：将“数据文件 + 删除文件 + 字节范围”组合为一个最小扫描单元，便于引擎按 task 粒度 并行读取。可拆分特性允许大文件被多个 task 分段处理，提升并行度。
 *
 * <p>上下游关系：由 {@link TableScan} 规划产出，常被组合为 {@link CombinedScanTask}； 被引擎读取器消费。
 */
public interface FileScanTask extends ContentScanTask<DataFile>, SplittableScanTask<FileScanTask> {
  /**
   * 返回读取本任务数据文件时需要应用的 {@link DeleteFile 删除文件}列表。
   *
   * <p>读取器应在产出数据行前先应用这些删除文件，过滤掉被删除的行。
   *
   * @return 需应用的删除文件列表，可能为空
   */
  List<DeleteFile> deletes();

  /**
   * 返回本文件扫描任务所使用的 {@link Schema}。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，表示不支持 schema 查询。
   *
   * @return 扫描使用的 schema
   */
  default Schema schema() {
    throw new UnsupportedOperationException("Does not support schema getter");
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

  /** 标识本任务为 {@link FileScanTask} 类型，默认返回 true。 */
  @Override
  default boolean isFileScanTask() {
    return true;
  }

  /** 将本任务作为 {@link FileScanTask} 返回（类型转换辅助方法），默认返回自身。 */
  @Override
  default FileScanTask asFileScanTask() {
    return this;
  }
}
