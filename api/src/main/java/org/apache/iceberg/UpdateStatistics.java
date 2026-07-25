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

/**
 * 更新表统计文件的 API。
 *
 * <p>所属模块：iceberg-api（表维护操作接口层）。
 *
 * <p>职责：按快照 ID 设置或移除表的 {@link StatisticsFile}，用于维护 Bloom Filter / 列统计等辅助统计信息的生命周期。
 *
 * <p>设计意图：通过 {@link #setStatistics} 替换某快照已有统计文件（若有），通过 {@link #removeStatistics} 移除指定快照的统计文件，{@link
 * #apply()} 返回更新后的统计 文件列表。
 *
 * <p>上下游关系：继承 {@link PendingUpdate}；由 core 模块实现，被维护作业调用。
 */
public interface UpdateStatistics extends PendingUpdate<List<StatisticsFile>> {
  /**
   * 为指定快照设置统计文件，替换该快照已有的统计文件（若存在）。
   *
   * @param snapshotId 快照 ID
   * @param statisticsFile 统计文件
   * @return this，便于链式调用
   */
  UpdateStatistics setStatistics(long snapshotId, StatisticsFile statisticsFile);

  /**
   * 移除指定快照的统计文件。
   *
   * @param snapshotId 快照 ID
   * @return this，便于链式调用
   */
  UpdateStatistics removeStatistics(long snapshotId);
}
