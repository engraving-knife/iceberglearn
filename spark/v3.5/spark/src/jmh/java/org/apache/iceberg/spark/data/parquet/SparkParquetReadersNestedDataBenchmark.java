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
package org.apache.iceberg.spark.data.parquet;

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.avro.generic.GenericData;
import org.apache.iceberg.Files;
import org.apache.iceberg.Schema;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.spark.SparkBenchmarkUtil;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.data.RandomData;
import org.apache.iceberg.spark.data.SparkParquetReaders;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.UnsafeProjection;
import org.apache.spark.sql.execution.datasources.parquet.ParquetReadSupport;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * 文件级说明：SparkParquetReadersNestedDataBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.5）。职责：对 SparkParquet读取器嵌套数据 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.SingleShotTime)
public class SparkParquetReadersNestedDataBenchmark {

  private static final DynMethods.UnboundMethod APPLY_PROJECTION =
      DynMethods.builder("apply").impl(UnsafeProjection.class, InternalRow.class).build();
  private static final Schema SCHEMA =
      new Schema(
          required(0, "id", Types.LongType.get()),
          optional(
              4,
              "nested",
              Types.StructType.of(
                  required(1, "col1", Types.StringType.get()),
                  required(2, "col2", Types.DoubleType.get()),
                  required(3, "col3", Types.LongType.get()))));
  private static final Schema PROJECTED_SCHEMA =
      new Schema(
          optional(4, "nested", Types.StructType.of(required(1, "col1", Types.StringType.get()))));
  private static final int NUM_RECORDS = 1000000;
  private File dataFile;

  /** 初始化：setupBenchmark，为基准测试准备测试数据与运行环境。 */
  @Setup
  public void setupBenchmark() throws IOException {
    dataFile = File.createTempFile("parquet-nested-data-benchmark", ".parquet");
    dataFile.delete();
    List<GenericData.Record> records = RandomData.generateList(SCHEMA, NUM_RECORDS, 0L);
    try (FileAppender<GenericData.Record> writer =
        Parquet.write(Files.localOutput(dataFile)).schema(SCHEMA).named("benchmark").build()) {
      writer.addAll(records);
    }
  }

  /** 清理：tearDownBenchmark，回收基准测试占用的临时数据与资源。 */
  @TearDown
  public void tearDownBenchmark() {
    if (dataFile != null) {
      dataFile.delete();
    }
  }

  /**
   * 基准测试场景：读取使用Iceberg读取器。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void readUsingIcebergReader(Blackhole blackhole) throws IOException {
    try (CloseableIterable<InternalRow> rows =
        Parquet.read(Files.localInput(dataFile))
            .project(SCHEMA)
            .createReaderFunc(type -> SparkParquetReaders.buildReader(SCHEMA, type))
            .build()) {

      for (InternalRow row : rows) {
        blackhole.consume(row);
      }
    }
  }

  /**
   * 基准测试场景：读取使用Iceberg读取器unsafe。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void readUsingIcebergReaderUnsafe(Blackhole blackhole) throws IOException {
    try (CloseableIterable<InternalRow> rows =
        Parquet.read(Files.localInput(dataFile))
            .project(SCHEMA)
            .createReaderFunc(type -> SparkParquetReaders.buildReader(SCHEMA, type))
            .build()) {

      Iterable<InternalRow> unsafeRows =
          Iterables.transform(
              rows, APPLY_PROJECTION.bind(SparkBenchmarkUtil.projection(SCHEMA, SCHEMA))::invoke);

      for (InternalRow row : unsafeRows) {
        blackhole.consume(row);
      }
    }
  }

  /**
   * 基准测试场景：读取使用Spark读取器。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void readUsingSparkReader(Blackhole blackhole) throws IOException {
    StructType sparkSchema = SparkSchemaUtil.convert(SCHEMA);
    try (CloseableIterable<InternalRow> rows =
        Parquet.read(Files.localInput(dataFile))
            .project(SCHEMA)
            .readSupport(new ParquetReadSupport())
            .set("org.apache.spark.sql.parquet.row.requested_schema", sparkSchema.json())
            .set("spark.sql.parquet.binaryAsString", "false")
            .set("spark.sql.parquet.int96AsTimestamp", "false")
            .set("spark.sql.caseSensitive", "false")
            .set("spark.sql.parquet.fieldId.write.enabled", "false")
            .set("spark.sql.parquet.inferTimestampNTZ.enabled", "false")
            .set("spark.sql.legacy.parquet.nanosAsLong", "false")
            .callInit()
            .build()) {

      for (InternalRow row : rows) {
        blackhole.consume(row);
      }
    }
  }

  /**
   * 基准测试场景：读取带投影使用Iceberg读取器。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void readWithProjectionUsingIcebergReader(Blackhole blackhole) throws IOException {
    try (CloseableIterable<InternalRow> rows =
        Parquet.read(Files.localInput(dataFile))
            .project(PROJECTED_SCHEMA)
            .createReaderFunc(type -> SparkParquetReaders.buildReader(PROJECTED_SCHEMA, type))
            .build()) {

      for (InternalRow row : rows) {
        blackhole.consume(row);
      }
    }
  }

  /**
   * 基准测试场景：读取带投影使用Iceberg读取器unsafe。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void readWithProjectionUsingIcebergReaderUnsafe(Blackhole blackhole) throws IOException {
    try (CloseableIterable<InternalRow> rows =
        Parquet.read(Files.localInput(dataFile))
            .project(PROJECTED_SCHEMA)
            .createReaderFunc(type -> SparkParquetReaders.buildReader(PROJECTED_SCHEMA, type))
            .build()) {

      Iterable<InternalRow> unsafeRows =
          Iterables.transform(
              rows,
              APPLY_PROJECTION.bind(
                      SparkBenchmarkUtil.projection(PROJECTED_SCHEMA, PROJECTED_SCHEMA))
                  ::invoke);

      for (InternalRow row : unsafeRows) {
        blackhole.consume(row);
      }
    }
  }

  /**
   * 基准测试场景：读取带投影使用Spark读取器。
   *
   * <p>测量该操作在当前参数组合下的吞吐与单次执行延迟， 通过 Blackhole 消费结果以避免 JIT 死代码消除，确保性能数据有效。
   */
  @Benchmark
  @Threads(1)
  public void readWithProjectionUsingSparkReader(Blackhole blackhole) throws IOException {
    StructType sparkSchema = SparkSchemaUtil.convert(PROJECTED_SCHEMA);
    try (CloseableIterable<InternalRow> rows =
        Parquet.read(Files.localInput(dataFile))
            .project(PROJECTED_SCHEMA)
            .readSupport(new ParquetReadSupport())
            .set("org.apache.spark.sql.parquet.row.requested_schema", sparkSchema.json())
            .set("spark.sql.parquet.binaryAsString", "false")
            .set("spark.sql.parquet.int96AsTimestamp", "false")
            .set("spark.sql.caseSensitive", "false")
            .set("spark.sql.parquet.inferTimestampNTZ.enabled", "false")
            .set("spark.sql.legacy.parquet.nanosAsLong", "false")
            .callInit()
            .build()) {

      for (InternalRow row : rows) {
        blackhole.consume(row);
      }
    }
  }
}
