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

/**
 * 文件级说明：FLIP-27 source 中用于创建与克隆记录批次的工厂接口。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/reader 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>创建可被对象池复用的记录数组。
 *   <li>把 DataIterator 中复用的 RowData 元素克隆到批次数组指定位置。
 * </ul>
 *
 * <p>设计意图：FLIP-27 source 的 {@code SplitReader#fetch()} 返回一批记录， 而 RowData 的 DataIterator
 * 默认复用同一对象，需要本工厂进行克隆以避免数据污染。
 *
 * <p>上下游关系：上游为 {@link org.apache.iceberg.flink.source.DataIterator}， 下游为 {@code
 * ArrayPoolDataIteratorBatcher}（按池化方式批量返回给 source reader）。
 */
interface RecordFactory<T> extends Serializable {
  /** 创建指定大小的记录批次数组。 */
  T[] createBatch(int batchSize);

  /** 把源记录克隆到批次数组的指定位置。 */
  void clone(T from, T[] batch, int position);
}
