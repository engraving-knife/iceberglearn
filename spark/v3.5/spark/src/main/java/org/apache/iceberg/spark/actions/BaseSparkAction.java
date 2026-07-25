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

import static org.apache.iceberg.MetadataTableType.ALL_MANIFESTS;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.iceberg.AllManifestsTable;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileContent;
import org.apache.iceberg.ManifestContent;
import org.apache.iceberg.ManifestFiles;
import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.ReachableFileUtil;
import org.apache.iceberg.StaticTableOperations;
import org.apache.iceberg.StatisticsFile;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.BulkDeletionFailureException;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.ClosingIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.iceberg.relocated.com.google.common.collect.ListMultimap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Multimaps;
import org.apache.iceberg.spark.JobGroupInfo;
import org.apache.iceberg.spark.JobGroupUtils;
import org.apache.iceberg.spark.SparkTableUtil;
import org.apache.iceberg.spark.source.SerializableTableWithSize;
import org.apache.iceberg.util.Tasks;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Spark 的 Iceberg 表维护操作基类。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），位于 actions 子包，是所有 Spark 端 Iceberg
 * 表操作（过期快照、重写数据文件、快照表等）的公共父类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link SparkSession} 与 {@link JavaSparkContext}，提供子类执行 Spark 作业所需环境。
 *   <li>维护操作的 option 选项表，支持链式调用（CRTP 自类型模式）。
 *   <li>提供 JobGroup 包装能力，便于在 Spark UI 中标识 Iceberg 操作。
 *   <li>统一构造各类元数据文件 Dataset（manifest、manifest list、statistics、其他元数据文件）， 供子类做文件清理/统计。
 *   <li>提供文件删除能力（普通并发删除与批量删除两种），并汇总删除统计信息。
 * </ul>
 *
 * <p>设计意图：使用 CRTP（Curiously Recurring Template Pattern）让链式 option() 等方法返回子类型本身， 保证类型流畅性；通过
 * Broadcast + Encoder 把 Iceberg Table 序列化到 executor 端读取 manifest， 利用 Spark
 * 并行能力加速元数据扫描。删除流程支持重试与失败抑制，保证部分失败不中断整体。
 *
 * <p>上下游关系：被 {@link ExpireSnapshotsSparkAction}、{@link RewriteDataFilesSparkAction}、 {@link
 * SnapshotTableSparkAction} 等具体 action 继承；依赖 iceberg-core 的 Table/FileIO/ManifestFiles 与 Spark
 * SQL/Core API。
 *
 * @param <ThisT> 子类型本身，用于链式 API 类型流畅
 */
abstract class BaseSparkAction<ThisT> {

  protected static final String MANIFEST = "Manifest";
  protected static final String MANIFEST_LIST = "Manifest List";
  protected static final String STATISTICS_FILES = "Statistics Files";
  protected static final String OTHERS = "Others";

  protected static final String FILE_PATH = "file_path";
  protected static final String LAST_MODIFIED = "last_modified";

  protected static final Splitter COMMA_SPLITTER = Splitter.on(",");
  protected static final Joiner COMMA_JOINER = Joiner.on(',');

  private static final Logger LOG = LoggerFactory.getLogger(BaseSparkAction.class);
  private static final AtomicInteger JOB_COUNTER = new AtomicInteger();
  private static final int DELETE_NUM_RETRIES = 3;
  private static final int DELETE_GROUP_SIZE = 100000;

  private final SparkSession spark;
  private final JavaSparkContext sparkContext;
  private final Map<String, String> options = Maps.newHashMap();

  /**
   * 构造 BaseSparkAction。
   *
   * @param spark 当前 SparkSession
   */
  protected BaseSparkAction(SparkSession spark) {
    this.spark = spark;
    this.sparkContext = JavaSparkContext.fromSparkContext(spark.sparkContext());
  }

  /** 返回当前操作所用的 SparkSession。 */
  protected SparkSession spark() {
    return spark;
  }

  /** 返回当前操作所用的 JavaSparkContext。 */
  protected JavaSparkContext sparkContext() {
    return sparkContext;
  }

