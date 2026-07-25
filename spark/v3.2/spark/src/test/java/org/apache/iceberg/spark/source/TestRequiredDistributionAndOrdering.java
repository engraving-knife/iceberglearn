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
package org.apache.iceberg.spark.source;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.spark.SparkCatalogTestBase;
import org.apache.iceberg.spark.SparkWriteOptions;
import org.apache.spark.SparkException;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.junit.After;
import org.junit.Test;

/**
 * 文件级说明：测试 TestRequiredDistributionAndOrdering 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 必需分布与排序 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestRequiredDistributionAndOrdering extends SparkCatalogTestBase {

  /** 测试必需分布与排序。 */
  public TestRequiredDistributionAndOrdering(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 删除测试表。 */
  @After
  public void dropTestTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试默认local排序场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDefaultLocalSort() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, c3 STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (c3)",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(3, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(4, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(5, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(6, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(7, "BBBBBBBBBB", "A"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF = ds.coalesce(1).sortWithinPartitions("c1");

    // should insert a local sort by partition columns by default
    inputDF.writeTo(tableName).append();

    assertEquals(
        "Row count must match",
        ImmutableList.of(row(7L)),
        sql("SELECT count(*) FROM %s", tableName));
  }

  /** 测试分区列areprepended用于range分布场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionColumnsArePrependedForRangeDistribution() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, c3 STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (c3)",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(3, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(4, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(5, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(6, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(7, "BBBBBBBBBB", "A"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF = ds.coalesce(1).sortWithinPartitions("c1");

    Table table = validationCatalog.loadTable(tableIdent);

    // should automatically prepend partition columns to the ordering
    table
        .updateProperties()
        .set(TableProperties.WRITE_DISTRIBUTION_MODE, TableProperties.WRITE_DISTRIBUTION_MODE_RANGE)
        .commit();
    table.replaceSortOrder().asc("c1").asc("c2").commit();
    inputDF.writeTo(tableName).append();

    assertEquals(
        "Row count must match",
        ImmutableList.of(row(7L)),
        sql("SELECT count(*) FROM %s", tableName));
  }

  /** 测试排序顺序includes分区列场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSortOrderIncludesPartitionColumns() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, c3 STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (c3)",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(3, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(4, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(5, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(6, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(7, "BBBBBBBBBB", "A"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF = ds.coalesce(1).sortWithinPartitions("c1");

    Table table = validationCatalog.loadTable(tableIdent);

    // should succeed with a correct sort order
    table.replaceSortOrder().asc("c3").asc("c1").asc("c2").commit();
    inputDF.writeTo(tableName).append();

    assertEquals(
        "Row count must match",
        ImmutableList.of(row(7L)),
        sql("SELECT count(*) FROM %s", tableName));
  }

  /** 测试disabled分布与排序场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDisabledDistributionAndOrdering() {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, c3 STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (c3)",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(3, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(4, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(5, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(6, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(7, "BBBBBBBBBB", "A"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF = ds.coalesce(1).sortWithinPartitions("c1");

    // should fail if ordering is disabled
    AssertHelpers.assertThrows(
        "Should reject writes without ordering",
        SparkException.class,
        "Writing job aborted",
        () -> {
          try {
            inputDF
                .writeTo(tableName)
                .option(SparkWriteOptions.USE_TABLE_DISTRIBUTION_AND_ORDERING, "false")
                .append();
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });
  }

  /** 测试哈希分布场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testHashDistribution() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, c3 STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (c3)",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(3, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(4, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(5, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(6, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(7, "BBBBBBBBBB", "A"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF = ds.coalesce(1).sortWithinPartitions("c1");

    Table table = validationCatalog.loadTable(tableIdent);

    // should automatically prepend partition columns to the local ordering after hash distribution
    table
        .updateProperties()
        .set(TableProperties.WRITE_DISTRIBUTION_MODE, TableProperties.WRITE_DISTRIBUTION_MODE_HASH)
        .commit();
    table.replaceSortOrder().asc("c1").asc("c2").commit();
    inputDF.writeTo(tableName).append();

    assertEquals(
        "Row count must match",
        ImmutableList.of(row(7L)),
        sql("SELECT count(*) FROM %s", tableName));
  }

  /** 测试no排序桶转换无扩展场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testNoSortBucketTransformsWithoutExtensions() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, c3 STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (bucket(2, c1))",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBB", "B"),
            new ThreeColumnRecord(3, "BBBB", "B"),
            new ThreeColumnRecord(4, "BBBB", "B"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF = ds.coalesce(1).sortWithinPartitions("c1");

    // should fail by default as extensions are disabled
    AssertHelpers.assertThrows(
        "Should reject writes without ordering",
        SparkException.class,
        "Writing job aborted",
        () -> {
          try {
            inputDF.writeTo(tableName).append();
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    inputDF.writeTo(tableName).option(SparkWriteOptions.FANOUT_ENABLED, "true").append();

    List<Object[]> expected =
        ImmutableList.of(
            row(1, null, "A"), row(2, "BBBB", "B"), row(3, "BBBB", "B"), row(4, "BBBB", "B"));

    assertEquals("Rows must match", expected, sql("SELECT * FROM %s ORDER BY c1", tableName));
  }

  /** 测试range分布带quoted列names场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRangeDistributionWithQuotedColumnsNames() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, `c.3` STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (`c.3`)",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(3, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(4, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(5, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(6, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(7, "BBBBBBBBBB", "A"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF =
        ds.selectExpr("c1", "c2", "c3 as `c.3`").coalesce(1).sortWithinPartitions("c1");

    Table table = validationCatalog.loadTable(tableIdent);

    table
        .updateProperties()
        .set(TableProperties.WRITE_DISTRIBUTION_MODE, TableProperties.WRITE_DISTRIBUTION_MODE_RANGE)
        .commit();
    table.replaceSortOrder().asc("c1").asc("c2").commit();
    inputDF.writeTo(tableName).append();

    assertEquals(
        "Row count must match",
        ImmutableList.of(row(7L)),
        sql("SELECT count(*) FROM %s", tableName));
  }

  /** 测试哈希分布带quoted列names场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testHashDistributionWithQuotedColumnsNames() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (c1 INT, c2 STRING, `c``3` STRING) "
            + "USING iceberg "
            + "PARTITIONED BY (`c``3`)",
        tableName);

    List<ThreeColumnRecord> data =
        ImmutableList.of(
            new ThreeColumnRecord(1, null, "A"),
            new ThreeColumnRecord(2, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(3, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(4, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(5, "BBBBBBBBBB", "A"),
            new ThreeColumnRecord(6, "BBBBBBBBBB", "B"),
            new ThreeColumnRecord(7, "BBBBBBBBBB", "A"));
    Dataset<Row> ds = spark.createDataFrame(data, ThreeColumnRecord.class);
    Dataset<Row> inputDF =
        ds.selectExpr("c1", "c2", "c3 as `c``3`").coalesce(1).sortWithinPartitions("c1");

    Table table = validationCatalog.loadTable(tableIdent);

    table
        .updateProperties()
        .set(TableProperties.WRITE_DISTRIBUTION_MODE, TableProperties.WRITE_DISTRIBUTION_MODE_HASH)
        .commit();
    table.replaceSortOrder().asc("c1").asc("c2").commit();
    inputDF.writeTo(tableName).append();

    assertEquals(
        "Row count must match",
        ImmutableList.of(row(7L)),
        sql("SELECT count(*) FROM %s", tableName));
  }
}
