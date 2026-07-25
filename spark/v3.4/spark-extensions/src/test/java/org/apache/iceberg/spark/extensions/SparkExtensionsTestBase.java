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
package org.apache.iceberg.spark.extensions;

import static org.apache.hadoop.hive.conf.HiveConf.ConfVars.METASTOREURIS;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.hive.HiveCatalog;
import org.apache.iceberg.hive.TestHiveMetastore;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkCatalogTestBase;
import org.apache.iceberg.spark.SparkTestBase;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;
import org.junit.BeforeClass;

/**
 * 文件级说明：测试 SparkExtensionsTestBase 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Spark扩展 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public abstract class SparkExtensionsTestBase extends SparkCatalogTestBase {

  private static final Random RANDOM = ThreadLocalRandom.current();

  /** Spark扩展测试基类。 */
  public SparkExtensionsTestBase(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 启动元存储与Spark。 */
  @BeforeClass
  public static void startMetastoreAndSpark() {
    SparkTestBase.metastore = new TestHiveMetastore();
    metastore.start();
    SparkTestBase.hiveConf = metastore.hiveConf();

    SparkTestBase.spark =
        SparkSession.builder()
            .master("local[2]")
            .config("spark.testing", "true")
            .config(SQLConf.PARTITION_OVERWRITE_MODE().key(), "dynamic")
            .config("spark.sql.extensions", IcebergSparkSessionExtensions.class.getName())
            .config("spark.hadoop." + METASTOREURIS.varname, hiveConf.get(METASTOREURIS.varname))
            .config("spark.sql.shuffle.partitions", "4")
            .config("spark.sql.hive.metastorePartitionPruningFallbackOnException", "true")
            .config("spark.sql.legacy.respectNullabilityInTextDatasetConversion", "true")
            .config(
                SQLConf.ADAPTIVE_EXECUTION_ENABLED().key(), String.valueOf(RANDOM.nextBoolean()))
            .enableHiveSupport()
            .getOrCreate();

    SparkTestBase.catalog =
        (HiveCatalog)
            CatalogUtil.loadCatalog(
                HiveCatalog.class.getName(), "hive", ImmutableMap.of(), hiveConf);
  }
}
