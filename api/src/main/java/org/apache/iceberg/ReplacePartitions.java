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
 * 按分区覆写 API：用新数据替换表中某些分区的全部文件（动态分区替换）。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>累积待新增的 {@link DataFile}。
 *   <li>提交时把所有"含有新增数据"的分区中的旧文件替换为新文件，生成新快照。
 *   <li>提供冲突检测与校验配置，保障非幂等替换的隔离性。
 * </ul>
 *
 * <p>设计意图：主要用于兼容 Hive 风格的"动态分区插入"语义。默认采用幂等校验模式； 也可通过 {@link #validateNoConflictingDeletes()} 与
 * {@link #validateNoConflictingData()} 配置为强校验模式，确保自 {@link #validateFromSnapshot(long)}
 * 起没有冲突的并发删除或新增。 提交冲突时会把变更应用到新的最新快照上重试。
 *
 * <p>上下游关系：由 {@link Table#newReplacePartitions()} 创建；下游实现位于 core 模块。 推荐优先使用 {@link OverwriteFiles}
 * 而非本接口做显式覆写。
 */
public interface ReplacePartitions extends SnapshotUpdate<ReplacePartitions> {
  /**
   * 向表中新增一个数据文件。
   *
   * @param file 待新增的数据文件
   * @return this，便于链式调用
   */
  ReplacePartitions addFile(DataFile file);

  /**
   * 校验本次操作不会替换任何分区，即纯追加。
   *
   * <p>逻辑：提交时校验所有新增文件落入的分区在当前表中尚不存在数据，否则提交失败。 用于在期望"只追加不替换"的场景下防止意外覆写。
   *
   * @return this，便于链式调用
   */
  ReplacePartitions validateAppendOnly();

  /**
   * 设置本操作校验所基于的快照 ID，校验将针对该快照之后的变更进行。
   *
   * <p>逻辑：应在提交前调用，把 snapshotId 设为本次操作开始读取表时的快照。若此后有并发操作 在待替换分区内新增/删除文件，校验会检测到并失败。未调用时从表起始快照开始校验。
   *
   * @param snapshotId 基准快照 ID（应设为操作开始读取表时的快照）
   * @return this，便于链式调用
   */
  ReplacePartitions validateFromSnapshot(long snapshotId);

  /**
   * 启用"并发删除不冲突"校验。
   *
   * <p>逻辑：非幂等替换分区操作必需。若并发操作删除了正在被替换的分区中的数据，本次替换必须 中止，否则可能"复活"被并发删除的行。
   *
   * @return this，便于链式调用
   */
  ReplacePartitions validateNoConflictingDeletes();

  /**
   * 启用"并发新增数据不冲突"校验。
   *
   * <p>逻辑：非幂等替换分区操作必需。若并发操作在正在被替换的分区中新增了数据，本次替换必须 中止，否则可能误删并发新增的行。
   *
   * @return this，便于链式调用
   */
  ReplacePartitions validateNoConflictingData();
}
