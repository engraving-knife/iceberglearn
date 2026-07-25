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
package org.apache.iceberg.parquet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.crypto.FileDecryptionProperties;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ColumnPath;
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：Parquet 读取器配置，封装文件打开、schema 投影、row group 过滤与读取器构造。
 *
 * <p>所属模块：iceberg-parquet（读取侧配置中心，被 ParquetReader/VectorizedParquetReader 使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>打开 Parquet 文件，读取文件 schema 并按 expectedSchema 做列投影。
 *   <li>处理无 ID 的旧文件：通过 NameMapping 补 ID 或按名称回退。
 *   <li>对每个 row group 应用 stats/dict/bloom 三重过滤器，生成 shouldSkip 数组。
 *   <li>计算各 row group 的起始行位置（用于 _pos 元数据列）与总值数。
 *   <li>按是否向量化构造 ParquetValueReader 或 VectorizedReader。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>集中配置：把读取所需的所有状态（reader/projection/model/shouldSkip 等）一次构造完毕， 避免读取过程中重复打开文件。
 *   <li>copy() 复制：支持在不重新打开文件的情况下复制配置（reader 置 null，使用时再打开）。
 *   <li>三重过滤：stats（min/max）+ dict（字典值）+ bloom 过滤器组合下推，最大化 row group 裁剪。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.parquet.ParquetReader} 与 {@link VectorizedParquetReader}
 * 构造；依赖 {@link ParquetFileReader}、 {@link ParquetMetricsRowGroupFilter}、{@link
 * ParquetDictionaryRowGroupFilter}、 {@link ParquetBloomRowGroupFilter}。
 *
 * @param <T> 读取的值类型
 */
class ReadConf<T> {
  private final ParquetFileReader reader;
  private final InputFile file;
  private final ParquetReadOptions options;
  private final MessageType projection;
  private final ParquetValueReader<T> model;
  private final VectorizedReader<T> vectorizedModel;
  private final List<BlockMetaData> rowGroups;
  private final boolean[] shouldSkip;
  private final long totalValues;
  private final boolean reuseContainers;
  private final Integer batchSize;
  private final long[] startRowPositions;

  // List of column chunk metadata for each row group
  private final List<Map<ColumnPath, ColumnChunkMetaData>> columnChunkMetaDataForRowGroups;

