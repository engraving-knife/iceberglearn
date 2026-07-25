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
package org.apache.iceberg.flink.source.enumerator;

import java.util.Collection;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 一次连续枚举的结果，包含发现的 split 与起止快照位置。
 *
 * <p>所属模块：iceberg-flink（source enumerator 侧）。
 *
 * <p>职责：封装增量/初始扫描产出的 split 集合及对应的 from/to 枚举位置，供连续枚举器追踪消费进度。
 *
 * <p>设计意图：不可变值对象，fromPosition 可为 null（首次枚举），toPosition 不可为 null（其快照 id/时间戳可空）。
 *
 * <p>上下游关系：由 {@link ContinuousSplitPlannerImpl} 产出，被 {@link ContinuousIcebergEnumerator} 消费。
 */
class ContinuousEnumerationResult {
  private final Collection<IcebergSourceSplit> splits;
  private final IcebergEnumeratorPosition fromPosition;
  private final IcebergEnumeratorPosition toPosition;

  /**
   * 构造枚举结果。
   *
   * @param splits split 集合（不可为 null，可为空）
   * @param fromPosition 起始位置（可为 null）
   * @param toPosition 结束位置（不可为 null，其快照 id/时间戳可空）
   */
  ContinuousEnumerationResult(
      Collection<IcebergSourceSplit> splits,
      IcebergEnumeratorPosition fromPosition,
      IcebergEnumeratorPosition toPosition) {
    Preconditions.checkArgument(splits != null, "Invalid to splits collection: null");
    Preconditions.checkArgument(toPosition != null, "Invalid end position: null");
    this.splits = splits;
    this.fromPosition = fromPosition;
    this.toPosition = toPosition;
  }

  /** 返回发现的 split 集合。 */
  public Collection<IcebergSourceSplit> splits() {
    return splits;
  }

  /** 返回起始枚举位置（首次为 null）。 */
  public IcebergEnumeratorPosition fromPosition() {
    return fromPosition;
  }

  /** 返回结束枚举位置。 */
  public IcebergEnumeratorPosition toPosition() {
    return toPosition;
  }
}
