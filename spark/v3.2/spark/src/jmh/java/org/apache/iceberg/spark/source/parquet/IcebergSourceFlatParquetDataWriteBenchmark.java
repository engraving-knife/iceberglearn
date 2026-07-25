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
package org.apache.iceberg.spark.source.parquet;

import static org.apache.spark.sql.functions.expr;

import java.io.IOException;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.source.IcebergSourceFlatDataBenchmark;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.internal.SQLConf;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

/**
 * 文件级说明：IcebergSourceFlatParquetDataWriteBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.2）。职责：对 Iceberg数据源扁平Parquet数据写入 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
public class IcebergSourceFlatParquetDataWriteBenchmark extends IcebergSourceFlatDataBenchmark {

  private static final int NUM_ROWS = 5000000;

  /** 初始化：setupBenchmark，为基准测试准备测试数据与运行环境。 */
  @Setup
  public void setupBenchmark() {
    setupSpark();
  }

  /** 清理：tearDownBenchmark，回收基准测试占用的临时数据与资源。 */
  @TearDown
  public void tearDownBenchmark() throws IOException {
    tearDownSpark();
    cleanupFiles();
  }

  /**
   * 基准测试场景：写入Iceberg。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void writeIceberg() {
    String tableLocation = table().location();
    benchmarkData().write().format("iceberg").mode(SaveMode.Append).save(tableLocation);
  }

  /**
   * 基准测试场景：写入文件数据源。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void writeFileSource() {
    Map<String, String> conf = Maps.newHashMap();
    conf.put(SQLConf.PARQUET_COMPRESSION().key(), "gzip");
    withSQLConf(conf, () -> benchmarkData().write().mode(SaveMode.Append).parquet(dataLocation()));
  }

  /** 辅助方法：基准测试数据。 */
  private Dataset<Row> benchmarkData() {
    return spark()
        .range(NUM_ROWS)
        .withColumnRenamed("id", "longCol")
        .withColumn("intCol", expr("CAST(longCol AS INT)"))
        .withColumn("floatCol", expr("CAST(longCol AS FLOAT)"))
        .withColumn("doubleCol", expr("CAST(longCol AS DOUBLE)"))
        .withColumn("decimalCol", expr("CAST(longCol AS DECIMAL(20, 5))"))
        .withColumn("dateCol", expr("DATE_ADD(CURRENT_DATE(), (intCol % 20))"))
        .withColumn("timestampCol", expr("TO_TIMESTAMP(dateCol)"))
        .withColumn("stringCol", expr("CAST(dateCol AS STRING)"))
        .coalesce(1);
  }
}
