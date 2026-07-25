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
package org.apache.iceberg.spark.action;

import static org.apache.spark.sql.functions.lit;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.DeleteOrphanFiles;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.io.Files;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkSessionCatalog;
import org.apache.iceberg.spark.actions.SparkActions;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * 文件级说明：DeleteOrphanFilesBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.4）。职责：对 删除孤儿文件 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.SingleShotTime)
@Timeout(time = 1000, timeUnit = TimeUnit.HOURS)
public class DeleteOrphanFilesBenchmark {

  private static final String TABLE_NAME = "delete_orphan_perf";
  private static final int NUM_SNAPSHOTS = 1000;
  private static final int NUM_FILES = 1000;

  private SparkSession spark;
  private final List<String> validAndOrphanPaths = Lists.newArrayList();
  private Table table;

  /** 初始化：setupBench，为基准测试准备测试数据与运行环境。 */
  @Setup
  public void setupBench() {
    setupSpark();
    initTable();
    appendData();
    addOrphans();
  }

  /** 清理：teardownBench，回收基准测试占用的临时数据与资源。 */
  @TearDown
  public void teardownBench() {
    tearDownSpark();
  }

  /**
   * 基准测试场景：测试删除孤儿文件。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void testDeleteOrphanFiles(Blackhole blackhole) {
    Dataset<Row> validAndOrphanPathsDF =
        spark
            .createDataset(validAndOrphanPaths, Encoders.STRING())
            .withColumnRenamed("value", "file_path")
            .withColumn("last_modified", lit(new Timestamp(10000)));

    DeleteOrphanFiles.Result results =
        SparkActions.get(spark)
            .deleteOrphanFiles(table())
            .compareToFileList(validAndOrphanPathsDF)
            .execute();
    blackhole.consume(results);
  }

  /** 辅助方法：init表。 */
  private void initTable() {
    spark.sql(
        String.format(
            "CREATE TABLE %s(id INT, name STRING)"
                + " USING ICEBERG"
                + " TBLPROPERTIES ( 'format-version' = '2')",
            TABLE_NAME));
  }

  /** 辅助方法：追加数据。 */
  private void appendData() {
    String location = table().location();
    PartitionSpec partitionSpec = table().spec();

    for (int i = 0; i < NUM_SNAPSHOTS; i++) {
      AppendFiles appendFiles = table().newFastAppend();
      for (int j = 0; j < NUM_FILES; j++) {
        String path = String.format("%s/path/to/data-%d-%d.parquet", location, i, j);
        validAndOrphanPaths.add(path);
        DataFile dataFile =
            DataFiles.builder(partitionSpec)
                .withPath(path)
                .withFileSizeInBytes(10)
                .withRecordCount(1)
                .build();
        appendFiles.appendFile(dataFile);
      }
      appendFiles.commit();
    }
  }

  /** 辅助方法：添加孤儿。 */
  private void addOrphans() {
    String location = table.location();
    // Generate 10% orphan files
    int orphanFileCount = (NUM_FILES * NUM_SNAPSHOTS) / 10;
    for (int i = 0; i < orphanFileCount; i++) {
      validAndOrphanPaths.add(
          String.format("%s/path/to/data-%s.parquet", location, UUID.randomUUID()));
    }
  }

  /** 辅助方法：表。 */
  private Table table() {
    if (table == null) {
      try {
        table = Spark3Util.loadIcebergTable(spark, TABLE_NAME);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return table;
  }

  /** 辅助方法：目录warehouse。 */
  private String catalogWarehouse() {
    return Files.createTempDir().getAbsolutePath() + "/" + UUID.randomUUID() + "/";
  }

  /** 辅助方法：初始化Spark。 */
  private void setupSpark() {
    SparkSession.Builder builder =
        SparkSession.builder()
            .config("spark.sql.catalog.spark_catalog", SparkSessionCatalog.class.getName())
            .config("spark.sql.catalog.spark_catalog.type", "hadoop")
            .config("spark.sql.catalog.spark_catalog.warehouse", catalogWarehouse())
            .master("local");
    spark = builder.getOrCreate();
  }

  /** 辅助方法：tear下推Spark。 */
  private void tearDownSpark() {
    spark.stop();
  }
}
