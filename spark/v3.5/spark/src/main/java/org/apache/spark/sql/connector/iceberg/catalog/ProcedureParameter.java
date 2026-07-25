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
package org.apache.spark.sql.connector.iceberg.catalog;

import org.apache.spark.sql.types.DataType;

/**
 * 存储过程（{@link Procedure}）的输入参数接口。
 *
 * <p>所属模块：iceberg-spark（位于 Spark connector iceberg catalog 包）。定义存储过程参数的 名称、类型与是否必填，供过程声明与调用绑定使用。
 *
 * <p>设计意图：通过静态工厂方法 {@link #required}/{@link #optional} 构造，隐藏实现类 ProcedureParameterImpl，便于后续扩展参数表示。
 */
public interface ProcedureParameter {

  /**
   * 构造必填输入参数。
   *
   * @param name 参数名
   * @param dataType 参数类型
   * @return 构造的存储过程参数
   */
  static ProcedureParameter required(String name, DataType dataType) {
    return new ProcedureParameterImpl(name, dataType, true);
  }

  /**
   * 构造可选输入参数。
   *
   * @param name 参数名
   * @param dataType 参数类型
   * @return 构造的可选存储过程参数
   */
  static ProcedureParameter optional(String name, DataType dataType) {
    return new ProcedureParameterImpl(name, dataType, false);
  }

  /** 返回参数名。 */
  String name();

  /** 返回参数类型。 */
  DataType dataType();

  /** 返回该参数是否必填。 */
  boolean required();
}
