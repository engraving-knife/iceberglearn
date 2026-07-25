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

import org.apache.iceberg.expressions.Expression;

/**
 * 将等值删除文件（equality delete files）转换为位置删除文件（position delete files）的动作。
 *
 * <p>所属模块：iceberg-api。本接口继承自 {@link SnapshotUpdate}，因此转换过程会产出新快照。
 *
 * <p>职责：选择目标等值删除文件，将其重写为位置删除文件，以改善读取时的删除应用效率 （位置删除在向量化和合并读取场景下通常更高效）。
 *
 * <p>设计意图：等值删除与位置删除各有性能权衡；提供该动作允许在合适的时机把等值删除物化为 位置删除，便于引擎优化后续读取。本接口仅定义契约，具体实现由引擎模块提供。
 *
 * <p>上下游关系：依赖 {@link SnapshotUpdate} 的快照提交能力；结果通过 {@link Result} 返回统计信息。
 */
public interface ConvertEqualityDeleteFiles
    extends SnapshotUpdate<ConvertEqualityDeleteFiles, ConvertEqualityDeleteFiles.Result> {

  /**
   * 设置用于筛选待转换等值删除文件的过滤器。
   *
   * <p>逻辑：该过滤器会被转换为分区过滤器（采用 inclusive projection）。任何可能包含匹配行的 文件都会被本动作处理，其对应等值删除文件将被转换为位置删除文件。
   *
   * @param expression 用于定位删除文件的 Iceberg 表达式
   * @return this，便于链式调用
   */
  ConvertEqualityDeleteFiles filter(Expression expression);

  /** 动作执行结果，包含执行摘要统计。 */
  interface Result {
    /**
     * 返回已被转换的等值删除文件数量。
     *
     * @return 已转换的等值删除文件数
     */
    int convertedEqualityDeleteFilesCount();

    /**
     * 返回本次新增的位置删除文件数量。
     *
     * @return 新增的位置删除文件数
     */
    int addedPositionDeleteFilesCount();
  }
}
