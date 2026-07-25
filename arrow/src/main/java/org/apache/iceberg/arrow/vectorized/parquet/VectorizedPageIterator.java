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
package org.apache.iceberg.arrow.vectorized.parquet;

import java.io.IOException;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.iceberg.arrow.vectorized.NullabilityHolder;
import org.apache.iceberg.parquet.BasePageIterator;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.parquet.ValuesAsBytesReader;
import org.apache.parquet.CorruptDeltaByteArrays;
import org.apache.parquet.bytes.ByteBufferInputStream;
import org.apache.parquet.bytes.BytesUtils;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Encoding;
import org.apache.parquet.column.page.DataPageV1;
import org.apache.parquet.column.page.DataPageV2;
import org.apache.parquet.column.values.RequiresPreviousReader;
import org.apache.parquet.column.values.ValuesReader;
import org.apache.parquet.io.ParquetDecodingException;

/**
 * 文件级说明：Parquet 数据页的向量化迭代器，按批次解码页内值并写入 Arrow 向量。
 *
 * <p>所属模块：iceberg-arrow 的 parquet 子包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>初始化数据读取器与 definition level 读取器
 *   <li>按字典解码模式（NONE/LAZY/EAGER）分发普通或字典编码批量读取； LAZY 产出字典编码向量，EAGER 提前解码为实际值
 * </ul>
 *
 * <p>设计意图：继承 {@link BasePageIterator} 复用页遍历公共逻辑，通过内部类为每种 基本类型提供专用读取器，按编码模式选择批量读取路径以提升吞吐。
 *
 * <p>上下游关系：被 {@code VectorizedColumnIterator} 调用。
 */
public class VectorizedPageIterator extends BasePageIterator {
  private final boolean setArrowValidityVector;

  /**
   * 构造页迭代器。
   *
   * @param desc 列描述符
   * @param writerVersion 写入器版本
   * @param setValidityVector 是否设置 Arrow 有效性向量
   */
  public VectorizedPageIterator(
      ColumnDescriptor desc, String writerVersion, boolean setValidityVector) {
    super(desc, writerVersion);
    this.setArrowValidityVector = setValidityVector;
  }

  private ValuesAsBytesReader plainValuesReader = null;
  private VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader = null;
  private boolean allPagesDictEncoded;
  private VectorizedParquetDefinitionLevelReader vectorizedDefinitionLevelReader;

  private enum DictionaryDecodeMode {
    NONE, // plain encoding
    LAZY,
    EAGER
  }

  private DictionaryDecodeMode dictionaryDecodeMode;

  /** 设置行组内是否全部字典编码（影响 LAZY/EAGER 模式选择）。 */
  public void setAllPagesDictEncoded(boolean allDictEncoded) {
    this.allPagesDictEncoded = allDictEncoded;
  }

  /** 重置页迭代器状态，清空普通读取器与 definition level 读取器。 */
  @Override
  protected void reset() {
    super.reset();
    this.plainValuesReader = null;
    this.vectorizedDefinitionLevelReader = null;
  }

  /**
   * 根据数据编码初始化值读取器并确定字典解码模式。
   *
   * <p>逻辑：字典编码时创建字典解码读取器，int 类型或非全字典编码选 EAGER（提前解码）， 否则选 LAZY（产出字典编码向量）；普通编码仅支持 PLAIN；并处理损坏的
   * DeltaByteArray 顺序读兼容。
   *
   * @param dataEncoding 数据编码
   * @param in 数据输入流
   * @param valueCount 值数
   * @throws ParquetDecodingException 字典缺失或读取失败
   * @throws UnsupportedOperationException 非字典且非 PLAIN 编码
   */
  @Override
  protected void initDataReader(Encoding dataEncoding, ByteBufferInputStream in, int valueCount) {
    ValuesReader previousReader = plainValuesReader;
    if (dataEncoding.usesDictionary()) {
      if (dictionary == null) {
        throw new ParquetDecodingException(
            "could not read page in col "
                + desc
                + " as the dictionary was missing for encoding "
                + dataEncoding);
      }
      try {
        dictionaryEncodedValuesReader =
            new VectorizedDictionaryEncodedParquetValuesReader(
                desc.getMaxDefinitionLevel(), setArrowValidityVector);
        dictionaryEncodedValuesReader.initFromPage(valueCount, in);
        if (ParquetUtil.isIntType(desc.getPrimitiveType()) || !allPagesDictEncoded) {
          dictionaryDecodeMode = DictionaryDecodeMode.EAGER;
        } else {
          dictionaryDecodeMode = DictionaryDecodeMode.LAZY;
        }
      } catch (IOException e) {
        throw new ParquetDecodingException("could not read page in col " + desc, e);
      }
    } else {
      if (dataEncoding != Encoding.PLAIN) {
        throw new UnsupportedOperationException(
            "Cannot support vectorized reads for column "
                + desc
                + " with "
                + "encoding "
                + dataEncoding
                + ". Disable vectorized reads to read this table/file");
      }
      plainValuesReader = new ValuesAsBytesReader();
      plainValuesReader.initFromPage(valueCount, in);
      dictionaryDecodeMode = DictionaryDecodeMode.NONE;
    }
    if (CorruptDeltaByteArrays.requiresSequentialReads(writerVersion, dataEncoding)
        && previousReader instanceof RequiresPreviousReader) {
      // previous reader can only be set if reading sequentially
      ((RequiresPreviousReader) plainValuesReader).setPreviousReader(previousReader);
    }
  }

