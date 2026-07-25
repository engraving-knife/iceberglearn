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

import org.apache.spark.QueryContext;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.connector.catalog.Identifier;
import scala.Option;
import scala.collection.immutable.Map$;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：当调用不存在的存储过程时抛出的分析异常。
 *
 * <p>设计意图：继承 Spark AnalysisException，提供 procedure 维度的错误信息。
 *
 * <p>上下游关系：由 ResolveProcedures / SparkProcedures 在过程未找到时抛出。
 */
public class NoSuchProcedureException extends AnalysisException {
  public NoSuchProcedureException(Identifier ident) {
    super(
        "Procedure " + ident + " not found",
        Option.empty(),
        Option.empty(),
        Option.empty(),
        Option.empty(),
        Option.empty(),
        Map$.MODULE$.<String, String>empty(),
        new QueryContext[0]);
  }
}
