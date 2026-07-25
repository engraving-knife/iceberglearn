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
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortDirection;
import org.apache.iceberg.transforms.SortOrderVisitor;
import org.apache.spark.sql.connector.expressions.Expressions;
import org.apache.spark.sql.connector.expressions.NullOrdering;
import org.apache.spark.sql.connector.expressions.SortOrder;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：将 Iceberg SortOrder 转换为 Spark SortOrder 表达式的访问器。
 *
 * <p>设计意图：使用访问者模式递归转换排序字段与方向，保证两端排序语义一致。
 *
 * <p>上下游关系：由 SparkTable / SparkWriteBuilder 等在向 Spark 暴露 sort-order 属性时调用。
 */
class SortOrderToSpark implements SortOrderVisitor<SortOrder> {

  private final Map<Integer, String> quotedNameById;

  SortOrderToSpark(Schema schema) {
    this.quotedNameById = SparkSchemaUtil.indexQuotedNameById(schema);
  }
  /** 执行 field 相关操作。 */
  @Override
  public SortOrder field(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.column(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }
  /** 执行 bucket 相关操作。 */
  @Override
  public SortOrder bucket(
      String sourceName, int id, int width, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.bucket(width, quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }
  /** 执行 truncate 相关操作。 */
  @Override
  public SortOrder truncate(
      String sourceName, int id, int width, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.apply(
            "truncate", Expressions.literal(width), Expressions.column(quotedName(id))),
        toSpark(direction),
        toSpark(nullOrder));
  }
  /** 执行 year 相关操作。 */
  @Override
  public SortOrder year(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.years(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }
  /** 执行 month 相关操作。 */
  @Override
  public SortOrder month(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.months(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }
  /** 执行 day 相关操作。 */
  @Override
  public SortOrder day(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.days(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }
  /** 执行 hour 相关操作。 */
  @Override
  public SortOrder hour(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.hours(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }
  /** 执行 quotedName 相关操作。 */
  private String quotedName(int id) {
    return quotedNameById.get(id);
  }
  /** 转换为 Spark 对象。 */
  private org.apache.spark.sql.connector.expressions.SortDirection toSpark(
      SortDirection direction) {
    if (direction == SortDirection.ASC) {
      return org.apache.spark.sql.connector.expressions.SortDirection.ASCENDING;
    } else {
      return org.apache.spark.sql.connector.expressions.SortDirection.DESCENDING;
    }
  }
  /** 转换为 Spark 对象。 */
  private NullOrdering toSpark(NullOrder nullOrder) {
    return nullOrder == NullOrder.NULLS_FIRST ? NullOrdering.NULLS_FIRST : NullOrdering.NULLS_LAST;
  }
}
