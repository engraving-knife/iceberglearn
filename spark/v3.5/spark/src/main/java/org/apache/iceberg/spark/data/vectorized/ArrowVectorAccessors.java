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

import org.apache.iceberg.arrow.vectorized.ArrowVectorAccessor;
import org.apache.iceberg.arrow.vectorized.VectorHolder;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ArrowColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Arrow 向量访问器的统一获取入口。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，vectorized 子包负责 向量化读取时 Arrow 向量与 Spark
 * ColumnVector 之间的适配）。
 *
 * <p>职责：根据 {@link VectorHolder} 的类型，通过工厂创建对应的 {@link ArrowVectorAccessor}，使 Spark 能从 Arrow 向量中读取
 * Decimal、UTF8String、 ColumnarArray、ArrowColumnVector 等类型的值。
 *
 * <p>设计意图：将访问器创建逻辑封装在工厂类中，对外只暴露简单的静态方法， 屏蔽不同 Arrow 向量类型的差异。私有构造函数防止实例化。
 *
 * <p>上下游关系：被向量化读取路径（如 Arrow 批处理读取器）调用； 依赖 Iceberg Arrow 模块的 VectorHolder 和 ArrowVectorAccessor。
 */
public class ArrowVectorAccessors {

  private static final ArrowVectorAccessorFactory factory = new ArrowVectorAccessorFactory();

  /**
   * 根据 VectorHolder 获取对应的 Arrow 向量访问器。
   *
   * @param holder 向量持有者，封装了 Arrow 向量及其元数据
   * @return 适配 Spark 类型的 Arrow 向量访问器
   */
  static ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector>
      getVectorAccessor(VectorHolder holder) {
    return factory.getVectorAccessor(holder);
  }

  private ArrowVectorAccessors() {}
}
