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
package org.apache.iceberg;

import java.util.Collection;
import java.util.List;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.util.TableScanUtil;

/**
 * {@link CombinedScanTask} 的核心实现：把若干 {@link FileScanTask} 聚合成一个可整体调度的复合扫描任务。
 *
 * <p>所属模块：iceberg-core（核心实现层），向引擎层暴露合并后的扫描任务视图。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一组 {@link FileScanTask}，提供文件集合、字节大小、行数估算、文件数等聚合指标。
 *   <li>支持以数组或 List 两种方式构造；List 构造会先调用 {@link TableScanUtil#mergeTasks} 合并相邻任务，减少扫描碎片。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>数组存储 + 懒初始化 List 视图：内部以数组形式持有任务保证序列化轻量与内存紧凑； {@link #files()} 首次调用时构建不可变 List 视图，便于多次读取。
 *   <li>transient 标记避免重复序列化 List，反序列化后按需重建。
 * </ul>
 *
 * <p>上下游关系：被 core 的扫描规划器以及各引擎的扫描任务调度逻辑调用，底层依赖 {@link TableScanUtil} 的合并工具。
 */
public class BaseCombinedScanTask implements CombinedScanTask {
  private final FileScanTask[] tasks;
  private transient volatile List<FileScanTask> taskList = null;

  /**
   * 以数组形式构造复合扫描任务。
   *
   * @param tasks 待聚合的文件扫描任务数组
   */
  public BaseCombinedScanTask(FileScanTask... tasks) {
    Preconditions.checkNotNull(tasks, "tasks cannot be null");
    this.tasks = tasks;
  }

  /**
   * 以 List 形式构造复合扫描任务。
   *
   * <p>逻辑：先通过 {@link TableScanUtil#mergeTasks} 合并相邻任务（同文件/相邻 split 合并）， 再转为数组保存，降低任务碎片数量，提升扫描效率。
   *
   * @param tasks 待聚合与合并的文件扫描任务列表
   */
  public BaseCombinedScanTask(List<FileScanTask> tasks) {
    Preconditions.checkNotNull(tasks, "tasks cannot be null");
    this.tasks = TableScanUtil.mergeTasks(tasks).toArray(new FileScanTask[0]);
  }

  /**
   * 返回本复合任务包含的所有文件扫描任务（不可变视图）。
   *
   * <p>逻辑：首次调用时基于内部数组构建不可变 List 缓存，后续直接返回缓存，保证线程安全读。
   *
   * @return 文件扫描任务集合
   */
  @Override
  public Collection<FileScanTask> files() {
    if (taskList == null) {
      this.taskList = ImmutableList.copyOf(tasks);
    }

    return taskList;
  }

  /**
   * 汇总所有扫描任务覆盖的字节大小。
   *
   * @return 字节总数
   */
  @Override
  public long sizeBytes() {
    long sizeBytes = 0L;
    for (FileScanTask task : tasks) {
      sizeBytes += task.sizeBytes();
    }
    return sizeBytes;
  }

  /**
   * 汇总所有扫描任务预计读取的行数。
   *
   * @return 估算行数总和
   */
  @Override
  public long estimatedRowsCount() {
    long estimatedRowsCount = 0L;
    for (FileScanTask task : tasks) {
      estimatedRowsCount += task.estimatedRowsCount();
    }
    return estimatedRowsCount;
  }

  /**
   * 汇总所有扫描任务涉及的文件数量。
   *
   * @return 文件数总和
   */
  @Override
  public int filesCount() {
    int filesCount = 0;
    for (FileScanTask task : tasks) {
      filesCount += task.filesCount();
    }
    return filesCount;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("tasks", Joiner.on(", ").join(tasks)).toString();
  }
}
