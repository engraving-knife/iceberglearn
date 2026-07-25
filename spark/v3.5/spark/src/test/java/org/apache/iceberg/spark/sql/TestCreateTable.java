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
package org.apache.iceberg.spark.sql;

import java.io.File;
import java.util.Map;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.spark.SparkCatalogTestBase;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StructType;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * 文件级说明：测试 TestCreateTable 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 创建表 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestCreateTable extends SparkCatalogTestBase {
  /** 测试创建表。 */
  public TestCreateTable(String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 删除测试表。 */
  @After
  public void dropTestTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试转换ignore场景场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testTransformIgnoreCase() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));
    sql(
        "CREATE TABLE IF NOT EXISTS %s (id BIGINT NOT NULL, ts timestamp) "
            + "USING iceberg partitioned by (HOURS(ts))",
        tableName);
    Assert.assertTrue("Table should already exist", validationCatalog.tableExists(tableIdent));
    sql(
        "CREATE TABLE IF NOT EXISTS %s (id BIGINT NOT NULL, ts timestamp) "
            + "USING iceberg partitioned by (hours(ts))",
        tableName);
    Assert.assertTrue("Table should already exist", validationCatalog.tableExists(tableIdent));
  }

  /** 测试转换singularform场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testTransformSingularForm() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));
    sql(
        "CREATE TABLE IF NOT EXISTS %s (id BIGINT NOT NULL, ts timestamp) "
            + "USING iceberg partitioned by (hour(ts))",
        tableName);
    Assert.assertTrue("Table should exist", validationCatalog.tableExists(tableIdent));
  }

  /** 测试转换pluralform场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testTransformPluralForm() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));
    sql(
        "CREATE TABLE IF NOT EXISTS %s (id BIGINT NOT NULL, ts timestamp) "
            + "USING iceberg partitioned by (hours(ts))",
        tableName);
    Assert.assertTrue("Table should exist", validationCatalog.tableExists(tableIdent));
  }

  /** 测试创建表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTable() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql("CREATE TABLE %s (id BIGINT NOT NULL, data STRING) USING iceberg", tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNotNull("Should load the new table", table);

    StructType expectedSchema =
        StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));
    Assert.assertEquals(
        "Should have the expected schema", expectedSchema, table.schema().asStruct());
    Assert.assertEquals("Should not be partitioned", 0, table.spec().fields().size());
    Assert.assertNull(
        "Should not have the default format set",
        table.properties().get(TableProperties.DEFAULT_FILE_FORMAT));
  }

  /** 测试创建表在root命名空间场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTableInRootNamespace() {
    Assume.assumeTrue(
        "Hadoop has no default namespace configured", "testhadoop".equals(catalogName));

    try {
      sql("CREATE TABLE %s.table (id bigint) USING iceberg", catalogName);
    } finally {
      sql("DROP TABLE IF EXISTS %s.table", catalogName);
    }
  }

  /** 测试创建表使用Parquet场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTableUsingParquet() {
    Assume.assumeTrue(
        "Not working with session catalog because Spark will not use v2 for a Parquet table",
        !"spark_catalog".equals(catalogName));

    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql("CREATE TABLE %s (id BIGINT NOT NULL, data STRING) USING parquet", tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNotNull("Should load the new table", table);

    StructType expectedSchema =
        StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));
    Assert.assertEquals(
        "Should have the expected schema", expectedSchema, table.schema().asStruct());
    Assert.assertEquals("Should not be partitioned", 0, table.spec().fields().size());
    Assert.assertEquals(
        "Should not have default format parquet",
        "parquet",
        table.properties().get(TableProperties.DEFAULT_FILE_FORMAT));

    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CREATE TABLE %s.default.fail (id BIGINT NOT NULL, data STRING) USING crocodile",
                    catalogName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported format in USING: crocodile");
  }

  /** 测试创建表分区通过场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTablePartitionedBy() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL, created_at TIMESTAMP, category STRING, data STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (category, bucket(8, id), days(created_at))",
        tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNotNull("Should load the new table", table);

    StructType expectedSchema =
        StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "created_at", Types.TimestampType.withZone()),
            NestedField.optional(3, "category", Types.StringType.get()),
            NestedField.optional(4, "data", Types.StringType.get()));
    Assert.assertEquals(
        "Should have the expected schema", expectedSchema, table.schema().asStruct());

    PartitionSpec expectedSpec =
        PartitionSpec.builderFor(new Schema(expectedSchema.fields()))
            .identity("category")
            .bucket("id", 8)
            .day("created_at")
            .build();
    Assert.assertEquals("Should be partitioned correctly", expectedSpec, table.spec());

    Assert.assertNull(
        "Should not have the default format set",
        table.properties().get(TableProperties.DEFAULT_FILE_FORMAT));
  }

  /** 测试创建表列comments场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTableColumnComments() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL COMMENT 'Unique identifier', data STRING COMMENT 'Data value') "
            + "USING iceberg",
        tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNotNull("Should load the new table", table);

    StructType expectedSchema =
        StructType.of(
            NestedField.required(1, "id", Types.LongType.get(), "Unique identifier"),
            NestedField.optional(2, "data", Types.StringType.get(), "Data value"));
    Assert.assertEquals(
        "Should have the expected schema", expectedSchema, table.schema().asStruct());
    Assert.assertEquals("Should not be partitioned", 0, table.spec().fields().size());
    Assert.assertNull(
        "Should not have the default format set",
        table.properties().get(TableProperties.DEFAULT_FILE_FORMAT));
  }

  /** 测试创建表comment场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTableComment() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL, data STRING) "
            + "USING iceberg "
            + "COMMENT 'Table doc'",
        tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNotNull("Should load the new table", table);

    StructType expectedSchema =
        StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));
    Assert.assertEquals(
        "Should have the expected schema", expectedSchema, table.schema().asStruct());
    Assert.assertEquals("Should not be partitioned", 0, table.spec().fields().size());
    Assert.assertNull(
        "Should not have the default format set",
        table.properties().get(TableProperties.DEFAULT_FILE_FORMAT));
    Assert.assertEquals(
        "Should have the table comment set in properties",
        "Table doc",
        table.properties().get(TableCatalog.PROP_COMMENT));
  }

  /** 测试创建表路径场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTableLocation() throws Exception {
    Assume.assumeTrue(
        "Cannot set custom locations for Hadoop catalog tables",
        !(validationCatalog instanceof HadoopCatalog));

    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    File tableLocation = temp.newFolder();
    Assert.assertTrue(tableLocation.delete());

    String location = "file:" + tableLocation.toString();

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL, data STRING) "
            + "USING iceberg "
            + "LOCATION '%s'",
        tableName, location);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNotNull("Should load the new table", table);

    StructType expectedSchema =
        StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));
    Assert.assertEquals(
        "Should have the expected schema", expectedSchema, table.schema().asStruct());
    Assert.assertEquals("Should not be partitioned", 0, table.spec().fields().size());
    Assert.assertNull(
        "Should not have the default format set",
        table.properties().get(TableProperties.DEFAULT_FILE_FORMAT));
    Assert.assertEquals("Should have a custom table location", location, table.location());
  }

  /** 测试创建表属性场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTableProperties() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL, data STRING) "
            + "USING iceberg "
            + "TBLPROPERTIES (p1=2, p2='x')",
        tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNotNull("Should load the new table", table);

    StructType expectedSchema =
        StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));
    Assert.assertEquals(
        "Should have the expected schema", expectedSchema, table.schema().asStruct());
    Assert.assertEquals("Should not be partitioned", 0, table.spec().fields().size());
    Assert.assertEquals("Should have property p1", "2", table.properties().get("p1"));
    Assert.assertEquals("Should have property p2", "x", table.properties().get("p2"));
  }

  /** 测试创建表带格式v2through表属性场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateTableWithFormatV2ThroughTableProperty() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL, data STRING) "
            + "USING iceberg "
            + "TBLPROPERTIES ('format-version'='2')",
        tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertEquals(
        "should create table using format v2",
        2,
        ((BaseTable) table).operations().current().formatVersion());
  }

  /** 测试upgrade表带格式v2through表属性场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUpgradeTableWithFormatV2ThroughTableProperty() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL, data STRING) "
            + "USING iceberg "
            + "TBLPROPERTIES ('format-version'='1')",
        tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    TableOperations ops = ((BaseTable) table).operations();
    Assert.assertEquals("should create table using format v1", 1, ops.refresh().formatVersion());

    sql("ALTER TABLE %s SET TBLPROPERTIES ('format-version'='2')", tableName);
    Assert.assertEquals("should update table to use format v2", 2, ops.refresh().formatVersion());
  }

  /** 测试downgrade表到格式v1through表属性fails场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDowngradeTableToFormatV1ThroughTablePropertyFails() {
    Assert.assertFalse("Table should not already exist", validationCatalog.tableExists(tableIdent));

    sql(
        "CREATE TABLE %s "
            + "(id BIGINT NOT NULL, data STRING) "
            + "USING iceberg "
            + "TBLPROPERTIES ('format-version'='2')",
        tableName);

    Table table = validationCatalog.loadTable(tableIdent);
    TableOperations ops = ((BaseTable) table).operations();
    Assert.assertEquals("should create table using format v2", 2, ops.refresh().formatVersion());

    Assertions.assertThatThrownBy(
            () -> sql("ALTER TABLE %s SET TBLPROPERTIES ('format-version'='1')", tableName))
        .cause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot downgrade v2 table to v1");
  }
}
