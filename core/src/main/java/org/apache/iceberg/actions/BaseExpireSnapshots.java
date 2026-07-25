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
 * 过期快照动作的基础接口（基于 Immutables 生成不可变实现）。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：为 {@link ExpireSnapshots} 动作定义 core 侧的基础契约与不可变结果类型 {@link Result}，由 Immutables 注解处理器生成
 * {@code ImmutableExpireSnapshots} 实现类。
 *
 * <p>设计意图：通过 Immutables 的 {@code @Value.Style} 统一控制生成类的可见性与 builder 可见性，
 * 避免手写样板化代码。过期快照会移除指定保留期之前的旧快照及其不再被引用的文件。
 *
 * <p>上下游关系：实现 {@link ExpireSnapshots}（iceberg-api），被具体引擎模块的过期快照动作继承使用。
 */
@Value.Enclosing
@SuppressWarnings("ImmutablesStyle")
@Value.Style(
    typeImmutableEnclosing = "ImmutableExpireSnapshots",
    visibility = ImplementationVisibility.PUBLIC,
    builderVisibility = BuilderVisibility.PUBLIC)
interface BaseExpireSnapshots extends ExpireSnapshots {

  /**
   * 过期快照动作的不可变结果类型，继承 {@link ExpireSnapshots.Result}。
   *
   * <p>重写 {@code deletedStatisticsFilesCount} 并提供默认值，确保统计文件删除计数有缺省值。
   */
  @Value.Immutable
  interface Result extends ExpireSnapshots.Result {
    @Override
    @Value.Default
    default long deletedStatisticsFilesCount() {
      return ExpireSnapshots.Result.super.deletedStatisticsFilesCount();
    }
  }
}
