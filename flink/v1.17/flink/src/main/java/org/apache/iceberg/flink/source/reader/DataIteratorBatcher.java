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

import java.io.Serializable;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.iceberg.flink.source.DataIterator;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 文件级说明：将 DataIterator 转换为批量 RecordsWithSplitIds 迭代器的函数式接口。
 *
 * <p>所属模块：iceberg-flink（source/reader 子包），用于 FLIP-27 Source 的 SplitReader.fetch()。
 *
 * <p>职责：把逐条记录的 DataIterator 转换为批量记录的迭代器，提高 fetcher 与 reader 之间的数据传递效率。
 *
 * <p>设计意图：函数式接口（@FunctionalInterface），可序列化以便在 TaskManager 上使用。 不同实现可采用不同的批量策略（数组池、List 等）。
 *
 * <p>上下游关系：被 {@link DataIteratorReaderFunction} 调用； 实现类如 {@link ArrayPoolDataIteratorBatcher}。
 *
 * @param <T> 记录类型
 */
@FunctionalInterface
public interface DataIteratorBatcher<T> extends Serializable {
  /**
   * 将 DataIterator 转换为批量记录迭代器。
   *
   * @param splitId split 标识符
   * @param inputIterator 输入数据迭代器
   * @return 批量记录迭代器
   */
  CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>> batch(
      String splitId, DataIterator<T> inputIterator);
}
