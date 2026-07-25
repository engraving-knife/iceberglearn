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
package org.apache.iceberg.transforms;

import java.util.List;
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortDirection;
import org.apache.iceberg.SortField;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 排序规约（SortOrder）访问者接口：按排序字段的变换类型分派回调。
 *
 * <p>所属模块：iceberg-api（被 core 与各引擎用于遍历排序字段并生成引擎特定的排序输出）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为 field（identity/null）/bucket/truncate/year/month/day/hour/unknown 各类变换提供回调方法， 每个回调携带排序方向与
 *       null 顺序。
 *   <li>提供静态 {@link #visit(SortOrder, SortOrderVisitor)} 遍历排序规约的所有字段。
 * </ul>
 *
 * <p>设计意图：访问者模式把"变换类型"与"对变换的处理"解耦；与 {@link PartitionSpecVisitor} 类似， 但额外携带 {@link SortDirection} 与
 * {@link NullOrder}，因为排序需要明确方向与 null 处理。
 *
 * <p>上下游关系：被 core 的扫描规划、各引擎的排序下推使用；输入依赖 {@link SortOrder}、 {@link SortField}、{@link Schema}。
 *
 * @param <T> 访问者回调的返回类型
 */
public interface SortOrderVisitor<T> {

  /**
   * 访问 identity/null 排序字段。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  T field(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder);

  /**
   * 访问 bucket 排序字段。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param width 桶数量
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  T bucket(
      String sourceName, int sourceId, int width, SortDirection direction, NullOrder nullOrder);

  /**
   * 访问 truncate 排序字段。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param width 截断宽度
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  T truncate(
      String sourceName, int sourceId, int width, SortDirection direction, NullOrder nullOrder);

  /**
   * 访问 year 排序字段。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  T year(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder);

  /**
   * 访问 month 排序字段。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  T month(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder);

  /**
   * 访问 day 排序字段。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  T day(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder);

  /**
   * 访问 hour 排序字段。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  T hour(String sourceName, int sourceId, SortDirection direction, NullOrder nullOrder);

  /**
   * 访问未知变换的排序字段，默认抛异常。
   *
   * @param sourceName 源列名
   * @param sourceId 源列 ID
   * @param transform 变换的字符串表示
   * @param direction 排序方向
   * @param nullOrder null 顺序
   * @return 回调结果
   */
  default T unknown(
      String sourceName,
      int sourceId,
      String transform,
      SortDirection direction,
      NullOrder nullOrder) {
    throw new UnsupportedOperationException(
        String.format("Unknown transform %s is not supported", transform));
  }

  /**
   * 遍历排序规约的所有字段并收集访问结果。
   *
   * <p>逻辑：按 sortOrder.fields() 顺序逐个处理，查源列名后按 transform 类型分派到 visitor 的对应方法； transform 为 null 或
   * Identity 走 field 回调；其余按 Bucket/Truncate/时间粒度/UnknownTransform 分派。
   *
   * @param sortOrder 待遍历的排序规约
   * @param visitor 访问者
   * @param <R> 返回类型
   * @return 每个字段访问结果的列表
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  static <R> List<R> visit(SortOrder sortOrder, SortOrderVisitor<R> visitor) {
    Schema schema = sortOrder.schema();
    List<R> results = Lists.newArrayListWithExpectedSize(sortOrder.fields().size());

    for (SortField field : sortOrder.fields()) {
      String sourceName = schema.findColumnName(field.sourceId());
      Transform<?, ?> transform = field.transform();

      if (transform == null || transform instanceof Identity) {
        results.add(
            visitor.field(sourceName, field.sourceId(), field.direction(), field.nullOrder()));
      } else if (transform instanceof Bucket) {
        int numBuckets = ((Bucket<?>) transform).numBuckets();
        results.add(
            visitor.bucket(
                sourceName, field.sourceId(), numBuckets, field.direction(), field.nullOrder()));
      } else if (transform instanceof Truncate) {
        int width = ((Truncate<?>) transform).width();
        results.add(
            visitor.truncate(
                sourceName, field.sourceId(), width, field.direction(), field.nullOrder()));
      } else if (transform == Dates.YEAR
          || transform == Timestamps.YEAR
          || transform instanceof Years) {
        results.add(
            visitor.year(sourceName, field.sourceId(), field.direction(), field.nullOrder()));
      } else if (transform == Dates.MONTH
          || transform == Timestamps.MONTH
          || transform instanceof Months) {
        results.add(
            visitor.month(sourceName, field.sourceId(), field.direction(), field.nullOrder()));
      } else if (transform == Dates.DAY
          || transform == Timestamps.DAY
          || transform instanceof Days) {
        results.add(
            visitor.day(sourceName, field.sourceId(), field.direction(), field.nullOrder()));
      } else if (transform == Timestamps.HOUR || transform instanceof Hours) {
        results.add(
            visitor.hour(sourceName, field.sourceId(), field.direction(), field.nullOrder()));
      } else if (transform instanceof UnknownTransform) {
        results.add(
            visitor.unknown(
                sourceName,
                field.sourceId(),
                transform.toString(),
                field.direction(),
                field.nullOrder()));
      }
    }

    return results;
  }
}