  /** CRTP 钩子，子类返回 this 以支持链式调用返回子类型。 */
  protected abstract ThisT self();

  /**
   * 设置单个选项，返回子类型本身以支持链式调用。
   *
   * @param name 选项名
   * @param value 选项值
   * @return 当前 action 实例
   */
  public ThisT option(String name, String value) {
    options.put(name, value);
    return self();
  }

  /**
   * 批量设置选项，返回子类型本身。
   *
   * @param newOptions 选项映射
   * @return 当前 action 实例
   */
  public ThisT options(Map<String, String> newOptions) {
    options.putAll(newOptions);
    return self();
  }

  /** 返回当前累积的选项映射。 */
  protected Map<String, String> options() {
    return options;
  }

  /**
   * 在指定 JobGroupInfo 上下文中执行 supplier，便于 Spark UI 区分 Iceberg 作业。
   *
   * @param info 作业组信息
   * @param supplier 实际作业逻辑
   * @param <T> 返回类型
   * @return supplier 执行结果
   */
  protected <T> T withJobGroupInfo(JobGroupInfo info, Supplier<T> supplier) {
    return JobGroupUtils.withJobGroupInfo(sparkContext, info, supplier);
  }

  /**
   * 创建新的 JobGroupInfo，groupId 拼接全局自增序号以保证唯一。
   *
   * @param groupId 作业组标识前缀
   * @param desc 作业组描述
   * @return 唯一的 JobGroupInfo
   */
  protected JobGroupInfo newJobGroupInfo(String groupId, String desc) {
    return new JobGroupInfo(groupId + "-" + JOB_COUNTER.incrementAndGet(), desc);
  }

  /**
   * 基于给定元数据构造只读静态表。
   *
   * <p>设计意图：用 {@link StaticTableOperations} 指定 metadata 文件位置，构造不依赖当前表最新状态的 {@link
   * BaseTable}，便于在过期/回滚等场景读取历史快照对应的元数据。
   *
   * @param metadata 表元数据
   * @param io 文件 IO
   * @return 静态只读表
   */
  protected Table newStaticTable(TableMetadata metadata, FileIO io) {
    String metadataFileLocation = metadata.metadataFileLocation();
    StaticTableOperations ops = new StaticTableOperations(metadataFileLocation, io);
    return new BaseTable(ops, metadataFileLocation);
  }

  /** 返回表所有内容文件（数据/删除文件）的 Dataset，不过滤快照。 */
  protected Dataset<FileInfo> contentFileDS(Table table) {
    return contentFileDS(table, null);
  }

  /**
   * 返回内容文件（数据文件与删除文件）的 Dataset。
   *
   * <p>逻辑：把表序列化后广播到 executor；从 manifest 元数据表选出 manifest bean 并去重、重分区， 再用 {@link ReadManifest}
   * flatMap 读取每个 manifest 的条目，得到 (path, type) 形式的 FileInfo。 重分区用于避免自适应执行合并过多 task。
   *
   * @param table 目标表
   * @param snapshotIds 可选的快照 ID 集合，非空时只读取这些快照的 manifest
   * @return 内容文件 FileInfo Dataset
   */
  protected Dataset<FileInfo> contentFileDS(Table table, Set<Long> snapshotIds) {
    Table serializableTable = SerializableTableWithSize.copyOf(table);
    Broadcast<Table> tableBroadcast = sparkContext.broadcast(serializableTable);
    int numShufflePartitions = spark.sessionState().conf().numShufflePartitions();

    Dataset<ManifestFileBean> manifestBeanDS =
        manifestDF(table, snapshotIds)
            .selectExpr(
                "content",
                "path",
                "length",
                "0 as sequenceNumber",
                "partition_spec_id as partitionSpecId",
                "added_snapshot_id as addedSnapshotId")
            .dropDuplicates("path")
            .repartition(numShufflePartitions) // avoid adaptive execution combining tasks
            .as(ManifestFileBean.ENCODER);

    return manifestBeanDS.flatMap(new ReadManifest(tableBroadcast), FileInfo.ENCODER);
  }

