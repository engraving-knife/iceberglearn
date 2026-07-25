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

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.StructType;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg 存储过程接口，定义可通过 CALL 语句调用的命名过程及其参数与返回列。
 *
 * <p>设计意图：抽象存储过程契约，使 Spark 可统一调用 Iceberg 维护操作。
 *
 * <p>上下游关系：由各 Procedure 实现（如 RewriteDataFilesProcedure）；由 CallExec 执行。
 */
public interface Procedure {
  /** Returns the input parameters of this procedure. */
  ProcedureParameter[] parameters();

  /** Returns the type of rows produced by this procedure. */
  StructType outputType();

  /**
   * Executes this procedure.
   *
   * <p>Spark will align the provided arguments according to the input parameters defined in {@link
   * #parameters()} either by position or by name before execution.
   *
   * <p>Implementations may provide a summary of execution by returning one or many rows as a
   * result. The schema of output rows must match the defined output type in {@link #outputType()}.
   *
   * @param args input arguments
   * @return the result of executing this procedure with the given arguments
   */
  InternalRow[] call(InternalRow args);

  /** Returns the description of this procedure. */
  default String description() {
    return this.getClass().toString();
  }
}
