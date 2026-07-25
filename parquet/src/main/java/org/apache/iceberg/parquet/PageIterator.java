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
import org.apache.parquet.CorruptDeltaByteArrays;
import org.apache.parquet.bytes.ByteBufferInputStream;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Encoding;
import org.apache.parquet.column.ValuesType;
import org.apache.parquet.column.page.DataPage;
import org.apache.parquet.column.page.DataPageV1;
import org.apache.parquet.column.page.DataPageV2;
import org.apache.parquet.column.values.RequiresPreviousReader;
import org.apache.parquet.column.values.ValuesReader;
import org.apache.parquet.io.ParquetDecodingException;
import org.apache.parquet.io.api.Binary;

/**
 * 文件级说明：Parquet 数据页值迭代器，实现 {@link TripleIterator}。
 *
 * <p>所属模块：iceberg-parquet（Parquet 列式读取底层基础设施）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link BasePageIterator}，在页内三元组迭代基础上提供类型化的值读取
 *       （nextBoolean/Integer/Long/Float/Double/Binary）。
 *   <li>实现 initDataReader / initDefinitionLevelsReader，处理字典编码、RLE 解码、
 *       CorruptDeltaByteArrays（PARQUET-246）等兼容性问题。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>工厂 + 匿名子类：{@link #newIndex} 按 PrimitiveTypeName 创建特化迭代器。
 *   <li>advance() 前置：每次读取值前先推进 DL/RL，确保 currentDefinitionLevel/currentRepetitionLevel 返回的是当前值对应的级别。
 *   <li>PARQUET-246 兼容：CorruptDeltaByteArrays 场景下需要顺序读取，通过 setPreviousReader 传递前一个读取器以修复损坏数据。
 * </ul>
 *
 * <p>上下游关系：继承 BasePageIterator，被 ParquetValueReaders 的列读取器使用。
 *
 * @param <T> 读取的值类型
 */
abstract class PageIterator<T> extends BasePageIterator implements TripleIterator<T> {
  /**
   * 工厂方法：按 Parquet 原始类型创建对应的 PageIterator。
   *
   * <p>逻辑：根据 PrimitiveTypeName（BOOLEAN/INT32/INT64/INT96/FLOAT/DOUBLE/BINARY 等） 创建匿名子类，将泛型 next()
   * 路由到 nextBoolean/Integer/Long/Float/Double/Binary。
   *
   * @param desc Parquet 列描述符
   * @param writerVersion Parquet writer 版本（用于兼容性处理）
   * @param <T> 值类型
   * @return 类型匹配的 PageIterator
   * @throws UnsupportedOperationException 若类型不受支持
   */
  @SuppressWarnings("unchecked")
  static <T> PageIterator<T> newIterator(ColumnDescriptor desc, String writerVersion) {
    switch (desc.getPrimitiveType().getPrimitiveTypeName()) {
      case BOOLEAN:
        return (PageIterator<T>)
            new PageIterator<Boolean>(desc, writerVersion) {
              @Override
              public Boolean next() {
                return nextBoolean();
              }
            };
      case INT32:
        return (PageIterator<T>)
            new PageIterator<Integer>(desc, writerVersion) {
              @Override
              public Integer next() {
                return nextInteger();
              }
            };
      case INT64:
        return (PageIterator<T>)
            new PageIterator<Long>(desc, writerVersion) {
              @Override
              public Long next() {
                return nextLong();
              }
            };
      case INT96:
        return (PageIterator<T>)
            new PageIterator<Binary>(desc, writerVersion) {
              @Override
              public Binary next() {
                return nextBinary();
              }
            };
      case FLOAT:
        return (PageIterator<T>)
            new PageIterator<Float>(desc, writerVersion) {
              @Override
              public Float next() {
                return nextFloat();
              }
            };
      case DOUBLE:
        return (PageIterator<T>)
            new PageIterator<Double>(desc, writerVersion) {
              @Override
              public Double next() {
                return nextDouble();
              }
            };
      case FIXED_LEN_BYTE_ARRAY:
      case BINARY:
        return (PageIterator<T>)
            new PageIterator<Binary>(desc, writerVersion) {
              @Override
              public Binary next() {
                return nextBinary();
              }
            };
      default:
        throw new UnsupportedOperationException(
            "Unsupported primitive type: " + desc.getPrimitiveType().getPrimitiveTypeName());
    }
  }

