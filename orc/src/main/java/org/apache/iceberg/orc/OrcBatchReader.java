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

import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * ORC 批量读取器接口：一次读取一个 {@link VectorizedRowBatch} 并返回一批行对象。
 *
 * <p>所属模块：iceberg-orc。面向向量化读取场景，与逐行 {@link OrcRowReader} 相对。
 *
 * <p>职责：定义批量读取的契约，由具体实现把列向量解码为行集合。
 *
 * <p>设计意图：将“如何从列向量构建行”的逻辑从 ORC Reader 中解耦， 使 Iceberg 可插入不同的行模型（GenericRecord、Spark InternalRow 等）。
 *
 * <p>上下游关系：被 {@link OrcIterable} 在向量化读取模式下调用。
 */
public interface OrcBatchReader<T> {

  /**
   * 读取一个 {@link VectorizedRowBatch} 并返回一批行对象。
   *
   * @param batch ORC 列式批量数据
   * @return 解码后的行集合
   */
  T read(VectorizedRowBatch batch);

  /**
   * 设置当前 batch 在文件中的偏移，供需要文件位置的 reader 使用。
   *
   * @param batchOffsetInFile batch 在 ORC 文件中的字节偏移
   */
  void setBatchContext(long batchOffsetInFile);
}
