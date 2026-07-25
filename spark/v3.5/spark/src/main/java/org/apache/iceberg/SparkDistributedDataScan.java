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
package org.apache.iceberg;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.ClosingIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.JobGroupInfo;
import org.apache.iceberg.spark.JobGroupUtils;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.spark.actions.ManifestFileBean;
import org.apache.iceberg.spark.source.SerializableTableWithSize;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.SparkSession;

/**
 * 可利用 Spark 集群资源进行计划（planning）的批数据扫描。
 *
 * <p>所属模块：iceberg-spark（位于 org.apache.iceberg 包，为 Spark 提供分布式扫描计划能力）。 继承 {@link
 * BaseDistributedDataScan}，将清单过滤分发到 Spark 集群远端执行，只把命中的数据文件 与删除文件拉回 driver，删除文件的分配在本地完成。当远端并行度远高于
 * driver 核数时收益明显。
 *
 * <p>适用场景：对所有分区的上下界有选择性过滤条件的查询，或元数据聚类较差时；也适用于大表全表 扫描，但拉回 driver 的数据/删除文件明细成本需关注。注意过滤后的元数据大小不应超过
 * spark.driver.maxResultSize。
 *
 * <p>性能建议：启用 Kryo 序列化、增加 driver 核数、调大 spark.resultGetter.threads。
 *
 * <p>上下游关系：由 Spark 读取路径在需要分布式计划时使用；依赖 {@link JobGroupUtils} 设置作业组， 通过广播 {@link
 * SerializableTableWithSize} 在 executor 上读取清单。
 */
public class SparkDistributedDataScan extends BaseDistributedDataScan {

  private static final Joiner COMMA = Joiner.on(',');
  private static final String DELETE_PLANNING_JOB_GROUP_ID = "DELETE-PLANNING";
  private static final String DATA_PLANNING_JOB_GROUP_ID = "DATA-PLANNING";

  private final SparkSession spark;
  private final JavaSparkContext sparkContext;
  private final SparkReadConf readConf;

  private Broadcast<Table> tableBroadcast = null;

  /** 以 SparkSession、表与读取配置构造，使用表 schema 与默认扫描上下文。 */
  public SparkDistributedDataScan(SparkSession spark, Table table, SparkReadConf readConf) {
    this(spark, table, readConf, table.schema(), newTableScanContext(table));
  }

  /** 内部构造：在 refine 时以新 schema 与上下文创建新实例。 */
  private SparkDistributedDataScan(
      SparkSession spark,
      Table table,
      SparkReadConf readConf,
      Schema schema,
      TableScanContext context) {
    super(table, schema, context);
    this.spark = spark;
    this.sparkContext = JavaSparkContext.fromSparkContext(spark.sparkContext());
    this.readConf = readConf;
  }

  /** 返回带新表/schema/上下文的精炼扫描实例。 */
  @Override
  protected BatchScan newRefinedScan(
      Table newTable, Schema newSchema, TableScanContext newContext) {
    return new SparkDistributedDataScan(spark, newTable, readConf, newSchema, newContext);
  }

  /** 远端并行度，取自读取配置。 */
  @Override
  protected int remoteParallelism() {
    return readConf.parallelism();
  }

  /** 数据计划模式，取自读取配置。 */
  @Override
  protected PlanningMode dataPlanningMode() {
    return readConf.dataPlanningMode();
  }

  /** 是否拷贝远端计划出的数据文件：本实现返回 false。 */
  @Override
  protected boolean shouldCopyRemotelyPlannedDataFiles() {
    return false;
  }

  /** 在作业组中远端计划数据文件，委托给 {@link #doPlanDataRemotely}。 */
  @Override
  protected Iterable<CloseableIterable<DataFile>> planDataRemotely(
      List<ManifestFile> dataManifests, boolean withColumnStats) {
    JobGroupInfo info = new JobGroupInfo(DATA_PLANNING_JOB_GROUP_ID, jobDesc("data"));
    return withJobGroupInfo(info, () -> doPlanDataRemotely(dataManifests, withColumnStats));
  }

  /**
   * 远端计划数据文件实现。
   *
   * <p>逻辑：将清单转为 bean 并行化，用 {@link ReadDataManifest} 在 executor 上读取并过滤；
   * 按分区收集结果，统计匹配与跳过文件数，返回每组数据文件的可关闭迭代。
   */
  private Iterable<CloseableIterable<DataFile>> doPlanDataRemotely(
      List<ManifestFile> dataManifests, boolean withColumnStats) {
    scanMetrics().scannedDataManifests().increment(dataManifests.size());

    JavaRDD<DataFile> dataFileRDD =
        sparkContext
            .parallelize(toBeans(dataManifests), dataManifests.size())
            .flatMap(new ReadDataManifest(tableBroadcast(), context(), withColumnStats));
    List<List<DataFile>> dataFileGroups = collectPartitions(dataFileRDD);

    int matchingFilesCount = dataFileGroups.stream().mapToInt(List::size).sum();
    int skippedFilesCount = liveFilesCount(dataManifests) - matchingFilesCount;
    scanMetrics().skippedDataFiles().increment(skippedFilesCount);

    return Iterables.transform(dataFileGroups, CloseableIterable::withNoopClose);
  }

  /** 删除计划模式，取自读取配置。 */
  @Override
  protected PlanningMode deletePlanningMode() {
    return readConf.deletePlanningMode();
  }

  /** 在作业组中远端计划删除文件，委托给 {@link #doPlanDeletesRemotely}。 */
  @Override
  protected DeleteFileIndex planDeletesRemotely(List<ManifestFile> deleteManifests) {
    JobGroupInfo info = new JobGroupInfo(DELETE_PLANNING_JOB_GROUP_ID, jobDesc("deletes"));
    return withJobGroupInfo(info, () -> doPlanDeletesRemotely(deleteManifests));
  }

