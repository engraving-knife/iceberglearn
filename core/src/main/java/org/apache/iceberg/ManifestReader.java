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

import static org.apache.iceberg.expressions.Expressions.alwaysTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.avro.AvroIterable;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.InclusiveMetricsEvaluator;
import org.apache.iceberg.expressions.Projections;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.metrics.ScanMetrics;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.PartitionSet;

/**
 * 数据 manifest 与删除 manifest 文件的基础读取器。
 *
 * <p>所属模块：iceberg-core，继承 {@link CloseableGroup} 并实现 {@link CloseableIterable}， 是扫描阶段读取 manifest
 * 文件、产出 {@link ContentFile} 的核心组件。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以 Avro 方式读取 manifest 文件中的 {@link ManifestEntry}，并应用可继承元数据。
 *   <li>支持列裁剪（select/project）、分区过滤、行级过滤与大小写敏感设置。
 *   <li>结合 {@link Evaluator}（分区级）与 {@link InclusiveMetricsEvaluator}（文件级统计） 跳过不匹配的文件，减少 IO。
 *   <li>通过 {@code inheritableMetadata} 补全旧版本 manifest 缺失的序列号等信息。
 * </ul>
 *
 * <p>设计意图：采用流式 {@link CloseableIterable} 避免一次性加载全部条目；evaluator 与 metricsEvaluator
 * 懒加载且复用；列裁剪与统计列按需注入，兼顾投影下推与 metrics 评估需求。
 *
 * <p>上下游关系：由 {@code ManifestGroup} 等扫描编排器创建；依赖 {@link InputFile} 读取底层文件， 产出 {@link ManifestEntry}
 * 供扫描任务使用。
 *
 * @param <F> 该读取器返回的文件类型，{@link DataFile} 或 {@link DeleteFile}
 */
