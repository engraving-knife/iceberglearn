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
 * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 UnaryUnboundFunction。
 *
 * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
 */
abstract class UnaryUnboundFunction implements UnboundFunction {

  /**
   * 执行该方法的具体逻辑。
   *
   * @param inputType 参数
   * @return 结果对象
   */
  @Override
  public BoundFunction bind(StructType inputType) {
    DataType valueType = valueType(inputType);
    return doBind(valueType);
  }

  /** 执行该方法的具体逻辑。 */
  protected abstract BoundFunction doBind(DataType valueType);

  /** 执行该方法的具体逻辑。 */
  private DataType valueType(StructType inputType) {
    if (inputType.size() != 1) {
      throw new UnsupportedOperationException("Wrong number of inputs (expected value)");
    }

    return inputType.fields()[0].dataType();
  }
}
