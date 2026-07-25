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
package org.apache.iceberg.spark.actions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.spark.SparkDistributionAndOrderingUtil;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkWriteOptions;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SortOrderUtil;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.utils.DistributionAndOrderingUtils$;
import org.apache.spark.sql.connector.distributions.Distributions;
import org.apache.spark.sql.connector.distributions.OrderedDistribution;
import org.apache.spark.sql.connector.expressions.SortOrder;
import org.apache.spark.sql.internal.SQLConf;

/**
 * 基于 Spark 执行的 Iceberg 表维护动作的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkShufflingDataRewriter。
 *
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
 */
abstract class SparkShufflingDataRewriter extends SparkSizeBasedDataRewriter {

  /**
   * The number of shuffle partitions and consequently the number of output files created by the
   * Spark sort is based on the size of the input data files used in this file rewriter. Due to
   * compression, the disk file sizes may not accurately represent the size of files in the output.
   * This parameter lets the user adjust the file size used for estimating actual output data size.
   * A factor greater than 1.0 would generate more files than we would expect based on the on-disk
   * file size. A value less than 1.0 would create fewer files than we would expect based on the
   * on-disk size.
   */
  public static final String COMPRESSION_FACTOR = "compression-factor";

  public static final double COMPRESSION_FACTOR_DEFAULT = 1.0;

  private double compressionFactor;

  /** 构造 SparkShufflingDataRewriter 实例。 */
  protected SparkShufflingDataRewriter(SparkSession spark, Table table) {
    super(spark, table);
  }

  /** 执行该方法的具体逻辑。 */
  protected abstract Dataset<Row> sortedDF(Dataset<Row> df, List<FileScanTask> group);

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.<String>builder()
        .addAll(super.validOptions())
        .add(COMPRESSION_FACTOR)
        .build();
  }

  /**
   * 执行初始化。
   *
   * @param options 参数
   */
  @Override
  public void init(Map<String, String> options) {
    super.init(options);
    this.compressionFactor = compressionFactor(options);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param groupId 参数
   * @param group 参数
   */
  @Override
  public void doRewrite(String groupId, List<FileScanTask> group) {
    // the number of shuffle partition controls the number of output files
    spark().conf().set(SQLConf.SHUFFLE_PARTITIONS().key(), numShufflePartitions(group));

    Dataset<Row> scanDF =
        spark()
            .read()
            .format("iceberg")
            .option(SparkReadOptions.SCAN_TASK_SET_ID, groupId)
            .load(groupId);

    Dataset<Row> sortedDF = sortedDF(scanDF, group);

    sortedDF
        .write()
        .format("iceberg")
        .option(SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID, groupId)
        .option(SparkWriteOptions.TARGET_FILE_SIZE_BYTES, writeMaxFileSize())
        .option(SparkWriteOptions.USE_TABLE_DISTRIBUTION_AND_ORDERING, "false")
        .mode("append")
        .save(groupId);
  }

  /** 执行该方法的具体逻辑。 */
  protected Dataset<Row> sort(Dataset<Row> df, org.apache.iceberg.SortOrder sortOrder) {
    SortOrder[] ordering = SparkDistributionAndOrderingUtil.convert(sortOrder);
    OrderedDistribution distribution = Distributions.ordered(ordering);
    SQLConf conf = spark().sessionState().conf();
    LogicalPlan plan = df.logicalPlan();
    LogicalPlan sortPlan =
        DistributionAndOrderingUtils$.MODULE$.prepareQuery(distribution, ordering, plan, conf);
    return new Dataset<>(spark(), sortPlan, df.encoder());
  }

  /** 执行该方法的具体逻辑。 */
  protected org.apache.iceberg.SortOrder outputSortOrder(
      List<FileScanTask> group, org.apache.iceberg.SortOrder sortOrder) {
    boolean includePartitionColumns = !group.get(0).spec().equals(table().spec());
    if (includePartitionColumns) {
      // build in the requirement for partition sorting into our sort order
      // as the original spec for this group does not match the output spec
      return SortOrderUtil.buildSortOrder(table(), sortOrder);
    } else {
      return sortOrder;
    }
  }

  /** 执行该方法的具体逻辑。 */
  private long numShufflePartitions(List<FileScanTask> group) {
    long numOutputFiles = numOutputFiles((long) (inputSize(group) * compressionFactor));
    return Math.max(1, numOutputFiles);
  }

  /** 执行该方法的具体逻辑。 */
  private double compressionFactor(Map<String, String> options) {
    double value =
        PropertyUtil.propertyAsDouble(options, COMPRESSION_FACTOR, COMPRESSION_FACTOR_DEFAULT);
    Preconditions.checkArgument(
        value > 0, "'%s' is set to %s but must be > 0", COMPRESSION_FACTOR, value);
    return value;
  }
}
