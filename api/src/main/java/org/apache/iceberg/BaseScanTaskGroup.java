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
import java.util.Collections;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 扫描任务分组的通用基类实现。
 *
 * <p>所属模块：iceberg-api（核心扫描任务抽象层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有属于同一分组键（grouping key）的一组 {@link ScanTask}；
 *   <li>提供分组级别的大小、行数、文件数等聚合统计信息；
 *   <li>支持任务集合的懒加载与序列化恢复。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>任务以数组形式存储（{@code Object[] tasks}），便于序列化（如 Spark 广播）； 同时保留一个 transient 的不可变集合视图，避免每次访问都重新构建。
 *   <li>{@link #tasks()} 使用双检锁懒构建集合视图，保证线程安全且仅构建一次。
 * </ul>
 *
 * <p>上下游关系：实现 {@link ScanTaskGroup} 接口；被 core/引擎模块用于将多个文件扫描任务 组合成一个可并行执行的单元（如 Spark 的一个 task）。
 *
 * @param <T> 具体的扫描任务类型
 */
public class BaseScanTaskGroup<T extends ScanTask> implements ScanTaskGroup<T> {
  private final StructLike groupingKey;
  private final Object[] tasks;
  private transient volatile Collection<T> taskCollection;

  /**
   * 构造带分组键的任务分组。
   *
   * @param groupingKey 分组键，标识本组任务所属的分区/分桶等
   * @param tasks 本组包含的扫描任务集合
   */
  public BaseScanTaskGroup(StructLike groupingKey, Collection<T> tasks) {
    Preconditions.checkNotNull(tasks, "tasks cannot be null");
    this.groupingKey = groupingKey;
    this.tasks = tasks.toArray();
    this.taskCollection = Collections.unmodifiableCollection(tasks);
  }

  /**
   * 构造无分组键的任务分组（使用空 struct 作为占位分组键）。
   *
   * @param tasks 本组包含的扫描任务集合
   */
  public BaseScanTaskGroup(Collection<T> tasks) {
    this(EmptyStructLike.get(), tasks);
  }

  @Override
  public StructLike groupingKey() {
    return groupingKey;
  }

  /**
   * 返回本组所有任务的不可变集合视图。
   *
   * <p>逻辑：若 transient 视图为空（典型场景为反序列化后），使用双检锁从底层数组重建 不可变集合，避免并发场景下重复构建。
   *
   * @return 任务集合（不可变）
   */
  @Override
  @SuppressWarnings("unchecked")
  public Collection<T> tasks() {
    if (taskCollection == null) {
      synchronized (this) {
        if (taskCollection == null) {
          ImmutableList.Builder<T> listBuilder =
              ImmutableList.builderWithExpectedSize(tasks.length);
          for (Object task : tasks) {
            listBuilder.add((T) task);
          }
          this.taskCollection = listBuilder.build();
        }
      }
    }

    return taskCollection;
  }

  /** 累加本组所有任务的目标字节数。 */
  @Override
  public long sizeBytes() {
    long sizeBytes = 0L;
    for (Object task : tasks) {
      sizeBytes += ((ScanTask) task).sizeBytes();
    }
    return sizeBytes;
  }

  /** 累加本组所有任务的估算行数。 */
  @Override
  public long estimatedRowsCount() {
    long estimatedRowsCount = 0L;
    for (Object task : tasks) {
      estimatedRowsCount += ((ScanTask) task).estimatedRowsCount();
    }
    return estimatedRowsCount;
  }

  /** 累加本组所有任务涉及的文件数。 */
  @Override
  public int filesCount() {
    int filesCount = 0;
    for (Object task : tasks) {
      filesCount += ((ScanTask) task).filesCount();
    }
    return filesCount;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("tasks", Joiner.on(", ").join(tasks)).toString();
  }
}
