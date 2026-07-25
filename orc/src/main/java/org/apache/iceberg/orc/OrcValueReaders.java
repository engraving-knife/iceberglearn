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
package org.apache.iceberg.orc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.types.Types;
import org.apache.orc.storage.ql.exec.vector.BytesColumnVector;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;
import org.apache.orc.storage.ql.exec.vector.DoubleColumnVector;
import org.apache.orc.storage.ql.exec.vector.LongColumnVector;
import org.apache.orc.storage.ql.exec.vector.StructColumnVector;

/**
 * ORC 字段值读取器的工具类与基础实现集合。
 *
 * <p>所属模块：iceberg-orc。提供基础类型（boolean/int/long/float/double/bytes）的单例 reader， 以及 struct 读取器基类 {@link
 * StructReader}、常量 reader、行位置 reader 等通用实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供基础类型的单例
 *       reader（BooleanReader/IntegerReader/LongReader/FloatReader/DoubleReader/BytesReader）。
 *   <li>StructReader：struct 读取基类，处理常量列/元数据列/普通列的混合读取。
 *   <li>ConstantReader：投影场景下返回固定常量值。
 *   <li>RowPositionReader：返回行在文件中的绝对位置（batch 偏移 + 行号）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>StructReader 用 isConstantOrMetadataField 标记哪些位是常量/元数据列， 读取时跳过对应列向量（vector=null），使常量列不依赖 ORC
 *       数据。
 *   <li>基础类型 reader 全部单例，避免重复构造。
 *   <li>RowPositionReader 通过 setBatchContext 接收 batch 偏移，与行号相加得到文件绝对位置。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.data.orc.GenericOrcReaders.StructReader} 继承； 各基础 reader 被
 * {@link org.apache.iceberg.data.orc.GenericOrcReader.ReadBuilder} 调用。
 */
public class OrcValueReaders {
  private OrcValueReaders() {}

  /** 返回 boolean reader（单例），从 LongColumnVector 读取 0/1。 */
  public static OrcValueReader<Boolean> booleans() {
    return BooleanReader.INSTANCE;
  }

  /** 返回 int reader（单例），从 LongColumnVector 读取。 */
  public static OrcValueReader<Integer> ints() {
    return IntegerReader.INSTANCE;
  }

  /** 返回 long reader（单例），从 LongColumnVector 读取。 */
  public static OrcValueReader<Long> longs() {
    return LongReader.INSTANCE;
  }

  /** 返回 float reader（单例），从 DoubleColumnVector 读取后强转 float。 */
  public static OrcValueReader<Float> floats() {
    return FloatReader.INSTANCE;
  }

  /** 返回 double reader（单例），从 DoubleColumnVector 读取。 */
  public static OrcValueReader<Double> doubles() {
    return DoubleReader.INSTANCE;
  }

  /** 返回 byte[] reader（单例），从 BytesColumnVector 读取切片并拷贝。 */
  public static OrcValueReader<byte[]> bytes() {
    return BytesReader.INSTANCE;
  }

  /** 创建常量 reader，始终返回指定常量值（投影场景补充缺失列）。 */
  public static <C> OrcValueReader<C> constants(C constant) {
    return new ConstantReader<>(constant);
  }

  private static class BooleanReader implements OrcValueReader<Boolean> {
    static final BooleanReader INSTANCE = new BooleanReader();

    private BooleanReader() {}

    @Override
    public Boolean nonNullRead(ColumnVector vector, int row) {
      return ((LongColumnVector) vector).vector[row] != 0;
    }
  }

  private static class IntegerReader implements OrcValueReader<Integer> {
    static final IntegerReader INSTANCE = new IntegerReader();

    private IntegerReader() {}

    @Override
    public Integer nonNullRead(ColumnVector vector, int row) {
      return (int) ((LongColumnVector) vector).vector[row];
    }
  }

  private static class LongReader implements OrcValueReader<Long> {
    static final LongReader INSTANCE = new LongReader();

    private LongReader() {}

    @Override
    public Long nonNullRead(ColumnVector vector, int row) {
      return ((LongColumnVector) vector).vector[row];
    }
  }

  private static class FloatReader implements OrcValueReader<Float> {
    private static final FloatReader INSTANCE = new FloatReader();

    private FloatReader() {}

    @Override
    public Float nonNullRead(ColumnVector vector, int row) {
      return (float) ((DoubleColumnVector) vector).vector[row];
    }
  }

  private static class DoubleReader implements OrcValueReader<Double> {
    private static final DoubleReader INSTANCE = new DoubleReader();

    private DoubleReader() {}

    @Override
    public Double nonNullRead(ColumnVector vector, int row) {
      return ((DoubleColumnVector) vector).vector[row];
    }
  }

  private static class BytesReader implements OrcValueReader<byte[]> {
    private static final BytesReader INSTANCE = new BytesReader();

    private BytesReader() {}

    @Override
    public byte[] nonNullRead(ColumnVector vector, int row) {
      BytesColumnVector bytesVector = (BytesColumnVector) vector;

      return Arrays.copyOfRange(
          bytesVector.vector[row],
          bytesVector.start[row],
          bytesVector.start[row] + bytesVector.length[row]);
    }
  }

