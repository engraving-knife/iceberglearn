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
package org.apache.iceberg.flink.source.split;

/**
 * 文件级说明：Iceberg source split 的状态枚举。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/split 子包）。
 *
 * <p>职责：定义 split 在分配过程中的状态：
 *
 * <ul>
 *   <li>{@link #UNASSIGNED}：尚未分配给任何 reader。
 *   <li>{@link #ASSIGNED}：已分配给 reader，正在或等待读取。
 *   <li>{@link #COMPLETED}：已读取完成。
 * </ul>
 *
 * <p>设计意图：通过统一的状态枚举，便于协调器维护 split 状态机， 实现重新分配、超时回收等策略。
 *
 * <p>上下游关系：上游为协调器创建 split 时初始化为 UNASSIGNED， 下游为 {@code SplitAssigner} 在分配与回收过程中更新状态。
 */
public enum IcebergSourceSplitStatus {
  /** 尚未分配给任何 reader。 */
  UNASSIGNED,
  /** 已分配给 reader，正在或等待读取。 */
  ASSIGNED,
  /** 已读取完成。 */
  COMPLETED
}
