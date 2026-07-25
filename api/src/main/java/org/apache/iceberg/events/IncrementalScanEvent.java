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
package org.apache.iceberg.events;

import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.Expression;

/**
 * 增量扫描事件：在表执行增量扫描（增量读取一段时间内变更的数据）规划完成时发送给监听器。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与事件契约的最底层模块，被 core/引擎集成依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装一次增量扫描的关键参数：表名、起止快照 ID、过滤条件、投影 Schema、起始快照是否包含。
 *   <li>作为 {@link Listener} 的事件载体在 {@link Listeners#notifyAll(Object)} 中分发。
 * </ul>
 *
 * <p>设计意图：采用不可变值对象（final 字段 + 无 setter），保证事件在多监听器间分发的线程安全 与语义一致；与 {@link ScanEvent}
 * 区分，专用于"基于快照区间的增量"语义。
 *
 * <p>上下游关系：由 core 模块的扫描规划逻辑在增量扫描完成后构造并经 {@link Listeners} 分发； 被 SDK 使用方注册的 {@link Listener} 监听消费。
 */
public final class IncrementalScanEvent {
  private final String tableName;
  private final long fromSnapshotId;
  private final long toSnapshotId;
  private final Expression filter;
  private final Schema projection;
  private final boolean fromSnapshotInclusive;

  /**
   * 构造一个增量扫描事件。
   *
   * @param tableName 被扫描的表名
   * @param fromSnapshotId 增量起始快照 ID
   * @param toSnapshotId 增量终止快照 ID
   * @param filter 扫描过滤表达式，可为 null
   * @param projection 投影 Schema（仅读取指定列），可为 null
   * @param fromSnapshotInclusive 起始快照是否包含在结果内
   */
  public IncrementalScanEvent(
      String tableName,
      long fromSnapshotId,
      long toSnapshotId,
      Expression filter,
      Schema projection,
      boolean fromSnapshotInclusive) {
    this.tableName = tableName;
    this.fromSnapshotId = fromSnapshotId;
    this.toSnapshotId = toSnapshotId;
    this.filter = filter;
    this.projection = projection;
    this.fromSnapshotInclusive = fromSnapshotInclusive;
  }

  /** 返回被扫描的表名。 */
  public String tableName() {
    return tableName;
  }

  /** 返回增量起始快照 ID。 */
  public long fromSnapshotId() {
    return fromSnapshotId;
  }

  /** 返回增量终止快照 ID。 */
  public long toSnapshotId() {
    return toSnapshotId;
  }

  /** 返回扫描过滤表达式，可能为 null。 */
  public Expression filter() {
    return filter;
  }

  /** 返回投影 Schema，可能为 null。 */
  public Schema projection() {
    return projection;
  }

  /** 返回起始快照是否包含在增量结果内。 */
  public boolean isFromSnapshotInclusive() {
    return fromSnapshotInclusive;
  }
}
