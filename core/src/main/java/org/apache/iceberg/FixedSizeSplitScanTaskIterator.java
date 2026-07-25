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

/**
 * 按固定大小切分扫描任务的迭代器。
 *
 * <p>所属模块：iceberg-core（核心实现层），用于把过大的 {@link ScanTask} 切分为多个固定大小的子任务。
 *
 * <p>职责：维护偏移与剩余长度，每次 {@link #next()} 切出不超过 splitSize 的子任务，直到剩余长度为 0。
 *
 * <p>设计意图：简单固定大小策略，便于引擎按统一 split 大小并行处理。
 *
 * @param <T> 产出的扫描任务类型
 */
class FixedSizeSplitScanTaskIterator<T extends ScanTask> implements SplitScanTaskIterator<T> {
  private final T parentTask;
  private final long splitSize;
  private final SplitScanTaskCreator<T> splitTaskCreator;
  private long offset;
  private long remainingLength;

  /**
   * 构造方法。
   *
   * @param parentTask 被切分的父任务
   * @param parentTaskLength 父任务总长度
   * @param splitSize 每个子任务的目标大小
   * @param splitTaskCreator 子任务创建器
   */
  FixedSizeSplitScanTaskIterator(
      T parentTask,
      long parentTaskLength,
      long splitSize,
      SplitScanTaskCreator<T> splitTaskCreator) {
    this.parentTask = parentTask;
    this.splitSize = splitSize;
    this.splitTaskCreator = splitTaskCreator;
    this.offset = 0;
    this.remainingLength = parentTaskLength;
  }

  /** 是否还有未切分的剩余长度。 */
  @Override
  public boolean hasNext() {
    return remainingLength > 0;
  }

  /**
   * 返回下一个切分子任务。
   *
   * <p>逻辑：取 min(splitSize, remainingLength) 作为本子任务长度，委托 splitTaskCreator 创建， 更新偏移与剩余长度。
   */
  @Override
  public T next() {
    long length = Math.min(splitSize, remainingLength);
    T splitTask = splitTaskCreator.create(parentTask, offset, length);
    offset += length;
    remainingLength -= length;
    return splitTask;
  }
}
