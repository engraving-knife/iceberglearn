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
package org.apache.iceberg.spark;

import java.util.Map;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expression.Operation;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.expressions.aggregate.AggregateFunc;
import org.apache.spark.sql.connector.expressions.aggregate.Count;
import org.apache.spark.sql.connector.expressions.aggregate.CountStar;
import org.apache.spark.sql.connector.expressions.aggregate.Max;
import org.apache.spark.sql.connector.expressions.aggregate.Min;

/**
 * Spark 聚合函数 -> Iceberg 聚合表达式转换器。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：把 Spark DSv2 聚合函数（{@link AggregateFunc}）转换为 Iceberg {@link Expression}， 用于把聚合下推到 Iceberg
 * manifest 文件统计（如 count/max/min）。
 *
 * <p>设计意图：用静态映射表把 Spark 聚合类映射到 Iceberg Operation，再在 convert 中按 op 分支构造 对应 Iceberg 表达式；count
 * distinct 与非 NamedReference 列无法下推，返回 null。
 *
 * <p>上下游关系：被 Spark 读取计划下推逻辑调用；依赖 iceberg-core 的 Expressions。
 */
public class SparkAggregates {
  private SparkAggregates() {}

  private static final Map<Class<? extends AggregateFunc>, Operation> AGGREGATES =
      ImmutableMap.<Class<? extends AggregateFunc>, Operation>builder()
          .put(Count.class, Operation.COUNT)
          .put(CountStar.class, Operation.COUNT_STAR)
          .put(Max.class, Operation.MAX)
          .put(Min.class, Operation.MIN)
          .buildOrThrow();

  /**
   * 把 Spark 聚合函数转为 Iceberg 表达式。
   *
   * <p>逻辑：按聚合类型分支：COUNT（非 distinct 且列为 NamedReference 时转 count）、 COUNT_STAR（转 countStar）、MAX/MIN（列为
   * NamedReference 时转对应表达式）； 其余返回 null 表示无法下推。
   *
   * @param aggregate Spark 聚合函数
   * @return Iceberg 表达式，或 null 表示不可下推
   */
  public static Expression convert(AggregateFunc aggregate) {
    Operation op = AGGREGATES.get(aggregate.getClass());
    if (op != null) {
      switch (op) {
        case COUNT:
          Count countAgg = (Count) aggregate;
          if (countAgg.isDistinct()) {
            // manifest file doesn't have count distinct so this can't be pushed down
            return null;
          }

          if (countAgg.column() instanceof NamedReference) {
            return Expressions.count(SparkUtil.toColumnName((NamedReference) countAgg.column()));
          } else {
            return null;
          }

        case COUNT_STAR:
          return Expressions.countStar();

        case MAX:
          Max maxAgg = (Max) aggregate;
          if (maxAgg.column() instanceof NamedReference) {
            return Expressions.max(SparkUtil.toColumnName((NamedReference) maxAgg.column()));
          } else {
            return null;
          }

        case MIN:
          Min minAgg = (Min) aggregate;
          if (minAgg.column() instanceof NamedReference) {
            return Expressions.min(SparkUtil.toColumnName((NamedReference) minAgg.column()));
          } else {
            return null;
          }
      }
    }

    return null;
  }
}
