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
package org.apache.iceberg.parquet;

import java.util.List;
import org.apache.parquet.column.page.PageReadStore;

/**
 * 文件级说明：Parquet 值读取器接口，按行读取并组装上层记录对象。
 *
 * <p>所属模块：iceberg-parquet（Parquet 读取侧的核心抽象，定义从列三元组到 T 的转换契约）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义按行读取接口 {@link #read(Object)}，将底层列迭代器的值组装为上层记录 T。
 *   <li>暴露所持有的列三元组迭代器，供外部按 row group 设置页数据源。
 *   <li>在新 row group 开始时通过 {@link #setPageSource} 下发页源与起始行位置。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>reuse 复用：read 接受 reuse 对象以减少对象分配，适配高吞吐场景。
 *   <li>列分离：columns() 暴露全部列迭代器，使上层可统一推进所有列的页源。
 * </ul>
 *
 * <p>上下游关系：被 {@link ReadConf}、各 data 模块的 Reader 实现；依赖 {@link TripleIterator} 与 {@link
 * PageReadStore}。
 */
public interface ParquetValueReader<T> {
  /**
   * 读取下一行数据并组装为 T。
   *
   * @param reuse 可复用的对象，实现可酌情使用以减少分配；可为 null
   * @return 读取到的记录
   */
  T read(T reuse);

  /**
   * 返回该读取器使用的主列三元组迭代器（通常是 struct 第一个字段所在列）。
   *
   * @return 主列迭代器
   */
  TripleIterator<?> column();

  /**
   * 返回该读取器依赖的全部列三元组迭代器。
   *
   * @return 列迭代器列表
   */
  List<TripleIterator<?>> columns();

  /**
   * 为新 row group 设置页数据源与起始行位置。
   *
   * <p>逻辑：把页源下发给所有列迭代器，使其重置状态并准备读取该 row group。
   *
   * @param pageStore 当前 row group 的页存储
   * @param rowPosition 当前 row group 在文件中的起始行位置
   */
  void setPageSource(PageReadStore pageStore, long rowPosition);
}