  /**
   * 构造读取配置。
   *
   * <p>逻辑：打开文件读取 schema；按 hasIds/nameMapping/fallback 三路做列投影； 生成 shouldSkip 数组并对每个 row group
   * 应用三重过滤；计算起始行位置与总值数； 按 readerFunc 或 batchedReaderFunc 构造读取器。
   *
   * @param file 输入文件
   * @param options Parquet 读取选项
   * @param expectedSchema 期望读取的 schema
   * @param filter 过滤表达式（可为 null）
   * @param readerFunc 逐行读取器构造函数（与 batchedReaderFunc 互斥）
   * @param batchedReaderFunc 向量化读取器构造函数
   * @param nameMapping 字段名到 ID 的映射
   * @param reuseContainers 是否复用容器对象
   * @param caseSensitive 是否大小写敏感
   * @param bSize 批次大小
   */
  @SuppressWarnings("unchecked")
  ReadConf(
      InputFile file,
      ParquetReadOptions options,
      Schema expectedSchema,
      Expression filter,
      Function<MessageType, ParquetValueReader<?>> readerFunc,
      Function<MessageType, VectorizedReader<?>> batchedReaderFunc,
      NameMapping nameMapping,
      boolean reuseContainers,
      boolean caseSensitive,
      Integer bSize) {
    this.file = file;
    this.options = options;
    this.reader = newReader(file, options);
    MessageType fileSchema = reader.getFileMetaData().getSchema();

    MessageType typeWithIds;
    if (ParquetSchemaUtil.hasIds(fileSchema)) {
      typeWithIds = fileSchema;
      this.projection = ParquetSchemaUtil.pruneColumns(fileSchema, expectedSchema);
    } else if (nameMapping != null) {
      typeWithIds = ParquetSchemaUtil.applyNameMapping(fileSchema, nameMapping);
      this.projection = ParquetSchemaUtil.pruneColumns(typeWithIds, expectedSchema);
    } else {
      typeWithIds = ParquetSchemaUtil.addFallbackIds(fileSchema);
      this.projection = ParquetSchemaUtil.pruneColumnsFallback(fileSchema, expectedSchema);
    }

    this.rowGroups = reader.getRowGroups();
    this.shouldSkip = new boolean[rowGroups.size()];
    this.startRowPositions = new long[rowGroups.size()];

    // Fetch all row groups starting positions to compute the row offsets of the filtered row groups
    Map<Long, Long> offsetToStartPos = generateOffsetToStartPos(expectedSchema);

    ParquetMetricsRowGroupFilter statsFilter = null;
    ParquetDictionaryRowGroupFilter dictFilter = null;
    ParquetBloomRowGroupFilter bloomFilter = null;
    if (filter != null) {
      statsFilter = new ParquetMetricsRowGroupFilter(expectedSchema, filter, caseSensitive);
      dictFilter = new ParquetDictionaryRowGroupFilter(expectedSchema, filter, caseSensitive);
      bloomFilter = new ParquetBloomRowGroupFilter(expectedSchema, filter, caseSensitive);
    }

    long computedTotalValues = 0L;
    for (int i = 0; i < shouldSkip.length; i += 1) {
      BlockMetaData rowGroup = rowGroups.get(i);
      startRowPositions[i] =
          offsetToStartPos == null ? 0 : offsetToStartPos.get(rowGroup.getStartingPos());
      boolean shouldRead =
          filter == null
              || (statsFilter.shouldRead(typeWithIds, rowGroup)
                  && dictFilter.shouldRead(
                      typeWithIds, rowGroup, reader.getDictionaryReader(rowGroup))
                  && bloomFilter.shouldRead(
                      typeWithIds, rowGroup, reader.getBloomFilterDataReader(rowGroup)));
      this.shouldSkip[i] = !shouldRead;
      if (shouldRead) {
        computedTotalValues += rowGroup.getRowCount();
      }
    }

    this.totalValues = computedTotalValues;
    if (readerFunc != null) {
      this.model = (ParquetValueReader<T>) readerFunc.apply(typeWithIds);
      this.vectorizedModel = null;
      this.columnChunkMetaDataForRowGroups = null;
    } else {
      this.model = null;
      this.vectorizedModel = (VectorizedReader<T>) batchedReaderFunc.apply(typeWithIds);
      this.columnChunkMetaDataForRowGroups = getColumnChunkMetadataForRowGroups();
    }

    this.reuseContainers = reuseContainers;
    this.batchSize = bSize;
  }

  private ReadConf(ReadConf<T> toCopy) {
    this.reader = null;
    this.file = toCopy.file;
    this.options = toCopy.options;
    this.projection = toCopy.projection;
    this.model = toCopy.model;
    this.rowGroups = toCopy.rowGroups;
    this.shouldSkip = toCopy.shouldSkip;
    this.totalValues = toCopy.totalValues;
    this.reuseContainers = toCopy.reuseContainers;
    this.batchSize = toCopy.batchSize;
    this.vectorizedModel = toCopy.vectorizedModel;
    this.columnChunkMetaDataForRowGroups = toCopy.columnChunkMetaDataForRowGroups;
    this.startRowPositions = toCopy.startRowPositions;
  }

  /**
   * 返回 ParquetFileReader，设置投影 schema。
   *
   * <p>逻辑：若 reader 存在则设置投影 schema 后返回；否则新建 reader。 copy() 后 reader 为 null，需重新打开。
   *
   * @return ParquetFileReader
   */
  ParquetFileReader reader() {
    if (reader != null) {
      reader.setRequestedSchema(projection);
      return reader;
    }

    ParquetFileReader newReader = newReader(file, options);
    newReader.setRequestedSchema(projection);
    return newReader;
  }

  ParquetValueReader<T> model() {
    return model;
  }

