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
 * Iceberg Spark 集成相关组件，实现排序相关逻辑。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SortOrderToSpark。
 */
class SortOrderToSpark implements SortOrderVisitor<SortOrder> {

  private final Map<Integer, String> quotedNameById;

  SortOrderToSpark(Schema schema) {
    this.quotedNameById = SparkSchemaUtil.indexQuotedNameById(schema);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param sourceName 参数
   * @param id 参数
   * @param direction 参数
   * @param nullOrder 参数
   * @return 结果对象
   */
  @Override
  public SortOrder field(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.column(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param sourceName 参数
   * @param id 参数
   * @param width 参数
   * @param direction 参数
   * @param nullOrder 参数
   * @return 结果对象
   */
  @Override
  public SortOrder bucket(
      String sourceName, int id, int width, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.bucket(width, quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param sourceName 参数
   * @param id 参数
   * @param width 参数
   * @param direction 参数
   * @param nullOrder 参数
   * @return 结果对象
   */
  @Override
  public SortOrder truncate(
      String sourceName, int id, int width, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.apply(
            "truncate", Expressions.column(quotedName(id)), Expressions.literal(width)),
        toSpark(direction),
        toSpark(nullOrder));
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param sourceName 参数
   * @param id 参数
   * @param direction 参数
   * @param nullOrder 参数
   * @return 结果对象
   */
  @Override
  public SortOrder year(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.years(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param sourceName 参数
   * @param id 参数
   * @param direction 参数
   * @param nullOrder 参数
   * @return 结果对象
   */
  @Override
  public SortOrder month(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.months(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param sourceName 参数
   * @param id 参数
   * @param direction 参数
   * @param nullOrder 参数
   * @return 结果对象
   */
  @Override
  public SortOrder day(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.days(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param sourceName 参数
   * @param id 参数
   * @param direction 参数
   * @param nullOrder 参数
   * @return 结果对象
   */
  @Override
  public SortOrder hour(String sourceName, int id, SortDirection direction, NullOrder nullOrder) {
    return Expressions.sort(
        Expressions.hours(quotedName(id)), toSpark(direction), toSpark(nullOrder));
  }

  /** 执行该方法的具体逻辑。 */
  private String quotedName(int id) {
    return quotedNameById.get(id);
  }

  /** 转换为spark。 */
  private org.apache.spark.sql.connector.expressions.SortDirection toSpark(
      SortDirection direction) {
    if (direction == SortDirection.ASC) {
      return org.apache.spark.sql.connector.expressions.SortDirection.ASCENDING;
    } else {
      return org.apache.spark.sql.connector.expressions.SortDirection.DESCENDING;
    }
  }

  /** 转换为spark。 */
  private NullOrdering toSpark(NullOrder nullOrder) {
    return nullOrder == NullOrder.NULLS_FIRST ? NullOrdering.NULLS_FIRST : NullOrdering.NULLS_LAST;
  }
}
