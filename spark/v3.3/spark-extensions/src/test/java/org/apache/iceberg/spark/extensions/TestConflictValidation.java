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

import java.util.List;
import java.util.Map;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.IsolationLevel;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.SparkWriteOptions;
import org.apache.iceberg.spark.source.SimpleRecord;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.functions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestConflictValidation 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 冲突校验 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestConflictValidation extends SparkExtensionsTestBase {

  /** 测试冲突校验。 */
  public TestConflictValidation(
      String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Before
  public void createTables() {
    sql(
        "CREATE TABLE %s (id int, data string) USING iceberg "
            + "PARTITIONED BY (id)"
            + "TBLPROPERTIES"
            + "('format-version'='2',"
            + "'write.delete.mode'='merge-on-read')",
        tableName);
    sql("INSERT INTO %s VALUES (1, 'a'), (2, 'b'), (3, 'c')", tableName);
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试覆盖写过滤器serializable隔离场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwriteFilterSerializableIsolation() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);
    long snapshotId = table.currentSnapshot().snapshotId();

    List<SimpleRecord> records = Lists.newArrayList(new SimpleRecord(1, "a"));
    spark.createDataFrame(records, SimpleRecord.class).writeTo(tableName).append();

    // Validating from previous snapshot finds conflicts
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting new data files should throw exception",
        ValidationException.class,
        "Found conflicting files that can contain records matching ref(name=\"id\") == 1:",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
                .overwrite(functions.col("id").equalTo(1));
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
        .overwrite(functions.col("id").equalTo(1));
  }

  /** 测试覆盖写过滤器serializable隔离2场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwriteFilterSerializableIsolation2() throws Exception {
    List<SimpleRecord> records =
        Lists.newArrayList(new SimpleRecord(1, "a"), new SimpleRecord(1, "b"));
    spark.createDataFrame(records, SimpleRecord.class).coalesce(1).writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    long snapshotId = table.currentSnapshot().snapshotId();

    // This should add a delete file
    sql("DELETE FROM %s WHERE id='1' and data='b'", tableName);
    table.refresh();

    // Validating from previous snapshot finds conflicts
    List<SimpleRecord> conflictingRecords = Lists.newArrayList(new SimpleRecord(1, "a"));
    Dataset<Row> conflictingDf = spark.createDataFrame(conflictingRecords, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting new delete files should throw exception",
        ValidationException.class,
        "Found new conflicting delete files that can apply to records matching ref(name=\"id\") == 1:",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
                .overwrite(functions.col("id").equalTo(1));
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
        .overwrite(functions.col("id").equalTo(1));
  }

  /** 测试覆盖写过滤器serializable隔离3场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwriteFilterSerializableIsolation3() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);
    long snapshotId = table.currentSnapshot().snapshotId();

    // This should delete a data file
    sql("DELETE FROM %s WHERE id='1'", tableName);
    table.refresh();

    // Validating from previous snapshot finds conflicts
    List<SimpleRecord> conflictingRecords = Lists.newArrayList(new SimpleRecord(1, "a"));
    Dataset<Row> conflictingDf = spark.createDataFrame(conflictingRecords, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting deleted data files should throw exception",
        ValidationException.class,
        "Found conflicting deleted files that can contain records matching ref(name=\"id\") == 1:",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
                .overwrite(functions.col("id").equalTo(1));
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
        .overwrite(functions.col("id").equalTo(1));
  }

  /** 测试覆盖写过滤器no快照id校验场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwriteFilterNoSnapshotIdValidation() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);

    List<SimpleRecord> records = Lists.newArrayList(new SimpleRecord(1, "a"));
    spark.createDataFrame(records, SimpleRecord.class).writeTo(tableName).append();

    // Validating from no snapshot id defaults to beginning snapshot id and finds conflicts
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting new data files should throw exception",
        ValidationException.class,
        "Found conflicting files that can contain records matching ref(name=\"id\") == 1:",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
                .overwrite(functions.col("id").equalTo(1));
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
        .overwrite(functions.col("id").equalTo(1));
  }

  /** 测试覆盖写过滤器快照隔离场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwriteFilterSnapshotIsolation() throws Exception {
    List<SimpleRecord> records =
        Lists.newArrayList(new SimpleRecord(1, "a"), new SimpleRecord(1, "b"));
    spark.createDataFrame(records, SimpleRecord.class).coalesce(1).writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    long snapshotId = table.currentSnapshot().snapshotId();

    // This should add a delete file
    sql("DELETE FROM %s WHERE id='1' and data='b'", tableName);
    table.refresh();

    // Validating from previous snapshot finds conflicts
    List<SimpleRecord> conflictingRecords = Lists.newArrayList(new SimpleRecord(1, "a"));
    Dataset<Row> conflictingDf = spark.createDataFrame(conflictingRecords, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting new delete files should throw exception",
        ValidationException.class,
        "Found new conflicting delete files that can apply to records matching ref(name=\"id\") == 1:",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
                .overwrite(functions.col("id").equalTo(1));
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
        .overwrite(functions.col("id").equalTo(1));
  }

  /** 测试覆盖写过滤器快照隔离2场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwriteFilterSnapshotIsolation2() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);
    long snapshotId = table.currentSnapshot().snapshotId();

    List<SimpleRecord> records = Lists.newArrayList(new SimpleRecord(1, "a"));
    spark.createDataFrame(records, SimpleRecord.class).writeTo(tableName).append();

    // Validation should not fail due to conflicting data file in snapshot isolation mode
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
        .overwrite(functions.col("id").equalTo(1));
  }

  /** 测试覆盖写分区serializable隔离场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwritePartitionSerializableIsolation() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);
    final long snapshotId = table.currentSnapshot().snapshotId();

    List<SimpleRecord> records = Lists.newArrayList(new SimpleRecord(1, "a"));
    spark.createDataFrame(records, SimpleRecord.class).writeTo(tableName).append();

    // Validating from previous snapshot finds conflicts
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting deleted data files should throw exception",
        ValidationException.class,
        "Found conflicting files that can contain records matching partitions [id=1]",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
                .overwritePartitions();
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
        .overwritePartitions();
  }

  /** 测试覆盖写分区快照隔离场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwritePartitionSnapshotIsolation() throws Exception {
    List<SimpleRecord> records =
        Lists.newArrayList(new SimpleRecord(1, "a"), new SimpleRecord(1, "b"));
    spark.createDataFrame(records, SimpleRecord.class).coalesce(1).writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    final long snapshotId = table.currentSnapshot().snapshotId();

    // This should generate a delete file
    sql("DELETE FROM %s WHERE data='a'", tableName);

    // Validating from previous snapshot finds conflicts
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting deleted data files should throw exception",
        ValidationException.class,
        "Found new conflicting delete files that can apply to records matching [id=1]",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
                .overwritePartitions();
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
        .overwritePartitions();
  }

  /** 测试覆盖写分区快照隔离2场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwritePartitionSnapshotIsolation2() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);
    final long snapshotId = table.currentSnapshot().snapshotId();

    // This should delete a data file
    sql("DELETE FROM %s WHERE id='1'", tableName);

    // Validating from previous snapshot finds conflicts
    List<SimpleRecord> records = Lists.newArrayList(new SimpleRecord(1, "a"));
    spark.createDataFrame(records, SimpleRecord.class).coalesce(1).writeTo(tableName).append();
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);

    AssertHelpers.assertThrowsCause(
        "Conflicting deleted data files should throw exception",
        ValidationException.class,
        "Found conflicting deleted files that can apply to records matching [id=1]",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
                .overwritePartitions();
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long newSnapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(newSnapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
        .overwritePartitions();
  }

  /** 测试覆盖写分区快照隔离3场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwritePartitionSnapshotIsolation3() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);
    final long snapshotId = table.currentSnapshot().snapshotId();

    List<SimpleRecord> records = Lists.newArrayList(new SimpleRecord(1, "a"));
    spark.createDataFrame(records, SimpleRecord.class).writeTo(tableName).append();

    // Validation should not find conflicting data file in snapshot isolation mode
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SNAPSHOT.toString())
        .overwritePartitions();
  }

  /** 测试覆盖写分区no快照id校验场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testOverwritePartitionNoSnapshotIdValidation() throws Exception {
    Table table = validationCatalog.loadTable(tableIdent);

    List<SimpleRecord> records = Lists.newArrayList(new SimpleRecord(1, "a"));
    spark.createDataFrame(records, SimpleRecord.class).writeTo(tableName).append();

    // Validating from null snapshot is equivalent to validating from beginning
    Dataset<Row> conflictingDf = spark.createDataFrame(records, SimpleRecord.class);
    AssertHelpers.assertThrowsCause(
        "Conflicting deleted data files should throw exception",
        ValidationException.class,
        "Found conflicting files that can contain records matching partitions [id=1]",
        () -> {
          try {
            conflictingDf
                .writeTo(tableName)
                .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
                .overwritePartitions();
          } catch (NoSuchTableException e) {
            throw new RuntimeException(e);
          }
        });

    // Validating from latest snapshot should succeed
    table.refresh();
    long snapshotId = table.currentSnapshot().snapshotId();
    conflictingDf
        .writeTo(tableName)
        .option(SparkWriteOptions.VALIDATE_FROM_SNAPSHOT_ID, String.valueOf(snapshotId))
        .option(SparkWriteOptions.ISOLATION_LEVEL, IsolationLevel.SERIALIZABLE.toString())
        .overwritePartitions();
  }
}
