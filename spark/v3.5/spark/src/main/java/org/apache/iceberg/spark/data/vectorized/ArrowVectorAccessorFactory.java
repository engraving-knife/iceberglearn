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
 * Spark 专用的 Arrow 向量访问器工厂。
 *
 * <p>所属模块：iceberg-spark（data/vectorized 子包，负责向量化读取时 Arrow 向量到 Spark 列式数据的适配）。
 *
 * <p>职责：为 Arrow 向量读取提供 Spark 类型化的访问器实现——将 Arrow 的 Decimal、字符串、 数组、结构体分别映射为 Spark 的 {@link
 * Decimal}、{@link UTF8String}、 {@link ColumnarArray}、{@link ArrowColumnVector}。
 *
 * <p>设计意图：继承通用工厂 {@link GenericArrowVectorAccessorFactory}，把「具体引擎类型如何从 Arrow
 * 向量取值」这一差异点下放给各引擎实现，使核心向量化读取逻辑与 Spark 解耦。 通过方法引用注入四个具体工厂实现，避免在通用工厂中硬编码 Spark 类型。
 *
 * <p>上下游关系：被 Iceberg 的向量化 Arrow 列向量读取器用于创建具体类型的取值访问器。
 */
final class ArrowVectorAccessorFactory
    extends GenericArrowVectorAccessorFactory<
        Decimal, UTF8String, ColumnarArray, ArrowColumnVector> {

  /** 注入 Decimal、字符串、结构体子列、数组四个具体工厂实现。 */
  ArrowVectorAccessorFactory() {
    super(
        DecimalFactoryImpl::new,
        StringFactoryImpl::new,
        StructChildFactoryImpl::new,
        ArrayFactoryImpl::new);
  }

  /** Spark Decimal 类型的 Arrow 向量工厂：从 long 或 BigDecimal 构造 Spark {@link Decimal}。 */
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

  /**
   * Spark UTF8String 类型的 Arrow 向量工厂：从 VarChar、FixedSizeBinary、字节或 ByteBuffer 构造 {@link UTF8String}。
   */
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

  /** Spark 数组类型的 Arrow 向量工厂：构造子列向量与 {@link ColumnarArray}。 */
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

  /** Spark 结构体子列的 Arrow 向量工厂：将子向量包装为 {@link ArrowColumnVector}。 */
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
