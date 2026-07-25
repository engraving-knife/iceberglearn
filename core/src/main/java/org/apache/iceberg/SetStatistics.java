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
import java.util.Map;
import java.util.Optional;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 设置/移除统计文件操作（{@link UpdateStatistics} 的 core 实现）。
 *
 * <p>所属模块：iceberg-core。职责：按快照 id 增删 {@link StatisticsFile}，并生成新的表元数据提交。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Optional 表达删除：用 {@code Optional<StatisticsFile>} 在同一 map 中区分"设置"与"移除"。
 *   <li>快照 id 校验：要求 statisticsFile 的 snapshotId 与传入一致，避免错配。
 *   <li>幂等合并：apply 时基于当前元数据合并 statisticsToSet，生成最终统计文件列表。
 * </ul>
 *
 * <p>上下游关系：上游为 {@link Table#updateStatistics()}；下游通过 {@link TableOperations} 提交。
 */
public class SetStatistics implements UpdateStatistics {
  private final TableOperations ops;
  private final Map<Long, Optional<StatisticsFile>> statisticsToSet = Maps.newHashMap();

  /**
   * 构造方法：传入 TableOperations，初始化空变更映射。
   *
   * @param ops 表操作接口
   */
  public SetStatistics(TableOperations ops) {
    this.ops = ops;
  }

  /**
   * 为指定快照设置统计文件。
   *
   * <p>校验：statisticsFile 的 snapshotId 必须与传入 snapshotId 一致。
   *
   * @param snapshotId 快照 id
   * @param statisticsFile 统计文件
   * @return 当前 builder，支持链式调用
   */
  @Override
  public UpdateStatistics setStatistics(long snapshotId, StatisticsFile statisticsFile) {
    Preconditions.checkArgument(snapshotId == statisticsFile.snapshotId());
    statisticsToSet.put(snapshotId, Optional.of(statisticsFile));
    return this;
  }

  /**
   * 标记指定快照的统计文件为待删除（用 Optional.empty() 占位）。
   *
   * @param snapshotId 快照 id
   * @return 当前 builder，支持链式调用
   */
  @Override
  public UpdateStatistics removeStatistics(long snapshotId) {
    statisticsToSet.put(snapshotId, Optional.empty());
    return this;
  }

  /**
   * 计算应用所有变更后的统计文件列表（基于当前元数据）。
   *
   * @return 新的统计文件列表
   */
  @Override
  public List<StatisticsFile> apply() {
    return internalApply(ops.current()).statisticsFiles();
  }

  /** 提交统计文件变更到 TableOperations（不重试，单次提交）。 */
  @Override
  public void commit() {
    TableMetadata base = ops.current();
    TableMetadata newMetadata = internalApply(base);
    ops.commit(base, newMetadata);
  }

  /**
   * 基于给定 base 元数据合并 statisticsToSet，构造新的 TableMetadata。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>从 base 构建 {@link TableMetadata.Builder}；
   *   <li>遍历 statisticsToSet：present 调用 setStatistics，empty 调用 removeStatistics；
   *   <li>build 出新元数据。
   * </ol>
   *
   * @param base 基础元数据
   * @return 包含统计文件变更的新元数据
   */
  private TableMetadata internalApply(TableMetadata base) {
    TableMetadata.Builder builder = TableMetadata.buildFrom(base);
    statisticsToSet.forEach(
        (snapshotId, statistics) -> {
          if (statistics.isPresent()) {
            builder.setStatistics(snapshotId, statistics.get());
          } else {
            builder.removeStatistics(snapshotId);
          }
        });
    return builder.build();
  }
}
