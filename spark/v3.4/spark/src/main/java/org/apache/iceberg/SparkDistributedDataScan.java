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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：基于 Spark 分布式扫描的数据扫描实现，在 Spark 集群上并行执行 Iceberg 扫描以加速大表统计。
 *
 * <p>设计意图：将扫描任务分发到 Executor 并行执行，再在 Driver 汇总结果。
 *
 * <p>上下游关系：由 SparkActions 等在需要分布式扫描时使用。
 */
public class SparkDistributedDataScan extends BaseDistributedDataScan {

  private static final Joiner COMMA = Joiner.on(',');
  private static final String DELETE_PLANNING_JOB_GROUP_ID = "DELETE-PLANNING";
  private static final String DATA_PLANNING_JOB_GROUP_ID = "DATA-PLANNING";

  private final SparkSession spark;
  private final JavaSparkContext sparkContext;
  private final SparkReadConf readConf;

  private Broadcast<Table> tableBroadcast = null;

  public SparkDistributedDataScan(SparkSession spark, Table table, SparkReadConf readConf) {
    this(spark, table, readConf, table.schema(), newTableScanContext(table));
  }

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
  /** 创建 RefinedScan 实例。 */
  @Override
  protected BatchScan newRefinedScan(
      Table newTable, Schema newSchema, TableScanContext newContext) {
    return new SparkDistributedDataScan(spark, newTable, readConf, newSchema, newContext);
  }
  /** 执行 remoteParallelism 相关操作。 */
  @Override
  protected int remoteParallelism() {
    return readConf.parallelism();
  }
  /** 执行 dataPlanningMode 相关操作。 */
  @Override
  protected PlanningMode dataPlanningMode() {
    return readConf.dataPlanningMode();
  }
  /** 执行 shouldCopyRemotelyPlannedDataFiles 相关操作。 */
  @Override
  protected boolean shouldCopyRemotelyPlannedDataFiles() {
    return false;
  }
  /** 执行 planDataRemotely 相关操作。 */
  @Override
  protected Iterable<CloseableIterable<DataFile>> planDataRemotely(
      List<ManifestFile> dataManifests, boolean withColumnStats) {
    JobGroupInfo info = new JobGroupInfo(DATA_PLANNING_JOB_GROUP_ID, jobDesc("data"));
    return withJobGroupInfo(info, () -> doPlanDataRemotely(dataManifests, withColumnStats));
  }
  /** 执行 doPlanDataRemotely 相关操作。 */
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
  /** 执行 deletePlanningMode 相关操作。 */
  @Override
  protected PlanningMode deletePlanningMode() {
    return readConf.deletePlanningMode();
  }
  /** 执行 planDeletesRemotely 相关操作。 */
  @Override
  protected DeleteFileIndex planDeletesRemotely(List<ManifestFile> deleteManifests) {
    JobGroupInfo info = new JobGroupInfo(DELETE_PLANNING_JOB_GROUP_ID, jobDesc("deletes"));
    return withJobGroupInfo(info, () -> doPlanDeletesRemotely(deleteManifests));
  }
  /** 执行 doPlanDeletesRemotely 相关操作。 */
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

  private <T> T withJobGroupInfo(JobGroupInfo info, Supplier<T> supplier) {
    return JobGroupUtils.withJobGroupInfo(sparkContext, info, supplier);
  }
  /** 执行 jobDesc 相关操作。 */
  private String jobDesc(String type) {
    List<String> options = Lists.newArrayList();
    options.add("snapshot_id=" + snapshot().snapshotId());
    String optionsAsString = COMMA.join(options);
    return String.format("Planning %s (%s) for %s", type, optionsAsString, table().name());
  }
  /** 转换为 Beans。 */
  private List<ManifestFileBean> toBeans(List<ManifestFile> manifests) {
    return manifests.stream().map(ManifestFileBean::fromManifest).collect(Collectors.toList());
  }
  /** 执行 tableBroadcast 相关操作。 */
  private Broadcast<Table> tableBroadcast() {
    if (tableBroadcast == null) {
      Table serializableTable = SerializableTableWithSize.copyOf(table());
      this.tableBroadcast = sparkContext.broadcast(serializableTable);
    }

    return tableBroadcast;
  }

  private <T> List<List<T>> collectPartitions(JavaRDD<T> rdd) {
    int[] partitionIds = IntStream.range(0, rdd.getNumPartitions()).toArray();
    return Arrays.asList(rdd.collectPartitions(partitionIds));
  }
  /** 执行 liveFilesCount 相关操作。 */
  private int liveFilesCount(List<ManifestFile> manifests) {
    return manifests.stream().mapToInt(this::liveFilesCount).sum();
  }
  /** 执行 liveFilesCount 相关操作。 */
  private int liveFilesCount(ManifestFile manifest) {
    return manifest.existingFilesCount() + manifest.addedFilesCount();
  }
  /** 创建 TableScanContext 实例。 */
  private static TableScanContext newTableScanContext(Table table) {
    if (table instanceof BaseTable) {
      MetricsReporter reporter = ((BaseTable) table).reporter();
      return ImmutableTableScanContext.builder().metricsReporter(reporter).build();
    } else {
      return TableScanContext.empty();
    }
  }

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
