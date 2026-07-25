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

import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Projections;

/**
 * 文件覆写 API：通过删除一组旧文件并添加一组新文件来更新表的某个数据片段。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>累积待新增的 {@link DataFile} 与待删除的 {@link DataFile}。
 *   <li>支持按行过滤表达式批量删除文件，实现幂等写入或更新/删除。
 *   <li>提供冲突检测与校验配置，保障非幂等覆写的隔离性。
 * </ul>
 *
 * <p>设计意图：默认采用幂等模式提交；亦可配置为"按行过滤覆写部分文件并保证不会有需要过滤的 新数据被并发加入"的强校验模式。提交时若检测到表已前进，会把变更应用到新的最新快照上重试。 通过
 * {@link Projections#inclusive(PartitionSpec)} 选候选文件、 {@link Projections#strict(PartitionSpec)}
 * 判定整文件删除，保证"整文件删除当且仅当其全部行 都匹配过滤条件"。
 *
 * <p>上下游关系：由 {@link Table#newOverwrite()} 创建；下游实现位于 core 模块， 落地为一次替换类快照提交。
 */
public interface OverwriteFiles extends SnapshotUpdate<OverwriteFiles> {
  /**
   * 按行过滤表达式删除文件。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>用 {@link Projections#inclusive(PartitionSpec)} 把行级表达式投影到分区级， 选出可能含匹配行的候选文件；
   *   <li>用 {@link Projections#strict(PartitionSpec)} 严格投影判定文件是否全部行都匹配， 全部匹配才整文件删除；
   *   <li>若某文件可能既含匹配行又含非匹配行，则抛 {@link ValidationException}。
   * </ul>
   *
   * @param expr 行级过滤表达式
   * @return this，便于链式调用
   * @throws ValidationException 存在"部分行匹配"的文件
   */
  OverwriteFiles overwriteByRowFilter(Expression expr);

  /**
   * 向表中新增一个数据文件。
   *
   * @param file 待新增的数据文件
   * @return this，便于链式调用
   */
  OverwriteFiles addFile(DataFile file);

  /**
   * 从表中删除一个数据文件。
   *
   * @param file 待删除的数据文件
   * @return this，便于链式调用
   */
  OverwriteFiles deleteFile(DataFile file);

  /**
   * 声明每个新增文件都必须匹配覆写过滤表达式。
   *
   * <p>逻辑：调用后，提交时会对每个新增文件做校验，确保其匹配覆写行过滤。用于保证幂等性： 即使重跑也不会留下"本应被过滤掉"的文件。
   *
   * @return this，便于链式调用
   */
  OverwriteFiles validateAddedFilesMatchOverwriteFilter();

  /**
   * 设置本操作读取所基于的快照 ID，校验将针对该快照之后的变更进行。
   *
   * <p>若未设置，则校验覆盖从初始快照到当前的所有祖先快照。
   *
   * @param snapshotId 基准快照 ID
   * @return this，便于链式调用
   */
  OverwriteFiles validateFromSnapshot(long snapshotId);

  /**
   * 启用/关闭校验阶段表达式绑定的大小写敏感性。
   *
   * @param caseSensitive 是否大小写敏感
   * @return this，便于链式调用
   */
  OverwriteFiles caseSensitive(boolean caseSensitive);

  /**
   * 设置冲突检测过滤器，用于校验并发新增的数据文件与删除文件是否冲突。
   *
   * @param conflictDetectionFilter 行级冲突检测表达式
   * @return this，便于链式调用
   */
  OverwriteFiles conflictDetectionFilter(Expression conflictDetectionFilter);

  /**
   * 启用"并发新增数据不冲突"校验。
   *
   * <p>逻辑：用于非幂等覆写提交。若在 {@link #validateFromSnapshot(long)} 指定的快照之后， 有并发操作新增了可能匹配 {@link
   * #conflictDetectionFilter(Expression)} 的文件，则本次覆写 提交失败。未设置冲突检测过滤器时，任何并发新增数据都会导致失败。
   *
   * @return this，便于链式调用
   */
  OverwriteFiles validateNoConflictingData();

  /**
   * 启用"并发删除不冲突"校验。
   *
   * <p>逻辑：非幂等覆写必需。若并发操作删除了本次正在覆写的文件，则必须中止提交，否则可能 "复活"被并发删除的行。校验基于 {@link
   * #conflictDetectionFilter(Expression)}，作用于 {@link #validateFromSnapshot(long)}
   * 之后的变更；若未设置冲突检测过滤器，则使用 {@link #overwriteByRowFilter(Expression)} 的行过滤来检查新删除文件，并确保通过 {@link
   * #deleteFile(DataFile)} 删除的文件不存在并发删除冲突。
   *
   * @return this，便于链式调用
   */
  OverwriteFiles validateNoConflictingDeletes();
}
