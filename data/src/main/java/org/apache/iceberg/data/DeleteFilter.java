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
package org.apache.iceberg.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.iceberg.Accessor;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileContent;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.avro.DataReader;
import org.apache.iceberg.data.orc.GenericOrcReader;
import org.apache.iceberg.data.parquet.GenericParquetReaders;
import org.apache.iceberg.deletes.DeleteCounter;
import org.apache.iceberg.deletes.Deletes;
import org.apache.iceberg.deletes.PositionDeleteIndex;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Multimap;
import org.apache.iceberg.relocated.com.google.common.collect.Multimaps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.StructLikeSet;
import org.apache.iceberg.util.StructProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 删除文件过滤抽象基类：在读取数据文件时把“位置删除”和“等值删除”应用到行流上， 输出最终可见的记录。
 *
 * <p>所属模块：iceberg-data（向 JVM 应用提供基于 {@link Record} 等通用模型的 Iceberg 表读写支持； 本类是读路径上应用删除文件的过滤框架）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把传入的 {@link DeleteFile} 列表按内容类型拆分为位置删除（POSITION_DELETES） 与等值删除（EQUALITY_DELETES）两组。
 *   <li>计算“实际需要读取的 Schema”（{@code requiredSchema}）：在用户请求投影基础上 补齐删除判定所需字段（如 {@code _pos}、等值字段、可选的
 *       {@code _deleted} 列）。
 *   <li>提供 {@link #filter(CloseableIterable)} 入口，先应用位置删除、再应用等值删除， 支持两种语义：直接过滤掉被删行，或在 {@code
 *       _deleted} 列上打标记（保留行）。
 *   <li>按删除规模自适应选择“内存集合判定”或“流式判定”策略，避免大删除集合撑爆内存。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法：{@link #asStructLike(Object)}、{@link #getInputFile(String)}、 {@link
 *       #pos(Object)}、{@link #markRowDeleted(Object)} 由子类按自身记录类型实现， 让本类可与多种行表示（{@link Record}、Spark
 *       InternalRow 等）解耦。
 *   <li>位置删除按 {@code _pos} 行号判定；等值删除按“等值字段集合”分组构建 {@link StructLikeSet}，把多组等值删除合并为 OR 谓词。
 *   <li>阈值切换（{@link #setFilterThreshold}）：删除条数较少时构建内存索引快速判定； 超过阈值则走 {@link Deletes#streamingFilter}
 *       / {@link Deletes#streamingMarker} 的流式合并，避免一次性物化全部删除。
 *   <li>延迟与缓存：等值谓词、位置索引按需构建并缓存，避免重复打开删除文件。
 * </ul>
 *
 * <p>上下游关系：依赖 iceberg-core 的 deletes/util/expressions 工具，以及 avro/parquet/orc 读取入口与 {@code
 * GenericParquetReaders} / {@code GenericOrcReader} / {@code DataReader}； 被 {@link GenericReader}
 * 及各引擎读取路径调用。
 *
 * @param <T> 上层记录的 Java 类型
 */
public abstract class DeleteFilter<T> {
  private static final Logger LOG = LoggerFactory.getLogger(DeleteFilter.class);
  private static final long DEFAULT_SET_FILTER_THRESHOLD = 100_000L;
  private static final Schema POS_DELETE_SCHEMA =
      new Schema(MetadataColumns.DELETE_FILE_PATH, MetadataColumns.DELETE_FILE_POS);

  private final long setFilterThreshold;
  private final String filePath;
  private final List<DeleteFile> posDeletes;
  private final List<DeleteFile> eqDeletes;
  private final Schema requiredSchema;
  private final Accessor<StructLike> posAccessor;
  private final boolean hasIsDeletedColumn;
  private final int isDeletedColumnPosition;
  private final DeleteCounter counter;

  private PositionDeleteIndex deleteRowPositions = null;
  private List<Predicate<T>> isInDeleteSets = null;
  private Predicate<T> eqDeleteRows = null;

  /**
   * 构造删除过滤器。
   *
   * <p>逻辑：把传入的 deletes 按 content 拆分到位置删除与等值删除两个不可变列表； 调用 {@link #fileProjection} 计算
   * requiredSchema（在 requestedSchema 基础上补齐删除判定 所需字段）；取 {@code _pos} 字段访问器、判定是否存在 {@code _deleted}
   * 列并记录其列位置。
   *
   * @param filePath 当前数据文件路径（位置删除按它过滤）
   * @param deletes 当前数据文件关联的删除文件列表
   * @param tableSchema 表 Schema
   * @param requestedSchema 用户请求的投影 Schema
   * @param counter 删除计数器，用于统计被过滤掉的行数
   */
  protected DeleteFilter(
      String filePath,
      List<DeleteFile> deletes,
      Schema tableSchema,
      Schema requestedSchema,
      DeleteCounter counter) {
    this.setFilterThreshold = DEFAULT_SET_FILTER_THRESHOLD;
    this.filePath = filePath;
    this.counter = counter;

    ImmutableList.Builder<DeleteFile> posDeleteBuilder = ImmutableList.builder();
    ImmutableList.Builder<DeleteFile> eqDeleteBuilder = ImmutableList.builder();
    for (DeleteFile delete : deletes) {
      switch (delete.content()) {
        case POSITION_DELETES:
          LOG.debug("Adding position delete file {} to filter", delete.path());
          posDeleteBuilder.add(delete);
          break;
        case EQUALITY_DELETES:
          LOG.debug("Adding equality delete file {} to filter", delete.path());
          eqDeleteBuilder.add(delete);
          break;
        default:
          throw new UnsupportedOperationException(
              "Unknown delete file content: " + delete.content());
      }
    }

    this.posDeletes = posDeleteBuilder.build();
    this.eqDeletes = eqDeleteBuilder.build();
    this.requiredSchema = fileProjection(tableSchema, requestedSchema, posDeletes, eqDeletes);
    this.posAccessor = requiredSchema.accessorForField(MetadataColumns.ROW_POSITION.fieldId());
    this.hasIsDeletedColumn =
        requiredSchema.findField(MetadataColumns.IS_DELETED.fieldId()) != null;
    this.isDeletedColumnPosition = requiredSchema.columns().indexOf(MetadataColumns.IS_DELETED);
  }

  /**
   * 构造删除过滤器（不带外部计数器版本）。
   *
   * <p>内部创建一个新的 {@link DeleteCounter}，适用于不需要回读删除计数的场景。
   *
   * @param filePath 当前数据文件路径
   * @param deletes 关联的删除文件列表
   * @param tableSchema 表 Schema
   * @param requestedSchema 用户请求的投影 Schema
   */
  protected DeleteFilter(
      String filePath, List<DeleteFile> deletes, Schema tableSchema, Schema requestedSchema) {
    this(filePath, deletes, tableSchema, requestedSchema, new DeleteCounter());
  }

  /** 返回 {@code _deleted} 列在 requiredSchema 中的位置；不存在则返回 -1。 */
  protected int columnIsDeletedPosition() {
    return isDeletedColumnPosition;
  }

  /** 返回实际需要读取的 Schema（已补齐删除判定所需字段）。 */
  public Schema requiredSchema() {
    return requiredSchema;
  }

  /** 是否存在位置删除文件。 */
  public boolean hasPosDeletes() {
    return !posDeletes.isEmpty();
  }

  /** 是否存在等值删除文件。 */
  public boolean hasEqDeletes() {
    return !eqDeletes.isEmpty();
  }

  /** 将删除计数器加一（用于上层统计被过滤掉的行数）。 */
  public void incrementDeleteCount() {
    counter.increment();
  }

  /** 返回 {@code _pos} 字段访问器（包级可见，供子类使用）。 */
  Accessor<StructLike> posAccessor() {
    return posAccessor;
  }

  /** 子类实现：把上层记录 T 转为 {@link StructLike}，用于等值删除判定。 */
  protected abstract StructLike asStructLike(T record);

  /** 子类实现：按 location 返回删除文件对应的 {@link InputFile}。 */
  protected abstract InputFile getInputFile(String location);

  /**
   * 取记录在数据文件中的行号。
   *
   * <p>默认实现：用 {@code posAccessor} 从 {@link #asStructLike(Object)} 结果中读取 {@code _pos} 字段值并强转为 long。
   *
   * @param record 上层记录
   * @return 行号
   */
  protected long pos(T record) {
    return (Long) posAccessor.get(asStructLike(record));
  }

  /**
   * 主入口：对输入记录流应用全部删除，返回过滤/标记后的记录流。
   *
   * <p>逻辑：先 {@link #applyPosDeletes} 应用位置删除，再 {@link #applyEqDeletes} 应用等值删除。
   *
   * @param records 原始记录流
   * @return 处理后的记录流
   */
  public CloseableIterable<T> filter(CloseableIterable<T> records) {
    return applyEqDeletes(applyPosDeletes(records));
  }

  /**
   * 构建并缓存等值删除谓词列表（按“等值字段 ID 集合”分组，每组一个谓词）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若已缓存则直接返回；否则初始化列表，若没有等值删除文件则返回空列表。
   *   <li>按等值字段 ID 集合对等值删除文件分组（同一组字段对应的删除合并判定）。
   *   <li>对每组：从 requiredSchema 选出对应字段形成 deleteSchema，构造 {@link InternalRecordWrapper} 与 {@link
   *       StructProjection}（用于把上层行投影到 deleteSchema 顺序）；打开所有该组删除文件读取记录并 {@code copy}（因需被集合持有）； 调用
   *       {@link Deletes#toEqualitySet} 构建去重后的 {@link StructLikeSet}。
   *   <li>生成谓词：行投影后是否在该 set 中，命中即视为被删。
   * </ol>
   *
   * @return 等值删除谓词列表（每组一个）
   */
  private List<Predicate<T>> applyEqDeletes() {
    if (isInDeleteSets != null) {
      return isInDeleteSets;
    }

    isInDeleteSets = Lists.newArrayList();
    if (eqDeletes.isEmpty()) {
      return isInDeleteSets;
    }

    Multimap<Set<Integer>, DeleteFile> filesByDeleteIds =
        Multimaps.newMultimap(Maps.newHashMap(), Lists::newArrayList);
    for (DeleteFile delete : eqDeletes) {
      filesByDeleteIds.put(Sets.newHashSet(delete.equalityFieldIds()), delete);
    }

    for (Map.Entry<Set<Integer>, Collection<DeleteFile>> entry :
        filesByDeleteIds.asMap().entrySet()) {
      Set<Integer> ids = entry.getKey();
      Iterable<DeleteFile> deletes = entry.getValue();

      Schema deleteSchema = TypeUtil.select(requiredSchema, ids);
      InternalRecordWrapper wrapper = new InternalRecordWrapper(deleteSchema.asStruct());

      // a projection to select and reorder fields of the file schema to match the delete rows
      StructProjection projectRow = StructProjection.create(requiredSchema, deleteSchema);

      Iterable<CloseableIterable<Record>> deleteRecords =
          Iterables.transform(deletes, delete -> openDeletes(delete, deleteSchema));

      // copy the delete records because they will be held in a set
      CloseableIterable<Record> records =
          CloseableIterable.transform(CloseableIterable.concat(deleteRecords), Record::copy);

      StructLikeSet deleteSet =
          Deletes.toEqualitySet(
              CloseableIterable.transform(records, wrapper::copyFor), deleteSchema.asStruct());

      Predicate<T> isInDeleteSet =
          record -> deleteSet.contains(projectRow.wrap(asStructLike(record)));
      isInDeleteSets.add(isInDeleteSet);
    }

    return isInDeleteSets;
  }

  /**
   * 找出被等值删除命中的行（用于上层判断哪些行已被删）。
   *
   * <p>逻辑：把 {@link #applyEqDeletes()} 的全部谓词用 OR 合并，再对输入流做过滤， 保留“被删”的行。
   *
   * @param records 原始记录流
   * @return 命中等值删除的记录流
   */
  public CloseableIterable<T> findEqualityDeleteRows(CloseableIterable<T> records) {
    // Predicate to test whether a row has been deleted by equality deletions.
    Predicate<T> deletedRows = applyEqDeletes().stream().reduce(Predicate::or).orElse(t -> false);

    return CloseableIterable.filter(records, deletedRows);
  }

  /**
   * 把等值删除应用到记录流，过滤掉被删行或在 {@code _deleted} 列上打标记。
   *
   * <p>逻辑：将全部等值删除谓词用 OR 合并，再委托 {@link #createDeleteIterable} 按 {@code hasIsDeletedColumn}
   * 选择“打标记”或“过滤”模式。
   *
   * @param records 已应用位置删除后的记录流
   * @return 处理后的记录流
   */
  private CloseableIterable<T> applyEqDeletes(CloseableIterable<T> records) {
    Predicate<T> isEqDeleted = applyEqDeletes().stream().reduce(Predicate::or).orElse(t -> false);

    return createDeleteIterable(records, isEqDeleted);
  }

  /**
   * 子类可重写：把某条记录的 {@code _deleted} 列标记为已删除。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，仅当上层需要保留行而非过滤时 才由子类实现。
   *
   * @param item 待标记的记录
   */
  protected void markRowDeleted(T item) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement markRowDeleted");
  }

  /**
   * 返回“未被等值删除”的谓词（AND 合并各组的取反谓词），用于上层做行级过滤。
   *
   * <p>逻辑：把 {@link #applyEqDeletes()} 的每个谓词取反后用 AND 合并；结果缓存到 {@code eqDeleteRows}
   * 避免重复构建。无等值删除时返回恒真谓词。
   *
   * @return 未被等值删除的谓词
   */
  public Predicate<T> eqDeletedRowFilter() {
    if (eqDeleteRows == null) {
      eqDeleteRows =
          applyEqDeletes().stream().map(Predicate::negate).reduce(Predicate::and).orElse(t -> true);
    }
    return eqDeleteRows;
  }

  /**
   * 返回当前位置删除的位置索引（懒构建并缓存）。
   *
   * <p>逻辑：若无位置删除文件则返回 null；否则首次调用时打开所有位置删除文件， 调用 {@link Deletes#toPositionIndex} 构建索引并缓存。
   *
   * @return 位置删除索引；无位置删除时返回 null
   */
  public PositionDeleteIndex deletedRowPositions() {
    if (posDeletes.isEmpty()) {
      return null;
    }

    if (deleteRowPositions == null) {
      List<CloseableIterable<Record>> deletes = Lists.transform(posDeletes, this::openPosDeletes);
      deleteRowPositions = Deletes.toPositionIndex(filePath, deletes);
    }
    return deleteRowPositions;
  }

  /**
   * 把位置删除应用到记录流。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>无位置删除时直接返回原流。
   *   <li>位置删除总条数小于 {@link #setFilterThreshold} 时：构建内存 {@link PositionDeleteIndex}，按行号判定是否被删，走
   *       {@link #createDeleteIterable}（过滤或打标记）。
   *   <li>超过阈值时：走流式合并——若存在 {@code _deleted} 列则用 {@link Deletes#streamingMarker} 边读边标记，否则用 {@link
   *       Deletes#streamingFilter} 边读边过滤，避免物化全部删除位置。
   * </ul>
   *
   * @param records 原始记录流
   * @return 处理后的记录流
   */
  private CloseableIterable<T> applyPosDeletes(CloseableIterable<T> records) {
    if (posDeletes.isEmpty()) {
      return records;
    }

    List<CloseableIterable<Record>> deletes = Lists.transform(posDeletes, this::openPosDeletes);

    // if there are fewer deletes than a reasonable number to keep in memory, use a set
    if (posDeletes.stream().mapToLong(DeleteFile::recordCount).sum() < setFilterThreshold) {
      PositionDeleteIndex positionIndex = Deletes.toPositionIndex(filePath, deletes);
      Predicate<T> isDeleted = record -> positionIndex.isDeleted(pos(record));
      return createDeleteIterable(records, isDeleted);
    }

    return hasIsDeletedColumn
        ? Deletes.streamingMarker(
            records, this::pos, Deletes.deletePositions(filePath, deletes), this::markRowDeleted)
        : Deletes.streamingFilter(
            records, this::pos, Deletes.deletePositions(filePath, deletes), counter);
  }

  /**
   * 根据是否存在 {@code _deleted} 列，选择“打标记”或“过滤”两种删除应用方式。
   *
   * @param records 记录流
   * @param isDeleted 判定某条记录是否被删的谓词
   * @return 处理后的记录流
   */
  private CloseableIterable<T> createDeleteIterable(
      CloseableIterable<T> records, Predicate<T> isDeleted) {
    return hasIsDeletedColumn
        ? Deletes.markDeleted(records, isDeleted, this::markRowDeleted)
        : Deletes.filterDeleted(records, isDeleted, counter);
  }

  /** 用位置删除 Schema 打开删除文件。 */
  private CloseableIterable<Record> openPosDeletes(DeleteFile file) {
    return openDeletes(file, POS_DELETE_SCHEMA);
  }

  /**
   * 打开单个删除文件并按 deleteSchema 投影读取为 {@link Record} 流。
   *
   * <p>逻辑：按删除文件格式选择 Avro/Parquet/ORC 读取入口；对 Parquet 和 ORC，若为 位置删除文件则额外下推 {@code _file_path =
   * filePath} 过滤，避免读取其它数据文件 的位置删除行。Avro 不支持下推过滤故全量读取。
   *
   * @param deleteFile 删除文件
   * @param deleteSchema 读取投影 Schema
   * @return 删除记录流
   */
  private CloseableIterable<Record> openDeletes(DeleteFile deleteFile, Schema deleteSchema) {
    LOG.trace("Opening delete file {}", deleteFile.path());
    InputFile input = getInputFile(deleteFile.path().toString());
    switch (deleteFile.format()) {
      case AVRO:
        return Avro.read(input)
            .project(deleteSchema)
            .reuseContainers()
            .createReaderFunc(DataReader::create)
            .build();

      case PARQUET:
        Parquet.ReadBuilder builder =
            Parquet.read(input)
                .project(deleteSchema)
                .reuseContainers()
                .createReaderFunc(
                    fileSchema -> GenericParquetReaders.buildReader(deleteSchema, fileSchema));

        if (deleteFile.content() == FileContent.POSITION_DELETES) {
          builder.filter(Expressions.equal(MetadataColumns.DELETE_FILE_PATH.name(), filePath));
        }

        return builder.build();

      case ORC:
        // Reusing containers is automatic for ORC. No need to set 'reuseContainers' here.
        ORC.ReadBuilder orcBuilder =
            ORC.read(input)
                .project(deleteSchema)
                .createReaderFunc(
                    fileSchema -> GenericOrcReader.buildReader(deleteSchema, fileSchema));

        if (deleteFile.content() == FileContent.POSITION_DELETES) {
          orcBuilder.filter(Expressions.equal(MetadataColumns.DELETE_FILE_PATH.name(), filePath));
        }

        return orcBuilder.build();
      default:
        throw new UnsupportedOperationException(
            String.format(
                "Cannot read deletes, %s is not a supported format: %s",
                deleteFile.format().name(), deleteFile.path()));
    }
  }

  /**
   * 计算实际需要读取的 Schema：在 requestedSchema 基础上补齐删除判定所需字段。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>无任何删除文件时直接返回 requestedSchema。
   *   <li>收集 requiredIds：有位置删除则加 {@code _pos}；遍历等值删除累加其等值字段 ID。
   *   <li>求 requiredIds 相对 requestedSchema 已投影 ID 的差集 missingIds；为空则直接返回。
   *   <li>否则把 missingIds 中普通字段按表 Schema 顺序追加到列尾；{@code _pos} 与 {@code _deleted}（如缺失）放在最后。嵌套字段暂不支持（见
   *       TODO）。
   * </ol>
   *
   * <p>设计意图：保证读取数据文件时一次性读到删除判定所需的全部字段，避免重复 IO； 元数据列追加在尾部，避免影响用户投影的主体列顺序。
   *
   * @param tableSchema 表 Schema
   * @param requestedSchema 用户请求投影
   * @param posDeletes 位置删除文件列表
   * @param eqDeletes 等值删除文件列表
   * @return 补齐后的实际读取 Schema
   */
  private static Schema fileProjection(
      Schema tableSchema,
      Schema requestedSchema,
      List<DeleteFile> posDeletes,
      List<DeleteFile> eqDeletes) {
    if (posDeletes.isEmpty() && eqDeletes.isEmpty()) {
      return requestedSchema;
    }

    Set<Integer> requiredIds = Sets.newLinkedHashSet();
    if (!posDeletes.isEmpty()) {
      requiredIds.add(MetadataColumns.ROW_POSITION.fieldId());
    }

    for (DeleteFile eqDelete : eqDeletes) {
      requiredIds.addAll(eqDelete.equalityFieldIds());
    }

    Set<Integer> missingIds =
        Sets.newLinkedHashSet(
            Sets.difference(requiredIds, TypeUtil.getProjectedIds(requestedSchema)));

    if (missingIds.isEmpty()) {
      return requestedSchema;
    }

    // TODO: support adding nested columns. this will currently fail when finding nested columns to
    // add
    List<Types.NestedField> columns = Lists.newArrayList(requestedSchema.columns());
    for (int fieldId : missingIds) {
      if (fieldId == MetadataColumns.ROW_POSITION.fieldId()
          || fieldId == MetadataColumns.IS_DELETED.fieldId()) {
        continue; // add _pos and _deleted at the end
      }

      Types.NestedField field = tableSchema.asStruct().field(fieldId);
      Preconditions.checkArgument(field != null, "Cannot find required field for ID %s", fieldId);

      columns.add(field);
    }

    if (missingIds.contains(MetadataColumns.ROW_POSITION.fieldId())) {
      columns.add(MetadataColumns.ROW_POSITION);
    }

    if (missingIds.contains(MetadataColumns.IS_DELETED.fieldId())) {
      columns.add(MetadataColumns.IS_DELETED);
    }

    return new Schema(columns);
  }
}
