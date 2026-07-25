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

import org.apache.iceberg.expressions.ResidualEvaluator;

/**
 * 位置删除（position deletes）扫描任务的基础实现。
 *
 * <p>所属模块：iceberg-core（扫描任务实现层，扩展 api 中定义的 {@link PositionDeletesScanTask} 接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载一个 {@link DeleteFile}（位置删除文件）及其 schema、分区、残留表达式，作为扫描引擎 消费的最小单位。
 *   <li>通过实现 {@link SplittableScanTask}，支持按字节区间把任务切分为 {@link SplitPositionDeletesScanTask}
 *       子任务，便于并行执行。
 * </ul>
 *
 * <p>设计意图：位置删除文件可按 offset 切分（每个 split 对应文件的一段字节区间）， 故实现 SplittableScanTask；{@link #newSplitTask}
 * 委托给具体的 SplitPositionDeletesScanTask 类型，保证切分后的子任务类型与 changelog 合并逻辑一致。
 *
 * <p>上下游关系：由 position deletes 扫描（{@code PositionDeletesScan}）构造，被引擎消费； 切分后由 {@link
 * SplitPositionDeletesScanTask} 表示子任务。
 */
class BasePositionDeletesScanTask extends BaseContentScanTask<PositionDeletesScanTask, DeleteFile>
    implements PositionDeletesScanTask, SplittableScanTask<PositionDeletesScanTask> {

  /**
   * 构造一个位置删除扫描任务。
   *
   * @param file 位置删除文件
   * @param schemaString 序列化后的 schema JSON
   * @param specString 序列化后的分区规格 JSON
   * @param evaluator 残留表达式求值器
   */
  BasePositionDeletesScanTask(
      DeleteFile file, String schemaString, String specString, ResidualEvaluator evaluator) {
    super(file, schemaString, specString, evaluator);
  }

  /** 返回自身，用于泛型 self() 模式回传具体类型。 */
  @Override
  protected BasePositionDeletesScanTask self() {
    return this;
  }

  /**
   * 基于父任务和字节区间创建一个切分子任务。
   *
   * @param parentTask 父任务
   * @param offset 子任务起始字节偏移
   * @param length 子任务字节长度
   * @return 新的切分子任务
   */
  @Override
  protected PositionDeletesScanTask newSplitTask(
      PositionDeletesScanTask parentTask, long offset, long length) {
    return new SplitPositionDeletesScanTask(parentTask, offset, length);
  }
}
