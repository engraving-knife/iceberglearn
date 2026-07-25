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

import static org.apache.iceberg.TableProperties.WRITE_AUDIT_PUBLISH_ENABLED;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchProcedureException;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

/**
 * 文件级说明：测试 TestCherrypickSnapshotProcedure 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 cherrypick快照存储过程 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestCherrypickSnapshotProcedure extends SparkExtensionsTestBase {

  /** 测试cherrypick快照存储过程。 */
  public TestCherrypickSnapshotProcedure(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试cherrypick快照使用位置参数场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCherrypickSnapshotUsingPositionalArgs() {
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);
    sql("ALTER TABLE %s SET TBLPROPERTIES ('%s' 'true')", tableName, WRITE_AUDIT_PUBLISH_ENABLED);

    spark.conf().set("spark.wap.id", "1");

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should not see rows from staged snapshot",
        ImmutableList.of(),
        sql("SELECT * FROM %s", tableName));

    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot wapSnapshot = Iterables.getOnlyElement(table.snapshots());

    List<Object[]> output =
        sql(
            "CALL %s.system.cherrypick_snapshot('%s', %dL)",
            catalogName, tableIdent, wapSnapshot.snapshotId());

    table.refresh();

    Snapshot currentSnapshot = table.currentSnapshot();

    assertEquals(
        "Procedure output must match",
        ImmutableList.of(row(wapSnapshot.snapshotId(), currentSnapshot.snapshotId())),
        output);

    assertEquals(
        "Cherrypick must be successful",
        ImmutableList.of(row(1L, "a")),
        sql("SELECT * FROM %s", tableName));
  }

  /** 测试cherrypick快照使用命名参数场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCherrypickSnapshotUsingNamedArgs() {
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);
    sql("ALTER TABLE %s SET TBLPROPERTIES ('%s' 'true')", tableName, WRITE_AUDIT_PUBLISH_ENABLED);

    spark.conf().set("spark.wap.id", "1");

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should not see rows from staged snapshot",
        ImmutableList.of(),
        sql("SELECT * FROM %s", tableName));

    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot wapSnapshot = Iterables.getOnlyElement(table.snapshots());

    List<Object[]> output =
        sql(
            "CALL %s.system.cherrypick_snapshot(snapshot_id => %dL, table => '%s')",
            catalogName, wapSnapshot.snapshotId(), tableIdent);

    table.refresh();

    Snapshot currentSnapshot = table.currentSnapshot();

    assertEquals(
        "Procedure output must match",
        ImmutableList.of(row(wapSnapshot.snapshotId(), currentSnapshot.snapshotId())),
        output);

    assertEquals(
        "Cherrypick must be successful",
        ImmutableList.of(row(1L, "a")),
        sql("SELECT * FROM %s", tableName));
  }

  /** 测试cherrypick快照refreshesrelationcache场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCherrypickSnapshotRefreshesRelationCache() {
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);
    sql("ALTER TABLE %s SET TBLPROPERTIES ('%s' 'true')", tableName, WRITE_AUDIT_PUBLISH_ENABLED);

    Dataset<Row> query = spark.sql("SELECT * FROM " + tableName + " WHERE id = 1");
    query.createOrReplaceTempView("tmp");

    spark.sql("CACHE TABLE tmp");

    assertEquals("View should not produce rows", ImmutableList.of(), sql("SELECT * FROM tmp"));

    spark.conf().set("spark.wap.id", "1");

    sql("INSERT INTO TABLE %s VALUES (1, 'a')", tableName);

    assertEquals(
        "Should not see rows from staged snapshot",
        ImmutableList.of(),
        sql("SELECT * FROM %s", tableName));

    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot wapSnapshot = Iterables.getOnlyElement(table.snapshots());

    sql(
        "CALL %s.system.cherrypick_snapshot('%s', %dL)",
        catalogName, tableIdent, wapSnapshot.snapshotId());

    assertEquals(
        "Cherrypick snapshot should be visible",
        ImmutableList.of(row(1L, "a")),
        sql("SELECT * FROM tmp"));

    sql("UNCACHE TABLE tmp");
  }

  /** 测试cherrypickinvalid快照场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCherrypickInvalidSnapshot() {
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);

    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.system.cherrypick_snapshot('%s', -1L)", catalogName, tableIdent))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Cannot cherry-pick unknown snapshot ID: -1");
  }

  /** 测试invalidcherrypick快照场景场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testInvalidCherrypickSnapshotCases() {
    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.system.cherrypick_snapshot('n', table => 't', 1L)", catalogName))
        .isInstanceOf(AnalysisException.class)
        .hasMessage("Named and positional arguments cannot be mixed");

    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.custom.cherrypick_snapshot('n', 't', 1L)", catalogName))
        .isInstanceOf(NoSuchProcedureException.class)
        .hasMessage("Procedure custom.cherrypick_snapshot not found");

    Assertions.assertThatThrownBy(() -> sql("CALL %s.system.cherrypick_snapshot('t')", catalogName))
        .isInstanceOf(AnalysisException.class)
        .hasMessage("Missing required parameters: [snapshot_id]");

    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.system.cherrypick_snapshot('', 1L)", catalogName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot handle an empty identifier for argument table");

    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.system.cherrypick_snapshot('t', 2.2)", catalogName))
        .isInstanceOf(AnalysisException.class)
        .hasMessageStartingWith("Wrong arg type for snapshot_id: cannot cast");
  }
}
