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

import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Objects;

/**
 * 文件级说明：Iceberg 流式 source 枚举器的位置标记，记录已枚举到的快照。
 *
 * <p>所属模块：iceberg-flink（source/enumerator 子包），用于流式 source 的增量读取位置追踪。
 *
 * <p>职责：持有 snapshotId 和 snapshotTimestampMs，标识 enumerator 已处理到哪个快照。
 *
 * <p>设计意图：不可变值对象 + 静态工厂方法。empty() 表示初始位置（未枚举任何快照）， of() 创建具体位置。主要用于 enumerator 的状态恢复和日志记录。
 *
 * <p>上下游关系：被 {@link ContinuousSplitPlanner}、{@link AbstractIcebergEnumerator} 使用， 作为 {@link
 * IcebergEnumeratorState} 的一部分被序列化和恢复。
 */
class IcebergEnumeratorPosition {
  private final Long snapshotId;
  // Track snapshot timestamp mainly for info logging
  private final Long snapshotTimestampMs;

  /** 返回空位置（表示尚未枚举任何快照）。 */
  static IcebergEnumeratorPosition empty() {
    return new IcebergEnumeratorPosition(null, null);
  }

  /** 创建指定快照 id 和时间戳的位置。 */
  static IcebergEnumeratorPosition of(long snapshotId, Long snapshotTimestampMs) {
    return new IcebergEnumeratorPosition(snapshotId, snapshotTimestampMs);
  }

  private IcebergEnumeratorPosition(Long snapshotId, Long snapshotTimestampMs) {
    this.snapshotId = snapshotId;
    this.snapshotTimestampMs = snapshotTimestampMs;
  }

  /** 判断是否为空位置（snapshotId 为 null）。 */
  boolean isEmpty() {
    return snapshotId == null;
  }

  Long snapshotId() {
    return snapshotId;
  }

  Long snapshotTimestampMs() {
    return snapshotTimestampMs;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("snapshotId", snapshotId)
        .add("snapshotTimestampMs", snapshotTimestampMs)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(snapshotId, snapshotTimestampMs);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IcebergEnumeratorPosition other = (IcebergEnumeratorPosition) o;
    return Objects.equal(snapshotId, other.snapshotId())
        && Objects.equal(snapshotTimestampMs, other.snapshotTimestampMs());
  }
}
