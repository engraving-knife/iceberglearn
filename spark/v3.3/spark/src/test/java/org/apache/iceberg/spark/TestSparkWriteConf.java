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

import static org.apache.iceberg.TableProperties.DELETE_DISTRIBUTION_MODE;
import static org.apache.iceberg.TableProperties.MERGE_DISTRIBUTION_MODE;
import static org.apache.iceberg.TableProperties.UPDATE_DISTRIBUTION_MODE;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE_HASH;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE_NONE;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE_RANGE;

import java.util.Map;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestSparkWriteConf 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 Spark写配置 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkWriteConf extends SparkTestBaseWithCatalog {

  /** 前。 */
  @Before
  public void before() {
    sql(
        "CREATE TABLE %s (id BIGINT, data STRING, date DATE, ts TIMESTAMP) "
            + "USING iceberg "
            + "PARTITIONED BY (date, days(ts))",
        tableName);
  }

  /** 后。 */
  @After
  public void after() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试Spark写配置分布默认场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkWriteConfDistributionDefault() {
    Table table = validationCatalog.loadTable(tableIdent);

    SparkWriteConf writeConf = new SparkWriteConf(spark, table, ImmutableMap.of());

    Assert.assertEquals(DistributionMode.HASH, writeConf.distributionMode());
    Assert.assertEquals(DistributionMode.HASH, writeConf.deleteDistributionMode());
    Assert.assertEquals(DistributionMode.HASH, writeConf.updateDistributionMode());
    Assert.assertEquals(DistributionMode.HASH, writeConf.copyOnWriteMergeDistributionMode());
    Assert.assertEquals(DistributionMode.HASH, writeConf.positionDeltaMergeDistributionMode());
  }

  /** 测试Spark写配置分布模式带写选项场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkWriteConfDistributionModeWithWriteOption() {
    Table table = validationCatalog.loadTable(tableIdent);

    Map<String, String> writeOptions =
        ImmutableMap.of(SparkWriteOptions.DISTRIBUTION_MODE, DistributionMode.NONE.modeName());

    SparkWriteConf writeConf = new SparkWriteConf(spark, table, writeOptions);
    Assert.assertEquals(DistributionMode.NONE, writeConf.distributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.deleteDistributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.updateDistributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.copyOnWriteMergeDistributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.positionDeltaMergeDistributionMode());
  }

  /** 测试Spark写配置分布模式带会话配置场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkWriteConfDistributionModeWithSessionConfig() {
    withSQLConf(
        ImmutableMap.of(SparkSQLProperties.DISTRIBUTION_MODE, DistributionMode.NONE.modeName()),
        () -> {
          Table table = validationCatalog.loadTable(tableIdent);

          SparkWriteConf writeConf = new SparkWriteConf(spark, table, ImmutableMap.of());

          Assert.assertEquals(DistributionMode.NONE, writeConf.distributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.deleteDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.updateDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.copyOnWriteMergeDistributionMode());
          Assert.assertEquals(
              DistributionMode.NONE, writeConf.positionDeltaMergeDistributionMode());
        });
  }

  /** 测试Spark写配置分布模式带表属性场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkWriteConfDistributionModeWithTableProperties() {
    Table table = validationCatalog.loadTable(tableIdent);

    table
        .updateProperties()
        .set(WRITE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_NONE)
        .set(DELETE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_NONE)
        .set(UPDATE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_NONE)
        .set(MERGE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_NONE)
        .commit();

    SparkWriteConf writeConf = new SparkWriteConf(spark, table, ImmutableMap.of());
    Assert.assertEquals(DistributionMode.NONE, writeConf.distributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.deleteDistributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.updateDistributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.copyOnWriteMergeDistributionMode());
    Assert.assertEquals(DistributionMode.NONE, writeConf.positionDeltaMergeDistributionMode());
  }

  /** 测试Spark写配置分布模式带tblprop与会话配置场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkWriteConfDistributionModeWithTblPropAndSessionConfig() {
    withSQLConf(
        ImmutableMap.of(SparkSQLProperties.DISTRIBUTION_MODE, DistributionMode.NONE.modeName()),
        () -> {
          Table table = validationCatalog.loadTable(tableIdent);

          table
              .updateProperties()
              .set(WRITE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_RANGE)
              .set(DELETE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_RANGE)
              .set(UPDATE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_RANGE)
              .set(MERGE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_RANGE)
              .commit();

          SparkWriteConf writeConf = new SparkWriteConf(spark, table, ImmutableMap.of());
          // session config overwrite the table properties
          Assert.assertEquals(DistributionMode.NONE, writeConf.distributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.deleteDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.updateDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.copyOnWriteMergeDistributionMode());
          Assert.assertEquals(
              DistributionMode.NONE, writeConf.positionDeltaMergeDistributionMode());
        });
  }

  /** 测试Spark写配置分布模式带写选项与会话配置场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkWriteConfDistributionModeWithWriteOptionAndSessionConfig() {
    withSQLConf(
        ImmutableMap.of(SparkSQLProperties.DISTRIBUTION_MODE, DistributionMode.RANGE.modeName()),
        () -> {
          Table table = validationCatalog.loadTable(tableIdent);

          Map<String, String> writeOptions =
              ImmutableMap.of(
                  SparkWriteOptions.DISTRIBUTION_MODE, DistributionMode.NONE.modeName());

          SparkWriteConf writeConf = new SparkWriteConf(spark, table, writeOptions);
          // write options overwrite the session config
          Assert.assertEquals(DistributionMode.NONE, writeConf.distributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.deleteDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.updateDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.copyOnWriteMergeDistributionMode());
          Assert.assertEquals(
              DistributionMode.NONE, writeConf.positionDeltaMergeDistributionMode());
        });
  }

  /** 测试Spark写配置分布模式带everything场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkWriteConfDistributionModeWithEverything() {
    withSQLConf(
        ImmutableMap.of(SparkSQLProperties.DISTRIBUTION_MODE, DistributionMode.RANGE.modeName()),
        () -> {
          Table table = validationCatalog.loadTable(tableIdent);

          Map<String, String> writeOptions =
              ImmutableMap.of(
                  SparkWriteOptions.DISTRIBUTION_MODE, DistributionMode.NONE.modeName());

          table
              .updateProperties()
              .set(WRITE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_HASH)
              .set(DELETE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_HASH)
              .set(UPDATE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_HASH)
              .set(MERGE_DISTRIBUTION_MODE, WRITE_DISTRIBUTION_MODE_HASH)
              .commit();

          SparkWriteConf writeConf = new SparkWriteConf(spark, table, writeOptions);
          // write options take the highest priority
          Assert.assertEquals(DistributionMode.NONE, writeConf.distributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.deleteDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.updateDistributionMode());
          Assert.assertEquals(DistributionMode.NONE, writeConf.copyOnWriteMergeDistributionMode());
          Assert.assertEquals(
              DistributionMode.NONE, writeConf.positionDeltaMergeDistributionMode());
        });
  }
}
