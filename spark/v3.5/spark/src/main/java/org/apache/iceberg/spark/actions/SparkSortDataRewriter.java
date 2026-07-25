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
package org.apache.iceberg.spark.actions;

import java.util.function.Function;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * 基于排序（sort）策略的数据文件重写器。
 *
 * <p>所属模块：iceberg-spark（actions 子包，提供基于 Spark 的表维护动作实现）。
 *
 * <p>职责：在重写数据文件时按指定排序顺序对数据进行全局排序，使输出文件在排序键上 连续分布，提升后续查询的数据跳跃（data skipping）效果。
 *
 * <p>设计意图：继承 {@link SparkShufflingDataRewriter} 复用 shuffle 框架， 仅提供排序顺序与排序后数据集的转换。构造时校验排序顺序有效性，
 * 支持使用表已有排序顺序或外部传入的排序顺序两种方式。
 *
 * <p>上下游关系：被数据文件重写动作按策略选中并调用，依赖父类完成 shuffle 与写出。
 */
class SparkSortDataRewriter extends SparkShufflingDataRewriter {

  private final SortOrder sortOrder;

  /**
   * 使用表自身排序顺序构造重写器。
   *
   * @throws IllegalArgumentException 当表未配置有效排序顺序时抛出
   */
  SparkSortDataRewriter(SparkSession spark, Table table) {
    super(spark, table);
    Preconditions.checkArgument(
        table.sortOrder().isSorted(),
        "Cannot sort data without a valid sort order, table '%s' is unsorted and no sort order is provided",
        table.name());
    this.sortOrder = table.sortOrder();
  }

  /**
   * 使用外部指定的排序顺序构造重写器。
   *
   * @param sortOrder 外部传入的排序顺序，必须非空且已排序
   * @throws IllegalArgumentException 当排序顺序为空或未排序时抛出
   */
  SparkSortDataRewriter(SparkSession spark, Table table, SortOrder sortOrder) {
    super(spark, table);
    Preconditions.checkArgument(
        sortOrder != null && sortOrder.isSorted(),
        "Cannot sort data without a valid sort order, the provided sort order is null or empty");
    this.sortOrder = sortOrder;
  }

  /** 返回该重写策略的可读名称。 */
  @Override
  public String description() {
    return "SORT";
  }

  /** 返回本次重写使用的排序顺序。 */
  @Override
  protected SortOrder sortOrder() {
    return sortOrder;
  }

  /**
   * 对数据集应用排序函数得到排序后的数据集。
   *
   * @param df 原始数据集
   * @param sortFunc 排序变换函数
   * @return 排序后的数据集
   */
  @Override
  protected Dataset<Row> sortedDF(Dataset<Row> df, Function<Dataset<Row>, Dataset<Row>> sortFunc) {
    return sortFunc.apply(df);
  }
}
