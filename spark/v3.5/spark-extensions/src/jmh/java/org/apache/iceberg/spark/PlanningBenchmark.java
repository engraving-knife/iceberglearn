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

import static org.apache.iceberg.PlanningMode.DISTRIBUTED;
import static org.apache.iceberg.PlanningMode.LOCAL;
import static org.apache.spark.sql.functions.lit;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.BatchScan;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileMetadata;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.PlanningMode;
import org.apache.iceberg.RowDelta;
import org.apache.iceberg.RowLevelOperationMode;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SparkDistributedDataScan;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.data.RandomData;
import org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.catalyst.parser.ParseException;
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
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * 文件级说明：PlanningBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.5）。职责：对 规划 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Timeout(time = 20, timeUnit = TimeUnit.MINUTES)
@BenchmarkMode(Mode.SingleShotTime)
public class PlanningBenchmark {

  private static final String TABLE_NAME = "test_table";
  private static final String PARTITION_COLUMN = "ss_ticket_number";
  private static final int PARTITION_VALUE = 10;
  private static final String SORT_KEY_COLUMN = "ss_sold_date_sk";
  private static final int SORT_KEY_VALUE = 5;

  private static final Expression SORT_KEY_PREDICATE =
      Expressions.equal(SORT_KEY_COLUMN, SORT_KEY_VALUE);
  private static final Expression PARTITION_PREDICATE =
      Expressions.equal(PARTITION_COLUMN, PARTITION_VALUE);
  private static final Expression PARTITION_AND_SORT_KEY_PREDICATE =
      Expressions.and(PARTITION_PREDICATE, SORT_KEY_PREDICATE);

  private static final int NUM_PARTITIONS = 30;
  private static final int NUM_REAL_DATA_FILES_PER_PARTITION = 25;
  private static final int NUM_REPLICA_DATA_FILES_PER_PARTITION = 50_000;
  private static final int NUM_DELETE_FILES_PER_PARTITION = 50;
  private static final int NUM_ROWS_PER_DATA_FILE = 500;

  private final Configuration hadoopConf = new Configuration();
  private SparkSession spark;
  private Table table;

  /** 初始化：setupBenchmark，为基准测试准备测试数据与运行环境。 */
  @Setup
  public void setupBenchmark() throws NoSuchTableException, ParseException {
    setupSpark();
    initTable();
    initDataAndDeletes();
  }

  /** 清理：tearDownBenchmark，回收基准测试占用的临时数据与资源。 */
  @TearDown
  public void tearDownBenchmark() {
    dropTable();
    tearDownSpark();
  }

  /**
   * 基准测试场景：local规划带分区and最小最大过滤。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void localPlanningWithPartitionAndMinMaxFilter(Blackhole blackhole) {
    BatchScan scan = table.newBatchScan();
    List<ScanTask> fileTasks = planFilesWithoutColumnStats(scan, PARTITION_AND_SORT_KEY_PREDICATE);
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：distributed规划带分区and最小最大过滤。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void distributedPlanningWithPartitionAndMinMaxFilter(Blackhole blackhole) {
    BatchScan scan = newDistributedScan(DISTRIBUTED, DISTRIBUTED);
    List<ScanTask> fileTasks = planFilesWithoutColumnStats(scan, PARTITION_AND_SORT_KEY_PREDICATE);
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：local规划带最小最大过滤。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void localPlanningWithMinMaxFilter(Blackhole blackhole) {
    BatchScan scan = table.newBatchScan();
    List<ScanTask> fileTasks = planFilesWithoutColumnStats(scan, SORT_KEY_PREDICATE);
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：distributed规划带最小最大过滤。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void distributedPlanningWithMinMaxFilter(Blackhole blackhole) {
    BatchScan scan = newDistributedScan(DISTRIBUTED, DISTRIBUTED);
    List<ScanTask> fileTasks = planFilesWithoutColumnStats(scan, SORT_KEY_PREDICATE);
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：local规划无过滤。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void localPlanningWithoutFilter(Blackhole blackhole) {
    BatchScan scan = table.newBatchScan();
    List<ScanTask> fileTasks = planFilesWithoutColumnStats(scan, Expressions.alwaysTrue());
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：distributed规划无过滤。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void distributedPlanningWithoutFilter(Blackhole blackhole) {
    BatchScan scan = newDistributedScan(DISTRIBUTED, DISTRIBUTED);
    List<ScanTask> fileTasks = planFilesWithoutColumnStats(scan, Expressions.alwaysTrue());
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：local规划无过滤带stats。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void localPlanningWithoutFilterWithStats(Blackhole blackhole) {
    BatchScan scan = table.newBatchScan();
    List<ScanTask> fileTasks = planFilesWithColumnStats(scan, Expressions.alwaysTrue());
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：distributed规划无过滤带stats。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void distributedPlanningWithoutFilterWithStats(Blackhole blackhole) {
    BatchScan scan = newDistributedScan(DISTRIBUTED, DISTRIBUTED);
    List<ScanTask> fileTasks = planFilesWithColumnStats(scan, Expressions.alwaysTrue());
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：distributed数据local删除规划无过滤带stats。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void distributedDataLocalDeletesPlanningWithoutFilterWithStats(Blackhole blackhole) {
    BatchScan scan = newDistributedScan(DISTRIBUTED, LOCAL);
    List<ScanTask> fileTasks = planFilesWithColumnStats(scan, Expressions.alwaysTrue());
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：local数据distributed删除规划无过滤带stats。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void localDataDistributedDeletesPlanningWithoutFilterWithStats(Blackhole blackhole) {
    BatchScan scan = newDistributedScan(LOCAL, DISTRIBUTED);
    List<ScanTask> fileTasks = planFilesWithColumnStats(scan, Expressions.alwaysTrue());
    blackhole.consume(fileTasks);
  }

  /**
   * 基准测试场景：local规划通过distributed扫描无过滤带stats。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void localPlanningViaDistributedScanWithoutFilterWithStats(Blackhole blackhole) {
    BatchScan scan = newDistributedScan(LOCAL, LOCAL);
    List<ScanTask> fileTasks = planFilesWithColumnStats(scan, Expressions.alwaysTrue());
    blackhole.consume(fileTasks);
  }

  /** 辅助方法：初始化Spark。 */
  private void setupSpark() {
    this.spark =
        SparkSession.builder()
            .config("spark.ui.enabled", false)
            .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            .config("spark.driver.maxResultSize", "8G")
            .config("spark.sql.extensions", IcebergSparkSessionExtensions.class.getName())
            .config("spark.sql.catalog.spark_catalog", SparkSessionCatalog.class.getName())
            .config("spark.sql.catalog.spark_catalog.type", "hadoop")
            .config("spark.sql.catalog.spark_catalog.warehouse", newWarehouseDir())
            .master("local[*]")
            .getOrCreate();
  }

