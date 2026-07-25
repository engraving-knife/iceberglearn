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
package org.apache.iceberg.flink.source;

import java.util.List;
import java.util.Map;
import org.apache.flink.annotation.Internal;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.DeleteFilter;
import org.apache.iceberg.encryption.InputFilesDecryptor;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.FlinkSourceFilter;
import org.apache.iceberg.flink.RowDataWrapper;
import org.apache.iceberg.flink.data.FlinkAvroReader;
import org.apache.iceberg.flink.data.FlinkOrcReader;
import org.apache.iceberg.flink.data.FlinkParquetReaders;
import org.apache.iceberg.flink.data.RowDataProjection;
import org.apache.iceberg.flink.data.RowDataUtil;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.PartitionUtil;

/**
 * 文件级说明：把 Iceberg 文件扫描任务读取为 Flink RowData 的 reader。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据文件格式（PARQUET/AVRO/ORC）创建对应的 Iterable。
 *   <li>应用删除文件过滤（{@link DeleteFilter}）以保证读取最新数据。
 *   <li>应用行级过滤、投影与分区常量下推。
 * </ul>
 *
 * <p>设计意图：复用 Iceberg 的读取 builder（{@link Parquet}/{@link Avro}/{@link ORC}）， 通过 Flink 专属 reader
 * 实现类把内部结构转换为 RowData； 通过 {@link FlinkDeleteFilter} 把等值/位置删除应用于流式读取。
 *
 * <p>上下游关系：上游为 {@link FileScanTask}， 下游为 Flink reader 实现（{@link FlinkParquetReaders}/{@link
 * FlinkAvroReader}/{@link FlinkOrcReader}）。
 */
@Internal
public class RowDataFileScanTaskReader implements FileScanTaskReader<RowData> {

  private final Schema tableSchema;
  private final Schema projectedSchema;
  private final String nameMapping;
  private final boolean caseSensitive;
  private final FlinkSourceFilter rowFilter;

  /**
   * 构造 reader。
   *
   * <p>逻辑：若提供非空 filters，合并为单个表达式并构造 FlinkSourceFilter。
   *
   * @param tableSchema 表 schema
   * @param projectedSchema 投影 schema
   * @param nameMapping 名称映射，可为空
   * @param caseSensitive 是否大小写敏感
   * @param filters 过滤表达式列表
   */
  public RowDataFileScanTaskReader(
      Schema tableSchema,
      Schema projectedSchema,
      String nameMapping,
      boolean caseSensitive,
      List<Expression> filters) {
    this.tableSchema = tableSchema;
    this.projectedSchema = projectedSchema;
    this.nameMapping = nameMapping;
    this.caseSensitive = caseSensitive;

    if (filters != null && !filters.isEmpty()) {
      Expression combinedExpression =
          filters.stream().reduce(Expressions.alwaysTrue(), Expressions::and);
      this.rowFilter =
          new FlinkSourceFilter(this.projectedSchema, combinedExpression, this.caseSensitive);
    } else {
      this.rowFilter = null;
    }
  }

  /**
   * 打开文件扫描 task 的迭代器。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>选择分区 schema 并构建分区常量映射。
   *   <li>用 FlinkDeleteFilter 包装原始 iterable 过滤删除行。
   *   <li>若投影与 required schema 不同则再次投影去除元数据列。
   * </ol>
   *
   * @param task 文件扫描任务
   * @param inputFilesDecryptor 输入文件解密器
   * @return RowData 迭代器
   */
  @Override
  public CloseableIterator<RowData> open(
      FileScanTask task, InputFilesDecryptor inputFilesDecryptor) {
    Schema partitionSchema = TypeUtil.select(projectedSchema, task.spec().identitySourceIds());

    Map<Integer, ?> idToConstant =
        partitionSchema.columns().isEmpty()
            ? ImmutableMap.of()
            : PartitionUtil.constantsMap(task, RowDataUtil::convertConstant);

    FlinkDeleteFilter deletes =
        new FlinkDeleteFilter(task, tableSchema, projectedSchema, inputFilesDecryptor);
    CloseableIterable<RowData> iterable =
        deletes.filter(
            newIterable(task, deletes.requiredSchema(), idToConstant, inputFilesDecryptor));

    // 投影 RowData 去除额外的元数据列
    if (!projectedSchema.sameSchema(deletes.requiredSchema())) {
      RowDataProjection rowDataProjection =
          RowDataProjection.create(
              deletes.requiredRowType(),
              deletes.requiredSchema().asStruct(),
              projectedSchema.asStruct());
      iterable = CloseableIterable.transform(iterable, rowDataProjection::wrap);
    }

    return iterable.iterator();
  }

