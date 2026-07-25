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
 * Iceberg 表在 Spark DataSource V2 中的实现的构建器，负责分步骤构造目标对象。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkStagedScanBuilder。
 *
 * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
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

  /**
   * 构造并返回目标对象。
   *
   * @return 结果对象
   */
  @Override
  public Scan build() {
    /** 执行该方法的具体逻辑。 */
    return new SparkStagedScan(spark, table, readConf);
  }
}
