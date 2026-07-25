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

import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.ChangelogOperation;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.spark.SparkReadOptions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestCreateChangelogViewProcedure 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 创建变更日志视图存储过程 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestCreateChangelogViewProcedure extends SparkExtensionsTestBase {
  private static final String DELETE = ChangelogOperation.DELETE.name();
  private static final String INSERT = ChangelogOperation.INSERT.name();
  private static final String UPDATE_BEFORE = ChangelogOperation.UPDATE_BEFORE.name();
  private static final String UPDATE_AFTER = ChangelogOperation.UPDATE_AFTER.name();

  /** 测试创建变更日志视图存储过程。 */
  public TestCreateChangelogViewProcedure(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 移除表。 */
  @After
  public void removeTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 创建表带two列。 */
  public void createTableWithTwoColumns() {
    sql("CREATE TABLE %s (id INT, data STRING) USING iceberg", tableName);
    sql("ALTER TABLE %s ADD PARTITION FIELD data", tableName);
  }

  /** 创建表带three列。 */
  private void createTableWithThreeColumns() {
    sql("CREATE TABLE %s (id INT, data STRING, age INT) USING iceberg", tableName);
    sql("ALTER TABLE %s ADD PARTITION FIELD id", tableName);
  }

  /** 创建表带标识符字段。 */
  private void createTableWithIdentifierField() {
    sql("CREATE TABLE %s (id INT NOT NULL, data STRING) USING iceberg", tableName);
    sql("ALTER TABLE %s SET IDENTIFIER FIELDS id", tableName);
  }

  /** 测试customized视图name场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCustomizedViewName() {
    createTableWithTwoColumns();
    sql("INSERT INTO %s VALUES (1, 'a')", tableName);
    sql("INSERT INTO %s VALUES (2, 'b')", tableName);

    Table table = validationCatalog.loadTable(tableIdent);

    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (-2, 'b')", tableName);

    table.refresh();

    Snapshot snap2 = table.currentSnapshot();

    sql(
        "CALL %s.system.create_changelog_view("
            + "table => '%s',"
            + "options => map('%s','%s','%s','%s'),"
            + "changelog_view => '%s')",
        catalogName,
        tableName,
        SparkReadOptions.START_SNAPSHOT_ID,
        snap1.snapshotId(),
        SparkReadOptions.END_SNAPSHOT_ID,
        snap2.snapshotId(),
        "cdc_view");

    long rowCount = sql("select * from %s", "cdc_view").stream().count();
    Assert.assertEquals(2, rowCount);
  }

  /** 测试no快照idinput场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testNoSnapshotIdInput() {
    createTableWithTwoColumns();
    sql("INSERT INTO %s VALUES (1, 'a')", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap0 = table.currentSnapshot();

    sql("INSERT INTO %s VALUES (2, 'b')", tableName);
    table.refresh();
    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (-2, 'b')", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view(" + "table => '%s')",
            catalogName, tableName, "cdc_view");

    String viewName = (String) returns.get(0)[0];
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", INSERT, 0, snap0.snapshotId()),
            row(2, "b", INSERT, 1, snap1.snapshotId()),
            row(-2, "b", INSERT, 2, snap2.snapshotId()),
            row(2, "b", DELETE, 2, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id", viewName));
  }

  /** 测试时间戳based查询场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testTimestampsBasedQuery() {
    createTableWithTwoColumns();
    long beginning = System.currentTimeMillis();

    sql("INSERT INTO %s VALUES (1, 'a')", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap0 = table.currentSnapshot();
    long afterFirstInsert = waitUntilAfter(snap0.timestampMillis());

    sql("INSERT INTO %s VALUES (2, 'b')", tableName);
    table.refresh();
    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (-2, 'b')", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    long afterInsertOverwrite = waitUntilAfter(snap2.timestampMillis());
    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view(table => '%s', "
                + "options => map('%s', '%s','%s', '%s'))",
            catalogName,
            tableName,
            SparkReadOptions.START_TIMESTAMP,
            beginning,
            SparkReadOptions.END_TIMESTAMP,
            afterInsertOverwrite);

    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", INSERT, 0, snap0.snapshotId()),
            row(2, "b", INSERT, 1, snap1.snapshotId()),
            row(-2, "b", INSERT, 2, snap2.snapshotId()),
            row(2, "b", DELETE, 2, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id", returns.get(0)[0]));

    // query the timestamps starting from the second insert
    returns =
        sql(
            "CALL %s.system.create_changelog_view(table => '%s', "
                + "options => map('%s', '%s', '%s', '%s'))",
            catalogName,
            tableName,
            SparkReadOptions.START_TIMESTAMP,
            afterFirstInsert,
            SparkReadOptions.END_TIMESTAMP,
            afterInsertOverwrite);

    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(2, "b", INSERT, 0, snap1.snapshotId()),
            row(-2, "b", INSERT, 1, snap2.snapshotId()),
            row(2, "b", DELETE, 1, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id", returns.get(0)[0]));
  }

  /** 测试带carryovers场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testWithCarryovers() {
    createTableWithTwoColumns();
    sql("INSERT INTO %s VALUES (1, 'a')", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap0 = table.currentSnapshot();

    sql("INSERT INTO %s VALUES (2, 'b')", tableName);
    table.refresh();
    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (-2, 'b'), (2, 'b'), (2, 'b')", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view("
                + "remove_carryovers => false,"
                + "table => '%s')",
            catalogName, tableName, "cdc_view");

    String viewName = (String) returns.get(0)[0];
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", INSERT, 0, snap0.snapshotId()),
            row(2, "b", INSERT, 1, snap1.snapshotId()),
            row(-2, "b", INSERT, 2, snap2.snapshotId()),
            row(2, "b", DELETE, 2, snap2.snapshotId()),
            row(2, "b", INSERT, 2, snap2.snapshotId()),
            row(2, "b", INSERT, 2, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id, _change_type", viewName));
  }

  /** 测试更新场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUpdate() {
    createTableWithTwoColumns();
    sql("ALTER TABLE %s DROP PARTITION FIELD data", tableName);
    sql("ALTER TABLE %s ADD PARTITION FIELD id", tableName);

    sql("INSERT INTO %s VALUES (1, 'a'), (2, 'b')", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (3, 'c'), (2, 'd')", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view(table => '%s', identifier_columns => array('id'))",
            catalogName, tableName);

    String viewName = (String) returns.get(0)[0];
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", INSERT, 0, snap1.snapshotId()),
            row(2, "b", INSERT, 0, snap1.snapshotId()),
            row(2, "b", UPDATE_BEFORE, 1, snap2.snapshotId()),
            row(2, "d", UPDATE_AFTER, 1, snap2.snapshotId()),
            row(3, "c", INSERT, 1, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id, data", viewName));
  }

  /** 测试更新带标识符字段场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUpdateWithIdentifierField() {
    createTableWithIdentifierField();

    sql("INSERT INTO %s VALUES (2, 'b')", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (3, 'c'), (2, 'd')", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view(table => '%s', compute_updates => true)",
            catalogName, tableName);

    String viewName = (String) returns.get(0)[0];
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(2, "b", INSERT, 0, snap1.snapshotId()),
            row(2, "b", UPDATE_BEFORE, 1, snap2.snapshotId()),
            row(2, "d", UPDATE_AFTER, 1, snap2.snapshotId()),
            row(3, "c", INSERT, 1, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id, data", viewName));
  }

  /** 测试更新带过滤器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUpdateWithFilter() {
    createTableWithTwoColumns();
    sql("ALTER TABLE %s DROP PARTITION FIELD data", tableName);
    sql("ALTER TABLE %s ADD PARTITION FIELD id", tableName);

    sql("INSERT INTO %s VALUES (1, 'a'), (2, 'b')", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (3, 'c'), (2, 'd')", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view(table => '%s', identifier_columns => array('id'))",
            catalogName, tableName);

    String viewName = (String) returns.get(0)[0];
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", INSERT, 0, snap1.snapshotId()),
            row(2, "b", INSERT, 0, snap1.snapshotId()),
            row(2, "b", UPDATE_BEFORE, 1, snap2.snapshotId()),
            row(2, "d", UPDATE_AFTER, 1, snap2.snapshotId())),
        // the predicate on partition columns will filter out the insert of (3, 'c') at the planning
        // phase
        sql("select * from %s where id != 3 order by _change_ordinal, id, data", viewName));
  }

  /** 测试更新带多个标识符列场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUpdateWithMultipleIdentifierColumns() {
    createTableWithThreeColumns();

    sql("INSERT INTO %s VALUES (1, 'a', 12), (2, 'b', 11)", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    sql("INSERT OVERWRITE %s VALUES (3, 'c', 13), (2, 'd', 11), (2, 'e', 12)", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view("
                + "identifier_columns => array('id','age'),"
                + "table => '%s')",
            catalogName, tableName);

    String viewName = (String) returns.get(0)[0];
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", 12, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, UPDATE_BEFORE, 1, snap2.snapshotId()),
            row(2, "d", 11, UPDATE_AFTER, 1, snap2.snapshotId()),
            row(2, "e", 12, INSERT, 1, snap2.snapshotId()),
            row(3, "c", 13, INSERT, 1, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id, data", viewName));
  }

  /** 测试移除carryovers场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRemoveCarryOvers() {
    createTableWithThreeColumns();

    sql("INSERT INTO %s VALUES (1, 'a', 12), (2, 'b', 11), (2, 'e', 12)", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    // carry-over row (2, 'e', 12)
    sql("INSERT OVERWRITE %s VALUES (3, 'c', 13), (2, 'd', 11), (2, 'e', 12)", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view("
                + "identifier_columns => array('id','age'), "
                + "table => '%s')",
            catalogName, tableName);

    String viewName = (String) returns.get(0)[0];
    // the carry-over rows (2, 'e', 12, 'DELETE', 1), (2, 'e', 12, 'INSERT', 1) are removed
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", 12, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, INSERT, 0, snap1.snapshotId()),
            row(2, "e", 12, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, UPDATE_BEFORE, 1, snap2.snapshotId()),
            row(2, "d", 11, UPDATE_AFTER, 1, snap2.snapshotId()),
            row(3, "c", 13, INSERT, 1, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id, data", viewName));
  }

  /** 测试移除carryovers无updated行场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRemoveCarryOversWithoutUpdatedRows() {
    createTableWithThreeColumns();

    sql("INSERT INTO %s VALUES (1, 'a', 12), (2, 'b', 11), (2, 'e', 12)", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    // carry-over row (2, 'e', 12)
    sql("INSERT OVERWRITE %s VALUES (3, 'c', 13), (2, 'd', 11), (2, 'e', 12)", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql("CALL %s.system.create_changelog_view(table => '%s')", catalogName, tableName);

    String viewName = (String) returns.get(0)[0];

    // the carry-over rows (2, 'e', 12, 'DELETE', 1), (2, 'e', 12, 'INSERT', 1) are removed, even
    // though update-row is not computed
    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", 12, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, INSERT, 0, snap1.snapshotId()),
            row(2, "e", 12, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, DELETE, 1, snap2.snapshotId()),
            row(2, "d", 11, INSERT, 1, snap2.snapshotId()),
            row(3, "c", 13, INSERT, 1, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id, data", viewName));
  }

  /** 测试net变更带移除carryovers场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testNetChangesWithRemoveCarryOvers() {
    // partitioned by id
    createTableWithThreeColumns();

    // insert rows: (1, 'a', 12) (2, 'b', 11) (2, 'e', 12)
    sql("INSERT INTO %s VALUES (1, 'a', 12), (2, 'b', 11), (2, 'e', 12)", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    // delete rows: (2, 'b', 11) (2, 'e', 12)
    // insert rows: (3, 'c', 13) (2, 'd', 11) (2, 'e', 12)
    sql("INSERT OVERWRITE %s VALUES (3, 'c', 13), (2, 'd', 11), (2, 'e', 12)", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    // delete rows: (2, 'd', 11) (2, 'e', 12) (3, 'c', 13)
    // insert rows: (3, 'c', 15) (2, 'e', 12)
    sql("INSERT OVERWRITE %s VALUES (3, 'c', 15), (2, 'e', 12)", tableName);
    table.refresh();
    Snapshot snap3 = table.currentSnapshot();

    // test with all snapshots
    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view(table => '%s', net_changes => true)",
            catalogName, tableName);

    String viewName = (String) returns.get(0)[0];

    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", 12, INSERT, 0, snap1.snapshotId()),
            row(3, "c", 15, INSERT, 2, snap3.snapshotId()),
            row(2, "e", 12, INSERT, 2, snap3.snapshotId())),
        sql("select * from %s order by _change_ordinal, data", viewName));

    // test with snap2 and snap3
    sql(
        "CALL %s.system.create_changelog_view(table => '%s', "
            + "options => map('start-snapshot-id','%s'), "
            + "net_changes => true)",
        catalogName, tableName, snap1.snapshotId());

    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(2, "b", 11, DELETE, 0, snap2.snapshotId()),
            row(3, "c", 15, INSERT, 1, snap3.snapshotId())),
        sql("select * from %s order by _change_ordinal, data", viewName));
  }

  /** 测试net变更带compute更新场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testNetChangesWithComputeUpdates() {
    createTableWithTwoColumns();
    assertThrows(
        "Should fail because net_changes is not supported with computing updates",
        IllegalArgumentException.class,
        () ->
            sql(
                "CALL %s.system.create_changelog_view(table => '%s', identifier_columns => array('id'), net_changes => true)",
                catalogName, tableName));
  }

  /** 测试非移除carryovers场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testNotRemoveCarryOvers() {
    createTableWithThreeColumns();

    sql("INSERT INTO %s VALUES (1, 'a', 12), (2, 'b', 11), (2, 'e', 12)", tableName);
    Table table = validationCatalog.loadTable(tableIdent);
    Snapshot snap1 = table.currentSnapshot();

    // carry-over row (2, 'e', 12)
    sql("INSERT OVERWRITE %s VALUES (3, 'c', 13), (2, 'd', 11), (2, 'e', 12)", tableName);
    table.refresh();
    Snapshot snap2 = table.currentSnapshot();

    List<Object[]> returns =
        sql(
            "CALL %s.system.create_changelog_view("
                + "remove_carryovers => false,"
                + "table => '%s')",
            catalogName, tableName);

    String viewName = (String) returns.get(0)[0];

    assertEquals(
        "Rows should match",
        ImmutableList.of(
            row(1, "a", 12, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, INSERT, 0, snap1.snapshotId()),
            row(2, "e", 12, INSERT, 0, snap1.snapshotId()),
            row(2, "b", 11, DELETE, 1, snap2.snapshotId()),
            row(2, "d", 11, INSERT, 1, snap2.snapshotId()),
            // the following two rows are carry-over rows
            row(2, "e", 12, DELETE, 1, snap2.snapshotId()),
            row(2, "e", 12, INSERT, 1, snap2.snapshotId()),
            row(3, "c", 13, INSERT, 1, snap2.snapshotId())),
        sql("select * from %s order by _change_ordinal, id, data, _change_type", viewName));
  }
}
