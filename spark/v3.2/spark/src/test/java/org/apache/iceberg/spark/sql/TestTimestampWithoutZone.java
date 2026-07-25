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

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkCatalogTestBase;
import org.apache.iceberg.spark.SparkSQLProperties;
import org.apache.iceberg.spark.SparkSessionCatalog;
import org.apache.iceberg.spark.SparkUtil;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestTimestampWithoutZone 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 时间戳无时区 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestTimestampWithoutZone extends SparkCatalogTestBase {

  private static final String newTableName = "created_table";
  private final Map<String, String> config;

  private static final Schema schema =
      new Schema(
          Types.NestedField.required(1, "id", Types.LongType.get()),
          Types.NestedField.required(2, "ts", Types.TimestampType.withoutZone()),
          Types.NestedField.required(3, "tsz", Types.TimestampType.withZone()));

  private final List<Object[]> values =
      ImmutableList.of(
          row(1L, toTimestamp("2021-01-01T00:00:00.0"), toTimestamp("2021-02-01T00:00:00.0")),
          row(2L, toTimestamp("2021-01-01T00:00:00.0"), toTimestamp("2021-02-01T00:00:00.0")),
          row(3L, toTimestamp("2021-01-01T00:00:00.0"), toTimestamp("2021-02-01T00:00:00.0")));

  /** 参数。 */
  @Parameterized.Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "spark_catalog",
        SparkSessionCatalog.class.getName(),
        ImmutableMap.of(
            "type", "hive",
            "default-namespace", "default",
            "parquet-enabled", "true",
            "cache-enabled", "false")
      }
    };
  }

  /** 测试时间戳无时区。 */
  public TestTimestampWithoutZone(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
    this.config = config;
  }

  /** 创建表。 */
  @Before
  public void createTables() {
    validationCatalog.createTable(tableIdent, schema);
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    validationCatalog.dropTable(tableIdent, true);
    sql("DROP TABLE IF EXISTS %s", newTableName);
  }

  /** 测试写时间戳无时区error场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testWriteTimestampWithoutZoneError() {
    AssertHelpers.assertThrows(
        String.format(
            "Write operation performed on a timestamp without timezone field while "
                + "'%s' set to false should throw exception",
            SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE),
        IllegalArgumentException.class,
        SparkUtil.TIMESTAMP_WITHOUT_TIMEZONE_ERROR,
        () -> sql("INSERT INTO %s VALUES %s", tableName, rowToSqlValues(values)));
  }

  /** 测试追加时间戳无时区场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAppendTimestampWithoutZone() {
    withSQLConf(
        ImmutableMap.of(SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE, "true"),
        () -> {
          sql("INSERT INTO %s VALUES %s", tableName, rowToSqlValues(values));

          Assert.assertEquals(
              "Should have " + values.size() + " row",
              (long) values.size(),
              scalarSql("SELECT count(*) FROM %s", tableName));

          assertEquals(
              "Row data should match expected",
              values,
              sql("SELECT * FROM %s ORDER BY id", tableName));
        });
  }

  /** 测试创建作为select带时间戳无时区场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateAsSelectWithTimestampWithoutZone() {
    withSQLConf(
        ImmutableMap.of(SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE, "true"),
        () -> {
          sql("INSERT INTO %s VALUES %s", tableName, rowToSqlValues(values));

          sql("CREATE TABLE %s USING iceberg AS SELECT * FROM %s", newTableName, tableName);

          Assert.assertEquals(
              "Should have " + values.size() + " row",
              (long) values.size(),
              scalarSql("SELECT count(*) FROM %s", newTableName));

          assertEquals(
              "Row data should match expected",
              sql("SELECT * FROM %s ORDER BY id", tableName),
              sql("SELECT * FROM %s ORDER BY id", newTableName));
        });
  }

  /** 测试创建新建表应have时间戳带时区Iceberg类型场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateNewTableShouldHaveTimestampWithZoneIcebergType() {
    withSQLConf(
        ImmutableMap.of(SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE, "true"),
        () -> {
          sql("INSERT INTO %s VALUES %s", tableName, rowToSqlValues(values));

          sql("CREATE TABLE %s USING iceberg AS SELECT * FROM %s", newTableName, tableName);

          Assert.assertEquals(
              "Should have " + values.size() + " row",
              (long) values.size(),
              scalarSql("SELECT count(*) FROM %s", newTableName));

          assertEquals(
              "Data from created table should match data from base table",
              sql("SELECT * FROM %s ORDER BY id", tableName),
              sql("SELECT * FROM %s ORDER BY id", newTableName));

          Table createdTable =
              validationCatalog.loadTable(TableIdentifier.of("default", newTableName));
          assertFieldsType(createdTable.schema(), Types.TimestampType.withZone(), "ts", "tsz");
        });
  }

  /** 测试创建新建表应have时间戳无时区Iceberg类型场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateNewTableShouldHaveTimestampWithoutZoneIcebergType() {
    withSQLConf(
        ImmutableMap.of(
            SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE, "true",
            SparkSQLProperties.USE_TIMESTAMP_WITHOUT_TIME_ZONE_IN_NEW_TABLES, "true"),
        () -> {
          spark
              .sessionState()
              .catalogManager()
              .currentCatalog()
              .initialize(catalog.name(), new CaseInsensitiveStringMap(config));
          sql("INSERT INTO %s VALUES %s", tableName, rowToSqlValues(values));

          sql("CREATE TABLE %s USING iceberg AS SELECT * FROM %s", newTableName, tableName);

          Assert.assertEquals(
              "Should have " + values.size() + " row",
              (long) values.size(),
              scalarSql("SELECT count(*) FROM %s", newTableName));

          assertEquals(
              "Row data should match expected",
              sql("SELECT * FROM %s ORDER BY id", tableName),
              sql("SELECT * FROM %s ORDER BY id", newTableName));
          Table createdTable =
              validationCatalog.loadTable(TableIdentifier.of("default", newTableName));
          assertFieldsType(createdTable.schema(), Types.TimestampType.withoutZone(), "ts", "tsz");
        });
  }

  /** 到时间戳。 */
  private Timestamp toTimestamp(String value) {
    return new Timestamp(DateTime.parse(value).getMillis());
  }

  /** 行到SQL值。 */
  private String rowToSqlValues(List<Object[]> rows) {
    List<String> rowValues =
        rows.stream()
            .map(
                row -> {
                  List<String> columns =
                      Arrays.stream(row)
                          .map(
                              value -> {
                                if (value instanceof Long) {
                                  return value.toString();
                                } else if (value instanceof Timestamp) {
                                  return String.format("timestamp '%s'", value);
                                }
                                throw new RuntimeException("Type is not supported");
                              })
                          .collect(Collectors.toList());
                  return "(" + Joiner.on(",").join(columns) + ")";
                })
            .collect(Collectors.toList());
    return Joiner.on(",").join(rowValues);
  }

  /** 断言字段类型。 */
  private void assertFieldsType(Schema actual, Type.PrimitiveType expected, String... fields) {
    actual
        .select(fields)
        .asStruct()
        .fields()
        .forEach(field -> Assert.assertEquals(expected, field.type()));
  }
}
