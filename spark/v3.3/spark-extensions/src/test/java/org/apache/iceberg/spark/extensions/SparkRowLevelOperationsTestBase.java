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

import static org.apache.iceberg.DataOperations.DELETE;
import static org.apache.iceberg.DataOperations.OVERWRITE;
import static org.apache.iceberg.SnapshotSummary.ADDED_DELETE_FILES_PROP;
import static org.apache.iceberg.SnapshotSummary.ADDED_FILES_PROP;
import static org.apache.iceberg.SnapshotSummary.CHANGED_PARTITION_COUNT_PROP;
import static org.apache.iceberg.SnapshotSummary.DELETED_FILES_PROP;
import static org.apache.iceberg.TableProperties.DEFAULT_FILE_FORMAT;
import static org.apache.iceberg.TableProperties.PARQUET_VECTORIZATION_ENABLED;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE_HASH;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE_NONE;
import static org.apache.iceberg.TableProperties.WRITE_DISTRIBUTION_MODE_RANGE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.Files;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotRef;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.SparkCatalog;
import org.apache.iceberg.spark.SparkSessionCatalog;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * 文件级说明：测试 SparkRowLevelOperationsTestBase 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 Spark行级别操作 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public abstract class SparkRowLevelOperationsTestBase extends SparkExtensionsTestBase {

  private static final Random RANDOM = ThreadLocalRandom.current();

  protected final String fileFormat;
  protected final boolean vectorized;
  protected final String distributionMode;
  protected final String branch;

  /** Spark行级别操作测试基类。 */
  public SparkRowLevelOperationsTestBase(
      String catalogName,
      String implementation,
      Map<String, String> config,
      String fileFormat,
      boolean vectorized,
      String distributionMode,
      String branch) {
    super(catalogName, implementation, config);
    this.fileFormat = fileFormat;
    this.vectorized = vectorized;
    this.distributionMode = distributionMode;
    this.branch = branch;
  }

  @Parameters(
      name =
          "catalogName = {0}, implementation = {1}, config = {2},"
              + " format = {3}, vectorized = {4}, distributionMode = {5}, branch = {6}")
  /** 参数。 */
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "testhive",
        SparkCatalog.class.getName(),
        ImmutableMap.of(
            "type", "hive",
            "default-namespace", "default"),
        "orc",
        true,
        WRITE_DISTRIBUTION_MODE_NONE,
        SnapshotRef.MAIN_BRANCH
      },
      {
        "testhive",
        SparkCatalog.class.getName(),
        ImmutableMap.of(
            "type", "hive",
            "default-namespace", "default"),
        "parquet",
        true,
        WRITE_DISTRIBUTION_MODE_NONE,
        null,
      },
      {
        "testhadoop",
        SparkCatalog.class.getName(),
        ImmutableMap.of("type", "hadoop"),
        "parquet",
        RANDOM.nextBoolean(),
        WRITE_DISTRIBUTION_MODE_HASH,
        null
      },
      {
        "spark_catalog",
        SparkSessionCatalog.class.getName(),
        ImmutableMap.of(
            "type", "hive",
            "default-namespace", "default",
            "clients", "1",
            "parquet-enabled", "false",
            "cache-enabled",
                "false" // Spark will delete tables using v1, leaving the cache out of sync
            ),
        "avro",
        false,
        WRITE_DISTRIBUTION_MODE_RANGE,
        "test"
      }
    };
  }

  protected abstract Map<String, String> extraTableProperties();

  /** init表。 */
  protected void initTable() {
    sql("ALTER TABLE %s SET TBLPROPERTIES('%s' '%s')", tableName, DEFAULT_FILE_FORMAT, fileFormat);
    sql(
        "ALTER TABLE %s SET TBLPROPERTIES('%s' '%s')",
        tableName, WRITE_DISTRIBUTION_MODE, distributionMode);

    switch (fileFormat) {
      case "parquet":
        sql(
            "ALTER TABLE %s SET TBLPROPERTIES('%s' '%b')",
            tableName, PARQUET_VECTORIZATION_ENABLED, vectorized);
        break;
      case "orc":
        Assert.assertTrue(vectorized);
        break;
      case "avro":
        Assert.assertFalse(vectorized);
        break;
    }

    Map<String, String> props = extraTableProperties();
    props.forEach(
        (prop, value) -> {
          sql("ALTER TABLE %s SET TBLPROPERTIES('%s' '%s')", tableName, prop, value);
        });
  }

  /** 创建与init表。 */
  protected void createAndInitTable(String schema) {
    createAndInitTable(schema, null);
  }

  /** 创建与init表。 */
  protected void createAndInitTable(String schema, String jsonData) {
    createAndInitTable(schema, "", jsonData);
  }

  /** 创建与init表。 */
  protected void createAndInitTable(String schema, String partitioning, String jsonData) {
    sql("CREATE TABLE %s (%s) USING iceberg %s", tableName, schema, partitioning);
    initTable();

    if (jsonData != null) {
      try {
        Dataset<Row> ds = toDS(schema, jsonData);
        ds.coalesce(1).writeTo(tableName).append();
        createBranchIfNeeded();
      } catch (NoSuchTableException e) {
        throw new RuntimeException("Failed to write data", e);
      }
    }
  }

  /** 追加。 */
  protected void append(String table, String jsonData) {
    append(table, null, jsonData);
  }

  /** 追加。 */
  protected void append(String table, String schema, String jsonData) {
    try {
      Dataset<Row> ds = toDS(schema, jsonData);
      ds.coalesce(1).writeTo(table).append();
    } catch (NoSuchTableException e) {
      throw new RuntimeException("Failed to write data", e);
    }
  }

  /** 创建或替换视图。 */
  protected void createOrReplaceView(String name, String jsonData) {
    createOrReplaceView(name, null, jsonData);
  }

  /** 创建或替换视图。 */
  protected void createOrReplaceView(String name, String schema, String jsonData) {
    Dataset<Row> ds = toDS(schema, jsonData);
    ds.createOrReplaceTempView(name);
  }

  /** 创建或替换视图。 */
  protected <T> void createOrReplaceView(String name, List<T> data, Encoder<T> encoder) {
    spark.createDataset(data, encoder).createOrReplaceTempView(name);
  }

  /** 到ds。 */
  private Dataset<Row> toDS(String schema, String jsonData) {
    List<String> jsonRows =
        Arrays.stream(jsonData.split("\n"))
            .filter(str -> str.trim().length() > 0)
            .collect(Collectors.toList());
    Dataset<String> jsonDS = spark.createDataset(jsonRows, Encoders.STRING());

    if (schema != null) {
      return spark.read().schema(schema).json(jsonDS);
    } else {
      return spark.read().json(jsonDS);
    }
  }

  /** 校验删除。 */
  protected void validateDelete(
      Snapshot snapshot, String changedPartitionCount, String deletedDataFiles) {
    validateSnapshot(snapshot, DELETE, changedPartitionCount, deletedDataFiles, null, null);
  }

  /** 校验复制上写。 */
  protected void validateCopyOnWrite(
      Snapshot snapshot,
      String changedPartitionCount,
      String deletedDataFiles,
      String addedDataFiles) {
    validateSnapshot(
        snapshot, OVERWRITE, changedPartitionCount, deletedDataFiles, null, addedDataFiles);
  }

  /** 校验合并上读。 */
  protected void validateMergeOnRead(
      Snapshot snapshot,
      String changedPartitionCount,
      String addedDeleteFiles,
      String addedDataFiles) {
    validateSnapshot(
        snapshot, OVERWRITE, changedPartitionCount, null, addedDeleteFiles, addedDataFiles);
  }

  /** 校验快照。 */
  protected void validateSnapshot(
      Snapshot snapshot,
      String operation,
      String changedPartitionCount,
      String deletedDataFiles,
      String addedDeleteFiles,
      String addedDataFiles) {
    Assert.assertEquals("Operation must match", operation, snapshot.operation());
    validateProperty(snapshot, CHANGED_PARTITION_COUNT_PROP, changedPartitionCount);
    validateProperty(snapshot, DELETED_FILES_PROP, deletedDataFiles);
    validateProperty(snapshot, ADDED_DELETE_FILES_PROP, addedDeleteFiles);
    validateProperty(snapshot, ADDED_FILES_PROP, addedDataFiles);
  }

  /** 校验属性。 */
  protected void validateProperty(Snapshot snapshot, String property, Set<String> expectedValues) {
    String actual = snapshot.summary().get(property);
    Assert.assertTrue(
        "Snapshot property "
            + property
            + " has unexpected value, actual = "
            + actual
            + ", expected one of : "
            + String.join(",", expectedValues),
        expectedValues.contains(actual));
  }

  /** 校验属性。 */
  protected void validateProperty(Snapshot snapshot, String property, String expectedValue) {
    String actual = snapshot.summary().get(property);
    Assert.assertEquals(
        "Snapshot property " + property + " has unexpected value.", expectedValue, actual);
  }

  /** 辅助方法：sleep。 */
  protected void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /** 写数据文件。 */
  protected DataFile writeDataFile(Table table, List<GenericRecord> records) {
    try {
      OutputFile file = Files.localOutput(temp.newFile());

      DataWriter<GenericRecord> dataWriter =
          Parquet.writeData(file)
              .forTable(table)
              .createWriterFunc(GenericParquetWriter::buildWriter)
              .overwrite()
              .build();

      try {
        for (GenericRecord record : records) {
          dataWriter.write(record);
        }
      } finally {
        dataWriter.close();
      }

      return dataWriter.toDataFile();

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 提交target。 */
  @Override
  protected String commitTarget() {
    return branch == null ? tableName : String.format("%s.branch_%s", tableName, branch);
  }

  /** 辅助方法：selectTarget。 */
  @Override
  protected String selectTarget() {
    return branch == null ? tableName : String.format("%s VERSION AS OF '%s'", tableName, branch);
  }

  /** 创建分支ifneeded。 */
  protected void createBranchIfNeeded() {
    if (branch != null && !branch.equals(SnapshotRef.MAIN_BRANCH)) {
      sql("ALTER TABLE %s CREATE BRANCH %s", tableName, branch);
    }
  }
}
