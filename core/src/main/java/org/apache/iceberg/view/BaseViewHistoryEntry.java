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
package org.apache.iceberg.view;

import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;

/**
 * 文件级说明：视图历史记录条目的基础接口（Immutables 生成器基座）。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块，定义 View 在 core 层的可变/不可变模型）。
 *
 * <p>职责：描述一次视图状态变更——在指定时间戳，视图的当前版本被设置为某个 version-id。
 *
 * <p>设计意图：通过 Immutables 注解 {@code @Value.Immutable} 自动生成不可变实现 {@code
 * ImmutableViewHistoryEntry}，接口仅声明契约；{@code Base} 前缀表明这是生成器基座， 实际使用的是生成的不可变类。
 *
 * <p>上下游关系：继承 {@link ViewHistoryEntry}（api 层契约），被 {@link ViewMetadata} 聚合为视图历史列表，由 {@link
 * ViewHistoryEntryParser} 序列化/反序列化。
 */
@Value.Immutable
@SuppressWarnings("ImmutablesStyle")
@Value.Style(
    typeImmutable = "ImmutableViewHistoryEntry",
    visibility = ImplementationVisibility.PUBLIC,
    builderVisibility = BuilderVisibility.PUBLIC)
interface BaseViewHistoryEntry extends ViewHistoryEntry {}
