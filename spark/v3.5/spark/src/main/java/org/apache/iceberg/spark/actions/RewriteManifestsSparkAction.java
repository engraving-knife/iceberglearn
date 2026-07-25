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

import static org.apache.iceberg.MetadataTableType.ENTRIES;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.ManifestFiles;
import org.apache.iceberg.ManifestWriter;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Partitioning;
import org.apache.iceberg.SerializableTable;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.actions.ImmutableRewriteManifests;
import org.apache.iceberg.actions.RewriteManifests;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.JobGroupInfo;
import org.apache.iceberg.spark.SparkDataFile;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.api.java.function.MapPartitionsFunction;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分布式重写清单（Manifest）并按分区聚集元数据的动作。
 *
 * <p>所属模块：iceberg-spark（actions 子包）。实现 {@link RewriteManifests}，利用 Spark 将
 * 当前分区规范的清单重新组织，使每个分区的元数据尽量聚集，提升后续读取效率。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>筛选待重写清单（按分区规范与自定义谓词）。
 *   <li>基于总大小与条目数估算目标清单数量，按非分区/分区表分别写出新清单。
 *   <li>用新清单替换旧清单并提交，失败时清理新清单文件。
 * </ul>
 *
 * <p>设计意图：通过 {@link #rewriteIf(Predicate)} 过滤、{@link #specId(int)} 指定规范、 {@link #stagingLocation}
 * 自定义暂存位置提供灵活性；分区表按分区列范围分片并排序， 保证同一分区元数据落同一清单；允许 10% 条目数误差以应对估算偏差。
 *
 * <p>上下游关系：由 {@link SparkActions#rewriteManifests(Table)} 创建；操作 Iceberg 表的 manifest 重写 API，依赖
 * {@link SparkDataFile} 进行 Spark 行与 Iceberg DataFile 转换。
 */
public class RewriteManifestsSparkAction
    extends BaseSnapshotUpdateSparkAction<RewriteManifestsSparkAction> implements RewriteManifests {

  public static final String USE_CACHING = "use-caching";
  public static final boolean USE_CACHING_DEFAULT = true;

  private static final Logger LOG = LoggerFactory.getLogger(RewriteManifestsSparkAction.class);

  private final Encoder<ManifestFile> manifestEncoder;
  private final Table table;
  private final int formatVersion;
  private final long targetManifestSizeBytes;

  private PartitionSpec spec = null;
  private Predicate<ManifestFile> predicate = manifest -> true;
  private String stagingLocation = null;

  /**
   * 构造并初始化重写动作。
   *
   * <p>逻辑：设置清单序列化编码器、目标清单大小（取表属性，默认值兜底）、暂存位置（默认为 元数据目录）、新清单格式版本（与表当前一致）。
   */
  RewriteManifestsSparkAction(SparkSession spark, Table table) {
    super(spark);
    this.manifestEncoder = Encoders.javaSerialization(ManifestFile.class);
    this.table = table;
    this.spec = table.spec();
    this.targetManifestSizeBytes =
        PropertyUtil.propertyAsLong(
            table.properties(),
            TableProperties.MANIFEST_TARGET_SIZE_BYTES,
            TableProperties.MANIFEST_TARGET_SIZE_BYTES_DEFAULT);

    // default the staging location to the metadata location
    TableOperations ops = ((HasTableOperations) table).operations();
    Path metadataFilePath = new Path(ops.metadataFileLocation("file"));
    this.stagingLocation = metadataFilePath.getParent().toString();

    // use the current table format version for new manifests
    this.formatVersion = ops.current().formatVersion();
  }

  /** 返回自身，供父类链式调用。 */
  @Override
  protected RewriteManifestsSparkAction self() {
    return this;
  }

  /** 指定要重写的分区规范 ID，校验其存在于表中。 */
  @Override
  public RewriteManifestsSparkAction specId(int specId) {
    Preconditions.checkArgument(table.specs().containsKey(specId), "Invalid spec id %s", specId);
    this.spec = table.specs().get(specId);
    return this;
  }

  /** 设置只重写满足谓词的清单。 */
  @Override
  public RewriteManifestsSparkAction rewriteIf(Predicate<ManifestFile> newPredicate) {
    this.predicate = newPredicate;
    return this;
  }

  /** 设置新清单的暂存输出位置。 */
  @Override
  public RewriteManifestsSparkAction stagingLocation(String newStagingLocation) {
    this.stagingLocation = newStagingLocation;
    return this;
  }

  /**
   * 执行清单重写。
   *
   * <p>逻辑：构造作业描述并以作业组信息包裹 {@link #doExecute} 执行，便于在 Spark UI 中追踪。
   *
   * @return 重写结果（重写的旧清单与新增清单）
   */
  @Override
  public RewriteManifests.Result execute() {
    String desc =
        String.format(
            "Rewriting manifests (staging location=%s) of %s", stagingLocation, table.name());
    JobGroupInfo info = newJobGroupInfo("REWRITE-MANIFESTS", desc);
    return withJobGroupInfo(info, this::doExecute);
  }

  /**
   * 实际执行清单重写的内部方法。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>查找匹配清单，为空或仅一个且目标数为 1 时直接返回空结果。
   *   <li>累加总大小与条目数，校验清单含文件计数；估算目标清单数与每清单目标条目数。
   *   <li>构建清单条目 Dataset，按表是否分区调用对应写出方法。
   *   <li>用新清单替换旧清单并提交，返回结果。
   * </ol>
   */
  private RewriteManifests.Result doExecute() {
    List<ManifestFile> matchingManifests = findMatchingManifests();
    if (matchingManifests.isEmpty()) {
      return ImmutableRewriteManifests.Result.builder()
          .addedManifests(ImmutableList.of())
          .rewrittenManifests(ImmutableList.of())
          .build();
    }

    long totalSizeBytes = 0L;
    int numEntries = 0;

    for (ManifestFile manifest : matchingManifests) {
      ValidationException.check(
          hasFileCounts(manifest), "No file counts in manifest: %s", manifest.path());

      totalSizeBytes += manifest.length();
      numEntries +=
          manifest.addedFilesCount() + manifest.existingFilesCount() + manifest.deletedFilesCount();
    }

    int targetNumManifests = targetNumManifests(totalSizeBytes);
    int targetNumManifestEntries = targetNumManifestEntries(numEntries, targetNumManifests);

    if (targetNumManifests == 1 && matchingManifests.size() == 1) {
      return ImmutableRewriteManifests.Result.builder()
          .addedManifests(ImmutableList.of())
          .rewrittenManifests(ImmutableList.of())
          .build();
    }

    Dataset<Row> manifestEntryDF = buildManifestEntryDF(matchingManifests);

    List<ManifestFile> newManifests;
    if (spec.fields().size() < 1) {
      newManifests = writeManifestsForUnpartitionedTable(manifestEntryDF, targetNumManifests);
    } else {
      newManifests =
          writeManifestsForPartitionedTable(
              manifestEntryDF, targetNumManifests, targetNumManifestEntries);
    }

    replaceManifests(matchingManifests, newManifests);

    return ImmutableRewriteManifests.Result.builder()
        .rewrittenManifests(matchingManifests)
        .addedManifests(newManifests)
        .build();
  }

  /**
   * 构建包含匹配清单活动条目的 Dataset。
   *
   * <p>逻辑：加载 ENTRIES 元数据表并过滤存活条目（status &lt; 2），取其所在清单名，与待重写 清单路径做 left_semi 连接，保留
   * snapshot_id/sequence_number/file_sequence_number/data_file。
   */
  private Dataset<Row> buildManifestEntryDF(List<ManifestFile> manifests) {
    Dataset<Row> manifestDF =
        spark()
            .createDataset(Lists.transform(manifests, ManifestFile::path), Encoders.STRING())
            .toDF("manifest");

    Dataset<Row> manifestEntryDF =
        loadMetadataTable(table, ENTRIES)
            .filter("status < 2") // select only live entries
            .selectExpr(
                "input_file_name() as manifest",
                "snapshot_id",
                "sequence_number",
                "file_sequence_number",
                "data_file");

    Column joinCond = manifestDF.col("manifest").equalTo(manifestEntryDF.col("manifest"));
    return manifestEntryDF
        .join(manifestDF, joinCond, "left_semi")
        .select("snapshot_id", "sequence_number", "file_sequence_number", "data_file");
  }

  /** 为非分区表写出新清单：广播表，按目标清单数 repartition 后写清单（不限制每清单条目数）。 */
  private List<ManifestFile> writeManifestsForUnpartitionedTable(
      Dataset<Row> manifestEntryDF, int numManifests) {
    Broadcast<Table> tableBroadcast = sparkContext().broadcast(SerializableTable.copyOf(table));
    StructType sparkType = (StructType) manifestEntryDF.schema().apply("data_file").dataType();
    Types.StructType combinedPartitionType = Partitioning.partitionType(table);

    // we rely only on the target number of manifests for unpartitioned tables
    // as we should not worry about having too much metadata per partition
    long maxNumManifestEntries = Long.MAX_VALUE;

    return manifestEntryDF
        .repartition(numManifests)
        .mapPartitions(
            toManifests(
                tableBroadcast,
                maxNumManifestEntries,
                stagingLocation,
                formatVersion,
                combinedPartitionType,
                spec,
                sparkType),
            manifestEncoder)
        .collectAsList();
  }

  /**
   * 为分区表写出新清单。
   *
   * <p>逻辑：广播表，按分区列范围分片并分区内排序，使同分区元数据聚集；每清单条目上限为目标 条目数的 1.1 倍（容忍估算偏差），通过 {@link #toManifests}
   * 在分区内可能切分为两个清单。
   */
  private List<ManifestFile> writeManifestsForPartitionedTable(
      Dataset<Row> manifestEntryDF, int numManifests, int targetNumManifestEntries) {

    Broadcast<Table> tableBroadcast = sparkContext().broadcast(SerializableTable.copyOf(table));
    StructType sparkType = (StructType) manifestEntryDF.schema().apply("data_file").dataType();
    Types.StructType combinedPartitionType = Partitioning.partitionType(table);

    // we allow the actual size of manifests to be 10% higher if the estimation is not precise
    // enough
    long maxNumManifestEntries = (long) (1.1 * targetNumManifestEntries);

    return withReusableDS(
        manifestEntryDF,
        df -> {
          Column partitionColumn = df.col("data_file.partition");
          return df.repartitionByRange(numManifests, partitionColumn)
              .sortWithinPartitions(partitionColumn)
              .mapPartitions(
                  toManifests(
                      tableBroadcast,
                      maxNumManifestEntries,
                      stagingLocation,
                      formatVersion,
                      combinedPartitionType,
                      spec,
                      sparkType),
                  manifestEncoder)
              .collectAsList();
        });
  }

  /**
   * 以可复用 Dataset 执行函数：启用缓存则 cache，否则按 shuffle 分区数 repartition 重建； 执行后释放缓存。避免同一 Dataset 被多次计算时重复读入。
   */
  private <T, U> U withReusableDS(Dataset<T> ds, Function<Dataset<T>, U> func) {
    Dataset<T> reusableDS;
    boolean useCaching =
        PropertyUtil.propertyAsBoolean(options(), USE_CACHING, USE_CACHING_DEFAULT);
    if (useCaching) {
      reusableDS = ds.cache();
    } else {
      int parallelism = SQLConf.get().numShufflePartitions();
      reusableDS =
          ds.repartition(parallelism).map((MapFunction<T, T>) value -> value, ds.exprEnc());
    }

    try {
      return func.apply(reusableDS);
    } finally {
      if (useCaching) {
        reusableDS.unpersist(false);
      }
    }
  }

  /** 返回当前快照中属于指定分区规范且满足谓词的数据清单列表。 */
  private List<ManifestFile> findMatchingManifests() {
    Snapshot currentSnapshot = table.currentSnapshot();

    if (currentSnapshot == null) {
      return ImmutableList.of();
    }

    return currentSnapshot.dataManifests(table.io()).stream()
        .filter(manifest -> manifest.partitionSpecId() == spec.specId() && predicate.test(manifest))
        .collect(Collectors.toList());
  }

  /** 按总大小与目标清单大小向上取整计算目标清单数。 */
  private int targetNumManifests(long totalSizeBytes) {
    return (int) ((totalSizeBytes + targetManifestSizeBytes - 1) / targetManifestSizeBytes);
  }

  /** 按总条目数与清单数向上取整计算每清单目标条目数。 */
  private int targetNumManifestEntries(int numEntries, int numManifests) {
    return (numEntries + numManifests - 1) / numManifests;
  }

  /** 判断清单是否包含 added/existing/deleted 文件计数字段（重写需要这些信息）。 */
  private boolean hasFileCounts(ManifestFile manifest) {
    return manifest.addedFilesCount() != null
        && manifest.existingFilesCount() != null
        && manifest.deletedFilesCount() != null;
  }

  /**
   * 用新清单替换旧清单并提交。
   *
   * <p>逻辑：构建 rewriteManifests 事务，注册删除与新增清单后提交；若未启用 snapshotIdInheritance
   * 且提交成功则删除新清单文件（已被重写）；提交状态未知时不清理（可能已成功）；其他异常时 清理所有新清单文件后重抛。
   */
  private void replaceManifests(
      Iterable<ManifestFile> deletedManifests, Iterable<ManifestFile> addedManifests) {
    try {
      boolean snapshotIdInheritanceEnabled =
          PropertyUtil.propertyAsBoolean(
              table.properties(),
              TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED,
              TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT);

      org.apache.iceberg.RewriteManifests rewriteManifests = table.rewriteManifests();
      deletedManifests.forEach(rewriteManifests::deleteManifest);
      addedManifests.forEach(rewriteManifests::addManifest);
      commit(rewriteManifests);

      if (!snapshotIdInheritanceEnabled) {
        // delete new manifests as they were rewritten before the commit
        deleteFiles(Iterables.transform(addedManifests, ManifestFile::path));
      }
    } catch (CommitStateUnknownException commitStateUnknownException) {
      // don't clean up added manifest files, because they may have been successfully committed.
      throw commitStateUnknownException;
    } catch (Exception e) {
      // delete all new manifests because the rewrite failed
      deleteFiles(Iterables.transform(addedManifests, ManifestFile::path));
      throw e;
    }
  }

  /** 在工作线程池中并发删除给定文件，失败仅告警不抛出。 */
  private void deleteFiles(Iterable<String> locations) {
    Tasks.foreach(locations)
        .executeWith(ThreadPools.getWorkerPool())
        .noRetry()
        .suppressFailureWhenFinished()
        .onFailure((location, exc) -> LOG.warn("Failed to delete: {}", location, exc))
        .run(location -> table.io().deleteFile(location));
  }

  /**
   * 将 rows 的 [startIndex, endIndex) 区间写出为一个清单文件。
   *
   * <p>逻辑：生成唯一清单名，创建 Avro 输出文件与 ManifestWriter；逐行读取 snapshotId、 sequenceNumber、fileSequenceNumber 与
   * data_file，用 SparkDataFile 包装后以 existing 条目写入； finally 关闭 writer 并返回 ManifestFile。
   */
  private static ManifestFile writeManifest(
      List<Row> rows,
      int startIndex,
      int endIndex,
      Broadcast<Table> tableBroadcast,
      String location,
      int format,
      Types.StructType combinedPartitionType,
      PartitionSpec spec,
      StructType sparkType)
      throws IOException {

    String manifestName = "optimized-m-" + UUID.randomUUID();
    Path manifestPath = new Path(location, manifestName);
    OutputFile outputFile =
        tableBroadcast
            .value()
            .io()
            .newOutputFile(FileFormat.AVRO.addExtension(manifestPath.toString()));

    Types.StructType combinedFileType = DataFile.getType(combinedPartitionType);
    Types.StructType manifestFileType = DataFile.getType(spec.partitionType());
    SparkDataFile wrapper = new SparkDataFile(combinedFileType, manifestFileType, sparkType);

    ManifestWriter<DataFile> writer = ManifestFiles.write(format, spec, outputFile, null);

    try {
      for (int index = startIndex; index < endIndex; index++) {
        Row row = rows.get(index);
        long snapshotId = row.getLong(0);
        long sequenceNumber = row.getLong(1);
        Long fileSequenceNumber = row.isNullAt(2) ? null : row.getLong(2);
        Row file = row.getStruct(3);
        writer.existing(wrapper.wrap(file), snapshotId, sequenceNumber, fileSequenceNumber);
      }
    } finally {
      writer.close();
    }

    return writer.toManifestFile();
  }

  /**
   * 返回分区级 MapPartitionsFunction：将分区内行写出为清单。
   *
   * <p>逻辑：行数不超过上限时写一个清单；超过则从中间切分写两个清单，控制单清单条目数。
   */
  private static MapPartitionsFunction<Row, ManifestFile> toManifests(
      Broadcast<Table> tableBroadcast,
      long maxNumManifestEntries,
      String location,
      int format,
      Types.StructType combinedPartitionType,
      PartitionSpec spec,
      StructType sparkType) {

    return rows -> {
      List<Row> rowsAsList = Lists.newArrayList(rows);

      if (rowsAsList.isEmpty()) {
        return Collections.emptyIterator();
      }

      List<ManifestFile> manifests = Lists.newArrayList();
      if (rowsAsList.size() <= maxNumManifestEntries) {
        manifests.add(
            writeManifest(
                rowsAsList,
                0,
                rowsAsList.size(),
                tableBroadcast,
                location,
                format,
                combinedPartitionType,
                spec,
                sparkType));
      } else {
        int midIndex = rowsAsList.size() / 2;
        manifests.add(
            writeManifest(
                rowsAsList,
                0,
                midIndex,
                tableBroadcast,
                location,
                format,
                combinedPartitionType,
                spec,
                sparkType));
        manifests.add(
            writeManifest(
                rowsAsList,
                midIndex,
                rowsAsList.size(),
                tableBroadcast,
                location,
                format,
                combinedPartitionType,
                spec,
                sparkType));
      }

      return manifests.iterator();
    };
  }
}
