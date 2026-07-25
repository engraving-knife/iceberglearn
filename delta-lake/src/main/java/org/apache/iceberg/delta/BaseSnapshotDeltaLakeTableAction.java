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
package org.apache.iceberg.delta;

import io.delta.standalone.DeltaLog;
import io.delta.standalone.VersionLog;
import io.delta.standalone.actions.Action;
import io.delta.standalone.actions.AddFile;
import io.delta.standalone.actions.RemoveFile;
import io.delta.standalone.exceptions.DeltaStandaloneException;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.DeleteFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.ManageSnapshots;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.OverwriteFiles;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.MappingUtil;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delta Lake 表快照为 Iceberg 表的核心实现（Action 模式）。
 *
 * <p>所属模块：iceberg-delta-lake（Delta Lake 表迁移到 Iceberg 的支持模块，本类是该模块的核心实现类）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取 Delta Lake 表的日志（DeltaLog）和快照，获取 schema、分区规格及数据文件列表。
 *   <li>将 Delta Lake 的 schema 和分区信息转换为 Iceberg 的 {@link Schema} 和 {@link PartitionSpec}。
 *   <li>在 Iceberg Catalog 中创建新表（通过事务），并将 Delta Lake 的初始快照及后续版本变更 逐个提交为 Iceberg
 *       事务操作（Append/Delete/Overwrite）。
 *   <li>为每个迁移的 Delta 版本打上标签（tag），便于溯源到原始 Delta 版本和时间戳。
 *   <li>收集数据文件统计信息（Metrics），保证 Iceberg 表的查询优化可用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>单事务提交：所有变更在一个 Iceberg {@link Transaction} 中完成，保证迁移的原子性—— 要么全部成功，要么表不会被创建。
 *   <li>增量回放：先提交初始可构造快照，再按版本日志逐条回放 AddFile/RemoveFile 动作， 精确还原 Delta Lake 表的演变历史为 Iceberg 快照序列。
 *   <li>容错跳过：初始快照可能因 checkpoint 截断或 VACUUM 清理而不可构造，此时自动跳到下一版本 重试，直到找到可构造的起点。
 *   <li>NameMapping：迁移时写入 {@code DEFAULT_NAME_MAPPING} 属性，使 Iceberg 能按字段名匹配 读取旧数据文件中的列统计，解决字段 ID
 *       对齐问题。
 * </ul>
 *
 * <p>上下游关系：由 {@link DeltaLakeToIcebergMigrationActionsProvider#snapshotDeltaLakeTable} 创建；上游依赖
 * Delta Lake Standalone API（{@link DeltaLog}）和 Hadoop {@link Configuration}； 下游通过 Iceberg {@link
 * Catalog} 创建表并提交事务。schema 转换委托给 {@link DeltaLakeTypeToType}。
 */
class BaseSnapshotDeltaLakeTableAction implements SnapshotDeltaLakeTable {

  private static final Logger LOG = LoggerFactory.getLogger(BaseSnapshotDeltaLakeTableAction.class);

  private static final String SNAPSHOT_SOURCE_PROP = "snapshot_source";
  private static final String DELTA_SOURCE_VALUE = "delta";
  private static final String ORIGINAL_LOCATION_PROP = "original_location";
  private static final String PARQUET_SUFFIX = ".parquet";
  private static final String DELTA_VERSION_TAG_PREFIX = "delta-version-";
  private static final String DELTA_TIMESTAMP_TAG_PREFIX = "delta-ts-";
  private final ImmutableMap.Builder<String, String> additionalPropertiesBuilder =
      ImmutableMap.builder();
  private DeltaLog deltaLog;
  private Catalog icebergCatalog;
  private final String deltaTableLocation;
  private TableIdentifier newTableIdentifier;
  private String newTableLocation;
  private HadoopFileIO deltaLakeFileIO;
  private long deltaStartVersion;

  /**
   * 构造一个 Delta Lake 表快照动作。
   *
   * <p>初始化时将新表位置默认设为 Delta Lake 表的位置，调用方可通过 {@link #tableLocation} 覆盖。
   *
   * @param deltaTableLocation Delta Lake 表的存储路径
   */
  BaseSnapshotDeltaLakeTableAction(String deltaTableLocation) {
    this.deltaTableLocation = deltaTableLocation;
    this.newTableLocation = deltaTableLocation;
  }

  /**
   * 批量设置新建 Iceberg 表的表属性。同名属性会被覆盖。
   *
   * @param properties 属性键值对
   * @return 当前动作实例，用于链式调用
   */
  @Override
  public SnapshotDeltaLakeTable tableProperties(Map<String, String> properties) {
    additionalPropertiesBuilder.putAll(properties);
    return this;
  }

  /**
   * 设置新建 Iceberg 表的单个表属性。同名属性会被覆盖。
   *
   * @param name 属性名
   * @param value 属性值
   * @return 当前动作实例，用于链式调用
   */
  @Override
  public SnapshotDeltaLakeTable tableProperty(String name, String value) {
    additionalPropertiesBuilder.put(name, value);
    return this;
  }

  /**
   * 设置新建 Iceberg 表的存储位置。
   *
   * @param location 新表存储路径
   * @return 当前动作实例，用于链式调用
   */
  @Override
  public SnapshotDeltaLakeTable tableLocation(String location) {
    this.newTableLocation = location;
    return this;
  }

  /**
   * 设置新建 Iceberg 表的标识符。执行前必须设置。
   *
   * @param identifier 表标识符（命名空间 + 表名）
   * @return 当前动作实例，用于链式调用
   */
  @Override
  public SnapshotDeltaLakeTable as(TableIdentifier identifier) {
    this.newTableIdentifier = identifier;
    return this;
  }

  /**
   * 设置新建 Iceberg 表所使用的 Iceberg Catalog。执行前必须设置。
   *
   * @param catalog Iceberg Catalog 实例
   * @return 当前动作实例，用于链式调用
   */
  @Override
  public SnapshotDeltaLakeTable icebergCatalog(Catalog catalog) {
    this.icebergCatalog = catalog;
    return this;
  }

  /**
   * 设置访问 Delta Lake 表所用的 Hadoop 配置。
   *
   * <p>逻辑：基于配置创建 {@link DeltaLog}（用于读取 Delta 日志）和 {@link HadoopFileIO} （用于读取数据文件），并获取 Delta Lake
   * 表中最早可用版本号（时间戳 0 之后的首个版本）。
   *
   * @param conf Hadoop 配置
   * @return 当前动作实例，用于链式调用
   */
  @Override
  public SnapshotDeltaLakeTable deltaLakeConfiguration(Configuration conf) {
    this.deltaLog = DeltaLog.forTable(conf, deltaTableLocation);
    this.deltaLakeFileIO = new HadoopFileIO(conf);
    // get the earliest version available in the delta lake table
    this.deltaStartVersion = deltaLog.getVersionAtOrAfterTimestamp(0L);
    return this;
  }

  /**
   * 执行 Delta Lake 表到 Iceberg 表的快照迁移。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验必要参数（Iceberg catalog/identifier、Delta Lake 配置、表存在性）。
   *   <li>获取 Delta Lake 最新快照，转换 schema 和分区规格。
   *   <li>在 Iceberg Catalog 中创建建表事务，并写入 NameMapping 属性。
   *   <li>提交初始可构造快照（{@link #commitInitialDeltaSnapshotToIcebergTransaction}）， 得到迁移起始版本号。
   *   <li>从起始版本之后逐条回放版本日志（{@link #commitDeltaVersionLogToIcebergTransaction}）， 将 AddFile/RemoveFile
   *       转为 Iceberg 的 Append/Delete/Overwrite 操作。
   *   <li>提交整个事务，返回迁移结果（数据文件数）。
   * </ol>
   *
   * @return 包含已迁移数据文件数的执行结果
   * @throws IllegalArgumentException 当必要参数未配置时
   * @throws ValidationException 当 Delta Lake 表不存在或无可构造快照时
   */
  @Override
  public SnapshotDeltaLakeTable.Result execute() {
    Preconditions.checkArgument(
        icebergCatalog != null && newTableIdentifier != null,
        "Iceberg catalog and identifier cannot be null. Make sure to configure the action with a valid Iceberg catalog and identifier.");
    Preconditions.checkArgument(
        deltaLog != null && deltaLakeFileIO != null,
        "Make sure to configure the action with a valid deltaLakeConfiguration");
    Preconditions.checkArgument(
        deltaLog.tableExists(),
        "Delta Lake table does not exist at the given location: %s",
        deltaTableLocation);
    ImmutableSet.Builder<String> migratedDataFilesBuilder = ImmutableSet.builder();
    io.delta.standalone.Snapshot updatedSnapshot = deltaLog.update();
    Schema schema = convertDeltaLakeSchema(updatedSnapshot.getMetadata().getSchema());
    PartitionSpec partitionSpec = getPartitionSpecFromDeltaSnapshot(schema, updatedSnapshot);
    Transaction icebergTransaction =
        icebergCatalog.newCreateTableTransaction(
            newTableIdentifier,
            schema,
            partitionSpec,
            newTableLocation,
            destTableProperties(updatedSnapshot, deltaTableLocation));
    icebergTransaction
        .table()
        .updateProperties()
        .set(
            TableProperties.DEFAULT_NAME_MAPPING,
            NameMappingParser.toJson(MappingUtil.create(icebergTransaction.table().schema())))
        .commit();
    long constructableStartVersion =
        commitInitialDeltaSnapshotToIcebergTransaction(
            updatedSnapshot.getVersion(), icebergTransaction, migratedDataFilesBuilder);
    Iterator<VersionLog> versionLogIterator =
        deltaLog.getChanges(
            constructableStartVersion + 1, false // not throw exception when data loss detected
            );
    while (versionLogIterator.hasNext()) {
      VersionLog versionLog = versionLogIterator.next();
      commitDeltaVersionLogToIcebergTransaction(
          versionLog, icebergTransaction, migratedDataFilesBuilder);
    }
    icebergTransaction.commitTransaction();

    long totalDataFiles = migratedDataFilesBuilder.build().size();
    LOG.info(
        "Successfully created Iceberg table {} from Delta Lake table at {}, total data file count: {}",
        newTableIdentifier,
        deltaTableLocation,
        totalDataFiles);
    return ImmutableSnapshotDeltaLakeTable.Result.builder()
        .snapshotDataFilesCount(totalDataFiles)
        .build();
  }

  /**
   * 将 Delta Lake 的 StructType schema 转换为 Iceberg 的 {@link Schema}。
   *
   * <p>委托 {@link DeltaLakeDataTypeVisitor#visit} 驱动 {@link DeltaLakeTypeToType} 访问者
   * 完成类型转换，再提取顶层字段构成 Schema。
   *
   * @param deltaSchema Delta Lake 的 schema
   * @return 转换后的 Iceberg Schema
   */
  private Schema convertDeltaLakeSchema(io.delta.standalone.types.StructType deltaSchema) {
    Type converted =
        DeltaLakeDataTypeVisitor.visit(deltaSchema, new DeltaLakeTypeToType(deltaSchema));
    return new Schema(converted.asNestedType().asStructType().fields());
  }

  /**
   * 从 Delta Lake 快照中提取分区规格。
   *
   * <p>逻辑：读取 Delta Lake 表的分区列名列表，若为空则返回非分区表 （{@link PartitionSpec#unpartitioned()}）；否则对每个分区列创建
   * identity 分区字段。
   *
   * @param schema 已转换的 Iceberg Schema
   * @param deltaSnapshot Delta Lake 快照
   * @return Iceberg 分区规格
   */
  private PartitionSpec getPartitionSpecFromDeltaSnapshot(
      Schema schema, io.delta.standalone.Snapshot deltaSnapshot) {
    List<String> partitionNames = deltaSnapshot.getMetadata().getPartitionColumns();
    if (partitionNames.isEmpty()) {
      return PartitionSpec.unpartitioned();
    }

    PartitionSpec.Builder builder = PartitionSpec.builderFor(schema);
    for (String partitionName : partitionNames) {
      builder.identity(partitionName);
    }
    return builder.build();
  }

  /**
   * 将初始 Delta 快照提交到 Iceberg 事务。
   *
   * <p>逻辑：从 {@code deltaStartVersion} 开始逐版本尝试，直到 {@code latestVersion}：
   *
   * <ol>
   *   <li>获取该版本快照的全部数据文件（{@code AddFile}）。
   *   <li>将每个 AddFile 转换为 Iceberg {@link DataFile}，并记录到已迁移文件集合。
   *   <li>通过 {@link AppendFiles} 追加这些文件，并为当前快照打标签。
   *   <li>若该版本因数据文件被 VACUUM 删除或早于最早 checkpoint 而不可构造（抛出
   *       NotFoundException/IllegalArgumentException/DeltaStandaloneException），则跳到下一版本重试。
   * </ol>
   *
   * <p>若所有版本均不可构造，抛出 {@link ValidationException}。
   *
   * <p>不可构造的两种情况详见 Delta Lake 的 <a
   * href="https://docs.delta.io/latest/delta-batch.html#-data-retention">Data Retention</a> 文档。
   *
   * @param latestVersion Delta Lake 表的最新版本号
   * @param transaction Iceberg 事务
   * @param migratedDataFilesBuilder 已迁移数据文件路径收集器
   * @return 成功提交的初始 Delta 版本号
   * @throws ValidationException 当不存在任何可构造快照时
   */
  private long commitInitialDeltaSnapshotToIcebergTransaction(
      long latestVersion,
      Transaction transaction,
      ImmutableSet.Builder<String> migratedDataFilesBuilder) {
    long constructableStartVersion = deltaStartVersion;
    while (constructableStartVersion <= latestVersion) {
      try {
        List<AddFile> initDataFiles =
            deltaLog.getSnapshotForVersionAsOf(constructableStartVersion).getAllFiles();
        List<DataFile> filesToAdd = Lists.newArrayList();
        for (AddFile addFile : initDataFiles) {
          DataFile dataFile = buildDataFileFromAction(addFile, transaction.table());
          filesToAdd.add(dataFile);
          migratedDataFilesBuilder.add(dataFile.path().toString());
        }

        // AppendFiles case
        AppendFiles appendFiles = transaction.newAppend();
        filesToAdd.forEach(appendFiles::appendFile);
        appendFiles.commit();
        tagCurrentSnapshot(constructableStartVersion, transaction);

        return constructableStartVersion;
      } catch (NotFoundException | IllegalArgumentException | DeltaStandaloneException e) {
        constructableStartVersion++;
      }
    }

    throw new ValidationException(
        "Delta Lake table at %s contains no constructable snapshot", deltaTableLocation);
  }

  /**
   * 将单个 Delta 版本日志提交到 Iceberg 事务。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>过滤出版本日志中的数据变更动作（仅 {@link AddFile} 和 {@link RemoveFile}）。
   *   <li>将每个动作转换为 Iceberg {@link DataFile}，分别归入"待添加"和"待删除"列表。
   *   <li>根据添加/删除文件的有无选择操作类型：
   *       <ul>
   *         <li>两者都有 → {@link OverwriteFiles}（DELETE/UPDATE 场景）
   *         <li>仅有添加 → {@link AppendFiles}（INSERT 场景）
   *         <li>仅有删除 → {@link DeleteFiles}（整文件删除场景）
   *         <li>两者都无 → 空追加（dummy append，仅为打标签）
   *       </ul>
   *   <li>为当前快照打上版本标签。
   * </ol>
   *
   * @param versionLog 待提交的 Delta 版本日志
   * @param transaction Iceberg 表事务
   * @param migratedDataFilesBuilder 已迁移数据文件路径收集器
   * @throws ValidationException 当遇到不支持的 Action 类型时
   */
  private void commitDeltaVersionLogToIcebergTransaction(
      VersionLog versionLog,
      Transaction transaction,
      ImmutableSet.Builder<String> migratedDataFilesBuilder) {
    // Only need actions related to data change: AddFile and RemoveFile
    List<Action> dataFileActions =
        versionLog.getActions().stream()
            .filter(action -> action instanceof AddFile || action instanceof RemoveFile)
            .collect(Collectors.toList());

    List<DataFile> filesToAdd = Lists.newArrayList();
    List<DataFile> filesToRemove = Lists.newArrayList();
    for (Action action : dataFileActions) {
      DataFile dataFile = buildDataFileFromAction(action, transaction.table());
      if (action instanceof AddFile) {
        filesToAdd.add(dataFile);
      } else if (action instanceof RemoveFile) {
        filesToRemove.add(dataFile);
      } else {
        throw new ValidationException(
            "The action %s's is unsupported", action.getClass().getSimpleName());
      }
      migratedDataFilesBuilder.add(dataFile.path().toString());
    }

    if (filesToAdd.size() > 0 && filesToRemove.size() > 0) {
      // OverwriteFiles case
      OverwriteFiles overwriteFiles = transaction.newOverwrite();
      filesToAdd.forEach(overwriteFiles::addFile);
      filesToRemove.forEach(overwriteFiles::deleteFile);
      overwriteFiles.commit();
    } else if (filesToAdd.size() > 0) {
      // AppendFiles case
      AppendFiles appendFiles = transaction.newAppend();
      filesToAdd.forEach(appendFiles::appendFile);
      appendFiles.commit();
    } else if (filesToRemove.size() > 0) {
      // DeleteFiles case
      DeleteFiles deleteFiles = transaction.newDelete();
      filesToRemove.forEach(deleteFiles::deleteFile);
      deleteFiles.commit();
    } else {
      // No data change case, dummy append to tag the snapshot
      transaction.newAppend().commit();
    }

    tagCurrentSnapshot(versionLog.getVersion(), transaction);
  }

  /**
   * 从 Delta Lake 的 Action（AddFile 或 RemoveFile）构建 Iceberg {@link DataFile}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>根据 Action 类型（AddFile/RemoveFile）提取文件路径、文件大小和分区值。
   *   <li>拼接完整文件路径（相对路径补全为绝对路径）。
   *   <li>校验分区值非 null（非分区表也要求空 Map 而非 null）。
   *   <li>通过路径后缀判断文件格式（目前仅支持 Parquet）。
   *   <li>打开 {@link InputFile}，校验文件存在；若 Action 未携带文件大小则从文件系统读取。
   *   <li>基于 {@link MetricsConfig} 和 {@link NameMapping} 从文件中提取统计指标（Metrics）。
   *   <li>按分区字段名拼接分区路径字符串，构建并返回 {@link DataFile}。
   * </ol>
   *
   * @param action Delta Lake 的文件动作（AddFile 或 RemoveFile）
   * @param table Iceberg 表（用于获取分区规格和表属性）
   * @return 构建好的 Iceberg {@link DataFile}
   * @throws ValidationException 当 Action 类型不支持或文件格式不支持时
   * @throws NotFoundException 当引用的文件在存储中不存在时
   */
  private DataFile buildDataFileFromAction(Action action, Table table) {
    PartitionSpec spec = table.spec();
    String path;
    long fileSize;
    Long nullableFileSize;
    Map<String, String> partitionValues;

    if (action instanceof AddFile) {
      AddFile addFile = (AddFile) action;
      path = addFile.getPath();
      nullableFileSize = addFile.getSize();
      partitionValues = addFile.getPartitionValues();
    } else if (action instanceof RemoveFile) {
      RemoveFile removeFile = (RemoveFile) action;
      path = removeFile.getPath();
      nullableFileSize = removeFile.getSize().orElse(null);
      partitionValues = removeFile.getPartitionValues();
    } else {
      throw new ValidationException(
          "Unexpected action type for Delta Lake: %s", action.getClass().getSimpleName());
    }

    String fullFilePath = getFullFilePath(path, deltaLog.getPath().toString());
    // For unpartitioned table, the partitionValues should be an empty map rather than null
    Preconditions.checkArgument(
        partitionValues != null,
        String.format("File %s does not specify a partitionValues", fullFilePath));

    FileFormat format = determineFileFormatFromPath(fullFilePath);
    InputFile file = deltaLakeFileIO.newInputFile(fullFilePath);
    if (!file.exists()) {
      throw new NotFoundException(
          "File %s is referenced in the logs of Delta Lake table at %s, but cannot be found in the storage",
          fullFilePath, deltaTableLocation);
    }

    // If the file size is not specified, the size should be read from the file
    if (nullableFileSize != null) {
      fileSize = nullableFileSize;
    } else {
      fileSize = file.getLength();
    }

    // get metrics from the file
    MetricsConfig metricsConfig = MetricsConfig.forTable(table);
    String nameMappingString = table.properties().get(TableProperties.DEFAULT_NAME_MAPPING);
    NameMapping nameMapping =
        nameMappingString != null ? NameMappingParser.fromJson(nameMappingString) : null;
    Metrics metrics = getMetricsForFile(file, format, metricsConfig, nameMapping);

    String partition =
        spec.fields().stream()
            .map(PartitionField::name)
            .map(name -> String.format("%s=%s", name, partitionValues.get(name)))
            .collect(Collectors.joining("/"));

    return DataFiles.builder(spec)
        .withPath(fullFilePath)
        .withFormat(format)
        .withFileSizeInBytes(fileSize)
        .withMetrics(metrics)
        .withPartitionPath(partition)
        .build();
  }

  /**
   * 根据文件路径后缀判断文件格式。
   *
   * @param path 文件路径
   * @return 文件格式枚举值
   * @throws ValidationException 当文件格式不支持时
   */
  private FileFormat determineFileFormatFromPath(String path) {
    if (path.endsWith(PARQUET_SUFFIX)) {
      return FileFormat.PARQUET;
    } else {
      throw new ValidationException("Do not support file format in path %s", path);
    }
  }

  /**
   * 从数据文件中提取统计指标（Metrics）。
   *
   * <p>目前仅支持 Parquet 格式，通过 {@link ParquetUtil#fileMetrics} 读取列级统计。
   *
   * @param file 数据文件
   * @param format 文件格式
   * @param metricsSpec 指标采集配置
   * @param mapping 字段名映射（用于按名匹配列，可为 null）
   * @return 文件级统计指标
   * @throws ValidationException 当文件格式不支持指标采集时
   */
  private Metrics getMetricsForFile(
      InputFile file, FileFormat format, MetricsConfig metricsSpec, NameMapping mapping) {
    if (format == FileFormat.PARQUET) {
      return ParquetUtil.fileMetrics(file, metricsSpec, mapping);
    }
    throw new ValidationException("Cannot get metrics from file format: %s", format);
  }

  /**
   * 组装目标 Iceberg 表的属性集合。
   *
   * <p>逻辑：合并三部分属性——用户通过 Builder 设置的额外属性、Delta Lake 快照自带的表配置、 以及迁移元信息属性（{@code
   * snapshot_source=delta} 和 {@code original_location}）。
   *
   * @param deltaSnapshot Delta Lake 快照（提供原始表配置）
   * @param originalLocation Delta Lake 表原始路径
   * @return 合并后的表属性 Map
   */
  private Map<String, String> destTableProperties(
      io.delta.standalone.Snapshot deltaSnapshot, String originalLocation) {
    additionalPropertiesBuilder.putAll(deltaSnapshot.getMetadata().getConfiguration());
    additionalPropertiesBuilder.putAll(
        ImmutableMap.of(
            SNAPSHOT_SOURCE_PROP, DELTA_SOURCE_VALUE, ORIGINAL_LOCATION_PROP, originalLocation));

    return additionalPropertiesBuilder.build();
  }

  /**
   * 为当前 Iceberg 快照打上 Delta 版本标签。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>获取 Iceberg 表当前快照 ID。
   *   <li>创建以 {@code delta-version-<版本号>} 为名的标签，指向当前快照。
   *   <li>若该 Delta 版本有提交时间戳，再创建以 {@code delta-ts-<时间戳>} 为名的标签。
   *   <li>提交快照管理操作。
   * </ol>
   *
   * <p>设计意图：通过标签建立 Iceberg 快照与 Delta Lake 原始版本/时间戳的对应关系， 便于迁移后溯源和审计。
   *
   * @param deltaVersion Delta Lake 版本号
   * @param transaction Iceberg 事务
   */
  private void tagCurrentSnapshot(long deltaVersion, Transaction transaction) {
    long currentSnapshotId = transaction.table().currentSnapshot().snapshotId();

    ManageSnapshots manageSnapshots = transaction.manageSnapshots();
    manageSnapshots.createTag(DELTA_VERSION_TAG_PREFIX + deltaVersion, currentSnapshotId);

    Timestamp deltaVersionTimestamp = deltaLog.getCommitInfoAt(deltaVersion).getTimestamp();
    if (deltaVersionTimestamp != null) {
      manageSnapshots.createTag(
          DELTA_TIMESTAMP_TAG_PREFIX + deltaVersionTimestamp.getTime(), currentSnapshotId);
    }
    manageSnapshots.commit();
  }

  /**
   * 获取数据文件的完整路径。
   *
   * <p>输入路径可能是绝对路径或相对路径：若为绝对路径直接返回（解码后），若为相对路径 则拼接 {@code tableRoot} 前缀。路径中的 URL 编码字符会被解码。
   *
   * @param path {@link AddFile#getPath()} 或 {@link RemoveFile#getPath()} 返回的路径（绝对或相对）
   * @param tableRoot Delta 表的根路径
   * @return 解码后的完整文件路径
   * @throws IllegalArgumentException 当路径无法解码时
   */
  private static String getFullFilePath(String path, String tableRoot) {
    URI dataFileUri = URI.create(path);
    try {
      String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
      if (dataFileUri.isAbsolute()) {
        return decodedPath;
      } else {
        return tableRoot + File.separator + decodedPath;
      }
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException(String.format("Cannot decode path %s", path), e);
    }
  }
}
