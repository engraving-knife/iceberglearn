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
 * 变更日志扫描任务：表示一次增量变更扫描中产出的一组行级变更（insert/delete）。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：在 {@link ScanTask} 基础上额外暴露变更类型、变更序号与提交快照 ID，使下游能够按 顺序回放表的行级变更。
 *
 * <p>设计意图：增量变更扫描（CDC 风格）需要保证变更可按确定的顺序应用，因此引入 {@link #changeOrdinal()} 作为全局排序依据，较低序号必须先应用。
 *
 * <p>上下游关系：由 {@link IncrementalChangelogScan} 规划产出，被引擎层用于行级 CDC 同步。
 */
public interface ChangelogScanTask extends ScanTask {
  /**
   * 返回本任务产出的变更类型（插入或删除）。
   *
   * @return 变更操作类型
   */
  ChangelogOperation operation();

  /**
   * 返回本任务变更的序号。
   *
   * <p>该序号指示变更的应用顺序：序号较小的变更必须先被应用，以保证最终状态一致。
   *
   * @return 变更序号
   */
  int changeOrdinal();

  /**
   * 返回变更所提交到的快照 ID。
   *
   * @return 提交快照 ID
   */
  long commitSnapshotId();
}