public class ManifestReader<F extends ContentFile<F>> extends CloseableGroup
    implements CloseableIterable<F> {
  static final ImmutableList<String> ALL_COLUMNS = ImmutableList.of("*");

  private static final Set<String> STATS_COLUMNS =
      ImmutableSet.of(
          "value_counts",
          "null_value_counts",
          "nan_value_counts",
          "lower_bounds",
          "upper_bounds",
          "record_count");

  /** manifest 文件类型枚举，区分数据文件与删除文件，决定 Avro 反序列化目标类。 */
  protected enum FileType {
    DATA_FILES(GenericDataFile.class.getName()),
    DELETE_FILES(GenericDeleteFile.class.getName());

    private final String fileClass;

    FileType(String fileClass) {
      this.fileClass = fileClass;
    }

    /** @return 该类型对应的 Avro 反序列化目标类全限定名 */
    private String fileClass() {
      return fileClass;
    }
  }

  private final InputFile file;
  private final InheritableMetadata inheritableMetadata;
  private final FileType content;
  private final PartitionSpec spec;
  private final Schema fileSchema;

  // updated by configuration methods
  private PartitionSet partitionSet = null;
  private Expression partFilter = alwaysTrue();
  private Expression rowFilter = alwaysTrue();
  private Schema fileProjection = null;
  private Collection<String> columns = null;
  private boolean caseSensitive = true;
  private ScanMetrics scanMetrics = ScanMetrics.noop();

  // lazily initialized
  private Evaluator lazyEvaluator = null;
  private InclusiveMetricsEvaluator lazyMetricsEvaluator = null;

  /**
   * 构造 manifest 读取器。
   *
   * <p>逻辑：若提供 specsById 则按 specId 查找分区 spec，否则从 manifest 文件元数据读取 spec； 随后依据 spec 的分区类型构建文件 schema。
   *
   * @param file manifest 输入文件
   * @param specId 分区 spec ID
   * @param specsById 按 ID 索引的所有分区 spec，可为 null
   * @param inheritableMetadata 可继承元数据（补全旧版本条目）
   * @param content 文件类型（数据/删除）
   */
  protected ManifestReader(
      InputFile file,
      int specId,
      Map<Integer, PartitionSpec> specsById,
      InheritableMetadata inheritableMetadata,
      FileType content) {
    this.file = file;
    this.inheritableMetadata = inheritableMetadata;
    this.content = content;

    if (specsById != null) {
      this.spec = specsById.get(specId);
    } else {
      this.spec = readPartitionSpec(file);
    }

    this.fileSchema = new Schema(DataFile.getType(spec.partitionType()).fields());
  }

  /**
   * 从 manifest 文件元数据中读取分区 spec（未外部传入 specsById 时使用）。
   *
   * @param inputFile manifest 输入文件
   * @return 解析得到的分区 spec
   */
  private <T extends ContentFile<T>> PartitionSpec readPartitionSpec(InputFile inputFile) {
    Map<String, String> metadata = readMetadata(inputFile);

    int specId = TableMetadata.INITIAL_SPEC_ID;
    String specProperty = metadata.get("partition-spec-id");
    if (specProperty != null) {
      specId = Integer.parseInt(specProperty);
    }

    Schema schema = SchemaParser.fromJson(metadata.get("schema"));
    return PartitionSpecParser.fromJsonFields(schema, specId, metadata.get("partition-spec"));
  }

  /**
   * 读取 manifest 文件的 Avro 元数据（schema、partition-spec 等）。
   *
   * <p>逻辑：以仅投影 status 列的方式打开 Avro 读取器，取出其 metadata 后立即关闭，避免读取全部数据。
   *
   * @param inputFile manifest 输入文件
   * @return Avro 文件元数据键值对
   */
  private static <T extends ContentFile<T>> Map<String, String> readMetadata(InputFile inputFile) {
    Map<String, String> metadata;
    try {
      try (AvroIterable<ManifestEntry<T>> headerReader =
          Avro.read(inputFile)
              .project(ManifestEntry.getSchema(Types.StructType.of()).select("status"))
              .classLoader(GenericManifestEntry.class.getClassLoader())
              .build()) {
        metadata = headerReader.getMetadata();
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return metadata;
  }

  /** 返回 是否为删除文件 manifest 读取器。 */
  public boolean isDeleteManifestReader() {
    return content == FileType.DELETE_FILES;
  }

  /** 返回 底层输入文件。 */
  public InputFile file() {
    return file;
  }

  /** 返回 manifest 对应的文件 schema。 */
  public Schema schema() {
    return fileSchema;
  }

  /** 返回 该 manifest 使用的分区 spec。 */
  public PartitionSpec spec() {
    return spec;
  }

  /**
   * 按列名选择读取列（列裁剪），与 {@link #project(Schema)} 互斥。
   *
   * @param newColumns 待读取的列名集合
   * @return 当前读取器（链式调用）
   */
  public ManifestReader<F> select(Collection<String> newColumns) {
    Preconditions.checkState(
        fileProjection == null,
        "Cannot select columns using both select(String...) and project(Schema)");
    this.columns = newColumns;
    return this;
  }

  /**
   * 按 schema 投影读取列（列裁剪），与 {@link #select(Collection)} 互斥。
   *
   * @param newFileProjection 投影 schema
   * @return 当前读取器（链式调用）
   */
  public ManifestReader<F> project(Schema newFileProjection) {
    Preconditions.checkState(
        columns == null, "Cannot select columns using both select(String...) and project(Schema)");
    this.fileProjection = newFileProjection;
    return this;
  }

  /**
   * 追加分区过滤表达式（与已有 partFilter 做 AND）。
   *
   * @param expr 分区过滤表达式
   * @return 当前读取器（链式调用）
   */
  public ManifestReader<F> filterPartitions(Expression expr) {
    this.partFilter = Expressions.and(partFilter, expr);
    return this;
  }

  /**
   * 按 {@link PartitionSet} 过滤分区，仅保留集合内的分区。
   *
   * @param partitions 允许的分区集合
   * @return 当前读取器（链式调用）
   */
  public ManifestReader<F> filterPartitions(PartitionSet partitions) {
    this.partitionSet = partitions;
    return this;
  }

  /**
   * 追加行级过滤表达式（与已有 rowFilter 做 AND），用于文件级 metrics 谓词下推。
   *
   * @param expr 行级过滤表达式
   * @return 当前读取器（链式调用）
   */
  public ManifestReader<F> filterRows(Expression expr) {
    this.rowFilter = Expressions.and(rowFilter, expr);
    return this;
  }

  /**
   * 设置是否大小写敏感，影响列名匹配与表达式求值。
   *
   * @param isCaseSensitive 是否大小写敏感
   * @return 当前读取器（链式调用）
   */
  public ManifestReader<F> caseSensitive(boolean isCaseSensitive) {
    this.caseSensitive = isCaseSensitive;
    return this;
  }

  /**
   * 设置扫描指标收集器，用于统计跳过的文件数。
   *
   * @param newScanMetrics 扫描指标
   * @return 当前读取器（链式调用）
   */
  ManifestReader<F> scanMetrics(ScanMetrics newScanMetrics) {
    this.scanMetrics = newScanMetrics;
    return this;
  }

  /** @return 全部条目的可关闭迭代（含已删除条目） */
  CloseableIterable<ManifestEntry<F>> entries() {
    return entries(false /* all entries */);
  }

  /**
   * 读取 manifest 条目，可选仅返回存活条目，并应用分区/行级/metrics 过滤。
   *
   * <p>逻辑：若存在行级或分区过滤，构造 evaluator 与 metricsEvaluator，并在需要时注入统计列； 对每个条目依次用分区 evaluator、metrics
   * evaluator、partitionSet 判定是否保留，并累计跳过文件数。 无过滤时直接打开投影读取。
   *
   * @param onlyLive 是否仅返回存活（非 DELETED）条目
   * @return 过滤后的条目迭代
   */
  private CloseableIterable<ManifestEntry<F>> entries(boolean onlyLive) {
    if (hasRowFilter() || hasPartitionFilter() || partitionSet != null) {
      Evaluator evaluator = evaluator();
      InclusiveMetricsEvaluator metricsEvaluator = metricsEvaluator();

      // ensure stats columns are present for metrics evaluation
      boolean requireStatsProjection = requireStatsProjection(rowFilter, columns);
      Collection<String> projectColumns =
          requireStatsProjection ? withStatsColumns(columns) : columns;
      CloseableIterable<ManifestEntry<F>> entries =
          open(projection(fileSchema, fileProjection, projectColumns, caseSensitive));

      return CloseableIterable.filter(
          content == FileType.DATA_FILES
              ? scanMetrics.skippedDataFiles()
              : scanMetrics.skippedDeleteFiles(),
          onlyLive ? filterLiveEntries(entries) : entries,
          entry ->
              entry != null
                  && evaluator.eval(entry.file().partition())
                  && metricsEvaluator.eval(entry.file())
                  && inPartitionSet(entry.file()));
    } else {
      CloseableIterable<ManifestEntry<F>> entries =
          open(projection(fileSchema, fileProjection, columns, caseSensitive));
      return onlyLive ? filterLiveEntries(entries) : entries;
    }
  }

  /** @return 是否存在行级过滤（rowFilter 非 alwaysTrue） */
  private boolean hasRowFilter() {
    return rowFilter != null && rowFilter != Expressions.alwaysTrue();
  }

  /** @return 是否存在分区过滤（partFilter 非 alwaysTrue） */
  private boolean hasPartitionFilter() {
    return partFilter != null && partFilter != Expressions.alwaysTrue();
  }

  /**
   * 判断文件是否属于允许的分区集合。
   *
   * @param fileToCheck 待检查文件
   * @return 未设置 partitionSet 返回 true，否则返回是否在集合内
   */
  private boolean inPartitionSet(F fileToCheck) {
    return partitionSet == null
        || partitionSet.contains(fileToCheck.specId(), fileToCheck.partition());
  }

  /**
   * 打开 manifest 文件并以给定投影读取条目。
   *
   * <p>逻辑：依据文件扩展名推断格式，目前仅支持 Avro；构造 AvroIterable 并做类名重映射， 追加 ROW_POSITION 列，复用容器并应用
   * inheritableMetadata 后返回。
   *
   * @param projection 读取投影 schema
   * @return 条目迭代
   */
  private CloseableIterable<ManifestEntry<F>> open(Schema projection) {
    FileFormat format = FileFormat.fromFileName(file.location());
    Preconditions.checkArgument(format != null, "Unable to determine format of manifest: %s", file);

    List<Types.NestedField> fields = Lists.newArrayList();
    fields.addAll(projection.asStruct().fields());
    fields.add(MetadataColumns.ROW_POSITION);

    switch (format) {
      case AVRO:
        AvroIterable<ManifestEntry<F>> reader =
            Avro.read(file)
                .project(ManifestEntry.wrapFileSchema(Types.StructType.of(fields)))
                .rename("manifest_entry", GenericManifestEntry.class.getName())
                .rename("partition", PartitionData.class.getName())
                .rename("r102", PartitionData.class.getName())
                .rename("data_file", content.fileClass())
                .rename("r2", content.fileClass())
                .classLoader(GenericManifestEntry.class.getClassLoader())
                .reuseContainers()
                .build();

        addCloseable(reader);

        return CloseableIterable.transform(reader, inheritableMetadata::apply);

      default:
        throw new UnsupportedOperationException("Invalid format for manifest file: " + format);
    }
  }

  /** @return 仅存活条目（排除 DELETED）的迭代 */
  CloseableIterable<ManifestEntry<F>> liveEntries() {
    return entries(true /* only live entries */);
  }

  /**
   * 过滤出存活条目（status 非 DELETED）。
   *
   * @param entries 原始条目迭代
   * @return 仅含存活条目的迭代
   */
  private CloseableIterable<ManifestEntry<F>> filterLiveEntries(
      CloseableIterable<ManifestEntry<F>> entries) {
    return CloseableIterable.filter(entries, this::isLiveEntry);
  }

  /** @return 条目是否存活（非 null 且 status 非 DELETED） */
  private boolean isLiveEntry(ManifestEntry<F> entry) {
    return entry != null && entry.status() != ManifestEntry.Status.DELETED;
  }

  /**
   * 返回数据文件迭代，按需丢弃统计信息并对文件做防御性拷贝。
   *
   * @return 数据文件迭代
   */
  @Override
  public CloseableIterator<F> iterator() {
    boolean dropStats = dropStats(columns);
    return CloseableIterable.transform(liveEntries(), e -> e.file().copy(!dropStats)).iterator();
  }

  /**
   * 计算最终投影 schema：优先列名选择，其次显式投影，最后返回原 schema。
   *
   * @param schema 原始 schema
   * @param project 显式投影 schema，可为 null
   * @param columns 列名集合，可为 null
   * @param caseSensitive 是否大小写敏感
   * @return 最终投影 schema
   */
  private static Schema projection(
      Schema schema, Schema project, Collection<String> columns, boolean caseSensitive) {
    if (columns != null) {
      if (caseSensitive) {
        return schema.select(columns);
      } else {
        return schema.caseInsensitiveSelect(columns);
      }
    } else if (project != null) {
      return project;
    }

    return schema;
  }

  /**
   * 懒加载分区级 {@link Evaluator}：将行过滤投影到分区空间后与 partFilter 合并。
   *
   * @return 分区级表达式求值器
   */
  private Evaluator evaluator() {
    if (lazyEvaluator == null) {
      Expression projected = Projections.inclusive(spec, caseSensitive).project(rowFilter);
      Expression finalPartFilter = Expressions.and(projected, partFilter);
      if (finalPartFilter != null) {
        this.lazyEvaluator = new Evaluator(spec.partitionType(), finalPartFilter, caseSensitive);
      } else {
        this.lazyEvaluator =
            new Evaluator(spec.partitionType(), Expressions.alwaysTrue(), caseSensitive);
      }
    }
    return lazyEvaluator;
  }

  /**
   * 懒加载文件级 {@link InclusiveMetricsEvaluator}，基于行过滤与 schema 评估文件 metrics。
   *
   * @return 文件级 metrics 求值器
   */
  private InclusiveMetricsEvaluator metricsEvaluator() {
    if (lazyMetricsEvaluator == null) {
      if (rowFilter != null) {
        this.lazyMetricsEvaluator =
            new InclusiveMetricsEvaluator(spec.schema(), rowFilter, caseSensitive);
      } else {
        this.lazyMetricsEvaluator =
            new InclusiveMetricsEvaluator(spec.schema(), Expressions.alwaysTrue(), caseSensitive);
      }
    }
    return lazyMetricsEvaluator;
  }

  /**
   * 判断是否需要额外注入统计列以支持 metrics 评估。
   *
   * <p>逻辑：当存在行过滤且未选全列、也未选全统计列时，需要补齐统计列。
   *
   * @param rowFilter 行过滤表达式
   * @param columns 已选列名集合
   * @return 是否需要注入统计列
   */
  private static boolean requireStatsProjection(Expression rowFilter, Collection<String> columns) {
    // Make sure we have all stats columns for metrics evaluator
    return rowFilter != Expressions.alwaysTrue()
        && columns != null
        && !columns.containsAll(ManifestReader.ALL_COLUMNS)
        && !columns.containsAll(STATS_COLUMNS);
  }

  /**
   * 判断是否应在返回文件时丢弃统计信息。
   *
   * <p>逻辑：未选全列时，若所选列与统计列无交集，或仅选了 record_count（基本类型）， 则丢弃统计以节省内存；部分选了其他统计列则保留。
   *
   * @param columns 已选列名集合
   * @return 是否丢弃统计信息
   */
  static boolean dropStats(Collection<String> columns) {
    // Make sure we only drop all stats if we had projected all stats
    // We do not drop stats even if we had partially added some stats columns, except for
    // record_count column.
    // Since we don't want to keep stats map which could be huge in size just because we select
    // record_count, which
    // is a primitive type.
    if (columns != null && !columns.containsAll(ManifestReader.ALL_COLUMNS)) {
      Set<String> intersection = Sets.intersection(Sets.newHashSet(columns), STATS_COLUMNS);
      return intersection.isEmpty() || intersection.equals(Sets.newHashSet("record_count"));
    }
    return false;
  }

  /**
   * 在所选列基础上补齐统计列，供 metrics 评估使用。
   *
   * @param columns 已选列名集合
   * @return 含统计列的列名列表
   */
  static List<String> withStatsColumns(Collection<String> columns) {
    if (columns.containsAll(ManifestReader.ALL_COLUMNS)) {
      return Lists.newArrayList(columns);
    } else {
      List<String> projectColumns = Lists.newArrayList(columns);
      projectColumns.addAll(STATS_COLUMNS); // order doesn't matter
      return projectColumns;
    }
  }
}
