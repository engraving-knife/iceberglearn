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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ColumnPath;
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：向量化 Parquet 读取器，按批次（batch）读取数据。
 *
 * <p>所属模块：iceberg-parquet（向量化读取入口，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：与 {@link ParquetReader} 类似，但按批次（而非逐行）读取数据， 适配向量化执行引擎（如 Spark Vectorized Parquet
 * Reader），减少方法调用开销。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>批量读取：每次 next() 读取最多 batchSize 条记录，通过 VectorizedReader 一次处理多行。
 *   <li>行组级列元数据：传递 ColumnChunkMetaData 给读取器，支持列级过滤（如字典过滤）。
 *   <li>与 ParquetReader 共享 ReadConf 结构，但使用 vectorizedModel 而非普通 model。
 * </ul>
 *
 * <p>上下游关系：被各引擎的向量化读取流程调用；依赖 ParquetFileReader、VectorizedReader。
 *
 * @param <T> 读取的批次类型
 */
public class VectorizedParquetReader<T> extends CloseableGroup implements CloseableIterable<T> {
  private final InputFile input;
  private final Schema expectedSchema;
  private final ParquetReadOptions options;
  private final Function<MessageType, VectorizedReader<?>> batchReaderFunc;
  private final Expression filter;
  private boolean reuseContainers;
  private final boolean caseSensitive;
  private final int batchSize;
  private final NameMapping nameMapping;

  /**
   * 构造向量化读取器。
   *
   * @param input 输入文件
   * @param expectedSchema 期望 schema（支持投影）
   * @param options Parquet 读取选项
   * @param readerFunc 构造向量化读取器的函数
   * @param nameMapping 字段名→ID 映射
   * @param filter 过滤表达式（alwaysTrue 替换为 null）
   * @param reuseContainers 是否复用容器
   * @param caseSensitive 大小写敏感
   * @param maxRecordsPerBatch 每批次最大记录数
   */
  public VectorizedParquetReader(
      InputFile input,
      Schema expectedSchema,
      ParquetReadOptions options,
      Function<MessageType, VectorizedReader<?>> readerFunc,
      NameMapping nameMapping,
      Expression filter,
      boolean reuseContainers,
      boolean caseSensitive,
      int maxRecordsPerBatch) {
    this.input = input;
    this.expectedSchema = expectedSchema;
    this.options = options;
    this.batchReaderFunc = readerFunc;
    // replace alwaysTrue with null to avoid extra work evaluating a trivial filter
    this.filter = filter == Expressions.alwaysTrue() ? null : filter;
    this.reuseContainers = reuseContainers;
    this.caseSensitive = caseSensitive;
    this.batchSize = maxRecordsPerBatch;
    this.nameMapping = nameMapping;
  }

  private ReadConf conf = null;

  /** 延迟初始化读取配置（与 ParquetReader.init 类似）。 */
  private ReadConf init() {
    if (conf == null) {
      ReadConf readConf =
          new ReadConf(
              input,
              options,
              expectedSchema,
              filter,
              null,
              batchReaderFunc,
              nameMapping,
              reuseContainers,
              caseSensitive,
              batchSize);
      this.conf = readConf.copy();
      return readConf;
    }
    return conf;
  }

  /** 创建批次迭代器。 */
  @Override
  public CloseableIterator<T> iterator() {
    FileIterator<T> iter = new FileIterator<>(init());
    addCloseable(iter);
    return iter;
  }

  /**
   * 向量化文件迭代器：逐行组读取，每次 next() 返回一个批次。
   *
   * <p>设计要点：advance() 跳过过滤行组并设置行组信息（含列元数据）； next() 计算本批次可读记录数（不超过行组剩余和 batchSize），通过 model.read
   * 批量读取。
   */
  private static class FileIterator<T> implements CloseableIterator<T> {
    private final ParquetFileReader reader;
    private final boolean[] shouldSkip;
    private final VectorizedReader<T> model;
    private final long totalValues;
    private final int batchSize;
    private final List<Map<ColumnPath, ColumnChunkMetaData>> columnChunkMetadata;
    private final boolean reuseContainers;
    private int nextRowGroup = 0;
    private long nextRowGroupStart = 0;
    private long valuesRead = 0;
    private T last = null;
    private final long[] rowGroupsStartRowPos;

    FileIterator(ReadConf conf) {
      this.reader = conf.reader();
      this.shouldSkip = conf.shouldSkip();
      this.totalValues = conf.totalValues();
      this.reuseContainers = conf.reuseContainers();
      this.model = conf.vectorizedModel();
      this.batchSize = conf.batchSize();
      this.model.setBatchSize(this.batchSize);
      this.columnChunkMetadata = conf.columnChunkMetadataForRowGroups();
      this.rowGroupsStartRowPos = conf.startRowPositions();
    }

    @Override
    public boolean hasNext() {
      return valuesRead < totalValues;
    }

    /**
     * 读取下一批记录。
     *
     * <p>逻辑：若当前行组已读完则 advance()；计算本批次记录数（min(行组剩余, batchSize)）； 通过 model.read 批量读取。
     *
     * @return 批次数据
     * @throws NoSuchElementException 若无更多数据
     */
    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (valuesRead >= nextRowGroupStart) {
        advance();
      }

      // batchSize is an integer, so casting to integer is safe
      int numValuesToRead = (int) Math.min(nextRowGroupStart - valuesRead, batchSize);
      if (reuseContainers) {
        this.last = model.read(last, numValuesToRead);
      } else {
        this.last = model.read(null, numValuesToRead);
      }
      valuesRead += numValuesToRead;

      return last;
    }

    /**
     * 推进到下一个未跳过的行组，设置页面源和列元数据。
     *
     * <p>逻辑：跳过 shouldSkip 标记的行组，读取下一个行组的 PageReadStore， 调用 model.setRowGroupInfo 传递页面源、列元数据和行起始位置。
     */
    private void advance() {
      while (shouldSkip[nextRowGroup]) {
        nextRowGroup += 1;
        reader.skipNextRowGroup();
      }
      PageReadStore pages;
      try {
        pages = reader.readNextRowGroup();
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }

      long rowPosition = rowGroupsStartRowPos[nextRowGroup];
      model.setRowGroupInfo(pages, columnChunkMetadata.get(nextRowGroup), rowPosition);
      nextRowGroupStart += pages.getRowCount();
      nextRowGroup += 1;
    }

    @Override
    public void close() throws IOException {
      model.close();
      reader.close();
    }
  }
}
