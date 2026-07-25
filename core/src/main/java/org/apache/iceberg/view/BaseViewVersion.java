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

import javax.annotation.Nullable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.BuilderVisibility;
import org.immutables.value.Value.Style.ImplementationVisibility;

/**
 * 文件级说明：视图某个时间点版本的基础接口（Immutables 生成器基座）。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：描述视图的一个版本，对应一份视图元数据文件；由 Create/Replace 等视图操作产生。
 *
 * <p>设计意图：通过 Immutables 注解自动生成 {@code ImmutableViewVersion}； {@code operation()} 标记为
 * {@code @Value.Lazy} 懒求值，从 summary 中提取操作类型， 避免在构建时强制要求该字段。
 *
 * <p>上下游关系：继承 {@link ViewVersion}（api 层契约），被 {@link ViewMetadata} 作为版本列表管理，由 {@link
 * ViewVersionParser} 序列化/反序列化。
 */
@Value.Immutable
@SuppressWarnings("ImmutablesStyle")
@Value.Style(
    typeImmutable = "ImmutableViewVersion",
    visibility = ImplementationVisibility.PUBLIC,
    builderVisibility = BuilderVisibility.PUBLIC)
interface BaseViewVersion extends ViewVersion {

  /**
   * 从 summary 中懒提取本次版本对应的操作类型（如 create/replace）。
   *
   * <p>设计要点：标记 {@code @Value.Lazy}，仅在被访问时计算；要求 summary 中必须存在 "operation" 键，否则抛出参数异常。
   *
   * @return 操作类型字符串
   */
  @Override
  @Value.Lazy
  default String operation() {
    Preconditions.checkArgument(
        summary().containsKey("operation"), "Invalid view version summary, missing operation");
    return summary().get("operation");
  }

  /** 返回该版本使用的默认 catalog 名称，可为 null。 */
  @Override
  @Nullable
  String defaultCatalog();
}
