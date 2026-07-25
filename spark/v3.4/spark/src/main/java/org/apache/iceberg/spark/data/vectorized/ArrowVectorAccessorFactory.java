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
package org.apache.iceberg.spark.data.vectorized;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.ValueVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.iceberg.arrow.vectorized.GenericArrowVectorAccessorFactory;
import org.apache.iceberg.util.UUIDUtil;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ArrowColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Arrow 向量访问器工厂，按 Spark 类型创建对应的 Arrow 向量取值器。
 *
 * <p>设计意图：以工厂模式屏蔽不同 Arrow 向量类型的取值差异，支持向量化读取。
 *
 * <p>上下游关系：由 VectorizedSparkParquetReaders / VectorizedSparkOrcReaders 使用。
 */
final class ArrowVectorAccessorFactory
    extends GenericArrowVectorAccessorFactory<
        Decimal, UTF8String, ColumnarArray, ArrowColumnVector> {

  ArrowVectorAccessorFactory() {
    super(
        DecimalFactoryImpl::new,
        StringFactoryImpl::new,
        StructChildFactoryImpl::new,
        ArrayFactoryImpl::new);
  }

  private static final class DecimalFactoryImpl implements DecimalFactory<Decimal> {
    /** 返回 GenericClass 属性。 */
    @Override
    public Class<Decimal> getGenericClass() {
      return Decimal.class;
    }
    /** 执行 ofLong 相关操作。 */
    @Override
    public Decimal ofLong(long value, int precision, int scale) {
      return Decimal.apply(value, precision, scale);
    }
    /** 执行 ofBigDecimal 相关操作。 */
    @Override
    public Decimal ofBigDecimal(BigDecimal value, int precision, int scale) {
      return Decimal.apply(value, precision, scale);
    }
  }

  private static final class StringFactoryImpl implements StringFactory<UTF8String> {
    /** 返回 GenericClass 属性。 */
    @Override
    public Class<UTF8String> getGenericClass() {
      return UTF8String.class;
    }
    /** 执行 ofRow 相关操作。 */
    @Override
    public UTF8String ofRow(VarCharVector vector, int rowId) {
      int start = vector.getStartOffset(rowId);
      int end = vector.getEndOffset(rowId);

      return UTF8String.fromAddress(
          null, vector.getDataBuffer().memoryAddress() + start, end - start);
    }
    /** 执行 ofRow 相关操作。 */
    @Override
    public UTF8String ofRow(FixedSizeBinaryVector vector, int rowId) {
      return UTF8String.fromString(UUIDUtil.convert(vector.get(rowId)).toString());
    }
    /** 执行 ofBytes 相关操作。 */
    @Override
    public UTF8String ofBytes(byte[] bytes) {
      return UTF8String.fromBytes(bytes);
    }
    /** 执行 ofByteBuffer 相关操作。 */
    @Override
    public UTF8String ofByteBuffer(ByteBuffer byteBuffer) {
      if (byteBuffer.hasArray()) {
        return UTF8String.fromBytes(
            byteBuffer.array(),
            byteBuffer.arrayOffset() + byteBuffer.position(),
            byteBuffer.remaining());
      }
      byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(bytes);
      return UTF8String.fromBytes(bytes);
    }
  }

  private static final class ArrayFactoryImpl
      implements ArrayFactory<ArrowColumnVector, ColumnarArray> {
    /** 执行 ofChild 相关操作。 */
    @Override
    public ArrowColumnVector ofChild(ValueVector childVector) {
      return new ArrowColumnVector(childVector);
    }
    /** 执行 ofRow 相关操作。 */
    @Override
    public ColumnarArray ofRow(ValueVector vector, ArrowColumnVector childData, int rowId) {
      ArrowBuf offsets = vector.getOffsetBuffer();
      int index = rowId * ListVector.OFFSET_WIDTH;
      int start = offsets.getInt(index);
      int end = offsets.getInt(index + ListVector.OFFSET_WIDTH);
      return new ColumnarArray(childData, start, end - start);
    }
  }

  private static final class StructChildFactoryImpl
      implements StructChildFactory<ArrowColumnVector> {
    /** 返回 GenericClass 属性。 */
    @Override
    public Class<ArrowColumnVector> getGenericClass() {
      return ArrowColumnVector.class;
    }
    /** 工厂构造方法。 */
    @Override
    public ArrowColumnVector of(ValueVector childVector) {
      return new ArrowColumnVector(childVector);
    }
  }
}