  /** 返回表所有 manifest 文件的 Dataset，不过滤快照。 */
  protected Dataset<FileInfo> manifestDS(Table table) {
    return manifestDS(table, null);
  }

  /**
   * 返回 manifest 文件的 Dataset，type 标记为 {@link #MANIFEST}。
   *
   * @param table 目标表
   * @param snapshotIds 可选快照 ID 集合
   * @return manifest FileInfo Dataset
   */
  protected Dataset<FileInfo> manifestDS(Table table, Set<Long> snapshotIds) {
    return manifestDF(table, snapshotIds)
        .select(col("path"), lit(MANIFEST).as("type"))
        .as(FileInfo.ENCODER);
  }

  /**
   * 加载 ALL_MANIFESTS 元数据表并按快照 ID 过滤得到 manifest 行 Dataset。
   *
   * @param table 目标表
   * @param snapshotIds 可选快照 ID 集合，非空时按 REF_SNAPSHOT_ID 过滤
   * @return manifest 行 Dataset
   */
  private Dataset<Row> manifestDF(Table table, Set<Long> snapshotIds) {
    Dataset<Row> manifestDF = loadMetadataTable(table, ALL_MANIFESTS);
    if (snapshotIds != null) {
      Column filterCond = col(AllManifestsTable.REF_SNAPSHOT_ID.name()).isInCollection(snapshotIds);
      return manifestDF.filter(filterCond);
    } else {
      return manifestDF;
    }
  }

  /** 返回表所有 manifest list（snapshot 文件）的 Dataset，不过滤快照。 */
  protected Dataset<FileInfo> manifestListDS(Table table) {
    return manifestListDS(table, null);
  }

  /**
   * 返回 manifest list 文件的 Dataset，type 标记为 {@link #MANIFEST_LIST}。
   *
   * @param table 目标表
   * @param snapshotIds 可选快照 ID 集合
   * @return manifest list FileInfo Dataset
   */
  protected Dataset<FileInfo> manifestListDS(Table table, Set<Long> snapshotIds) {
    List<String> manifestLists = ReachableFileUtil.manifestListLocations(table, snapshotIds);
    return toFileInfoDS(manifestLists, MANIFEST_LIST);
  }

  /**
   * 返回统计文件（statistics files）的 Dataset，type 标记为 {@link #STATISTICS_FILES}。
   *
   * <p>逻辑：根据 snapshotIds 构造谓词，通过 {@link ReachableFileUtil#statisticsFilesLocations} 取出文件路径列表后转为
   * Dataset。
   *
   * @param table 目标表
   * @param snapshotIds 可选快照 ID 集合，非空时只保留对应快照的统计文件
   * @return 统计文件 FileInfo Dataset
   */
  protected Dataset<FileInfo> statisticsFileDS(Table table, Set<Long> snapshotIds) {
    Predicate<StatisticsFile> predicate;
    if (snapshotIds == null) {
      predicate = statisticsFile -> true;
    } else {
      predicate = statisticsFile -> snapshotIds.contains(statisticsFile.snapshotId());
    }

    List<String> statisticsFiles = ReachableFileUtil.statisticsFilesLocations(table, predicate);
    return toFileInfoDS(statisticsFiles, STATISTICS_FILES);
  }

  /** 返回其他元数据文件（metadata 文件、version-hint、统计文件）的 Dataset，不含历史旧 metadata。 */
  protected Dataset<FileInfo> otherMetadataFileDS(Table table) {
    return otherMetadataFileDS(table, false /* include all reachable old metadata locations */);
  }

  /** 返回所有可达的其他元数据文件 Dataset，包含递归到的历史旧 metadata 文件。 */
  protected Dataset<FileInfo> allReachableOtherMetadataFileDS(Table table) {
    return otherMetadataFileDS(table, true /* include all reachable old metadata locations */);
  }

