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

import java.util.Map;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestAlterTableSchema 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 修改表模式 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestAlterTableSchema extends SparkExtensionsTestBase {
  /** 测试修改表模式。 */
  public TestAlterTableSchema(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 移除表。 */
  @After
  public void removeTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试集合标识符字段场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSetIdentifierFields() {
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, "
            + "location struct<lon:bigint NOT NULL,lat:bigint NOT NULL> NOT NULL) USING iceberg",
        tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertTrue(
        "Table should start without identifier", table.schema().identifierFieldIds().isEmpty());

    sql("ALTER TABLE %s SET IDENTIFIER FIELDS id", tableName);
    table.refresh();
    Assert.assertEquals(
        "Should have new identifier field",
        Sets.newHashSet(table.schema().findField("id").fieldId()),
        table.schema().identifierFieldIds());

    sql("ALTER TABLE %s SET IDENTIFIER FIELDS id, location.lon", tableName);
    table.refresh();
    Assert.assertEquals(
        "Should have new identifier field",
        Sets.newHashSet(
            table.schema().findField("id").fieldId(),
            table.schema().findField("location.lon").fieldId()),
        table.schema().identifierFieldIds());

    sql("ALTER TABLE %s SET IDENTIFIER FIELDS location.lon", tableName);
    table.refresh();
    Assert.assertEquals(
        "Should have new identifier field",
        Sets.newHashSet(table.schema().findField("location.lon").fieldId()),
        table.schema().identifierFieldIds());
  }

  /** 测试集合invalid标识符字段场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSetInvalidIdentifierFields() {
    sql("CREATE TABLE %s (id bigint NOT NULL, id2 bigint) USING iceberg", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertTrue(
        "Table should start without identifier", table.schema().identifierFieldIds().isEmpty());
    AssertHelpers.assertThrows(
        "should not allow setting unknown fields",
        IllegalArgumentException.class,
        "not found in current schema or added columns",
        () -> sql("ALTER TABLE %s SET IDENTIFIER FIELDS unknown", tableName));

    AssertHelpers.assertThrows(
        "should not allow setting optional fields",
        IllegalArgumentException.class,
        "not a required field",
        () -> sql("ALTER TABLE %s SET IDENTIFIER FIELDS id2", tableName));
  }

  /** 测试删除标识符字段场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropIdentifierFields() {
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, "
            + "location struct<lon:bigint NOT NULL,lat:bigint NOT NULL> NOT NULL) USING iceberg",
        tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertTrue(
        "Table should start without identifier", table.schema().identifierFieldIds().isEmpty());

    sql("ALTER TABLE %s SET IDENTIFIER FIELDS id, location.lon", tableName);
    table.refresh();
    Assert.assertEquals(
        "Should have new identifier fields",
        Sets.newHashSet(
            table.schema().findField("id").fieldId(),
            table.schema().findField("location.lon").fieldId()),
        table.schema().identifierFieldIds());

    sql("ALTER TABLE %s DROP IDENTIFIER FIELDS id", tableName);
    table.refresh();
    Assert.assertEquals(
        "Should removed identifier field",
        Sets.newHashSet(table.schema().findField("location.lon").fieldId()),
        table.schema().identifierFieldIds());

    sql("ALTER TABLE %s SET IDENTIFIER FIELDS id, location.lon", tableName);
    table.refresh();
    Assert.assertEquals(
        "Should have new identifier fields",
        Sets.newHashSet(
            table.schema().findField("id").fieldId(),
            table.schema().findField("location.lon").fieldId()),
        table.schema().identifierFieldIds());

    sql("ALTER TABLE %s DROP IDENTIFIER FIELDS id, location.lon", tableName);
    table.refresh();
    Assert.assertEquals(
        "Should have no identifier field", Sets.newHashSet(), table.schema().identifierFieldIds());
  }

  /** 测试删除invalid标识符字段场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropInvalidIdentifierFields() {
    sql(
        "CREATE TABLE %s (id bigint NOT NULL, data string NOT NULL, "
            + "location struct<lon:bigint NOT NULL,lat:bigint NOT NULL> NOT NULL) USING iceberg",
        tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertTrue(
        "Table should start without identifier", table.schema().identifierFieldIds().isEmpty());
    AssertHelpers.assertThrows(
        "should not allow dropping unknown fields",
        IllegalArgumentException.class,
        "field unknown not found",
        () -> sql("ALTER TABLE %s DROP IDENTIFIER FIELDS unknown", tableName));

    sql("ALTER TABLE %s SET IDENTIFIER FIELDS id", tableName);
    AssertHelpers.assertThrows(
        "should not allow dropping a field that is not an identifier",
        IllegalArgumentException.class,
        "data is not an identifier field",
        () -> sql("ALTER TABLE %s DROP IDENTIFIER FIELDS data", tableName));

    AssertHelpers.assertThrows(
        "should not allow dropping a nested field that is not an identifier",
        IllegalArgumentException.class,
        "location.lon is not an identifier field",
        () -> sql("ALTER TABLE %s DROP IDENTIFIER FIELDS location.lon", tableName));
  }
}
