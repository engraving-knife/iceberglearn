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

import static org.apache.iceberg.spark.SystemFunctionPushDownHelper.createPartitionedTable;
import static org.apache.iceberg.spark.SystemFunctionPushDownHelper.createUnpartitionedTable;
import static org.apache.iceberg.spark.SystemFunctionPushDownHelper.timestampStrToDayOrdinal;
import static org.apache.iceberg.spark.SystemFunctionPushDownHelper.timestampStrToHourOrdinal;
import static org.apache.iceberg.spark.SystemFunctionPushDownHelper.timestampStrToMonthOrdinal;
import static org.apache.iceberg.spark.SystemFunctionPushDownHelper.timestampStrToYearOrdinal;
import static org.apache.spark.sql.functions.date_add;
import static org.apache.spark.sql.functions.expr;

import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkTestBaseWithCatalog;
import org.apache.iceberg.spark.functions.BucketFunction;
import org.apache.iceberg.spark.functions.DaysFunction;
import org.apache.iceberg.spark.functions.HoursFunction;
import org.apache.iceberg.spark.functions.MonthsFunction;
import org.apache.iceberg.spark.functions.TruncateFunction;
import org.apache.iceberg.spark.functions.YearsFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.connector.catalog.functions.BoundFunction;
import org.apache.spark.sql.connector.expressions.Expression;
import org.apache.spark.sql.connector.expressions.FieldReference;
import org.apache.spark.sql.connector.expressions.LiteralValue;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.expressions.UserDefinedScalarFunc;
import org.apache.spark.sql.connector.expressions.filter.And;
import org.apache.spark.sql.connector.expressions.filter.Not;
import org.apache.spark.sql.connector.expressions.filter.Or;
import org.apache.spark.sql.connector.expressions.filter.Predicate;
import org.apache.spark.sql.connector.read.Batch;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.connector.read.Statistics;
import org.apache.spark.sql.connector.read.SupportsPushDownV2Filters;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestSparkScan 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Spark扫描 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public class TestSparkScan extends SparkTestBaseWithCatalog {

  private final String format;

  /** 参数。 */
  @Parameterized.Parameters(name = "format = {0}")
  public static Object[] parameters() {
    return new Object[] {"parquet", "avro", "orc"};
  }

  /** 测试Spark扫描。 */
  public TestSparkScan(String format) {
    this.format = format;
  }

  /** use目录。 */
  @Before
  public void useCatalog() {
    sql("USE %s", catalogName);
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试estimated行计数场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEstimatedRowCount() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (id BIGINT, date DATE) USING iceberg TBLPROPERTIES('%s' = '%s')",
        tableName, TableProperties.DEFAULT_FILE_FORMAT, format);

    Dataset<Row> df =
        spark
            .range(10000)
            .withColumn("date", date_add(expr("DATE '1970-01-01'"), expr("CAST(id AS INT)")))
            .select("id", "date");

    df.coalesce(1).writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    SparkScanBuilder scanBuilder =
        new SparkScanBuilder(spark, table, CaseInsensitiveStringMap.empty());
    SparkScan scan = (SparkScan) scanBuilder.build();
    Statistics stats = scan.estimateStatistics();

    Assert.assertEquals(10000L, stats.numRows().getAsLong());
  }

  /** 测试非分区years场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedYears() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    YearsFunction.TimestampToYearsFunction function = new YearsFunction.TimestampToYearsFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            "=",
            expressions(
                udf, intLit(timestampStrToYearOrdinal("2017-11-22T00:00:00.000000+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT Equal
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区years场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedYears() throws Exception {
    createPartitionedTable(spark, tableName, "years(ts)");

    SparkScanBuilder builder = scanBuilder();

    YearsFunction.TimestampToYearsFunction function = new YearsFunction.TimestampToYearsFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            "=",
            expressions(
                udf, intLit(timestampStrToYearOrdinal("2017-11-22T00:00:00.000000+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);

    // NOT Equal
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);
  }

  /** 测试非分区months场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedMonths() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    MonthsFunction.TimestampToMonthsFunction function =
        new MonthsFunction.TimestampToMonthsFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            ">",
            expressions(
                udf, intLit(timestampStrToMonthOrdinal("2017-11-22T00:00:00.000000+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT GT
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区months场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedMonths() throws Exception {
    createPartitionedTable(spark, tableName, "months(ts)");

    SparkScanBuilder builder = scanBuilder();

    MonthsFunction.TimestampToMonthsFunction function =
        new MonthsFunction.TimestampToMonthsFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            ">",
            expressions(
                udf, intLit(timestampStrToMonthOrdinal("2017-11-22T00:00:00.000000+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);

    // NOT GT
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);
  }

  /** 测试非分区days场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedDays() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    DaysFunction.TimestampToDaysFunction function = new DaysFunction.TimestampToDaysFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            "<",
            expressions(
                udf, dateLit(timestampStrToDayOrdinal("2018-11-20T00:00:00.000000+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT LT
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区days场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedDays() throws Exception {
    createPartitionedTable(spark, tableName, "days(ts)");

    SparkScanBuilder builder = scanBuilder();

    DaysFunction.TimestampToDaysFunction function = new DaysFunction.TimestampToDaysFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            "<",
            expressions(
                udf, dateLit(timestampStrToDayOrdinal("2018-11-20T00:00:00.000000+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);

    // NOT LT
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);
  }

  /** 测试非分区hours场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedHours() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    HoursFunction.TimestampToHoursFunction function = new HoursFunction.TimestampToHoursFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            ">=",
            expressions(
                udf, intLit(timestampStrToHourOrdinal("2017-11-22T06:02:09.243857+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT GTEQ
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区hours场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedHours() throws Exception {
    createPartitionedTable(spark, tableName, "hours(ts)");

    SparkScanBuilder builder = scanBuilder();

    HoursFunction.TimestampToHoursFunction function = new HoursFunction.TimestampToHoursFunction();
    UserDefinedScalarFunc udf = toUDF(function, expressions(fieldRef("ts")));
    Predicate predicate =
        new Predicate(
            ">=",
            expressions(
                udf, intLit(timestampStrToHourOrdinal("2017-11-22T06:02:09.243857+00:00"))));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(8);

    // NOT GTEQ
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(2);
  }

  /** 测试非分区桶长整型场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedBucketLong() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    BucketFunction.BucketLong function = new BucketFunction.BucketLong(DataTypes.LongType);
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(5), fieldRef("id")));
    Predicate predicate = new Predicate(">=", expressions(udf, intLit(2)));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT GTEQ
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区桶长整型场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedBucketLong() throws Exception {
    createPartitionedTable(spark, tableName, "bucket(5, id)");

    SparkScanBuilder builder = scanBuilder();

    BucketFunction.BucketLong function = new BucketFunction.BucketLong(DataTypes.LongType);
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(5), fieldRef("id")));
    Predicate predicate = new Predicate(">=", expressions(udf, intLit(2)));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(6);

    // NOT GTEQ
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(4);
  }

  /** 测试非分区桶字符串场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedBucketString() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    BucketFunction.BucketString function = new BucketFunction.BucketString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(5), fieldRef("data")));
    Predicate predicate = new Predicate("<=", expressions(udf, intLit(2)));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT LTEQ
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区桶字符串场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedBucketString() throws Exception {
    createPartitionedTable(spark, tableName, "bucket(5, data)");

    SparkScanBuilder builder = scanBuilder();

    BucketFunction.BucketString function = new BucketFunction.BucketString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(5), fieldRef("data")));
    Predicate predicate = new Predicate("<=", expressions(udf, intLit(2)));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(6);

    // NOT LTEQ
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(4);
  }

  /** 测试非分区截断字符串场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedTruncateString() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    TruncateFunction.TruncateString function = new TruncateFunction.TruncateString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(4), fieldRef("data")));
    Predicate predicate = new Predicate("<>", expressions(udf, stringLit("data")));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT NotEqual
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区截断字符串场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedTruncateString() throws Exception {
    createPartitionedTable(spark, tableName, "truncate(4, data)");

    SparkScanBuilder builder = scanBuilder();

    TruncateFunction.TruncateString function = new TruncateFunction.TruncateString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(4), fieldRef("data")));
    Predicate predicate = new Predicate("<>", expressions(udf, stringLit("data")));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);

    // NOT NotEqual
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(5);
  }

  /** 测试非分区是否空值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedIsNull() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    TruncateFunction.TruncateString function = new TruncateFunction.TruncateString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(4), fieldRef("data")));
    Predicate predicate = new Predicate("IS_NULL", expressions(udf));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT IsNull
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区是否空值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedIsNull() throws Exception {
    createPartitionedTable(spark, tableName, "truncate(4, data)");

    SparkScanBuilder builder = scanBuilder();

    TruncateFunction.TruncateString function = new TruncateFunction.TruncateString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(4), fieldRef("data")));
    Predicate predicate = new Predicate("IS_NULL", expressions(udf));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(0);

    // NOT IsNULL
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试非分区是否非空值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedIsNotNull() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    TruncateFunction.TruncateString function = new TruncateFunction.TruncateString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(4), fieldRef("data")));
    Predicate predicate = new Predicate("IS_NOT_NULL", expressions(udf));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT IsNotNull
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区是否非空值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedIsNotNull() throws Exception {
    createPartitionedTable(spark, tableName, "truncate(4, data)");

    SparkScanBuilder builder = scanBuilder();

    TruncateFunction.TruncateString function = new TruncateFunction.TruncateString();
    UserDefinedScalarFunc udf = toUDF(function, expressions(intLit(4), fieldRef("data")));
    Predicate predicate = new Predicate("IS_NOT_NULL", expressions(udf));
    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT IsNotNULL
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(0);
  }

  /** 测试非分区与场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedAnd() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    YearsFunction.TimestampToYearsFunction tsToYears = new YearsFunction.TimestampToYearsFunction();
    UserDefinedScalarFunc udf1 = toUDF(tsToYears, expressions(fieldRef("ts")));
    Predicate predicate1 = new Predicate("=", expressions(udf1, intLit(2017 - 1970)));

    BucketFunction.BucketLong bucketLong = new BucketFunction.BucketLong(DataTypes.LongType);
    UserDefinedScalarFunc udf = toUDF(bucketLong, expressions(intLit(5), fieldRef("id")));
    Predicate predicate2 = new Predicate(">=", expressions(udf, intLit(2)));
    Predicate predicate = new And(predicate1, predicate2);

    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT (years(ts) = 47 AND bucket(id, 5) >= 2)
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区与场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedAnd() throws Exception {
    createPartitionedTable(spark, tableName, "years(ts), bucket(5, id)");

    SparkScanBuilder builder = scanBuilder();

    YearsFunction.TimestampToYearsFunction tsToYears = new YearsFunction.TimestampToYearsFunction();
    UserDefinedScalarFunc udf1 = toUDF(tsToYears, expressions(fieldRef("ts")));
    Predicate predicate1 = new Predicate("=", expressions(udf1, intLit(2017 - 1970)));

    BucketFunction.BucketLong bucketLong = new BucketFunction.BucketLong(DataTypes.LongType);
    UserDefinedScalarFunc udf = toUDF(bucketLong, expressions(intLit(5), fieldRef("id")));
    Predicate predicate2 = new Predicate(">=", expressions(udf, intLit(2)));
    Predicate predicate = new And(predicate1, predicate2);

    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(1);

    // NOT (years(ts) = 47 AND bucket(id, 5) >= 2)
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(9);
  }

  /** 测试非分区或场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testUnpartitionedOr() throws Exception {
    createUnpartitionedTable(spark, tableName);

    SparkScanBuilder builder = scanBuilder();

    YearsFunction.TimestampToYearsFunction tsToYears = new YearsFunction.TimestampToYearsFunction();
    UserDefinedScalarFunc udf1 = toUDF(tsToYears, expressions(fieldRef("ts")));
    Predicate predicate1 = new Predicate("=", expressions(udf1, intLit(2017 - 1970)));

    BucketFunction.BucketLong bucketLong = new BucketFunction.BucketLong(DataTypes.LongType);
    UserDefinedScalarFunc udf = toUDF(bucketLong, expressions(intLit(5), fieldRef("id")));
    Predicate predicate2 = new Predicate(">=", expressions(udf, intLit(2)));
    Predicate predicate = new Or(predicate1, predicate2);

    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);

    // NOT (years(ts) = 47 OR bucket(id, 5) >= 2)
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(10);
  }

  /** 测试分区或场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedOr() throws Exception {
    createPartitionedTable(spark, tableName, "years(ts), bucket(5, id)");

    SparkScanBuilder builder = scanBuilder();

    YearsFunction.TimestampToYearsFunction tsToYears = new YearsFunction.TimestampToYearsFunction();
    UserDefinedScalarFunc udf1 = toUDF(tsToYears, expressions(fieldRef("ts")));
    Predicate predicate1 = new Predicate("=", expressions(udf1, intLit(2018 - 1970)));

    BucketFunction.BucketLong bucketLong = new BucketFunction.BucketLong(DataTypes.LongType);
    UserDefinedScalarFunc udf = toUDF(bucketLong, expressions(intLit(5), fieldRef("id")));
    Predicate predicate2 = new Predicate(">=", expressions(udf, intLit(2)));
    Predicate predicate = new Or(predicate1, predicate2);

    pushFilters(builder, predicate);
    Batch scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(6);

    // NOT (years(ts) = 48 OR bucket(id, 5) >= 2)
    builder = scanBuilder();

    predicate = new Not(predicate);
    pushFilters(builder, predicate);
    scan = builder.build().toBatch();

    Assertions.assertThat(scan.planInputPartitions().length).isEqualTo(4);
  }

  /** 扫描构建器。 */
  private SparkScanBuilder scanBuilder() throws Exception {
    Table table = Spark3Util.loadIcebergTable(spark, tableName);
    CaseInsensitiveStringMap options =
        new CaseInsensitiveStringMap(ImmutableMap.of("path", tableName));

    return new SparkScanBuilder(spark, table, options);
  }

  /** 下推过滤器。 */
  private void pushFilters(ScanBuilder scan, Predicate... predicates) {
    Assertions.assertThat(scan).isInstanceOf(SupportsPushDownV2Filters.class);
    SupportsPushDownV2Filters filterable = (SupportsPushDownV2Filters) scan;
    filterable.pushPredicates(predicates);
  }

  /** 表达式。 */
  private Expression[] expressions(Expression... expressions) {
    return expressions;
  }

  /** 辅助方法：intLit。 */
  private static LiteralValue<Integer> intLit(int value) {
    return LiteralValue.apply(value, DataTypes.IntegerType);
  }

  /** 日期lit。 */
  private static LiteralValue<Integer> dateLit(int value) {
    return LiteralValue.apply(value, DataTypes.DateType);
  }

  /** 字符串lit。 */
  private static LiteralValue<String> stringLit(String value) {
    return LiteralValue.apply(value, DataTypes.StringType);
  }

  /** 字段ref。 */
  private static NamedReference fieldRef(String col) {
    return FieldReference.apply(col);
  }

  /** 到UDF。 */
  private static UserDefinedScalarFunc toUDF(BoundFunction function, Expression[] expressions) {
    return new UserDefinedScalarFunc(function.name(), function.canonicalName(), expressions);
  }
}