  private PageIterator(ColumnDescriptor desc, String writerVersion) {
    super(desc, writerVersion);
  }

  /**
   * 设置当前数据页：调用父类初始化后，立即 advance() 推进到第一个值。
   *
   * @param page 要读取的数据页
   */
  @Override
  public void setPage(DataPage page) {
    super.setPage(page);
    advance();
  }

  @Override
  public int currentDefinitionLevel() {
    Preconditions.checkArgument(currentDL >= 0, "Should not read definition, past page end");
    return currentDL;
  }

  @Override
  public int currentRepetitionLevel() {
    //    Preconditions.checkArgument(currentDL >= 0, "Should not read repetition, past page end");
    return currentRL;
  }

  @Override
  public boolean nextBoolean() {
    advance();
    try {
      return values.readBoolean();
    } catch (RuntimeException e) {
      throw handleRuntimeException(e);
    }
  }

  @Override
  public int nextInteger() {
    advance();
    try {
      return values.readInteger();
    } catch (RuntimeException e) {
      throw handleRuntimeException(e);
    }
  }

  @Override
  public long nextLong() {
    advance();
    try {
      return values.readLong();
    } catch (RuntimeException e) {
      throw handleRuntimeException(e);
    }
  }

  @Override
  public float nextFloat() {
    advance();
    try {
      return values.readFloat();
    } catch (RuntimeException e) {
      throw handleRuntimeException(e);
    }
  }

  @Override
  public double nextDouble() {
    advance();
    try {
      return values.readDouble();
    } catch (RuntimeException e) {
      throw handleRuntimeException(e);
    }
  }

  @Override
  public Binary nextBinary() {
    advance();
    try {
      return values.readBytes();
    } catch (RuntimeException e) {
      throw handleRuntimeException(e);
    }
  }

  /** 读取 null 值：推进迭代器后返回 null（Parquet 值流中不存储 null）。 */
  @Override
  public <V> V nextNull() {
    advance();
    // values do not contain nulls
    return null;
  }

  /**
   * 推进到下一个三元组：读取下一个 DL 和 RL，更新迭代状态。
   *
   * <p>逻辑：若仍有未读三元组（triplesRead < triplesCount），读取下一个 DL/RL 并递增计数器， 置 hasNext=true；否则置
   * DL/RL=-1、hasNext=false，标记页结束。
   */
  private void advance() {
    if (triplesRead < triplesCount) {
      this.currentDL = definitionLevels.nextInt();
      this.currentRL = repetitionLevels.nextInt();
      this.triplesRead += 1;
      this.hasNext = true;
    } else {
      this.currentDL = -1;
      this.currentRL = -1;
      this.hasNext = false;
    }
  }

  /**
   * 处理值读取时的运行时异常，包装为带上下文信息的 ParquetDecodingException。
   *
   * <p>逻辑：若 writer 版本和编码可能触发 PARQUET-246（CorruptDeltaByteArrays）， 在异常信息中提示设置
   * parquet.split.files=false；所有异常都附带列名、值位置、DL/RL 等上下文。
   *
   * @param exception 原始运行时异常
   * @return 包装后的 ParquetDecodingException
   */
  RuntimeException handleRuntimeException(RuntimeException exception) {
    if (CorruptDeltaByteArrays.requiresSequentialReads(writerVersion, valueEncoding)
        && exception instanceof ArrayIndexOutOfBoundsException) {
      // this is probably PARQUET-246, which may happen if reading data with
      // MR because this can't be detected without reading all footers
      throw new ParquetDecodingException(
          "Read failure possibly due to " + "PARQUET-246: try setting parquet.split.files to false",
          new ParquetDecodingException(
              String.format(
                  "Can't read value in column %s at value %d out of %d in current page. "
                      + "repetition level: %d, definition level: %d",
                  desc, triplesRead, triplesCount, currentRL, currentDL),
              exception));
    }
    throw new ParquetDecodingException(
        String.format(
            "Can't read value in column %s at value %d out of %d in current page. "
                + "repetition level: %d, definition level: %d",
            desc, triplesRead, triplesCount, currentRL, currentDL),
        exception);
  }

