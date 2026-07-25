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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestSparkDistributedDataScanDeletes 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Sparkdistributed数据扫描删除
 * 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public class TestSparkDistributedDataScanDeletes
    extends DeleteFileIndexTestBase<BatchScan, ScanTask, ScanTaskGroup<ScanTask>> {

  /** 参数。 */
  @Parameterized.Parameters(name = "dataMode = {0}, deleteMode = {1}")
  public static Object[] parameters() {
    return new Object[][] {
      new Object[] {LOCAL, LOCAL},
      new Object[] {LOCAL, DISTRIBUTED},
      new Object[] {DISTRIBUTED, LOCAL},
      new Object[] {DISTRIBUTED, DISTRIBUTED}
    };
  }

  private static SparkSession spark = null;

  private final PlanningMode dataMode;
  private final PlanningMode deleteMode;

  /** 测试Sparkdistributed数据扫描删除。 */
  public TestSparkDistributedDataScanDeletes(
      PlanningMode dataPlanningMode, PlanningMode deletePlanningMode) {
    this.dataMode = dataPlanningMode;
    this.deleteMode = deletePlanningMode;
  }

  /** configure规划模式。 */
  @Before
  public void configurePlanningModes() {
    table
        .updateProperties()
        .set(TableProperties.DATA_PLANNING_MODE, dataMode.modeName())
        .set(TableProperties.DELETE_PLANNING_MODE, deleteMode.modeName())
        .commit();
  }

  /** 启动Spark。 */
  @BeforeClass
  public static void startSpark() {
    TestSparkDistributedDataScanDeletes.spark =
        SparkSession.builder()
            .master("local[2]")
            .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            .config(SQLConf.SHUFFLE_PARTITIONS().key(), "4")
            .getOrCreate();
  }

  /** 停止Spark。 */
  @AfterClass
  public static void stopSpark() {
    SparkSession currentSpark = TestSparkDistributedDataScanDeletes.spark;
    TestSparkDistributedDataScanDeletes.spark = null;
    currentSpark.stop();
  }

  /** 新建扫描。 */
  @Override
  protected BatchScan newScan(Table table) {
    SparkReadConf readConf = new SparkReadConf(spark, table, ImmutableMap.of());
    return new SparkDistributedDataScan(spark, table, readConf);
  }
}
