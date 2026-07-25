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

import org.apache.iceberg.expressions.Expression;

/**
 * 文件级说明：行级变更（row-level changes）编码 API 接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>累积数据文件与删除文件的变更，生成新的 {@link Snapshot} 并提交为当前快照。
 *   <li>支持行级 UPDATE/DELETE/MERGE 操作产出的 data + delete 文件组合提交。
 *   <li>提供丰富的并发冲突校验配置：校验引用数据文件存在性、冲突检测过滤、并发数据/删除文件冲突检测。
 * </ul>
 *
 * <p>设计意图：行级变更（如 COPY-ON-WRITE 或 MERGE-ON-READ 的 DELETE/UPDATE）通常同时产出
 * 新数据文件（重写的行）和删除文件（标记删除的行）。本接口将这些变更原子提交，并通过冲突检测 过滤器与多级校验保障正确性。提交时变更应用到最新快照，冲突时重试。可选的 {@link
 * #validateNoConflictingDataFiles()} 与 {@link #validateNoConflictingDeleteFiles()}
 * 分别控制可串行化隔离级别下的数据文件与删除文件冲突检测。
 *
 * <p>上下游关系：由 {@link Table#newRowDelta()} 创建；被引擎的行级 UPDATE/DELETE/MERGE 操作使用。
 */
public interface RowDelta extends SnapshotUpdate<RowDelta> {
  /**
   * 向表中添加一个 {@link DataFile}（含待插入的行）。
   *
   * @param inserts 含待插入行的数据文件
   * @return this，便于链式调用
   */
  RowDelta addRows(DataFile inserts);

  /**
   * 向表中添加一个 {@link DeleteFile}（含待删除的行）。
   *
   * @param deletes 含待删除行的删除文件
   * @return this，便于链式调用
   */
  RowDelta addDeletes(DeleteFile deletes);

  /**
   * 设置本操作中任何读取所使用的快照 ID。
   *
   * <p>校验将检查该快照 ID 之后的变更。若未设置，则校验从表初始快照到当前的所有祖先快照。
   *
   * @param snapshotId 快照 ID
   * @return this，便于链式调用
   */
  RowDelta validateFromSnapshot(long snapshotId);

  /**
   * 启用或禁用校验阶段表达式绑定的大小写敏感性。
   *
   * @param caseSensitive 为 true 时表达式绑定区分大小写
   * @return this，便于链式调用
   */
  RowDelta caseSensitive(boolean caseSensitive);

  /**
   * 添加本 RowDelta 成功提交所必需的、不能被并发提交移除的数据文件路径。
   *
   * <p>若自 {@link #validateFromSnapshot(long)} 指定快照以来，任何路径已被并发提交移除， 操作将抛出 {@link
   * org.apache.iceberg.exceptions.ValidationException}。
   *
   * <p>默认仅校验 rewrite 和 overwrite 提交。若需对 delete 提交也进行校验，需调用 {@link #validateDeletedFiles()}。
   *
   * @param referencedFiles 被位置删除文件引用的数据文件路径
   * @return this，便于链式调用
   */
  RowDelta validateDataFilesExist(Iterable<? extends CharSequence> referencedFiles);

  /**
   * 启用校验：确认传给 {@link #validateDataFilesExist(Iterable)} 的引用数据文件未被 delete 操作移除。
   *
   * <p>若某数据文件的行已被位置删除文件删除，并发地重写或覆写该数据文件会"复活"这些行。通常允许
   * 删除数据文件，但某些事务场景（先读后重新追加行）需要校验删除。本方法用于事务场景下的删除校验。
   *
   * @return this，便于链式调用
   */
  RowDelta validateDeletedFiles();

  /**
   * 设置冲突检测过滤器，用于校验并发新增的数据与删除文件是否冲突。
   *
   * <p>若不调用，默认使用 true 字面量作为冲突检测过滤器（即匹配所有行）。
   *
   * @param conflictDetectionFilter 针对表中行的表达式
   * @return this，便于链式调用
   */
  RowDelta conflictDetectionFilter(Expression conflictDetectionFilter);

  /**
   * 启用校验：确认并发新增的数据文件不与本提交的操作冲突。
   *
   * <p>当表被查询以决定删除/追加哪些文件时应调用本方法。若并发操作在数据读取后提交了新文件， 且该文件可能包含匹配冲突检测过滤器的行，本操作在重试时会检测到并失败。
   *
   * <p>调用本方法是维护 update/delete 操作可串行化隔离级别的必要条件；否则隔离级别为快照隔离。
   *
   * <p>校验使用传给 {@link #conflictDetectionFilter(Expression)} 的过滤器，应用于 {@link
   * #validateFromSnapshot(long)} 指定快照之后的操作。
   *
   * @return this，便于链式调用
   */
  RowDelta validateNoConflictingDataFiles();

  /**
   * 启用校验：确认并发新增的删除文件不与本提交的操作冲突。
   *
   * <p>当表被查询以产出 UPDATE 和 MERGE 操作的 row delta 时必须调用本方法（无论隔离级别）。 DELETE 操作无需调用，因为并发删除同一条记录是允许的。
   *
   * <p>校验使用传给 {@link #conflictDetectionFilter(Expression)} 的过滤器，应用于 {@link
   * #validateFromSnapshot(long)} 指定快照之后的操作。
   *
   * @return this，便于链式调用
   */
  RowDelta validateNoConflictingDeleteFiles();
}
