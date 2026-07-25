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

import org.apache.flink.api.connector.source.SourceOutput;
import org.apache.flink.connector.base.source.reader.RecordEmitter;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;

/**
 * 将批记录发射到 Flink {@link SourceOutput} 并更新 split 位置的记录发射器。
 *
 * <p>所属模块：iceberg-flink（source reader 侧），实现 {@link RecordEmitter}。
 *
 * <p>职责：把 {@link RecordAndPosition} 中的记录 collect 到 SourceOutput， 并据其位置信息更新 split 的
 * fileOffset/recordOffset 以支持断点续读。
 *
 * <p>设计意图：发射与位置更新成对发生，保证 checkpoint 状态与已发射记录一致。
 *
 * <p>上下游关系：被 {@link IcebergSourceReader} 调用。
 */
final class IcebergSourceRecordEmitter<T>
    implements RecordEmitter<RecordAndPosition<T>, T, IcebergSourceSplit> {

  IcebergSourceRecordEmitter() {}

  /**
   * 发射单条记录并更新 split 位置。
   *
   * @param element 记录与位置
   * @param output Flink 源输出
   * @param split 当前分片（就地更新位置）
   */
  @Override
  public void emitRecord(
      RecordAndPosition<T> element, SourceOutput<T> output, IcebergSourceSplit split) {
    output.collect(element.record());
    split.updatePosition(element.fileOffset(), element.recordOffset());
  }
}
