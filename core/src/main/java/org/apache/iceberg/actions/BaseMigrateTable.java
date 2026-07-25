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
 * 迁移表动作的基础接口（基于 Immutables 生成不可变实现）。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：为 {@link MigrateTable} 动作定义 core 侧的基础契约与不可变结果类型 {@link Result}， 由 Immutables 注解处理器生成 {@code
 * ImmutableMigrateTable} 实现类。
 *
 * <p>设计意图：通过 Immutables 的 {@code @Value.Style} 统一控制生成类的可见性与 builder 可见性， 避免手写样板化代码。迁移动作指把非 Iceberg
 * 表（如 Hive/Spark 外部表）原地转换为 Iceberg 表， 保留原表名与数据位置。
 *
 * <p>上下游关系：实现 {@link MigrateTable}（iceberg-api），被具体引擎模块的迁移表动作继承使用。
 */
@Value.Enclosing
@SuppressWarnings("ImmutablesStyle")
@Value.Style(
    typeImmutableEnclosing = "ImmutableMigrateTable",
    visibility = ImplementationVisibility.PUBLIC,
    builderVisibility = BuilderVisibility.PUBLIC)
interface BaseMigrateTable extends MigrateTable {

  /** 迁移表动作的不可变结果类型，继承 {@link MigrateTable.Result}。 */
  @Value.Immutable
  interface Result extends MigrateTable.Result {}
}
