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
package org.apache.iceberg.orc;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.iceberg.FieldMetrics;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * ORC 行写入器接口：把一行数据写入 ORC 的 {@link VectorizedRowBatch}。
 *
 * <p>所属模块：iceberg-orc。是行级写入的契约，由具体实现把行对象的各字段编码到列向量。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>write：把行数据写入 VectorizedRowBatch。
 *   <li>writers：返回所有字段写入器，便于外部收集统计。
 *   <li>metrics：返回各字段的 {@link FieldMetrics} 流（默认空）。
 * </ul>
 *
 * <p>设计意图：把行→列向量的编码逻辑从 ORC Writer 中解耦，使 Iceberg 可插入不同的行模型。
 *
 * <p>上下游关系：被 {@link OrcFileAppender} 调用逐行写入；实现类如 {@link
 * org.apache.iceberg.data.orc.GenericOrcWriter}。
 */
public interface OrcRowWriter<T> {

  /**
   * 把一行数据写入或追加到 ORC 的 VectorizedRowBatch。
   *
   * @param row 待写入行数据
   * @param output 目标 VectorizedRowBatch
   * @throws IOException 写入时发生 IO 错误
   */
  void write(T row, VectorizedRowBatch output) throws IOException;

  /** 返回所有字段写入器列表，便于外部收集统计与调试。 */
  List<OrcValueWriter<?>> writers();

  /** 返回此写入器跟踪的 {@link FieldMetrics} 流（默认空，由带统计的 writer 覆写）。 */
  default Stream<FieldMetrics<?>> metrics() {
    return Stream.empty();
  }
}
