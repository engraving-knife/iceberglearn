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
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.StructProjection;

/**
 * 元数据表"entries"系列的抽象基类，把 manifest 中的文件条目以行的方式暴露出来供扫描。
 *
 * <p>所属模块：iceberg-core（元数据表实现层，提供 manifest entries 的扫描能力）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 entries 元数据表（data files entries / delete files entries / all entries 等）共享的 schema
 *       构造与文件计划逻辑。
 *   <li>把 manifest 中每个 {@link ManifestEntry} 转换为可扫描行，并按需附加 {@code readable_metrics} 列，把原始 metrics
 *       转为人类可读形式。
 * </ul>
 *
 * <p>设计意图：把"读 manifest → 过滤 → 投影 → 输出行"这一通用流程抽象到基类，避免每个 entries 子类重复实现；通过 {@link ManifestReadTask}
 * 单 manifest 读取实现，把扫描任务直接对应到 一个 manifest，简化并行调度。
 *
 * <p>上下游关系：被 {@code DataFilesTable}、{@code DeleteFilesTable}、{@code AllEntriesTable} 等具体元数据表继承；底层依赖
 * {@link ManifestFiles} 读取 manifest，并使用 {@link MetricsUtil} 构造可读 metrics。
 */
abstract class BaseEntriesTable extends BaseMetadataTable {

  /**
   * 构造 entries 元数据表。
   *
   * @param table 被包装的底层 Iceberg 表
   * @param name 元数据表名
   */
  BaseEntriesTable(Table table, String name) {
    super(table, name);
  }

  /**
   * 返回该 entries 元数据表的 schema：基于 {@link ManifestEntry} schema，并附加 readable_metrics。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>用表的分区类型构造 {@link ManifestEntry} 的 schema；
   *   <li>若表无分区字段（partitionType 字段数为 0），则移除 partition 字段以避免空 struct （部分引擎不支持空 struct）；
   *   <li>把上述 schema 与 {@link MetricsUtil#readableMetricsSchema} 拼接，得到最终 schema。
   * </ul>
   *
   * @return 包含 manifest entry 字段与 readable_metrics 字段的 schema
   */
  @Override
  public Schema schema() {
    StructType partitionType = Partitioning.partitionType(table());
    Schema schema = ManifestEntry.getSchema(partitionType);
    if (partitionType.fields().size() < 1) {
      // avoid returning an empty struct, which is not always supported.
      // instead, drop the partition field (id 102)
      schema = TypeUtil.selectNot(schema, Sets.newHashSet(DataFile.PARTITION_ID));
    }

    return TypeUtil.join(schema, MetricsUtil.readableMetricsSchema(table().schema(), schema));
  }

