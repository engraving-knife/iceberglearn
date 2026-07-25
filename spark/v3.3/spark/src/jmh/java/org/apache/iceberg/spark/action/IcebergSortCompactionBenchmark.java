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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.current_date;
import static org.apache.spark.sql.functions.date_add;
import static org.apache.spark.sql.functions.expr;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortDirection;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.BinPackStrategy;
import org.apache.iceberg.relocated.com.google.common.io.Files;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.SparkSessionCatalog;
import org.apache.iceberg.spark.actions.SparkActions;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.types.DataTypes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;

/**
 * 文件级说明：IcebergSortCompactionBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.3）。职责：对 Iceberg排序compaction 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
@Fork(1)
@State(Scope.Benchmark)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.SingleShotTime)
@Timeout(time = 1000, timeUnit = TimeUnit.HOURS)
public class IcebergSortCompactionBenchmark {

  private static final String[] NAMESPACE = new String[] {"default"};
  private static final String NAME = "sortbench";
  private static final Identifier IDENT = Identifier.of(NAMESPACE, NAME);
  private static final int NUM_FILES = 8;
  private static final long NUM_ROWS = 7500000L;
  private static final long UNIQUE_VALUES = NUM_ROWS / 4;

  private final Configuration hadoopConf = initHadoopConf();
  private SparkSession spark;

  /** 初始化：setupBench，为基准测试准备测试数据与运行环境。 */
  @Setup
  public void setupBench() {
    setupSpark();
  }

  /** 清理：teardownBench，回收基准测试占用的临时数据与资源。 */
  @TearDown
  public void teardownBench() {
    tearDownSpark();
  }

  /** 初始化：setupIteration，为基准测试准备测试数据与运行环境。 */
  @Setup(Level.Iteration)
  public void setupIteration() {
    initTable();
    appendData();
  }

  /** 清理：cleanUpIteration，回收基准测试占用的临时数据与资源。 */
  @TearDown(Level.Iteration)
  public void cleanUpIteration() throws IOException {
    cleanupFiles();
  }

  /**
   * 基准测试场景：排序int。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void sortInt() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .sort(
            SortOrder.builderFor(table().schema())
                .sortBy("intCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .build())
        .execute();
  }

  /**
   * 基准测试场景：排序int2。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void sortInt2() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .sort(
            SortOrder.builderFor(table().schema())
                .sortBy("intCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol2", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .build())
        .execute();
  }

  /**
   * 基准测试场景：排序int3。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void sortInt3() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .sort(
            SortOrder.builderFor(table().schema())
                .sortBy("intCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol2", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol3", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol4", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .build())
        .execute();
  }

  /**
   * 基准测试场景：排序int4。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void sortInt4() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .sort(
            SortOrder.builderFor(table().schema())
                .sortBy("intCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol2", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol3", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol4", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .build())
        .execute();
  }

  /**
   * 基准测试场景：排序字符串。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void sortString() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .sort(
            SortOrder.builderFor(table().schema())
                .sortBy("stringCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .build())
        .execute();
  }

  /**
   * 基准测试场景：排序four列。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void sortFourColumns() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .sort(
            SortOrder.builderFor(table().schema())
                .sortBy("stringCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("dateCol", SortDirection.DESC, NullOrder.NULLS_FIRST)
                .sortBy("doubleCol", SortDirection.DESC, NullOrder.NULLS_FIRST)
                .build())
        .execute();
  }

  /**
   * 基准测试场景：排序six列。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void sortSixColumns() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .sort(
            SortOrder.builderFor(table().schema())
                .sortBy("stringCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("intCol", SortDirection.ASC, NullOrder.NULLS_FIRST)
                .sortBy("dateCol", SortDirection.DESC, NullOrder.NULLS_FIRST)
                .sortBy("timestampCol", SortDirection.DESC, NullOrder.NULLS_FIRST)
                .sortBy("doubleCol", SortDirection.DESC, NullOrder.NULLS_FIRST)
                .sortBy("longCol", SortDirection.DESC, NullOrder.NULLS_FIRST)
                .build())
        .execute();
  }

  /**
   * 基准测试场景：z排序int。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void zSortInt() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .zOrder("intCol")
        .execute();
  }

  /**
   * 基准测试场景：z排序int2。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void zSortInt2() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .zOrder("intCol", "intCol2")
        .execute();
  }

  /**
   * 基准测试场景：z排序int3。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void zSortInt3() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .zOrder("intCol", "intCol2", "intCol3")
        .execute();
  }

  /**
   * 基准测试场景：z排序int4。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void zSortInt4() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .zOrder("intCol", "intCol2", "intCol3", "intCol4")
        .execute();
  }

  /**
   * 基准测试场景：z排序字符串。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void zSortString() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .zOrder("stringCol")
        .execute();
  }

  /**
   * 基准测试场景：z排序four列。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void zSortFourColumns() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .zOrder("stringCol", "intCol", "dateCol", "doubleCol")
        .execute();
  }

  /**
   * 基准测试场景：z排序six列。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void zSortSixColumns() {
    SparkActions.get()
        .rewriteDataFiles(table())
        .option(BinPackStrategy.REWRITE_ALL, "true")
        .zOrder("stringCol", "intCol", "dateCol", "timestampCol", "doubleCol", "longCol")
        .execute();
  }

  /** 辅助方法：initHadoop配置。 */
  protected Configuration initHadoopConf() {
    return new Configuration();
  }

  /** 辅助方法：init表。 */
  protected final void initTable() {
    Schema schema =
        new Schema(
            required(1, "longCol", Types.LongType.get()),
            required(2, "intCol", Types.IntegerType.get()),
            required(3, "intCol2", Types.IntegerType.get()),
            required(4, "intCol3", Types.IntegerType.get()),
            required(5, "intCol4", Types.IntegerType.get()),
            required(6, "floatCol", Types.FloatType.get()),
            optional(7, "doubleCol", Types.DoubleType.get()),
            optional(8, "dateCol", Types.DateType.get()),
            optional(9, "timestampCol", Types.TimestampType.withZone()),
            optional(10, "stringCol", Types.StringType.get()));

    SparkSessionCatalog catalog;
    try {
      catalog =
          (SparkSessionCatalog) Spark3Util.catalogAndIdentifier(spark(), "spark_catalog").catalog();
      catalog.dropTable(IDENT);
      catalog.createTable(
          IDENT, SparkSchemaUtil.convert(schema), new Transform[0], Collections.emptyMap());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** 辅助方法：追加数据。 */
  private void appendData() {
    Dataset<Row> df =
        spark()
            .range(0, NUM_ROWS * NUM_FILES, 1, NUM_FILES)
            .drop("id")
            .withColumn("longCol", new RandomGeneratingUDF(UNIQUE_VALUES).randomLongUDF().apply())
            .withColumn(
                "intCol",
                new RandomGeneratingUDF(UNIQUE_VALUES)
                    .randomLongUDF()
                    .apply()
                    .cast(DataTypes.IntegerType))
            .withColumn(
                "intCol2",
                new RandomGeneratingUDF(UNIQUE_VALUES)
                    .randomLongUDF()
                    .apply()
                    .cast(DataTypes.IntegerType))
            .withColumn(
                "intCol3",
                new RandomGeneratingUDF(UNIQUE_VALUES)
                    .randomLongUDF()
                    .apply()
                    .cast(DataTypes.IntegerType))
            .withColumn(
                "intCol4",
                new RandomGeneratingUDF(UNIQUE_VALUES)
                    .randomLongUDF()
                    .apply()
                    .cast(DataTypes.IntegerType))
            .withColumn(
                "floatCol",
                new RandomGeneratingUDF(UNIQUE_VALUES)
                    .randomLongUDF()
                    .apply()
                    .cast(DataTypes.FloatType))
            .withColumn(
                "doubleCol",
                new RandomGeneratingUDF(UNIQUE_VALUES)
                    .randomLongUDF()
                    .apply()
                    .cast(DataTypes.DoubleType))
            .withColumn("dateCol", date_add(current_date(), col("intCol").mod(NUM_FILES)))
            .withColumn("timestampCol", expr("TO_TIMESTAMP(dateCol)"))
            .withColumn("stringCol", new RandomGeneratingUDF(UNIQUE_VALUES).randomString().apply());
    writeData(df);
  }

  /** 辅助方法：写入数据。 */
  private void writeData(Dataset<Row> df) {
    df.write().format("iceberg").mode(SaveMode.Append).save(NAME);
  }

  /** 辅助方法：表。 */
  protected final Table table() {
    try {
      return Spark3Util.loadIcebergTable(spark(), NAME);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** 辅助方法：Spark。 */
  protected final SparkSession spark() {
    return spark;
  }

  /** 辅助方法：获取目录warehouse。 */
  protected String getCatalogWarehouse() {
    String location = Files.createTempDir().getAbsolutePath() + "/" + UUID.randomUUID() + "/";
    return location;
  }

  /** 辅助方法：清理文件。 */
  protected void cleanupFiles() throws IOException {
    spark.sql("DROP TABLE IF EXISTS " + NAME);
  }

  /** 辅助方法：初始化Spark。 */
  protected void setupSpark() {
    SparkSession.Builder builder =
        SparkSession.builder()
            .config(
                "spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkSessionCatalog")
            .config("spark.sql.catalog.spark_catalog.type", "hadoop")
            .config("spark.sql.catalog.spark_catalog.warehouse", getCatalogWarehouse())
            .master("local[*]");
    spark = builder.getOrCreate();
    Configuration sparkHadoopConf = spark.sessionState().newHadoopConf();
    hadoopConf.forEach(entry -> sparkHadoopConf.set(entry.getKey(), entry.getValue()));
  }

  /** 辅助方法：tear下推Spark。 */
  protected void tearDownSpark() {
    spark.stop();
  }
}
