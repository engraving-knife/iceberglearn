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

import java.io.Serializable;
import java.util.Collection;
import javax.annotation.Nullable;
import org.apache.flink.annotation.Internal;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;

/**
 * 文件级说明：协调器（Enumerator）的 checkpoint 状态。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/enumerator 子包）。
 *
 * <p>职责：保存协调器在 checkpoint 时刻的全部状态：
 *
 * <ul>
 *   <li>最近枚举位置 {@link IcebergEnumeratorPosition}。
 *   <li>尚未分配的 pending splits。
 *   <li>历史枚举 split 数数组，用于恢复限流策略。
 * </ul>
 *
 * <p>设计意图：实现 {@link Serializable}，便于写入 Flink checkpoint； 通过不可变字段保证状态一致性。
 *
 * <p>上下游关系：上游为协调器在 checkpoint 时构造， 下游为协调器恢复时反序列化后重建内部状态。
 */
@Internal
public class IcebergEnumeratorState implements Serializable {
  @Nullable private final IcebergEnumeratorPosition lastEnumeratedPosition;
  private final Collection<IcebergSourceSplitState> pendingSplits;
  private int[] enumerationSplitCountHistory;

  /** 仅含 pending splits 的构造，位置与历史为空。 */
  public IcebergEnumeratorState(Collection<IcebergSourceSplitState> pendingSplits) {
    this(null, pendingSplits);
  }

  /** 含位置与 pending splits 的构造，历史为空数组。 */
  public IcebergEnumeratorState(
      @Nullable IcebergEnumeratorPosition lastEnumeratedPosition,
      Collection<IcebergSourceSplitState> pendingSplits) {
    this(lastEnumeratedPosition, pendingSplits, new int[0]);
  }

  /** 全参构造，含位置、pending splits 与枚举历史。 */
  public IcebergEnumeratorState(
      @Nullable IcebergEnumeratorPosition lastEnumeratedPosition,
      Collection<IcebergSourceSplitState> pendingSplits,
      int[] enumerationSplitCountHistory) {
    this.lastEnumeratedPosition = lastEnumeratedPosition;
    this.pendingSplits = pendingSplits;
    this.enumerationSplitCountHistory = enumerationSplitCountHistory;
  }

  @Nullable
  public IcebergEnumeratorPosition lastEnumeratedPosition() {
    return lastEnumeratedPosition;
  }

  public Collection<IcebergSourceSplitState> pendingSplits() {
    return pendingSplits;
  }

  public int[] enumerationSplitCountHistory() {
    return enumerationSplitCountHistory;
  }
}
