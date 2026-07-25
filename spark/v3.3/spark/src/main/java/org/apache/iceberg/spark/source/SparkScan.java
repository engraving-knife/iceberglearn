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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.Table;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.SparkUtil;
import org.apache.iceberg.spark.source.metrics.NumDeletes;
import org.apache.iceberg.spark.source.metrics.NumSplits;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.metric.CustomMetric;
import org.apache.spark.sql.connector.read.Batch;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.Statistics;
import org.apache.spark.sql.connector.read.SupportsReportStatistics;
import org.apache.spark.sql.connector.read.streaming.MicroBatchStream;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的扫描组件，负责构建和执行数据读取计划。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkScan。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
abstract class SparkScan implements Scan, SupportsReportStatistics {
  private static final Logger LOG = LoggerFactory.getLogger(SparkScan.class);

  private final JavaSparkContext sparkContext;
  private final Table table;
  private final SparkReadConf readConf;
  private final boolean caseSensitive;
  private final Schema expectedSchema;
  private final List<Expression> filterExpressions;
  private final boolean readTimestampWithoutZone;
  private final String branch;

  // lazy variables
  private StructType readSchema;

  SparkScan(
      SparkSession spark,
      Table table,
      SparkReadConf readConf,
      Schema expectedSchema,
      List<Expression> filters) {
    Schema snapshotSchema = SnapshotUtil.schemaFor(table, readConf.branch());
    SparkSchemaUtil.validateMetadataColumnReferences(snapshotSchema, expectedSchema);

    this.sparkContext = JavaSparkContext.fromSparkContext(spark.sparkContext());
    this.table = table;
    this.readConf = readConf;
    this.caseSensitive = readConf.caseSensitive();
    this.expectedSchema = expectedSchema;
    this.filterExpressions = filters != null ? filters : Collections.emptyList();
    this.readTimestampWithoutZone = readConf.handleTimestampWithoutZone();
    this.branch = readConf.branch();
  }

  /** 执行该方法的具体逻辑。 */
  protected Table table() {
    return table;
  }

  /** 执行该方法的具体逻辑。 */
  protected String branch() {
    return branch;
  }

  /** 执行该方法的具体逻辑。 */
  protected boolean caseSensitive() {
    return caseSensitive;
  }

  /** 执行该方法的具体逻辑。 */
  protected Schema expectedSchema() {
    return expectedSchema;
  }

  /** 按条件过滤。 */
  protected List<Expression> filterExpressions() {
    return filterExpressions;
  }

  /** 执行该方法的具体逻辑。 */
  protected Types.StructType groupingKeyType() {
    return Types.StructType.of();
  }

  /** 执行该方法的具体逻辑。 */
  protected abstract List<? extends ScanTaskGroup<?>> taskGroups();

  /**
   * 转换为batch。
   *
   * @return 结果对象
   */
  @Override
  public Batch toBatch() {
    /** 执行该方法的具体逻辑。 */
    return new SparkBatch(
        sparkContext, table, readConf, groupingKeyType(), taskGroups(), expectedSchema, hashCode());
  }

  /**
   * 转换为microbatchstream。
   *
   * @param checkpointLocation 参数
   * @return 结果对象
   */
  @Override
  public MicroBatchStream toMicroBatchStream(String checkpointLocation) {
    /** 执行该方法的具体逻辑。 */
    return new SparkMicroBatchStream(
        sparkContext, table, readConf, expectedSchema, checkpointLocation);
  }

  /**
   * 读取数据。
   *
   * @return 结果对象
   */
  @Override
  public StructType readSchema() {
    if (readSchema == null) {
      Preconditions.checkArgument(
          readTimestampWithoutZone || !SparkUtil.hasTimestampWithoutZone(expectedSchema),
          SparkUtil.TIMESTAMP_WITHOUT_TIMEZONE_ERROR);
      this.readSchema = SparkSchemaUtil.convert(expectedSchema);
    }
    return readSchema;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Statistics estimateStatistics() {
    return estimateStatistics(SnapshotUtil.latestSnapshot(table, branch));
  }

  /** 执行该方法的具体逻辑。 */
  protected Statistics estimateStatistics(Snapshot snapshot) {
    // its a fresh table, no data
    if (snapshot == null) {
      /** 执行该方法的具体逻辑。 */
      return new Stats(0L, 0L);
    }

    // estimate stats using snapshot summary only for partitioned tables
    // (metadata tables are unpartitioned)
    if (!table.spec().isUnpartitioned() && filterExpressions.isEmpty()) {
      LOG.debug(
          "Using snapshot {} metadata to estimate statistics for table {}",
          snapshot.snapshotId(),
          table.name());
      long totalRecords = totalRecords(snapshot);
      /** 执行该方法的具体逻辑。 */
      return new Stats(SparkSchemaUtil.estimateSize(readSchema(), totalRecords), totalRecords);
    }

    long rowsCount = taskGroups().stream().mapToLong(ScanTaskGroup::estimatedRowsCount).sum();
    long sizeInBytes = SparkSchemaUtil.estimateSize(readSchema(), rowsCount);
    /** 执行该方法的具体逻辑。 */
    return new Stats(sizeInBytes, rowsCount);
  }

  /** 执行该方法的具体逻辑。 */
  private long totalRecords(Snapshot snapshot) {
    Map<String, String> summary = snapshot.summary();
    return PropertyUtil.propertyAsLong(summary, SnapshotSummary.TOTAL_RECORDS_PROP, Long.MAX_VALUE);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    String groupingKeyFieldNamesAsString =
        groupingKeyType().fields().stream()
            .map(Types.NestedField::name)
            .collect(Collectors.joining(", "));

    return String.format(
        "%s (branch=%s) [filters=%s, groupedBy=%s]",
        table(), branch(), Spark3Util.describe(filterExpressions), groupingKeyFieldNamesAsString);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public CustomMetric[] supportedCustomMetrics() {
    return new CustomMetric[] {new NumSplits(), new NumDeletes()};
  }
}
