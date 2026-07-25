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
package org.apache.iceberg.flink.sink;

import java.util.Arrays;
import java.util.NavigableMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.iceberg.io.WriteResult;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * 文件级说明：Flink sink 提交结果的统计摘要。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：聚合一次 checkpoint 提交中所有待提交 {@link WriteResult} 的数据文件与删除文件 的数量、记录数和字节数，提供统一的查询接口供提交日志与指标使用。
 *
 * <p>设计意图：使用 {@link AtomicLong} 保证并发累加安全； 通过 NavigableMap 入参保持提交顺序，便于按 checkpoint ID 顺序统计。
 *
 * <p>上下游关系：上游为 {@code IcebergFilesCommitter} 的待提交结果， 下游为提交日志输出与指标上报。
 */
class CommitSummary {

  private final AtomicLong dataFilesCount = new AtomicLong();
  private final AtomicLong dataFilesRecordCount = new AtomicLong();
  private final AtomicLong dataFilesByteCount = new AtomicLong();
  private final AtomicLong deleteFilesCount = new AtomicLong();
  private final AtomicLong deleteFilesRecordCount = new AtomicLong();
  private final AtomicLong deleteFilesByteCount = new AtomicLong();

  /** 构造摘要，遍历所有待提交结果累加各类统计指标。 */
  CommitSummary(NavigableMap<Long, WriteResult> pendingResults) {
    pendingResults
        .values()
        .forEach(
            writeResult -> {
              dataFilesCount.addAndGet(writeResult.dataFiles().length);
              Arrays.stream(writeResult.dataFiles())
                  .forEach(
                      dataFile -> {
                        dataFilesRecordCount.addAndGet(dataFile.recordCount());
                        dataFilesByteCount.addAndGet(dataFile.fileSizeInBytes());
                      });
              deleteFilesCount.addAndGet(writeResult.deleteFiles().length);
              Arrays.stream(writeResult.deleteFiles())
                  .forEach(
                      deleteFile -> {
                        deleteFilesRecordCount.addAndGet(deleteFile.recordCount());
                        deleteFilesByteCount.addAndGet(deleteFile.fileSizeInBytes());
                      });
            });
  }

  /** 返回数据文件总数。 */
  long dataFilesCount() {
    return dataFilesCount.get();
  }

  /** 返回数据文件包含的记录总数。 */
  long dataFilesRecordCount() {
    return dataFilesRecordCount.get();
  }

  /** 返回数据文件总字节数。 */
  long dataFilesByteCount() {
    return dataFilesByteCount.get();
  }

  /** 返回删除文件总数。 */
  long deleteFilesCount() {
    return deleteFilesCount.get();
  }

  /** 返回删除文件包含的记录总数。 */
  long deleteFilesRecordCount() {
    return deleteFilesRecordCount.get();
  }

  /** 返回删除文件总字节数。 */
  long deleteFilesByteCount() {
    return deleteFilesByteCount.get();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dataFilesCount", dataFilesCount)
        .add("dataFilesRecordCount", dataFilesRecordCount)
        .add("dataFilesByteCount", dataFilesByteCount)
        .add("deleteFilesCount", deleteFilesCount)
        .add("deleteFilesRecordCount", deleteFilesRecordCount)
        .add("deleteFilesByteCount", deleteFilesByteCount)
        .toString();
  }
}
