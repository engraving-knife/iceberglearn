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
package org.apache.iceberg.spark.source;

import java.util.OptionalLong;
import org.apache.spark.sql.connector.read.Statistics;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现，收集并汇报任务执行指标。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 Stats。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class Stats implements Statistics {
  private final OptionalLong sizeInBytes;
  private final OptionalLong numRows;

  Stats(long sizeInBytes, long numRows) {
    this.sizeInBytes = OptionalLong.of(sizeInBytes);
    this.numRows = OptionalLong.of(numRows);
  }

  /**
   * 返回大小。
   *
   * @return 结果对象
   */
  @Override
  public OptionalLong sizeInBytes() {
    return sizeInBytes;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public OptionalLong numRows() {
    return numRows;
  }
}
