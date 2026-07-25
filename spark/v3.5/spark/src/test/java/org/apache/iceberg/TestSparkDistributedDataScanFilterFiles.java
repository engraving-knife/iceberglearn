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
package org.apache.iceberg;

import static org.apache.iceberg.PlanningMode.DISTRIBUTED;
import static org.apache.iceberg.PlanningMode.LOCAL;

import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * 文件级说明：测试 TestSparkDistributedDataScanFilterFiles 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Sparkdistributed数据扫描过滤器文件
 * 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public class TestSparkDistributedDataScanFilterFiles
    extends FilterFilesTestBase<BatchScan, ScanTask, ScanTaskGroup<ScanTask>> {

  /** 参数。 */
  @Parameters(name = "formatVersion = {0}, dataMode = {1}, deleteMode = {2}")
  public static Object[] parameters() {
    return new Object[][] {
      new Object[] {1, LOCAL, LOCAL},
      new Object[] {1, LOCAL, DISTRIBUTED},
      new Object[] {1, DISTRIBUTED, LOCAL},
      new Object[] {1, DISTRIBUTED, DISTRIBUTED},
      new Object[] {2, LOCAL, LOCAL},
      new Object[] {2, LOCAL, DISTRIBUTED},
      new Object[] {2, DISTRIBUTED, LOCAL},
      new Object[] {2, DISTRIBUTED, DISTRIBUTED}
    };
  }

  private static SparkSession spark = null;

  private final PlanningMode dataMode;
  private final PlanningMode deleteMode;

  /** 测试Sparkdistributed数据扫描过滤器文件。 */
  public TestSparkDistributedDataScanFilterFiles(
      int formatVersion, PlanningMode dataPlanningMode, PlanningMode deletePlanningMode) {
    super(formatVersion);
    this.dataMode = dataPlanningMode;
    this.deleteMode = deletePlanningMode;
  }

  /** 启动Spark。 */
  @BeforeClass
  public static void startSpark() {
    TestSparkDistributedDataScanFilterFiles.spark =
        SparkSession.builder()
            .master("local[2]")
            .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            .config(SQLConf.SHUFFLE_PARTITIONS().key(), "4")
            .getOrCreate();
  }

  /** 停止Spark。 */
  @AfterClass
  public static void stopSpark() {
    SparkSession currentSpark = TestSparkDistributedDataScanFilterFiles.spark;
    TestSparkDistributedDataScanFilterFiles.spark = null;
    currentSpark.stop();
  }

  /** 新建扫描。 */
  @Override
  protected BatchScan newScan(Table table) {
    table
        .updateProperties()
        .set(TableProperties.DATA_PLANNING_MODE, dataMode.modeName())
        .set(TableProperties.DELETE_PLANNING_MODE, deleteMode.modeName())
        .commit();
    SparkReadConf readConf = new SparkReadConf(spark, table, ImmutableMap.of());
    return new SparkDistributedDataScan(spark, table, readConf);
  }
}
