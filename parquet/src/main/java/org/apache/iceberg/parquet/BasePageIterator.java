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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.parquet.bytes.ByteBufferInputStream;
import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.bytes.BytesUtils;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.column.Encoding;
import org.apache.parquet.column.ValuesType;
import org.apache.parquet.column.page.DataPage;
import org.apache.parquet.column.page.DataPageV1;
import org.apache.parquet.column.page.DataPageV2;
import org.apache.parquet.column.values.ValuesReader;
import org.apache.parquet.column.values.rle.RunLengthBitPackingHybridDecoder;
import org.apache.parquet.io.ParquetDecodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Parquet 数据页（DataPage）迭代器基类（抽象）。
 *
 * <p>所属模块：iceberg-parquet（Parquet 列式读取底层基础设施，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>管理单个列在一个数据页内的三元组（value + definition level + repetition level）迭代状态。
 *   <li>解析 DataPageV1/V2 的字节布局，初始化 RL 读取器、DL 读取器和值读取器。
 *   <li>提供 IntIterator 抽象（ValuesReaderIntIterator / RLEIntIterator / NullIntIterator）
 *       统一处理定义级别与重复级别的读取。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法：将 {@code initDataReader} 与 {@code initDefinitionLevelsReader} 下沉到子类 （{@link
 *       PageIterator}），本类只管理公共状态与页面调度。
 *   <li>统一 V1/V2：通过 Visitor 模式分派 DataPageV1/V2 到不同的 initFromPage 实现， 屏蔽两种页面格式的布局差异（V1 的
 *       RL/DL/values 连续存储 vs V2 的 RL/DL 独立存储）。
 *   <li>NullIntIterator 优化：当 maxLevel=0 时（非嵌套列），RL/DL 恒为 0，用空迭代器避免解码开销。
 * </ul>
 *
 * <p>上下游关系：被 {@link PageIterator} 继承；间接被 ParquetValueReaders 的列读取器使用， 负责从 Parquet 数据页中逐值读取。
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class BasePageIterator {
  private static final Logger LOG = LoggerFactory.getLogger(BasePageIterator.class);

  protected final ColumnDescriptor desc;
  protected final String writerVersion;

  // iterator state
  protected boolean hasNext = false;
  protected int triplesRead = 0;
  protected int currentDL = 0;
  protected int currentRL = 0;

  // page bookkeeping
  protected Dictionary dictionary = null;
  protected DataPage page = null;
  protected int triplesCount = 0;
  protected Encoding valueEncoding = null;
  protected IntIterator definitionLevels = null;
  protected IntIterator repetitionLevels = null;
  protected ValuesReader values = null;

  protected BasePageIterator(ColumnDescriptor descriptor, String writerVersion) {
    this.desc = descriptor;
    this.writerVersion = writerVersion;
  }

  /** 重置迭代器状态，准备读取下一个数据页。 */
  protected void reset() {
    this.page = null;
    this.triplesCount = 0;
    this.triplesRead = 0;
    this.repetitionLevels = null;
    this.hasNext = false;
  }

  protected abstract void initDataReader(
      Encoding dataEncoding, ByteBufferInputStream in, int valueCount);

  protected abstract void initDefinitionLevelsReader(
      DataPageV1 dataPageV1, ColumnDescriptor descriptor, ByteBufferInputStream in, int count)
      throws IOException;

  protected abstract void initDefinitionLevelsReader(
      DataPageV2 dataPageV2, ColumnDescriptor descriptor) throws IOException;

  public int currentPageCount() {
    return triplesCount;
  }

  public boolean hasNext() {
    return hasNext;
  }

  /**
   * 设置当前要迭代的数据页，并通过 Visitor 分派到 V1/V2 的初始化逻辑。
   *
   * <p>逻辑：校验 page 非空后，调用 page.accept(Visitor) 分派到 {@link #initFromPage(DataPageV1)} 或 {@link
   * #initFromPage(DataPageV2)}， 完成页面内 RL/DL/值读取器的初始化。之后重置 triplesRead 并设置 hasNext。
   *
   * @param page 要读取的 Parquet 数据页，不可为 null
   */
  public void setPage(DataPage page) {
    Preconditions.checkNotNull(page, "Cannot read from null page");
    this.page = page;
    this.page.accept(
        new DataPage.Visitor<ValuesReader>() {
          @Override
          public ValuesReader visit(DataPageV1 dataPageV1) {
            initFromPage(dataPageV1);
            return null;
          }

          @Override
          public ValuesReader visit(DataPageV2 dataPageV2) {
            initFromPage(dataPageV2);
            return null;
          }
        });
    this.triplesRead = 0;
    this.hasNext = triplesRead < triplesCount;
  }

  /**
   * 初始化 DataPageV1：V1 页面中 RL/DL/值连续存储在同一字节流中。
   *
   * <p>逻辑：依次从字节流中初始化 RL 读取器、DL 读取器、值读取器，每次记录流的 position 用于调试。IOException 包装为
   * ParquetDecodingException。
   *
   * @param initPage DataPageV1 数据页
   */
  protected void initFromPage(DataPageV1 initPage) {
    this.triplesCount = initPage.getValueCount();
    ValuesReader rlReader =
        initPage.getRlEncoding().getValuesReader(desc, ValuesType.REPETITION_LEVEL);
    this.repetitionLevels = new ValuesReaderIntIterator(rlReader);
    try {
      BytesInput bytes = initPage.getBytes();
      LOG.debug("page size {} bytes and {} records", bytes.size(), triplesCount);
      LOG.debug("reading repetition levels at 0");
      ByteBufferInputStream in = bytes.toInputStream();
      rlReader.initFromPage(triplesCount, in);
      LOG.debug("reading definition levels at {}", in.position());
      initDefinitionLevelsReader(initPage, desc, in, triplesCount);
      LOG.debug("reading data at {}", in.position());
      initDataReader(initPage.getValueEncoding(), in, initPage.getValueCount());
    } catch (IOException e) {
      throw new ParquetDecodingException("could not read page " + initPage + " in col " + desc, e);
    }
  }

  /**
   * 初始化 DataPageV2：V2 页面中 RL/DL 独立存储，值部分单独存放。
   *
   * <p>逻辑：RL 直接从 initPage.getRepetitionLevels() 通过 RLE 解码； DL 委托给 initDefinitionLevelsReader；值从
   * getData() 流中初始化。
   *
   * @param initPage DataPageV2 数据页
   */
  protected void initFromPage(DataPageV2 initPage) {
    this.triplesCount = initPage.getValueCount();
    this.repetitionLevels =
        newRLEIterator(desc.getMaxRepetitionLevel(), initPage.getRepetitionLevels());
    try {
      initDefinitionLevelsReader(initPage, desc);
      LOG.debug("page data size {} bytes and {} records", initPage.getData().size(), triplesCount);
      initDataReader(initPage.getDataEncoding(), initPage.getData().toInputStream(), triplesCount);
    } catch (IOException e) {
      throw new ParquetDecodingException("could not read page " + initPage + " in col " + desc, e);
    }
  }

  /** 设置字典（用于字典编码列的解码）。 */
  public void setDictionary(Dictionary dict) {
    this.dictionary = dict;
  }

  /** 整数迭代器抽象：统一 RL/DL 的读取接口。 */
  protected abstract static class IntIterator {
    abstract int nextInt();
  }

  /** 基于 Parquet {@link ValuesReader} 的整数迭代器实现（用于 V1 页面的 RL/DL 读取）。 */
  static class ValuesReaderIntIterator extends IntIterator {
    private final ValuesReader delegate;

    ValuesReaderIntIterator(ValuesReader delegate) {
      this.delegate = delegate;
    }

    @Override
    int nextInt() {
      return delegate.readInteger();
    }
  }

  /**
   * 创建 RLE（Run-Length Encoding）整数迭代器，用于解码 V2 页面的 RL/DL。
   *
   * <p>逻辑：若 maxLevel==0（非嵌套列），返回 NullIntIterator（值恒为 0）以省去解码； 否则用 {@link
   * RunLengthBitPackingHybridDecoder} 包装字节流。
   *
   * @param maxLevel 最大级别（决定位宽）
   * @param bytes RL/DL 编码字节
   * @return IntIterator 实例
   */
  IntIterator newRLEIterator(int maxLevel, BytesInput bytes) {
    try {
      if (maxLevel == 0) {
        return new NullIntIterator();
      }
      return new RLEIntIterator(
          new RunLengthBitPackingHybridDecoder(
              BytesUtils.getWidthFromMaxInt(maxLevel), bytes.toInputStream()));
    } catch (IOException e) {
      throw new ParquetDecodingException("could not read levels in page for col " + desc, e);
    }
  }

  /** 基于 RLE 混合解码器的整数迭代器实现（用于 V2 页面的 RL/DL 读取）。 */
  static class RLEIntIterator extends IntIterator {
    private final RunLengthBitPackingHybridDecoder delegate;

    RLEIntIterator(RunLengthBitPackingHybridDecoder delegate) {
      this.delegate = delegate;
    }

    @Override
    int nextInt() {
      try {
        return delegate.readInt();
      } catch (IOException e) {
        throw new ParquetDecodingException(e);
      }
    }
  }

  /** 空迭代器：maxLevel=0 时使用，nextInt() 恒返回 0。 */
  static final class NullIntIterator extends IntIterator {
    @Override
    int nextInt() {
      return 0;
    }
  }
}
