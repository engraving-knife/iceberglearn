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
 * Spark DataSource V2 连接器扩展，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：接口 ProcedureParameter。
 *
 * <p>上下游：由 DataSource V2 框架调用，桥接 Spark 与 Iceberg。
 */
public interface ProcedureParameter {

  /** 执行该方法的具体逻辑。 */
  static ProcedureParameter required(String name, DataType dataType) {
    /** 执行该方法的具体逻辑。 */
    return new ProcedureParameterImpl(name, dataType, true);
  }

  /** 执行该方法的具体逻辑。 */
  static ProcedureParameter optional(String name, DataType dataType) {
    /** 执行该方法的具体逻辑。 */
    return new ProcedureParameterImpl(name, dataType, false);
  }

  /** 执行该方法的具体逻辑。 */
  String name();

  /** 执行该方法的具体逻辑。 */
  DataType dataType();

  /** 执行该方法的具体逻辑。 */
  boolean required();
}
