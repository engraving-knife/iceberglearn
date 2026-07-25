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

import java.util.List;
import java.util.Map;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.spark.SparkCatalogConfig;
import org.apache.iceberg.spark.SparkCatalogTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestRefreshTable 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 刷新表 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestRefreshTable extends SparkCatalogTestBase {

  /** 测试刷新表。 */
  public TestRefreshTable(String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Before
  public void createTables() {
    sql("CREATE TABLE %s (key int, value int) USING iceberg", tableName);
    sql("INSERT INTO %s VALUES (1,1)", tableName);
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试刷新command场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRefreshCommand() {
    // We are not allowed to change the session catalog after it has been initialized, so build a
    // new one
    if (catalogName.equals(SparkCatalogConfig.SPARK.catalogName())
        || catalogName.equals(SparkCatalogConfig.HADOOP.catalogName())) {
      spark.conf().set("spark.sql.catalog." + catalogName + ".cache-enabled", true);
      spark = spark.cloneSession();
    }

    List<Object[]> originalExpected = ImmutableList.of(row(1, 1));
    List<Object[]> originalActual = sql("SELECT * FROM %s", tableName);
    assertEquals("Table should start as expected", originalExpected, originalActual);

    // Modify table outside of spark, it should be cached so Spark should see the same value after
    // mutation
    Table table = validationCatalog.loadTable(tableIdent);
    DataFile file = table.currentSnapshot().addedDataFiles(table.io()).iterator().next();
    table.newDelete().deleteFile(file).commit();

    List<Object[]> cachedActual = sql("SELECT * FROM %s", tableName);
    assertEquals("Cached table should be unchanged", originalExpected, cachedActual);

    // Refresh the Spark catalog, should be empty
    sql("REFRESH TABLE %s", tableName);
    List<Object[]> refreshedExpected = ImmutableList.of();
    List<Object[]> refreshedActual = sql("SELECT * FROM %s", tableName);
    assertEquals("Refreshed table should be empty", refreshedExpected, refreshedActual);
  }
}
