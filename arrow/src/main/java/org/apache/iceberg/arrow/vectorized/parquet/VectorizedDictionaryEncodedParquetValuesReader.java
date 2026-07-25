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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.arrow.vector.BaseVariableWidthVector;
import org.apache.arrow.vector.BitVectorHelper;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.IntVector;
import org.apache.iceberg.arrow.vectorized.NullabilityHolder;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.parquet.column.Dictionary;

/**
 * 文件级说明：Parquet 字典编码数据的向量化解码器。
 *
 * <p>所属模块：iceberg-arrow 的 parquet 子包（字典编码列的批量解码）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>批量读取字典编码数据，将字典 id 解码为实际值后写入 Arrow 向量。
 *   <li>提供按类型划分的内部 DictEncodedReader（Long/Integer/Float/Double/时间戳/ Decimal/二进制/字符串等），由子类实现具体的
 *       nextVal 写入逻辑。
 * </ul>
 *
 * <p>设计意图：与其他向量化读取器不同，本解码器的 方法无需读取 definition level， 仅在存在非空值时被调用。通过抽象 {@link BaseDictEncodedReader}
 * 复用按组循环与 空值/有效性设置逻辑，nextVal 由各类型子类实现。
 *
 * <p>上下游关系：继承 {@link BaseVectorizedParquetValuesReader}； 被 {@link VectorizedPageIterator}
 * 调用以批量解码字典编码页。
 */
