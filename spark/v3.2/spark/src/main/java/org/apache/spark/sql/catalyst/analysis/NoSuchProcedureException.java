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
package org.apache.spark.sql.catalyst.analysis;

import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.connector.catalog.Identifier;
import scala.Option;

/**
 * Spark Catalyst 分析阶段的规则或检查，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 NoSuchProcedureException。
 *
 * <p>上下游：由 Spark SparkSessionExtensions 注册，作用于 Catalyst 计划。
 */
public class NoSuchProcedureException extends AnalysisException {
  /** 构造 NoSuchProcedureException 实例。 */
  public NoSuchProcedureException(Identifier ident) {
    super(
        "Procedure " + ident + " not found",
        Option.empty(),
        Option.empty(),
        Option.empty(),
        Option.empty(),
        Option.empty(),
        new String[0]);
  }
}
