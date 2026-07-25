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
 * 文件级说明：批式扫描的配置接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 模块提供具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义表批式扫描（一次性读取整批数据）的配置契约，继承自通用 {@link Scan}。
 *   <li>支持基于快照 ID、引用（branch/tag）或时间戳进行时间旅行（time travel）读取。
 *   <li>产出的扫描任务以 {@link ScanTask} / {@link ScanTaskGroup} 形式组织。
 * </ul>
 *
 * <p>设计意图：将扫描的“配置”与“执行”分离，扫描对象是不可变的配置载体，每次配置变更 都返回新的 {@code BatchScan} 实例，便于链式调用与并发共享。
 *
 * <p>上下游关系：由 {@link Table#newBatchScan()} 创建，被引擎批式读取器（如 Spark 批作业）消费。
 */
public interface BatchScan extends Scan<BatchScan, ScanTask, ScanTaskGroup<ScanTask>> {
  /**
   * 返回本次扫描所读取数据的 {@link Table}。
   *
   * @return 扫描所属的表
   */
  Table table();

  /**
   * 基于当前扫描配置，创建一个使用指定快照 ID 的新 {@link BatchScan}（时间旅行读取）。
   *
   * @param snapshotId 目标快照 ID
   * @return 基于该快照 ID 的新扫描对象
   * @throws IllegalArgumentException 若快照不存在
   */
  BatchScan useSnapshot(long snapshotId);

  /**
   * 基于当前扫描配置，创建一个使用指定引用（branch 或 tag）的新 {@link BatchScan}。
   *
   * @param ref 引用名称
   * @return 基于该引用的新扫描对象
   * @throws IllegalArgumentException 若指定名称的引用不存在
   */
  BatchScan useRef(String ref);

  /**
   * 基于当前扫描配置，创建一个“时间旅行”到指定时间点的新 {@link BatchScan}：使用扫描分支上 （未设置分支时为 main 分支）不超过给定时间的最新快照。
   *
   * @param timestampMillis 时间戳（毫秒）
   * @return 基于该时间点快照的新扫描对象
   * @throws IllegalArgumentException 若找不到对应快照，或对 tag 引用尝试时间旅行
   */
  BatchScan asOfTime(long timestampMillis);

  /**
   * 返回本次扫描将使用的 {@link Snapshot}。
   *
   * <p>若未通过 {@link #asOfTime(long)} 或 {@link #useSnapshot(long)} 指定，则使用表的当前快照。
   *
   * @return 本次扫描使用的快照
   */
  Snapshot snapshot();
}
