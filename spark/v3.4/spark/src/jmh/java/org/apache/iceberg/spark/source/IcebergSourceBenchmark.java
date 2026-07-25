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
package org.apache.iceberg.spark.source;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.UpdateProperties;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;
import org.apache.spark.sql.types.StructType;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * 文件级说明：IcebergSourceBenchmark 性能基准测试。
 *
 * <p>所属模块：iceberg-spark（v3.4）。职责：对 Iceberg数据源 相关读写操作进行 JMH 性能基准测试， 衡量吞吐与单次执行延迟等性能指标。
 *
 * <p>测试策略：基于 JMH 框架，使用 @Benchmark 方法配合 @Setup/@TearDown 准备与回收测试数据， 通过 Blackhole 消费结果以避免 JIT
 * 死代码消除，覆盖不同参数组合下的性能表现。
 */
@Fork(1)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.SingleShotTime)
public abstract class IcebergSourceBenchmark {

  private final Configuration hadoopConf = initHadoopConf();
  private final Table table = initTable();
  private SparkSession spark;

  protected abstract Configuration initHadoopConf();

  /** 辅助方法：Hadoop配置。 */
  protected final Configuration hadoopConf() {
    return hadoopConf;
  }

  protected abstract Table initTable();

  /** 辅助方法：表。 */
  protected final Table table() {
    return table;
  }

  /** 辅助方法：Spark。 */
  protected final SparkSession spark() {
    return spark;
  }

  /** 辅助方法：新建表路径。 */
  protected String newTableLocation() {
    String tmpDir = hadoopConf.get("hadoop.tmp.dir");
    Path tablePath = new Path(tmpDir, "spark-iceberg-table-" + UUID.randomUUID());
    return tablePath.toString();
  }

  /** 辅助方法：数据路径。 */
  protected String dataLocation() {
    Map<String, String> properties = table.properties();
    return properties.getOrDefault(
        TableProperties.WRITE_DATA_LOCATION, String.format("%s/data", table.location()));
  }

  /** 辅助方法：清理文件。 */
  protected void cleanupFiles() throws IOException {
    try (FileSystem fileSystem = FileSystem.get(hadoopConf)) {
      Path dataPath = new Path(dataLocation());
      fileSystem.delete(dataPath, true);
      Path tablePath = new Path(table.location());
      fileSystem.delete(tablePath, true);
    }
  }

  /** 辅助方法：初始化Spark。 */
  protected void setupSpark(boolean enableDictionaryEncoding) {
    SparkSession.Builder builder = SparkSession.builder().config("spark.ui.enabled", false);
    if (!enableDictionaryEncoding) {
      builder
          .config("parquet.dictionary.page.size", "1")
          .config("parquet.enable.dictionary", false)
          .config(TableProperties.PARQUET_DICT_SIZE_BYTES, "1");
    }
    builder.master("local");
    spark = builder.getOrCreate();
    Configuration sparkHadoopConf = spark.sessionState().newHadoopConf();
    hadoopConf.forEach(entry -> sparkHadoopConf.set(entry.getKey(), entry.getValue()));
  }

  /** 辅助方法：初始化Spark。 */
  protected void setupSpark() {
    setupSpark(false);
  }

  /** 辅助方法：tear下推Spark。 */
  protected void tearDownSpark() {
    spark.stop();
  }

  /** 辅助方法：物化。 */
  protected void materialize(Dataset<?> ds) {
    ds.queryExecution().toRdd().toJavaRDD().foreach(record -> {});
  }

  /** 辅助方法：物化。 */
  protected void materialize(Dataset<?> ds, Blackhole blackhole) {
    blackhole.consume(ds.queryExecution().toRdd().toJavaRDD().count());
  }

  /** 辅助方法：追加as文件。 */
  protected void appendAsFile(Dataset<Row> ds) {
    // ensure the schema is precise (including nullability)
    StructType sparkSchema = SparkSchemaUtil.convert(table.schema());
    spark
        .createDataFrame(ds.rdd(), sparkSchema)
        .coalesce(1)
        .write()
        .format("iceberg")
        .mode(SaveMode.Append)
        .save(table.location());
  }

  /** 辅助方法：带SQL配置。 */
  protected void withSQLConf(Map<String, String> conf, Action action) {
    SQLConf sqlConf = SQLConf.get();

    Map<String, String> currentConfValues = Maps.newHashMap();
    conf.keySet()
        .forEach(
            confKey -> {
              if (sqlConf.contains(confKey)) {
                String currentConfValue = sqlConf.getConfString(confKey);
                currentConfValues.put(confKey, currentConfValue);
              }
            });

    conf.forEach(
        (confKey, confValue) -> {
          if (SQLConf.isStaticConfigKey(confKey)) {
            throw new RuntimeException("Cannot modify the value of a static config: " + confKey);
          }
          sqlConf.setConfString(confKey, confValue);
        });

    try {
      action.invoke();
    } finally {
      conf.forEach(
          (confKey, confValue) -> {
            if (currentConfValues.containsKey(confKey)) {
              sqlConf.setConfString(confKey, currentConfValues.get(confKey));
            } else {
              sqlConf.unsetConf(confKey);
            }
          });
    }
  }

  /** 辅助方法：带表属性。 */
  protected void withTableProperties(Map<String, String> props, Action action) {
    Map<String, String> tableProps = table.properties();
    Map<String, String> currentPropValues = Maps.newHashMap();
    props
        .keySet()
        .forEach(
            propKey -> {
              if (tableProps.containsKey(propKey)) {
                String currentPropValue = tableProps.get(propKey);
                currentPropValues.put(propKey, currentPropValue);
              }
            });

    UpdateProperties updateProperties = table.updateProperties();
    props.forEach(updateProperties::set);
    updateProperties.commit();

    try {
      action.invoke();
    } finally {
      UpdateProperties restoreProperties = table.updateProperties();
      props.forEach(
          (propKey, propValue) -> {
            if (currentPropValues.containsKey(propKey)) {
              restoreProperties.set(propKey, currentPropValues.get(propKey));
            } else {
              restoreProperties.remove(propKey);
            }
          });
      restoreProperties.commit();
    }
  }

  /** 辅助方法：文件格式。 */
  protected FileFormat fileFormat() {
    throw new UnsupportedOperationException("Unsupported file format");
  }
}
