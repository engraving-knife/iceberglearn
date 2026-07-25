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
package org.apache.iceberg.spark;

import static org.apache.spark.sql.functions.current_date;
import static org.apache.spark.sql.functions.date_add;
import static org.apache.spark.sql.functions.expr;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.RowLevelOperationMode;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.catalyst.parser.ParseException;
import org.apache.spark.sql.internal.SQLConf;
import org.apache.spark.sql.types.StructType;
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
import org.openjdk.jmh.annotations.Warmup;

/**
 * 文件级说明：MergeCardinalityCheckBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.3）。职责：对 合并基数检查 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.SingleShotTime)
public class MergeCardinalityCheckBenchmark {

  private static final String TABLE_NAME = "test_table";
  private static final int NUM_FILES = 5;
  private static final int NUM_ROWS_PER_FILE = 1_000_000;
  private static final int NUM_UNMATCHED_RECORDS_PER_MERGE = 100_000;

  private final Configuration hadoopConf = new Configuration();
  private SparkSession spark;
  private long originalSnapshotId;

  /** 初始化：setupBenchmark，为基准测试准备测试数据与运行环境。 */
  @Setup
  public void setupBenchmark() throws NoSuchTableException, ParseException {
    setupSpark();
    initTable();
    appendData();

    Table table = Spark3Util.loadIcebergTable(spark, TABLE_NAME);
    this.originalSnapshotId = table.currentSnapshot().snapshotId();
  }

  /** 清理：tearDownBenchmark，回收基准测试占用的临时数据与资源。 */
  @TearDown
  public void tearDownBenchmark() {
    tearDownSpark();
    dropTable();
  }

  /**
   * 基准测试场景：复制上写入合并基数检查10percent更新。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void copyOnWriteMergeCardinalityCheck10PercentUpdates() {
    runBenchmark(RowLevelOperationMode.COPY_ON_WRITE, 0.1);
  }

  /**
   * 基准测试场景：复制上写入合并基数检查30percent更新。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void copyOnWriteMergeCardinalityCheck30PercentUpdates() {
    runBenchmark(RowLevelOperationMode.COPY_ON_WRITE, 0.3);
  }

  /**
   * 基准测试场景：复制上写入合并基数检查90percent更新。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void copyOnWriteMergeCardinalityCheck90PercentUpdates() {
    runBenchmark(RowLevelOperationMode.COPY_ON_WRITE, 0.9);
  }

  /**
   * 基准测试场景：合并上读取合并基数检查10percent更新。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void mergeOnReadMergeCardinalityCheck10PercentUpdates() {
    runBenchmark(RowLevelOperationMode.MERGE_ON_READ, 0.1);
  }

  /**
   * 基准测试场景：合并上读取合并基数检查30percent更新。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void mergeOnReadMergeCardinalityCheck30PercentUpdates() {
    runBenchmark(RowLevelOperationMode.MERGE_ON_READ, 0.3);
  }

  /**
   * 基准测试场景：合并上读取合并基数检查90percent更新。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void mergeOnReadMergeCardinalityCheck90PercentUpdates() {
    runBenchmark(RowLevelOperationMode.MERGE_ON_READ, 0.9);
  }

  /** 辅助方法：run基准测试。 */
  private void runBenchmark(RowLevelOperationMode mode, double updatePercentage) {
    sql(
        "ALTER TABLE %s SET TBLPROPERTIES ('%s' '%s')",
        TABLE_NAME, TableProperties.MERGE_MODE, mode.modeName());

    Dataset<Long> insertDataDF = spark.range(-NUM_UNMATCHED_RECORDS_PER_MERGE, 0, 1);
    Dataset<Long> updateDataDF = spark.range((long) (updatePercentage * NUM_ROWS_PER_FILE));
    Dataset<Long> sourceDF = updateDataDF.union(insertDataDF);
    sourceDF.createOrReplaceTempView("source");

    sql(
        "MERGE INTO %s t USING source s "
            + "ON t.id = s.id "
            + "WHEN MATCHED THEN "
            + " UPDATE SET stringCol = 'invalid' "
            + "WHEN NOT MATCHED THEN "
            + " INSERT (id, intCol, floatCol, doubleCol, decimalCol, dateCol, timestampCol, stringCol) "
            + "   VALUES (s.id, null, null, null, null, null, null, 'new')",
        TABLE_NAME);

    sql(
        "CALL system.rollback_to_snapshot(table => '%s', snapshot_id => %dL)",
        TABLE_NAME, originalSnapshotId);
  }

  /** 辅助方法：初始化Spark。 */
  private void setupSpark() {
    this.spark =
        SparkSession.builder()
            .config("spark.ui.enabled", false)
            .config("spark.sql.extensions", IcebergSparkSessionExtensions.class.getName())
            .config("spark.sql.catalog.spark_catalog", SparkSessionCatalog.class.getName())
            .config("spark.sql.catalog.spark_catalog.type", "hadoop")
            .config("spark.sql.catalog.spark_catalog.warehouse", newWarehouseDir())
            .config(SQLConf.DYNAMIC_PARTITION_PRUNING_ENABLED().key(), "false")
            .config(SQLConf.ADAPTIVE_EXECUTION_ENABLED().key(), "false")
            .config(SQLConf.SHUFFLE_PARTITIONS().key(), "2")
            .master("local")
            .getOrCreate();
  }

  /** 辅助方法：tear下推Spark。 */
  private void tearDownSpark() {
    spark.stop();
  }

  /** 辅助方法：init表。 */
  private void initTable() {
    sql(
        "CREATE TABLE %s ( "
            + " id LONG, intCol INT, floatCol FLOAT, doubleCol DOUBLE, "
            + " decimalCol DECIMAL(20, 5), dateCol DATE, timestampCol TIMESTAMP, "
            + " stringCol STRING)"
            + "USING iceberg "
            + "TBLPROPERTIES ("
            + " '%s' '%s',"
            + " '%s' '%d',"
            + " '%s' '%d')",
        TABLE_NAME,
        TableProperties.MERGE_DISTRIBUTION_MODE,
        DistributionMode.NONE.modeName(),
        TableProperties.SPLIT_OPEN_FILE_COST,
        Integer.MAX_VALUE,
        TableProperties.FORMAT_VERSION,
        2);

    sql("ALTER TABLE %s WRITE ORDERED BY id", TABLE_NAME);
  }

  /** 辅助方法：删除表。 */
  private void dropTable() {
    sql("DROP TABLE IF EXISTS %s PURGE", TABLE_NAME);
  }

  /** 辅助方法：追加数据。 */
  private void appendData() throws NoSuchTableException {
    for (int fileNum = 1; fileNum <= NUM_FILES; fileNum++) {
      Dataset<Row> inputDF =
          spark
              .range(NUM_ROWS_PER_FILE)
              .withColumn("intCol", expr("CAST(id AS INT)"))
              .withColumn("floatCol", expr("CAST(id AS FLOAT)"))
              .withColumn("doubleCol", expr("CAST(id AS DOUBLE)"))
              .withColumn("decimalCol", expr("CAST(id AS DECIMAL(20, 5))"))
              .withColumn("dateCol", date_add(current_date(), fileNum))
              .withColumn("timestampCol", expr("TO_TIMESTAMP(dateCol)"))
              .withColumn("stringCol", expr("CAST(dateCol AS STRING)"));
      appendAsFile(inputDF);
    }
  }

  /** 辅助方法：追加as文件。 */
  private void appendAsFile(Dataset<Row> df) throws NoSuchTableException {
    // ensure the schema is precise (including nullability)
    StructType sparkSchema = spark.table(TABLE_NAME).schema();
    spark.createDataFrame(df.rdd(), sparkSchema).coalesce(1).writeTo(TABLE_NAME).append();
  }

  /** 辅助方法：新建warehousedir。 */
  private String newWarehouseDir() {
    return hadoopConf.get("hadoop.tmp.dir") + UUID.randomUUID();
  }

  /** 辅助方法：SQL。 */
  @FormatMethod
  private void sql(@FormatString String query, Object... args) {
    spark.sql(String.format(query, args));
  }
}
