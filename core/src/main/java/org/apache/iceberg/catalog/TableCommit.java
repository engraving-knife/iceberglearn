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
package org.apache.iceberg.catalog;

import java.util.List;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.UpdateRequirement;
import org.apache.iceberg.UpdateRequirements;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.immutables.value.Value;

/**
 * 文件级说明：单表提交描述，封装一次表提交需要校验的要求与已应用的元数据变更。
 *
 * <p>所属模块：iceberg-core（catalog 包），作为多表事务/提交协议的核心数据载体，位于 提交语义层，向上被事务编排者使用，向下依赖 {@link
 * UpdateRequirement} 与 {@link MetadataUpdate}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>携带目标表标识 {@link TableIdentifier}。
 *   <li>提供 {@link UpdateRequirement} 列表，提交时用于校验基线元数据是否仍满足前提条件。
 *   <li>提供 {@link MetadataUpdate} 列表，描述本次提交实际应用的元数据变更。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不可变值对象：使用 Immutables（{@link Value.Immutable}）生成不可变实现，保证提交描述 在多表事务中可安全传递与比较。
 *   <li>要求与变更分离：要求（requirements）表达“提交成功的前置断言”，变更（updates）表达 “提交要落地的动作”，二者解耦便于校验与重放。
 *   <li>可由元数据差异派生：通过 {@link #create(TableIdentifier, TableMetadata, TableMetadata)}
 *       从基线/更新两份元数据自动推导要求与变更，避免调用方手工拼装。
 * </ul>
 *
 * <p>上下游关系：由事务实现（如多表提交/REST 提交）构造，提交时交给 Catalog 校验并应用； 依赖 {@link UpdateRequirements} 推导要求，依赖 {@link
 * TableMetadata#changes()} 推导变更。
 */
@Value.Immutable
public interface TableCommit {
  /** 返回本次提交的目标表标识。 */
  TableIdentifier identifier();

  /** 返回提交时需要校验的要求列表（基线断言）。 */
  List<UpdateRequirement> requirements();

  /** 返回本次提交已应用的元数据变更列表。 */
  List<MetadataUpdate> updates();

  /**
   * 由基线与更新两份元数据构造一个 {@link TableCommit}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 identifier、base、updated 非空。
   *   <li>校验 base 与 updated 的 uuid 一致，确保指向同一张表的元数据演进。
   *   <li>用 {@link UpdateRequirements#forUpdateTable} 基于 base 与 updated 的差异推导要求。
   *   <li>以 updated 的 {@link TableMetadata#changes()} 作为变更列表，构建不可变实例。
   * </ol>
   *
   * @param identifier 目标表标识
   * @param base 基线元数据，用于推导并校验要求
   * @param updated 更新后元数据，用于推导已应用的变更
   * @return 单表提交描述
   */
  static TableCommit create(TableIdentifier identifier, TableMetadata base, TableMetadata updated) {
    Preconditions.checkArgument(null != identifier, "Invalid table identifier: null");
    Preconditions.checkArgument(null != base && null != updated, "Invalid table metadata: null");
    Preconditions.checkArgument(
        base.uuid().equals(updated.uuid()),
        "UUID of base (%s) and updated (%s) table metadata does not match",
        base.uuid(),
        updated.uuid());

    return ImmutableTableCommit.builder()
        .identifier(identifier)
        .requirements(UpdateRequirements.forUpdateTable(base, updated.changes()))
        .updates(updated.changes())
        .build();
  }
}
