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
package org.apache.iceberg.actions;

import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;

/**
 * 快照表动作的基础接口（基于 Immutables 生成不可变实现）。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：为 {@link SnapshotTable} 动作定义 core 侧的基础契约与不可变结果类型 {@link Result}， 由 Immutables 注解处理器生成
 * {@code ImmutableSnapshotTable} 实现类。
 *
 * <p>设计意图：通过 Immutables 的 {@code @Value.Style} 统一控制生成类的可见性与 builder 可见性， 避免手写样板化代码。快照表动作指创建一个
 * Iceberg 表，其数据指向现有表的当前快照， 不复制数据，适用于快速分析或备份场景。
 *
 * <p>上下游关系：实现 {@link SnapshotTable}（iceberg-api），被具体引擎模块的快照表动作继承使用。
 */
@Value.Enclosing
@SuppressWarnings("ImmutablesStyle")
@Value.Style(
    typeImmutableEnclosing = "ImmutableSnapshotTable",
    visibility = ImplementationVisibility.PUBLIC,
    builderVisibility = BuilderVisibility.PUBLIC)
interface BaseSnapshotTable extends SnapshotTable {

  /** 快照表动作的不可变结果类型，继承 {@link SnapshotTable.Result}。 */
  @Value.Immutable
  interface Result extends SnapshotTable.Result {}
}
