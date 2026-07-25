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
package org.apache.iceberg.expressions;

import org.apache.iceberg.DataFile;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Types;

/**
 * COUNT(非空) 聚合：统计某字段非空值的个数。
 *
 * <p>所属模块：iceberg-api（表达式体系聚合分支中 Count 类的具体实现之一，继承 {@link CountAggregate}，与 {@link CountStar} 并列）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>行级求值（{@link #countFor(StructLike)}）：该字段值非 null 计 1，否则计 0。
 *   <li>文件级求值（{@link #countFor(DataFile)}）：用文件预统计的值计数减去 null 值计数， 得到非空值计数。
 *   <li>判断文件是否同时具备值计数与 null 计数（{@link #hasValue(DataFile)}）。
 * </ul>
 *
 * <p>设计意图：复用 DataFile 上已有的 valueCounts 与 nullValueCounts 列指标，避免逐行扫描； 当任一统计缺失时 hasValue 返回 false，由
 * {@link NullSafeAggregator} 标记整体失效。
 *
 * <p>上下游关系：由 {@link UnboundAggregate#bind} 在 COUNT 操作下构造； 被 {@link AggregateEvaluator} 通过 {@link
 * BoundAggregate.Aggregator} 驱动累积。
 *
 * @param <T> 聚合字段的 Java 类型
 */
public class CountNonNull<T> extends CountAggregate<T> {
  private final int fieldId;
  private final Types.NestedField field;

  protected CountNonNull(BoundTerm<T> term) {
    super(Operation.COUNT, term);
    this.field = term.ref().field();
    this.fieldId = field.fieldId();
  }

  /**
   * 行级求值：字段值非 null 计 1，否则计 0。
   *
   * @param row 一行数据
   * @return 0 或 1
   */
  @Override
  protected Long countFor(StructLike row) {
    return term().eval(row) != null ? 1L : 0L;
  }

  /**
   * 判断文件是否同时具备值计数与 null 值计数。
   *
   * @param file 数据文件
   * @return 两种计数都存在返回 true
   */
  @Override
  protected boolean hasValue(DataFile file) {
    return file.valueCounts().containsKey(fieldId) && file.nullValueCounts().containsKey(fieldId);
  }

  /**
   * 文件级求值：值计数减去 null 值计数，得到非空值计数。
   *
   * <p>逻辑：分别安全取值计数与 null 值计数，做安全减法（任一为 null 返回 null）。
   *
   * @param file 数据文件
   * @return 非空值计数，统计缺失时返回 null
   */
  @Override
  protected Long countFor(DataFile file) {
    return safeSubtract(
        safeGet(file.valueCounts(), fieldId), safeGet(file.nullValueCounts(), fieldId, 0L));
  }

  /**
   * 安全减法：两值均非 null 时返回差值，否则返回 null。
   *
   * @param left 被减数
   * @param right 减数
   * @return 差值，或 null
   */
  private Long safeSubtract(Long left, Long right) {
    if (left != null && right != null) {
      return left - right;
    }

    return null;
  }
}
