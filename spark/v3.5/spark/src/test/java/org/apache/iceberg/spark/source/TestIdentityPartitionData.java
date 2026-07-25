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

import static org.apache.iceberg.PlanningMode.DISTRIBUTED;
import static org.apache.iceberg.PlanningMode.LOCAL;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.PlanningMode;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.SparkTableUtil;
import org.apache.iceberg.spark.SparkTestBase;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.TableIdentifier;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestIdentityPartitionData 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 恒等分区数据 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public class TestIdentityPartitionData extends SparkTestBase {
  private static final Configuration CONF = new Configuration();
  private static final HadoopTables TABLES = new HadoopTables(CONF);

  /** 参数。 */
  @Parameterized.Parameters(name = "format = {0}, vectorized = {1}, planningMode = {2}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"parquet", false, LOCAL},
      {"parquet", true, DISTRIBUTED},
      {"avro", false, LOCAL},
      {"orc", false, DISTRIBUTED},
      {"orc", true, LOCAL},
    };
  }

  private final String format;
  private final boolean vectorized;
  private final Map<String, String> properties;

  /** 测试恒等分区数据。 */
  public TestIdentityPartitionData(String format, boolean vectorized, PlanningMode planningMode) {
    this.format = format;
    this.vectorized = vectorized;
    this.properties =
        ImmutableMap.of(
            TableProperties.DEFAULT_FILE_FORMAT, format,
            TableProperties.DATA_PLANNING_MODE, planningMode.modeName(),
            TableProperties.DELETE_PLANNING_MODE, planningMode.modeName());
  }

  private static final Schema LOG_SCHEMA =
      new Schema(
          Types.NestedField.optional(1, "id", Types.IntegerType.get()),
          Types.NestedField.optional(2, "date", Types.StringType.get()),
          Types.NestedField.optional(3, "level", Types.StringType.get()),
          Types.NestedField.optional(4, "message", Types.StringType.get()));

  private static final List<LogMessage> LOGS =
      ImmutableList.of(
          LogMessage.debug("2020-02-02", "debug event 1"),
          LogMessage.info("2020-02-02", "info event 1"),
          LogMessage.debug("2020-02-02", "debug event 2"),
          LogMessage.info("2020-02-03", "info event 2"),
          LogMessage.debug("2020-02-03", "debug event 3"),
          LogMessage.info("2020-02-03", "info event 3"),
          LogMessage.error("2020-02-03", "error event 1"),
          LogMessage.debug("2020-02-04", "debug event 4"),
          LogMessage.warn("2020-02-04", "warn event 1"),
          LogMessage.debug("2020-02-04", "debug event 5"));

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private PartitionSpec spec =
      PartitionSpec.builderFor(LOG_SCHEMA).identity("date").identity("level").build();
  private Table table = null;
  private Dataset<Row> logs = null;

  /** 初始化Parquet。 */
  /**
   * Use the Hive Based table to make Identity Partition Columns with no duplication of the data in
   * the underlying parquet files. This makes sure that if the identity mapping fails, the test will
   * also fail.
   */
  private void setupParquet() throws Exception {
    File location = temp.newFolder("logs");
    File hiveLocation = temp.newFolder("hive");
    String hiveTable = "hivetable";
    Assert.assertTrue("Temp folder should exist", location.exists());

    this.logs =
        spark.createDataFrame(LOGS, LogMessage.class).select("id", "date", "level", "message");
    spark.sql(String.format("DROP TABLE IF EXISTS %s", hiveTable));
    logs.orderBy("date", "level", "id")
        .write()
        .partitionBy("date", "level")
        .format("parquet")
        .option("path", hiveLocation.toString())
        .saveAsTable(hiveTable);

    this.table =
        TABLES.create(
            SparkSchemaUtil.schemaForTable(spark, hiveTable),
            SparkSchemaUtil.specForTable(spark, hiveTable),
            properties,
            location.toString());

    SparkTableUtil.importSparkTable(
        spark, new TableIdentifier(hiveTable), table, location.toString());
  }

  /** 初始化表。 */
  @Before
  public void setupTable() throws Exception {
    if (format.equals("parquet")) {
      setupParquet();
    } else {
      File location = temp.newFolder("logs");
      Assert.assertTrue("Temp folder should exist", location.exists());

      this.table = TABLES.create(LOG_SCHEMA, spec, properties, location.toString());
      this.logs =
          spark.createDataFrame(LOGS, LogMessage.class).select("id", "date", "level", "message");

      logs.orderBy("date", "level", "id")
          .write()
          .format("iceberg")
          .mode("append")
          .save(location.toString());
    }
  }

  /** 测试全投影场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testFullProjection() {
    List<Row> expected = logs.orderBy("id").collectAsList();
    List<Row> actual =
        spark
            .read()
            .format("iceberg")
            .option(SparkReadOptions.VECTORIZATION_ENABLED, String.valueOf(vectorized))
            .load(table.location())
            .orderBy("id")
            .select("id", "date", "level", "message")
            .collectAsList();
    Assert.assertEquals("Rows should match", expected, actual);
  }

  /** 测试投影场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testProjections() {
    String[][] cases =
        new String[][] {
          // individual fields
          new String[] {"date"},
          new String[] {"level"},
          new String[] {"message"},
          // field pairs
          new String[] {"date", "message"},
          new String[] {"level", "message"},
          new String[] {"date", "level"},
          // out-of-order pairs
          new String[] {"message", "date"},
          new String[] {"message", "level"},
          new String[] {"level", "date"},
          // full projection, different orderings
          new String[] {"date", "level", "message"},
          new String[] {"level", "date", "message"},
          new String[] {"date", "message", "level"},
          new String[] {"level", "message", "date"},
          new String[] {"message", "date", "level"},
          new String[] {"message", "level", "date"}
        };

    for (String[] ordering : cases) {
      List<Row> expected = logs.select("id", ordering).orderBy("id").collectAsList();
      List<Row> actual =
          spark
              .read()
              .format("iceberg")
              .option(SparkReadOptions.VECTORIZATION_ENABLED, String.valueOf(vectorized))
              .load(table.location())
              .select("id", ordering)
              .orderBy("id")
              .collectAsList();
      Assert.assertEquals(
          "Rows should match for ordering: " + Arrays.toString(ordering), expected, actual);
    }
  }
}
