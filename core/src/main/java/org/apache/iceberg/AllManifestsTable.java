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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.StructProjection;

/**
 * 元数据表：将表当前所有快照引用到的 manifest 文件以行形式暴露出来。
 *
 * <p>所属模块：iceberg-core。Iceberg 元数据表体系的一部分，用于让计算引擎像扫描普通表一样 查询表的 manifest 列表，便于排查与统计 manifest 情况。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 manifest 文件作为行的 schema（含 content、path、长度、分区摘要、引用快照 ID 等）。
 *   <li>遍历当前表所有快照，把每个快照 manifest list 中的 manifest 转换为行。
 *   <li>支持按 {@code reference_snapshot_id} 进行表达式下推过滤，跳过不匹配的快照。
 * </ul>
 *
 * <p>设计意图：manifest 是 Iceberg 元数据的核心载体，通过元数据表形式暴露可以复用 SQL 引擎的 扫描/过滤能力；同一 manifest
 * 可能被多个快照引用，因此结果允许重复行。{@link SnapshotEvaluator} 实现了"谓词下推到快照级"的过滤优化，避免读取无关快照的 manifest list。
 *
 * <p>上下游关系：继承 {@link BaseMetadataTable}，被各引擎通过 {@code table.all_manifests} 元数据表 名访问；底层依赖 {@link
 * FileIO} 读取 manifest list 文件。
 */
public class AllManifestsTable extends BaseMetadataTable {
  public static final Types.NestedField REF_SNAPSHOT_ID =
      Types.NestedField.required(18, "reference_snapshot_id", Types.LongType.get());

  private static final Schema MANIFEST_FILE_SCHEMA =
      new Schema(
          Types.NestedField.required(14, "content", Types.IntegerType.get()),
          Types.NestedField.required(1, "path", Types.StringType.get()),
          Types.NestedField.required(2, "length", Types.LongType.get()),
          Types.NestedField.optional(3, "partition_spec_id", Types.IntegerType.get()),
          Types.NestedField.optional(4, "added_snapshot_id", Types.LongType.get()),
          Types.NestedField.optional(5, "added_data_files_count", Types.IntegerType.get()),
          Types.NestedField.optional(6, "existing_data_files_count", Types.IntegerType.get()),
          Types.NestedField.optional(7, "deleted_data_files_count", Types.IntegerType.get()),
          Types.NestedField.required(15, "added_delete_files_count", Types.IntegerType.get()),
          Types.NestedField.required(16, "existing_delete_files_count", Types.IntegerType.get()),
          Types.NestedField.required(17, "deleted_delete_files_count", Types.IntegerType.get()),
          Types.NestedField.optional(
              8,
              "partition_summaries",
              Types.ListType.ofRequired(
                  9,
                  Types.StructType.of(
                      Types.NestedField.required(10, "contains_null", Types.BooleanType.get()),
                      Types.NestedField.required(11, "contains_nan", Types.BooleanType.get()),
                      Types.NestedField.optional(12, "lower_bound", Types.StringType.get()),
                      Types.NestedField.optional(13, "upper_bound", Types.StringType.get())))),
          REF_SNAPSHOT_ID);

  /**
   * 构造 {@code table.all_manifests} 元数据表，使用默认表名后缀。
   *
   * @param table 底层真实表
   */
  AllManifestsTable(Table table) {
    this(table, table.name() + ".all_manifests");
  }

  /**
   * 构造 AllManifestsTable，允许自定义表名（用于元数据表的派生场景）。
   *
   * @param table 底层真实表
   * @param name 元数据表名
   */
  AllManifestsTable(Table table, String name) {
    super(table, name);
  }

  @Override
  public TableScan newScan() {
    return new AllManifestsTableScan(table(), MANIFEST_FILE_SCHEMA);
  }

  @Override
  public Schema schema() {
    return MANIFEST_FILE_SCHEMA;
  }

  @Override
  MetadataTableType metadataTableType() {
    return MetadataTableType.ALL_MANIFESTS;
  }

  /**
   * AllManifestsTable 的扫描实现：负责把表的快照集合转换为 manifest 行扫描任务。
   *
   * <p>设计意图：复用 {@link BaseAllMetadataTableScan} 的"全量快照视图"语义， 跨所有快照（而非仅当前快照）枚举 manifest，因而命名为 All*。
   */
  public static class AllManifestsTableScan extends BaseAllMetadataTableScan {

    AllManifestsTableScan(Table table, Schema fileSchema) {
      super(table, fileSchema, MetadataTableType.ALL_MANIFESTS);
    }

