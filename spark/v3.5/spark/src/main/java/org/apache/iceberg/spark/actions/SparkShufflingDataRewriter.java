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
 * 基于 Spark shuffle 的数据重写器抽象基类。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），actions 子包。是 SparkSortDataRewriter 与
 * SparkZOrderDataRewriter 的公共父类，提供"读 -> 排序 shuffle -> 写"的通用骨架。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义压缩因子（compression-factor）与每文件 shuffle 分区数（shuffle-partitions-per-file） 两个调优参数。
 *   <li>根据输入数据大小与压缩因子估算目标 shuffle 分区数。
 *   <li>通过 {@link DistributionAndOrderingUtils$} 把排序分布注入 Spark 逻辑计划， 必要时用 {@link
 *       OrderAwareCoalesce} 把多个有序分区合并为单个大文件。
 * </ul>
 *
 * <p>设计意图：用 Spark 的分布与排序声明（RequiresDistributionAndOrdering）驱动 shuffle， 而非手动 repartition+sort，以便与
 * Spark 优化器协作；当目标文件过大时通过 shuffle-partitions-per-file 拆分为多个有序分区再合并，缓解单分区内存压力（需 Iceberg 扩展）。
 *
 * <p>上下游关系：继承 {@link SparkSizeBasedDataRewriter}，被 sort/zOrder 子类继承； 被 {@link
 * RewriteDataFilesSparkAction} 调用。
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

  /** 返回本重写器支持的合法选项集合（含父类选项与 compression-factor、shuffle-partitions-per-file）。 */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.<String>builder()
        .addAll(super.validOptions())
        .add(COMPRESSION_FACTOR)
        .add(SHUFFLE_PARTITIONS_PER_FILE)
        .build();
  }

  /** 初始化重写器：先调用父类 init，再读取 compressionFactor 与 numShufflePartitionsPerFile。 */
  @Override
  public void init(Map<String, String> options) {
    super.init(options);
    this.compressionFactor = compressionFactor(options);
    this.numShufflePartitionsPerFile = numShufflePartitionsPerFile(options);
  }

  /**
   * 执行单组文件的重写。
   *
   * <p>逻辑：以 groupId 读取 Iceberg 数据集，应用排序函数得到 sortedDF， 再以 append 模式写回 Iceberg，关闭表自带分布与排序（由本类自行控制），
   * 携带目标文件大小与 rewritten 任务集 ID。
   *
   * @param groupId 文件组 ID
   * @param group 文件扫描任务列表
   */
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

  /** 构造排序函数：把 Iceberg SortOrder 转 Spark SortOrder，按 numShufflePartitions 注入排序计划。 */
  private Function<Dataset<Row>, Dataset<Row>> sortFunction(List<FileScanTask> group) {
    SortOrder[] ordering = Spark3Util.toOrdering(outputSortOrder(group));
    int numShufflePartitions = numShufflePartitions(group);
    return (df) -> transformPlan(df, plan -> sortPlan(plan, ordering, numShufflePartitions));
  }

  /**
   * 在逻辑计划上注入排序与分布。
   *
   * <p>逻辑：用 {@link DistributionAndOrderingUtils$#prepareQuery} 注入 OrderedWrite； 若
   * numShufflePartitionsPerFile > 1，再叠加 {@link OrderAwareCoalesce} 把多个有序分区合并。
   *
   * @param plan 原始逻辑计划
   * @param ordering Spark SortOrder 数组
   * @param numShufflePartitions shuffle 分区数
   * @return 注入排序后的逻辑计划
   */
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

  /** 对 Dataset 的逻辑计划应用变换函数，返回新的 Dataset。 */
  private Dataset<Row> transformPlan(Dataset<Row> df, Function<LogicalPlan, LogicalPlan> func) {
    return new Dataset<>(spark(), func.apply(df.logicalPlan()), df.encoder());
  }

  /**
   * 计算输出排序规则。
   *
   * <p>逻辑：若文件组所属 spec 与表当前 spec 不一致，用 {@link SortOrderUtil#buildSortOrder} 把分区列排序前置到子类排序规则中，保证跨
   * spec 文件能正确按输出分区排序。
   */
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

  /** 根据输入大小×压缩因子估算目标输出文件数，再乘以每文件分区数得到 shuffle 分区数，至少为 1。 */
  private int numShufflePartitions(List<FileScanTask> group) {
    int numOutputFiles = (int) numOutputFiles((long) (inputSize(group) * compressionFactor));
    return Math.max(1, numOutputFiles * numShufflePartitionsPerFile);
  }

  /** 读取并校验 compression-factor 选项，必须 > 0。 */
  private double compressionFactor(Map<String, String> options) {
    double value =
        PropertyUtil.propertyAsDouble(options, COMPRESSION_FACTOR, COMPRESSION_FACTOR_DEFAULT);
    Preconditions.checkArgument(
        value > 0, "'%s' is set to %s but must be > 0", COMPRESSION_FACTOR, value);
    return value;
  }

  /** 读取并校验 shuffle-partitions-per-file 选项，>1 时要求启用 Iceberg Spark 扩展。 */
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

  /**
   * 排序写声明：实现 {@link RequiresDistributionAndOrdering}，向 Spark 声明所需的分布与排序。
   *
   * <p>设计意图：被 {@link DistributionAndOrderingUtils$} 用于在查询计划阶段注入 shuffle+sort，
   * distributionStrictlylyRequired=true 表示必须严格满足分区数与分布。
   */
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
