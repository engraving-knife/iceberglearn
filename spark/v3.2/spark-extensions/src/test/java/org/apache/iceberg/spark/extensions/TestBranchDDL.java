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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotRef;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.spark.SparkCatalogConfig;
import org.apache.iceberg.spark.source.SimpleRecord;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.catalyst.parser.extensions.IcebergParseException;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestBranchDDL 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 分支DDL 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestBranchDDL extends SparkExtensionsTestBase {
  private static final String[] TIME_UNITS = {"DAYS", "HOURS", "MINUTES"};

  /** 前。 */
  @Before
  public void before() {
    sql("CREATE TABLE %s (id INT, data STRING) USING iceberg", tableName);
  }

  /** 移除表。 */
  @After
  public void removeTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 参数。 */
  @Parameterized.Parameters(name = "catalogName = {0}, implementation = {1}, config = {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        SparkCatalogConfig.SPARK.catalogName(),
        SparkCatalogConfig.SPARK.implementation(),
        SparkCatalogConfig.SPARK.properties()
      }
    };
  }

  /** 测试分支DDL。 */
  public TestBranchDDL(String catalog, String implementation, Map<String, String> properties) {
    super(catalog, implementation, properties);
  }

  /** 测试创建分支场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranch() throws NoSuchTableException {
    Table table = insertRows();
    long snapshotId = table.currentSnapshot().snapshotId();

    Integer minSnapshotsToKeep = 2;
    long maxSnapshotAge = 2L;
    long maxRefAge = 10L;
    for (String timeUnit : TIME_UNITS) {
      String branchName = "b1" + timeUnit;
      sql(
          "ALTER TABLE %s CREATE BRANCH %s AS OF VERSION %d RETAIN %d %s WITH SNAPSHOT RETENTION %d SNAPSHOTS %d %s",
          tableName,
          branchName,
          snapshotId,
          maxRefAge,
          timeUnit,
          minSnapshotsToKeep,
          maxSnapshotAge,
          timeUnit);
      table.refresh();
      SnapshotRef ref = table.refs().get(branchName);
      Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());
      Assert.assertEquals(minSnapshotsToKeep, ref.minSnapshotsToKeep());
      Assert.assertEquals(
          TimeUnit.valueOf(timeUnit).toMillis(maxSnapshotAge), ref.maxSnapshotAgeMs().longValue());
      Assert.assertEquals(
          TimeUnit.valueOf(timeUnit).toMillis(maxRefAge), ref.maxRefAgeMs().longValue());
    }
  }

  /** 测试创建分支上空表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranchOnEmptyTable() {
    String branchName = "b1";
    sql("ALTER TABLE %s CREATE BRANCH %s", tableName, "b1");
    Table table = validationCatalog.loadTable(tableIdent);

    SnapshotRef mainRef = table.refs().get(SnapshotRef.MAIN_BRANCH);
    Assertions.assertThat(mainRef).isNull();

    SnapshotRef ref = table.refs().get(branchName);
    Assertions.assertThat(ref).isNotNull();
    Assertions.assertThat(ref.minSnapshotsToKeep()).isNull();
    Assertions.assertThat(ref.maxSnapshotAgeMs()).isNull();
    Assertions.assertThat(ref.maxRefAgeMs()).isNull();

    Snapshot snapshot = table.snapshot(ref.snapshotId());
    Assertions.assertThat(snapshot.parentId()).isNull();
    Assertions.assertThat(snapshot.addedDataFiles(table.io())).isEmpty();
    Assertions.assertThat(snapshot.removedDataFiles(table.io())).isEmpty();
    Assertions.assertThat(snapshot.addedDeleteFiles(table.io())).isEmpty();
    Assertions.assertThat(snapshot.removedDeleteFiles(table.io())).isEmpty();
  }

  /** 测试创建分支use默认配置场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranchUseDefaultConfig() throws NoSuchTableException {
    Table table = insertRows();
    String branchName = "b1";
    sql("ALTER TABLE %s CREATE BRANCH %s", tableName, branchName);
    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());
    Assert.assertNull(ref.minSnapshotsToKeep());
    Assert.assertNull(ref.maxSnapshotAgeMs());
    Assert.assertNull(ref.maxRefAgeMs());
  }

  /** 测试创建分支use自定义最小快照到keep场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranchUseCustomMinSnapshotsToKeep() throws NoSuchTableException {
    Integer minSnapshotsToKeep = 2;
    Table table = insertRows();
    String branchName = "b1";
    sql(
        "ALTER TABLE %s CREATE BRANCH %s WITH SNAPSHOT RETENTION %d SNAPSHOTS",
        tableName, branchName, minSnapshotsToKeep);
    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());
    Assert.assertEquals(minSnapshotsToKeep, ref.minSnapshotsToKeep());
    Assert.assertNull(ref.maxSnapshotAgeMs());
    Assert.assertNull(ref.maxRefAgeMs());
  }

  /** 测试创建分支use自定义最大快照age场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranchUseCustomMaxSnapshotAge() throws NoSuchTableException {
    long maxSnapshotAge = 2L;
    Table table = insertRows();
    String branchName = "b1";
    sql(
        "ALTER TABLE %s CREATE BRANCH %s WITH SNAPSHOT RETENTION %d DAYS",
        tableName, branchName, maxSnapshotAge);
    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());
    Assert.assertNull(ref.minSnapshotsToKeep());
    Assert.assertEquals(TimeUnit.DAYS.toMillis(maxSnapshotAge), ref.maxSnapshotAgeMs().longValue());
    Assert.assertNull(ref.maxRefAgeMs());
  }

  /** 测试创建分支if非存在场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranchIfNotExists() throws NoSuchTableException {
    long maxSnapshotAge = 2L;
    Table table = insertRows();
    String branchName = "b1";
    sql(
        "ALTER TABLE %s CREATE BRANCH %s WITH SNAPSHOT RETENTION %d DAYS",
        tableName, branchName, maxSnapshotAge);

    AssertHelpers.assertThrows(
        "Cannot create an existing branch",
        IllegalArgumentException.class,
        "Ref b1 already exists",
        () -> sql("ALTER TABLE %s CREATE BRANCH %s", tableName, branchName));

    sql("ALTER TABLE %s CREATE BRANCH IF NOT EXISTS %s", tableName, branchName);

    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());
    Assert.assertNull(ref.minSnapshotsToKeep());
    Assert.assertEquals(TimeUnit.DAYS.toMillis(maxSnapshotAge), ref.maxSnapshotAgeMs().longValue());
    Assert.assertNull(ref.maxRefAgeMs());
  }

  /** 测试创建分支use自定义最小快照到keep与最大快照age场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranchUseCustomMinSnapshotsToKeepAndMaxSnapshotAge()
      throws NoSuchTableException {
    Integer minSnapshotsToKeep = 2;
    long maxSnapshotAge = 2L;
    Table table = insertRows();
    String branchName = "b1";

    sql(
        "ALTER TABLE %s CREATE BRANCH %s WITH SNAPSHOT RETENTION %d SNAPSHOTS %d DAYS",
        tableName, branchName, minSnapshotsToKeep, maxSnapshotAge);
    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());
    Assert.assertEquals(minSnapshotsToKeep, ref.minSnapshotsToKeep());
    Assert.assertEquals(TimeUnit.DAYS.toMillis(maxSnapshotAge), ref.maxSnapshotAgeMs().longValue());
    Assert.assertNull(ref.maxRefAgeMs());

    AssertHelpers.assertThrows(
        "Illegal statement",
        IcebergParseException.class,
        "no viable alternative at input 'WITH SNAPSHOT RETENTION'",
        () ->
            sql("ALTER TABLE %s CREATE BRANCH %s WITH SNAPSHOT RETENTION", tableName, branchName));
  }

  /** 测试创建分支use自定义最大refage场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateBranchUseCustomMaxRefAge() throws NoSuchTableException {
    long maxRefAge = 10L;
    Table table = insertRows();
    String branchName = "b1";
    sql("ALTER TABLE %s CREATE BRANCH %s RETAIN %d DAYS", tableName, branchName, maxRefAge);
    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());
    Assert.assertNull(ref.minSnapshotsToKeep());
    Assert.assertNull(ref.maxSnapshotAgeMs());
    Assert.assertEquals(TimeUnit.DAYS.toMillis(maxRefAge), ref.maxRefAgeMs().longValue());

    AssertHelpers.assertThrows(
        "Illegal statement",
        IcebergParseException.class,
        "mismatched input",
        () -> sql("ALTER TABLE %s CREATE BRANCH %s RETAIN", tableName, branchName));

    AssertHelpers.assertThrows(
        "Illegal statement",
        IcebergParseException.class,
        "mismatched input",
        () -> sql("ALTER TABLE %s CREATE BRANCH %s RETAIN %s DAYS", tableName, branchName, "abc"));

    AssertHelpers.assertThrows(
        "Illegal statement",
        IcebergParseException.class,
        "mismatched input 'SECONDS' expecting {'DAYS', 'HOURS', 'MINUTES'}",
        () ->
            sql(
                "ALTER TABLE %s CREATE BRANCH %s RETAIN %d SECONDS",
                tableName, branchName, maxRefAge));
  }

  /** 测试删除分支场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropBranch() throws NoSuchTableException {
    insertRows();

    Table table = validationCatalog.loadTable(tableIdent);
    String branchName = "b1";
    table.manageSnapshots().createBranch(branchName, table.currentSnapshot().snapshotId()).commit();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertEquals(table.currentSnapshot().snapshotId(), ref.snapshotId());

    sql("ALTER TABLE %s DROP BRANCH %s", tableName, branchName);
    table.refresh();

    ref = table.refs().get(branchName);
    Assert.assertNull(ref);
  }

  /** 测试删除分支does非存在场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropBranchDoesNotExist() {
    AssertHelpers.assertThrows(
        "Cannot perform drop branch on branch which does not exist",
        IllegalArgumentException.class,
        "Branch does not exist: nonExistingBranch",
        () -> sql("ALTER TABLE %s DROP BRANCH %s", tableName, "nonExistingBranch"));
  }

  /** 测试删除分支fails用于标签场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropBranchFailsForTag() throws NoSuchTableException {
    String tagName = "b1";
    Table table = insertRows();
    table.manageSnapshots().createTag(tagName, table.currentSnapshot().snapshotId()).commit();

    AssertHelpers.assertThrows(
        "Cannot perform drop branch on tag",
        IllegalArgumentException.class,
        "Ref b1 is a tag not a branch",
        () -> sql("ALTER TABLE %s DROP BRANCH %s", tableName, tagName));
  }

  /** 测试删除分支不存在的conformingname场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropBranchNonConformingName() {
    AssertHelpers.assertThrows(
        "Non-conforming branch name",
        IcebergParseException.class,
        "mismatched input '123'",
        () -> sql("ALTER TABLE %s DROP BRANCH %s", tableName, "123"));
  }

  /** 测试删除main分支fails场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropMainBranchFails() {
    AssertHelpers.assertThrows(
        "Cannot drop the main branch",
        IllegalArgumentException.class,
        "Cannot remove main branch",
        () -> sql("ALTER TABLE %s DROP BRANCH main", tableName));
  }

  /** 测试删除分支if存在场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropBranchIfExists() {
    String branchName = "nonExistingBranch";
    Table table = validationCatalog.loadTable(tableIdent);
    Assert.assertNull(table.refs().get(branchName));

    sql("ALTER TABLE %s DROP BRANCH IF EXISTS %s", tableName, branchName);
    table.refresh();

    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertNull(ref);
  }

  /** 测试替换分支fails用于标签场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testReplaceBranchFailsForTag() throws NoSuchTableException {
    String tagName = "tag1";

    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();
    Table table = validationCatalog.loadTable(tableIdent);
    long first = table.currentSnapshot().snapshotId();
    table.manageSnapshots().createTag(tagName, first).commit();
    df.writeTo(tableName).append();
    long second = table.currentSnapshot().snapshotId();

    AssertHelpers.assertThrows(
        "Cannot perform replace branch on tags",
        IllegalArgumentException.class,
        "Ref tag1 is a tag not a branch",
        () -> sql("ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d", tableName, tagName, second));
  }

  /** 测试替换分支场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testReplaceBranch() throws NoSuchTableException {
    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    long first = table.currentSnapshot().snapshotId();
    String branchName = "b1";
    long expectedMaxRefAgeMs = 1000;
    int expectedMinSnapshotsToKeep = 2;
    long expectedMaxSnapshotAgeMs = 1000;
    table
        .manageSnapshots()
        .createBranch(branchName, first)
        .setMaxRefAgeMs(branchName, expectedMaxRefAgeMs)
        .setMinSnapshotsToKeep(branchName, expectedMinSnapshotsToKeep)
        .setMaxSnapshotAgeMs(branchName, expectedMaxSnapshotAgeMs)
        .commit();

    df.writeTo(tableName).append();
    long second = table.currentSnapshot().snapshotId();

    sql("ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d", tableName, branchName, second);

    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertNotNull(ref);
    Assert.assertEquals(second, ref.snapshotId());
    Assert.assertEquals(expectedMinSnapshotsToKeep, ref.minSnapshotsToKeep().intValue());
    Assert.assertEquals(expectedMaxSnapshotAgeMs, ref.maxSnapshotAgeMs().longValue());
    Assert.assertEquals(expectedMaxRefAgeMs, ref.maxRefAgeMs().longValue());
  }

  /** 测试替换分支does非存在场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testReplaceBranchDoesNotExist() throws NoSuchTableException {
    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();
    Table table = validationCatalog.loadTable(tableIdent);

    AssertHelpers.assertThrows(
        "Cannot perform replace branch on branch which does not exist",
        IllegalArgumentException.class,
        "Branch does not exist",
        () ->
            sql(
                "ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d",
                tableName, "someBranch", table.currentSnapshot().snapshotId()));
  }

  /** 测试替换分支带retain场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testReplaceBranchWithRetain() throws NoSuchTableException {
    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    long first = table.currentSnapshot().snapshotId();
    String branchName = "b1";
    table.manageSnapshots().createBranch(branchName, first).commit();
    SnapshotRef b1 = table.refs().get(branchName);
    Integer minSnapshotsToKeep = b1.minSnapshotsToKeep();
    Long maxSnapshotAgeMs = b1.maxSnapshotAgeMs();
    df.writeTo(tableName).append();
    long second = table.currentSnapshot().snapshotId();

    long maxRefAge = 10;
    for (String timeUnit : TIME_UNITS) {
      sql(
          "ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d RETAIN %d %s",
          tableName, branchName, second, maxRefAge, timeUnit);

      table.refresh();
      SnapshotRef ref = table.refs().get(branchName);
      Assert.assertNotNull(ref);
      Assert.assertEquals(second, ref.snapshotId());
      Assert.assertEquals(minSnapshotsToKeep, ref.minSnapshotsToKeep());
      Assert.assertEquals(maxSnapshotAgeMs, ref.maxSnapshotAgeMs());
      Assert.assertEquals(
          TimeUnit.valueOf(timeUnit).toMillis(maxRefAge), ref.maxRefAgeMs().longValue());
    }
  }

  /** 测试替换分支带快照retention场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testReplaceBranchWithSnapshotRetention() throws NoSuchTableException {
    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();
    String branchName = "b1";
    Table table = validationCatalog.loadTable(tableIdent);
    long first = table.currentSnapshot().snapshotId();
    table.manageSnapshots().createBranch(branchName, first).commit();
    df.writeTo(tableName).append();
    long second = table.currentSnapshot().snapshotId();

    Integer minSnapshotsToKeep = 2;
    long maxSnapshotAge = 2;
    Long maxRefAgeMs = table.refs().get(branchName).maxRefAgeMs();
    for (String timeUnit : TIME_UNITS) {
      sql(
          "ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d WITH SNAPSHOT RETENTION %d SNAPSHOTS %d %s",
          tableName, branchName, second, minSnapshotsToKeep, maxSnapshotAge, timeUnit);

      table.refresh();
      SnapshotRef ref = table.refs().get(branchName);
      Assert.assertNotNull(ref);
      Assert.assertEquals(second, ref.snapshotId());
      Assert.assertEquals(minSnapshotsToKeep, ref.minSnapshotsToKeep());
      Assert.assertEquals(
          TimeUnit.valueOf(timeUnit).toMillis(maxSnapshotAge), ref.maxSnapshotAgeMs().longValue());
      Assert.assertEquals(maxRefAgeMs, ref.maxRefAgeMs());
    }
  }

  /** 测试替换分支带retain与快照retention场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testReplaceBranchWithRetainAndSnapshotRetention() throws NoSuchTableException {
    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    long first = table.currentSnapshot().snapshotId();
    String branchName = "b1";
    table.manageSnapshots().createBranch(branchName, first).commit();
    df.writeTo(tableName).append();
    long second = table.currentSnapshot().snapshotId();

    Integer minSnapshotsToKeep = 2;
    long maxSnapshotAge = 2;
    long maxRefAge = 10;
    for (String timeUnit : TIME_UNITS) {
      sql(
          "ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d RETAIN %d %s WITH SNAPSHOT RETENTION %d SNAPSHOTS %d %s",
          tableName,
          branchName,
          second,
          maxRefAge,
          timeUnit,
          minSnapshotsToKeep,
          maxSnapshotAge,
          timeUnit);

      table.refresh();
      SnapshotRef ref = table.refs().get(branchName);
      Assert.assertNotNull(ref);
      Assert.assertEquals(second, ref.snapshotId());
      Assert.assertEquals(minSnapshotsToKeep, ref.minSnapshotsToKeep());
      Assert.assertEquals(
          TimeUnit.valueOf(timeUnit).toMillis(maxSnapshotAge), ref.maxSnapshotAgeMs().longValue());
      Assert.assertEquals(
          TimeUnit.valueOf(timeUnit).toMillis(maxRefAge), ref.maxRefAgeMs().longValue());
    }
  }

  /** 测试创建或替换场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateOrReplace() throws NoSuchTableException {
    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    long first = table.currentSnapshot().snapshotId();
    String branchName = "b1";
    df.writeTo(tableName).append();
    long second = table.currentSnapshot().snapshotId();
    table.manageSnapshots().createBranch(branchName, second).commit();

    sql(
        "ALTER TABLE %s CREATE OR REPLACE BRANCH %s AS OF VERSION %d",
        tableName, branchName, first);

    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    Assert.assertNotNull(ref);
    Assert.assertEquals(first, ref.snapshotId());
  }

  /** 创建或替换。 */
  @Test
  public void createOrReplace() throws NoSuchTableException {
    Table table = insertRows();
    long first = table.currentSnapshot().snapshotId();
    String branchName = "b1";
    insertRows();
    long second = table.currentSnapshot().snapshotId();
    table.manageSnapshots().createBranch(branchName, second).commit();

    sql(
        "ALTER TABLE %s CREATE OR REPLACE BRANCH %s AS OF VERSION %d",
        tableName, branchName, first);
    table.refresh();
    assertThat(table.refs().get(branchName).snapshotId()).isEqualTo(second);
  }

  /** 测试创建或替换分支上空表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testCreateOrReplaceBranchOnEmptyTable() {
    String branchName = "b1";
    sql("ALTER TABLE %s CREATE OR REPLACE BRANCH %s", tableName, "b1");
    Table table = validationCatalog.loadTable(tableIdent);

    SnapshotRef mainRef = table.refs().get(SnapshotRef.MAIN_BRANCH);
    Assertions.assertThat(mainRef).isNull();

    SnapshotRef ref = table.refs().get(branchName);
    Assertions.assertThat(ref).isNotNull();
    Assertions.assertThat(ref.minSnapshotsToKeep()).isNull();
    Assertions.assertThat(ref.maxSnapshotAgeMs()).isNull();
    Assertions.assertThat(ref.maxRefAgeMs()).isNull();

    Snapshot snapshot = table.snapshot(ref.snapshotId());
    Assertions.assertThat(snapshot.parentId()).isNull();
    Assertions.assertThat(snapshot.addedDataFiles(table.io())).isEmpty();
    Assertions.assertThat(snapshot.removedDataFiles(table.io())).isEmpty();
    Assertions.assertThat(snapshot.addedDeleteFiles(table.io())).isEmpty();
    Assertions.assertThat(snapshot.removedDeleteFiles(table.io())).isEmpty();
  }

  /** 创建或替换带不存在的已存在的分支。 */
  @Test
  public void createOrReplaceWithNonExistingBranch() throws NoSuchTableException {
    Table table = insertRows();
    String branchName = "b1";
    insertRows();
    long snapshotId = table.currentSnapshot().snapshotId();

    sql(
        "ALTER TABLE %s CREATE OR REPLACE BRANCH %s AS OF VERSION %d",
        tableName, branchName, snapshotId);
    table.refresh();
    assertThat(table.refs().get(branchName).snapshotId()).isEqualTo(snapshotId);
  }

  /** 替换分支。 */
  @Test
  public void replaceBranch() throws NoSuchTableException {
    Table table = insertRows();
    long first = table.currentSnapshot().snapshotId();
    String branchName = "b1";
    long expectedMaxRefAgeMs = 1000;
    table
        .manageSnapshots()
        .createBranch(branchName, first)
        .setMaxRefAgeMs(branchName, expectedMaxRefAgeMs)
        .commit();

    insertRows();
    long second = table.currentSnapshot().snapshotId();

    sql("ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d", tableName, branchName, second);
    table.refresh();
    SnapshotRef ref = table.refs().get(branchName);
    assertThat(ref.snapshotId()).isEqualTo(second);
    assertThat(ref.maxRefAgeMs()).isEqualTo(expectedMaxRefAgeMs);
  }

  /** 替换分支does非存在。 */
  @Test
  public void replaceBranchDoesNotExist() throws NoSuchTableException {
    Table table = insertRows();

    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "ALTER TABLE %s REPLACE BRANCH %s AS OF VERSION %d",
                    tableName, "someBranch", table.currentSnapshot().snapshotId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Branch does not exist: someBranch");
  }

  /** 插入行。 */
  private Table insertRows() throws NoSuchTableException {
    List<SimpleRecord> records =
        ImmutableList.of(new SimpleRecord(1, "a"), new SimpleRecord(2, "b"));
    Dataset<Row> df = spark.createDataFrame(records, SimpleRecord.class);
    df.writeTo(tableName).append();
    return validationCatalog.loadTable(tableIdent);
  }
}
