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

import java.util.List;
import java.util.NoSuchElementException;
import org.apache.iceberg.util.ArrayUtil;

/**
 * 基于已知偏移量（如 Parquet row group 偏移）切分扫描任务的迭代器。
 *
 * <p>所属模块：iceberg-core（扫描任务切分层）。
 *
 * <p>职责：根据给定的偏移量数组把一个父任务切分为多个子任务，每个子任务覆盖一个偏移区间。
 *
 * <p>设计意图：某些文件格式（如 Parquet）有天然的切分点（row group 边界），按这些边界切分 可以避免在 row group
 * 中间断开导致的额外读取开销。本迭代器接收偏移量列表，计算每个 split 的 (offset, length)，并通过 {@link SplitScanTaskCreator} 创建子任务。
 *
 * <p>上下游关系：由 {@link SplittableScanTask} 的实现类在需要按已知偏移切分时使用。
 *
 * @param <T> 扫描任务类型
 */
class OffsetsAwareSplitScanTaskIterator<T extends ScanTask> implements SplitScanTaskIterator<T> {
  private final T parentTask;
  private final SplitScanTaskCreator<T> splitTaskCreator;
  private final long[] offsets;
  private final long[] splitSizes;
  private int splitIndex = 0;

  /**
   * 构造一个基于偏移量切分的迭代器（List 版本）。
   *
   * @param parentTask 父任务
   * @param parentTaskLength 父任务总长度
   * @param offsets 切分偏移量列表
   * @param splitTaskCreator 子任务创建器
   */
  OffsetsAwareSplitScanTaskIterator(
      T parentTask,
      long parentTaskLength,
      List<Long> offsets,
      SplitScanTaskCreator<T> splitTaskCreator) {
    this(parentTask, parentTaskLength, ArrayUtil.toLongArray(offsets), splitTaskCreator);
  }

  /**
   * 构造一个基于偏移量切分的迭代器（数组版本）。
   *
   * <p>逻辑：根据相邻偏移量之差计算每个 split 的长度；最后一个 split 的长度为父任务总长度 减去最后一个偏移量。
   *
   * @param parentTask 父任务
   * @param parentTaskLength 父任务总长度
   * @param offsets 切分偏移量数组
   * @param splitTaskCreator 子任务创建器
   */
  OffsetsAwareSplitScanTaskIterator(
      T parentTask,
      long parentTaskLength,
      long[] offsets,
      SplitScanTaskCreator<T> splitTaskCreator) {
    this.parentTask = parentTask;
    this.splitTaskCreator = splitTaskCreator;
    this.offsets = offsets;
    this.splitSizes = new long[offsets.length];
    if (offsets.length > 0) {
      int lastIndex = offsets.length - 1;
      for (int index = 0; index < lastIndex; index++) {
        splitSizes[index] = offsets[index + 1] - offsets[index];
      }
      splitSizes[lastIndex] = parentTaskLength - offsets[lastIndex];
    }
  }

  /** 是否还有未消费的 split。 */
  @Override
  public boolean hasNext() {
    return splitIndex < splitSizes.length;
  }

  /**
   * 返回下一个切分子任务。
   *
   * @return 下一个子任务
   * @throws NoSuchElementException 若没有更多 split
   */
  @Override
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    long offset = offsets[splitIndex];
    long splitSize = splitSizes[splitIndex];
    splitIndex += 1; // create 1 split per offset
    return splitTaskCreator.create(parentTask, offset, splitSize);
  }
}
