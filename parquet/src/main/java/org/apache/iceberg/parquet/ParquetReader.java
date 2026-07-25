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
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：Iceberg Parquet 文件读取器，实现 {@link CloseableIterable}。
 *
 * <p>所属模块：iceberg-parquet（核心读取入口，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按期望 schema 读取 Parquet 文件，支持列投影、过滤下推、NameMapping。
 *   <li>逐行组（Row Group）读取数据，通过 ParquetValueReader 将列式数据转为行式记录。
 *   <li>支持行组级跳过（shouldSkip）和容器复用（reuseContainers）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>ReadConf 延迟初始化：init() 首次调用时创建 ReadConf 并保存副本，避免重复初始化。
 *   <li>FileIterator 逐行组读取：advance() 跳过被过滤的行组，按需读取下一个行组的页面数据， 通过 model.setPageSource 设置页面源后逐行读取。
 *   <li>alwaysTrue 优化：将 alwaysTrue 过滤表达式替换为 null，避免无谓的谓词求值。
 * </ul>
 *
 * <p>上下游关系：被各引擎的 Parquet 读取入口（如 GenericParquetReaders）调用； 依赖 ParquetFileReader（Parquet
 * 文件读取）、ParquetValueReader（值读取器）。
 *
 * @param <T> 读取的记录类型
 */
public class ParquetReader<T> extends CloseableGroup implements CloseableIterable<T> {
  private final InputFile input;
  private final Schema expectedSchema;
  private final ParquetReadOptions options;
  private final Function<MessageType, ParquetValueReader<?>> readerFunc;
  private final Expression filter;
  private final boolean reuseContainers;
  private final boolean caseSensitive;
  private final NameMapping nameMapping;

  /**
   * 构造 Parquet 读取器。
   *
   * @param input 输入文件
   * @param expectedSchema 期望读取的 Iceberg schema（支持投影）
   * @param options Parquet 读取选项
   * @param readerFunc 根据 Parquet MessageType 构造读取器的函数
   * @param nameMapping 字段名→ID 映射（用于无 ID 文件）
   * @param filter 过滤表达式（alwaysTrue 会被替换为 null）
   * @param reuseContainers 是否复用容器对象以减少 GC
   * @param caseSensitive 字段名匹配是否大小写敏感
   */
  public ParquetReader(
      InputFile input,
      Schema expectedSchema,
      ParquetReadOptions options,
      Function<MessageType, ParquetValueReader<?>> readerFunc,
      NameMapping nameMapping,
      Expression filter,
      boolean reuseContainers,
      boolean caseSensitive) {
    this.input = input;
    this.expectedSchema = expectedSchema;
    this.options = options;
    this.readerFunc = readerFunc;
    // replace alwaysTrue with null to avoid extra work evaluating a trivial filter
    this.filter = filter == Expressions.alwaysTrue() ? null : filter;
    this.reuseContainers = reuseContainers;
    this.caseSensitive = caseSensitive;
    this.nameMapping = nameMapping;
  }

  private ReadConf<T> conf = null;

  /**
   * 延迟初始化读取配置：首次调用时创建 ReadConf 并缓存。
   *
   * <p>逻辑：若 conf 为 null，创建 ReadConf 并保存副本（copy），后续直接返回缓存的 conf。
   *
   * @return 读取配置
   */
  private ReadConf<T> init() {
    if (conf == null) {
      ReadConf<T> readConf =
          new ReadConf<>(
              input,
              options,
              expectedSchema,
              filter,
              readerFunc,
              null,
              nameMapping,
              reuseContainers,
              caseSensitive,
              null);
      this.conf = readConf.copy();
      return readConf;
    }
    return conf;
  }

  /**
   * 创建行迭代器：初始化 ReadConf 后构造 FileIterator，并加入 CloseableGroup 管理。
   *
   * @return 行迭代器
   */
  @Override
  public CloseableIterator<T> iterator() {
    FileIterator<T> iter = new FileIterator<>(init());
    addCloseable(iter);
    return iter;
  }

  /**
   * 文件级行迭代器：逐行组读取 Parquet 数据并逐行产出记录。
   *
   * <p>设计要点：shouldSkip 数组标记需跳过的行组；advance() 跳过过滤行组并读取下一行组页面； next() 通过 model.read() 将列式数据转为行记录。
   */
  private static class FileIterator<T> implements CloseableIterator<T> {
    private final ParquetFileReader reader;
    private final boolean[] shouldSkip;
    private final ParquetValueReader<T> model;
    private final long totalValues;
    private final boolean reuseContainers;
    private final long[] rowGroupsStartRowPos;

    private int nextRowGroup = 0;
    private long nextRowGroupStart = 0;
    private long valuesRead = 0;
    private T last = null;

    FileIterator(ReadConf<T> conf) {
      this.reader = conf.reader();
      this.shouldSkip = conf.shouldSkip();
      this.model = conf.model();
      this.totalValues = conf.totalValues();
      this.reuseContainers = conf.reuseContainers();
      this.rowGroupsStartRowPos = conf.startRowPositions();
    }

    @Override
    public boolean hasNext() {
      return valuesRead < totalValues;
    }

    /**
     * 读取下一条记录。
     *
     * <p>逻辑：若当前行组已读完（valuesRead >= nextRowGroupStart），调用 advance() 读取下一行组； 然后通过 model.read()
     * 读取一行，根据 reuseContainers 决定是否复用容器。
     *
     * @return 下一条记录
     */
    @Override
    public T next() {
      if (valuesRead >= nextRowGroupStart) {
        advance();
      }

      if (reuseContainers) {
        this.last = model.read(last);
      } else {
        this.last = model.read(null);
      }
      valuesRead += 1;

      return last;
    }

    /**
     * 推进到下一个未跳过的行组并读取其页面数据。
     *
     * <p>逻辑：循环跳过 shouldSkip 标记的行组，读取下一个行组的 PageReadStore， 记录行起始位置，并通过 model.setPageSource 设置页面源。
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
      nextRowGroupStart += pages.getRowCount();
      nextRowGroup += 1;

      model.setPageSource(pages, rowPosition);
    }

    @Override
    public void close() throws IOException {
      reader.close();
    }
  }
}
