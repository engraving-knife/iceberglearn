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
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.spark.sql.AnalysisException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestSnapshotTableProcedure 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 快照表存储过程 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSnapshotTableProcedure extends SparkExtensionsTestBase {
  private static final String sourceName = "spark_catalog.default.source";
  // Currently we can only Snapshot only out of the Spark Session Catalog

  public TestSnapshotTableProcedure(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
    sql("DROP TABLE IF EXISTS %S", sourceName);
  }

  /** 测试快照场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSnapshot() throws IOException {
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        sourceName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", sourceName);
    Object result =
        scalarSql("CALL %s.system.snapshot('%s', '%s')", catalogName, sourceName, tableName);

    Assert.assertEquals("Should have added one file", 1L, result);

    Table createdTable = validationCatalog.loadTable(tableIdent);
    String tableLocation = createdTable.location();
    Assert.assertNotEquals("Table should not have the original location", location, tableLocation);

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a"), row(1L, "a")),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 测试快照带属性场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSnapshotWithProperties() throws IOException {
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        sourceName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", sourceName);
    Object result =
        scalarSql(
            "CALL %s.system.snapshot(source_table => '%s', table => '%s', properties => map('foo','bar'))",
            catalogName, sourceName, tableName);

    Assert.assertEquals("Should have added one file", 1L, result);

    Table createdTable = validationCatalog.loadTable(tableIdent);

    String tableLocation = createdTable.location();
    Assert.assertNotEquals("Table should not have the original location", location, tableLocation);

    Map<String, String> props = createdTable.properties();
    Assert.assertEquals("Should have extra property set", "bar", props.get("foo"));

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a"), row(1L, "a")),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 测试快照带alternate路径场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSnapshotWithAlternateLocation() throws IOException {
    Assume.assumeTrue(
        "No Snapshoting with Alternate locations with Hadoop Catalogs",
        !catalogName.contains("hadoop"));
    String location = temp.newFolder().toString();
    String snapshotLocation = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        sourceName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", sourceName);
    Object[] result =
        sql(
                "CALL %s.system.snapshot(source_table => '%s', table => '%s', location => '%s')",
                catalogName, sourceName, tableName, snapshotLocation)
            .get(0);

    Assert.assertEquals("Should have added one file", 1L, result[0]);

    String storageLocation = validationCatalog.loadTable(tableIdent).location();
    Assert.assertEquals(
        "Snapshot should be made at specified location", snapshotLocation, storageLocation);

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a"), row(1L, "a")),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 测试删除表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropTable() throws IOException {
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        sourceName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", sourceName);

    Object result =
        scalarSql("CALL %s.system.snapshot('%s', '%s')", catalogName, sourceName, tableName);
    Assert.assertEquals("Should have added one file", 1L, result);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a")),
        sql("SELECT * FROM %s", tableName));

    sql("DROP TABLE %s", tableName);

    assertEquals(
        "Source table should be intact",
        ImmutableList.of(row(1L, "a")),
        sql("SELECT * FROM %s", sourceName));
  }

  /** 测试快照带conflictingprops场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSnapshotWithConflictingProps() throws IOException {
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        sourceName, location);
    sql("INSERT INTO TABLE %s VALUES (1, 'a')", sourceName);

    Object result =
        scalarSql(
            "CALL %s.system.snapshot("
                + "source_table => '%s',"
                + "table => '%s',"
                + "properties => map('%s', 'true', 'snapshot', 'false'))",
            catalogName, sourceName, tableName, TableProperties.GC_ENABLED);
    Assert.assertEquals("Should have added one file", 1L, result);

    assertEquals(
        "Should have expected rows",
        ImmutableList.of(row(1L, "a")),
        sql("SELECT * FROM %s", tableName));

    Table table = validationCatalog.loadTable(tableIdent);
    Map<String, String> props = table.properties();
    Assert.assertEquals("Should override user value", "true", props.get("snapshot"));
    Assert.assertEquals(
        "Should override user value", "false", props.get(TableProperties.GC_ENABLED));
  }

  /** 测试invalid快照场景场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testInvalidSnapshotsCases() throws IOException {
    String location = temp.newFolder().toString();
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string) USING parquet LOCATION '%s'",
        sourceName, location);

    AssertHelpers.assertThrows(
        "Should reject calls without all required args",
        AnalysisException.class,
        "Missing required parameters",
        () -> sql("CALL %s.system.snapshot('foo')", catalogName));

    AssertHelpers.assertThrows(
        "Should reject calls with invalid arg types",
        AnalysisException.class,
        "Wrong arg type",
        () -> sql("CALL %s.system.snapshot('n', 't', map('foo', 'bar'))", catalogName));

    AssertHelpers.assertThrows(
        "Should reject calls with invalid map args",
        AnalysisException.class,
        "cannot resolve 'map",
        () ->
            sql(
                "CALL %s.system.snapshot('%s', 'fable', 'loc', map(2, 1, 1))",
                catalogName, sourceName));

    AssertHelpers.assertThrows(
        "Should reject calls with empty table identifier",
        IllegalArgumentException.class,
        "Cannot handle an empty identifier",
        () -> sql("CALL %s.system.snapshot('', 'dest')", catalogName));

    AssertHelpers.assertThrows(
        "Should reject calls with empty table identifier",
        IllegalArgumentException.class,
        "Cannot handle an empty identifier",
        () -> sql("CALL %s.system.snapshot('src', '')", catalogName));
  }
}
