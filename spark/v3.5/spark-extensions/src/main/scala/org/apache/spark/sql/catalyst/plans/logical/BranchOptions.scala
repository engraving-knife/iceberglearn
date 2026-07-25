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

package org.apache.spark.sql.catalyst.plans.logical

/**
 * 分支保留策略选项，用于创建/替换分支时指定快照保留相关参数。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark 3.5 Catalyst 扩展，向 Spark 注册
 * Iceberg 特有的逻辑命令节点）。
 *
 * <p>职责：作为 {@code CreateOrReplaceBranch} 命令的附属参数容器，封装分支级别的
 * 快照保留策略（最大快照数、保留时长等），在执行分支操作时透传给 Iceberg
 * 的 {@link org.apache.iceberg.SnapshotRef} / {@link org.apache.iceberg.ManageSnapshots}。
 *
 * <p>设计意图：用不可变 case class 承载可选参数（Option[Long]），既便于模式匹配提取，
 * 也允许调用方只指定关心的部分策略，未指定项为 None 由 Iceberg 使用默认值。
 *
 * <p>上下游关系：由 ExtendedDataSourceV2Strategy 在解析 CreateOrReplaceBranch 时构造；
 * 被 CreateOrReplaceBranchExec 读取并传给底层 SnapshotManager。
 */
case class BranchOptions (snapshotId: Option[Long], numSnapshots: Option[Long],
                          snapshotRetain: Option[Long], snapshotRefRetain: Option[Long])
