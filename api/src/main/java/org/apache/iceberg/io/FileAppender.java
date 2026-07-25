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
package org.apache.iceberg.io;

import java.io.Closeable;
import java.util.Iterator;
import java.util.List;
import org.apache.iceberg.Metrics;

/**
 * 文件级说明：数据文件追加写入器接口，把数据逐条追加写入到一个数据文件中。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #add(Object)} 逐条写入数据。
 *   <li>通过 {@link #addAll(Iterator)} / {@link #addAll(Iterable)} 批量写入。
 *   <li>关闭后通过 {@link #metrics()} 暴露文件统计指标、通过 {@link #length()} 暴露文件长度。
 *   <li>可选地通过 {@link #splitOffsets()} 暴露推荐的切分位置，用于扫描任务规划。
 * </ul>
 *
 * <p>设计意图：把“写数据文件”这一动作抽象成统一接口，使得 Parquet/ORC/Avro 等不同格式的 写入器实现都可以被 Iceberg 写路径以相同方式驱动。{@code
 * metrics} / {@code splitOffsets} 仅在文件关闭后有效，是为了在写完成时一次性产出统计与切分信息，避免写入中途状态不一致。
 *
 * <p>上下游关系：由 Iceberg 写路径（DataWriter 等）创建并驱动；底层由各格式实现提供具体 类。产出的 {@link Metrics} 会进入表元数据用于查询优化。
 *
 * @param <D> 写入的数据类型
 */
public interface FileAppender<D> extends Closeable {
  /**
   * 向文件追加一条数据。
   *
   * @param datum 要写入的数据
   */
  void add(D datum);

  /**
   * 批量追加迭代器中的所有数据。
   *
   * <p>逻辑：循环调用 {@link #add(Object)} 直到迭代器耗尽。
   *
   * @param values 数据迭代器
   */
  default void addAll(Iterator<D> values) {
    while (values.hasNext()) {
      add(values.next());
    }
  }

  /**
   * 批量追加可迭代对象中的所有数据。
   *
   * <p>逻辑：委托给 {@link #addAll(Iterator)}，传入迭代器。
   *
   * @param values 数据可迭代对象
   */
  default void addAll(Iterable<D> values) {
    addAll(values.iterator());
  }

  /**
   * 返回该文件的 {@link Metrics} 统计信息。仅在文件关闭后有效。
   *
   * @return 文件统计指标
   */
  Metrics metrics();

  /**
   * 返回该文件的长度。仅在文件关闭后有效。
   *
   * @return 文件长度（字节）
   */
  long length();

  /**
   * 返回推荐的切分位置列表（如适用），否则返回 null。
   *
   * <p>设计要点：当可用时，该信息用于规划扫描任务，扫描任务的边界由这些偏移决定。返回 列表必须按升序排序。仅在文件关闭后有效。
   *
   * @return 切分偏移列表（升序），或 null 表示无推荐
   */
  default List<Long> splitOffsets() {
    return null;
  }
}
