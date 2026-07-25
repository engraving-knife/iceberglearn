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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.NamedReference;
import org.apache.iceberg.expressions.Zorder;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.ExtendedParser;
import org.apache.iceberg.spark.SparkCatalogConfig;
import org.apache.iceberg.spark.SparkTableCache;
import org.apache.iceberg.spark.SystemFunctionPushDownHelper;
import org.apache.iceberg.spark.source.ThreeColumnRecord;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchProcedureException;
import org.apache.spark.sql.internal.SQLConf;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 文件级说明：测试 TestRewriteDataFilesProcedure 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 重写数据文件存储过程 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestRewriteDataFilesProcedure extends SparkExtensionsTestBase {

  private static final String QUOTED_SPECIAL_CHARS_TABLE_NAME = "`table:with.special:chars`";

  /** 测试重写数据文件存储过程。 */
  public TestRewriteDataFilesProcedure(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 初始化Spark。 */
  @BeforeClass
  public static void setupSpark() {
    // disable AQE as tests assume that writes generate a particular number of files
    spark.conf().set(SQLConf.ADAPTIVE_EXECUTION_ENABLED().key(), "false");
  }

  /** 移除表。 */
  @After
  public void removeTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
    sql("DROP TABLE IF EXISTS %s", tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));
  }

  /** 测试z顺序排序表达式场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testZOrderSortExpression() {
    List<ExtendedParser.RawOrderField> order =
        ExtendedParser.parseSortOrder(spark, "c1, zorder(c2, c3)");
    Assert.assertEquals("Should parse 2 order fields", 2, order.size());
    Assert.assertEquals(
        "First field should be a ref", "c1", ((NamedReference<?>) order.get(0).term()).name());
    Assert.assertTrue("Second field should be zorder", order.get(1).term() instanceof Zorder);
  }

  /** 测试重写数据文件在空表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesInEmptyTable() {
    createTable();
    List<Object[]> output = sql("CALL %s.system.rewrite_data_files('%s')", catalogName, tableIdent);
    assertEquals("Procedure output must match", ImmutableList.of(row(0, 0, 0L, 0)), output);
  }

  /** 测试重写数据文件上分区表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesOnPartitionTable() {
    createPartitionTable();
    // create 5 files for each partition (c2 = 'foo' and c2 = 'bar')
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    List<Object[]> output =
        sql("CALL %s.system.rewrite_data_files(table => '%s')", catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 10 data files and add 2 data files (one per partition) ",
        row(10, 2),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件上不存在的分区表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesOnNonPartitionTable() {
    createTable();
    // create 10 files under non-partitioned table
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    List<Object[]> output =
        sql("CALL %s.system.rewrite_data_files(table => '%s')", catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 10 data files and add 1 data files",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带选项场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithOptions() {
    createTable();
    // create 10 files under non-partitioned table
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    // set the min-input-files = 12, instead of default 5 to skip compacting the files.
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s', options => map('min-input-files','12'))",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 0 data files and add 0 data files",
        ImmutableList.of(row(0, 0, 0L, 0)),
        output);

    List<Object[]> actualRecords = currentData();
    assertEquals("Data should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带排序strategy场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithSortStrategy() {
    createTable();
    // create 10 files under non-partitioned table
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    // set sort_order = c1 DESC LAST
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s', "
                + "strategy => 'sort', sort_order => 'c1 DESC NULLS LAST')",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 10 data files and add 1 data files",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带排序strategy与多个shuffle分区每文件场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithSortStrategyAndMultipleShufflePartitionsPerFile() {
    createTable();
    insertData(10 /* file count */);

    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files("
                + " table => '%s', "
                + " strategy => 'sort', "
                + " sort_order => 'c1', "
                + " options => map('shuffle-partitions-per-file', '2'))",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 10 data files and add 1 data files",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));

    // as there is only one small output file, validate the query ordering (it will not change)
    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null));
    assertEquals("Should have expected rows", expectedRows, sql("SELECT * FROM %s", tableName));
  }

  /** 测试重写数据文件带z顺序场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithZOrder() {
    createTable();
    // create 10 files under non-partitioned table
    insertData(10);

    // set z_order = c1,c2
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s', "
                + "strategy => 'sort', sort_order => 'zorder(c1,c2)')",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 10 data files and add 1 data files",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    // Due to Z_order, the data written will be in the below order.
    // As there is only one small output file, we can validate the query ordering (as it will not
    // change).
    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null));
    assertEquals("Should have expected rows", expectedRows, sql("SELECT * FROM %s", tableName));
  }

  /** 测试重写数据文件带z顺序与多个shuffle分区每文件场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithZOrderAndMultipleShufflePartitionsPerFile() {
    createTable();
    insertData(10 /* file count */);

    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files("
                + " table => '%s', "
                + "strategy => 'sort', "
                + " sort_order => 'zorder(c1, c2)', "
                + " options => map('shuffle-partitions-per-file', '2'))",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 10 data files and add 1 data files",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));

    // due to z-ordering, the data will be written in the below order
    // as there is only one small output file, validate the query ordering (it will not change)
    ImmutableList<Object[]> expectedRows =
        ImmutableList.of(
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(2, "bar", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null),
            row(1, "foo", null));
    assertEquals("Should have expected rows", expectedRows, sql("SELECT * FROM %s", tableName));
  }

  /** 测试重写数据文件带过滤器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithFilter() {
    createTable();
    // create 10 files under non-partitioned table
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    // select only 5 files for compaction (files that may have c1 = 1)
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s',"
                + " where => 'c1 = 1 and c2 is not null')",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 5 data files (containing c1 = 1) and add 1 data files",
        row(5, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带deterministictrue过滤器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithDeterministicTrueFilter() {
    createTable();
    // create 10 files under non-partitioned table
    insertData(10);
    List<Object[]> expectedRecords = currentData();
    // select all 10 files for compaction
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s', where => '1=1')",
            catalogName, tableIdent);
    assertEquals(
        "Action should rewrite 10 data files and add 1 data files",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));
    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带deterministicfalse过滤器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithDeterministicFalseFilter() {
    createTable();
    // create 10 files under non-partitioned table
    insertData(10);
    List<Object[]> expectedRecords = currentData();
    // select no files for compaction
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s', where => '0=1')",
            catalogName, tableIdent);
    assertEquals(
        "Action should rewrite 0 data files and add 0 data files",
        row(0, 0),
        Arrays.copyOf(output.get(0), 2));
    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带过滤器上分区表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithFilterOnPartitionTable() {
    createPartitionTable();
    // create 5 files for each partition (c2 = 'foo' and c2 = 'bar')
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    // select only 5 files for compaction (files in the partition c2 = 'bar')
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c2 = \"bar\"')",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 5 data files from single matching partition"
            + "(containing c2 = bar) and add 1 data files",
        row(5, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带过滤器上桶表达式场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithFilterOnOnBucketExpression() {
    // currently spark session catalog only resolve to v1 functions instead of desired v2 functions
    // https://github.com/apache/spark/blob/branch-3.4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L2070-L2083
    Assume.assumeFalse(catalogName.equals(SparkCatalogConfig.SPARK.catalogName()));
    createBucketPartitionTable();
    // create 5 files for each partition (c2 = 'foo' and c2 = 'bar')
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    // select only 5 files for compaction (files in the partition c2 = 'bar')
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s',"
                + " where => '%s.system.bucket(2, c2) = 0')",
            catalogName, tableIdent, catalogName);

    assertEquals(
        "Action should rewrite 5 data files from single matching partition"
            + "(containing bucket(c2) = 0) and add 1 data files",
        row(5, 1),
        row(output.get(0)[0], output.get(0)[1]));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带在过滤器上分区表场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithInFilterOnPartitionTable() {
    createPartitionTable();
    // create 5 files for each partition (c2 = 'foo' and c2 = 'bar')
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    // select only 5 files for compaction (files in the partition c2 in ('bar'))
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c2 in (\"bar\")')",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 5 data files from single matching partition"
            + "(containing c2 = bar) and add 1 data files",
        row(5, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写数据文件带所有possible过滤器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithAllPossibleFilters() {
    createPartitionTable();
    // create 5 files for each partition (c2 = 'foo' and c2 = 'bar')
    insertData(10);

    // Pass the literal value which is not present in the data files.
    // So that parsing can be tested on a same dataset without actually compacting the files.

    // EqualTo
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 = 3')",
        catalogName, tableIdent);
    // GreaterThan
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 > 3')",
        catalogName, tableIdent);
    // GreaterThanOrEqual
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 >= 3')",
        catalogName, tableIdent);
    // LessThan
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 < 0')",
        catalogName, tableIdent);
    // LessThanOrEqual
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 <= 0')",
        catalogName, tableIdent);
    // In
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 in (3,4,5)')",
        catalogName, tableIdent);
    // IsNull
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 is null')",
        catalogName, tableIdent);
    // IsNotNull
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c3 is not null')",
        catalogName, tableIdent);
    // And
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 = 3 and c2 = \"bar\"')",
        catalogName, tableIdent);
    // Or
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 = 3 or c1 = 5')",
        catalogName, tableIdent);
    // Not
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c1 not in (1,2)')",
        catalogName, tableIdent);
    // StringStartsWith
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s'," + " where => 'c2 like \"%s\"')",
        catalogName, tableIdent, "car%");
    // TODO: Enable when org.apache.iceberg.spark.SparkFilters have implementations for
    // StringEndsWith & StringContains
    // StringEndsWith
    // sql("CALL %s.system.rewrite_data_files(table => '%s'," +
    //     " where => 'c2 like \"%s\"')", catalogName, tableIdent, "%car");
    // StringContains
    // sql("CALL %s.system.rewrite_data_files(table => '%s'," +
    //     " where => 'c2 like \"%s\"')", catalogName, tableIdent, "%car%");
  }

  /** 测试重写数据文件带possiblev2过滤器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithPossibleV2Filters() {
    // currently spark session catalog only resolve to v1 functions instead of desired v2 functions
    // https://github.com/apache/spark/blob/branch-3.4/sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/analysis/Analyzer.scala#L2070-L2083
    Assume.assumeFalse(catalogName.equals(SparkCatalogConfig.SPARK.catalogName()));

    SystemFunctionPushDownHelper.createPartitionedTable(spark, tableName, "id");
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s',"
            + " where => '%s.system.bucket(2, data) >= 0')",
        catalogName, tableIdent, catalogName);
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s',"
            + " where => '%s.system.truncate(4, id) >= 1')",
        catalogName, tableIdent, catalogName);
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s',"
            + " where => '%s.system.years(ts) >= 1')",
        catalogName, tableIdent, catalogName);
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s',"
            + " where => '%s.system.months(ts) >= 1')",
        catalogName, tableIdent, catalogName);
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s',"
            + " where => '%s.system.days(ts) >= date(\"2023-01-01\")')",
        catalogName, tableIdent, catalogName);
    sql(
        "CALL %s.system.rewrite_data_files(table => '%s',"
            + " where => '%s.system.hours(ts) >= 1')",
        catalogName, tableIdent, catalogName);
  }

  /** 测试重写数据文件带invalidinputs场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteDataFilesWithInvalidInputs() {
    createTable();
    // create 2 files under non-partitioned table
    insertData(2);

    // Test for invalid strategy
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', options => map('min-input-files','2'), "
                        + "strategy => 'temp')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unsupported strategy: temp. Only binpack or sort is supported");

    // Test for sort_order with binpack strategy
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', strategy => 'binpack', "
                        + "sort_order => 'c1 ASC NULLS FIRST')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Must use only one rewriter type (bin-pack, sort, zorder)");

    // Test for sort strategy without any (default/user defined) sort_order
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', strategy => 'sort')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot sort data without a valid sort order");

    // Test for sort_order with invalid null order
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', strategy => 'sort', "
                        + "sort_order => 'c1 ASC none')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unable to parse sortOrder: c1 ASC none");

    // Test for sort_order with invalid sort direction
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', strategy => 'sort', "
                        + "sort_order => 'c1 none NULLS FIRST')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unable to parse sortOrder: c1 none NULLS FIRST");

    // Test for sort_order with invalid column name
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', strategy => 'sort', "
                        + "sort_order => 'col1 DESC NULLS FIRST')",
                    catalogName, tableIdent))
        .isInstanceOf(ValidationException.class)
        .hasMessageStartingWith("Cannot find field 'col1' in struct:");

    // Test with invalid filter column col1
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', " + "where => 'col1 = 3')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse predicates in where option: col1 = 3");

    // Test for z_order with invalid column name
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', strategy => 'sort', "
                        + "sort_order => 'zorder(col1)')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Cannot find column 'col1' in table schema (case sensitive = false): "
                + "struct<1: c1: optional int, 2: c2: optional string, 3: c3: optional string>");

    // Test for z_order with sort_order
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', strategy => 'sort', "
                        + "sort_order => 'c1,zorder(c2,c3)')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Cannot mix identity sort columns and a Zorder sort expression:" + " c1,zorder(c2,c3)");
  }

  /** 测试invalid场景用于重写数据文件场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testInvalidCasesForRewriteDataFiles() {
    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.system.rewrite_data_files('n', table => 't')", catalogName))
        .isInstanceOf(AnalysisException.class)
        .hasMessage("Named and positional arguments cannot be mixed");

    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.custom.rewrite_data_files('n', 't')", catalogName))
        .isInstanceOf(NoSuchProcedureException.class)
        .hasMessage("Procedure custom.rewrite_data_files not found");

    Assertions.assertThatThrownBy(() -> sql("CALL %s.system.rewrite_data_files()", catalogName))
        .isInstanceOf(AnalysisException.class)
        .hasMessage("Missing required parameters: [table]");

    Assertions.assertThatThrownBy(
            () -> sql("CALL %s.system.rewrite_data_files(table => 't', table => 't')", catalogName))
        .isInstanceOf(AnalysisException.class)
        .hasMessageEndingWith("Duplicate procedure argument: table");

    Assertions.assertThatThrownBy(() -> sql("CALL %s.system.rewrite_data_files('')", catalogName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot handle an empty identifier for argument table");
  }

  /** 测试binpack表带specialchars场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBinPackTableWithSpecialChars() {
    Assume.assumeTrue(catalogName.equals(SparkCatalogConfig.HADOOP.catalogName()));

    TableIdentifier identifier =
        TableIdentifier.of("default", QUOTED_SPECIAL_CHARS_TABLE_NAME.replaceAll("`", ""));
    sql(
        "CREATE TABLE %s (c1 int, c2 string, c3 string) USING iceberg",
        tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    insertData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME), 10);

    List<Object[]> expectedRecords = currentData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s', where => 'c2 is not null')",
            catalogName, tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    assertEquals(
        "Action should rewrite 10 data files and add 1 data file",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isEqualTo(
            Long.valueOf(snapshotSummary(identifier).get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);

    Assert.assertEquals("Table cache must be empty", 0, SparkTableCache.get().size());
  }

  /** 测试排序表带specialchars场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSortTableWithSpecialChars() {
    Assume.assumeTrue(catalogName.equals(SparkCatalogConfig.HADOOP.catalogName()));

    TableIdentifier identifier =
        TableIdentifier.of("default", QUOTED_SPECIAL_CHARS_TABLE_NAME.replaceAll("`", ""));
    sql(
        "CREATE TABLE %s (c1 int, c2 string, c3 string) USING iceberg",
        tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    insertData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME), 10);

    List<Object[]> expectedRecords = currentData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files("
                + "  table => '%s',"
                + "  strategy => 'sort',"
                + "  sort_order => 'c1',"
                + "  where => 'c2 is not null')",
            catalogName, tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    assertEquals(
        "Action should rewrite 10 data files and add 1 data file",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(
            Long.valueOf(snapshotSummary(identifier).get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);

    Assert.assertEquals("Table cache must be empty", 0, SparkTableCache.get().size());
  }

  /** 测试z顺序表带specialchars场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testZOrderTableWithSpecialChars() {
    Assume.assumeTrue(catalogName.equals(SparkCatalogConfig.HADOOP.catalogName()));

    TableIdentifier identifier =
        TableIdentifier.of("default", QUOTED_SPECIAL_CHARS_TABLE_NAME.replaceAll("`", ""));
    sql(
        "CREATE TABLE %s (c1 int, c2 string, c3 string) USING iceberg",
        tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    insertData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME), 10);

    List<Object[]> expectedRecords = currentData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files("
                + "  table => '%s',"
                + "  strategy => 'sort',"
                + "  sort_order => 'zorder(c1, c2)',"
                + "  where => 'c2 is not null')",
            catalogName, tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));

    assertEquals(
        "Action should rewrite 10 data files and add 1 data file",
        row(10, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(
            Long.valueOf(snapshotSummary(identifier).get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData(tableName(QUOTED_SPECIAL_CHARS_TABLE_NAME));
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);

    Assert.assertEquals("Table cache must be empty", 0, SparkTableCache.get().size());
  }

  /** 测试默认排序顺序场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDefaultSortOrder() {
    createTable();
    // add a default sort order for a table
    sql("ALTER TABLE %s WRITE ORDERED BY c2", tableName);

    // this creates 2 files under non-partitioned table due to sort order.
    insertData(10);
    List<Object[]> expectedRecords = currentData();

    // When the strategy is set to 'sort' but the sort order is not specified,
    // use table's default sort order.
    List<Object[]> output =
        sql(
            "CALL %s.system.rewrite_data_files(table => '%s', "
                + "strategy => 'sort', "
                + "options => map('min-input-files','2'))",
            catalogName, tableIdent);

    assertEquals(
        "Action should rewrite 2 data files and add 1 data files",
        row(2, 1),
        Arrays.copyOf(output.get(0), 2));
    // verify rewritten bytes separately
    assertThat(output.get(0)).hasSize(4);
    assertThat(output.get(0)[2])
        .isInstanceOf(Long.class)
        .isEqualTo(Long.valueOf(snapshotSummary().get(SnapshotSummary.REMOVED_FILE_SIZE_PROP)));

    List<Object[]> actualRecords = currentData();
    assertEquals("Data after compaction should not change", expectedRecords, actualRecords);
  }

  /** 测试重写带untranslated或unconverted过滤器场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRewriteWithUntranslatedOrUnconvertedFilter() {
    createTable();
    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', where => 'substr(encode(c2, \"utf-8\"), 2) = \"fo\"')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot translate Spark expression");

    Assertions.assertThatThrownBy(
            () ->
                sql(
                    "CALL %s.system.rewrite_data_files(table => '%s', where => 'substr(c2, 2) = \"fo\"')",
                    catalogName, tableIdent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot convert Spark filter");
  }

  /** 创建表。 */
  private void createTable() {
    sql("CREATE TABLE %s (c1 int, c2 string, c3 string) USING iceberg", tableName);
  }

  /** 创建分区表。 */
  private void createPartitionTable() {
    sql(
        "CREATE TABLE %s (c1 int, c2 string, c3 string) "
            + "USING iceberg "
            + "PARTITIONED BY (c2) "
            + "TBLPROPERTIES ('%s' '%s')",
        tableName,
        TableProperties.WRITE_DISTRIBUTION_MODE,
        TableProperties.WRITE_DISTRIBUTION_MODE_NONE);
  }

  /** 创建桶分区表。 */
  private void createBucketPartitionTable() {
    sql(
        "CREATE TABLE %s (c1 int, c2 string, c3 string) "
            + "USING iceberg "
            + "PARTITIONED BY (bucket(2, c2)) "
            + "TBLPROPERTIES ('%s' '%s')",
        tableName,
        TableProperties.WRITE_DISTRIBUTION_MODE,
        TableProperties.WRITE_DISTRIBUTION_MODE_NONE);
  }

  /** 插入数据。 */
  private void insertData(int filesCount) {
    insertData(tableName, filesCount);
  }

  /** 插入数据。 */
  private void insertData(String table, int filesCount) {
    ThreeColumnRecord record1 = new ThreeColumnRecord(1, "foo", null);
    ThreeColumnRecord record2 = new ThreeColumnRecord(2, "bar", null);

    List<ThreeColumnRecord> records = Lists.newArrayList();
    IntStream.range(0, filesCount / 2)
        .forEach(
            i -> {
              records.add(record1);
              records.add(record2);
            });

    Dataset<Row> df =
        spark.createDataFrame(records, ThreeColumnRecord.class).repartition(filesCount);
    try {
      df.writeTo(table).append();
    } catch (org.apache.spark.sql.catalyst.analysis.NoSuchTableException e) {
      throw new RuntimeException(e);
    }
  }

  /** 快照summary。 */
  private Map<String, String> snapshotSummary() {
    return snapshotSummary(tableIdent);
  }

  /** 快照summary。 */
  private Map<String, String> snapshotSummary(TableIdentifier tableIdentifier) {
    return validationCatalog.loadTable(tableIdentifier).currentSnapshot().summary();
  }

  /** 当前数据。 */
  private List<Object[]> currentData() {
    return currentData(tableName);
  }

  /** 当前数据。 */
  private List<Object[]> currentData(String table) {
    return rowsToJava(spark.sql("SELECT * FROM " + table + " order by c1, c2, c3").collectAsList());
  }
}
