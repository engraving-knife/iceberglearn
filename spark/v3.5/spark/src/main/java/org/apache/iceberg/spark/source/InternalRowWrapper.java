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
package org.apache.iceberg.spark.source;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.apache.iceberg.StructLike;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 把 Spark {@link InternalRow} 适配为 Iceberg {@link StructLike}。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source 子包。
 *
 * <p>职责：包装 Spark InternalRow，提供 Iceberg StructLike 接口的 get/set/size 方法， 便于把 Spark 行传给 Iceberg 的
 * {@link org.apache.iceberg.PartitionKey#partition(StructLike)} 等逻辑。
 *
 * <p>设计意图：按 Spark 字段类型预编译 getter 函数数组，避免每次 get 都做类型分支判断； String 转 Java String、Decimal 转
 * BigDecimal、Binary 转 ByteBuffer、嵌套 Struct 递归包装。 wrap 方法支持行复用，减少对象分配。
 *
 * <p>上下游关系：被 Spark 写入路径用于构造分区键；依赖 Spark catalyst InternalRow。
 */
class InternalRowWrapper implements StructLike {
  private final DataType[] types;
  private final BiFunction<InternalRow, Integer, ?>[] getters;
  private InternalRow row = null;

  /** 构造包装器，按 rowType 字段类型预编译 getter 数组。 */
  @SuppressWarnings("unchecked")
  InternalRowWrapper(StructType rowType) {
    this.types = Stream.of(rowType.fields()).map(StructField::dataType).toArray(DataType[]::new);
    this.getters = Stream.of(types).map(InternalRowWrapper::getter).toArray(BiFunction[]::new);
  }

  /** 包装指定 InternalRow 并返回 this，便于复用包装器实例。 */
  InternalRowWrapper wrap(InternalRow internalRow) {
    this.row = internalRow;
    return this;
  }
  /** 返回大小。 */
  @Override
  public int size() {
    return types.length;
  }

  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    if (row.isNullAt(pos)) {
      return null;
    } else if (getters[pos] != null) {
      return javaClass.cast(getters[pos].apply(row, pos));
    }

    return javaClass.cast(row.get(pos, types[pos]));
  }

  @Override
  public <T> void set(int pos, T value) {
    row.update(pos, value);
  }

  /**
   * 按 Spark 类型构造取值函数。
   *
   * <p>逻辑：String/Decimal/Binary/Struct 各自特化取值并转为 Iceberg 友好类型； 其他类型返回 null，由 get 方法回退到 row.get(pos,
   * type)。
   */
  private static BiFunction<InternalRow, Integer, ?> getter(DataType type) {
    if (type instanceof StringType) {
      return (row, pos) -> row.getUTF8String(pos).toString();
    } else if (type instanceof DecimalType) {
      DecimalType decimal = (DecimalType) type;
      return (row, pos) ->
          row.getDecimal(pos, decimal.precision(), decimal.scale()).toJavaBigDecimal();
    } else if (type instanceof BinaryType) {
      return (row, pos) -> ByteBuffer.wrap(row.getBinary(pos));
    } else if (type instanceof StructType) {
      StructType structType = (StructType) type;
      InternalRowWrapper nestedWrapper = new InternalRowWrapper(structType);
      return (row, pos) -> nestedWrapper.wrap(row.getStruct(pos, structType.size()));
    }

    return null;
  }
}
