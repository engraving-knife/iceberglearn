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
 * Spark 向量化读取 Iceberg 数据的列式访问组件的工厂，负责创建实例。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 ArrowVectorAccessorFactory。
 *
 * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
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

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件的工厂，负责创建实例。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 DecimalFactoryImpl。
   *
   * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static final class DecimalFactoryImpl implements DecimalFactory<Decimal> {
    /** 返回genericclass。 */
    @Override
    public Class<Decimal> getGenericClass() {
      return Decimal.class;
    }

    /**
     * 构造实例。
     *
     * @param value 参数
     * @param precision 参数
     * @param scale 参数
     * @return 结果对象
     */
    @Override
    public Decimal ofLong(long value, int precision, int scale) {
      return Decimal.apply(value, precision, scale);
    }

    /**
     * 构造实例。
     *
     * @param value 参数
     * @param precision 参数
     * @param scale 参数
     * @return 结果对象
     */
    @Override
    public Decimal ofBigDecimal(BigDecimal value, int precision, int scale) {
      return Decimal.apply(value, precision, scale);
    }
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件的工厂，负责创建实例。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 StringFactoryImpl。
   *
   * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static final class StringFactoryImpl implements StringFactory<UTF8String> {
    /** 返回genericclass。 */
    @Override
    public Class<UTF8String> getGenericClass() {
      return UTF8String.class;
    }

    /**
     * 构造实例。
     *
     * @param vector 参数
     * @param rowId 参数
     * @return 结果对象
     */
    @Override
    public UTF8String ofRow(VarCharVector vector, int rowId) {
      int start = vector.getStartOffset(rowId);
      int end = vector.getEndOffset(rowId);

      return UTF8String.fromAddress(
          null, vector.getDataBuffer().memoryAddress() + start, end - start);
    }

    /**
     * 构造实例。
     *
     * @param vector 参数
     * @param rowId 参数
     * @return 结果对象
     */
    @Override
    public UTF8String ofRow(FixedSizeBinaryVector vector, int rowId) {
      return UTF8String.fromString(UUIDUtil.convert(vector.get(rowId)).toString());
    }

    /**
     * 构造实例。
     *
     * @param bytes 参数
     * @return 结果对象
     */
    @Override
    public UTF8String ofBytes(byte[] bytes) {
      return UTF8String.fromBytes(bytes);
    }

    /**
     * 构造实例。
     *
     * @param byteBuffer 参数
     * @return 结果对象
     */
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

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件的工厂，负责创建实例。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ArrayFactoryImpl。
   *
   * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static final class ArrayFactoryImpl
      implements ArrayFactory<ArrowColumnVector, ColumnarArray> {
    /**
     * 构造实例。
     *
     * @param childVector 参数
     * @return 结果对象
     */
    @Override
    public ArrowColumnVector ofChild(ValueVector childVector) {
      /** 执行该方法的具体逻辑。 */
      return new ArrowColumnVector(childVector);
    }

    /**
     * 构造实例。
     *
     * @param vector 参数
     * @param childData 参数
     * @param rowId 参数
     * @return 结果对象
     */
    @Override
    public ColumnarArray ofRow(ValueVector vector, ArrowColumnVector childData, int rowId) {
      ArrowBuf offsets = vector.getOffsetBuffer();
      int index = rowId * ListVector.OFFSET_WIDTH;
      int start = offsets.getInt(index);
      int end = offsets.getInt(index + ListVector.OFFSET_WIDTH);
      /** 执行该方法的具体逻辑。 */
      return new ColumnarArray(childData, start, end - start);
    }
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件的工厂，负责创建实例。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 StructChildFactoryImpl。
   *
   * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static final class StructChildFactoryImpl
      implements StructChildFactory<ArrowColumnVector> {
    /** 返回genericclass。 */
    @Override
    public Class<ArrowColumnVector> getGenericClass() {
      return ArrowColumnVector.class;
    }

    /**
     * 构造实例。
     *
     * @param childVector 参数
     * @return 结果对象
     */
    @Override
    public ArrowColumnVector of(ValueVector childVector) {
      /** 执行该方法的具体逻辑。 */
      return new ArrowColumnVector(childVector);
    }
  }
}
