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
 * SQL 解析得到的 CALL 语句节点。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。表示尚未解析的存储过程调用语句，
 * 包含过程的多段名与参数列表，是解析阶段（Parser）产物，供后续分析阶段绑定实际过程。
 */
case class CallStatement(name: Seq[String], args: Seq[CallArgument]) extends LeafParsedStatement

/**
 * CALL 语句参数的基类（密封特质）。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。统一抽象命名参数与位置参数，
 * 携带参数对应的 Spark 表达式 {@link Expression}。使用 sealed 限定子类型，便于穷举匹配。
 */
sealed trait CallArgument {
  /** 该参数对应的 Spark 表达式。 */
  def expr: Expression
}

/**
 * CALL 语句中的命名参数（name => expr 形式）。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。用于 CALL proc(arg => value) 语法，
 * 按名称将实参绑定到过程形参。
 */
case class NamedArgument(name: String, expr: Expression) extends CallArgument

/**
 * CALL 语句中的位置参数。
 *
 * <p>所属模块：iceberg-spark 的 spark-extensions。用于 CALL proc(value1, value2) 语法，
 * 按位置顺序将实参绑定到过程形参。
 */
case class PositionalArgument(expr: Expression) extends CallArgument