  /**
   * 收集其他元数据文件（metadata 文件、version-hint、统计文件）并转为 Dataset。
   *
   * @param table 目标表
   * @param recursive 是否递归收集所有历史 metadata 文件位置
   * @return 其他元数据文件 FileInfo Dataset
   */
  private Dataset<FileInfo> otherMetadataFileDS(Table table, boolean recursive) {
    List<String> otherMetadataFiles = Lists.newArrayList();
    otherMetadataFiles.addAll(ReachableFileUtil.metadataFileLocations(table, recursive));
    otherMetadataFiles.add(ReachableFileUtil.versionHintLocation(table));
    otherMetadataFiles.addAll(ReachableFileUtil.statisticsFilesLocations(table));
    return toFileInfoDS(otherMetadataFiles, OTHERS);
  }

  /**
   * 加载 Iceberg 元数据表（如 ALL_MANIFESTS）为 Spark Dataset。
   *
   * @param table 目标表
   * @param type 元数据表类型
   * @return 元数据表行 Dataset
   */
  protected Dataset<Row> loadMetadataTable(Table table, MetadataTableType type) {
    return SparkTableUtil.loadMetadataTable(spark, table, type);
  }

  /**
   * 把路径列表转为 FileInfo Dataset，type 统一标记。
   *
   * @param paths 文件路径列表
   * @param type 文件类型标记
   * @return FileInfo Dataset
   */
  private Dataset<FileInfo> toFileInfoDS(List<String> paths, String type) {
    List<FileInfo> fileInfoList = Lists.transform(paths, path -> new FileInfo(path, type));
    return spark.createDataset(fileInfoList, FileInfo.ENCODER);
  }

  /**
   * 并发删除文件并按文件类型统计删除数量。
   *
   * <p>逻辑：使用 {@link Tasks#foreach} 在给定线程池上并发执行 deleteFunc，配置 3 次重试且 遇到 {@link NotFoundException}
   * 时停止重试；失败仅告警不中断，最终汇总到 {@link DeleteSummary}。
   *
   * @param executorService 并发删除所用线程池
   * @param deleteFunc 实际删除单个文件的函数
   * @param files 待删除文件 FileInfo 迭代器（path, type）
   * @return 删除统计摘要
   */
  protected DeleteSummary deleteFiles(
      ExecutorService executorService, Consumer<String> deleteFunc, Iterator<FileInfo> files) {

    DeleteSummary summary = new DeleteSummary();

    Tasks.foreach(files)
        .retry(DELETE_NUM_RETRIES)
        .stopRetryOn(NotFoundException.class)
        .suppressFailureWhenFinished()
        .executeWith(executorService)
        .onFailure(
            (fileInfo, exc) -> {
              String path = fileInfo.getPath();
              String type = fileInfo.getType();
              LOG.warn("Delete failed for {}: {}", type, path, exc);
            })
        .run(
            fileInfo -> {
              String path = fileInfo.getPath();
              String type = fileInfo.getType();
              deleteFunc.accept(path);
              summary.deletedFile(path, type);
            });

    return summary;
  }

  /**
   * 批量删除文件并按文件类型统计删除数量。
   *
   * <p>逻辑：把文件按 {@link #DELETE_GROUP_SIZE} 分组，对每组调用 {@link SupportsBulkOperations#deleteFiles}
   * 批量删除；捕获 {@link BulkDeletionFailureException} 累计失败数，成功数记入 {@link DeleteSummary}。
   *
   * @param io 支持批量删除的文件 IO
   * @param files 待删除文件 FileInfo 迭代器
   * @return 删除统计摘要
   */
  protected DeleteSummary deleteFiles(SupportsBulkOperations io, Iterator<FileInfo> files) {
    DeleteSummary summary = new DeleteSummary();
    Iterator<List<FileInfo>> fileGroups = Iterators.partition(files, DELETE_GROUP_SIZE);

    Tasks.foreach(fileGroups)
        .suppressFailureWhenFinished()
        .run(fileGroup -> deleteFileGroup(fileGroup, io, summary));

    return summary;
  }