  /** 是否产出字典编码向量（LAZY 模式）。 */
  public boolean producesDictionaryEncodedVector() {
    return dictionaryDecodeMode == DictionaryDecodeMode.LAZY;
  }

  /**
   * 从 DataPageV1 初始化 definition level 读取器。
   *
   * <p>逻辑：按最大定义级别计算位宽，创建 VectorizedParquetDefinitionLevelReader 并从页初始化。
   *
   * @param dataPageV1 数据页 V1
   * @param desc 列描述符
   * @param in 输入流
   * @param triplesCount 三元组数
   * @throws IOException 读取异常
   */
  @Override
  protected void initDefinitionLevelsReader(
      DataPageV1 dataPageV1, ColumnDescriptor desc, ByteBufferInputStream in, int triplesCount)
      throws IOException {
    int bitWidth = BytesUtils.getWidthFromMaxInt(desc.getMaxDefinitionLevel());
    this.vectorizedDefinitionLevelReader =
        new VectorizedParquetDefinitionLevelReader(
            bitWidth, desc.getMaxDefinitionLevel(), setArrowValidityVector);
    this.vectorizedDefinitionLevelReader.initFromPage(triplesCount, in);
  }

  /**
   * 从 DataPageV2 初始化 definition level 读取器（不读长度前缀，V2 自行划分页字节）。
   *
   * @param dataPageV2 数据页 V2
   * @param desc 列描述符
   * @throws IOException 读取异常
   */
  @Override
  protected void initDefinitionLevelsReader(DataPageV2 dataPageV2, ColumnDescriptor desc)
      throws IOException {
    int bitWidth = BytesUtils.getWidthFromMaxInt(desc.getMaxDefinitionLevel());
    // do not read the length from the stream. v2 pages handle dividing the page bytes.
    this.vectorizedDefinitionLevelReader =
        new VectorizedParquetDefinitionLevelReader(
            bitWidth, desc.getMaxDefinitionLevel(), false, setArrowValidityVector);
    this.vectorizedDefinitionLevelReader.initFromPage(
        dataPageV2.getValueCount(), dataPageV2.getDefinitionLevels().toInputStream());
  }

  /**
   * 读取一批字典 id（字典 id 与 definition level 一样以 RLE/位打包编码）。
   *
   * @param vector 存储 id 的 IntVector
   * @param expectedBatchSize 期望批大小
   * @param numValsInVector 向量已有值数
   * @param holder 空值持有者
   * @return 实际读取的行数
   */
  public int nextBatchDictionaryIds(
      final IntVector vector,
      final int expectedBatchSize,
      final int numValsInVector,
      NullabilityHolder holder) {
    final int actualBatchSize = getActualBatchSize(expectedBatchSize);
    if (actualBatchSize <= 0) {
      return 0;
    }
    vectorizedDefinitionLevelReader
        .dictionaryIdReader()
        .nextDictEncodedBatch(
            vector,
            numValsInVector,
            -1,
            actualBatchSize,
            holder,
            dictionaryEncodedValuesReader,
            null);
    triplesRead += actualBatchSize;
    this.hasNext = triplesRead < triplesCount;
    return actualBatchSize;
  }

  /** 页读取骨架：按字典解码模式分发普通或字典编码批量读取。 */
  abstract class BagePageReader {
    /**
     * 读取一批值写入向量。
     *
     * <p>逻辑：计算实际批大小；EAGER 模式调用 nextDictEncodedVal 提前解码，否则调用 nextVal； 更新 triplesRead 与 hasNext。
     *
     * @param vector 目标向量
     * @param expectedBatchSize 期望批大小
     * @param numValsInVector 向量已有值数
     * @param typeWidth 类型宽度
     * @param holder 空值持有者
     * @return 实际读取行数
     */
    public int nextBatch(
        FieldVector vector,
        int expectedBatchSize,
        int numValsInVector,
        int typeWidth,
        NullabilityHolder holder) {
      final int actualBatchSize = getActualBatchSize(expectedBatchSize);
      if (actualBatchSize <= 0) {
        return 0;
      }
      if (dictionaryDecodeMode == DictionaryDecodeMode.EAGER) {
        nextDictEncodedVal(vector, actualBatchSize, numValsInVector, typeWidth, holder);
      } else {
        nextVal(vector, actualBatchSize, numValsInVector, typeWidth, holder);
      }
      triplesRead += actualBatchSize;
      hasNext = triplesRead < triplesCount;
      return actualBatchSize;
    }

