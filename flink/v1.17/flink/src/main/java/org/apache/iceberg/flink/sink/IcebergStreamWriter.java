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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.flink.streaming.api.operators.AbstractStreamOperator;
import org.apache.flink.streaming.api.operators.BoundedOneInput;
import org.apache.flink.streaming.api.operators.ChainingStrategy;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.iceberg.io.TaskWriter;
import org.apache.iceberg.io.WriteResult;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * 文件级说明：Iceberg 流式写入算子，将输入数据写入 Iceberg 数据文件。
 *
 * <p>所属模块：iceberg-flink（sink 子包），继承 Flink 的 {@link AbstractStreamOperator}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>接收上游数据，委托 {@link TaskWriter} 写入数据文件和删除文件。
 *   <li>在 checkpoint barrier 到达时（prepareSnapshotPreBarrier）flush 当前 writer 并产出 WriteResult。
 *   <li>在有界流结束时（endInput）flush 剩余文件。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>ChainingStrategy.ALWAYS：允许与上游算子链式调用，减少序列化开销。
 *   <li>每次 checkpoint 时 flush 并重建 writer，保证 exactly-once 语义。
 *   <li>flush 后将 writer 置 null，防止 endInput 后的重复 flush。
 * </ul>
 *
 * <p>上下游关系：上游为 {@code DataStream<RowData>}；下游为 {@link IcebergFilesCommitter}（接收 WriteResult）。
 *
 * @param <T> 输入数据类型
 */
class IcebergStreamWriter<T> extends AbstractStreamOperator<WriteResult>
    implements OneInputStreamOperator<T, WriteResult>, BoundedOneInput {

  private static final long serialVersionUID = 1L;

  private final String fullTableName;
  private final TaskWriterFactory<T> taskWriterFactory;

  private transient TaskWriter<T> writer;
  private transient int subTaskId;
  private transient int attemptId;
  private transient IcebergStreamWriterMetrics writerMetrics;

  /**
   * 构造方法。
   *
   * @param fullTableName 完整表名（用于指标注册）
   * @param taskWriterFactory 任务写入器工厂
   */
  IcebergStreamWriter(String fullTableName, TaskWriterFactory<T> taskWriterFactory) {
    this.fullTableName = fullTableName;
    this.taskWriterFactory = taskWriterFactory;
    setChainingStrategy(ChainingStrategy.ALWAYS);
  }

  /**
   * 算子初始化。
   *
   * <p>逻辑：获取 subTaskId 和 attemptId → 初始化写入指标 → 初始化 TaskWriterFactory → 创建首个 writer。
   */
  @Override
  public void open() {
    this.subTaskId = getRuntimeContext().getIndexOfThisSubtask();
    this.attemptId = getRuntimeContext().getAttemptNumber();
    this.writerMetrics = new IcebergStreamWriterMetrics(super.metrics, fullTableName);

    // Initialize the task writer factory.
    this.taskWriterFactory.initialize(subTaskId, attemptId);

    // Initialize the task writer.
    this.writer = taskWriterFactory.create();
  }

  /**
   * checkpoint barrier 到达时触发 flush。
   *
   * <p>逻辑：flush 当前 writer 的 WriteResult 到下游 → 重建新的 writer 供下一周期使用。 保证 checkpoint 之间的数据一致性。
   *
   * @param checkpointId checkpoint id
   */
  @Override
  public void prepareSnapshotPreBarrier(long checkpointId) throws Exception {
    flush();
    this.writer = taskWriterFactory.create();
  }

  /** 处理一条输入记录，委托 writer 写入。 */
  @Override
  public void processElement(StreamRecord<T> element) throws Exception {
    writer.write(element.getValue());
  }

  /** 关闭算子，释放 writer 资源。 */
  @Override
  public void close() throws Exception {
    super.close();
    if (writer != null) {
      writer.close();
      writer = null;
    }
  }

  /**
   * 有界流输入结束时的处理。
   *
   * <p>逻辑：flush 剩余文件到下游。对于未启用 checkpoint 的有界流，确保不丢失数据。
   */
  @Override
  public void endInput() throws IOException {
    // For bounded stream, it may don't enable the checkpoint mechanism so we'd better to emit the
    // remaining completed files to downstream before closing the writer so that we won't miss any
    // of them.
    // Note that if the task is not closed after calling endInput, checkpoint may be triggered again
    // causing files to be sent repeatedly, the writer is marked as null after the last file is sent
    // to guard against duplicated writes.
    flush();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("table_name", fullTableName)
        .add("subtask_id", subTaskId)
        .add("attempt_id", attemptId)
        .toString();
  }

  /**
   * 关闭所有打开的文件，将 WriteResult 发送到下游 committer 算子。
   *
   * <p>逻辑：complete writer 获取 WriteResult → 更新指标 → 发送到下游 → 记录 flush 耗时 → 将 writer 置 null 防止重复 flush。
   */
  private void flush() throws IOException {
    if (writer == null) {
      return;
    }

    long startNano = System.nanoTime();
    WriteResult result = writer.complete();
    writerMetrics.updateFlushResult(result);
    output.collect(new StreamRecord<>(result));
    writerMetrics.flushDuration(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano));

    // Set writer to null to prevent duplicate flushes in the corner case of
    // prepareSnapshotPreBarrier happening after endInput.
    writer = null;
  }
}
