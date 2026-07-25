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

import org.apache.spark.sql.connector.distributions.Distribution;
import org.apache.spark.sql.connector.distributions.Distributions;
import org.apache.spark.sql.connector.expressions.SortOrder;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 写入要求载体，描述 Iceberg 期望的写入分布与排序要求。
 *
 * <p>设计意图：封装分布模式与排序顺序，用于在 Spark 写入前施加必要的 shuffle/sort。
 *
 * <p>上下游关系：由 SparkWriteBuilder / SetWriteDistributionAndOrdering 等使用。
 */
public class SparkWriteRequirements {

  public static final SparkWriteRequirements EMPTY =
      new SparkWriteRequirements(Distributions.unspecified(), new SortOrder[0]);

  private final Distribution distribution;
  private final SortOrder[] ordering;

  SparkWriteRequirements(Distribution distribution, SortOrder[] ordering) {
    this.distribution = distribution;
    this.ordering = ordering;
  }
  /** 返回分布信息。 */
  public Distribution distribution() {
    return distribution;
  }
  /** 返回排序信息。 */
  public SortOrder[] ordering() {
    return ordering;
  }
  /** 判断是否存在 Ordering。 */
  public boolean hasOrdering() {
    return ordering.length != 0;
  }
}
