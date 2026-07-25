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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Arrow 向量取值器集合，提供各类型的 Arrow 向量值访问实现。
 *
 * <p>设计意图：集中放置具体取值逻辑，供工厂按类型选取。
 *
 * <p>上下游关系：由 ArrowVectorAccessorFactory 使用。
 */
public class ArrowVectorAccessors {

  private static final ArrowVectorAccessorFactory factory = new ArrowVectorAccessorFactory();

  static ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector>
      getVectorAccessor(VectorHolder holder) {
    return factory.getVectorAccessor(holder);
  }

  private ArrowVectorAccessors() {}
}
