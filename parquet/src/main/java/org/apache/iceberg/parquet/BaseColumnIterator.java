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

import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.column.page.DataPage;
import org.apache.parquet.column.page.PageReader;

/**
 * 文件级说明：Parquet 列迭代器抽象基类，按 row group 维度管理列数据的分页推进。
 *
 * <p>所属模块：iceberg-parquet（Parquet 列式读取底座，被上层 ValueReader/ColumnIterator 复用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一个 {@link ColumnDescriptor} 与对应的 {@link PageReader}，管理一个 row group 内某列的读取状态。
 *   <li>记录该 row group 的三元组（value+def+rep）总数、已读数量、当前页边界，并按需推进 到下一个 {@link DataPage}。
 *   <li>读取并下发字典（Dictionary），用于字典编码列的解码。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>状态按 row group 重置：每次 {@link #setPageSource(PageReader)} 都清零计数并重置页迭代器， 保证同一迭代器可被复用于多个 row
 *       group。
 *   <li>懒推进：{@link #advance()} 仅在已读三元组数到达当前页边界时才尝试加载下一页， 避免无谓预读。
 *   <li>暴露字段（{@code protected} 字段）供子类直接访问，简化迭代器的实现开销。
 * </ul>
 *
 * <p>上下游关系：依赖 parquet-mr 的 {@link PageReader}；被 {@link ColumnIterator} 与 {@link
 * ValuesAsBytesReader} 等子类继承；上层由 {@link ParquetValueReader} 使用。
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class BaseColumnIterator {
  /** 当前列的 schema 描述符，含路径、物理类型等。 */
  protected final ColumnDescriptor desc;

  // 以下状态在每次 setPageSource 时按 row group 重置
  /** 当前 row group 的页数据源。 */
  protected PageReader pageSource = null;
  /** 当前 row group 的三元组总数。 */
  protected long triplesCount = 0L;
  /** 当前 row group 已读三元组数。 */
  protected long triplesRead = 0L;
  /** 当前已加载页的累计三元组边界，达到即触发 advance 到下一页。 */
  protected long advanceNextPageCount = 0L;
  /** 字典编码列的字典，非字典列为 null。 */
  protected Dictionary dictionary;

  /**
   * 构造列迭代器。
   *
   * @param descriptor 列描述符
   */
  protected BaseColumnIterator(ColumnDescriptor descriptor) {
    this.desc = descriptor;
  }

  /**
   * 为新 row group 设置页数据源并重置内部状态。
   *
   * <p>逻辑：记录页源与三元组总数，重置已读计数；重置页迭代器并读取字典下发； 最后调用 {@link #advance()} 预加载第一页数据。
   *
   * @param source 当前 row group 该列的页读取器
   */
  public void setPageSource(PageReader source) {
    this.pageSource = source;
    this.triplesCount = source.getTotalValueCount();
    this.triplesRead = 0L;
    this.advanceNextPageCount = 0L;
    BasePageIterator pageIterator = pageIterator();
    pageIterator.reset();
    dictionary = ParquetUtil.readDictionary(desc, pageSource);
    pageIterator.setDictionary(dictionary);
    advance();
  }

  /**
   * 子类提供具体的页迭代器实例，用于解码单页数据。
   *
   * @return 页迭代器
   */
  protected abstract BasePageIterator pageIterator();

  /**
   * 在已读三元组数到达当前页边界时，从 {@link #pageSource} 读取并加载下一个数据页。
   *
   * <p>逻辑：若 {@code triplesRead >= advanceNextPageCount}，循环调用 {@link
   * PageReader#readPage()}，把页交给页迭代器并累加其三元组计数到边界； 读不到更多页（返回 null）则直接返回。
   */
  protected void advance() {
    if (triplesRead >= advanceNextPageCount) {
      BasePageIterator pageIterator = pageIterator();
      while (!pageIterator.hasNext()) {
        DataPage page = pageSource.readPage();
        if (page != null) {
          pageIterator.setPage(page);
          this.advanceNextPageCount += pageIterator.currentPageCount();
        } else {
          return;
        }
      }
    }
  }

  /**
   * 判断当前 row group 是否还有未读三元组。
   *
   * @return true 表示仍有数据可读
   */
  public boolean hasNext() {
    return triplesRead < triplesCount;
  }
}