  /**
   * 按文件格式创建对应 iterable，并应用行级过滤。
   *
   * <p>逻辑：对 PARQUET/AVRO/ORC 分别调用 newParquetIterable/newAvroIterable/newOrcIterable； 若提供了 rowFilter
   * 则包装一层过滤。
   *
   * @param task 文件扫描任务
   * @param schema 读取 schema
   * @param idToConstant 分区常量映射
   * @param inputFilesDecryptor 输入文件解密器
   * @return RowData iterable
   */
  private CloseableIterable<RowData> newIterable(
      FileScanTask task,
      Schema schema,
      Map<Integer, ?> idToConstant,
      InputFilesDecryptor inputFilesDecryptor) {
    CloseableIterable<RowData> iter;
    if (task.isDataTask()) {
      throw new UnsupportedOperationException("Cannot read data task.");
    } else {
      switch (task.file().format()) {
        case PARQUET:
          iter = newParquetIterable(task, schema, idToConstant, inputFilesDecryptor);
          break;

        case AVRO:
          iter = newAvroIterable(task, schema, idToConstant, inputFilesDecryptor);
          break;

        case ORC:
          iter = newOrcIterable(task, schema, idToConstant, inputFilesDecryptor);
          break;

        default:
          throw new UnsupportedOperationException(
              "Cannot read unknown format: " + task.file().format());
      }
    }

    if (rowFilter != null) {
      return CloseableIterable.filter(iter, rowFilter::filter);
    }
    return iter;
  }

  /** 创建 AVRO 文件的 iterable。 */
  private CloseableIterable<RowData> newAvroIterable(
      FileScanTask task,
      Schema schema,
      Map<Integer, ?> idToConstant,
      InputFilesDecryptor inputFilesDecryptor) {
    Avro.ReadBuilder builder =
        Avro.read(inputFilesDecryptor.getInputFile(task))
            .reuseContainers()
            .project(schema)
            .split(task.start(), task.length())
            .createReaderFunc(readSchema -> new FlinkAvroReader(schema, readSchema, idToConstant));

    if (nameMapping != null) {
      builder.withNameMapping(NameMappingParser.fromJson(nameMapping));
    }

    return builder.build();
  }

  /** 创建 Parquet 文件的 iterable，带 residual 过滤与大小写敏感性。 */
  private CloseableIterable<RowData> newParquetIterable(
      FileScanTask task,
      Schema schema,
      Map<Integer, ?> idToConstant,
      InputFilesDecryptor inputFilesDecryptor) {
    Parquet.ReadBuilder builder =
        Parquet.read(inputFilesDecryptor.getInputFile(task))
            .split(task.start(), task.length())
            .project(schema)
            .createReaderFunc(
                fileSchema -> FlinkParquetReaders.buildReader(schema, fileSchema, idToConstant))
            .filter(task.residual())
            .caseSensitive(caseSensitive)
            .reuseContainers();

    if (nameMapping != null) {
      builder.withNameMapping(NameMappingParser.fromJson(nameMapping));
    }

    return builder.build();
  }

  /**
   * 创建 ORC 文件的 iterable。
   *
   * <p>逻辑：ORC 不支持读取常量与元数据字段，先从 schema 中剔除这些列再投影。
   */
  private CloseableIterable<RowData> newOrcIterable(
      FileScanTask task,
      Schema schema,
      Map<Integer, ?> idToConstant,
      InputFilesDecryptor inputFilesDecryptor) {
    Schema readSchemaWithoutConstantAndMetadataFields =
        TypeUtil.selectNot(
            schema, Sets.union(idToConstant.keySet(), MetadataColumns.metadataFieldIds()));

    ORC.ReadBuilder builder =
        ORC.read(inputFilesDecryptor.getInputFile(task))
            .project(readSchemaWithoutConstantAndMetadataFields)
            .split(task.start(), task.length())
            .createReaderFunc(
                readOrcSchema -> new FlinkOrcReader(schema, readOrcSchema, idToConstant))
            .filter(task.residual())
            .caseSensitive(caseSensitive);

    if (nameMapping != null) {
      builder.withNameMapping(NameMappingParser.fromJson(nameMapping));
    }

    return builder.build();
  }

  /**
   * Flink 的删除文件过滤器，把 Iceberg 的 DeleteFilter 适配到 RowData。
   *
   * <p>逻辑：维护 required RowType 与 RowDataWrapper， 把 RowData 包装为 StructLike 以便父类执行等值/位置删除过滤； 通过
   * inputFilesDecryptor 获取删除文件输入。
   */
  private static class FlinkDeleteFilter extends DeleteFilter<RowData> {
    private final RowType requiredRowType;
    private final RowDataWrapper asStructLike;
    private final InputFilesDecryptor inputFilesDecryptor;

    FlinkDeleteFilter(
        FileScanTask task,
        Schema tableSchema,
        Schema requestedSchema,
        InputFilesDecryptor inputFilesDecryptor) {
      super(task.file().path().toString(), task.deletes(), tableSchema, requestedSchema);
      this.requiredRowType = FlinkSchemaUtil.convert(requiredSchema());
      this.asStructLike = new RowDataWrapper(requiredRowType, requiredSchema().asStruct());
      this.inputFilesDecryptor = inputFilesDecryptor;
    }

    /** 返回 required RowType。 */
    public RowType requiredRowType() {
      return requiredRowType;
    }

    /** 把 RowData 包装为 StructLike 以便删除过滤。 */
    @Override
    protected StructLike asStructLike(RowData row) {
      return asStructLike.wrap(row);
    }

    /** 通过 inputFilesDecryptor 获取指定路径的输入文件。 */
    @Override
    protected InputFile getInputFile(String location) {
      return inputFilesDecryptor.getInputFile(location);
    }
  }
}