    private AllManifestsTableScan(Table table, Schema schema, TableScanContext context) {
      super(table, schema, MetadataTableType.ALL_MANIFESTS, context);
    }

    @Override
    protected TableScan newRefinedScan(Table table, Schema schema, TableScanContext context) {
      return new AllManifestsTableScan(table, schema, context);
    }

    /**
     * 规划文件扫描任务，每个快照对应一个 manifest 读取任务。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>若忽略残留谓词则使用 alwaysTrue，否则保留用户过滤器。
     *   <li>用 {@link SnapshotEvaluator} 把行过滤器绑定到 schema 上，对快照集合做谓词下推过滤。
     *   <li>对每个命中快照：若存在 manifest list 文件则构造 {@link ManifestListReadTask}； 否则回退到基于 {@link
     *       StaticDataTask} 的内存行（用于无 manifest list 的旧版本快照）。
     * </ol>
     */
    @Override
    protected CloseableIterable<FileScanTask> doPlanFiles() {
      FileIO io = table().io();
      Map<Integer, PartitionSpec> specs = Maps.newHashMap(table().specs());
      Expression filter = shouldIgnoreResiduals() ? Expressions.alwaysTrue() : filter();

      SnapshotEvaluator snapshotEvaluator =
          new SnapshotEvaluator(filter, MANIFEST_FILE_SCHEMA.asStruct(), isCaseSensitive());
      Iterable<Snapshot> filteredSnapshots =
          Iterables.filter(table().snapshots(), snapshotEvaluator::eval);

      return CloseableIterable.withNoopClose(
          Iterables.transform(
              filteredSnapshots,
              snap -> {
                if (snap.manifestListLocation() != null) {
                  return new ManifestListReadTask(
                      io, schema(), specs, snap.manifestListLocation(), filter, snap.snapshotId());
                } else {
                  return StaticDataTask.of(
                      io.newInputFile(
                          ((BaseTable) table()).operations().current().metadataFileLocation()),
                      MANIFEST_FILE_SCHEMA,
                      schema(),
                      snap.allManifests(io),
                      manifest ->
                          manifestFileToRow(
                              specs.get(manifest.partitionSpecId()), manifest, snap.snapshotId()));
                }
              }));
    }
  }

  /**
   * 读取单个 manifest list 文件并按 schema 投影返回行的 {@link DataTask}。
   *
   * <p>设计要点：manifest list 本身是 Avro 文件，本任务直接读取其记录并转换为元数据表行； 不真正分裂文件，因此 {@link #split(long)} 返回自身。
   */
  static class ManifestListReadTask implements DataTask {
    private final FileIO io;
    private final Schema schema;
    private final Map<Integer, PartitionSpec> specs;
    private final String manifestListLocation;
    private final Expression residual;
    private final long referenceSnapshotId;
    private DataFile lazyDataFile = null;

    ManifestListReadTask(
        FileIO io,
        Schema schema,
        Map<Integer, PartitionSpec> specs,
        String manifestListLocation,
        Expression residual,
        long referenceSnapshotId) {
      this.io = io;
      this.schema = schema;
      this.specs = specs;
      this.manifestListLocation = manifestListLocation;
      this.residual = residual;
      this.referenceSnapshotId = referenceSnapshotId;
    }

    @Override
    public List<DeleteFile> deletes() {
      return ImmutableList.of();
    }

    /**
     * 读取 manifest list Avro 文件，把每条 manifest 记录转换成元数据表行。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>通过 {@link Avro} 读取 manifest list 文件，并把 Avro schema 中的 {@code manifest_file}、{@code
     *       partitions} 等名称重映射到 Iceberg 的 {@link GenericManifestFile}/{@link
     *       GenericPartitionFieldSummary} 类。
     *   <li>关闭容器复用（reuseContainers=false），避免返回的行对象被后续迭代覆盖。
     *   <li>用 {@link StructProjection} 把完整 schema 投影到用户请求的 schema 上。
     * </ol>
     *
     * @return manifest 行的可关闭迭代器
     */
    @Override
    public CloseableIterable<StructLike> rows() {
      try (CloseableIterable<ManifestFile> manifests =
          Avro.read(io.newInputFile(manifestListLocation))
              .rename("manifest_file", GenericManifestFile.class.getName())
              .rename("partitions", GenericPartitionFieldSummary.class.getName())
              .rename("r508", GenericPartitionFieldSummary.class.getName())
              .project(ManifestFile.schema())
              .classLoader(GenericManifestFile.class.getClassLoader())
              .reuseContainers(false)
              .build()) {

        CloseableIterable<StructLike> rowIterable =
            CloseableIterable.transform(
                manifests,
                manifest ->
                    manifestFileToRow(
                        specs.get(manifest.partitionSpecId()), manifest, referenceSnapshotId));

        StructProjection projection = StructProjection.create(MANIFEST_FILE_SCHEMA, schema);
        return CloseableIterable.transform(rowIterable, projection::wrap);

      } catch (IOException e) {
        throw new RuntimeIOException(e, "Cannot read manifest list file: %s", manifestListLocation);
      }
    }

