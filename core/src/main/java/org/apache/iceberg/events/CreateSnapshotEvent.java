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

import java.util.Map;

/**
 * 文件级说明：表快照创建事件，记录一次提交生成新快照的关键信息。
 *
 * <p>所属模块：iceberg-core（events 包），属于 Iceberg 事件通知机制的数据载体， 位于事件生产层，由提交流程在生成新快照时发出。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载表名、操作类型（append/replace/overwrite/delete 等）、新快照 id、序列号与摘要。
 *   <li>作为不可变值对象，供监听器（Listener）订阅并据此触发下游动作（如通知、审计、缓存失效）。
 * </ul>
 *
 * <p>设计意图：使用 final 不可变类与 final 字段，保证事件在多线程监听链路中安全传递； 事件只携带轻量摘要，避免持有表元数据等大对象。
 *
 * <p>上下游关系：由快照提交逻辑在创建快照后构造并发布；被注册的事件监听器消费。
 */
public final class CreateSnapshotEvent {
  private final String tableName;
  private final String operation;
  private final long snapshotId;
  private final long sequenceNumber;
  private final Map<String, String> summary;

  /**
   * 构造一个快照创建事件。
   *
   * @param tableName 表名
   * @param operation 触发本次快照的操作类型
   * @param snapshotId 新生成的快照 id
   * @param sequenceNumber 表的序列号
   * @param summary 快照摘要（如新增/删除数据文件数等）
   */
  public CreateSnapshotEvent(
      String tableName,
      String operation,
      long snapshotId,
      long sequenceNumber,
      Map<String, String> summary) {
    this.tableName = tableName;
    this.operation = operation;
    this.snapshotId = snapshotId;
    this.sequenceNumber = sequenceNumber;
    this.summary = summary;
  }

  /** 返回表名。 */
  public String tableName() {
    return tableName;
  }

  /** 返回触发本次快照的操作类型。 */
  public String operation() {
    return operation;
  }

  /** 返回新生成的快照 id。 */
  public long snapshotId() {
    return snapshotId;
  }

  /** 返回表的序列号。 */
  public long sequenceNumber() {
    return sequenceNumber;
  }

  /** 返回快照摘要信息。 */
  public Map<String, String> summary() {
    return summary;
  }
}
