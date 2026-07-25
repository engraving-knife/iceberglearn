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
package org.apache.iceberg.util;

import org.apache.iceberg.NullOrder;
import org.apache.iceberg.SortDirection;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.transforms.SortOrderVisitor;

/**
 * 复制排序字段的访问器实现：遍历一个 {@link SortOrder} 的所有排序字段，并以相同定义重新写入 另一个 {@link
 * SortOrder.Builder}，用于在不修改字段语义的前提下重建排序顺序。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：作为 {@link SortOrderVisitor} 的实现，把每种排序字段（普通字段、bucket、truncate、 year/month/day/hour
 * 等变换）原样转写到目标 Builder。
 *
 * <p>设计意图：采用访问者模式解耦“排序字段结构”与“对字段的操作”，使得复制、改写等操作无需 修改 SortOrder 自身。本类即“复制”操作的访问者实现。
 *
 * <p>上下游关系：由需要重建或派生 SortOrder 的工具（如 {@link SortOrderUtil}）调用； 依赖 api 模块的 {@link SortOrder}、{@link
 * Expressions} 与 {@link SortOrderVisitor}。
 */
class CopySortOrderFields implements SortOrderVisitor<Void> {
  private final SortOrder.Builder builder;

  CopySortOrderFields(SortOrder.Builder builder) {
    this.builder = builder;
  }

  /**
   * 访问普通排序字段，按原字段名、方向与空值顺序复制到目标 Builder。
   *
   * @param sourceName 源字段名
   * @param sourceId 源字段 ID（本方法未使用，由 Builder 自行解析）
   * @param direction 排序方向
   * @param nullOrder 空值排序规则
   * @return 固定返回 null（访问者模式约定）
   */
  @Override
  public Void field(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder) {
    builder.sortBy(sourceName, direction, nullOrder);
    return null;
  }

  /**
   * 访问 bucket 变换排序字段，按相同桶数复制。
   *
   * @param sourceName 源字段名
   * @param sourceId 源字段 ID（未使用）
   * @param numBuckets 桶数
   * @param direction 排序方向
   * @param nullOrder 空值排序规则
   * @return 固定返回 null
   */
  @Override
  public Void bucket(
      String sourceName,
      int sourceId,
      int numBuckets,
      SortDirection direction,
      NullOrder nullOrder) {
    builder.sortBy(Expressions.bucket(sourceName, numBuckets), direction, nullOrder);
    return null;
  }

  /**
   * 访问 truncate 变换排序字段，按相同截断宽度复制。
   *
   * @param sourceName 源字段名
   * @param sourceId 源字段 ID（未使用）
   * @param width 截断宽度
   * @param direction 排序方向
   * @param nullOrder 空值排序规则
   * @return 固定返回 null
   */
  @Override
  public Void truncate(
      String sourceName, int sourceId, int width, SortDirection direction, NullOrder nullOrder) {
    builder.sortBy(Expressions.truncate(sourceName, width), direction, nullOrder);
    return null;
  }

  /**
   * 访问 year 变换排序字段，复制到目标 Builder。
   *
   * @param sourceName 源字段名
   * @param sourceId 源字段 ID（未使用）
   * @param direction 排序方向
   * @param nullOrder 空值排序规则
   * @return 固定返回 null
   */
  @Override
  public Void year(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder) {
    builder.sortBy(Expressions.year(sourceName), direction, nullOrder);
    return null;
  }

  /**
   * 访问 month 变换排序字段，复制到目标 Builder。
   *
   * @param sourceName 源字段名
   * @param sourceId 源字段 ID（未使用）
   * @param direction 排序方向
   * @param nullOrder 空值排序规则
   * @return 固定返回 null
   */
  @Override
  public Void month(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder) {
    builder.sortBy(Expressions.month(sourceName), direction, nullOrder);
    return null;
  }

  /**
   * 访问 day 变换排序字段，复制到目标 Builder。
   *
   * @param sourceName 源字段名
   * @param sourceId 源字段 ID（未使用）
   * @param direction 排序方向
   * @param nullOrder 空值排序规则
   * @return 固定返回 null
   */
  @Override
  public Void day(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder) {
    builder.sortBy(Expressions.day(sourceName), direction, nullOrder);
    return null;
  }

  /**
   * 访问 hour 变换排序字段，复制到目标 Builder。
   *
   * @param sourceName 源字段名
   * @param sourceId 源字段 ID（未使用）
   * @param direction 排序方向
   * @param nullOrder 空值排序规则
   * @return 固定返回 null
   */
  @Override
  public Void hour(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder) {
    builder.sortBy(Expressions.hour(sourceName), direction, nullOrder);
    return null;
  }
}
