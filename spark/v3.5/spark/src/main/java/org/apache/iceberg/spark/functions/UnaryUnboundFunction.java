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
 * 单参数未绑定函数基类：约束 Spark SQL 函数仅接受一个输入参数。
 *
 * <p>所属模块：iceberg-spark（functions 子包，提供可在 Spark SQL 中使用的 Iceberg 函数）。
 *
 * <p>职责：实现 {@link UnboundFunction#bind}，校验输入 schema 仅含一个字段， 取出该字段类型后委托子类的 {@link #doBind} 完成具体绑定。
 *
 * <p>设计意图：将「单参数校验」这一通用逻辑抽取到基类，子类只需关注如何根据 单个参数类型构造 {@link BoundFunction}，减少重复代码。
 *
 * <p>上下游关系：实现 Spark {@link UnboundFunction}，被具体 Iceberg SQL 函数（如类型转换函数）继承。
 */
abstract class UnaryUnboundFunction implements UnboundFunction {

  /**
   * 绑定函数：校验输入为单参数后，按参数类型构造已绑定函数。
   *
   * @param inputType 输入 schema
   * @return 已绑定函数
   */
  @Override
  public BoundFunction bind(StructType inputType) {
    DataType valueType = valueType(inputType);
    return doBind(valueType);
  }

  /**
   * 由子类实现：根据已校验的单参数类型构造 {@link BoundFunction}。
   *
   * @param valueType 单个参数的数据类型
   * @return 已绑定函数
   */
  protected abstract BoundFunction doBind(DataType valueType);

  /**
   * 校验输入 schema 仅含一个字段并返回其类型。
   *
   * @throws UnsupportedOperationException 当输入字段数不为 1 时抛出
   */
  private DataType valueType(StructType inputType) {
    if (inputType.size() != 1) {
      throw new UnsupportedOperationException("Wrong number of inputs (expected value)");
    }

    return inputType.fields()[0].dataType();
  }
}
