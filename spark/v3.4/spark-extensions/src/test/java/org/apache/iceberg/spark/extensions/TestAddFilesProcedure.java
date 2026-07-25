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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 TestAddFilesProcedure 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 添加文件存储过程 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestAddFilesProcedure extends SparkExtensionsTestBase {

  private final String sourceTableName = "source_table";
  private File fileTableDir;

  /** 测试添加文件存储过程。 */
  public TestAddFilesProcedure(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /** 初始化tempdirs。 */
  @Before
  public void setupTempDirs() {
    try {
      fileTableDir = temp.newFolder();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** 删除表。 */
  @After
  public void dropTables() {
    sql("DROP TABLE IF EXISTS %s", sourceTableName);
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 添加数据非分区。 */
  @Test
  public void addDataUnpartitioned() {
    createUnpartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT * FROM %s ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 删除与添加back非分区。 */
  @Test
  public void deleteAndAddBackUnpartitioned() {
    createUnpartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    sql(
        "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
        catalogName, tableName, fileTableDir.getAbsolutePath());

    String deleteData = "DELETE FROM %s";
    sql(deleteData, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT * FROM %s ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  @Ignore // TODO Classpath issues prevent us from actually writing to a Spark ORC table
  /** 添加数据非分区ORC。 */
  public void addDataUnpartitionedOrc() {
    createUnpartitionedFileTable("orc");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    Object result =
        scalarSql(
            "CALL %s.system.add_files('%s', '`orc`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    Assert.assertEquals(2L, result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT * FROM %s ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 添加Avro文件。 */
  @Test
  public void addAvroFile() throws Exception {
    // Spark Session Catalog cannot load metadata tables
    // with "The namespace in session catalog must have exactly one name part"
    Assume.assumeFalse(catalogName.equals("spark_catalog"));

    // Create an Avro file

    Schema schema =
        SchemaBuilder.record("record")
            .fields()
            .requiredInt("id")
            .requiredString("data")
            .endRecord();
    GenericRecord record1 = new GenericData.Record(schema);
    record1.put("id", 1L);
    record1.put("data", "a");
    GenericRecord record2 = new GenericData.Record(schema);
    record2.put("id", 2L);
    record2.put("data", "b");
    File outputFile = temp.newFile("test.avro");

    DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter(schema);
    DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter(datumWriter);
    dataFileWriter.create(schema, outputFile);
    dataFileWriter.append(record1);
    dataFileWriter.append(record2);
    dataFileWriter.close();

    String createIceberg = "CREATE TABLE %s (id Long, data String) USING iceberg";
    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`avro`.`%s`')",
            catalogName, tableName, outputFile.getPath());
    assertEquals("Procedure output must match", ImmutableList.of(row(1L, 1L)), result);

    List<Object[]> expected = Lists.newArrayList(new Object[] {1L, "a"}, new Object[] {2L, "b"});

    assertEquals(
        "Iceberg table contains correct data",
        expected,
        sql("SELECT * FROM %s ORDER BY id", tableName));

    List<Object[]> actualRecordCount =
        sql("select %s from %s.files", DataFile.RECORD_COUNT.name(), tableName);
    List<Object[]> expectedRecordCount = Lists.newArrayList();
    expectedRecordCount.add(new Object[] {2L});
    assertEquals(
        "Iceberg file metadata should have correct metadata count",
        expectedRecordCount,
        actualRecordCount);
  }

  // TODO Adding spark-avro doesn't work in tests
  @Ignore
  public void addDataUnpartitionedAvro() {
    createUnpartitionedFileTable("avro");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    Object result =
        scalarSql(
            "CALL %s.system.add_files('%s', '`avro`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    Assert.assertEquals(2L, result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT * FROM %s ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 添加数据非分区hive。 */
  @Test
  public void addDataUnpartitionedHive() {
    createUnpartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql("CALL %s.system.add_files('%s', '%s')", catalogName, tableName, sourceTableName);

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT * FROM %s ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 添加数据非分区extracol。 */
  @Test
  public void addDataUnpartitionedExtraCol() {
    createUnpartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String, foo string) USING iceberg";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT * FROM %s ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加数据非分区missingcol。 */
  @Test
  public void addDataUnpartitionedMissingCol() {
    createUnpartitionedFileTable("parquet");

    String createIceberg = "CREATE TABLE %s (id Integer, name String, dept String) USING iceberg";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept FROM %s ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 添加数据分区missingcol。 */
  @Test
  public void addDataPartitionedMissingCol() {
    createPartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(8L, 4L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept FROM %s ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 添加数据分区。 */
  @Test
  public void addDataPartitioned() {
    createPartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(8L, 4L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  @Ignore // TODO Classpath issues prevent us from actually writing to a Spark ORC table
  /** 添加数据分区ORC。 */
  public void addDataPartitionedOrc() {
    createPartitionedFileTable("orc");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    Object result =
        scalarSql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    Assert.assertEquals(8L, result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  // TODO Adding spark-avro doesn't work in tests
  @Ignore
  public void addDataPartitionedAvro() {
    createPartitionedFileTable("avro");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    Object result =
        scalarSql(
            "CALL %s.system.add_files('%s', '`avro`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    Assert.assertEquals(8L, result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加数据分区hive。 */
  @Test
  public void addDataPartitionedHive() {
    createPartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql("CALL %s.system.add_files('%s', '%s')", catalogName, tableName, sourceTableName);

    assertEquals("Procedure output must match", ImmutableList.of(row(8L, 4L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加分区到分区。 */
  @Test
  public void addPartitionToPartitioned() {
    createPartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 1 ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 删除与添加back分区。 */
  @Test
  public void deleteAndAddBackPartitioned() {
    createPartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    sql(
        "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))",
        catalogName, tableName, fileTableDir.getAbsolutePath());

    String deleteData = "DELETE FROM %s where id = 1";
    sql(deleteData, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 1 ORDER BY id", sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 添加分区到分区快照idinheritanceenabled在tworuns。 */
  @Test
  public void addPartitionToPartitionedSnapshotIdInheritanceEnabledInTwoRuns() {
    createPartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)"
            + "TBLPROPERTIES ('%s'='true')";

    sql(createIceberg, tableName, TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED);

    sql(
        "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))",
        catalogName, tableName, fileTableDir.getAbsolutePath());

    sql(
        "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 2))",
        catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id < 3 ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));

    // verify manifest file name has uuid pattern
    String manifestPath = (String) sql("select path from %s.manifests", tableName).get(0)[0];

    Pattern uuidPattern = Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8}");

    Matcher matcher = uuidPattern.matcher(manifestPath);
    Assert.assertTrue("verify manifest path has uuid", matcher.find());
  }

  /** 添加数据分区通过日期到分区。 */
  @Test
  public void addDataPartitionedByDateToPartitioned() {
    createDatePartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, date Date) USING iceberg PARTITIONED BY (date)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('date', '2021-01-01'))",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, date FROM %s WHERE date = '2021-01-01' ORDER BY id", sourceTableName),
        sql("SELECT id, name, date FROM %s ORDER BY id", tableName));
  }

  /** 添加数据分区验证分区类型inferredcorrectly。 */
  @Test
  public void addDataPartitionedVerifyPartitionTypeInferredCorrectly() {
    createTableWithTwoPartitions("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, date Date, dept String) USING iceberg PARTITIONED BY (date, dept)";

    sql(createIceberg, tableName);

    sql(
        "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('date', '2021-01-01'))",
        catalogName, tableName, fileTableDir.getAbsolutePath());

    String sqlFormat =
        "SELECT id, name, dept, date FROM %s WHERE date = '2021-01-01' and dept= '01' ORDER BY id";
    assertEquals(
        "Iceberg table contains correct data",
        sql(sqlFormat, sourceTableName),
        sql(sqlFormat, tableName));
  }

  /** 添加filtered分区到分区。 */
  @Test
  public void addFilteredPartitionsToPartitioned() {
    createCompositePartitionedTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg "
            + "PARTITIONED BY (id, dept)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 1 ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加filtered分区到分区2。 */
  @Test
  public void addFilteredPartitionsToPartitioned2() {
    createCompositePartitionedTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg "
            + "PARTITIONED BY (id, dept)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('dept', 'hr'))",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(6L, 3L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql(
            "SELECT id, name, dept, subdept FROM %s WHERE dept = 'hr' ORDER BY id",
            sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加filtered分区到分区带空值值过滤上id。 */
  @Test
  public void addFilteredPartitionsToPartitionedWithNullValueFilteringOnId() {
    createCompositePartitionedTableWithNullValueInPartitionColumn("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg "
            + "PARTITIONED BY (id, dept)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 1 ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加filtered分区到分区带空值值过滤上dept。 */
  @Test
  public void addFilteredPartitionsToPartitionedWithNullValueFilteringOnDept() {
    createCompositePartitionedTableWithNullValueInPartitionColumn("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg "
            + "PARTITIONED BY (id, dept)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('dept', 'hr'))",
            catalogName, tableName, fileTableDir.getAbsolutePath());

    assertEquals("Procedure output must match", ImmutableList.of(row(6L, 3L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql(
            "SELECT id, name, dept, subdept FROM %s WHERE dept = 'hr' ORDER BY id",
            sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加weird场景hive表。 */
  @Test
  public void addWeirdCaseHiveTable() {
    createWeirdCaseTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, `naMe` String, dept String, subdept String) USING iceberg "
            + "PARTITIONED BY (`naMe`)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '%s', map('naMe', 'John Doe'))",
            catalogName, tableName, sourceTableName);

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    /*
    While we would like to use
    SELECT id, `naMe`, dept, subdept FROM %s WHERE `naMe` = 'John Doe' ORDER BY id
    Spark does not actually handle this pushdown correctly for hive based tables and it returns 0 records
     */
    List<Object[]> expected =
        sql("SELECT id, `naMe`, dept, subdept from %s ORDER BY id", sourceTableName).stream()
            .filter(r -> r[1].equals("John Doe"))
            .collect(Collectors.toList());

    // TODO when this assert breaks Spark fixed the pushdown issue
    Assert.assertEquals(
        "If this assert breaks it means that Spark has fixed the pushdown issue",
        0,
        sql(
                "SELECT id, `naMe`, dept, subdept from %s WHERE `naMe` = 'John Doe' ORDER BY id",
                sourceTableName)
            .size());

    // Pushdown works for iceberg
    Assert.assertEquals(
        "We should be able to pushdown mixed case partition keys",
        2,
        sql(
                "SELECT id, `naMe`, dept, subdept FROM %s WHERE `naMe` = 'John Doe' ORDER BY id",
                tableName)
            .size());

    assertEquals(
        "Iceberg table contains correct data",
        expected,
        sql("SELECT id, `naMe`, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** 添加分区到分区hive。 */
  @Test
  public void addPartitionToPartitionedHive() {
    createPartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> result =
        sql(
            "CALL %s.system.add_files('%s', '%s', map('id', 1))",
            catalogName, tableName, sourceTableName);

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 1 ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s ORDER BY id", tableName));
  }

  /** invalid数据import。 */
  @Test
  public void invalidDataImport() {
    createPartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    Assertions.assertThatThrownBy(
            () ->
                scalarSql(
                    "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('id', 1))",
                    catalogName, tableName, fileTableDir.getAbsolutePath()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot use partition filter with an unpartitioned table");

    Assertions.assertThatThrownBy(
            () ->
                scalarSql(
                    "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
                    catalogName, tableName, fileTableDir.getAbsolutePath()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add partitioned files to an unpartitioned table");
  }

  /** invalid数据import分区。 */
  @Test
  public void invalidDataImportPartitioned() {
    createUnpartitionedFileTable("parquet");

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    Assertions.assertThatThrownBy(
            () ->
                scalarSql(
                    "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('x', '1', 'y', '2'))",
                    catalogName, tableName, fileTableDir.getAbsolutePath()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add data files to target table")
        .hasMessageContaining("is greater than the number of partitioned columns");

    Assertions.assertThatThrownBy(
            () ->
                scalarSql(
                    "CALL %s.system.add_files('%s', '`parquet`.`%s`', map('dept', '2'))",
                    catalogName, tableName, fileTableDir.getAbsolutePath()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add files to target table")
        .hasMessageContaining(
            "specified partition filter refers to columns that are not partitioned");
  }

  /** 添加twice。 */
  @Test
  public void addTwice() {
    createPartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> result1 =
        sql(
            "CALL %s.system.add_files("
                + "table => '%s', "
                + "source_table => '%s', "
                + "partition_filter => map('id', 1))",
            catalogName, tableName, sourceTableName);
    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result1);

    List<Object[]> result2 =
        sql(
            "CALL %s.system.add_files("
                + "table => '%s', "
                + "source_table => '%s', "
                + "partition_filter => map('id', 2))",
            catalogName, tableName, sourceTableName);
    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result2);

    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 1 ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 1 ORDER BY id", tableName));
    assertEquals(
        "Iceberg table contains correct data",
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 2 ORDER BY id", sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s WHERE id = 2 ORDER BY id", tableName));
  }

  /** duplicate数据分区。 */
  @Test
  public void duplicateDataPartitioned() {
    createPartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    sql(
        "CALL %s.system.add_files("
            + "table => '%s', "
            + "source_table => '%s', "
            + "partition_filter => map('id', 1))",
        catalogName, tableName, sourceTableName);

    Assertions.assertThatThrownBy(
            () ->
                scalarSql(
                    "CALL %s.system.add_files("
                        + "table => '%s', "
                        + "source_table => '%s', "
                        + "partition_filter => map('id', 1))",
                    catalogName, tableName, sourceTableName))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            "Cannot complete import because data files to be imported already"
                + " exist within the target table");
  }

  /** duplicate数据分区allowed。 */
  @Test
  public void duplicateDataPartitionedAllowed() {
    createPartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> result1 =
        sql(
            "CALL %s.system.add_files("
                + "table => '%s', "
                + "source_table => '%s', "
                + "partition_filter => map('id', 1))",
            catalogName, tableName, sourceTableName);

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result1);

    List<Object[]> result2 =
        sql(
            "CALL %s.system.add_files("
                + "table => '%s', "
                + "source_table => '%s', "
                + "partition_filter => map('id', 1),"
                + "check_duplicate_files => false)",
            catalogName, tableName, sourceTableName);

    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result2);

    assertEquals(
        "Iceberg table contains correct data",
        sql(
            "SELECT id, name, dept, subdept FROM %s WHERE id = 1 UNION ALL "
                + "SELECT id, name, dept, subdept FROM %s WHERE id = 1",
            sourceTableName, sourceTableName),
        sql("SELECT id, name, dept, subdept FROM %s", tableName, tableName));
  }

  /** duplicate数据非分区。 */
  @Test
  public void duplicateDataUnpartitioned() {
    createUnpartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    sql("CALL %s.system.add_files('%s', '%s')", catalogName, tableName, sourceTableName);

    Assertions.assertThatThrownBy(
            () ->
                scalarSql(
                    "CALL %s.system.add_files('%s', '%s')",
                    catalogName, tableName, sourceTableName))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith(
            "Cannot complete import because data files to be imported already"
                + " exist within the target table");
  }

  /** duplicate数据非分区allowed。 */
  @Test
  public void duplicateDataUnpartitionedAllowed() {
    createUnpartitionedHiveTable();

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";

    sql(createIceberg, tableName);

    List<Object[]> result1 =
        sql("CALL %s.system.add_files('%s', '%s')", catalogName, tableName, sourceTableName);
    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result1);

    List<Object[]> result2 =
        sql(
            "CALL %s.system.add_files("
                + "table => '%s', "
                + "source_table => '%s',"
                + "check_duplicate_files => false)",
            catalogName, tableName, sourceTableName);
    assertEquals("Procedure output must match", ImmutableList.of(row(2L, 1L)), result2);

    assertEquals(
        "Iceberg table contains correct data",
        sql(
            "SELECT * FROM (SELECT * FROM %s UNION ALL " + "SELECT * from %s) ORDER BY id",
            sourceTableName, sourceTableName),
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 测试空importdoes非throw场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEmptyImportDoesNotThrow() {

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg";
    sql(createIceberg, tableName);

    // Empty path based import
    List<Object[]> pathResult =
        sql(
            "CALL %s.system.add_files('%s', '`parquet`.`%s`')",
            catalogName, tableName, fileTableDir.getAbsolutePath());
    assertEquals("Procedure output must match", ImmutableList.of(row(0L, 0L)), pathResult);
    assertEquals(
        "Iceberg table contains no added data when importing from an empty path",
        emptyQueryResult,
        sql("SELECT * FROM %s ORDER BY id", tableName));

    // Empty table based import
    String createHive =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) STORED AS parquet";
    sql(createHive, sourceTableName);

    List<Object[]> tableResult =
        sql("CALL %s.system.add_files('%s', '%s')", catalogName, tableName, sourceTableName);
    assertEquals("Procedure output must match", ImmutableList.of(row(0L, 0L)), tableResult);
    assertEquals(
        "Iceberg table contains no added data when importing from an empty table",
        emptyQueryResult,
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  /** 测试分区import从空分区does非throw场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testPartitionedImportFromEmptyPartitionDoesNotThrow() {
    createPartitionedHiveTable();

    final int emptyPartitionId = 999;
    // Add an empty partition to the hive table
    sql(
        "ALTER TABLE %s ADD PARTITION (id = '%d') LOCATION '%d'",
        sourceTableName, emptyPartitionId, emptyPartitionId);

    String createIceberg =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING iceberg PARTITIONED BY (id)";

    sql(createIceberg, tableName);

    List<Object[]> tableResult =
        sql(
            "CALL %s.system.add_files("
                + "table => '%s', "
                + "source_table => '%s', "
                + "partition_filter => map('id', %d))",
            catalogName, tableName, sourceTableName, emptyPartitionId);

    assertEquals("Procedure output must match", ImmutableList.of(row(0L, 0L)), tableResult);
    assertEquals(
        "Iceberg table contains no added data when importing from an empty table",
        emptyQueryResult,
        sql("SELECT * FROM %s ORDER BY id", tableName));
  }

  private static final List<Object[]> emptyQueryResult = Lists.newArrayList();

  private static final StructField[] struct = {
    new StructField("id", DataTypes.IntegerType, true, Metadata.empty()),
    new StructField("name", DataTypes.StringType, true, Metadata.empty()),
    new StructField("dept", DataTypes.StringType, true, Metadata.empty()),
    new StructField("subdept", DataTypes.StringType, true, Metadata.empty())
  };

  private static final Dataset<Row> unpartitionedDF =
      spark
          .createDataFrame(
              ImmutableList.of(
                  RowFactory.create(1, "John Doe", "hr", "communications"),
                  RowFactory.create(2, "Jane Doe", "hr", "salary"),
                  RowFactory.create(3, "Matt Doe", "hr", "communications"),
                  RowFactory.create(4, "Will Doe", "facilities", "all")),
              new StructType(struct))
          .repartition(1);

  private static final Dataset<Row> singleNullRecordDF =
      spark
          .createDataFrame(
              ImmutableList.of(RowFactory.create(null, null, null, null)), new StructType(struct))
          .repartition(1);

  private static final Dataset<Row> partitionedDF =
      unpartitionedDF.select("name", "dept", "subdept", "id");

  private static final Dataset<Row> compositePartitionedDF =
      unpartitionedDF.select("name", "subdept", "id", "dept");

  private static final Dataset<Row> compositePartitionedNullRecordDF =
      singleNullRecordDF.select("name", "subdept", "id", "dept");

  private static final Dataset<Row> weirdColumnNamesDF =
      unpartitionedDF.select(
          unpartitionedDF.col("id"),
          unpartitionedDF.col("subdept"),
          unpartitionedDF.col("dept"),
          unpartitionedDF.col("name").as("naMe"));

  private static final StructField[] dateStruct = {
    new StructField("id", DataTypes.IntegerType, true, Metadata.empty()),
    new StructField("name", DataTypes.StringType, true, Metadata.empty()),
    new StructField("ts", DataTypes.DateType, true, Metadata.empty()),
    new StructField("dept", DataTypes.StringType, true, Metadata.empty()),
  };

  /** 到日期。 */
  private static java.sql.Date toDate(String value) {
    return new java.sql.Date(DateTime.parse(value).getMillis());
  }

  private static final Dataset<Row> dateDF =
      spark
          .createDataFrame(
              ImmutableList.of(
                  RowFactory.create(1, "John Doe", toDate("2021-01-01"), "01"),
                  RowFactory.create(2, "Jane Doe", toDate("2021-01-01"), "01"),
                  RowFactory.create(3, "Matt Doe", toDate("2021-01-02"), "02"),
                  RowFactory.create(4, "Will Doe", toDate("2021-01-02"), "02")),
              new StructType(dateStruct))
          .repartition(2);

  /** 创建非分区文件表。 */
  private void createUnpartitionedFileTable(String format) {
    String createParquet =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING %s LOCATION '%s'";

    sql(createParquet, sourceTableName, format, fileTableDir.getAbsolutePath());
    unpartitionedDF.write().insertInto(sourceTableName);
    unpartitionedDF.write().insertInto(sourceTableName);
  }

  /** 创建分区文件表。 */
  private void createPartitionedFileTable(String format) {
    String createParquet =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING %s PARTITIONED BY (id) "
            + "LOCATION '%s'";

    sql(createParquet, sourceTableName, format, fileTableDir.getAbsolutePath());

    partitionedDF.write().insertInto(sourceTableName);
    partitionedDF.write().insertInto(sourceTableName);
  }

  /** 创建composite分区表。 */
  private void createCompositePartitionedTable(String format) {
    String createParquet =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING %s "
            + "PARTITIONED BY (id, dept) LOCATION '%s'";
    sql(createParquet, sourceTableName, format, fileTableDir.getAbsolutePath());

    compositePartitionedDF.write().insertInto(sourceTableName);
    compositePartitionedDF.write().insertInto(sourceTableName);
  }

  /** 创建composite分区表带空值值在分区列。 */
  private void createCompositePartitionedTableWithNullValueInPartitionColumn(String format) {
    String createParquet =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) USING %s "
            + "PARTITIONED BY (id, dept) LOCATION '%s'";
    sql(createParquet, sourceTableName, format, fileTableDir.getAbsolutePath());

    Dataset<Row> unionedDF =
        compositePartitionedDF
            .unionAll(compositePartitionedNullRecordDF)
            .select("name", "subdept", "id", "dept")
            .repartition(1);

    unionedDF.write().insertInto(sourceTableName);
    unionedDF.write().insertInto(sourceTableName);
  }

  /** 创建weird场景表。 */
  private void createWeirdCaseTable() {
    String createParquet =
        "CREATE TABLE %s (id Integer, subdept String, dept String) "
            + "PARTITIONED BY (`naMe` String) STORED AS parquet";

    sql(createParquet, sourceTableName);

    weirdColumnNamesDF.write().insertInto(sourceTableName);
    weirdColumnNamesDF.write().insertInto(sourceTableName);
  }

  /** 创建非分区hive表。 */
  private void createUnpartitionedHiveTable() {
    String createHive =
        "CREATE TABLE %s (id Integer, name String, dept String, subdept String) STORED AS parquet";

    sql(createHive, sourceTableName);

    unpartitionedDF.write().insertInto(sourceTableName);
    unpartitionedDF.write().insertInto(sourceTableName);
  }

  /** 创建分区hive表。 */
  private void createPartitionedHiveTable() {
    String createHive =
        "CREATE TABLE %s (name String, dept String, subdept String) "
            + "PARTITIONED BY (id Integer) STORED AS parquet";

    sql(createHive, sourceTableName);

    partitionedDF.write().insertInto(sourceTableName);
    partitionedDF.write().insertInto(sourceTableName);
  }

  /** 创建日期分区文件表。 */
  private void createDatePartitionedFileTable(String format) {
    String createParquet =
        "CREATE TABLE %s (id Integer, name String, date Date) USING %s "
            + "PARTITIONED BY (date) LOCATION '%s'";

    sql(createParquet, sourceTableName, format, fileTableDir.getAbsolutePath());

    dateDF.select("id", "name", "ts").write().insertInto(sourceTableName);
  }

  /** 创建表带two分区。 */
  private void createTableWithTwoPartitions(String format) {
    String createParquet =
        "CREATE TABLE %s (id Integer, name String, date Date, dept String) USING %s "
            + "PARTITIONED BY (date, dept) LOCATION '%s'";

    sql(createParquet, sourceTableName, format, fileTableDir.getAbsolutePath());

    dateDF.write().insertInto(sourceTableName);
  }
}