  /** 辅助方法：tear下推Spark。 */
  private void tearDownSpark() {
    spark.stop();
  }

  /** 辅助方法：init表。 */
  private void initTable() throws NoSuchTableException, ParseException {
    sql(
        "CREATE TABLE %s ( "
            + " `ss_sold_date_sk` INT, "
            + " `ss_sold_time_sk` INT, "
            + " `ss_item_sk` INT, "
            + " `ss_customer_sk` STRING, "
            + " `ss_cdemo_sk` STRING, "
            + " `ss_hdemo_sk` STRING, "
            + " `ss_addr_sk` STRING, "
            + " `ss_store_sk` STRING, "
            + " `ss_promo_sk` STRING, "
            + " `ss_ticket_number` INT, "
            + " `ss_quantity` STRING, "
            + " `ss_wholesale_cost` STRING, "
            + " `ss_list_price` STRING, "
            + " `ss_sales_price` STRING, "
            + " `ss_ext_discount_amt` STRING, "
            + " `ss_ext_sales_price` STRING, "
            + " `ss_ext_wholesale_cost` STRING, "
            + " `ss_ext_list_price` STRING, "
            + " `ss_ext_tax` STRING, "
            + " `ss_coupon_amt` STRING, "
            + " `ss_net_paid` STRING, "
            + " `ss_net_paid_inc_tax` STRING, "
            + " `ss_net_profit` STRING "
            + ")"
            + "USING iceberg "
            + "PARTITIONED BY (%s) "
            + "TBLPROPERTIES ("
            + " '%s' '%b',"
            + " '%s' '%s',"
            + " '%s' '%d')",
        TABLE_NAME,
        PARTITION_COLUMN,
        TableProperties.MANIFEST_MERGE_ENABLED,
        false,
        TableProperties.DELETE_MODE,
        RowLevelOperationMode.MERGE_ON_READ.modeName(),
        TableProperties.FORMAT_VERSION,
        2);

    this.table = Spark3Util.loadIcebergTable(spark, TABLE_NAME);
  }

  /** 辅助方法：删除表。 */
  private void dropTable() {
    sql("DROP TABLE IF EXISTS %s PURGE", TABLE_NAME);
  }

  /** 辅助方法：加载added数据文件。 */
  private DataFile loadAddedDataFile() {
    table.refresh();

    Iterable<DataFile> dataFiles = table.currentSnapshot().addedDataFiles(table.io());
    return Iterables.getOnlyElement(dataFiles);
  }

  /** 辅助方法：加载added删除文件。 */
  private DeleteFile loadAddedDeleteFile() {
    table.refresh();

    Iterable<DeleteFile> deleteFiles = table.currentSnapshot().addedDeleteFiles(table.io());
    return Iterables.getOnlyElement(deleteFiles);
  }

