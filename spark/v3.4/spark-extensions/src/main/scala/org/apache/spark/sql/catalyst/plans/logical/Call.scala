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

package org.apache.spark.sql.catalyst.plans.logical

import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.util.truncatedString
import org.apache.spark.sql.connector.iceberg.catalog.Procedure
/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：已解析的 CALL 语句逻辑计划节点，封装过程实例与输入参数。
 * <p>设计意图：表示一个待执行的存储过程调用。
 * <p>上下游关系：由 ResolveProcedures 创建；由 CallExec 执行。
 */

case class Call(procedure: Procedure, args: Seq[Expression]) extends LeafCommand {
  override lazy val output: Seq[Attribute] = procedure.outputType.toAttributes
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"Call${truncatedString(output.toSeq, "[", ", ", "]", maxFields)} ${procedure.description}"
  }
}