    /**
     * 懒构造代表本 manifest list 文件的 {@link DataFile}，作为扫描任务的"载体文件"。
     *
     * <p>设计要点：recordCount 仅置 1（不实际统计），因为引擎只关心文件存在性与格式。
     */
    @Override
    public DataFile file() {
      if (lazyDataFile == null) {
        this.lazyDataFile =
            DataFiles.builder(PartitionSpec.unpartitioned())
                .withInputFile(io.newInputFile(manifestListLocation))
                .withRecordCount(1)
                .withFormat(FileFormat.AVRO)
                .build();
      }

      return lazyDataFile;
    }

    @Override
    public PartitionSpec spec() {
      return PartitionSpec.unpartitioned();
    }

    @Override
    public long start() {
      return 0;
    }

    /**
     * 返回固定长度，避免真实查询文件长度带来的远程 IO 开销。
     *
     * @return 固定 8192 字节
     */
    @Override
    public long length() {
      // return a generic length to avoid looking up the actual length
      return 8192;
    }

    @Override
    public Expression residual() {
      // this table is unpartitioned so the residual is always constant
      return residual;
    }

    @Override
    public Iterable<FileScanTask> split(long splitSize) {
      return ImmutableList.of(this); // don't split
    }
  }

  /**
   * 把单个 {@link ManifestFile} 转换为元数据表中的一行。
   *
   * <p>设计要点：根据 manifest 的 content 类型（DATA/DELETES）分别填充对应计数列， 另一种类型的计数列置
   * 0，从而让单条记录同时反映"数据文件计数"和"删除文件计数"。
   *
   * @param spec manifest 所用的分区规则
   * @param manifest 待转换的 manifest
   * @param referenceSnapshotId 引用此 manifest 的快照 ID
   * @return 转换后的行
   */
  static StaticDataTask.Row manifestFileToRow(
      PartitionSpec spec, ManifestFile manifest, long referenceSnapshotId) {
    return StaticDataTask.Row.of(
        manifest.content().id(),
        manifest.path(),
        manifest.length(),
        manifest.partitionSpecId(),
        manifest.snapshotId(),
        manifest.content() == ManifestContent.DATA ? manifest.addedFilesCount() : 0,
        manifest.content() == ManifestContent.DATA ? manifest.existingFilesCount() : 0,
        manifest.content() == ManifestContent.DATA ? manifest.deletedFilesCount() : 0,
        manifest.content() == ManifestContent.DELETES ? manifest.addedFilesCount() : 0,
        manifest.content() == ManifestContent.DELETES ? manifest.existingFilesCount() : 0,
        manifest.content() == ManifestContent.DELETES ? manifest.deletedFilesCount() : 0,
        ManifestsTable.partitionSummariesToRows(spec, manifest.partitions()),
        referenceSnapshotId);
  }

  /**
   * 快照级谓词评估器：把行过滤器绑定到 manifest 表 schema 上，针对单个快照判定其是否可能命中。
   *
   * <p>设计意图：实现"谓词下推到快照级"，避免对每个快照都读取 manifest list； 只有当过滤器涉及 {@code reference_snapshot_id}
   * 时才会真正下推，其余条件按"可能命中"放行。
   */
  private static class SnapshotEvaluator {

    private final Expression boundExpr;

    private SnapshotEvaluator(Expression expr, Types.StructType structType, boolean caseSensitive) {
      this.boundExpr = Binder.bind(structType, expr, caseSensitive);
    }

    private boolean eval(Snapshot snapshot) {
      return new SnapshotEvalVisitor().eval(snapshot);
    }

    /**
     * 表达式访问器：在 {@code reference_snapshot_id} 上做比较，其他字段一律按"可能命中"返回。
     *
     * <p>设计要点：返回值语义为"快照是否可能匹配"，遵循三值逻辑的保守版本—— 当无法判断时返回 true，避免错误剪枝。
     */
    private class SnapshotEvalVisitor extends BoundExpressionVisitor<Boolean> {

      private long snapshotId;
      private static final boolean ROWS_MIGHT_MATCH = true;
      private static final boolean ROWS_CANNOT_MATCH = false;

      private boolean eval(Snapshot snapshot) {
        this.snapshotId = snapshot.snapshotId();
        return ExpressionVisitors.visitEvaluator(boundExpr, this);
      }