    /**
     * 由子类实现：普通编码批量写入值。
     *
     * @param vector 目标向量
     * @param batchSize 批大小
     * @param numVals 起始值偏移
     * @param typeWidth 类型宽度
     * @param holder 空值持有者
     */
    protected abstract void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder);

    /**
     * 由子类实现：字典编码批量解码写入值。
     *
     * @param vector 目标向量
     * @param batchSize 批大小
     * @param numVals 起始值偏移
     * @param typeWidth 类型宽度
     * @param holder 空值持有者
     */
    protected abstract void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder);
  }

  /** INT32 类型数据页读取器。 */
  class IntPageReader extends BagePageReader {
    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .integerReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .integerReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /** INT64 类型数据页读取器。 */
  class LongPageReader extends BagePageReader {

    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .longReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .longReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /** TIMESTAMP_MILLIS 类型数据页读取器。Iceberg 中时间戳统一以微秒表示， 因此写入向量前将毫秒值乘以 1000。 */
  class TimestampMillisPageReader extends BagePageReader {

    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .timestampMillisReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .timestampMillisReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /** TimestampInt96（旧版 Parquet 时间戳）类型数据页读取器。 */
  class TimestampInt96PageReader extends BagePageReader {
    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .timestampInt96Reader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .timestampInt96Reader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /** FLOAT 类型数据页读取器。 */
  class FloatPageReader extends BagePageReader {

    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .floatReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .floatReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /** DOUBLE 类型数据页读取器。 */
  class DoublePageReader extends BagePageReader {

    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .doubleReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .doubleReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /**
   * 计算实际批大小：取期望批大小与页内剩余三元组数的较小值。
   *
   * @param expectedBatchSize 期望批大小
   * @return 实际批大小
   */
  private int getActualBatchSize(int expectedBatchSize) {
    return Math.min(expectedBatchSize, triplesCount - triplesRead);
  }

  /** 定长二进制页读取器。 */
  class FixedSizeBinaryPageReader extends BagePageReader {
    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .fixedSizeBinaryReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .fixedSizeBinaryReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /** 变宽类型（ENUM、JSON、UTF8、BSON）数据页读取器。 */
  class VarWidthTypePageReader extends BagePageReader {
    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .varWidthReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .varWidthReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /**
   * 定宽二进制类型（如 BYTE[7]）数据页读取器。Spark 不支持定宽二进制， 为兼容该限制，从 Parquet 读为定宽二进制后存入 Arrow 的 {@link
   * VarBinaryVector}。
   */
  class FixedWidthBinaryPageReader extends BagePageReader {
    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .fixedWidthBinaryReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .fixedWidthBinaryReader()
          .nextDictEncodedBatch(
              vector,
              numVals,
              typeWidth,
              batchSize,
              holder,
              dictionaryEncodedValuesReader,
              dictionary);
    }
  }

  /** 布尔类型数据页读取器。 */
  class BooleanPageReader extends BagePageReader {
    @Override
    protected void nextVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      vectorizedDefinitionLevelReader
          .booleanReader()
          .nextBatch(vector, numVals, typeWidth, batchSize, holder, plainValuesReader);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector, int batchSize, int numVals, int typeWidth, NullabilityHolder holder) {
      throw new UnsupportedOperationException();
    }
  }

  /** 创建 INT32 页读取器。 */
  IntPageReader intPageReader() {
    return new IntPageReader();
  }

  /** 创建 INT64 页读取器。 */
  LongPageReader longPageReader() {
    return new LongPageReader();
  }

  /** 创建 TIMESTAMP_MILLIS 页读取器。 */
  TimestampMillisPageReader timestampMillisPageReader() {
    return new TimestampMillisPageReader();
  }

  /** 创建 TimestampInt96 页读取器。 */
  TimestampInt96PageReader timestampInt96PageReader() {
    return new TimestampInt96PageReader();
  }

  /** 创建 FLOAT 页读取器。 */
  FloatPageReader floatPageReader() {
    return new FloatPageReader();
  }

  /** 创建 DOUBLE 页读取器。 */
  DoublePageReader doublePageReader() {
    return new DoublePageReader();
  }

  /** 创建定长二进制页读取器。 */
  FixedSizeBinaryPageReader fixedSizeBinaryPageReader() {
    return new FixedSizeBinaryPageReader();
  }

  /** 创建变宽类型页读取器。 */
  VarWidthTypePageReader varWidthTypePageReader() {
    return new VarWidthTypePageReader();
  }

  /** 创建定宽二进制页读取器。 */
  FixedWidthBinaryPageReader fixedWidthBinaryPageReader() {
    return new FixedWidthBinaryPageReader();
  }

  /** 创建布尔页读取器。 */
  BooleanPageReader booleanPageReader() {
    return new BooleanPageReader();
  }
}
