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
package org.apache.iceberg.flink.source;

/**
 * 文件级说明：Iceberg Flink 流式读取的起始策略枚举。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source 子包）。
 *
 * <p>职责：定义流式作业首次启动时如何选择起始快照， 决定是从历史数据全表扫描后切换到增量，还是直接从某个快照开始增量读取。
 *
 * <p>设计意图：通过枚举集中表达流式起始语义，避免散落在配置项中的多分支判断。
 *
 * <p>上下游关系：上游为 Flink SQL/表 API 配置项 {@code startup-mode}， 下游为 {@code ScanContext} 与连续增量枚举器 {@code
 * ContinuousIcebergEnumerator}。
 */
public enum StreamingStartingStrategy {
  /**
   * 先做常规全表扫描，然后切换到增量模式。
   *
   * <p>增量模式从当前快照（不含）开始。
   */
  TABLE_SCAN_THEN_INCREMENTAL,

  /**
   * 从最新快照（含）开始增量模式。
   *
   * <p>若此时表为空（无快照），则未来所有 append 快照都应被发现。
   */
  INCREMENTAL_FROM_LATEST_SNAPSHOT,

  /**
   * 从最早快照（含）开始增量模式。
   *
   * <p>若此时表为空（无快照），则未来所有 append 快照都应被发现。
   */
  INCREMENTAL_FROM_EARLIEST_SNAPSHOT,

  /** 从指定快照 ID（含）开始增量模式。 */
  INCREMENTAL_FROM_SNAPSHOT_ID,

  /**
   * 从指定时间戳对应的快照（含）开始增量模式。
   *
   * <p>若时间戳恰好落在两个快照之间，则从该时间戳之后的快照开始。
   */
  INCREMENTAL_FROM_SNAPSHOT_TIMESTAMP
}
