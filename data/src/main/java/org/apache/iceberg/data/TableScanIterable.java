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
package org.apache.iceberg.data;

import java.io.IOException;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 表扫描结果可迭代对象：把 {@link TableScan} 的任务规划与 {@link GenericReader} 的读取 组合为可迭代的 {@link Record}
 * 流，并统一管理资源关闭。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持； 本类是 {@link
 * IcebergGenerics} 读取链路的最终产出物）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构造时即开始后台规划扫描任务（{@code scan.planTasks()}）。
 *   <li>{@link #iterator()} 时由 {@link GenericReader} 打开任务并返回 Record 迭代器。
 *   <li>实现 {@link CloseableGroup}，统一关闭数据文件与清单文件资源。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link CloseableGroup} 以聚合底层可关闭资源（迭代器、任务流），保证关闭顺序。
 *   <li>任务规划在构造期启动，使规划与读取可并行，降低端到端延迟。
 *   <li>{@link #close()} 先关闭任务流（清单），再关闭数据文件，避免遗漏底层 IO 句柄。
 * </ul>
 *
 * <p>上下游关系：由 {@link IcebergGenerics.ScanBuilder#build()} 创建；持有 {@link GenericReader} 与 {@link
 * TableScan} 规划出的任务流，供使用方迭代消费。
 */
class TableScanIterable extends CloseableGroup implements CloseableIterable<Record> {
  private final GenericReader reader;
  private final CloseableIterable<CombinedScanTask> tasks;

  /**
   * 构造可迭代对象：创建 {@link GenericReader} 并启动后台任务规划。
   *
   * @param scan 表扫描
   * @param reuseContainers 是否复用记录容器
   */
  TableScanIterable(TableScan scan, boolean reuseContainers) {
    this.reader = new GenericReader(scan, reuseContainers);
    // start planning tasks in the background
    this.tasks = scan.planTasks();
  }

  /**
   * 返回 Record 迭代器，由 {@link GenericReader} 打开已规划的任务。
   *
   * <p>设计要点：把返回的迭代器注册到 {@link CloseableGroup}，确保 {@link #close()} 时一并关闭。
   *
   * @return Record 迭代器
   */
  @Override
  public CloseableIterator<Record> iterator() {
    CloseableIterator<Record> iter = reader.open(tasks);
    addCloseable(iter);
    return iter;
  }

  /**
   * 关闭资源：先关闭任务流（清单文件），再调用父类关闭数据文件。
   *
   * @throws IOException 关闭过程中发生的 IO 异常
   */
  @Override
  public void close() throws IOException {
    tasks.close(); // close manifests from scan planning
    super.close(); // close data files
  }
}