  /**
   * 静态文件计划方法：基于给定 manifests、过滤与投影产出 {@link FileScanTask} 集合。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>构建按 specId 缓存的 {@link ManifestEvaluator}，用于基于行过滤对 manifest 做粗粒度过滤 （利用 manifest 的
   *       partition summary 跳过不可能命中的 manifest）；
   *   <li>用该 evaluator 过滤 manifests，仅保留可能命中的；
   *   <li>对每个剩余 manifest 构造一个 {@link ManifestReadTask}，并把残留表达式封装其中。
   * </ol>
   *
   * <p>设计意图：把过滤与任务构造分离，且 evaluator 按 specId 缓存避免重复构造，提升扫描性能。
   *
   * @param table 表
   * @param manifests 待扫描的 manifest 列表
   * @param tableSchema 表 schema
   * @param projectedSchema 投影 schema
   * @param context 扫描上下文（含行过滤、大小写敏感性等）
   * @return 文件扫描任务集合
   */
  static CloseableIterable<FileScanTask> planFiles(
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
   * 单 manifest 读取任务：把一个 manifest 中的所有条目作为可扫描行返回，实现 {@link DataTask}。
   *
   * <p>设计意图：一个 manifest 对应一个任务，便于按 manifest 粒度并行执行；通过 {@link StructProjection} 做列投影，并按需把 metrics
   * 转换为 readable_metrics 列。
   *
   * <p>该任务不支持 split（一个 manifest 视为不可再分的整体）。
   */
  static class ManifestReadTask extends BaseFileScanTask implements DataTask {
    private final Schema projection;
    private final Schema fileProjection;
    private final Schema dataTableSchema;
    private final FileIO io;
    private final ManifestFile manifest;
    private final Map<Integer, PartitionSpec> specsById;

    /**
     * 构造一个 manifest 读取任务。
     *
     * @param table 表
     * @param manifest 待读取的 manifest
     * @param projection 投影 schema
     * @param schemaString 序列化后的 schema JSON
     * @param specString 序列化后的分区规格 JSON
     * @param residuals 残留表达式求值器
     */
    ManifestReadTask(
        Table table,
        ManifestFile manifest,
        Schema projection,
        String schemaString,
        String specString,
        ResidualEvaluator residuals) {
      super(DataFiles.fromManifest(manifest), null, schemaString, specString, residuals);
      this.projection = projection;
      this.io = table.io();
      this.manifest = manifest;
      this.specsById = Maps.newHashMap(table.specs());
      this.dataTableSchema = table.schema();

      Type fileProjectionType = projection.findType("data_file");
      this.fileProjection =
          fileProjectionType != null
              ? new Schema(fileProjectionType.asStructType().fields())
              : new Schema();
    }

    /** 返回该任务对应的 manifest，仅用于测试。 */
    @VisibleForTesting
    ManifestFile manifest() {
      return manifest;
    }

    /**
     * 返回 manifest 中所有条目转换后的行。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>若投影中不含 readable_metrics 字段，直接对 manifest entry 做列投影并返回；
     *   <li>若含 readable_metrics 字段，先计算所需的 metrics 投影（确保底层 metrics 列被读取）， 再移除投影中的 readable_metrics
     *       字段用于做 entry 投影，最后把可读 metrics 附加到结果行。
     * </ul>
     *
     * @return 可扫描行集合
     */
    @Override
    public CloseableIterable<StructLike> rows() {
      Types.NestedField readableMetricsField = projection.findField(MetricsUtil.READABLE_METRICS);

      if (readableMetricsField == null) {
        StructProjection structProjection = structProjection(projection);

        return CloseableIterable.transform(
            entries(fileProjection), entry -> structProjection.wrap((StructLike) entry));
      } else {
        Schema requiredFileProjection = requiredFileProjection();
        Schema actualProjection = removeReadableMetrics(projection, readableMetricsField);
        StructProjection structProjection = structProjection(actualProjection);

        return CloseableIterable.transform(
            entries(requiredFileProjection),
            entry -> withReadableMetrics(structProjection, entry, readableMetricsField));
      }
    }

    /**
     * 计算用于填充 readable_metrics 列所需的底层 metrics 列的 file projection。
     *
     * <p>逻辑：把 {@link MetricsUtil#READABLE_METRIC_COLS} 对应的原始列与当前 fileProjection 合并， 确保读取 manifest
     * 时这些 metrics 列也会被读出，供后续转换为可读 metrics。
     */
    private Schema requiredFileProjection() {
      Schema projectionForReadableMetrics =
          new Schema(
              MetricsUtil.READABLE_METRIC_COLS.stream()
                  .map(MetricsUtil.ReadableMetricColDefinition::originalCol)
                  .collect(Collectors.toList()));
      return TypeUtil.join(fileProjection, projectionForReadableMetrics);
    }

    /**
     * 从投影 schema 中移除 readable_metrics 相关字段，避免在 entry 投影中重复处理。
     *
     * @param projectionSchema 原始投影 schema
     * @param readableMetricsField readable_metrics 字段
     * @return 移除 readable_metrics 后的 schema
     */
    private Schema removeReadableMetrics(
        Schema projectionSchema, Types.NestedField readableMetricsField) {
      Set<Integer> readableMetricsIds = TypeUtil.getProjectedIds(readableMetricsField.type());
      return TypeUtil.selectNot(projectionSchema, readableMetricsIds);
    }

    /**
     * 构造从 manifest entry schema 到目标投影 schema 的列投影器。
     *
     * @param projectedSchema 目标投影 schema
     * @return {@link StructProjection}
     */
    private StructProjection structProjection(Schema projectedSchema) {
      Schema manifestEntrySchema = ManifestEntry.wrapFileSchema(fileProjection.asStruct());
      return StructProjection.create(manifestEntrySchema, projectedSchema);
    }

    /**
     * 读取该任务 manifest 中的所有条目，并应用给定的 file struct 投影。
     *
     * @param fileStructProjection 应用到 'data_file' struct 的投影
     * @return manifest 条目集合
     */
    private CloseableIterable<? extends ManifestEntry<? extends ContentFile<?>>> entries(
        Schema fileStructProjection) {
      return ManifestFiles.open(manifest, io, specsById).project(fileStructProjection).entries();
    }

    /**
     * 把一个 manifest entry 投影后附加 readable_metrics 列。
     *
     * <p>逻辑：先用 structProjection 包装 entry 得到投影行，再用 {@link MetricsUtil#readableMetricsStruct} 把 entry
     * 对应文件的 metrics 转为可读结构， 最后用 {@link MetricsUtil.StructWithReadableMetrics} 把二者合成一行。
     *
     * @param structProjection 应用于 manifest entry 的投影
     * @param entry manifest 条目
     * @param readableMetricsField 投影中的 readable_metrics 字段
     * @return 投影后并附加 readable_metrics 的行
     */
    private StructLike withReadableMetrics(
        StructProjection structProjection,
        ManifestEntry<? extends ContentFile<?>> entry,
        Types.NestedField readableMetricsField) {
      StructProjection struct = structProjection.wrap((StructLike) entry);
      int structSize = projection.columns().size();

      MetricsUtil.ReadableMetricsStruct readableMetrics =
          readableMetrics(entry.file(), readableMetricsField);
      int metricsPosition = projection.columns().indexOf(readableMetricsField);

      return new MetricsUtil.StructWithReadableMetrics(
          struct, structSize, readableMetrics, metricsPosition);
    }

    /**
     * 根据数据表 schema 与目标投影类型，构造文件的可读 metrics 结构。
     *
     * @param file 内容文件
     * @param readableMetricsField readable_metrics 字段
     * @return 可读 metrics 结构
     */
    private MetricsUtil.ReadableMetricsStruct readableMetrics(
        ContentFile<?> file, Types.NestedField readableMetricsField) {
      StructType projectedMetricType = readableMetricsField.type().asStructType();
      return MetricsUtil.readableMetricsStruct(dataTableSchema, file, projectedMetricType);
    }

    /**
     * 不支持切分：一个 manifest 读取任务视为整体，直接返回自身。
     *
     * @param splitSize 切分大小（被忽略）
     * @return 仅包含自身的列表
     */
    @Override
    public Iterable<FileScanTask> split(long splitSize) {
      return ImmutableList.of(this); // don't split
    }
  }
}
