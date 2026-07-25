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
import java.util.function.Function;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkFunctionCatalog;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkWriteOptions;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SortOrderUtil;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.OrderAwareCoalesce;
import org.apache.spark.sql.catalyst.plans.logical.OrderAwareCoalescer;
import org.apache.spark.sql.connector.distributions.Distribution;
import org.apache.spark.sql.connector.distributions.Distributions;
import org.apache.spark.sql.connector.distributions.OrderedDistribution;
import org.apache.spark.sql.connector.expressions.SortOrder;
import org.apache.spark.sql.connector.write.RequiresDistributionAndOrdering;
import org.apache.spark.sql.execution.datasources.v2.DistributionAndOrderingUtils$;
import scala.Option;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：需要 shuffle 的数据文件重写器基类，为排序类重写提供 shuffle + sort 的通用骨架。
 *
 * <p>设计意图：在重写前按排序键 shuffle 数据，保证写出文件内部有序。
 *
 * <p>上下游关系：被 SparkSortDataRewriter / SparkZOrderDataRewriter 继承。
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

  /**
   * The number of shuffle partitions to use for each output file. By default, this file rewriter
   * assumes each shuffle partition would become a separate output file. Attempting to generate
   * large output files of 512 MB or higher may strain the memory resources of the cluster as such
   * rewrites would require lots of Spark memory. This parameter can be used to further divide up
   * the data which will end up in a single file. For example, if the target file size is 2 GB, but
   * the cluster can only handle shuffles of 512 MB, this parameter could be set to 4. Iceberg will
   * use a custom coalesce operation to stitch these sorted partitions back together into a single
   * sorted file.
   *
   * <p>Note using this parameter requires enabling Iceberg Spark session extensions.
   */
  public static final String SHUFFLE_PARTITIONS_PER_FILE = "shuffle-partitions-per-file";

  public static final int SHUFFLE_PARTITIONS_PER_FILE_DEFAULT = 1;

  private double compressionFactor;
  private int numShufflePartitionsPerFile;

  protected SparkShufflingDataRewriter(SparkSession spark, Table table) {
    super(spark, table);
  }
  /** 执行 sortOrder 相关操作。 */
  protected abstract org.apache.iceberg.SortOrder sortOrder();
  /** 执行 sortedDF 相关操作。 */
  protected abstract Dataset<Row> sortedDF(
      Dataset<Row> df, Function<Dataset<Row>, Dataset<Row>> sortFunc);
  /** 执行 validOptions 相关操作。 */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.<String>builder()
        .addAll(super.validOptions())
        .add(COMPRESSION_FACTOR)
        .add(SHUFFLE_PARTITIONS_PER_FILE)
        .build();
  }
  /** 初始化。 */
  @Override
  public void init(Map<String, String> options) {
    super.init(options);
    this.compressionFactor = compressionFactor(options);
    this.numShufflePartitionsPerFile = numShufflePartitionsPerFile(options);
  }
  /** 执行 doRewrite 相关操作。 */
  @Override
  public void doRewrite(String groupId, List<FileScanTask> group) {
    Dataset<Row> scanDF =
        spark()
            .read()
            .format("iceberg")
            .option(SparkReadOptions.SCAN_TASK_SET_ID, groupId)
            .load(groupId);

    Dataset<Row> sortedDF = sortedDF(scanDF, sortFunction(group));

    sortedDF
        .write()
        .format("iceberg")
        .option(SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID, groupId)
        .option(SparkWriteOptions.TARGET_FILE_SIZE_BYTES, writeMaxFileSize())
        .option(SparkWriteOptions.USE_TABLE_DISTRIBUTION_AND_ORDERING, "false")
        .mode("append")
        .save(groupId);
  }
  /** 执行 sortFunction 相关操作。 */
  private Function<Dataset<Row>, Dataset<Row>> sortFunction(List<FileScanTask> group) {
    SortOrder[] ordering = Spark3Util.toOrdering(outputSortOrder(group));
    int numShufflePartitions = numShufflePartitions(group);
    return (df) -> transformPlan(df, plan -> sortPlan(plan, ordering, numShufflePartitions));
  }
  /** 执行 sortPlan 相关操作。 */
  private LogicalPlan sortPlan(LogicalPlan plan, SortOrder[] ordering, int numShufflePartitions) {
    SparkFunctionCatalog catalog = SparkFunctionCatalog.get();
    OrderedWrite write = new OrderedWrite(ordering, numShufflePartitions);
    LogicalPlan sortPlan =
        DistributionAndOrderingUtils$.MODULE$.prepareQuery(write, plan, Option.apply(catalog));

    if (numShufflePartitionsPerFile == 1) {
      return sortPlan;
    } else {
      OrderAwareCoalescer coalescer = new OrderAwareCoalescer(numShufflePartitionsPerFile);
      int numOutputPartitions = numShufflePartitions / numShufflePartitionsPerFile;
      return new OrderAwareCoalesce(numOutputPartitions, coalescer, sortPlan);
    }
  }
  /** 执行 transformPlan 相关操作。 */
  private Dataset<Row> transformPlan(Dataset<Row> df, Function<LogicalPlan, LogicalPlan> func) {
    return new Dataset<>(spark(), func.apply(df.logicalPlan()), df.encoder());
  }
  /** 执行 outputSortOrder 相关操作。 */
  private org.apache.iceberg.SortOrder outputSortOrder(List<FileScanTask> group) {
    boolean includePartitionColumns = !group.get(0).spec().equals(table().spec());
    if (includePartitionColumns) {
      // build in the requirement for partition sorting into our sort order
      // as the original spec for this group does not match the output spec
      return SortOrderUtil.buildSortOrder(table(), sortOrder());
    } else {
      return sortOrder();
    }
  }
  /** 执行 numShufflePartitions 相关操作。 */
  private int numShufflePartitions(List<FileScanTask> group) {
    int numOutputFiles = (int) numOutputFiles((long) (inputSize(group) * compressionFactor));
    return Math.max(1, numOutputFiles * numShufflePartitionsPerFile);
  }
  /** 执行 compressionFactor 相关操作。 */
  private double compressionFactor(Map<String, String> options) {
    double value =
        PropertyUtil.propertyAsDouble(options, COMPRESSION_FACTOR, COMPRESSION_FACTOR_DEFAULT);
    Preconditions.checkArgument(
        value > 0, "'%s' is set to %s but must be > 0", COMPRESSION_FACTOR, value);
    return value;
  }
  /** 执行 numShufflePartitionsPerFile 相关操作。 */
  private int numShufflePartitionsPerFile(Map<String, String> options) {
    int value =
        PropertyUtil.propertyAsInt(
            options, SHUFFLE_PARTITIONS_PER_FILE, SHUFFLE_PARTITIONS_PER_FILE_DEFAULT);
    Preconditions.checkArgument(
        value > 0, "'%s' is set to %s but must be > 0", SHUFFLE_PARTITIONS_PER_FILE, value);
    Preconditions.checkArgument(
        value == 1 || Spark3Util.extensionsEnabled(spark()),
        "Using '%s' requires enabling Iceberg Spark session extensions",
        SHUFFLE_PARTITIONS_PER_FILE);
    return value;
  }

  private static class OrderedWrite implements RequiresDistributionAndOrdering {
    private final OrderedDistribution distribution;
    private final SortOrder[] ordering;
    private final int numShufflePartitions;

    OrderedWrite(SortOrder[] ordering, int numShufflePartitions) {
      this.distribution = Distributions.ordered(ordering);
      this.ordering = ordering;
      this.numShufflePartitions = numShufflePartitions;
    }
    /** 执行 requiredDistribution 相关操作。 */
    @Override
    public Distribution requiredDistribution() {
      return distribution;
    }
    /** 执行 distributionStrictlyRequired 相关操作。 */
    @Override
    public boolean distributionStrictlyRequired() {
      return true;
    }
    /** 执行 requiredNumPartitions 相关操作。 */
    @Override
    public int requiredNumPartitions() {
      return numShufflePartitions;
    }
    /** 执行 requiredOrdering 相关操作。 */
    @Override
    public SortOrder[] requiredOrdering() {
      return ordering;
    }
  }
}
