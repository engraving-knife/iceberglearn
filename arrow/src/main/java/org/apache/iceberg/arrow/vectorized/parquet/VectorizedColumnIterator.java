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

import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
import org.apache.iceberg.arrow.vectorized.NullabilityHolder;
import org.apache.iceberg.parquet.BaseColumnIterator;
import org.apache.iceberg.parquet.BasePageIterator;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.column.page.PageReader;

/**
 * 文件级说明：列迭代器的向量化版本，按批次读取行组内某列的数据页。
 *
 * <p>所属模块：iceberg-arrow 的 parquet 子包（Parquet 列向量化读取的调度层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>委托 {@link VectorizedPageIterator} 逐页解码，按 batchSize 分批将值写入 Arrow 向量。
 *   <li>提供按类型划分的内部 BatchReader（Integer/Long/Float/Double/Dictionary/时间戳/ 定长/变长/布尔等），每个 BatchReader
 *       把分批逻辑转发给对应页读取器。
 *   <li>仅支持非嵌套列（maxRepetitionLevel == 0）。
 * </ul>
 *
 * <p>设计意图：通过抽象 {@link BatchReader} + 具体子类把“分批循环”与“按类型写入”分离， nextBatch 固定批次循环骨架，nextBatchOf
 * 由子类实现类型相关的页读取委托。
 *
 * <p>上下游关系：继承 {@link BaseColumnIterator}；被 {@link VectorizedArrowReader} 调用； 下游委托 {@link
 * VectorizedPageIterator}。
 */
public class VectorizedColumnIterator extends BaseColumnIterator {

  private final VectorizedPageIterator vectorizedPageIterator;
  private int batchSize;

  /**
   * 构造列迭代器，校验仅非嵌套列并创建页迭代器。
   *
   * @param desc 列描述符
   * @param writerVersion 写入器版本
   * @param setArrowValidityVector 是否设置 Arrow 有效性向量
   */
  public VectorizedColumnIterator(
      ColumnDescriptor desc, String writerVersion, boolean setArrowValidityVector) {
    super(desc);
    Preconditions.checkArgument(
        desc.getMaxRepetitionLevel() == 0,
        "Only non-nested columns are supported for vectorized reads");
    this.vectorizedPageIterator =
        new VectorizedPageIterator(desc, writerVersion, setArrowValidityVector);
  }

  /**
   * 设置批大小。
   *
   * @param batchSize 每批最大行数
   */
  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  /**
   * 设置行组页源并返回字典。
   *
   * <p>逻辑：先告知页迭代器本行组是否全部字典编码（因 setPageSource 可能触发数据页读取， 需提前知道编码情况），再调用父类 setPageSource，最后返回字典。
   *
   * @param store 页读取器
   * @param allPagesDictEncoded 行组内是否全部字典编码
   * @return Parquet 字典
   */
  public Dictionary setRowGroupInfo(PageReader store, boolean allPagesDictEncoded) {
    // setPageSource can result in a data page read. If that happens, we need
    // to know in advance whether all the pages in the row group are dictionary encoded or not
    this.vectorizedPageIterator.setAllPagesDictEncoded(allPagesDictEncoded);
    super.setPageSource(store);
    return dictionary;
  }

  @Override
  /**
   * 返回底层页迭代器。
   *
   * @return {@link VectorizedPageIterator}
   */
  protected BasePageIterator pageIterator() {
    return vectorizedPageIterator;
  }

  /**
   * 当前列是否产出字典编码向量。
   *
   * @return 产出字典编码向量返回 true
   */
  public boolean producesDictionaryEncodedVector() {
    return vectorizedPageIterator.producesDictionaryEncodedVector();
  }

  /** 批量读取骨架：按 batchSize 循环调用 nextBatchOf 将值写入向量。 */
  public abstract class BatchReader {
    /**
     * 读取一批值写入向量。
     *
     * <p>逻辑：在未达 batchSize 且仍有数据时循环 advance 并调用 nextBatchOf，累加已读行数， 更新 triplesRead 与向量行数。
     *
     * @param fieldVector 目标向量
     * @param typeWidth 类型宽度
     * @param holder 空值持有者
     */
    public void nextBatch(FieldVector fieldVector, int typeWidth, NullabilityHolder holder) {
      int rowsReadSoFar = 0;
      while (rowsReadSoFar < batchSize && hasNext()) {
        advance();
        int rowsInThisBatch =
            nextBatchOf(fieldVector, batchSize - rowsReadSoFar, rowsReadSoFar, typeWidth, holder);
        rowsReadSoFar += rowsInThisBatch;
        triplesRead += rowsInThisBatch;
        fieldVector.setValueCount(rowsReadSoFar);
      }
    }

