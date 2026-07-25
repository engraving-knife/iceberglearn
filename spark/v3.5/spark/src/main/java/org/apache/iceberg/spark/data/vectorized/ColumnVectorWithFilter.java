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

import org.apache.iceberg.arrow.vectorized.VectorHolder;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 带行号映射的 Arrow 列向量：在向量化读取应用行级过滤后，按映射表对外暴露有效行。
 *
 * <p>所属模块：iceberg-spark（data/vectorized 子包，用于向量化读取路径）。
 *
 * <p>职责：包装底层 {@link IcebergArrowColumnVector}，通过行号映射数组将「逻辑行号」 转换为「物理行号」，使上层只读取过滤后保留的行。
 *
 * <p>设计意图：向量化读取常配合行级过滤（如删除文件或不等式过滤）使用，过滤后会得到一个 保留行的行号映射表。本类在所有取值方法中统一做行号转换，对上层屏蔽物理存储与逻辑视图的差异，
 * 避免在每个调用点重复处理映射。空值判断同时查询 nullabilityHolder。
 *
 * <p>上下游关系：继承 {@link IcebergArrowColumnVector}，由向量化读取器在存在行过滤时构造， 供 Spark 向量化读取引擎消费。
 */
public class ColumnVectorWithFilter extends IcebergArrowColumnVector {
  private final int[] rowIdMapping;

  /**
   * 构造带行号映射的列向量。
   *
   * @param holder 底层向量持有者
   * @param rowIdMapping 逻辑行号到物理行号的映射数组
   */
  public ColumnVectorWithFilter(VectorHolder holder, int[] rowIdMapping) {
    super(holder);
    this.rowIdMapping = rowIdMapping;
  }
  /** 判断是否 NullAt。 */
  @Override
  public boolean isNullAt(int rowId) {
    return nullabilityHolder().isNullAt(rowIdMapping[rowId]) == 1;
  }
  /** 返回 Boolean 属性。 */
  @Override
  public boolean getBoolean(int rowId) {
    return accessor().getBoolean(rowIdMapping[rowId]);
  }
  /** 返回 Int 属性。 */
  @Override
  public int getInt(int rowId) {
    return accessor().getInt(rowIdMapping[rowId]);
  }
  /** 返回 Long 属性。 */
  @Override
  public long getLong(int rowId) {
    return accessor().getLong(rowIdMapping[rowId]);
  }
  /** 返回 Float 属性。 */
  @Override
  public float getFloat(int rowId) {
    return accessor().getFloat(rowIdMapping[rowId]);
  }
  /** 返回 Double 属性。 */
  @Override
  public double getDouble(int rowId) {
    return accessor().getDouble(rowIdMapping[rowId]);
  }
  /** 返回 Array 属性。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getArray(rowIdMapping[rowId]);
  }
  /** 返回 Decimal 属性。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getDecimal(rowIdMapping[rowId], precision, scale);
  }
  /** 返回 UTF8String 属性。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getUTF8String(rowIdMapping[rowId]);
  }
  /** 返回 Binary 属性。 */
  @Override
  public byte[] getBinary(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getBinary(rowIdMapping[rowId]);
  }
}
