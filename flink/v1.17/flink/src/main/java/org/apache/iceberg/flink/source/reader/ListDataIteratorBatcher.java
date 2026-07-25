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

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.iceberg.flink.FlinkConfigOptions;
import org.apache.iceberg.flink.source.DataIterator;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 使用 ArrayList 缓存批次的 DataIterator 批处理器。
 *
 * <p>所属模块：iceberg-flink（source reader 侧），实现 {@link DataIteratorBatcher}。
 *
 * <p>职责：将 DataIterator 的记录按批次大小打包为 {@link RecordsWithSplitIds}，每批创建新的 ArrayList。
 *
 * <p>设计意图：当上游 reader 函数已对记录做了克隆（如 FlinkRecordReaderFunction），无需再使用数组池克隆， 直接用 ArrayList
 * 更简单。每批在当前文件读完时提前截断以保证 fileOffset 一致。
 *
 * <p>上下游关系：被 {@link DataIteratorReaderFunction} 调用。
 */
class ListDataIteratorBatcher<T> implements DataIteratorBatcher<T> {

  private final int batchSize;

  /**
   * 构造批处理器。
   *
   * @param config Flink 配置（读取批次大小）
   */
  ListDataIteratorBatcher(ReadableConfig config) {
    this.batchSize = config.get(FlinkConfigOptions.SOURCE_READER_FETCH_BATCH_RECORD_COUNT);
  }

  /** 返回 {@link ListBatchIterator} 包装输入迭代器。 */
  @Override
  public CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>> batch(
      String splitId, DataIterator<T> dataIterator) {
    return new ListBatchIterator(splitId, dataIterator);
  }

  /** 用 ArrayList 收集记录并产出 {@link ListBatchRecords} 的迭代器。 */
  private class ListBatchIterator
      implements CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>> {

    private final String splitId;
    private final DataIterator<T> inputIterator;

    ListBatchIterator(String splitId, DataIterator<T> inputIterator) {
      this.splitId = splitId;
      this.inputIterator = inputIterator;
    }

    @Override
    public boolean hasNext() {
      return inputIterator.hasNext();
    }

    @Override
    public RecordsWithSplitIds<RecordAndPosition<T>> next() {
      if (!inputIterator.hasNext()) {
        throw new NoSuchElementException();
      }

      final List<T> batch = Lists.newArrayListWithCapacity(batchSize);
      int recordCount = 0;
      while (inputIterator.hasNext() && recordCount < batchSize) {
        T nextRecord = inputIterator.next();
        batch.add(nextRecord);
        recordCount++;
        if (!inputIterator.currentFileHasNext()) {
          // break early so that records have the same fileOffset.
          break;
        }
      }

      return ListBatchRecords.forRecords(
          splitId, batch, inputIterator.fileOffset(), inputIterator.recordOffset() - recordCount);
    }

    @Override
    public void close() throws IOException {
      if (inputIterator != null) {
        inputIterator.close();
      }
    }
  }
}
