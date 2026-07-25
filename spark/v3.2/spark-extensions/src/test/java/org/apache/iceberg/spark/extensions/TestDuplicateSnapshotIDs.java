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
import org.apache.iceberg.SnapshotIdGeneratorUtil;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * 文件级说明：测试 TestDuplicateSnapshotIDs 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 duplicate快照ids 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestDuplicateSnapshotIDs extends SparkExtensionsTestBase {

  /** 测试duplicate快照ids。 */
  public TestDuplicateSnapshotIDs(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试same快照idback到back场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSameSnapshotIDBackToBack() {
    sql("DROP TABLE IF EXISTS %s ", tableName);
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);

    try (MockedStatic<SnapshotIdGeneratorUtil> utilities =
        Mockito.mockStatic(SnapshotIdGeneratorUtil.class)) {
      utilities.when(SnapshotIdGeneratorUtil::generateSnapshotID).thenReturn(42L, 42L, 43L);
      // use 42L as snapshot id for the insert
      sql("INSERT INTO TABLE %s SELECT 1, 'a' ", tableName);
      // use 42L as snapshot id for the insert and use 43L on retry.
      sql("INSERT INTO TABLE %s SELECT 2, 'b' ", tableName);
    }
    // use regular snapshot id logic for the insert
    sql("INSERT INTO TABLE %s SELECT 3, 'c' ", tableName);

    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(row(1L, "a"), row(2L, "b"), row(3L, "c"));
    assertEquals(
        "should have all the rows", expectedRows, sql("SELECT * from %s order by id", tableName));
    Assert.assertEquals(sql("SELECT * from %s.snapshots", tableName).size(), 3);
    Assert.assertEquals(
        sql("SELECT * from %s.snapshots where snapshot_id = 42L", tableName).size(), 1);
    Assert.assertEquals(
        sql("SELECT * from %s.snapshots where snapshot_id = 43L", tableName).size(), 1);
  }

  /** 测试same快照id场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSameSnapshotID() {
    sql("DROP TABLE IF EXISTS %s ", tableName);
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);

    try (MockedStatic<SnapshotIdGeneratorUtil> utilities =
        Mockito.mockStatic(SnapshotIdGeneratorUtil.class)) {
      utilities.when(SnapshotIdGeneratorUtil::generateSnapshotID).thenReturn(42L);
      // use 42L as snapshot id for the insert
      sql("INSERT INTO TABLE %s SELECT 1, 'a' ", tableName);
    }
    // use regular snapshot id logic for the inserts
    sql("INSERT INTO TABLE %s SELECT 2, 'b' ", tableName);
    sql("INSERT INTO TABLE %s SELECT 3, 'c' ", tableName);
    try (MockedStatic<SnapshotIdGeneratorUtil> utilities =
        Mockito.mockStatic(SnapshotIdGeneratorUtil.class)) {
      utilities.when(SnapshotIdGeneratorUtil::generateSnapshotID).thenReturn(42L, 43L);
      // use 42L as snapshot id for the insert and retry with 43L.
      sql("INSERT INTO TABLE %s SELECT 4, 'd' ", tableName);
    }
    // use regular snapshot id logic for the insert
    sql("INSERT INTO TABLE %s SELECT 5, 'e' ", tableName);

    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(row(1L, "a"), row(2L, "b"), row(3L, "c"), row(4L, "d"), row(5L, "e"));
    assertEquals(
        "should have all the rows", expectedRows, sql("SELECT * from %s order by id", tableName));
    Assert.assertEquals(sql("SELECT * from %s.snapshots", tableName).size(), 5);
    Assert.assertEquals(
        sql("SELECT * from %s.snapshots where snapshot_id = 42L", tableName).size(), 1);
    Assert.assertEquals(
        sql("SELECT * from %s.snapshots where snapshot_id = 43L", tableName).size(), 1);
  }
}
