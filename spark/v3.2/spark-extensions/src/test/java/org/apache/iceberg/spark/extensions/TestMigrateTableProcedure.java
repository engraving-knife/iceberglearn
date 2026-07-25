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

import java.io.IOException;
import java.util.Map;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.spark.sql.AnalysisException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestMigrateTableProcedure 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 迁移表存储过程 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestMigrateTableProcedure extends SparkExtensionsTestBase {

  /** 测试迁移表存储过程。 */
  public TestMigrateTableProcedure(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
    sql("DROP TABLE IF EXISTS %s_BACKUP_", tableName);
  }

  /** 测试迁移场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigrate() throws IOException {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        tableName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);
    Object result = scalarSql("CALL %s.system.migrate('%s')", catalogName, tableName);

    Assert.assertEquals("Should have added one file", 1L, result);

    Table createdTable = validationCatalog.loadTable(tableIdent);

    String tableLocation = createdTable.location().replace("file:", "");
    Assert.assertEquals("Table should have original location", location, tableLocation);

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a"), row(1L, "a")),
        sql("SELECT * FROM %s ORDER BY id", tableName));

    sql("DROP TABLE %s", tableName + "_BACKUP_");
  }

  /** 测试迁移带选项场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigrateWithOptions() throws IOException {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        tableName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    Object result =
        scalarSql("CALL %s.system.migrate('%s', map('foo', 'bar'))", catalogName, tableName);

    Assert.assertEquals("Should have added one file", 1L, result);

    Table createdTable = validationCatalog.loadTable(tableIdent);

    Map<String, String> props = createdTable.properties();
    Assert.assertEquals("Should have extra property set", "bar", props.get("foo"));

    String tableLocation = createdTable.location().replace("file:", "");
    Assert.assertEquals("Table should have original location", location, tableLocation);

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a"), row(1L, "a")),
        sql("SELECT * FROM %s ORDER BY id", tableName));

    sql("DROP TABLE IF EXISTS %s", tableName + "_BACKUP_");
  }

  /** 测试迁移带删除backup场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigrateWithDropBackup() throws IOException {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        tableName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    Object result =
        scalarSql(
            "CALL %s.system.migrate(table => '%s', drop_backup => true)", catalogName, tableName);
    Assert.assertEquals("Should have added one file", 1L, result);
    Assert.assertFalse(spark.catalog().tableExists(tableName + "_BACKUP_"));
  }

  /** 测试迁移带invalid指标配置场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigrateWithInvalidMetricsConfig() throws IOException {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));

    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        tableName, location);

    AssertHelpers.assertThrows(
        "Should reject invalid metrics config",
        ValidationException.class,
        "Invalid metrics config",
        () -> {
          String props = "map('write.metadata.metrics.column.x', 'X')";
          sql("CALL %s.system.migrate('%s', %s)", catalogName, tableName, props);
        });
  }

  /** 测试迁移带conflictingprops场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigrateWithConflictingProps() throws IOException {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));

    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        tableName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    Object result =
        scalarSql("CALL %s.system.migrate('%s', map('migrated', 'false'))", catalogName, tableName);
    Assert.assertEquals("Should have added one file", 1L, result);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a")),
        sql("SELECT * FROM %s", tableName));

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertEquals("Should override user value", "true", table.properties().get("migrated"));
  }

  /** 测试invalid迁移场景场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testInvalidMigrateCases() {
    AssertHelpers.assertThrows(
        "Should reject calls without all required args",
        AnalysisException.class,
        "Missing required parameters",
        () -> sql("CALL %s.system.migrate()", catalogName));

    AssertHelpers.assertThrows(
        "Should reject calls with invalid arg types",
        AnalysisException.class,
        "Wrong arg type",
        () -> sql("CALL %s.system.migrate(map('foo','bar'))", catalogName));

    AssertHelpers.assertThrows(
        "Should reject calls with empty table identifier",
        IllegalArgumentException.class,
        "Cannot handle an empty identifier",
        () -> sql("CALL %s.system.migrate('')", catalogName));
  }

  /** 测试迁移分区带specialcharacter场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigratePartitionWithSpecialCharacter() throws IOException {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string, dt date) USING parquet "
            + "PARTITIONED BY (data, dt) LOCATION '%s'",
        tableName, location);
    sql("INSERT INTO TABLE %s VALUES (1, '2023/05/30', date '2023-05-30')", tableName);
    Object result = scalarSql("CALL %s.system.migrate('%s')", catalogName, tableName);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "2023/05/30", java.sql.Date.valueOf("2023-05-30"))),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 测试迁移空分区表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigrateEmptyPartitionedTable() throws Exception {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet PARTITIONED BY (id) LOCATION '%s'",
        tableName, location);
    Object result = scalarSql("CALL %s.system.migrate('%s')", catalogName, tableName);
    Assert.assertEquals(0L, result);
  }

  /** 测试迁移空表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testMigrateEmptyTable() throws Exception {
    Assume.assumeTrue(catalogName.equals("spark_catalog"));
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        tableName, location);
    Object result = scalarSql("CALL %s.system.migrate('%s')", catalogName, tableName);
    Assert.assertEquals(0L, result);
  }
}
