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
import java.util.stream.Stream;
import org.apache.iceberg.FieldMetrics;
import org.apache.parquet.column.ColumnWriteStore;

/**
 * 文件级说明：Parquet 值写入器接口，按行接收上层记录并写入对应列。
 *
 * <p>所属模块：iceberg-parquet（Parquet 写入侧的核心抽象，定义从 T 到列三元组的转换契约）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义按行写入接口 {@link #write(int, Object)}，将上层记录 T 拆解为各列值写入。
 *   <li>暴露所持有的列三元组写入器，供外部按 row group 刷新列存储。
 *   <li>在新 row group 开始时通过 {@link #setColumnStore} 下发列存储。
 *   <li>可选地提供字段级指标（{@link FieldMetrics}）用于统计收集。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>repetitionLevel 参数：write 携带重复级别，支持 list/map 等嵌套结构的编码。
 *   <li>metrics 默认空流：多数写入器无需采集指标，默认返回空流避免强制实现。
 * </ul>
 *
 * <p>上下游关系：被 {@link BaseParquetWriter} 构造、由 {@link ParquetWriteAdapter} 调用； 依赖 {@link
 * ColumnWriteStore} 与 {@link TripleWriter}。
 */
public interface ParquetValueWriter<T> {
  /**
   * 写入一行数据。
   *
   * @param repetitionLevel 当前写入的重复级别，用于嵌套结构编码
   * @param value 待写入的记录
   */
  void write(int repetitionLevel, T value);

  /**
   * 返回该写入器持有的全部列三元组写入器。
   *
   * @return 列写入器列表
   */
  List<TripleWriter<?>> columns();

  /**
   * 设置底层列存储，使写入器把缓冲数据刷出到对应列。
   *
   * @param columnStore Parquet 列写存储
   */
  void setColumnStore(ColumnWriteStore columnStore);

  /**
   * 返回该写入器跟踪的字段级指标流，默认为空流。
   *
   * <p>用于收集每个字段的统计信息（如 min/max/null 计数），供写入完成时落盘。
   *
   * @return 字段指标流
   */
  default Stream<FieldMetrics<?>> metrics() {
    return Stream.empty();
  }
}
