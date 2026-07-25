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
package org.apache.iceberg.spark;

import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.Row;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：Iceberg StructLike 的 Spark 实现，以 Spark InternalRow 作为底层数据载体。
 *
 * <p>设计意图：适配器模式，使 Spark 行可在需要 StructLike 的 Iceberg API（如分区值）中使用。
 *
 * <p>上下游关系：由分区计算、排序等场景使用。
 */
public class SparkStructLike implements StructLike {

  private final Types.StructType type;
  private Row wrapped;

  public SparkStructLike(Types.StructType type) {
    this.type = type;
  }
  /** 包装。 */
  public SparkStructLike wrap(Row row) {
    this.wrapped = row;
    return this;
  }
  /** 返回大小。 */
  @Override
  public int size() {
    return type.fields().size();
  }

  @Override
  public <T> T get(int pos, Class<T> javaClass) {
    Types.NestedField field = type.fields().get(pos);
    return javaClass.cast(SparkValueConverter.convert(field.type(), wrapped.get(pos)));
  }

  @Override
  public <T> void set(int pos, T value) {
    throw new UnsupportedOperationException("Not implemented: set");
  }
}
