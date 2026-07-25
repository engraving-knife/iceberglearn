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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.ManifestEvaluator;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;

/**
 * 文件类元数据表的抽象基类，提供 files/all_files 等 metadata 表的通用 schema 与扫描逻辑。
 *
 * <p>所属模块：iceberg-core。元数据表体系的核心基类，子类（如 {@code DataFilesTable}、 {@code DeleteFilesTable} 等）通过指定
 * manifest 来源来复用本类的扫描规划。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>计算文件元数据表的 schema：基于分区类型生成 {@link DataFile} 类型并附加 {@code readable_metrics} 虚拟列。
 *   <li>提供 {@link #planFiles} 静态逻辑：用 manifest 评估器过滤 manifest，并构造 {@link ManifestReadTask}。
 *   <li>定义两个抽象扫描基类（当前快照视图 / 全部快照视图）供子类扩展。
 * </ul>
 *
 * <p>设计意图：通过抽象方法 {@code manifests()} 把"从哪些 manifest 取文件"这一差异化策略 延迟到子类，基类只负责通用的过滤、投影和任务构造；使用
 * Caffeine 缓存按 specId 缓存 {@link ManifestEvaluator}，避免对同一分区规则重复构建评估器。
 *
 * <p>上下游关系：继承 {@link BaseMetadataTable}；被 {@code DataFilesTable}、 {@code AllDataFilesTable}、{@code
 * DeleteFilesTable} 等子类复用。
 */
abstract class BaseFilesTable extends BaseMetadataTable {

  BaseFilesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 计算文件元数据表的 schema：基于分区类型构造 {@link DataFile} 字段，并附加 readable_metrics 列。
   *
   * <p>逻辑：若表无分区字段，则从 schema 中移除 partition 字段以避免返回空 struct （某些引擎不支持空 struct）；最后通过 {@link
   * MetricsUtil#readableMetricsSchema} 附加 可读指标列。
   */
  @Override
  public Schema schema() {
    StructType partitionType = Partitioning.partitionType(table());
    Schema schema = new Schema(DataFile.getType(partitionType).fields());
    if (partitionType.fields().size() < 1) {
      // avoid returning an empty struct, which is not always supported.
      // instead, drop the partition field
      schema = TypeUtil.selectNot(schema, Sets.newHashSet(DataFile.PARTITION_ID));
    }

    return TypeUtil.join(schema, MetricsUtil.readableMetricsSchema(table().schema(), schema));
  }

  /**
   * 规划文件扫描任务：基于 manifest 评估器过滤 manifest，并为每个 manifest 生成一个读取任务。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从 context 读取行过滤器、大小写敏感、是否忽略残留谓词等参数。
   *   <li>使用 Caffeine 按 specId 缓存 {@link ManifestEvaluator}，构建时基于表 schema 做字段映射后构造评估器。
   *   <li>用评估器过滤 manifest 集合，保留可能命中的 manifest。
   *   <li>序列化投影 schema 与 unpartitioned 分区规则字符串，构造 {@link ManifestReadTask}。
   * </ol>
   *
   * @param table 目标表
   * @param manifests 待筛选的 manifest 集合
   * @param tableSchema 表 schema
   * @param projectedSchema 用户请求的投影 schema
   * @param context 扫描上下文
   * @return 文件扫描任务集合
   */
  private static CloseableIterable<FileScanTask> planFiles(
      Table table,
      CloseableIterable<ManifestFile> manifests,
      Schema tableSchema,
      Schema projectedSchema,
      TableScanContext context) {
    Expression rowFilter = context.rowFilter();
    boolean caseSensitive = context.caseSensitive();
    boolean ignoreResiduals = context.ignoreResiduals();

    LoadingCache<Integer, ManifestEvaluator> evalCache =
        Caffeine.newBuilder()
            .build(
                specId -> {
                  PartitionSpec spec = table.specs().get(specId);
                  PartitionSpec transformedSpec = BaseFilesTable.transformSpec(tableSchema, spec);
                  return ManifestEvaluator.forRowFilter(rowFilter, transformedSpec, caseSensitive);
                });

    CloseableIterable<ManifestFile> filteredManifests =
        CloseableIterable.filter(
            manifests, manifest -> evalCache.get(manifest.partitionSpecId()).eval(manifest));

    String schemaString = SchemaParser.toJson(projectedSchema);
    String specString = PartitionSpecParser.toJson(PartitionSpec.unpartitioned());
    Expression filter = ignoreResiduals ? Expressions.alwaysTrue() : rowFilter;
    ResidualEvaluator residuals = ResidualEvaluator.unpartitioned(filter);

    return CloseableIterable.transform(
        filteredManifests,
        manifest ->
            new ManifestReadTask(
                table, manifest, projectedSchema, schemaString, specString, residuals));
  }

  /**
   * 当前快照视图下的文件扫描基类：子类提供 manifest 来源，基类负责统一规划。
   *
   * <p>设计意图：分离"取哪些 manifest"与"如何把 manifest 转换为扫描任务"两个职责， 子类只需实现 {@link #manifests()}。
   */
  abstract static class BaseFilesTableScan extends BaseMetadataTableScan {

    protected BaseFilesTableScan(Table table, Schema schema, MetadataTableType tableType) {
      super(table, schema, tableType);
    }

    protected BaseFilesTableScan(
        Table table, Schema schema, MetadataTableType tableType, TableScanContext context) {
      super(table, schema, tableType, context);
    }

    /** 返回本次扫描要遍历的 manifest 集合，由子类按其语义（当前快照/历史快照等）提供。 */
    protected abstract CloseableIterable<ManifestFile> manifests();

    @Override
    protected CloseableIterable<FileScanTask> doPlanFiles() {
      return BaseFilesTable.planFiles(table(), manifests(), tableSchema(), schema(), context());
    }
  }

  /**
   * 全量快照视图下的文件扫描基类：与 {@link BaseFilesTableScan} 类似，但继承自 {@link BaseAllMetadataTableScan}，意味着
   * manifest 来源跨越所有快照。
   */
  abstract static class BaseAllFilesTableScan extends BaseAllMetadataTableScan {

    protected BaseAllFilesTableScan(Table table, Schema schema, MetadataTableType tableType) {
      super(table, schema, tableType);
    }

    protected BaseAllFilesTableScan(
        Table table, Schema schema, MetadataTableType tableType, TableScanContext context) {
      super(table, schema, tableType, context);
    }

    /** 返回本次扫描要遍历的 manifest 集合，由子类按其语义提供。 */
    protected abstract CloseableIterable<ManifestFile> manifests();

    @Override
    protected CloseableIterable<FileScanTask> doPlanFiles() {
      return BaseFilesTable.planFiles(table(), manifests(), tableSchema(), schema(), context());
    }
  }

  /**
   * 读取单个 manifest 并将其中的文件元数据以行形式暴露的 {@link DataTask}。
   *
   * <p>设计要点：同时支持数据 manifest 与删除 manifest；当用户请求 {@code readable_metrics}
   * 列时，会做一次特殊投影——先去掉该虚拟列，再补齐构造它所需的底层 metrics 列， 最后在返回前把 metrics 计算为可读形式附加到行上。
   */
  static class ManifestReadTask extends BaseFileScanTask implements DataTask {

    private final FileIO io;
    private final Map<Integer, PartitionSpec> specsById;
    private final ManifestFile manifest;
    private final Schema dataTableSchema;
    private final Schema projection;

    ManifestReadTask(
        Table table,
        ManifestFile manifest,
        Schema projection,
        String schemaString,
        String specString,
        ResidualEvaluator residuals) {
      super(DataFiles.fromManifest(manifest), null, schemaString, specString, residuals);
      this.io = table.io();
      this.specsById = Maps.newHashMap(table.specs());
      this.manifest = manifest;
      this.dataTableSchema = table.schema();
      this.projection = projection;
    }

    /**
     * 返回 manifest 中的文件作为行。
     *
     * <p>逻辑：若投影不包含 {@code readable_metrics} 列，直接把读取到的 {@link ContentFile} 强转为 {@link StructLike}
     * 返回；否则需要计算投影（去掉虚拟列并补齐底层 metrics 列）， 然后对每个文件附加可读 metrics 字段。
     */
    @Override
    public CloseableIterable<StructLike> rows() {
      Types.NestedField readableMetricsField = projection.findField(MetricsUtil.READABLE_METRICS);

      if (readableMetricsField == null) {
        return CloseableIterable.transform(files(projection), file -> (StructLike) file);
      } else {

        Schema actualProjection = projectionForReadableMetrics(projection, readableMetricsField);
        return CloseableIterable.transform(
            files(actualProjection), f -> withReadableMetrics(f, readableMetricsField));
      }
    }

    /**
     * 根据 manifest 的内容类型选择读取方式，返回 manifest 内的文件集合。
     *
     * @param fileProjection 文件投影 schema
     * @return 数据文件或删除文件的可迭代集合
     */
    private CloseableIterable<? extends ContentFile<?>> files(Schema fileProjection) {
      switch (manifest.content()) {
        case DATA:
          return ManifestFiles.read(manifest, io, specsById).project(fileProjection);
        case DELETES:
          return ManifestFiles.readDeleteManifest(manifest, io, specsById).project(fileProjection);
        default:
          throw new IllegalArgumentException(
              "Unsupported manifest content type:" + manifest.content());
      }
    }

    /**
     * 为文件元数据追加 {@code readable_metrics} 列，返回带可读指标的行。
     *
     * @param file 内容文件元数据
     * @param readableMetricsField 投影中的 readable_metrics 字段
     * @return 附加了 readable_metrics 字段的行结构
     */
    private StructLike withReadableMetrics(
        ContentFile<?> file, Types.NestedField readableMetricsField) {
      int structSize = projection.columns().size();
      MetricsUtil.ReadableMetricsStruct readableMetrics =
          readableMetrics(file, readableMetricsField);
      int metricsPosition = projection.columns().indexOf(readableMetricsField);

      return new MetricsUtil.StructWithReadableMetrics(
          (StructLike) file, structSize, readableMetrics, metricsPosition);
    }

    /**
     * 计算单个文件的可读指标结构。
     *
     * @param file 内容文件
     * @param readableMetricsField 投影中的 readable_metrics 字段
     * @return 可读指标结构
     */
    private MetricsUtil.ReadableMetricsStruct readableMetrics(
        ContentFile<?> file, Types.NestedField readableMetricsField) {
      StructType projectedMetricType = readableMetricsField.type().asStructType();
      return MetricsUtil.readableMetricsStruct(dataTableSchema, file, projectedMetricType);
    }

    /**
     * 计算用于读取 manifest 的实际投影：从用户投影中移除虚拟 {@code readable_metrics} 列， 同时保证构造可读指标所需的底层 metrics
     * 列被包含进最终投影。
     *
     * <p>设计意图：readable_metrics 是计算列，无法直接从 manifest 读取，因此读取时 必须先取出其依赖的底层列，再在内存中计算。
     *
     * @param requestedProjection 用户请求的投影
     * @param readableMetricsField readable_metrics 字段
     * @return 实际用于读取 manifest 的投影 schema
     */
    private Schema projectionForReadableMetrics(
        Schema requestedProjection, Types.NestedField readableMetricsField) {
      Set<Integer> readableMetricsIds = TypeUtil.getProjectedIds(readableMetricsField.type());
      Schema realProjection = TypeUtil.selectNot(requestedProjection, readableMetricsIds);

      Schema requiredMetricsColumns =
          new Schema(
              MetricsUtil.READABLE_METRIC_COLS.stream()
                  .map(MetricsUtil.ReadableMetricColDefinition::originalCol)
                  .collect(Collectors.toList()));
      return TypeUtil.join(realProjection, requiredMetricsColumns);
    }

    @Override
    public Iterable<FileScanTask> split(long splitSize) {
      return ImmutableList.of(this); // don't split
    }

    @VisibleForTesting
    ManifestFile manifest() {
      return manifest;
    }
  }
}
