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
import org.apache.iceberg.arrow.vectorized.VectorHolder.ConstantVectorHolder;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.vectorized.ColumnVector;

/**
 * Spark 向量化读取 Iceberg 数据的列式访问组件的构建器，负责分步骤构造目标对象。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ColumnVectorBuilder。
 *
 * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
class ColumnVectorBuilder {
  private boolean[] isDeleted;
  private int[] rowIdMapping;

  /**
   * 返回带新设置的副本。
   *
   * @param rowIdMappingArray 参数
   * @param isDeletedArray 参数
   * @return 结果对象
   */
  public ColumnVectorBuilder withDeletedRows(int[] rowIdMappingArray, boolean[] isDeletedArray) {
    this.rowIdMapping = rowIdMappingArray;
    this.isDeleted = isDeletedArray;
    return this;
  }

  /**
   * 构造并返回目标对象。
   *
   * @param holder 参数
   * @param numRows 参数
   * @return 结果对象
   */
  public ColumnVector build(VectorHolder holder, int numRows) {
    if (holder.isDummy()) {
      if (holder instanceof VectorHolder.DeletedVectorHolder) {
        /** 删除数据或文件。 */
        return new DeletedColumnVector(Types.BooleanType.get(), isDeleted);
      } else if (holder instanceof ConstantVectorHolder) {
        /** 执行该方法的具体逻辑。 */
        return new ConstantColumnVector(
            Types.IntegerType.get(), numRows, ((ConstantVectorHolder<?>) holder).getConstant());
      } else {
        throw new IllegalStateException("Unknown dummy vector holder: " + holder);
      }
    } else if (rowIdMapping != null) {
      /** 执行该方法的具体逻辑。 */
      return new ColumnVectorWithFilter(holder, rowIdMapping);
    } else {
      /** 执行该方法的具体逻辑。 */
      return new IcebergArrowColumnVector(holder);
    }
  }
}
