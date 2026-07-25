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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.ManifestEvaluator;
import org.apache.iceberg.expressions.Projections;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ParallelIterable;
import org.apache.iceberg.util.TableScanUtil;

/**
 * 所属模块：iceberg-core；元数据表（Metadata Table）层。
 *
 * <p>职责：将底层表的"位置删除文件（position delete files）"以可扫描的元数据表形式暴露出来， 其 {@link Scan} 输出 {@link
 * PositionDeletesScanTask}。具体包括：
 *
 * <ul>
 *   <li>定义位置删除元数据表的 Schema（删除文件路径、删除位置、被删除行、分区值、spec_id 等）
 *   <li>提供 {@link BatchScan} 实现以读取位置删除文件
 *   <li>对底表过滤条件做分区投影，用于过滤删除 Manifest
 * </ul>
 *
 * <p>设计意图：作为只读元数据视图，将位置删除文件作为独立行数据暴露，便于读取、重写或分析； 通过 Caffeine 缓存按 specId 复用 {@link
 * ManifestEvaluator}、{@link ResidualEvaluator} 与 规格字符串，避免重复构造。
 *
 * <p>上下游关系：依赖 {@link Table}、{@link ManifestFiles}、{@link PartitionSpec} 等； 被 {@code
 * PositionDeletesRewriteAction} 等需要对位置删除文件重写的上层逻辑使用。
 */
public class PositionDeletesTable extends BaseMetadataTable {

  // 删除行所属分区的列名
  public static final String PARTITION = "partition";
  // 分区规格 ID 的列名
  public static final String SPEC_ID = "spec_id";
  // 删除文件路径的列名
  public static final String DELETE_FILE_PATH = "delete_file_path";

  // 位置删除元数据表的 Schema
  private final Schema schema;
  // 默认分区规格 ID
  private final int defaultSpecId;
  // 经过 transform 后的分区规格映射（specId -> PartitionSpec）
  private final Map<Integer, PartitionSpec> specs;

  /**
   * 构造位置删除元数据表，使用 {@code 原表名 + ".position_deletes"} 作为表名。
   *
   * @param table 底层 Iceberg 表
   */
  PositionDeletesTable(Table table) {
    this(table, table.name() + ".position_deletes");
  }

  /**
   * 构造位置删除元数据表，允许自定义表名。
   *
   * <p>逻辑：调用父类构造器后，计算 Schema、记录默认 specId，并对底表的所有分区规格做 transform。
   *
   * @param table 底层 Iceberg 表
   * @param name 元数据表名
   */
  PositionDeletesTable(Table table, String name) {
    super(table, name);
    this.schema = calculateSchema();
    this.defaultSpecId = table.spec().specId();
    this.specs = transformSpecs(schema(), table.specs());
  }