  /**
   * 远端计划删除文件实现。
   *
   * <p>逻辑：并行化删除清单，用 {@link ReadDeleteManifest} 在 executor 上读取过滤后 collect； 统计跳过文件数，构建 {@link
   * DeleteFileIndex} 返回。
   */
  private DeleteFileIndex doPlanDeletesRemotely(List<ManifestFile> deleteManifests) {
    scanMetrics().scannedDeleteManifests().increment(deleteManifests.size());

    List<DeleteFile> deleteFiles =
        sparkContext
            .parallelize(toBeans(deleteManifests), deleteManifests.size())
            .flatMap(new ReadDeleteManifest(tableBroadcast(), context()))
            .collect();

    int skippedFilesCount = liveFilesCount(deleteManifests) - deleteFiles.size();
    scanMetrics().skippedDeleteFiles().increment(skippedFilesCount);

    return DeleteFileIndex.builderFor(deleteFiles)
        .specsById(table().specs())
        .caseSensitive(isCaseSensitive())
        .scanMetrics(scanMetrics())
        .build();
  }

  /** 在指定作业组信息下执行 supplier，便于 UI 追踪。 */
  private <T> T withJobGroupInfo(JobGroupInfo info, Supplier<T> supplier) {
    return JobGroupUtils.withJobGroupInfo(sparkContext, info, supplier);
  }

  /** 构造计划作业描述（含快照 ID 与表名）。 */
  private String jobDesc(String type) {
    List<String> options = Lists.newArrayList();
    options.add("snapshot_id=" + snapshot().snapshotId());
    String optionsAsString = COMMA.join(options);
    return String.format("Planning %s (%s) for %s", type, optionsAsString, table().name());
  }

  /** 将清单列表转为可序列化的 {@link ManifestFileBean} 列表。 */
  private List<ManifestFileBean> toBeans(List<ManifestFile> manifests) {
    return manifests.stream().map(ManifestFileBean::fromManifest).collect(Collectors.toList());
  }

  /** 懒初始化并返回可序列化表的广播变量。 */
  private Broadcast<Table> tableBroadcast() {
    if (tableBroadcast == null) {
      Table serializableTable = SerializableTableWithSize.copyOf(table());
      this.tableBroadcast = sparkContext.broadcast(serializableTable);
    }

    return tableBroadcast;
  }

  /** 按分区 ID 收集 RDD 各分区结果到嵌套列表。 */
  private <T> List<List<T>> collectPartitions(JavaRDD<T> rdd) {
    int[] partitionIds = IntStream.range(0, rdd.getNumPartitions()).toArray();
    return Arrays.asList(rdd.collectPartitions(partitionIds));
  }

  /** 统计清单列表中存活文件总数。 */
  private int liveFilesCount(List<ManifestFile> manifests) {
    return manifests.stream().mapToInt(this::liveFilesCount).sum();
  }

  /** 统计单个清单中存活文件数（existing + added）。 */
  private int liveFilesCount(ManifestFile manifest) {
    return manifest.existingFilesCount() + manifest.addedFilesCount();
  }

  /** 由表构造扫描上下文，若为 BaseTable 则携带其 metrics reporter。 */
  private static TableScanContext newTableScanContext(Table table) {
    if (table instanceof BaseTable) {
      MetricsReporter reporter = ((BaseTable) table).reporter();
      return ImmutableTableScanContext.builder().metricsReporter(reporter).build();
    } else {
      return TableScanContext.empty();
    }
  }

  /** executor 端读取数据清单的 FlatMapFunction：按过滤条件读取数据文件并返回迭代。 */
  private static class ReadDataManifest implements FlatMapFunction<ManifestFileBean, DataFile> {

    private final Broadcast<Table> table;
    private final Expression filter;
    private final boolean withStats;
    private final boolean isCaseSensitive;

    ReadDataManifest(Broadcast<Table> table, TableScanContext context, boolean withStats) {
      this.table = table;
      this.filter = context.rowFilter();
      this.withStats = withStats;
      this.isCaseSensitive = context.caseSensitive();
    }

    @Override
    public Iterator<DataFile> call(ManifestFileBean manifest) throws Exception {
      FileIO io = table.value().io();
      Map<Integer, PartitionSpec> specs = table.value().specs();
      return new ClosingIterator<>(
          ManifestFiles.read(manifest, io, specs)
              .select(withStats ? SCAN_WITH_STATS_COLUMNS : SCAN_COLUMNS)
              .filterRows(filter)
              .caseSensitive(isCaseSensitive)
              .iterator());
    }
  }

  /** executor 端读取删除清单的 FlatMapFunction：按过滤条件读取删除文件并返回迭代。 */
  private static class ReadDeleteManifest implements FlatMapFunction<ManifestFileBean, DeleteFile> {

    private final Broadcast<Table> table;
    private final Expression filter;
    private final boolean isCaseSensitive;

    ReadDeleteManifest(Broadcast<Table> table, TableScanContext context) {
      this.table = table;
      this.filter = context.rowFilter();
      this.isCaseSensitive = context.caseSensitive();
    }

    @Override
    public Iterator<DeleteFile> call(ManifestFileBean manifest) throws Exception {
      FileIO io = table.value().io();
      Map<Integer, PartitionSpec> specs = table.value().specs();
      return new ClosingIterator<>(
          ManifestFiles.readDeleteManifest(manifest, io, specs)
              .select(DELETE_SCAN_WITH_STATS_COLUMNS)
              .filterRows(filter)
              .caseSensitive(isCaseSensitive)
              .iterator());
    }
  }
}
