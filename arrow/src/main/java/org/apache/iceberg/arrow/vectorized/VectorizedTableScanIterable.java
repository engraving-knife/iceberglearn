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
package org.apache.iceberg.arrow.vectorized;

import java.io.IOException;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 文件级说明：Iceberg 表扫描的向量化可迭代读取实现。
 *
 * <p>所属模块：iceberg-arrow（表扫描向量化读取的入口适配）。
 *
 * <p>职责：包装 {@link ArrowReader}，规划扫描任务并以默认或指定批大小、容器复用策略 产出 {@link ColumnarBatch} 的可关闭迭代器。
 *
 * <p>设计意图：作为 {@link ArrowReader} 的便捷封装，提供默认批大小（2^16）与非复用容器的 开箱即用配置；实现 {@link CloseableIterable}
 * 以纳入资源管理，关闭时同时关闭任务规划 清单与数据文件。
 *
 * <p>上下游关系：上游为 {@link TableScan}；内部委托 {@link ArrowReader}；下游被引擎迭代消费。
 */
public class VectorizedTableScanIterable extends CloseableGroup
    implements CloseableIterable<ColumnarBatch> {

  private static final int BATCH_SIZE_IN_NUM_ROWS = 1 << 16;

  private final ArrowReader reader;
  private final CloseableIterable<CombinedScanTask> tasks;

  /**
   * 使用默认批大小（{@link #BATCH_SIZE_IN_NUM_ROWS}）且不复用容器构造实例。
   *
   * @param scan 表扫描
   */
  public VectorizedTableScanIterable(TableScan scan) {
    this(scan, BATCH_SIZE_IN_NUM_ROWS, false);
  }

  /**
   * 构造实例，详见 {@link ArrowReader#ArrowReader(TableScan, int, boolean)}。
   *
   * @param scan 表扫描
   * @param batchSize 批大小
   * @param reuseContainers 是否复用容器
   */
  public VectorizedTableScanIterable(TableScan scan, int batchSize, boolean reuseContainers) {
    this.reader = new ArrowReader(scan, batchSize, reuseContainers);
    // start planning tasks in the background
    this.tasks = scan.planTasks();
  }

  @Override
  /**
   * 返回列式批次的可关闭迭代器，并将其纳入本组的资源管理。
   *
   * @return 列式批次迭代器
   */
  public CloseableIterator<ColumnarBatch> iterator() {
    CloseableIterator<ColumnarBatch> iter = reader.open(tasks);
    addCloseable(iter);
    return iter;
  }

  @Override
  /**
   * 关闭任务规划清单与数据文件资源。
   *
   * @throws IOException 关闭异常
   */
  public void close() throws IOException {
    tasks.close(); // close manifests from scan planning
    super.close(); // close data files
  }
}
