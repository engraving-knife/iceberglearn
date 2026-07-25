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

import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.expressions.Term;

/**
 * 分区 spec 演化 API。
 *
 * <p>所属模块：iceberg-api（表更新操作接口层）。
 *
 * <p>职责：支持按源列名或表达式 term 添加分区字段、按名或 term 移除分区字段、重命名分区 字段，从而演化表的分区策略。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>提交时将变更应用到当前表元数据；与 SnapshotUpdate 不同，本接口在发生提交冲突时 不会重试，而是直接抛 {@link CommitFailedException}。
 *   <li>对同名/同 transform 的字段做冲突检测，避免演化过程中产生歧义。
 * </ul>
 *
 * <p>上下游关系：继承 {@link PendingUpdate}；由 core 模块实现，被引擎/用户调用以演化分区。
 */
public interface UpdatePartitionSpec extends PendingUpdate<PartitionSpec> {
  /**
   * 设置源 schema 列解析是否大小写敏感。
   *
   * @param isCaseSensitive 列解析是否大小写敏感
   * @return this，便于链式调用
   */
  UpdatePartitionSpec caseSensitive(boolean isCaseSensitive);

  /**
   * 按源列名添加一个 identity 分区字段（字段名与源列名相同）。
   *
   * <p>设计要点：源列通过 {@link Schema#findField(String)} 定位。
   *
   * @param sourceName 表 schema 中的源列名
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若该源列已有 identity 分区字段，或与其他增删/重命名冲突
   */
  UpdatePartitionSpec addField(String sourceName);

  /**
   * 按表达式 term 添加分区字段。
   *
   * <p>设计要点：若 term 含 transform 则使用该 transform，否则用 identity；新分区字段名 由源列名与 transform 拼出。
   *
   * @param term 表达式 term
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若该 transform+源列已存在分区字段，或与其他变更冲突
   */
  UpdatePartitionSpec addField(Term term);

  /**
   * 按表达式 term 添加分区字段，并指定分区字段名。
   *
   * @param name 分区字段名
   * @param term 分区 transform 表达式
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若该 transform+源列已存在、同名分区字段已存在，或与其他变更冲突
   */
  UpdatePartitionSpec addField(String name, Term term);

  /**
   * 按分区字段名移除分区字段。
   *
   * @param name 待移除的分区字段名
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若该名分区字段不存在，或与其他变更冲突
   */
  UpdatePartitionSpec removeField(String name);

  /**
   * 按 transform 表达式 term 移除分区字段。
   *
   * <p>设计要点：移除 transform 与源引用都匹配的分区字段；若 term 是无 transform 的引用， 则按 identity 处理。
   *
   * @param term 待移除的分区 transform 表达式
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若对应 transform+源列的分区字段不存在，或与其他变更冲突
   */
  UpdatePartitionSpec removeField(Term term);

  /**
   * 重命名分区 spec 中的字段。
   *
   * @param name 待重命名的分区字段名
   * @param newName 新名称
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若 name 不是 schema 中的列，或与其他变更冲突
   */
  UpdatePartitionSpec renameField(String name, String newName);
}
