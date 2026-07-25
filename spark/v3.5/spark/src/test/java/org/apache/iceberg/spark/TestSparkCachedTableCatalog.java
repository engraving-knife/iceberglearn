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

import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 文件级说明：测试 TestSparkCachedTableCatalog 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Spark缓存表目录 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkCachedTableCatalog extends SparkTestBaseWithCatalog {

  private static final SparkTableCache TABLE_CACHE = SparkTableCache.get();

  /** 初始化缓存表目录。 */
  @BeforeClass
  public static void setupCachedTableCatalog() {
    spark.conf().set("spark.sql.catalog.testcache", SparkCachedTableCatalog.class.getName());
  }

  /** unset缓存表目录。 */
  @AfterClass
  public static void unsetCachedTableCatalog() {
    spark.conf().unset("spark.sql.catalog.testcache");
  }

  /** 测试Spark缓存表目录。 */
  public TestSparkCachedTableCatalog() {
    super(SparkCatalogConfig.HIVE);
  }

  /** 测试时间旅行场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testTimeTravel() {
    sql("CREATE TABLE %s (id INT, dep STRING) USING iceberg", tableName);

    Table table = validationCatalog.loadTable(tableIdent);

    sql("INSERT INTO TABLE %s VALUES (1, 'hr')", tableName);

    table.refresh();
    Snapshot firstSnapshot = table.currentSnapshot();
    waitUntilAfter(firstSnapshot.timestampMillis());

    sql("INSERT INTO TABLE %s VALUES (2, 'hr')", tableName);

    table.refresh();
    Snapshot secondSnapshot = table.currentSnapshot();
    waitUntilAfter(secondSnapshot.timestampMillis());

    sql("INSERT INTO TABLE %s VALUES (3, 'hr')", tableName);

    table.refresh();

    try {
      TABLE_CACHE.add("key", table);

      assertEquals(
          "Should have expected rows in 3rd snapshot",
          ImmutableList.of(row(1, "hr"), row(2, "hr"), row(3, "hr")),
          sql("SELECT * FROM testcache.key ORDER BY id"));

      assertEquals(
          "Should have expected rows in 2nd snapshot",
          ImmutableList.of(row(1, "hr"), row(2, "hr")),
          sql(
              "SELECT * FROM testcache.`key#at_timestamp_%s` ORDER BY id",
              secondSnapshot.timestampMillis()));

      assertEquals(
          "Should have expected rows in 1st snapshot",
          ImmutableList.of(row(1, "hr")),
          sql(
              "SELECT * FROM testcache.`key#snapshot_id_%d` ORDER BY id",
              firstSnapshot.snapshotId()));

    } finally {
      TABLE_CACHE.remove("key");
    }
  }
}
