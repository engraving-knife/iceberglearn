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
package org.apache.iceberg.arrow.vectorized;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.apache.arrow.vector.VarCharVector;
import org.apache.iceberg.arrow.vectorized.GenericArrowVectorAccessorFactory.DecimalFactory;
import org.apache.iceberg.arrow.vectorized.GenericArrowVectorAccessorFactory.StringFactory;

/**
 * 文件级说明：Arrow 向量访问器的工厂入口，按 Iceberg 类型生成具体访问器实例。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有全局共享的 {@link GenericArrowVectorAccessorFactory}，配置 Decimal 工厂为 {@link
 *       JavaDecimalFactory}（产出 {@link BigDecimal}）、字符串工厂为 {@link JavaStringFactory}（产出 {@link
 *       String}），Struct/List 工厂抛出不支持异常。
 *   <li>对外暴露 {@link #getVectorAccessor(VectorHolder)}，依据向量持有者构造访问器。
 * </ul>
 *
 * <p>设计意图：通过工厂模式隔离不同向量类型的访问器构造细节，调用方只需传入 {@link VectorHolder} 即可获得类型合适的 {@link
 * ArrowVectorAccessor}，便于扩展新的引擎 特定类型（如 Spark 的 UTF8String）。使用 Java 原生 BigDecimal/String 作为通用默认实现。
 *
 * <p>上下游关系：上游被 {@link VectorizedArrowReader}、{@link ColumnVector} 等调用； 下游委托给 {@link
 * GenericArrowVectorAccessorFactory}。
 */
final class ArrowVectorAccessors {

  private static final GenericArrowVectorAccessorFactory<?, String, ?, ?> factory;

  static {
    factory =
        new GenericArrowVectorAccessorFactory<>(
            JavaDecimalFactory::new,
            JavaStringFactory::new,
            throwingSupplier("Struct type is not supported"),
            throwingSupplier("List type is not supported"));
  }

  /**
   * 构造一个总是抛出 {@link UnsupportedOperationException} 的 Supplier，用于不支持类型的占位。
   *
   * @param message 异常信息
   * @param <T> 返回类型
   * @return 抛异常的 Supplier
   */
  private static <T> Supplier<T> throwingSupplier(String message) {
    return () -> {
      throw new UnsupportedOperationException(message);
    };
  }

  private ArrowVectorAccessors() {
    throw new UnsupportedOperationException(
        ArrowVectorAccessors.class.getName() + " cannot be instantiated.");
  }

  /**
   * 根据向量持有者构造对应的 Arrow 向量访问器。
   *
   * @param holder 向量持有者
   * @return 类型合适的 {@link ArrowVectorAccessor}
   */
  static ArrowVectorAccessor<?, String, ?, ?> getVectorAccessor(VectorHolder holder) {
    return factory.getVectorAccessor(holder);
  }

  /** 字符串工厂实现：将 Arrow 的 UTF8 数据转换为 Java {@link String}。 */
  private static final class JavaStringFactory implements StringFactory<String> {
    @Override
    public Class<String> getGenericClass() {
      return String.class;
    }

    @Override
    public String ofRow(VarCharVector vector, int rowId) {
      return ofBytes(vector.get(rowId));
    }

    @Override
    public String ofBytes(byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String ofByteBuffer(ByteBuffer byteBuffer) {
      if (byteBuffer.hasArray()) {
        return new String(
            byteBuffer.array(),
            byteBuffer.arrayOffset() + byteBuffer.position(),
            byteBuffer.remaining(),
            StandardCharsets.UTF_8);
      }
      byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  /** Decimal 工厂实现：将数值转换为 Java {@link BigDecimal}。 */
  private static final class JavaDecimalFactory implements DecimalFactory<BigDecimal> {

    @Override
    public Class<BigDecimal> getGenericClass() {
      return BigDecimal.class;
    }

    @Override
    public BigDecimal ofLong(long value, int precision, int scale) {
      return BigDecimal.valueOf(value, scale);
    }

    @Override
    public BigDecimal ofBigDecimal(BigDecimal value, int precision, int scale) {
      return BigDecimal.valueOf(value.unscaledValue().longValue(), scale);
    }
  }
}
