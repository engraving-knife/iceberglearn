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
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.vector.BaseVariableWidthVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.BitVectorHelper;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.iceberg.arrow.vectorized.NullabilityHolder;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.parquet.ValuesAsBytesReader;
import org.apache.parquet.column.Dictionary;

/**
 * 文件级说明：结合 definition level 向量化读取 Parquet 列值并写入 Arrow 向量，处理空值与字典/普通编码。所属模块：iceberg-arrow 的 parquet
 * 子包。继承 BaseVectorizedParquetValuesReader，被 VectorizedPageIterator 调用。设计意图：按 definition level
 * 判定空值，RLE 模式批量拷贝、PACKED 模式逐值处理；NumericBaseReader 处理定宽数值，BaseReader 处理其他类型，子类实现
 * nextVal/nextDictEncodedVal。
 */
public final class VectorizedParquetDefinitionLevelReader
    extends BaseVectorizedParquetValuesReader {

  /**
   * 构造 definition level 读取器（定宽，不读长度前缀）。
   *
   * @param bitWidth 位宽
   * @param maxDefLevel 最大定义级别
   * @param setArrowValidityVector 是否设置 Arrow 有效性向量
   */
  public VectorizedParquetDefinitionLevelReader(
      int bitWidth, int maxDefLevel, boolean setArrowValidityVector) {
    super(bitWidth, maxDefLevel, setArrowValidityVector);
  }

  /**
   * 构造 definition level 读取器，可指定是否读取长度前缀。
   *
   * @param bitWidth 位宽
   * @param maxDefLevel 最大定义级别
   * @param readLength 是否读取长度前缀
   * @param setArrowValidityVector 是否设置 Arrow 有效性向量
   */
  public VectorizedParquetDefinitionLevelReader(
      int bitWidth, int maxDefLevel, boolean readLength, boolean setArrowValidityVector) {
    super(bitWidth, maxDefLevel, readLength, setArrowValidityVector);
  }

  /** 定宽数值类型的批量读取骨架（Long/Integer/Float/Double），结合 definition level 处理空值。 */
  abstract class NumericBaseReader {
    /**
     * 读取一批定宽数值写入向量（普通编码）。
     *
     * <p>逻辑：按组循环；RLE 模式批量拷贝连续非空值，PACKED 模式逐值按 definition level 判定非空则调用 nextVal 写入并标记，否则置空。
     *
     * @param vector 目标向量
     * @param startOffset 起始偏移
     * @param typeWidth 类型宽度
     * @param numValsToRead 待读值数
     * @param nullabilityHolder 空值持有者
     * @param valuesReader 原始值读取器
     */
    public void nextBatch(
        final FieldVector vector,
        final int startOffset,
        final int typeWidth,
        final int numValsToRead,
        NullabilityHolder nullabilityHolder,
        ValuesAsBytesReader valuesReader) {
      int bufferIdx = startOffset;
      int left = numValsToRead;
      while (left > 0) {
        if (currentCount == 0) {
          readNextGroup();
        }
        int numValues = Math.min(left, currentCount);
        switch (mode) {
          case RLE:
            setNextNValuesInVector(
                typeWidth, nullabilityHolder, valuesReader, bufferIdx, vector, numValues);
            bufferIdx += numValues;
            break;
          case PACKED:
            for (int i = 0; i < numValues; ++i) {
              if (packedValuesBuffer[packedValuesBufferIdx++] == maxDefLevel) {
                nextVal(vector, bufferIdx * typeWidth, valuesReader, mode);
                nullabilityHolder.setNotNull(bufferIdx);
                if (setArrowValidityVector) {
                  BitVectorHelper.setBit(vector.getValidityBuffer(), bufferIdx);
                }
              } else {
                setNull(nullabilityHolder, bufferIdx, vector.getValidityBuffer());
              }
              bufferIdx++;
            }
            break;
        }
        left -= numValues;
        currentCount -= numValues;
      }
    }

    /**
     * 读取一批字典编码定宽数值写入向量。
     *
     * <p>逻辑：按组循环；RLE 模式下当前值等于 maxDefLevel 时批量解码，否则批量置空； PACKED 模式逐值按 definition level 判定调用
     * nextDictEncodedVal 或置空。
     *
     * @param vector 目标向量
     * @param startOffset 起始偏移
     * @param typeWidth 类型宽度
     * @param numValsToRead 待读值数
     * @param nullabilityHolder 空值持有者
     * @param dictionaryEncodedValuesReader 字典解码读取器
     * @param dict Parquet 字典
     */
    public void nextDictEncodedBatch(
        final FieldVector vector,
        final int startOffset,
        final int typeWidth,
        final int numValsToRead,
        NullabilityHolder nullabilityHolder,
        VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader,
        Dictionary dict) {
      int idx = startOffset;
      int left = numValsToRead;
      while (left > 0) {
        if (currentCount == 0) {
          readNextGroup();
        }
        int numValues = Math.min(left, currentCount);
        ArrowBuf validityBuffer = vector.getValidityBuffer();
        switch (mode) {
          case RLE:
            if (currentValue == maxDefLevel) {
              nextDictEncodedVal(
                  vector,
                  idx,
                  dictionaryEncodedValuesReader,
                  dict,
                  mode,
                  numValues,
                  nullabilityHolder,
                  typeWidth);
            } else {
              setNulls(nullabilityHolder, idx, numValues, validityBuffer);
            }
            idx += numValues;
            break;
          case PACKED:
            for (int i = 0; i < numValues; i++) {
              if (packedValuesBuffer[packedValuesBufferIdx++] == maxDefLevel) {
                nextDictEncodedVal(
                    vector,
                    idx,
                    dictionaryEncodedValuesReader,
                    dict,
                    mode,
                    numValues,
                    nullabilityHolder,
                    typeWidth);
                nullabilityHolder.setNotNull(idx);
                if (setArrowValidityVector) {
                  BitVectorHelper.setBit(vector.getValidityBuffer(), idx);
                }
              } else {
                setNull(nullabilityHolder, idx, validityBuffer);
              }
              idx++;
            }
            break;
        }
        left -= numValues;
        currentCount -= numValues;
      }
    }

    /**
     * 由子类实现：从普通编码读取器读取一个值写入向量指定位置。
     *
     * @param vector 目标向量
     * @param idx 写入位置（字节偏移）
     * @param valuesReader 原始值读取器
     * @param mode 当前解码模式
     */
    protected abstract void nextVal(
        FieldVector vector, int idx, ValuesAsBytesReader valuesReader, Mode mode);

    /**
     * 由子类实现：将一个字典编码值解码后写入向量指定位置。
     *
     * @param vector 目标向量
     * @param idx 写入位置
     * @param dictionaryEncodedValuesReader 字典解码读取器
     * @param dict Parquet 字典
     * @param mode 当前解码模式
     */
    protected abstract void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader,
        Dictionary dict,
        Mode mode,
        int numValues,
        NullabilityHolder holder,
        int typeWidth);
  }

  /** long 值读取器。 */
  class LongReader extends NumericBaseReader {
    @Override
    protected void nextVal(
        FieldVector vector, int idx, ValuesAsBytesReader valuesReader, Mode mode) {
      vector.getDataBuffer().setLong(idx, valuesReader.readLong());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader,
        Dictionary dict,
        Mode mode,
        int numValues,
        NullabilityHolder holder,
        int typeWidth) {
      if (Mode.RLE.equals(mode)) {
        dictionaryEncodedValuesReader
            .longDictEncodedReader()
            .nextBatch(vector, idx, numValues, dict, holder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        vector
            .getDataBuffer()
            .setLong(
                (long) idx * typeWidth,
                dict.decodeToLong(dictionaryEncodedValuesReader.readInteger()));
      }
    }
  }

  /** double 值读取器。 */
  class DoubleReader extends NumericBaseReader {
    @Override
    protected void nextVal(
        FieldVector vector, int idx, ValuesAsBytesReader valuesReader, Mode mode) {
      vector.getDataBuffer().setDouble(idx, valuesReader.readDouble());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader,
        Dictionary dict,
        Mode mode,
        int numValues,
        NullabilityHolder holder,
        int typeWidth) {
      if (Mode.RLE.equals(mode)) {
        dictionaryEncodedValuesReader
            .doubleDictEncodedReader()
            .nextBatch(vector, idx, numValues, dict, holder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        vector
            .getDataBuffer()
            .setDouble(
                (long) idx * typeWidth,
                dict.decodeToDouble(dictionaryEncodedValuesReader.readInteger()));
      }
    }
  }

  /** float 值读取器。 */
  class FloatReader extends NumericBaseReader {
    @Override
    protected void nextVal(
        FieldVector vector, int idx, ValuesAsBytesReader valuesReader, Mode mode) {
      vector.getDataBuffer().setFloat(idx, valuesReader.readFloat());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader,
        Dictionary dict,
        Mode mode,
        int numValues,
        NullabilityHolder holder,
        int typeWidth) {
      if (Mode.RLE.equals(mode)) {
        dictionaryEncodedValuesReader
            .floatDictEncodedReader()
            .nextBatch(vector, idx, numValues, dict, holder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        vector
            .getDataBuffer()
            .setFloat(
                (long) idx * typeWidth,
                dict.decodeToFloat(dictionaryEncodedValuesReader.readInteger()));
      }
    }
  }

  /** int 值读取器。 */
  class IntegerReader extends NumericBaseReader {
    @Override
    protected void nextVal(
        FieldVector vector, int idx, ValuesAsBytesReader valuesReader, Mode mode) {
      vector.getDataBuffer().setInt(idx, valuesReader.readInteger());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader,
        Dictionary dict,
        Mode mode,
        int numValues,
        NullabilityHolder holder,
        int typeWidth) {
      if (Mode.RLE.equals(mode)) {
        dictionaryEncodedValuesReader
            .integerDictEncodedReader()
            .nextBatch(vector, idx, numValues, dict, holder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        vector
            .getDataBuffer()
            .setInt(
                (long) idx * typeWidth,
                dict.decodeToInt(dictionaryEncodedValuesReader.readInteger()));
      }
    }
  }

  /** 非定宽数值类型的批量读取骨架（时间戳/二进制/Decimal/布尔/字典 id 等），结合 definition level 处理空值。 */
  abstract class BaseReader {
    /**
     * 读取一批值写入向量（普通编码）。
     *
     * <p>逻辑：按组循环；RLE 模式按当前值是否等于 maxDefLevel 批量处理，PACKED 模式逐值 按 definition level 判定调用 nextVal 或置空。
     *
     * @param vector 目标向量
     * @param startOffset 起始偏移
     * @param typeWidth 类型宽度
     * @param numValsToRead 待读值数
     * @param nullabilityHolder 空值持有者
     * @param valuesReader 原始值读取器
     */
    public void nextBatch(
        final FieldVector vector,
        final int startOffset,
        final int typeWidth,
        final int numValsToRead,
        NullabilityHolder nullabilityHolder,
        ValuesAsBytesReader valuesReader) {
      int bufferIdx = startOffset;
      int left = numValsToRead;
      while (left > 0) {
        if (currentCount == 0) {
          readNextGroup();
        }
        int numValues = Math.min(left, currentCount);
        byte[] byteArray = null;
        if (typeWidth > -1) {
          byteArray = new byte[typeWidth];
        }
        switch (mode) {
          case RLE:
            if (currentValue == maxDefLevel) {
              for (int i = 0; i < numValues; i++) {
                nextVal(vector, bufferIdx, valuesReader, typeWidth, byteArray);
                nullabilityHolder.setNotNull(bufferIdx);
                bufferIdx++;
              }
            } else {
              setNulls(nullabilityHolder, bufferIdx, numValues, vector.getValidityBuffer());
              bufferIdx += numValues;
            }
            break;
          case PACKED:
            for (int i = 0; i < numValues; i++) {
              if (packedValuesBuffer[packedValuesBufferIdx++] == maxDefLevel) {
                nextVal(vector, bufferIdx, valuesReader, typeWidth, byteArray);
                nullabilityHolder.setNotNull(bufferIdx);
              } else {
                setNull(nullabilityHolder, bufferIdx, vector.getValidityBuffer());
              }
              bufferIdx++;
            }
            break;
        }
        left -= numValues;
        currentCount -= numValues;
      }
    }

    /**
     * 读取一批字典编码值写入向量。
     *
     * <p>逻辑：按组循环；RLE 模式按当前值是否等于 maxDefLevel 批量解码或置空， PACKED 模式逐值按 definition level 判定调用
     * nextDictEncodedVal 或置空。
     *
     * @param vector 目标向量
     * @param startOffset 起始偏移
     * @param typeWidth 类型宽度
     * @param numValsToRead 待读值数
     * @param nullabilityHolder 空值持有者
     * @param dictionaryEncodedValuesReader 字典解码读取器
     * @param dict Parquet 字典
     */
    public void nextDictEncodedBatch(
        final FieldVector vector,
        final int startOffset,
        final int typeWidth,
        final int numValsToRead,
        NullabilityHolder nullabilityHolder,
        VectorizedDictionaryEncodedParquetValuesReader dictionaryEncodedValuesReader,
        Dictionary dict) {
      int idx = startOffset;
      int left = numValsToRead;
      while (left > 0) {
        if (currentCount == 0) {
          readNextGroup();
        }
        int numValues = Math.min(left, currentCount);
        ArrowBuf validityBuffer = vector.getValidityBuffer();
        switch (mode) {
          case RLE:
            if (currentValue == maxDefLevel) {
              nextDictEncodedVal(
                  vector,
                  idx,
                  dictionaryEncodedValuesReader,
                  numValues,
                  dict,
                  nullabilityHolder,
                  typeWidth,
                  mode);
            } else {
              setNulls(nullabilityHolder, idx, numValues, validityBuffer);
            }
            idx += numValues;
            break;
          case PACKED:
            for (int i = 0; i < numValues; i++) {
              if (packedValuesBuffer[packedValuesBufferIdx++] == maxDefLevel) {
                nextDictEncodedVal(
                    vector,
                    idx,
                    dictionaryEncodedValuesReader,
                    numValues,
                    dict,
                    nullabilityHolder,
                    typeWidth,
                    mode);
                nullabilityHolder.setNotNull(idx);
                if (setArrowValidityVector) {
                  BitVectorHelper.setBit(vector.getValidityBuffer(), idx);
                }
              } else {
                setNull(nullabilityHolder, idx, validityBuffer);
              }
              idx++;
            }
            break;
        }
        left -= numValues;
        currentCount -= numValues;
      }
    }

    /**
     * 由子类实现：从普通编码读取器读取一个值写入向量指定位置。
     *
     * @param vector 目标向量
     * @param idx 写入位置
     * @param valuesReader 原始值读取器
     * @param mode 当前解码模式
     */
    protected abstract void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray);

    /**
     * 由子类实现：将一个字典编码值解码后写入向量指定位置。
     *
     * @param vector 目标向量
     * @param idx 写入位置
     * @param dictionaryEncodedValuesReader 字典解码读取器
     * @param dict Parquet 字典
     * @param mode 当前解码模式
     */
    protected abstract void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode);
  }

  /** 毫秒时间戳读取器，放大到微秒。 */
  class TimestampMillisReader extends BaseReader {

    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      vector.getDataBuffer().setLong((long) idx * typeWidth, valuesReader.readLong() * 1000);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .timestampMillisDictEncodedReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        vector
            .getDataBuffer()
            .setLong((long) idx * typeWidth, dict.decodeToLong(reader.readInteger()) * 1000);
      }
    }
  }

  /** INT96 时间戳读取器。 */
  class TimestampInt96Reader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      // 8 bytes (time of day nanos) + 4 bytes(julianDay) = 12 bytes
      ByteBuffer buffer = valuesReader.getBuffer(12).order(ByteOrder.LITTLE_ENDIAN);
      long timestampInt96 = ParquetUtil.extractTimestampInt96(buffer);
      vector.getDataBuffer().setLong((long) idx * typeWidth, timestampInt96);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      switch (mode) {
        case RLE:
          reader
              .timestampInt96DictEncodedReader()
              .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
          break;
        case PACKED:
          ByteBuffer buffer =
              dict.decodeToBinary(reader.readInteger())
                  .toByteBuffer()
                  .order(ByteOrder.LITTLE_ENDIAN);
          long timestampInt96 = ParquetUtil.extractTimestampInt96(buffer);
          vector.getDataBuffer().setLong(idx, timestampInt96);
          break;
        default:
          throw new UnsupportedOperationException(
              "Unsupported mode for timestamp int96 reader: " + mode);
      }
    }
  }

  /** 定宽二进制读取器。 */
  class FixedWidthBinaryReader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      ByteBuffer buffer = valuesReader.getBuffer(typeWidth);
      ((VarBinaryVector) vector)
          .setSafe(
              idx,
              buffer.array(),
              buffer.position() + buffer.arrayOffset(),
              buffer.limit() - buffer.position());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .fixedWidthBinaryDictEncodedReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        ByteBuffer buffer = dict.decodeToBinary(reader.readInteger()).toByteBuffer();
        vector.getDataBuffer().setBytes((long) idx * typeWidth, buffer);
      }
    }
  }

  /** 定长二进制底层 Decimal 读取器。 */
  class FixedLengthDecimalReader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      valuesReader.getBuffer(typeWidth).get(byteArray, 0, typeWidth);
      DecimalVectorUtil.setBigEndian((DecimalVector) vector, idx, byteArray);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .fixedLengthDecimalDictEncodedReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        byte[] bytes = dict.decodeToBinary(reader.readInteger()).getBytesUnsafe();
        DecimalVectorUtil.setBigEndian((DecimalVector) vector, idx, bytes);
      }
    }
  }

  /** 定长二进制（如 UUID）读取器。 */
  class FixedSizeBinaryReader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      valuesReader.getBuffer(typeWidth).get(byteArray, 0, typeWidth);
      ((FixedSizeBinaryVector) vector).set(idx, byteArray);
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .fixedSizeBinaryDictEncodedReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        byte[] bytes = dict.decodeToBinary(reader.readInteger()).getBytes();
        byte[] vectorBytes = new byte[typeWidth];
        System.arraycopy(bytes, 0, vectorBytes, 0, typeWidth);
        ((FixedSizeBinaryVector) vector).set(idx, vectorBytes);
      }
    }
  }

  /** 变宽类型（字符串/二进制）读取器。 */
  class VarWidthReader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      int len = valuesReader.readInteger();
      ByteBuffer buffer = valuesReader.getBuffer(len);
      // Calling setValueLengthSafe takes care of allocating a larger buffer if
      // running out of space.
      ((BaseVariableWidthVector) vector).setValueLengthSafe(idx, len);
      int startOffset = ((BaseVariableWidthVector) vector).getStartOffset(idx);
      // It is possible that the data buffer was reallocated. So it is important to
      // not cache the data buffer reference but instead use vector.getDataBuffer().
      vector.getDataBuffer().setBytes(startOffset, buffer);
      // Similarly, we need to get the latest reference to the validity buffer as well
      // since reallocation changes reference of the validity buffers as well.
      if (setArrowValidityVector) {
        BitVectorHelper.setBit(vector.getValidityBuffer(), idx);
      }
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .varWidthBinaryDictEncodedReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        ((BaseVariableWidthVector) vector)
            .setSafe(idx, dict.decodeToBinary(reader.readInteger()).getBytesUnsafe());
      }
    }
  }

  /** int 底层 Decimal 读取器。 */
  class IntBackedDecimalReader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      ((DecimalVector) vector).set(idx, valuesReader.getBuffer(Integer.BYTES).getInt());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .intBackedDecimalDictEncodedReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        ((DecimalVector) vector).set(idx, dict.decodeToInt(reader.readInteger()));
      }
    }
  }

  /** long 底层 Decimal 读取器。 */
  class LongBackedDecimalReader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      ((DecimalVector) vector).set(idx, valuesReader.getBuffer(Long.BYTES).getLong());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .longBackedDecimalDictEncodedReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        ((DecimalVector) vector).set(idx, dict.decodeToLong(reader.readInteger()));
      }
    }
  }

  /** 布尔值读取器。 */
  class BooleanReader extends BaseReader {
    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      ((BitVector) vector).setSafe(idx, valuesReader.readBooleanAsInt());
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      throw new UnsupportedOperationException();
    }
  }

  /** 字典 id 读取器（IntVector）。 */
  class DictionaryIdReader extends BaseReader {

    @Override
    protected void nextVal(
        FieldVector vector,
        int idx,
        ValuesAsBytesReader valuesReader,
        int typeWidth,
        byte[] byteArray) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void nextDictEncodedVal(
        FieldVector vector,
        int idx,
        VectorizedDictionaryEncodedParquetValuesReader reader,
        int numValuesToRead,
        Dictionary dict,
        NullabilityHolder nullabilityHolder,
        int typeWidth,
        Mode mode) {
      if (Mode.RLE.equals(mode)) {
        reader
            .dictionaryIdReader()
            .nextBatch(vector, idx, numValuesToRead, dict, nullabilityHolder, typeWidth);
      } else if (Mode.PACKED.equals(mode)) {
        vector.getDataBuffer().setInt((long) idx * IntVector.TYPE_WIDTH, reader.readInteger());
      }
    }
  }

  /**
   * 将单个位置标记为空，并按需清除 Arrow 有效性位。
   *
   * @param nullabilityHolder 空值持有者
   * @param bufferIdx 位置
   * @param validityBuffer 有效性缓冲区
   */
  private void setNull(
      NullabilityHolder nullabilityHolder, int bufferIdx, ArrowBuf validityBuffer) {
    nullabilityHolder.setNull(bufferIdx);
    if (setArrowValidityVector) {
      BitVectorHelper.setValidityBit(validityBuffer, bufferIdx, 0);
    }
  }

  /**
   * 批量将多个位置标记为空，并按需清除 Arrow 有效性位。
   *
   * @param nullabilityHolder 空值持有者
   * @param idx 起始位置
   * @param numValues 数量
   * @param validityBuffer 有效性缓冲区
   */
  private void setNulls(
      NullabilityHolder nullabilityHolder, int idx, int numValues, ArrowBuf validityBuffer) {
    nullabilityHolder.setNulls(idx, numValues);
    if (setArrowValidityVector) {
      for (int i = 0; i < numValues; i++) {
        BitVectorHelper.setValidityBit(validityBuffer, idx + i, 0);
      }
    }
  }

  /**
   * RLE 模式下批量写入连续值：当前值等于 maxDefLevel 时批量拷贝并标记非空，否则批量置空。
   *
   * @param typeWidth 类型宽度
   * @param nullabilityHolder 空值持有者
   * @param valuesReader 原始值读取器
   * @param bufferIdx 起始位置
   * @param vector 目标向量
   * @param numValues 数量
   */
  private void setNextNValuesInVector(
      int typeWidth,
      NullabilityHolder nullabilityHolder,
      ValuesAsBytesReader valuesReader,
      int bufferIdx,
      FieldVector vector,
      int numValues) {
    ArrowBuf validityBuffer = vector.getValidityBuffer();
    if (currentValue == maxDefLevel) {
      ByteBuffer buffer = valuesReader.getBuffer(numValues * typeWidth);
      vector.getDataBuffer().setBytes((long) bufferIdx * typeWidth, buffer);
      nullabilityHolder.setNotNulls(bufferIdx, numValues);
      if (setArrowValidityVector) {
        for (int i = 0; i < numValues; i++) {
          BitVectorHelper.setBit(validityBuffer, bufferIdx + i);
        }
      }
    } else {
      setNulls(nullabilityHolder, bufferIdx, numValues, validityBuffer);
    }
  }

  /** 创建 long 读取器。 */
  LongReader longReader() {
    return new LongReader();
  }

  /** 创建 double 读取器。 */
  DoubleReader doubleReader() {
    return new DoubleReader();
  }

  /** 创建 float 读取器。 */
  FloatReader floatReader() {
    return new FloatReader();
  }

  /** 创建 int 读取器。 */
  IntegerReader integerReader() {
    return new IntegerReader();
  }

  /** 创建毫秒时间戳读取器。 */
  TimestampMillisReader timestampMillisReader() {
    return new TimestampMillisReader();
  }

  /** 创建 INT96 时间戳读取器。 */
  TimestampInt96Reader timestampInt96Reader() {
    return new TimestampInt96Reader();
  }

  /** 创建定宽二进制读取器。 */
  FixedWidthBinaryReader fixedWidthBinaryReader() {
    return new FixedWidthBinaryReader();
  }

  /** 创建定长 Decimal 读取器。 */
  FixedLengthDecimalReader fixedLengthDecimalReader() {
    return new FixedLengthDecimalReader();
  }

  /** 创建定长二进制读取器。 */
  FixedSizeBinaryReader fixedSizeBinaryReader() {
    return new FixedSizeBinaryReader();
  }

  /** 创建变宽类型读取器。 */
  VarWidthReader varWidthReader() {
    return new VarWidthReader();
  }

  /** 创建 int 底层 Decimal 读取器。 */
  IntBackedDecimalReader intBackedDecimalReader() {
    return new IntBackedDecimalReader();
  }

  /** 创建 long 底层 Decimal 读取器。 */
  LongBackedDecimalReader longBackedDecimalReader() {
    return new LongBackedDecimalReader();
  }

  /** 创建布尔读取器。 */
  BooleanReader booleanReader() {
    return new BooleanReader();
  }

  /** 创建字典 id 读取器。 */
  DictionaryIdReader dictionaryIdReader() {
    return new DictionaryIdReader();
  }
}
