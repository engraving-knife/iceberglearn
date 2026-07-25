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
package org.apache.iceberg.flink.source.reader;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：基于 List 的批量记录集，用于从 fetcher 向 source reader 传递一批记录。
 *
 * <p>所属模块：iceberg-flink（source/reader 子包），实现 Flink 的 {@link RecordsWithSplitIds} 接口。
 *
 * <p>职责：封装一个 split 的一批记录（List 形式），与 {@link ArrayBatchRecords} 功能类似但使用 List 存储。
 *
 * <p>设计意图：提供不依赖数组池的简单批量实现，适用于不需要数组复用的场景。 文件型 source 一次只读一个 split，故每个 batch 只包含一个 split 的记录。
 *
 * <p>上下游关系：由批量器（batcher）产出；被 FLIP-27 SourceReader 消费。
 *
 * @param <T> 记录类型
 */
class ListBatchRecords<T> implements RecordsWithSplitIds<RecordAndPosition<T>> {
  private String splitId;
  private final List<T> records;
  private final Set<String> finishedSplits;
  private final RecordAndPosition<T> recordAndPosition;

  // point to current read position within the records list
  private int position;

  /**
   * 构造方法。
   *
   * @param splitId split 标识符
   * @param records 记录列表
   * @param fileOffset 文件偏移量
   * @param startingRecordOffset 起始记录偏移量
   * @param finishedSplits 已完成的 split 集合
   */
  ListBatchRecords(
      String splitId,
      List<T> records,
      int fileOffset,
      long startingRecordOffset,
      Set<String> finishedSplits) {
    this.splitId = splitId;
    this.records = records;
    this.finishedSplits =
        Preconditions.checkNotNull(finishedSplits, "finishedSplits can be empty but not null");
    this.recordAndPosition = new RecordAndPosition<>();
    this.recordAndPosition.set(null, fileOffset, startingRecordOffset);

    this.position = 0;
  }

  @Nullable
  @Override
  public String nextSplit() {
    String nextSplit = this.splitId;
    // set the splitId to null to indicate no more splits
    // this class only contains record for one split
    this.splitId = null;
    return nextSplit;
  }

  @Nullable
  @Override
  public RecordAndPosition<T> nextRecordFromSplit() {
    if (position < records.size()) {
      recordAndPosition.record(records.get(position));
      position++;
      return recordAndPosition;
    } else {
      return null;
    }
  }

  @Override
  public Set<String> finishedSplits() {
    return finishedSplits;
  }

  public static <T> ListBatchRecords<T> forRecords(
      String splitId, List<T> records, int fileOffset, long startingRecordOffset) {
    return new ListBatchRecords<>(
        splitId, records, fileOffset, startingRecordOffset, Collections.emptySet());
  }
}
