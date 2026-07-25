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
package org.apache.iceberg.spark.functions;

import org.apache.spark.sql.connector.catalog.functions.BoundFunction;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructType;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：单参数未绑定函数基类，提供按输入类型延迟绑定的通用骨架。
 *
 * <p>设计意图：模板方法模式，子类只需实现具体绑定逻辑。
 *
 * <p>上下游关系：被 BucketFunction / TruncateFunction 等单参函数继承。
 */
abstract class UnaryUnboundFunction implements UnboundFunction {
  /** 绑定输入类型。 */
  @Override
  public BoundFunction bind(StructType inputType) {
    DataType valueType = valueType(inputType);
    return doBind(valueType);
  }
  /** 执行 doBind 相关操作。 */
  protected abstract BoundFunction doBind(DataType valueType);
  /** 执行 valueType 相关操作。 */
  private DataType valueType(StructType inputType) {
    if (inputType.size() != 1) {
      throw new UnsupportedOperationException("Wrong number of inputs (expected value)");
    }

    return inputType.fields()[0].dataType();
  }
}