  /**
   * 初始化值读取器。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若编码使用字典，需确保 dictionary 已设置，通过 getDictionaryBasedValuesReader 创建读取器；
   *   <li>否则通过 getValuesReader 创建普通读取器；
   *   <li>调用 initFromPage 初始化读取器；
   *   <li>若为 CorruptDeltaByteArrays 场景且前一个读取器实现了 RequiresPreviousReader， 设置 previousReader
   *       以支持顺序读取修复。
   * </ol>
   *
   * @param dataEncoding 数据编码方式
   * @param in 数据字节流
   * @param valueCount 值数量
   */
  @Override
  protected void initDataReader(Encoding dataEncoding, ByteBufferInputStream in, int valueCount) {
    ValuesReader previousReader = values;

    this.valueEncoding = dataEncoding;

    // TODO: May want to change this so that this class is not dictionary-aware.
    // For dictionary columns, this class could rely on wrappers to correctly handle dictionaries
    // This isn't currently possible because RLE must be read by getDictionaryBasedValuesReader
    if (dataEncoding.usesDictionary()) {
      if (dictionary == null) {
        throw new ParquetDecodingException(
            "could not read page in col "
                + desc
                + " as the dictionary was missing for encoding "
                + dataEncoding);
      }
      this.values =
          dataEncoding.getDictionaryBasedValuesReader(desc, ValuesType.VALUES, dictionary);
    } else {
      this.values = dataEncoding.getValuesReader(desc, ValuesType.VALUES);
    }

    //    if (dataEncoding.usesDictionary() && converter.hasDictionarySupport()) {
    //      bindToDictionary(dictionary);
    //    } else {
    //      bind(path.getType());
    //    }

    try {
      values.initFromPage(valueCount, in);
    } catch (IOException e) {
      throw new ParquetDecodingException("could not read page in col " + desc, e);
    }

    if (CorruptDeltaByteArrays.requiresSequentialReads(writerVersion, dataEncoding)
        && previousReader instanceof RequiresPreviousReader) {
      // previous reader can only be set if reading sequentially
      ((RequiresPreviousReader) values).setPreviousReader(previousReader);
    }
  }

  /**
   * 初始化 V1 页面的定义级别读取器：从字节流中读取 DL 编码。
   *
   * @param dataPageV1 DataPageV1 页面
   * @param desc 列描述符
   * @param in 字节流（RL 读取后剩余部分）
   * @param triplesCount 三元组总数
   * @throws IOException 若读取失败
   */
  @Override
  protected void initDefinitionLevelsReader(
      DataPageV1 dataPageV1, ColumnDescriptor desc, ByteBufferInputStream in, int triplesCount)
      throws IOException {
    ValuesReader dlReader =
        dataPageV1.getDlEncoding().getValuesReader(desc, ValuesType.DEFINITION_LEVEL);
    this.definitionLevels = new ValuesReaderIntIterator(dlReader);
    dlReader.initFromPage(triplesCount, in);
  }

  /** 初始化 V2 页面的定义级别读取器：通过 RLE 解码独立存储的 DL 字节。 */
  @Override
  protected void initDefinitionLevelsReader(DataPageV2 dataPageV2, ColumnDescriptor desc) {
    this.definitionLevels =
        newRLEIterator(desc.getMaxDefinitionLevel(), dataPageV2.getDefinitionLevels());
  }
}
