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

import org.apache.iceberg.Table;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：暂存扫描构建器，构建 SparkStagedScan。
 *
 * <p>设计意图：实现 ScanBuilder，面向分阶段写入的暂存数据。
 *
 * <p>上下游关系：由 StagedSparkTable 使用。
 */
class SparkStagedScanBuilder implements ScanBuilder {

  private final SparkSession spark;
  private final Table table;
  private final SparkReadConf readConf;

  SparkStagedScanBuilder(SparkSession spark, Table table, CaseInsensitiveStringMap options) {
    this.spark = spark;
    this.table = table;
    this.readConf = new SparkReadConf(spark, table, options);
  }
  /** 构建目标对象。 */
  @Override
  public Scan build() {
    return new SparkStagedScan(spark, table, readConf);
  }
}