      @Override
      public Boolean alwaysTrue() {
        return ROWS_MIGHT_MATCH;
      }

      @Override
      public Boolean alwaysFalse() {
        return ROWS_CANNOT_MATCH;
      }

      @Override
      public Boolean not(Boolean result) {
        return !result;
      }

      @Override
      public Boolean and(Boolean leftResult, Boolean rightResult) {
        return leftResult && rightResult;
      }

      @Override
      public Boolean or(Boolean leftResult, Boolean rightResult) {
        return leftResult || rightResult;
      }

      @Override
      public <T> Boolean isNull(BoundReference<T> ref) {
        if (isSnapshotRef(ref)) {
          return ROWS_CANNOT_MATCH; // reference_snapshot_id is never null
        } else {
          return ROWS_MIGHT_MATCH;
        }
      }

      @Override
      public <T> Boolean notNull(BoundReference<T> ref) {
        return ROWS_MIGHT_MATCH;
      }

      @Override
      public <T> Boolean isNaN(BoundReference<T> ref) {
        if (isSnapshotRef(ref)) {
          return ROWS_CANNOT_MATCH; // reference_snapshot_id is never nan
        } else {
          return ROWS_MIGHT_MATCH;
        }
      }

      @Override
      public <T> Boolean notNaN(BoundReference<T> ref) {
        return ROWS_MIGHT_MATCH;
      }

      @Override
      public <T> Boolean lt(BoundReference<T> ref, Literal<T> lit) {
        return compareSnapshotRef(ref, lit, compareResult -> compareResult < 0);
      }

      @Override
      public <T> Boolean ltEq(BoundReference<T> ref, Literal<T> lit) {
        return compareSnapshotRef(ref, lit, compareResult -> compareResult <= 0);
      }

      @Override
      public <T> Boolean gt(BoundReference<T> ref, Literal<T> lit) {
        return compareSnapshotRef(ref, lit, compareResult -> compareResult > 0);
      }

      @Override
      public <T> Boolean gtEq(BoundReference<T> ref, Literal<T> lit) {
        return compareSnapshotRef(ref, lit, compareResult -> compareResult >= 0);
      }

      @Override
      public <T> Boolean eq(BoundReference<T> ref, Literal<T> lit) {
        return compareSnapshotRef(ref, lit, compareResult -> compareResult == 0);
      }

      @Override
      public <T> Boolean notEq(BoundReference<T> ref, Literal<T> lit) {
        return compareSnapshotRef(ref, lit, compareResult -> compareResult != 0);
      }

      @Override
      public <T> Boolean in(BoundReference<T> ref, Set<T> literalSet) {
        if (isSnapshotRef(ref)) {
          if (!literalSet.contains(snapshotId)) {
            return ROWS_CANNOT_MATCH;
          }
        }
        return ROWS_MIGHT_MATCH;
      }

      @Override
      public <T> Boolean notIn(BoundReference<T> ref, Set<T> literalSet) {
        if (isSnapshotRef(ref)) {
          if (literalSet.contains(snapshotId)) {
            return ROWS_CANNOT_MATCH;
          }
        }
        return ROWS_MIGHT_MATCH;
      }

      @Override
      public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
        return ROWS_MIGHT_MATCH;
      }

      @Override
      public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
        return ROWS_MIGHT_MATCH;
      }

      /**
       * 在 {@code reference_snapshot_id} 字段上做比较，使用 long 比较器判定快照是否满足条件。
       *
       * @param ref 已绑定的引用；仅当其指向 {@code reference_snapshot_id} 时才进行比较
       * @param lit 待比较的字面量
       * @param desiredResult 对比较结果应用的判定函数，返回 true 表示满足预期
       * @return 若比较结果不满足预期则返回 false（不可命中），否则返回 true（可能命中）
       */
      private <T> Boolean compareSnapshotRef(
          BoundReference<T> ref, Literal<T> lit, Function<Integer, Boolean> desiredResult) {
        if (isSnapshotRef(ref)) {
          Literal<Long> longLit = lit.to(Types.LongType.get());
          int cmp = longLit.comparator().compare(snapshotId, longLit.value());
          if (!desiredResult.apply(cmp)) {
            return ROWS_CANNOT_MATCH;
          }
        }
        return ROWS_MIGHT_MATCH;
      }

      /** 判断引用是否指向 {@code reference_snapshot_id} 字段。 */
      private <T> boolean isSnapshotRef(BoundReference<T> ref) {
        return ref.fieldId() == REF_SNAPSHOT_ID.fieldId();
      }
    }
  }
}
