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

import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.iceberg.flink.source.DataIterator;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 基于 {@link DataIterator} 的 {@link ReaderFunction} 抽象实现。
 *
 * <p>所属模块：iceberg-flink（source reader 侧）。
 *
 * <p>职责：为给定 split 创建 DataIterator，seek 到断点位置后交给批处理器产出批记录。
 *
 * <p>设计意图：将"如何创建 DataIterator"留给子类实现（{@link #createDataIterator}）， 本类统一处理 seek 与批处理编排。
 *
 * <p>上下游关系：被 {@link IcebergSourceReader} 调用；上游为 split，下游为批处理器。
 *
 * @param <T> 记录类型
 */
public abstract class DataIteratorReaderFunction<T> implements ReaderFunction<T> {
  private final DataIteratorBatcher<T> batcher;

  /**
   * 构造函数。
   *
   * @param batcher 批处理器
   */
  public DataIteratorReaderFunction(DataIteratorBatcher<T> batcher) {
    this.batcher = batcher;
  }

  /**
   * 子类实现：为指定 split 创建 {@link DataIterator}。
   *
   * @param split 数据分片
   * @return 数据迭代器
   */
  protected abstract DataIterator<T> createDataIterator(IcebergSourceSplit split);

  /**
   * 对 split 应用读取：创建迭代器、seek 到断点、批处理后返回。
   *
   * @param split 数据分片
   * @return 批记录迭代器
   */
  @Override
  public CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>> apply(
      IcebergSourceSplit split) {
    DataIterator<T> inputIterator = createDataIterator(split);
    inputIterator.seek(split.fileOffset(), split.recordOffset());
    return batcher.batch(split.splitId(), inputIterator);
  }
}
