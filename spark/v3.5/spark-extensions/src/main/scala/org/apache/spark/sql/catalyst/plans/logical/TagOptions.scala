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
 * 快照引用（Tag）创建/替换时的可配置选项。
 *
 * <p>所属模块：iceberg-spark（spark-extensions 扩展包，为 Spark SQL 增加 Iceberg 专属
 * 逻辑算子，运行在 Spark Catalyst 优化器层）。
 *
 * <p>职责：承载创建或替换 Tag 时可选的两个参数——目标快照 ID 与引用保留时长，
 * 作为 {@link CreateOrReplaceTag} 算子的配置载体在逻辑计划阶段传递。
 *
 * <p>设计意图：用不可变样例类集中表达 Tag 选项，避免在算子签名中堆叠多个 Option 参数，
 * 便于后续扩展新的 Tag 配置项而不破坏已有调用方。
 *
 * <p>上下游关系：由 Spark SQL 解析层构造，传递给 {@link CreateOrReplaceTag} 逻辑算子，
 * 最终在物理执行层转换为对 Iceberg 表快照引用的操作。
 *
 * @param snapshotId 目标快照 ID，为 None 时使用表的当前快照
 * @param snapshotRefRetain Tag 引用的保留时长（毫秒），为 None 时使用表默认保留策略
 */
case class TagOptions(snapshotId: Option[Long], snapshotRefRetain: Option[Long])
