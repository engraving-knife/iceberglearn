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

import static org.apache.iceberg.Files.localOutput;
import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;
import static org.apache.spark.sql.functions.monotonically_increasing_id;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.avro.generic.GenericData;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.spark.data.AvroDataTest;
import org.apache.iceberg.spark.data.RandomData;
import org.apache.iceberg.spark.data.TestHelpers;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestParquetScan 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Parquet扫描 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public class TestParquetScan extends AvroDataTest {
  private static final Configuration CONF = new Configuration();

  private static SparkSession spark = null;

  /** 启动Spark。 */
  @BeforeClass
  public static void startSpark() {
    TestParquetScan.spark = SparkSession.builder().master("local[2]").getOrCreate();
  }

  /** 停止Spark。 */
  @AfterClass
  public static void stopSpark() {
    SparkSession currentSpark = TestParquetScan.spark;
    TestParquetScan.spark = null;
    currentSpark.stop();
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /** 参数。 */
  @Parameterized.Parameters(name = "vectorized = {0}")
  public static Object[] parameters() {
    return new Object[] {false, true};
  }

  private final boolean vectorized;

  /** 测试Parquet扫描。 */
  public TestParquetScan(boolean vectorized) {
    this.vectorized = vectorized;
  }

  /** 写与校验。 */
  @Override
  protected void writeAndValidate(Schema schema) throws IOException {
    Assume.assumeTrue(
        "Cannot handle non-string map keys in parquet-avro",
        null
            == TypeUtil.find(
                schema,
                type -> type.isMapType() && type.asMapType().keyType() != Types.StringType.get()));

    Table table = createTable(schema);

    // Important: use the table's schema for the rest of the test
    // When tables are created, the column ids are reassigned.
    List<GenericData.Record> expected = RandomData.generateList(table.schema(), 100, 1L);
    writeRecords(table, expected);

    configureVectorization(table);

    Dataset<Row> df = spark.read().format("iceberg").load(table.location());

    List<Row> rows = df.collectAsList();
    Assert.assertEquals("Should contain 100 rows", 100, rows.size());

    for (int i = 0; i < expected.size(); i += 1) {
      TestHelpers.assertEqualsSafe(table.schema().asStruct(), expected.get(i), rows.get(i));
    }
  }

  /** 测试空表投影场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEmptyTableProjection() throws IOException {
    Types.StructType structType =
        Types.StructType.of(
            required(100, "id", Types.LongType.get()),
            optional(101, "data", Types.StringType.get()),
            required(102, "b", Types.BooleanType.get()),
            optional(103, "i", Types.IntegerType.get()));
    Table table = createTable(new Schema(structType.fields()));

    List<GenericData.Record> expected = RandomData.generateList(table.schema(), 100, 1L);
    writeRecords(table, expected);

    configureVectorization(table);

    List<Row> rows =
        spark
            .read()
            .format("iceberg")
            .load(table.location())
            .select(monotonically_increasing_id())
            .collectAsList();
    assertThat(rows).hasSize(100);
  }

  /** 创建表。 */
  private Table createTable(Schema schema) throws IOException {
    File parent = temp.newFolder("parquet");
    File location = new File(parent, "test");
    HadoopTables tables = new HadoopTables(CONF);
    return tables.create(schema, PartitionSpec.unpartitioned(), location.toString());
  }

  /** 写记录。 */
  private void writeRecords(Table table, List<GenericData.Record> records) throws IOException {
    File dataFolder = new File(table.location(), "data");
    dataFolder.mkdirs();

    File parquetFile =
        new File(dataFolder, FileFormat.PARQUET.addExtension(UUID.randomUUID().toString()));

    try (FileAppender<GenericData.Record> writer =
        Parquet.write(localOutput(parquetFile)).schema(table.schema()).build()) {
      writer.addAll(records);
    }

    DataFile file =
        DataFiles.builder(PartitionSpec.unpartitioned())
            .withFileSizeInBytes(parquetFile.length())
            .withPath(parquetFile.toString())
            .withRecordCount(100)
            .build();

    table.newAppend().appendFile(file).commit();
  }

  /** 辅助方法：configureVectorization。 */
  private void configureVectorization(Table table) {
    table
        .updateProperties()
        .set(TableProperties.PARQUET_VECTORIZATION_ENABLED, String.valueOf(vectorized))
        .commit();
  }
}
