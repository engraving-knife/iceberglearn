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

import org.apache.spark.sql.catalyst.expressions.Expression

/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：未解析的 CALL 语句逻辑计划节点，仅包含过程名与原始参数表达式。
 * <p>设计意图：在解析前承载 CALL 语法信息，待 ResolveProcedures 解析为 Call。
 * <p>上下游关系：由 IcebergSqlExtensionsAstBuilder 创建；由 ResolveProcedures 消费。
 */
case class CallStatement(name: Seq[String], args: Seq[CallArgument]) extends LeafParsedStatement

/**
 * An argument in a CALL statement.
 */
sealed trait CallArgument {
  /** 执行 expr 相关操作。 */
  def expr: Expression
}

/**
 * An argument in a CALL statement identified by name.
 */
case class NamedArgument(name: String, expr: Expression) extends CallArgument

/**
 * An argument in a CALL statement identified by position.
 */
case class PositionalArgument(expr: Expression) extends CallArgument
