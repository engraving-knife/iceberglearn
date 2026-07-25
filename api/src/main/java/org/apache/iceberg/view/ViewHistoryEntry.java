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
package org.apache.iceberg.view;

/**
 * 视图历史条目。
 *
 * <p>所属模块：iceberg-api。是 view 模块记录视图状态变更历史的单元，由 {@link View#history()} 返回。
 *
 * <p>职责：记录在某时间戳处，视图的当前版本被设置为某个版本 ID 这一变更事实。
 *
 * <p>设计意图：与表的快照历史类似，视图也维护变更时间线，便于审计与时间旅行。每条记录只保存 时间戳与版本 ID，保持轻量。
 *
 * <p>上下游关系：由 {@link View#history()} 返回；反映 {@link ViewVersion} 的演进。
 */
public interface ViewHistoryEntry {
  /**
   * 返回该变更发生的时间戳（毫秒）。
   *
   * @return 变更时间戳
   */
  long timestampMillis();

  /**
   * 返回变更后新的当前版本 ID。
   *
   * @return 新的当前版本 ID
   */
  int versionId();
}
