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
import java.util.NoSuchElementException;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.flink.connector.base.source.reader.SourceReaderOptions;
import org.apache.flink.connector.file.src.util.Pool;
import org.apache.iceberg.flink.FlinkConfigOptions;
import org.apache.iceberg.flink.source.DataIterator;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 使用可回收数组池缓存批次的 DataIterator 批处理器。
 *
 * <p>所属模块：iceberg-flink（source reader 侧），实现 {@link DataIteratorBatcher}。
 *
 * <p>职责：将 {@link DataIterator} 的记录按批次大小打包为 {@link RecordsWithSplitIds}， 批次存储在可回收的数组池（{@link
 * Pool}）中以减少 GC 压力。
 *
 * <p>设计意图：池大小与 handover 队列容量一致，保证批次数与下游消费能力匹配； 池懒创建因其不可序列化。记录需克隆到批次数组（因 inputIterator 产出的记录可能被复用）。
 *
 * <p>上下游关系：被 {@link DataIteratorReaderFunction} 调用；上游为 DataIterator，下游为 Flink source reader。
 */
class ArrayPoolDataIteratorBatcher<T> implements DataIteratorBatcher<T> {
  private final int batchSize;
  private final int handoverQueueSize;
  private final RecordFactory<T> recordFactory;

  private transient Pool<T[]> pool;

  /**
   * 构造批处理器。
   *
   * @param config Flink 配置（读取批次大小与队列容量）
   * @param recordFactory 记录工厂（创建批次数组与克隆记录）
   */
  ArrayPoolDataIteratorBatcher(ReadableConfig config, RecordFactory<T> recordFactory) {
    this.batchSize = config.get(FlinkConfigOptions.SOURCE_READER_FETCH_BATCH_RECORD_COUNT);
    this.handoverQueueSize = config.get(SourceReaderOptions.ELEMENT_QUEUE_CAPACITY);
    this.recordFactory = recordFactory;
  }

  /**
   * 对输入迭代器进行批处理。
   *
   * <p>逻辑：懒创建数组池，返回 {@link ArrayPoolBatchIterator} 包装输入迭代器。
   *
   * @param splitId 分片 id
   * @param inputIterator 输入数据迭代器
   * @return 批记录迭代器
   */
  @Override
  public CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>> batch(
      String splitId, DataIterator<T> inputIterator) {
    Preconditions.checkArgument(inputIterator != null, "Input data iterator can't be null");
    // lazily create pool as it is not serializable
    if (pool == null) {
      this.pool = createPoolOfBatches(handoverQueueSize);
    }
    return new ArrayPoolBatchIterator(splitId, inputIterator, pool);
  }

  /** 创建指定大小的数组池，每个元素为一个批次数组。 */
  private Pool<T[]> createPoolOfBatches(int numBatches) {
    Pool<T[]> poolOfBatches = new Pool<>(numBatches);
    for (int batchId = 0; batchId < numBatches; batchId++) {
      T[] batch = recordFactory.createBatch(batchSize);
      poolOfBatches.add(batch);
    }

    return poolOfBatches;
  }

  /** 从数组池获取批次、填充记录并产出 {@link ArrayBatchRecords} 的迭代器。 */
  private class ArrayPoolBatchIterator
      implements CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>> {

    private final String splitId;
    private final DataIterator<T> inputIterator;
    private final Pool<T[]> pool;

    ArrayPoolBatchIterator(String splitId, DataIterator<T> inputIterator, Pool<T[]> pool) {
      this.splitId = splitId;
      this.inputIterator = inputIterator;
      this.pool = pool;
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

      T[] batch = getCachedEntry();
      int recordCount = 0;
      while (inputIterator.hasNext() && recordCount < batchSize) {
        // The record produced by inputIterator can be reused like for the RowData case.
        // inputIterator.next() can't be called again until the copy is made
        // since the record is not consumed immediately.
        T nextRecord = inputIterator.next();
        recordFactory.clone(nextRecord, batch, recordCount);
        recordCount++;
        if (!inputIterator.currentFileHasNext()) {
          // break early so that records in the ArrayResultIterator
          // have the same fileOffset.
          break;
        }
      }

      return ArrayBatchRecords.forRecords(
          splitId,
          pool.recycler(),
          batch,
          recordCount,
          inputIterator.fileOffset(),
          inputIterator.recordOffset() - recordCount);
    }

    /** 关闭输入迭代器。 */
    @Override
    public void close() throws IOException {
      inputIterator.close();
    }

    /** 从池中获取一个可用的批次数组，被中断时抛出异常。 */
    private T[] getCachedEntry() {
      try {
        return pool.pollEntry();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for array pool entry", e);
      }
    }
  }
}