  /**
   * 返回该元数据表的类型标识。
   *
   * @return {@link MetadataTableType#POSITION_DELETES}
   */
  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.POSITION_DELETES;
  }

  /**
   * 该元数据表不支持 {@link TableScan}，必须使用 {@link #newBatchScan()}。
   *
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public TableScan newScan() {
    throw new UnsupportedOperationException(
        "Cannot create TableScan from table of type POSITION_DELETES");
  }

  /**
   * 创建批量扫描器以读取位置删除文件。
   *
   * @return 位置删除批量扫描器
   */
  @Override
  public BatchScan newBatchScan() {
    return new PositionDeletesBatchScan(table(), schema());
  }

  /**
   * 返回位置删除元数据表的 Schema。
   *
   * @return 当前表的 Schema
   */
  @Override
  public Schema schema() {
    return schema;
  }

  /**
   * 返回默认 specId 对应的分区规格。
   *
   * @return 默认分区规格
   */
  @Override
  public PartitionSpec spec() {
    return specs.get(defaultSpecId);
  }

  /**
   * 返回所有经过 transform 后的分区规格映射。
   *
   * @return specId 到 PartitionSpec 的映射
   */
  @Override
  public Map<Integer, PartitionSpec> specs() {
    return specs;
  }

  /**
   * 返回以 "write." 为前缀的底表写属性，供 {@code PositionDeletesRewriteAction} 等使用。
   *
   * <p>设计意图：仅暴露与写入相关的属性，且保持与底表一致；返回不可修改 Map 防止外部篡改。
   *
   * @return 不可修改的写属性映射
   */
  @Override
  public Map<String, String> properties() {
    // The write properties are needed by PositionDeletesRewriteAction,
    // these properties should respect the ones of BaseTable.
    return Collections.unmodifiableMap(
        table().properties().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("write."))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  /**
   * 计算位置删除元数据表的 Schema。
   *
   * <p>逻辑：基于底表分区类型构造包含删除文件路径、删除位置、被删除行、分区、spec_id、文件路径的 Schema。对于非分区表（分区类型字段为空），为避免返回空 struct，剔除
   * partition 列。
   *
   * @return 计算得到的 Schema
   */
  private Schema calculateSchema() {
    Types.StructType partitionType = Partitioning.partitionType(table());
    Schema result =
        new Schema(
            MetadataColumns.DELETE_FILE_PATH,
            MetadataColumns.DELETE_FILE_POS,
            Types.NestedField.optional(
                MetadataColumns.DELETE_FILE_ROW_FIELD_ID,
                MetadataColumns.DELETE_FILE_ROW_FIELD_NAME,
                table().schema().asStruct(),
                MetadataColumns.DELETE_FILE_ROW_DOC),
            Types.NestedField.required(
                MetadataColumns.PARTITION_COLUMN_ID,
                PARTITION,
                partitionType,
                "Partition that position delete row belongs to"),
            Types.NestedField.required(
                MetadataColumns.SPEC_ID_COLUMN_ID,
                SPEC_ID,
                Types.IntegerType.get(),
                MetadataColumns.SPEC_ID_COLUMN_DOC),
            Types.NestedField.required(
                MetadataColumns.FILE_PATH_COLUMN_ID,
                DELETE_FILE_PATH,
                Types.StringType.get(),
                MetadataColumns.FILE_PATH_COLUMN_DOC));

    if (partitionType.fields().size() > 0) {
      return result;
    } else {
      // avoid returning an empty struct, which is not always supported.
      // instead, drop the partition field
      return TypeUtil.selectNot(result, Sets.newHashSet(MetadataColumns.PARTITION_COLUMN_ID));
    }
  }

  /**
   * 位置删除元数据表的批量扫描实现，负责规划 {@link ScanTask} 以读取位置删除文件。
   *
   * <p>设计意图：扩展 {@link SnapshotScan}，支持底表过滤条件（通过 {@link #baseTableFilter(Expression)} 设置），并使用
   * Caffeine 缓存按 specId 复用评估器与规格字符串，避免重复构造。
   */
  public static class PositionDeletesBatchScan
      extends SnapshotScan<BatchScan, ScanTask, ScanTaskGroup<ScanTask>> implements BatchScan {

    // 应用在底表上的过滤条件，仅其分区投影部分会对位置删除表生效
    private Expression baseTableFilter = Expressions.alwaysTrue();

    /**
     * 构造位置删除批量扫描器，使用空的扫描上下文。
     *
     * @param table 底层表
     * @param schema 扫描 Schema
     */
    protected PositionDeletesBatchScan(Table table, Schema schema) {
      super(table, schema, TableScanContext.empty());
    }

    /**
     * 已废弃的构造器，将在 v1.5.0 移除。
     *
     * @param table 底层表
     * @param schema 扫描 Schema
     * @param context 扫描上下文
     * @deprecated 该 API 将在 v1.5.0 移除
     */
    @Deprecated
    protected PositionDeletesBatchScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, context);
    }

    /**
     * 构造位置删除批量扫描器，允许指定扫描上下文与底表过滤条件。
     *
     * @param table 底层表
     * @param schema 扫描 Schema
     * @param context 扫描上下文
     * @param baseTableFilter 底表过滤条件
     */
    protected PositionDeletesBatchScan(
        Table table, Schema schema, TableScanContext context, Expression baseTableFilter) {
      super(table, schema, context);
      this.baseTableFilter = baseTableFilter;
    }

    /**
     * 基于新的表、Schema 与上下文创建精炼后的扫描器，保留当前底表过滤条件。
     *
     * @param newTable 新的底层表
     * @param newSchema 新的 Schema
     * @param newContext 新的扫描上下文
     * @return 新的扫描器实例
     */
    @Override
    protected PositionDeletesBatchScan newRefinedScan(
        Table newTable, Schema newSchema, TableScanContext newContext) {
      return new PositionDeletesBatchScan(newTable, newSchema, newContext, baseTableFilter);
    }

    /**
     * 规划扫描任务组，将文件按目标分片大小、回溯与文件开销切分为多个任务组。
     *
     * @return 扫描任务组集合
     */
    @Override
    public CloseableIterable<ScanTaskGroup<ScanTask>> planTasks() {
      return TableScanUtil.planTaskGroups(
          planFiles(), targetSplitSize(), splitLookback(), splitOpenFileCost());
    }

    /**
     * 返回本次扫描需要读取的列集合，是否包含统计列取决于扫描上下文配置。
     *
     * @return 列名列表
     */
    @Override
    protected List<String> scanColumns() {
      return context().returnColumnStats() ? DELETE_SCAN_WITH_STATS_COLUMNS : DELETE_SCAN_COLUMNS;
    }

    /**
     * 设置针对底表的过滤条件，仅其分区表达式部分会作用于位置删除表。
     *
     * <p>逻辑：通过 {@link
     * org.apache.iceberg.expressions.Projections.ProjectionEvaluator#project(Expression)}
     * 将过滤条件投影到底表分区规格上进行评估；无法投影的部分不会影响任务 residual。
     *
     * <ul>
     *   <li>仅可投影到底表分区规格上的分区表达式会被评估，并非所有表达式都可投影。
     *   <li>由于不能在分区表达式之外生效，该过滤不会贡献到任务的 residual（参见 {@link PositionDeletesScanTask#residual()}）。
     * </ul>
     *
     * @param expr 应用在底表上的过滤表达式
     * @return 新的扫描器实例，用于链式调用
     */
    public BatchScan baseTableFilter(Expression expr) {
      return new PositionDeletesBatchScan(
          table(), schema(), context(), Expressions.and(baseTableFilter, expr));
    }

    /**
     * 规划位置删除文件的扫描任务。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>序列化表 Schema 字符串，并对所有分区规格做 transform
     *   <li>构建多个 Caffeine 缓存：规格字符串、底表与位置删除表的 ManifestEvaluator、ResidualEvaluator
     *   <li>读取当前快照的删除 Manifest，按底表与位置删除表过滤条件双重过滤
     *   <li>对每个匹配的 Manifest 调用 {@link #posDeletesScanTasks} 生成扫描任务
     *   <li>若提供了 planExecutor 则并行读取，否则串行拼接
     * </ol>
     *
     * @return 扫描任务集合
     */
    @Override
    protected CloseableIterable<ScanTask> doPlanFiles() {
      String schemaString = SchemaParser.toJson(tableSchema());

      // prepare transformed partition specs and caches
      Map<Integer, PartitionSpec> transformedSpecs = transformSpecs(tableSchema(), table().specs());

      LoadingCache<Integer, String> specStringCache =
          partitionCacheOf(transformedSpecs, PartitionSpecParser::toJson);
      LoadingCache<Integer, ManifestEvaluator> deletesTableEvalCache =
          partitionCacheOf(
              transformedSpecs,
              spec -> ManifestEvaluator.forRowFilter(filter(), spec, isCaseSensitive()));
      LoadingCache<Integer, ManifestEvaluator> baseTableEvalCache =
          partitionCacheOf(
              table().specs(), // evaluate base table filters on base table specs
              spec -> ManifestEvaluator.forRowFilter(baseTableFilter, spec, isCaseSensitive()));
      LoadingCache<Integer, ResidualEvaluator> residualCache =
          partitionCacheOf(
              transformedSpecs,
              spec ->
                  ResidualEvaluator.of(
                      spec,
                      // there are no applicable filters in the base table's filter
                      // that we can use to evaluate on the position deletes table
                      shouldIgnoreResiduals() ? Expressions.alwaysTrue() : filter(),
                      isCaseSensitive()));

      // iterate through delete manifests
      List<ManifestFile> manifests = snapshot().deleteManifests(table().io());

      CloseableIterable<ManifestFile> matchingManifests =
          CloseableIterable.filter(
              scanMetrics().skippedDeleteManifests(),
              CloseableIterable.withNoopClose(manifests),
              manifest ->
                  baseTableEvalCache.get(manifest.partitionSpecId()).eval(manifest)
                      && deletesTableEvalCache.get(manifest.partitionSpecId()).eval(manifest));
      matchingManifests =
          CloseableIterable.count(scanMetrics().scannedDeleteManifests(), matchingManifests);

      Iterable<CloseableIterable<ScanTask>> tasks =
          CloseableIterable.transform(
              matchingManifests,
              manifest ->
                  posDeletesScanTasks(
                      manifest,
                      table().specs().get(manifest.partitionSpecId()),
                      schemaString,
                      transformedSpecs,
                      residualCache,
                      specStringCache));

      if (planExecutor() != null) {
        return new ParallelIterable<>(tasks, planExecutor());
      } else {
        return CloseableIterable.concat(tasks);
      }
    }

    /**
     * 读取单个 Manifest 的位置删除文件并构造对应的 {@link BasePositionDeletesScanTask}。
     *
     * <p>逻辑：返回一个惰性 {@link CloseableIterable}，在迭代时执行：将底表过滤投影到该 spec 上得到 分区过滤；读取删除 Manifest 的 live
     * entries，按行过滤与分区过滤；再过滤出 POSITION_DELETES 类型；最后转换为扫描任务，附带 Schema 字符串、规格字符串与 residual 评估器。
     *
     * @param manifest 待读取的删除 Manifest
     * @param spec 该 Manifest 对应的底表分区规格
     * @param schemaString 表 Schema 的 JSON 字符串
     * @param transformedSpecs transform 后的分区规格映射
     * @param residualCache residual 评估器缓存
     * @param specStringCache 规格字符串缓存
     * @return 扫描任务的可关闭迭代器
     */
    private CloseableIterable<ScanTask> posDeletesScanTasks(
        ManifestFile manifest,
        PartitionSpec spec,
        String schemaString,
        Map<Integer, PartitionSpec> transformedSpecs,
        LoadingCache<Integer, ResidualEvaluator> residualCache,
        LoadingCache<Integer, String> specStringCache) {
      return new CloseableIterable<ScanTask>() {
        private CloseableIterable<ScanTask> iterable;

        @Override
        public void close() throws IOException {
          if (iterable != null) {
            iterable.close();
          }
        }

        @Override
        public CloseableIterator<ScanTask> iterator() {
          Expression partitionFilter =
              Projections.inclusive(spec, isCaseSensitive()).project(baseTableFilter);

          // Filter partitions
          CloseableIterable<ManifestEntry<DeleteFile>> deleteFileEntries =
              ManifestFiles.readDeleteManifest(manifest, table().io(), transformedSpecs)
                  .caseSensitive(isCaseSensitive())
                  .select(scanColumns())
                  .filterRows(filter())
                  .filterPartitions(partitionFilter)
                  .scanMetrics(scanMetrics())
                  .liveEntries();

          // Filter delete file type
          CloseableIterable<ManifestEntry<DeleteFile>> positionDeleteEntries =
              CloseableIterable.filter(
                  deleteFileEntries,
                  entry -> entry.file().content().equals(FileContent.POSITION_DELETES));

          this.iterable =
              CloseableIterable.transform(
                  positionDeleteEntries,
                  entry -> {
                    int specId = entry.file().specId();
                    return new BasePositionDeletesScanTask(
                        entry.file().copy(context().returnColumnStats()),
                        schemaString,
                        specStringCache.get(specId),
                        residualCache.get(specId));
                  });
          return iterable.iterator();
        }
      };
    }

    /**
     * 按 specId 缓存构建结果的工具方法，避免重复构造评估器/规格字符串等对象。
     *
     * @param specs 分区规格映射
     * @param constructor 由 PartitionSpec 构造缓存值的函数
     * @param <T> 缓存值类型
     * @return 按 specId 加载的 Caffeine 缓存
     */
    private <T> LoadingCache<Integer, T> partitionCacheOf(
        Map<Integer, PartitionSpec> specs, Function<PartitionSpec, T> constructor) {
      return Caffeine.newBuilder()
          .build(
              specId -> {
                PartitionSpec spec = specs.get(specId);
                return constructor.apply(spec);
              });
    }
  }
}