  /**
   * Struct 读取器抽象基类：按字段位置逐列读取并组装行对象。
   *
   * <p>设计意图：
   *
   * <ul>
   *   <li>构造时按 Iceberg struct 字段顺序排列 readers，对常量列/元数据列（ROW_POSITION/ IS_DELETED/其他元数据列）插入特殊 reader
   *       而非从 ORC 列向量读取。
   *   <li>isConstantOrMetadataField 标记哪些位不对应 ORC 列向量，读取时 vector=null 跳过。
   *   <li>子类实现 create() 创建行实例和 set() 设置字段值。
   * </ul>
   */
  public abstract static class StructReader<T> implements OrcValueReader<T> {
    private final OrcValueReader<?>[] readers;
    private final boolean[] isConstantOrMetadataField;

    /**
     * 构造 struct reader。
     *
     * <p>逻辑：遍历 Iceberg struct 字段，按字段 id 检查是否在 idToConstant 中（常量列）， 是否为
     * ROW_POSITION/IS_DELETED/其他元数据列，分别插入对应特殊 reader； 普通字段则从 readers 列表按序取。
     */
    protected StructReader(
        List<OrcValueReader<?>> readers, Types.StructType struct, Map<Integer, ?> idToConstant) {
      List<Types.NestedField> fields = struct.fields();
      this.readers = new OrcValueReader[fields.size()];
      this.isConstantOrMetadataField = new boolean[fields.size()];
      for (int pos = 0, readerIndex = 0; pos < fields.size(); pos += 1) {
        Types.NestedField field = fields.get(pos);
        if (idToConstant.containsKey(field.fieldId())) {
          this.isConstantOrMetadataField[pos] = true;
          this.readers[pos] = constants(idToConstant.get(field.fieldId()));
        } else if (field.equals(MetadataColumns.ROW_POSITION)) {
          this.isConstantOrMetadataField[pos] = true;
          this.readers[pos] = new RowPositionReader();
        } else if (field.equals(MetadataColumns.IS_DELETED)) {
          this.isConstantOrMetadataField[pos] = true;
          this.readers[pos] = constants(false);
        } else if (MetadataColumns.isMetadataColumn(field.name())) {
          // in case of any other metadata field, fill with nulls
          this.isConstantOrMetadataField[pos] = true;
          this.readers[pos] = constants(null);
        } else {
          this.readers[pos] = readers.get(readerIndex++);
        }
      }
    }

    /** 创建新的行实例，由子类实现。 */
    protected abstract T create();

    /** 设置行实例指定位置的字段值，由子类实现。 */
    protected abstract void set(T struct, int pos, Object value);

    /** 返回指定位置的字段 reader。 */
    public OrcValueReader<?> reader(int pos) {
      return readers[pos];
    }

    @Override
    /** 读取非空 struct：创建行实例后委托 readInternal 逐列填充。 */
    public T nonNullRead(ColumnVector vector, int row) {
      StructColumnVector structVector = (StructColumnVector) vector;
      return readInternal(create(), structVector.fields, row);
    }

    /**
     * 逐列读取并填充行实例。
     *
     * <p>逻辑：遍历 readers，常量/元数据列 vector=null（reader 自行处理）， 普通列从 columnVectors 按序取向量；调 reader.read 后
     * set 到行实例。
     */
    private T readInternal(T struct, ColumnVector[] columnVectors, int row) {
      for (int c = 0, vectorIndex = 0; c < readers.length; ++c) {
        ColumnVector vector;
        if (isConstantOrMetadataField[c]) {
          vector = null;
        } else {
          vector = columnVectors[vectorIndex];
          vectorIndex++;
        }
        set(struct, c, reader(c).read(vector, row));
      }
      return struct;
    }

    @Override
    public void setBatchContext(long batchOffsetInFile) {
      for (OrcValueReader<?> reader : readers) {
        reader.setBatchContext(batchOffsetInFile);
      }
    }
  }

  /** 常量 reader：忽略向量始终返回固定值，用于投影补充缺失列。 */
  private static class ConstantReader<C> implements OrcValueReader<C> {
    private final C constant;

    private ConstantReader(C constant) {
      this.constant = constant;
    }

    @Override
    public C read(ColumnVector ignored, int ignoredRow) {
      return constant;
    }

    @Override
    public C nonNullRead(ColumnVector ignored, int ignoredRow) {
      return constant;
    }
  }

  /**
   * 行位置 reader：返回行在文件中的绝对位置。
   *
   * <p>设计要点：通过 setBatchContext 接收 batch 在文件中的起始行号， read 时返回 batchOffsetInFile + row。用于 Iceberg 的
   * ROW_POSITION 元数据列。
   */
  private static class RowPositionReader implements OrcValueReader<Long> {
    private long batchOffsetInFile;

    @Override
    public Long read(ColumnVector ignored, int row) {
      return batchOffsetInFile + row;
    }

    @Override
    public Long nonNullRead(ColumnVector ignored, int row) {
      throw new UnsupportedOperationException("Use RowPositionReader.read()");
    }

    @Override
    public void setBatchContext(long newBatchOffsetInFile) {
      this.batchOffsetInFile = newBatchOffsetInFile;
    }
  }
}
