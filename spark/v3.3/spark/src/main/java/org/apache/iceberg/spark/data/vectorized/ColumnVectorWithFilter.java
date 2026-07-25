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
 * Spark 向量化读取 Iceberg 数据的列式访问组件，实现数据过滤逻辑。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ColumnVectorWithFilter。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class ColumnVectorWithFilter extends IcebergArrowColumnVector {
  private final int[] rowIdMapping;

  /** 构造 ColumnVectorWithFilter 实例。 */
  public ColumnVectorWithFilter(VectorHolder holder, int[] rowIdMapping) {
    super(holder);
    this.rowIdMapping = rowIdMapping;
  }

  /** 判断是否nullat。 */
  @Override
  public boolean isNullAt(int rowId) {
    return nullabilityHolder().isNullAt(rowIdMapping[rowId]) == 1;
  }

  /** 返回boolean。 */
  @Override
  public boolean getBoolean(int rowId) {
    return accessor().getBoolean(rowIdMapping[rowId]);
  }

  /** 返回int。 */
  @Override
  public int getInt(int rowId) {
    return accessor().getInt(rowIdMapping[rowId]);
  }

  /** 返回long。 */
  @Override
  public long getLong(int rowId) {
    return accessor().getLong(rowIdMapping[rowId]);
  }

  /** 返回float。 */
  @Override
  public float getFloat(int rowId) {
    return accessor().getFloat(rowIdMapping[rowId]);
  }

  /** 返回double。 */
  @Override
  public double getDouble(int rowId) {
    return accessor().getDouble(rowIdMapping[rowId]);
  }

  /** 返回array。 */
  @Override
  public ColumnarArray getArray(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getArray(rowIdMapping[rowId]);
  }

  /** 返回decimal。 */
  @Override
  public Decimal getDecimal(int rowId, int precision, int scale) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getDecimal(rowIdMapping[rowId], precision, scale);
  }

  /** 返回utf8string。 */
  @Override
  public UTF8String getUTF8String(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getUTF8String(rowIdMapping[rowId]);
  }

  /** 返回binary。 */
  @Override
  public byte[] getBinary(int rowId) {
    if (isNullAt(rowId)) {
      return null;
    }
    return accessor().getBinary(rowIdMapping[rowId]);
  }
}
