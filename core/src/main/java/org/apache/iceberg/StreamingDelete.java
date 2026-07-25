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
import org.apache.iceberg.expressions.Expression;

/**
 * 流式删除文件的 {@link DeleteFiles} 实现，避免一次性将完整 manifest 加载到内存。
 *
 * <p>所属模块：iceberg-core，定位为表数据删除操作（DELETE）的流式执行器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link DeleteFiles} 接口，对外提供按文件路径、文件对象或行级过滤表达式删除数据文件的能力；
 *   <li>继承 {@link MergingSnapshotProducer}，复用其合并快照、manifest 写入与提交重试逻辑；
 *   <li>在删除过程中采用流式处理 manifest，避免大表场景下因加载全部 manifest 而引发内存压力。
 * </ul>
 *
 * <p>设计意图：将"删除"建模为一种特殊的合并快照操作，复用基类 {@link MergingSnapshotProducer} 的提交重试 与冲突处理能力（默认最多重试 5 次，超过后抛出
 * {@link CommitFailedException}）。流式处理 manifest 旨在 在不影响正确性的前提下降低峰值内存占用，从而支撑大表的删除操作。
 *
 * <p>上下游关系：依赖 {@link TableOperations} 进行提交、依赖 {@link Snapshot} 校验基线； 被上层 API（如 {@code
 * Table#newDelete()}）构造并驱动；提交结果会写入新的元数据与 manifest 文件。
 */
public class StreamingDelete extends MergingSnapshotProducer<DeleteFiles> implements DeleteFiles {
  private boolean validateFilesToDeleteExist = false;

  /**
   * 构造一个流式删除操作。
   *
   * @param tableName 目标表名，用于日志与诊断
   * @param ops 目标表的 {@link TableOperations}，提供元数据读写与提交能力
   */
  protected StreamingDelete(String tableName, TableOperations ops) {
    super(tableName, ops);
  }

  /** 返回当前对象自身，用于支持链式 API 的类型约束。 */
  @Override
  protected DeleteFiles self() {
    return this;
  }

  /** 返回本次操作的数据操作类型标识，固定为 {@link DataOperations#DELETE}。 */
  @Override
  protected String operation() {
    return DataOperations.DELETE;
  }

  /**
   * 按数据文件路径删除单个文件。
   *
   * @param path 待删除数据文件的路径
   * @return 当前对象，支持链式调用
   */
  @Override
  public StreamingDelete deleteFile(CharSequence path) {
    delete(path);
    return this;
  }

  /**
   * 按数据文件对象删除单个文件。
   *
   * @param file 待删除的 {@link DataFile}
   * @return 当前对象，支持链式调用
   */
  @Override
  public StreamingDelete deleteFile(DataFile file) {
    delete(file);
    return this;
  }

  /**
   * 按行级过滤表达式删除数据。匹配该表达式的所有数据文件将在提交时被标记为删除。
   *
   * @param expr 行过滤表达式
   * @return 当前对象，支持链式调用
   */
  @Override
  public StreamingDelete deleteFromRowFilter(Expression expr) {
    deleteByRowFilter(expr);
    return this;
  }

  /**
   * 启用"待删除文件必须存在"的校验，提交前会校验本次声明删除的文件路径确实存在。
   *
   * @return 当前对象，支持链式调用
   */
  @Override
  public DeleteFiles validateFilesExist() {
    this.validateFilesToDeleteExist = true;
    return this;
  }

  /**
   * 指定本次删除操作的目标分支。
   *
   * @param branch 目标分支名
   * @return 当前对象，支持链式调用
   */
  @Override
  public StreamingDelete toBranch(String branch) {
    targetBranch(branch);
    return this;
  }

  /**
   * 提交前的校验逻辑。
   *
   * <p>逻辑：若调用方通过 {@link #validateFilesExist()} 开启了存在性校验，则调用 {@link #failMissingDeletePaths()}
   * 对缺失的删除路径直接抛出异常，避免提交一个"声明删除但实际不存在的文件" 的快照。
   *
   * @param base 提交所基于的当前表元数据
   * @param parent 父快照，用于冲突检测
   */
  @Override
  protected void validate(TableMetadata base, Snapshot parent) {
    if (validateFilesToDeleteExist) {
      failMissingDeletePaths();
    }
  }
}
