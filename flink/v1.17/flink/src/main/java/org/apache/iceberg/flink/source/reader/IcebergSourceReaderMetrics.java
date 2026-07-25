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

import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;

/**
 * 文件级说明：Iceberg FLIP-27 Source Reader 的指标收集类。
 *
 * <p>所属模块：iceberg-flink（source/reader 子包），注册和管理 source reader 的 Flink 指标。
 *
 * <p>职责：跟踪和暴露以下指标：
 *
 * <ul>
 *   <li>assignedSplits/assignedBytes：已分配的 split 数量和字节数。
 *   <li>finishedSplits/finishedBytes：已完成的 split 数量和字节数。
 *   <li>splitReaderFetchCalls：SplitReader 的 fetch 调用次数。
 * </ul>
 *
 * <p>设计意图：将指标注册和更新逻辑封装在单独的类中，保持 reader 代码整洁。 指标按表名分组，便于多表场景区分。
 *
 * <p>上下游关系：被 {@link IcebergSourceReader} 调用以更新指标。
 */
public class IcebergSourceReaderMetrics {
  private final Counter assignedSplits;
  private final Counter assignedBytes;
  private final Counter finishedSplits;
  private final Counter finishedBytes;
  private final Counter splitReaderFetchCalls;

  /**
   * 构造方法，注册指标。
   *
   * @param metrics Flink MetricGroup
   * @param fullTableName 完整表名（用于指标分组）
   */
  public IcebergSourceReaderMetrics(MetricGroup metrics, String fullTableName) {
    MetricGroup readerMetrics =
        metrics.addGroup("IcebergSourceReader").addGroup("table", fullTableName);

    this.assignedSplits = readerMetrics.counter("assignedSplits");
    this.assignedBytes = readerMetrics.counter("assignedBytes");
    this.finishedSplits = readerMetrics.counter("finishedSplits");
    this.finishedBytes = readerMetrics.counter("finishedBytes");
    this.splitReaderFetchCalls = readerMetrics.counter("splitReaderFetchCalls");
  }

  public void incrementAssignedSplits(long count) {
    assignedSplits.inc(count);
  }

  public void incrementAssignedBytes(long count) {
    assignedBytes.inc(count);
  }

  public void incrementFinishedSplits(long count) {
    finishedSplits.inc(count);
  }

  public void incrementFinishedBytes(long count) {
    finishedBytes.inc(count);
  }

  public void incrementSplitReaderFetchCalls(long count) {
    splitReaderFetchCalls.inc(count);
  }
}