  /**
   * 删除一组文件，按类型分组后批量删除并汇总。
   *
   * <p>逻辑：用 {@link Multimaps#index} 按类型分组，对每组调用 {@code io.deleteFiles} 批量删除， 失败数从总数中扣除后记入 summary。
   *
   * @param fileGroup 文件组
   * @param io 支持批量删除的文件 IO
   * @param summary 删除统计摘要
   */
  private static void deleteFileGroup(
      List<FileInfo> fileGroup, SupportsBulkOperations io, DeleteSummary summary) {

    ListMultimap<String, FileInfo> filesByType = Multimaps.index(fileGroup, FileInfo::getType);
    ListMultimap<String, String> pathsByType =
        Multimaps.transformValues(filesByType, FileInfo::getPath);

    for (Map.Entry<String, Collection<String>> entry : pathsByType.asMap().entrySet()) {
      String type = entry.getKey();
      Collection<String> paths = entry.getValue();
      int failures = 0;
      try {
        io.deleteFiles(paths);
      } catch (BulkDeletionFailureException e) {
        failures = e.numberFailedObjects();
      }
      summary.deletedFiles(type, paths.size() - failures);
    }
  }

  /**
   * 文件删除统计摘要。
   *
   * <p>设计意图：用多个 {@link AtomicLong} 分类计数（数据文件、position/equality 删除文件、 manifest、manifest
   * list、统计文件、其他），支持并发累加；供 action 返回删除结果给调用方。
   */
  static class DeleteSummary {
    private final AtomicLong dataFilesCount = new AtomicLong(0L);
    private final AtomicLong positionDeleteFilesCount = new AtomicLong(0L);
    private final AtomicLong equalityDeleteFilesCount = new AtomicLong(0L);
    private final AtomicLong manifestsCount = new AtomicLong(0L);
    private final AtomicLong manifestListsCount = new AtomicLong(0L);
    private final AtomicLong statisticsFilesCount = new AtomicLong(0L);
    private final AtomicLong otherFilesCount = new AtomicLong(0L);

    /**
     * 按类型批量累加删除计数。
     *
     * @param type 文件类型
     * @param numFiles 本次删除的文件数
     */
    public void deletedFiles(String type, int numFiles) {
      if (FileContent.DATA.name().equalsIgnoreCase(type)) {
        dataFilesCount.addAndGet(numFiles);

      } else if (FileContent.POSITION_DELETES.name().equalsIgnoreCase(type)) {
        positionDeleteFilesCount.addAndGet(numFiles);

      } else if (FileContent.EQUALITY_DELETES.name().equalsIgnoreCase(type)) {
        equalityDeleteFilesCount.addAndGet(numFiles);

      } else if (MANIFEST.equalsIgnoreCase(type)) {
        manifestsCount.addAndGet(numFiles);

      } else if (MANIFEST_LIST.equalsIgnoreCase(type)) {
        manifestListsCount.addAndGet(numFiles);

      } else if (STATISTICS_FILES.equalsIgnoreCase(type)) {
        statisticsFilesCount.addAndGet(numFiles);

      } else if (OTHERS.equalsIgnoreCase(type)) {
        otherFilesCount.addAndGet(numFiles);

      } else {
        throw new ValidationException("Illegal file type: %s", type);
      }
    }