    /**
     * 由子类实现：读取一批值写入向量并返回本批读取行数。
     *
     * @param vector 目标向量
     * @param expectedBatchSize 期望批大小
     * @param numValsInVector 向量已有值数（起始偏移）
     * @param typeWidth 类型宽度
     * @param holder 空值持有者
     * @return 本批读取行数
     */
    protected abstract int nextBatchOf(
        FieldVector vector,
        int expectedBatchSize,
        int numValsInVector,
        int typeWidth,
        NullabilityHolder holder);
  }

  /** 整型批量读取器，委托 intPageReader。 */
  public class IntegerBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .intPageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 字典 id 批量读取器，将字典 id 写入 IntVector。 */
  public class DictionaryBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator.nextBatchDictionaryIds(
          (IntVector) vector, expectedBatchSize, numValsInVector, holder);
    }
  }

  /** 长整型批量读取器，委托 longPageReader。 */
  public class LongBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .longPageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 毫秒时间戳批量读取器，委托 timestampMillisPageReader。 */
  public class TimestampMillisBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .timestampMillisPageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** INT96 时间戳批量读取器，委托 timestampInt96PageReader。 */
  public class TimestampInt96BatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .timestampInt96PageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 单精度浮点批量读取器，委托 floatPageReader。 */
  public class FloatBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .floatPageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 双精度浮点批量读取器，委托 doublePageReader。 */
  public class DoubleBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .doublePageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 定长二进制批量读取器，委托 fixedSizeBinaryPageReader。 */
  public class FixedSizeBinaryBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .fixedSizeBinaryPageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 变宽类型批量读取器，委托 varWidthTypePageReader。 */
  public class VarWidthTypeBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .varWidthTypePageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 定宽二进制批量读取器，委托 fixedWidthBinaryPageReader。 */
  public class FixedWidthTypeBinaryBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .fixedWidthBinaryPageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 布尔批量读取器，委托 booleanPageReader。 */
  public class BooleanBatchReader extends BatchReader {
    @Override
    protected int nextBatchOf(
        final FieldVector vector,
        final int expectedBatchSize,
        final int numValsInVector,
        final int typeWidth,
        NullabilityHolder holder) {
      return vectorizedPageIterator
          .booleanPageReader()
          .nextBatch(vector, expectedBatchSize, numValsInVector, typeWidth, holder);
    }
  }

  /** 创建整型批量读取器。 */
  public IntegerBatchReader integerBatchReader() {
    return new IntegerBatchReader();
  }

  /** 创建字典 id 批量读取器。 */
  public DictionaryBatchReader dictionaryBatchReader() {
    return new DictionaryBatchReader();
  }

  /** 创建长整型批量读取器。 */
  public LongBatchReader longBatchReader() {
    return new LongBatchReader();
  }

  /** 创建毫秒时间戳批量读取器。 */
  public TimestampMillisBatchReader timestampMillisBatchReader() {
    return new TimestampMillisBatchReader();
  }

  /** 创建 INT96 时间戳批量读取器。 */
  public TimestampInt96BatchReader timestampInt96BatchReader() {
    return new TimestampInt96BatchReader();
  }

  /** 创建单精度浮点批量读取器。 */
  public FloatBatchReader floatBatchReader() {
    return new FloatBatchReader();
  }

  /** 创建双精度浮点批量读取器。 */
  public DoubleBatchReader doubleBatchReader() {
    return new DoubleBatchReader();
  }

  /** 创建定长二进制批量读取器。 */
  public FixedSizeBinaryBatchReader fixedSizeBinaryBatchReader() {
    return new FixedSizeBinaryBatchReader();
  }

  /** 创建变宽类型批量读取器。 */
  public VarWidthTypeBatchReader varWidthTypeBatchReader() {
    return new VarWidthTypeBatchReader();
  }

  /** 创建定宽二进制批量读取器。 */
  public FixedWidthTypeBinaryBatchReader fixedWidthTypeBinaryBatchReader() {
    return new FixedWidthTypeBinaryBatchReader();
  }

  /** 创建布尔批量读取器。 */
  public BooleanBatchReader booleanBatchReader() {
    return new BooleanBatchReader();
  }
}