  /** 辅助方法：init数据and删除。 */
  private void initDataAndDeletes() throws NoSuchTableException {
    Schema schema = table.schema();
    PartitionSpec spec = table.spec();
    LocationProvider locations = table.locationProvider();

    for (int partitionOrdinal = 0; partitionOrdinal < NUM_PARTITIONS; partitionOrdinal++) {
      Dataset<Row> inputDF =
          randomDataDF(schema, NUM_ROWS_PER_DATA_FILE)
              .drop(PARTITION_COLUMN)
              .withColumn(PARTITION_COLUMN, lit(partitionOrdinal))
              .drop(SORT_KEY_COLUMN)
              .withColumn(SORT_KEY_COLUMN, lit(Integer.MIN_VALUE));

      for (int fileOrdinal = 0; fileOrdinal < NUM_REAL_DATA_FILES_PER_PARTITION; fileOrdinal++) {
        appendAsFile(inputDF);
      }

      DataFile dataFile = loadAddedDataFile();

      sql(
          "DELETE FROM %s WHERE ss_item_sk IS NULL AND %s = %d",
          TABLE_NAME, PARTITION_COLUMN, partitionOrdinal);

      DeleteFile deleteFile = loadAddedDeleteFile();

      AppendFiles append = table.newFastAppend();

      for (int fileOrdinal = 0; fileOrdinal < NUM_REPLICA_DATA_FILES_PER_PARTITION; fileOrdinal++) {
        String replicaFileName = UUID.randomUUID() + "-replica.parquet";
        DataFile replicaDataFile =
            DataFiles.builder(spec)
                .copy(dataFile)
                .withPath(locations.newDataLocation(spec, dataFile.partition(), replicaFileName))
                .build();
        append.appendFile(replicaDataFile);
      }

      append.commit();

      RowDelta rowDelta = table.newRowDelta();

      for (int fileOrdinal = 0; fileOrdinal < NUM_DELETE_FILES_PER_PARTITION; fileOrdinal++) {
        String replicaFileName = UUID.randomUUID() + "-replica.parquet";
        DeleteFile replicaDeleteFile =
            FileMetadata.deleteFileBuilder(spec)
                .copy(deleteFile)
                .withPath(locations.newDataLocation(spec, deleteFile.partition(), replicaFileName))
                .build();
        rowDelta.addDeletes(replicaDeleteFile);
      }

      rowDelta.commit();

      Dataset<Row> sortedInputDF =
          randomDataDF(schema, NUM_ROWS_PER_DATA_FILE)
              .drop(SORT_KEY_COLUMN)
              .withColumn(SORT_KEY_COLUMN, lit(SORT_KEY_VALUE))
              .drop(PARTITION_COLUMN)
              .withColumn(PARTITION_COLUMN, lit(partitionOrdinal));
      appendAsFile(sortedInputDF);
    }
  }

  /** 辅助方法：追加as文件。 */
  private void appendAsFile(Dataset<Row> df) throws NoSuchTableException {
    df.coalesce(1).writeTo(TABLE_NAME).append();
  }

  /** 辅助方法：新建warehousedir。 */
  private String newWarehouseDir() {
    return hadoopConf.get("hadoop.tmp.dir") + UUID.randomUUID();
  }

  /** 辅助方法：random数据df。 */
  private Dataset<Row> randomDataDF(Schema schema, int numRows) {
    Iterable<InternalRow> rows = RandomData.generateSpark(schema, numRows, 0);
    JavaSparkContext context = JavaSparkContext.fromSparkContext(spark.sparkContext());
    JavaRDD<InternalRow> rowRDD = context.parallelize(Lists.newArrayList(rows));
    StructType rowSparkType = SparkSchemaUtil.convert(schema);
    return spark.internalCreateDataFrame(JavaRDD.toRDD(rowRDD), rowSparkType, false);
  }

  /** 辅助方法：计划文件无列stats。 */
  private List<ScanTask> planFilesWithoutColumnStats(BatchScan scan, Expression predicate) {
    return planFiles(scan, predicate, false);
  }

  /** 辅助方法：计划文件带列stats。 */
  private List<ScanTask> planFilesWithColumnStats(BatchScan scan, Expression predicate) {
    return planFiles(scan, predicate, true);
  }

  /** 辅助方法：计划文件。 */
  private List<ScanTask> planFiles(BatchScan scan, Expression predicate, boolean withColumnStats) {
    table.refresh();

    BatchScan configuredScan = scan.filter(predicate);

    if (withColumnStats) {
      configuredScan = scan.includeColumnStats();
    }

    try (CloseableIterable<ScanTask> fileTasks = configuredScan.planFiles()) {
      return Lists.newArrayList(fileTasks);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 辅助方法：新建distributed扫描。 */
  private BatchScan newDistributedScan(PlanningMode dataMode, PlanningMode deleteMode) {
    table
        .updateProperties()
        .set(TableProperties.DATA_PLANNING_MODE, dataMode.modeName())
        .set(TableProperties.DELETE_PLANNING_MODE, deleteMode.modeName())
        .commit();
    SparkReadConf readConf = new SparkReadConf(spark, table, ImmutableMap.of());
    return new SparkDistributedDataScan(spark, table, readConf);
  }

  /** 辅助方法：SQL。 */
  @FormatMethod
  private void sql(@FormatString String query, Object... args) {
    spark.sql(String.format(query, args));
  }
}