    /**
     * 按类型单个累加删除计数并记录日志。
     *
     * @param path 已删除文件路径
     * @param type 文件类型
     */
    public void deletedFile(String path, String type) {
      if (FileContent.DATA.name().equalsIgnoreCase(type)) {
        dataFilesCount.incrementAndGet();
        LOG.trace("Deleted data file: {}", path);

      } else if (FileContent.POSITION_DELETES.name().equalsIgnoreCase(type)) {
        positionDeleteFilesCount.incrementAndGet();
        LOG.trace("Deleted positional delete file: {}", path);

      } else if (FileContent.EQUALITY_DELETES.name().equalsIgnoreCase(type)) {
        equalityDeleteFilesCount.incrementAndGet();
        LOG.trace("Deleted equality delete file: {}", path);

      } else if (MANIFEST.equalsIgnoreCase(type)) {
        manifestsCount.incrementAndGet();
        LOG.debug("Deleted manifest: {}", path);

      } else if (MANIFEST_LIST.equalsIgnoreCase(type)) {
        manifestListsCount.incrementAndGet();
        LOG.debug("Deleted manifest list: {}", path);

      } else if (STATISTICS_FILES.equalsIgnoreCase(type)) {
        statisticsFilesCount.incrementAndGet();
        LOG.debug("Deleted statistics file: {}", path);

      } else if (OTHERS.equalsIgnoreCase(type)) {
        otherFilesCount.incrementAndGet();
        LOG.debug("Deleted other metadata file: {}", path);

      } else {
        throw new ValidationException("Illegal file type: %s", type);
      }
    }
    /** 执行 dataFilesCount 相关操作。 */
    public long dataFilesCount() {
      return dataFilesCount.get();
    }
    /** 执行 positionDeleteFilesCount 相关操作。 */
    public long positionDeleteFilesCount() {
      return positionDeleteFilesCount.get();
    }
    /** 执行 equalityDeleteFilesCount 相关操作。 */
    public long equalityDeleteFilesCount() {
      return equalityDeleteFilesCount.get();
    }
    /** 执行 manifestsCount 相关操作。 */
    public long manifestsCount() {
      return manifestsCount.get();
    }
    /** 执行 manifestListsCount 相关操作。 */
    public long manifestListsCount() {
      return manifestListsCount.get();
    }
    /** 执行 statisticsFilesCount 相关操作。 */
    public long statisticsFilesCount() {
      return statisticsFilesCount.get();
    }
    /** 执行 otherFilesCount 相关操作。 */
    public long otherFilesCount() {
      return otherFilesCount.get();
    }

    /** 返回所有类型文件删除总数。 */
    public long totalFilesCount() {
      return dataFilesCount()
          + positionDeleteFilesCount()
          + equalityDeleteFilesCount()
          + manifestsCount()
          + manifestListsCount()
          + statisticsFilesCount()
          + otherFilesCount();
    }
  }

  /**
   * 读取 manifest 文件条目的 FlatMapFunction（executor 端执行）。
   *
   * <p>设计意图：通过广播的 Table 在 executor 端读取 manifest，提取每个内容文件的 (path, content type)， 用 {@link
   * ClosingIterator} 保证迭代器关闭。区分 DATA 与 DELETES 两种 manifest 调用不同读取 API。
   */
  private static class ReadManifest implements FlatMapFunction<ManifestFileBean, FileInfo> {
    private final Broadcast<Table> table;

    ReadManifest(Broadcast<Table> table) {
      this.table = table;
    }
    /** 执行过程并返回结果行。 */
    @Override
    public Iterator<FileInfo> call(ManifestFileBean manifest) {
      return new ClosingIterator<>(entries(manifest));
    }

    /**
     * 读取单个 manifest 的所有条目并转为 FileInfo 迭代器。
     *
     * <p>逻辑：根据 manifest 内容类型（DATA/DELETES）选择 {@link ManifestFiles#read} 或 {@link
     * ManifestFiles#readDeleteManifest}，只投影 path 与 content 字段，再映射为 FileInfo。
     *
     * @param manifest 待读取的 manifest bean
     * @return FileInfo 的关闭式迭代器
     */
    public CloseableIterator<FileInfo> entries(ManifestFileBean manifest) {
      ManifestContent content = manifest.content();
      FileIO io = table.getValue().io();
      Map<Integer, PartitionSpec> specs = table.getValue().specs();
      List<String> proj = ImmutableList.of(DataFile.FILE_PATH.name(), DataFile.CONTENT.name());

      switch (content) {
        case DATA:
          return CloseableIterator.transform(
              ManifestFiles.read(manifest, io, specs).select(proj).iterator(),
              ReadManifest::toFileInfo);
        case DELETES:
          return CloseableIterator.transform(
              ManifestFiles.readDeleteManifest(manifest, io, specs).select(proj).iterator(),
              ReadManifest::toFileInfo);
        default:
          throw new IllegalArgumentException("Unsupported manifest content type:" + content);
      }
    }
    /** 转换为 FileInfo。 */
    static FileInfo toFileInfo(ContentFile<?> file) {
      return new FileInfo(file.path().toString(), file.content().toString());
    }
  }
}
