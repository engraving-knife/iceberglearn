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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.connector.source.SourceReaderContext;
import org.apache.flink.connector.base.source.reader.SingleThreadMultiplexSourceReaderBase;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.SerializableComparator;
import org.apache.iceberg.flink.source.split.SplitRequestEvent;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：FLIP-27 source 中的 SourceReader 实现，单线程多路复用读取 Iceberg 数据。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/reader 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>启动时若未从 checkpoint 恢复到 split 则向协调器请求 split。
 *   <li>split 读取完成后向协调器申请新 split。
 *   <li>把 Iceberg 的 split 状态与记录通过基类传递给 Flink 框架。
 * </ul>
 *
 * <p>设计意图：基于 Flink {@link SingleThreadMultiplexSourceReaderBase} 单线程复用模型， 通过 {@link
 * IcebergSourceSplitReader} 实际读取，{@link IcebergSourceRecordEmitter} 发射记录。
 *
 * <p>上下游关系：上游为 {@link IcebergSourceSplitReader}（实际读取 split）， 下游为 Flink Source 框架与 {@link
 * IcebergSource} 协调器（通过事件通信）。
 */
@Internal
public class IcebergSourceReader<T>
    extends SingleThreadMultiplexSourceReaderBase<
        RecordAndPosition<T>, T, IcebergSourceSplit, IcebergSourceSplit> {

  /**
   * 构造 SourceReader。
   *
   * @param metrics 指标收集器
   * @param readerFunction 创建数据迭代器的函数
   * @param splitComparator split 排序比较器
   * @param context Flink SourceReader 上下文
   */
  public IcebergSourceReader(
      IcebergSourceReaderMetrics metrics,
      ReaderFunction<T> readerFunction,
      SerializableComparator<IcebergSourceSplit> splitComparator,
      SourceReaderContext context) {
    super(
        () -> new IcebergSourceSplitReader<>(metrics, readerFunction, splitComparator, context),
        new IcebergSourceRecordEmitter<>(),
        context.getConfiguration(),
        context);
  }

  /**
   * 启动 reader。
   *
   * <p>逻辑：仅当未从 checkpoint 恢复到任何 split 时才请求 split， 否则 reader 重启会持续请求越来越多的 split。
   */
  @Override
  public void start() {
    // 仅当未从 checkpoint 恢复到 split 时请求。否则 reader 重启会持续请求越来越多的 split。
    if (getNumberOfCurrentlyAssignedSplits() == 0) {
      requestSplit(Collections.emptyList());
    }
  }

  /**
   * split 读取完成时回调，向协调器请求新 split。
   *
   * @param finishedSplitIds 已完成的 split ID 映射
   */
  @Override
  protected void onSplitFinished(Map<String, IcebergSourceSplit> finishedSplitIds) {
    requestSplit(Lists.newArrayList(finishedSplitIds.keySet()));
  }

  /** 直接返回原 split 作为初始状态。 */
  @Override
  protected IcebergSourceSplit initializedState(IcebergSourceSplit split) {
    return split;
  }

  /** 把 split 状态转换为 split 类型，本实现直接返回原状态。 */
  @Override
  protected IcebergSourceSplit toSplitType(String splitId, IcebergSourceSplit splitState) {
    return splitState;
  }

  /**
   * 向协调器发送 split 请求事件。
   *
   * @param finishedSplitIds 已完成的 split ID 列表
   */
  private void requestSplit(Collection<String> finishedSplitIds) {
    context.sendSourceEventToCoordinator(new SplitRequestEvent(finishedSplitIds));
  }
}
