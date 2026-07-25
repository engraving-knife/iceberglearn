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
 * 增量变更日志扫描 API：用于规划表的行级变更（insert/delete）。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：作为 {@link IncrementalScan} 的特化接口，固定产出 {@link ChangelogScanTask} 及其任务组，用于支持 CDC 风格的增量同步。
 *
 * <p>设计意图：通过继承 {@link IncrementalScan} 复用扫描区间与任务组规划通用能力， 仅在泛型参数上把任务类型收敛为 {@link
 * ChangelogScanTask}，使接口简洁且类型安全。
 *
 * <p>上下游关系：由 {@link Table#newIncrementalChangelogScan()} 创建，被引擎层用于 行级变更同步、增量物化视图等场景。
 */
public interface IncrementalChangelogScan
    extends IncrementalScan<
        IncrementalChangelogScan, ChangelogScanTask, ScanTaskGroup<ChangelogScanTask>> {}