  VectorizedReader<T> vectorizedModel() {
    return vectorizedModel;
  }

  boolean[] shouldSkip() {
    return shouldSkip;
  }

  /**
   * 生成 row group 起始字节偏移到起始行位置的映射。
   *
   * <p>逻辑：仅当 schema 包含 _pos 元数据列时才生成；独立打开文件读取各 row group 的起始偏移与行数，累加计算每个 row group 的起始行位置。
   *
   * @param schema 期望 schema
   * @return 偏移->起始行位置映射，或 null 表示不需要
   */
  private Map<Long, Long> generateOffsetToStartPos(Schema schema) {
    if (schema.findField(MetadataColumns.ROW_POSITION.fieldId()) == null) {
      return null;
    }

    FileDecryptionProperties decryptionProperties =
        (options == null) ? null : options.getDecryptionProperties();

    ParquetReadOptions readOptions =
        ParquetReadOptions.builder().withDecryption(decryptionProperties).build();

    try (ParquetFileReader fileReader = newReader(file, readOptions)) {
      Map<Long, Long> offsetToStartPos = Maps.newHashMap();

      long curRowCount = 0;
      for (int i = 0; i < fileReader.getRowGroups().size(); i += 1) {
        BlockMetaData meta = fileReader.getRowGroups().get(i);
        offsetToStartPos.put(meta.getStartingPos(), curRowCount);
        curRowCount += meta.getRowCount();
      }

      return offsetToStartPos;

    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create/close reader for file: " + file, e);
    }
  }

  long[] startRowPositions() {
    return startRowPositions;
  }

  long totalValues() {
    return totalValues;
  }

  boolean reuseContainers() {
    return reuseContainers;
  }

  Integer batchSize() {
    return batchSize;
  }

  List<Map<ColumnPath, ColumnChunkMetaData>> columnChunkMetadataForRowGroups() {
    return columnChunkMetaDataForRowGroups;
  }

  /**
   * 复制本配置（reader 置 null，使用时重新打开文件）。
   *
   * @return 配置副本
   */
  ReadConf<T> copy() {
    return new ReadConf<>(this);
  }

  /**
   * 打开 Parquet 文件 reader。
   *
   * @param file 输入文件
   * @param options 读取选项
   * @return ParquetFileReader
   * @throws RuntimeIOException 打开失败
   */
  private static ParquetFileReader newReader(InputFile file, ParquetReadOptions options) {
    try {
      return ParquetFileReader.open(ParquetIO.file(file), options);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to open Parquet file: %s", file.location());
    }
  }

  /**
   * 为每个 row group 收集投影列的 ColumnChunkMetaData。
   *
   * <p>逻辑：按投影列路径集合过滤各 row group 的列元数据，跳过的 row group 返回空 map。 供向量化读取器使用。
   *
   * @return 各 row group 的列元数据映射列表
   */
  private List<Map<ColumnPath, ColumnChunkMetaData>> getColumnChunkMetadataForRowGroups() {
    Set<ColumnPath> projectedColumns =
        projection.getColumns().stream()
            .map(columnDescriptor -> ColumnPath.get(columnDescriptor.getPath()))
            .collect(Collectors.toSet());
    ImmutableList.Builder<Map<ColumnPath, ColumnChunkMetaData>> listBuilder =
        ImmutableList.builder();
    for (int i = 0; i < rowGroups.size(); i++) {
      if (!shouldSkip[i]) {
        BlockMetaData blockMetaData = rowGroups.get(i);
        ImmutableMap.Builder<ColumnPath, ColumnChunkMetaData> mapBuilder = ImmutableMap.builder();
        blockMetaData.getColumns().stream()
            .filter(columnChunkMetaData -> projectedColumns.contains(columnChunkMetaData.getPath()))
            .forEach(
                columnChunkMetaData ->
                    mapBuilder.put(columnChunkMetaData.getPath(), columnChunkMetaData));
        listBuilder.add(mapBuilder.build());
      } else {
        listBuilder.add(ImmutableMap.of());
      }
    }
    return listBuilder.build();
  }
}
