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
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.connector.base.source.reader.RecordsBySplits;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.flink.connector.base.source.reader.splitreader.SplitReader;
import org.apache.flink.connector.base.source.reader.splitreader.SplitsAddition;
import org.apache.flink.connector.base.source.reader.splitreader.SplitsChange;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.SerializableComparator;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：FLIP-27 source 中的 SplitReader 实现，从 Iceberg split 中读取数据。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/reader 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从队列中取出待读取 split，逐个调用 {@link ReaderFunction} 创建数据迭代器。
 *   <li>处理新增 split 的添加（支持按 comparator 排序）。
 *   <li>split 读取完成后清理状态并上报指标。
 * </ul>
 *
 * <p>设计意图：单线程读取模型下，一次只处理一个 split，避免并发读取引发的状态混乱； 通过 comparator 支持自定义 split 处理顺序。
 *
 * <p>上下游关系：上游为 {@link IcebergSourceReader}（调用 fetch）， 下游为 {@link ReaderFunction}（创建每个 split
 * 的数据迭代器）。
 */
class IcebergSourceSplitReader<T> implements SplitReader<RecordAndPosition<T>, IcebergSourceSplit> {
  private static final Logger LOG = LoggerFactory.getLogger(IcebergSourceSplitReader.class);

  private final IcebergSourceReaderMetrics metrics;
  private final ReaderFunction<T> openSplitFunction;
  private final SerializableComparator<IcebergSourceSplit> splitComparator;
  private final int indexOfSubtask;
  private final Queue<IcebergSourceSplit> splits;

  private CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>> currentReader;
  private IcebergSourceSplit currentSplit;
  private String currentSplitId;

  /** 构造 SplitReader，传入指标、split 打开函数、排序比较器与 reader 上下文。 */
  IcebergSourceSplitReader(
      IcebergSourceReaderMetrics metrics,
      ReaderFunction<T> openSplitFunction,
      SerializableComparator<IcebergSourceSplit> splitComparator,
      SourceReaderContext context) {
    this.metrics = metrics;
    this.openSplitFunction = openSplitFunction;
    this.splitComparator = splitComparator;
    this.indexOfSubtask = context.getIndexOfSubtask();
    this.splits = new ArrayDeque<>();
  }

  /**
   * 拉取下一批记录。
   *
   * <p>逻辑：若当前无 reader 则从队列取出下一个 split 并打开； 若队列也为空，返回空结果让 fetcher 进入空闲状态； 若当前 reader
   * 还有数据则返回下一批，否则结束当前 split。
   *
   * @return 当前 split 的下一批记录
   * @throws IOException 读取失败时抛出
   */
  @Override
  public RecordsWithSplitIds<RecordAndPosition<T>> fetch() throws IOException {
    metrics.incrementSplitReaderFetchCalls(1);
    if (currentReader == null) {
      IcebergSourceSplit nextSplit = splits.poll();
      if (nextSplit != null) {
        currentSplit = nextSplit;
        currentSplitId = nextSplit.splitId();
        currentReader = openSplitFunction.apply(currentSplit);
      } else {
        // 返回空结果，使 split fetch 进入空闲，SplitFetcherManager 会关闭空闲 fetcher。
        return new RecordsBySplits(Collections.emptyMap(), Collections.emptySet());
      }
    }

    if (currentReader.hasNext()) {
      // Iterator#next() 不支持受检异常，因此用 UncheckedIOException 包装再解包。
      try {
        return currentReader.next();
      } catch (UncheckedIOException e) {
        throw e.getCause();
      }
    } else {
      return finishSplit();
    }
  }

  /**
   * 处理 split 变更，仅支持新增 split。
   *
   * <p>逻辑：若提供 comparator 则按其排序后再加入队列，否则直接加入； 同时更新分配的 split 与字节数指标。
   *
   * @param splitsChange split 变更
   */
  @Override
  public void handleSplitsChanges(SplitsChange<IcebergSourceSplit> splitsChange) {
    if (!(splitsChange instanceof SplitsAddition)) {
      throw new UnsupportedOperationException(
          String.format("Unsupported split change: %s", splitsChange.getClass()));
    }

    if (splitComparator != null) {
      List<IcebergSourceSplit> newSplits = Lists.newArrayList(splitsChange.splits());
      newSplits.sort(splitComparator);
      LOG.info("Add {} splits to reader: {}", newSplits.size(), newSplits);
      splits.addAll(newSplits);
    } else {
      LOG.info("Add {} splits to reader", splitsChange.splits().size());
      splits.addAll(splitsChange.splits());
    }
    metrics.incrementAssignedSplits(splitsChange.splits().size());
    metrics.incrementAssignedBytes(calculateBytes(splitsChange));
  }

  /** 空实现，由 fetcher 线程在 fetch 时被阻塞才需要唤醒，此处无阻塞读取。 */
  @Override
  public void wakeUp() {}

  /** 关闭当前 reader 并清理状态。 */
  @Override
  public void close() throws Exception {
    currentSplitId = null;
    if (currentReader != null) {
      currentReader.close();
    }
  }

  /** 计算单个 split 中所有文件的总字节数。 */
  private long calculateBytes(IcebergSourceSplit split) {
    return split.task().files().stream().map(FileScanTask::length).reduce(0L, Long::sum);
  }

  /** 计算变更中所有 split 的总字节数。 */
  private long calculateBytes(SplitsChange<IcebergSourceSplit> splitsChanges) {
    return splitsChanges.splits().stream().map(this::calculateBytes).reduce(0L, Long::sum);
  }

  /**
   * 结束当前 split 读取。
   *
   * <p>逻辑：关闭当前 reader，返回完成标记记录，并上报完成的 split 数与字节数。
   *
   * @return 标记 split 完成的记录
   * @throws IOException 关闭 reader 失败时抛出
   */
  private ArrayBatchRecords<T> finishSplit() throws IOException {
    if (currentReader != null) {
      currentReader.close();
      currentReader = null;
    }

    ArrayBatchRecords<T> finishRecords = ArrayBatchRecords.finishedSplit(currentSplitId);
    LOG.info("Split reader {} finished split: {}", indexOfSubtask, currentSplitId);
    metrics.incrementFinishedSplits(1);
    metrics.incrementFinishedBytes(calculateBytes(currentSplit));
    currentSplitId = null;
    return finishRecords;
  }
}
