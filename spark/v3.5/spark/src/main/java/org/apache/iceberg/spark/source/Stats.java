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
 * Spark 读取统计信息实现：携带字节数与行数。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source 子包。
 *
 * <p>职责：实现 {@link Statistics} 把 Iceberg 扫描得到的 sizeInBytes 与 numRows 暴露给 Spark 优化器。
 */
class Stats implements Statistics {
  private final OptionalLong sizeInBytes;
  private final OptionalLong numRows;

  /** 构造统计信息，指定字节数与行数。 */
  Stats(long sizeInBytes, long numRows) {
    this.sizeInBytes = OptionalLong.of(sizeInBytes);
    this.numRows = OptionalLong.of(numRows);
  }
  /** 执行 sizeInBytes 相关操作。 */
  @Override
  public OptionalLong sizeInBytes() {
    return sizeInBytes;
  }
  /** 执行 numRows 相关操作。 */
  @Override
  public OptionalLong numRows() {
    return numRows;
  }
}
