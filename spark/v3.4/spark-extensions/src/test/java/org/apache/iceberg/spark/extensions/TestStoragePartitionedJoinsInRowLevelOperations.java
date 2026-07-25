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

import static org.apache.iceberg.RowLevelOperationMode.COPY_ON_WRITE;
import static org.apache.iceberg.RowLevelOperationMode.MERGE_ON_READ;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.iceberg.RowLevelOperationMode;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkCatalogConfig;
import org.apache.iceberg.spark.SparkSQLProperties;
import org.apache.spark.sql.execution.SparkPlan;
import org.apache.spark.sql.internal.SQLConf;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestStoragePartitionedJoinsInRowLevelOperations 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 storage分区连接在行级别操作 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestStoragePartitionedJoinsInRowLevelOperations extends SparkExtensionsTestBase {

  private static final String OTHER_TABLE_NAME = "other_table";

  // open file cost and split size are set as 16 MB to produce a split per file
  private static final Map<String, String> COMMON_TABLE_PROPERTIES =
      ImmutableMap.of(
          TableProperties.FORMAT_VERSION,
          "2",
          TableProperties.SPLIT_SIZE,
          "16777216",
          TableProperties.SPLIT_OPEN_FILE_COST,
          "16777216");

  // only v2 bucketing and preserve data grouping properties have to be enabled to trigger SPJ
  // other properties are only to simplify testing and validation
  private static final Map<String, String> ENABLED_SPJ_SQL_CONF =
      ImmutableMap.of(
          SQLConf.V2_BUCKETING_ENABLED().key(),
          "true",
          SQLConf.V2_BUCKETING_PUSH_PART_VALUES_ENABLED().key(),
          "true",
          SQLConf.REQUIRE_ALL_CLUSTER_KEYS_FOR_CO_PARTITION().key(),
          "false",
          SQLConf.ADAPTIVE_EXECUTION_ENABLED().key(),
          "false",
          SQLConf.AUTO_BROADCASTJOIN_THRESHOLD().key(),
          "-1",
          SparkSQLProperties.PRESERVE_DATA_GROUPING,
          "true");

  /** 参数。 */
  @Parameterized.Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        SparkCatalogConfig.HIVE.catalogName(),
        SparkCatalogConfig.HIVE.implementation(),
        SparkCatalogConfig.HIVE.properties()
      }
    };
  }

  /** 测试storage分区连接在行级别操作。 */
  public TestStoragePartitionedJoinsInRowLevelOperations(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
    sql("DROP TABLE IF EXISTS %s", tableName(OTHER_TABLE_NAME));
  }

  /** 测试复制上写删除无shuffles场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCopyOnWriteDeleteWithoutShuffles() {
    checkDelete(COPY_ON_WRITE);
  }

  /** 测试合并上读删除无shuffles场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMergeOnReadDeleteWithoutShuffles() {
    checkDelete(MERGE_ON_READ);
  }

  /** 检查删除。 */
  private void checkDelete(RowLevelOperationMode mode) {
    String createTableStmt =
        "CREATE TABLE %s (id INT, salary INT, dep STRING)"
            + "USING iceberg "
            + "PARTITIONED BY (dep) "
            + "TBLPROPERTIES (%s)";

    sql(createTableStmt, tableName, tablePropsAsString(COMMON_TABLE_PROPERTIES));

    append(tableName, "{ \"id\": 1, \"salary\": 100, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 2, \"salary\": 200, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 3, \"salary\": 300, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 4, \"salary\": 400, \"dep\": \"hardware\" }");

    sql(createTableStmt, tableName(OTHER_TABLE_NAME), tablePropsAsString(COMMON_TABLE_PROPERTIES));

    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 1, \"salary\": 110, \"dep\": \"hr\" }");
    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 5, \"salary\": 500, \"dep\": \"hr\" }");

    Map<String, String> deleteTableProps =
        ImmutableMap.of(
            TableProperties.DELETE_MODE,
            mode.modeName(),
            TableProperties.DELETE_DISTRIBUTION_MODE,
            "none");

    sql("ALTER TABLE %s SET TBLPROPERTIES(%s)", tableName, tablePropsAsString(deleteTableProps));

    withSQLConf(
        ENABLED_SPJ_SQL_CONF,
        () -> {
          SparkPlan plan =
              executeAndKeepPlan(
                  "DELETE FROM %s t WHERE "
                      + "EXISTS (SELECT 1 FROM %s s WHERE t.id = s.id AND t.dep = s.dep) AND "
                      + "dep = 'hr'",
                  tableName, tableName(OTHER_TABLE_NAME));
          String planAsString = plan.toString();
          Assertions.assertThat(planAsString).doesNotContain("Exchange");
        });

    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(
            row(2, 200, "hr"), // remaining
            row(3, 300, "hr"), // remaining
            row(4, 400, "hardware")); // remaining

    assertEquals(
        "Should have expected rows",
        expectedRows,
        sql("SELECT * FROM %s ORDER BY id, salary", tableName));
  }

  /** 测试复制上写更新无shuffles场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCopyOnWriteUpdateWithoutShuffles() {
    checkUpdate(COPY_ON_WRITE);
  }

  /** 测试合并上读更新无shuffles场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMergeOnReadUpdateWithoutShuffles() {
    checkUpdate(MERGE_ON_READ);
  }

  /** 检查更新。 */
  private void checkUpdate(RowLevelOperationMode mode) {
    String createTableStmt =
        "CREATE TABLE %s (id INT, salary INT, dep STRING)"
            + "USING iceberg "
            + "PARTITIONED BY (dep) "
            + "TBLPROPERTIES (%s)";

    sql(createTableStmt, tableName, tablePropsAsString(COMMON_TABLE_PROPERTIES));

    append(tableName, "{ \"id\": 1, \"salary\": 100, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 2, \"salary\": 200, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 3, \"salary\": 300, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 4, \"salary\": 400, \"dep\": \"hardware\" }");

    sql(createTableStmt, tableName(OTHER_TABLE_NAME), tablePropsAsString(COMMON_TABLE_PROPERTIES));

    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 1, \"salary\": 110, \"dep\": \"hr\" }");
    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 5, \"salary\": 500, \"dep\": \"hr\" }");

    Map<String, String> updateTableProps =
        ImmutableMap.of(
            TableProperties.UPDATE_MODE,
            mode.modeName(),
            TableProperties.UPDATE_DISTRIBUTION_MODE,
            "none");

    sql("ALTER TABLE %s SET TBLPROPERTIES(%s)", tableName, tablePropsAsString(updateTableProps));

    withSQLConf(
        ENABLED_SPJ_SQL_CONF,
        () -> {
          SparkPlan plan =
              executeAndKeepPlan(
                  "UPDATE %s t SET salary = -1 WHERE "
                      + "EXISTS (SELECT 1 FROM %s s WHERE t.id = s.id AND t.dep = s.dep) AND "
                      + "dep = 'hr'",
                  tableName, tableName(OTHER_TABLE_NAME));
          String planAsString = plan.toString();
          Assertions.assertThat(planAsString).doesNotContain("Exchange");
        });

    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(
            row(1, -1, "hr"), // updated
            row(2, 200, "hr"), // existing
            row(3, 300, "hr"), // existing
            row(4, 400, "hardware")); // existing

    assertEquals(
        "Should have expected rows",
        expectedRows,
        sql("SELECT * FROM %s ORDER BY id, salary", tableName));
  }

  /** 测试复制上写合并无shuffles场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCopyOnWriteMergeWithoutShuffles() {
    checkMerge(COPY_ON_WRITE, false /* with ON predicate */);
  }

  /** 测试复制上写合并无shuffles带谓词场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCopyOnWriteMergeWithoutShufflesWithPredicate() {
    checkMerge(COPY_ON_WRITE, true /* with ON predicate */);
  }

  /** 测试合并上读合并无shuffles场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMergeOnReadMergeWithoutShuffles() {
    checkMerge(MERGE_ON_READ, false /* with ON predicate */);
  }

  /** 测试合并上读合并无shuffles带谓词场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMergeOnReadMergeWithoutShufflesWithPredicate() {
    checkMerge(MERGE_ON_READ, true /* with ON predicate */);
  }

  /** 检查合并。 */
  private void checkMerge(RowLevelOperationMode mode, boolean withPredicate) {
    String createTableStmt =
        "CREATE TABLE %s (id INT, salary INT, dep STRING)"
            + "USING iceberg "
            + "PARTITIONED BY (dep) "
            + "TBLPROPERTIES (%s)";

    sql(createTableStmt, tableName, tablePropsAsString(COMMON_TABLE_PROPERTIES));

    append(tableName, "{ \"id\": 1, \"salary\": 100, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 2, \"salary\": 200, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 3, \"salary\": 300, \"dep\": \"hr\" }");
    append(tableName, "{ \"id\": 4, \"salary\": 400, \"dep\": \"hardware\" }");
    append(tableName, "{ \"id\": 6, \"salary\": 600, \"dep\": \"software\" }");

    sql(createTableStmt, tableName(OTHER_TABLE_NAME), tablePropsAsString(COMMON_TABLE_PROPERTIES));

    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 1, \"salary\": 110, \"dep\": \"hr\" }");
    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 5, \"salary\": 500, \"dep\": \"hr\" }");
    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 6, \"salary\": 300, \"dep\": \"software\" }");
    append(tableName(OTHER_TABLE_NAME), "{ \"id\": 10, \"salary\": 1000, \"dep\": \"ops\" }");

    Map<String, String> mergeTableProps =
        ImmutableMap.of(
            TableProperties.MERGE_MODE,
            mode.modeName(),
            TableProperties.MERGE_DISTRIBUTION_MODE,
            "none");

    sql("ALTER TABLE %s SET TBLPROPERTIES(%s)", tableName, tablePropsAsString(mergeTableProps));

    withSQLConf(
        ENABLED_SPJ_SQL_CONF,
        () -> {
          String predicate = withPredicate ? "AND t.dep IN ('hr', 'ops', 'software')" : "";
          SparkPlan plan =
              executeAndKeepPlan(
                  "MERGE INTO %s AS t USING %s AS s "
                      + "ON t.id = s.id AND t.dep = s.dep %s "
                      + "WHEN MATCHED THEN "
                      + "  UPDATE SET t.salary = s.salary "
                      + "WHEN NOT MATCHED THEN "
                      + "  INSERT *",
                  tableName, tableName(OTHER_TABLE_NAME), predicate);
          String planAsString = plan.toString();
          if (mode == COPY_ON_WRITE) {
            int actualNumShuffles = StringUtils.countMatches(planAsString, "Exchange");
            Assert.assertEquals("Should be 1 shuffle with SPJ", 1, actualNumShuffles);
            Assertions.assertThat(planAsString).contains("Exchange hashpartitioning(_file");
          } else {
            Assertions.assertThat(planAsString).doesNotContain("Exchange");
          }
        });

    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(
            row(1, 110, "hr"), // updated
            row(2, 200, "hr"), // existing
            row(3, 300, "hr"), // existing
            row(4, 400, "hardware"), // existing
            row(5, 500, "hr"), // new
            row(6, 300, "software"), // updated
            row(10, 1000, "ops")); // new

    assertEquals(
        "Should have expected rows",
        expectedRows,
        sql("SELECT * FROM %s ORDER BY id, salary", tableName));
  }
}