public class VectorizedDictionaryEncodedParquetValuesReader
    extends BaseVectorizedParquetValuesReader {

  /**
   * 构造字典编码解码器。
   *
   * @param maxDefLevel 最大定义级别
   * @param setValidityVector 是否设置 Arrow 有效性向量
   */
  public VectorizedDictionaryEncodedParquetValuesReader(
      int maxDefLevel, boolean setValidityVector) {
    super(maxDefLevel, setValidityVector);
  }

  /** 字典解码批量读取骨架：按组循环读取字典 id，解码后写入向量并标记非空。 */
  abstract class BaseDictEncodedReader {
    /**
     * 读取一批字典编码值并解码写入向量。
     *
     * <p>逻辑：循环直到读满 numValuesToRead；每组按 RLE/PACKED 模式取值，调用 nextVal 解码 写入，并通过 nullabilityHolder
     * 标记非空，按需设置 Arrow 有效性位。
     *
     * @param vector 目标向量
     * @param startOffset 起始偏移
     * @param numValuesToRead 待读值数
     * @param dict Parquet 字典
     * @param nullabilityHolder 空值持有者
     * @param typeWidth 类型宽度（-1 表示按下标而非字节偏移）
     */
    public void nextBatch(
        FieldVector vector,
        int startOffset,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth) {
      int left = numValuesToRead;
      int idx = startOffset;
      while (left > 0) {
        if (currentCount == 0) {
          readNextGroup();
        }
        int numValues = Math.min(left, currentCount);
        for (int i = 0; i < numValues; i++) {
          int index = idx * typeWidth;
          if (typeWidth == -1) {
            index = idx;
          }
          if (Mode.RLE.equals(mode)) {
            nextVal(vector, dict, index, currentValue, typeWidth);
          } else if (Mode.PACKED.equals(mode)) {
            nextVal(vector, dict, index, packedValuesBuffer[packedValuesBufferIdx++], typeWidth);
          }
          nullabilityHolder.setNotNull(idx);
          if (setArrowValidityVector) {
            BitVectorHelper.setBit(vector.getValidityBuffer(), idx);
          }
          idx++;
        }
        left -= numValues;
        currentCount -= numValues;
      }
    }

    /**
     * 由子类实现：将单个字典值解码后写入向量指定位置。
     *
     * @param vector 目标向量
     * @param dict Parquet 字典
     * @param idx 写入位置（字节偏移或下标）
     * @param currentVal 字典 id
     * @param typeWidth 类型宽度
     */
    protected abstract void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth);
  }

  /** 直接写入字典 id 的读取器（IntVector）。 */
  class DictionaryIdReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      ((IntVector) vector).set(idx, currentVal);
    }
  }

  /** 解码为 long 的读取器。 */
  class LongDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      vector.getDataBuffer().setLong(idx, dict.decodeToLong(currentVal));
    }
  }

  /** 解码毫秒时间戳并放大到微秒的读取器。 */
  class TimestampMillisDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      vector.getDataBuffer().setLong(idx, dict.decodeToLong(currentVal) * 1000);
    }
  }

  /** 解码 INT96 时间戳的读取器。 */
  class TimestampInt96DictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      ByteBuffer buffer =
          dict.decodeToBinary(currentVal).toByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
      long timestampInt96 = ParquetUtil.extractTimestampInt96(buffer);
      vector.getDataBuffer().setLong(idx, timestampInt96);
    }
  }

  /** 解码为 int 的读取器。 */
  class IntegerDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      vector.getDataBuffer().setInt(idx, dict.decodeToInt(currentVal));
    }
  }

  /** 解码为 float 的读取器。 */
  class FloatDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      vector.getDataBuffer().setFloat(idx, dict.decodeToFloat(currentVal));
    }
  }

  /** 解码为 double 的读取器。 */
  class DoubleDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      vector.getDataBuffer().setDouble(idx, dict.decodeToDouble(currentVal));
    }
  }

  /** 解码定宽二进制的读取器。 */
  class FixedWidthBinaryDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      ByteBuffer buffer = dict.decodeToBinary(currentVal).toByteBuffer();
      vector.getDataBuffer().setBytes(idx, buffer);
    }
  }

  /** 解码定长二进制底层 Decimal 的读取器。 */
  class FixedLengthDecimalDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      byte[] bytes = dict.decodeToBinary(currentVal).getBytesUnsafe();
      DecimalVectorUtil.setBigEndian((DecimalVector) vector, idx, bytes);
    }
  }

  /** 解码变宽二进制（字符串）的读取器。 */
  class VarWidthBinaryDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      ByteBuffer buffer = dict.decodeToBinary(currentVal).toByteBuffer();
      ((BaseVariableWidthVector) vector)
          .setSafe(
              idx,
              buffer.array(),
              buffer.position() + buffer.arrayOffset(),
              buffer.limit() - buffer.position());
    }
  }

  /** 解码 int 底层 Decimal 的读取器。 */
  class IntBackedDecimalDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      ((DecimalVector) vector).set(idx, dict.decodeToInt(currentVal));
    }
  }

  /** 解码 long 底层 Decimal 的读取器。 */
  class LongBackedDecimalDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      ((DecimalVector) vector).set(idx, dict.decodeToLong(currentVal));
    }
  }

  /** 解码定长二进制（如 UUID）的读取器。 */
  class FixedSizeBinaryDictEncodedReader extends BaseDictEncodedReader {
    @Override
    protected void nextVal(
        FieldVector vector, Dictionary dict, int idx, int currentVal, int typeWidth) {
      byte[] bytes = dict.decodeToBinary(currentVal).getBytesUnsafe();
      byte[] vectorBytes = new byte[typeWidth];
      System.arraycopy(bytes, 0, vectorBytes, 0, typeWidth);
      ((FixedSizeBinaryVector) vector).set(idx, vectorBytes);
    }
  }

  /** 创建字典 id 读取器。 */
  public DictionaryIdReader dictionaryIdReader() {
    return new DictionaryIdReader();
  }

  /** 创建 long 解码读取器。 */
  public LongDictEncodedReader longDictEncodedReader() {
    return new LongDictEncodedReader();
  }

  /** 创建毫秒时间戳解码读取器。 */
  public TimestampMillisDictEncodedReader timestampMillisDictEncodedReader() {
    return new TimestampMillisDictEncodedReader();
  }

  /** 创建 INT96 时间戳解码读取器。 */
  public TimestampInt96DictEncodedReader timestampInt96DictEncodedReader() {
    return new TimestampInt96DictEncodedReader();
  }

  /** 创建 int 解码读取器。 */
  public IntegerDictEncodedReader integerDictEncodedReader() {
    return new IntegerDictEncodedReader();
  }

  /** 创建 float 解码读取器。 */
  public FloatDictEncodedReader floatDictEncodedReader() {
    return new FloatDictEncodedReader();
  }

  /** 创建 double 解码读取器。 */
  public DoubleDictEncodedReader doubleDictEncodedReader() {
    return new DoubleDictEncodedReader();
  }

  /** 创建定宽二进制解码读取器。 */
  public FixedWidthBinaryDictEncodedReader fixedWidthBinaryDictEncodedReader() {
    return new FixedWidthBinaryDictEncodedReader();
  }

  /** 创建定长 Decimal 解码读取器。 */
  public FixedLengthDecimalDictEncodedReader fixedLengthDecimalDictEncodedReader() {
    return new FixedLengthDecimalDictEncodedReader();
  }

  /** 创建变宽二进制解码读取器。 */
  public VarWidthBinaryDictEncodedReader varWidthBinaryDictEncodedReader() {
    return new VarWidthBinaryDictEncodedReader();
  }

  /** 创建 int 底层 Decimal 解码读取器。 */
  public IntBackedDecimalDictEncodedReader intBackedDecimalDictEncodedReader() {
    return new IntBackedDecimalDictEncodedReader();
  }

  /** 创建 long 底层 Decimal 解码读取器。 */
  public LongBackedDecimalDictEncodedReader longBackedDecimalDictEncodedReader() {
    return new LongBackedDecimalDictEncodedReader();
  }

  /** 创建定长二进制解码读取器。 */
  public FixedSizeBinaryDictEncodedReader fixedSizeBinaryDictEncodedReader() {
    return new FixedSizeBinaryDictEncodedReader();
  }
}
