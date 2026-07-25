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
package org.apache.iceberg.spark.source.parquet.vectorized;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.date_add;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.pmod;
import static org.apache.spark.sql.functions.to_date;
import static org.apache.spark.sql.functions.to_timestamp;

import java.util.Map;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.types.DataTypes;
import org.openjdk.jmh.annotations.Setup;

/**
 * 文件级说明：VectorizedReadDictionaryEncodedFlatParquetDataBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.4）。职责：对 向量化读取字典编码扁平Parquet数据 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
public class VectorizedReadDictionaryEncodedFlatParquetDataBenchmark
    extends VectorizedReadFlatParquetDataBenchmark {

  /** 初始化：setupBenchmark，为基准测试准备测试数据与运行环境。 */
  @Setup
  @Override
  public void setupBenchmark() {
    setupSpark(true);
    appendData();
  }

  /** 辅助方法：Parquet写入属性。 */
  @Override
  Map<String, String> parquetWriteProps() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(TableProperties.METADATA_COMPRESSION, "gzip");
    return properties;
  }

  /** 辅助方法：追加数据。 */
  @Override
  void appendData() {
    Dataset<Row> df = idDF();
    df = withLongColumnDictEncoded(df);
    df = withIntColumnDictEncoded(df);
    df = withFloatColumnDictEncoded(df);
    df = withDoubleColumnDictEncoded(df);
    df = withBigDecimalColumnNotDictEncoded(df); // no dictionary for fixed len binary in Parquet v1
    df = withDecimalColumnDictEncoded(df);
    df = withDateColumnDictEncoded(df);
    df = withTimestampColumnDictEncoded(df);
    df = withStringColumnDictEncoded(df);
    df = df.drop("id");
    df.write().format("iceberg").mode(SaveMode.Append).save(table().location());
  }

  /** 辅助方法：mod列。 */
  private static Column modColumn() {
    return pmod(col("id"), lit(9));
  }

  /** 辅助方法：idDF。 */
  private Dataset<Row> idDF() {
    return spark().range(0, NUM_ROWS_PER_FILE * NUM_FILES, 1, NUM_FILES).toDF();
  }

  /** 辅助方法：带长整型列dict编码。 */
  private static Dataset<Row> withLongColumnDictEncoded(Dataset<Row> df) {
    return df.withColumn("longCol", modColumn().cast(DataTypes.LongType));
  }

  /** 辅助方法：带int列dict编码。 */
  private static Dataset<Row> withIntColumnDictEncoded(Dataset<Row> df) {
    return df.withColumn("intCol", modColumn().cast(DataTypes.IntegerType));
  }

  /** 辅助方法：带单精度列dict编码。 */
  private static Dataset<Row> withFloatColumnDictEncoded(Dataset<Row> df) {
    return df.withColumn("floatCol", modColumn().cast(DataTypes.FloatType));
  }

  /** 辅助方法：带双精度列dict编码。 */
  private static Dataset<Row> withDoubleColumnDictEncoded(Dataset<Row> df) {
    return df.withColumn("doubleCol", modColumn().cast(DataTypes.DoubleType));
  }

  /** 辅助方法：带big十进制列非dict编码。 */
  private static Dataset<Row> withBigDecimalColumnNotDictEncoded(Dataset<Row> df) {
    return df.withColumn("bigDecimalCol", modColumn().cast("decimal(20,5)"));
  }

  /** 辅助方法：带十进制列dict编码。 */
  private static Dataset<Row> withDecimalColumnDictEncoded(Dataset<Row> df) {
    return df.withColumn("decimalCol", modColumn().cast("decimal(18,5)"));
  }

  /** 辅助方法：带日期列dict编码。 */
  private static Dataset<Row> withDateColumnDictEncoded(Dataset<Row> df) {
    Column days = modColumn().cast(DataTypes.ShortType);
    return df.withColumn("dateCol", date_add(to_date(lit("04/12/2019"), "MM/dd/yyyy"), days));
  }

  /** 辅助方法：带时间戳列dict编码。 */
  private static Dataset<Row> withTimestampColumnDictEncoded(Dataset<Row> df) {
    Column days = modColumn().cast(DataTypes.ShortType);
    return df.withColumn(
        "timestampCol", to_timestamp(date_add(to_date(lit("04/12/2019"), "MM/dd/yyyy"), days)));
  }

  /** 辅助方法：带字符串列dict编码。 */
  private static Dataset<Row> withStringColumnDictEncoded(Dataset<Row> df) {
    return df.withColumn("stringCol", modColumn().cast(DataTypes.StringType));
  }
}
