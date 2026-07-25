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

import java.io.Serializable;

/**
 * 表历史条目：记录表状态的一次变更。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：在指定时间戳上，把当前快照设置为指定的快照 ID。每条历史条目描述一次 "表当前快照切换"事件，是表元数据日志的基本单元。
 *
 * <p>设计意图：把"时间戳 → 快照 ID"的映射抽象为接口，便于不同实现（如内存对象、 序列化对象）共用同一套读取契约；实现 {@link Serializable} 以支持跨进程传递。
 *
 * <p>上下游关系：由表元数据中的历史日志维护，被时间旅行扫描、审计等场景读取。
 */
public interface HistoryEntry extends Serializable {
  /**
   * 返回本次变更发生的时间戳（毫秒）。
   *
   * @return 变更时间戳
   */
  long timestampMillis();

  /**
   * 返回本次变更设置的新当前快照 ID。
   *
   * @return 快照 ID
   */
  long snapshotId();
}
