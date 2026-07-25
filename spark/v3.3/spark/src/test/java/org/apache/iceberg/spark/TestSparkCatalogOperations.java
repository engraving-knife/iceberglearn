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

import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.Catalog;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableChange;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestSparkCatalogOperations 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 Spark目录操作 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkCatalogOperations extends SparkCatalogTestBase {
  /** 测试Spark目录操作。 */
  public TestSparkCatalogOperations(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Before
  public void createTable() {
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);
  }

  /** 移除表。 */
  @After
  public void removeTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试修改表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAlterTable() throws NoSuchTableException {
    BaseCatalog catalog = (BaseCatalog) spark.sessionState().catalogManager().catalog(catalogName);
    Identifier identifier = Identifier.of(tableIdent.namespace().levels(), tableIdent.name());

    String fieldName = "location";
    String propsKey = "note";
    String propsValue = "jazz";
    Table table =
        catalog.alterTable(
            identifier,
            TableChange.addColumn(new String[] {fieldName}, DataTypes.StringType, true),
            TableChange.setProperty(propsKey, propsValue));

    Assert.assertNotNull("Should return updated table", table);

    StructField expectedField = DataTypes.createStructField(fieldName, DataTypes.StringType, true);
    Assert.assertEquals(
        "Adding a column to a table should return the updated table with the new column",
        table.schema().fields()[2],
        expectedField);

    Assert.assertTrue(
        "Adding a property to a table should return the updated table with the new property",
        table.properties().containsKey(propsKey));
    Assert.assertEquals(
        "Altering a table to add a new property should add the correct value",
        propsValue,
        table.properties().get(propsKey));
  }

  /** 测试invalidate表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testInvalidateTable() {
    // load table to CachingCatalog
    sql("SELECT count(1) FROM %s", tableName);

    // recreate table from another catalog or program
    Catalog anotherCatalog = validationCatalog;
    Schema schema = anotherCatalog.loadTable(tableIdent).schema();
    anotherCatalog.dropTable(tableIdent);
    anotherCatalog.createTable(tableIdent, schema);

    // invalidate and reload table
    sql("REFRESH TABLE %s", tableName);
    sql("SELECT count(1) FROM %s", tableName);
  }
}
