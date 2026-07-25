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

import org.apache.spark.sql.SparkSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * 文件级说明：测试 TestSparkDistributedDataScanJavaSerialization 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Sparkdistributed数据扫描Java序列化
 * 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkDistributedDataScanJavaSerialization
    extends SparkDistributedDataScanTestBase {

  /** 测试Sparkdistributed数据扫描Java序列化。 */
  public TestSparkDistributedDataScanJavaSerialization(
      int formatVersion, PlanningMode dataPlanningMode, PlanningMode deletePlanningMode) {
    super(formatVersion, dataPlanningMode, deletePlanningMode);
  }

  /** 启动Spark。 */
  @BeforeClass
  public static void startSpark() {
    SparkDistributedDataScanTestBase.spark =
        initSpark("org.apache.spark.serializer.JavaSerializer");
  }

  /** 停止Spark。 */
  @AfterClass
  public static void stopSpark() {
    SparkSession currentSpark = SparkDistributedDataScanTestBase.spark;
    SparkDistributedDataScanTestBase.spark = null;
    currentSpark.stop();
  }
}
