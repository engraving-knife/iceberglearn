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
import org.apache.spark.sql.catalyst.types.DataTypeUtils
import org.apache.spark.sql.catalyst.util.truncatedString
import org.apache.spark.sql.connector.iceberg.catalog.Procedure

/**
 * 表示 CALL 存储过程语句的逻辑计划节点。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark v3.5 扩展模块），位于 Spark Catalyst 逻辑计划层。
 *
 * <p>职责：封装待调用的 Iceberg 存储过程 {@link Procedure} 及其实参表达式序列，
 * 作为 {@code CALL proc(...)} 语句解析后的逻辑计划叶子节点（{@link LeafCommand}）。
 *
 * <p>设计意图：把存储过程调用建模为逻辑命令节点，便于在分析期由
 * {@code ResolveProcedures}/{@code ProcedureArgumentCoercion} 解析过程名并强转参数，
 * 再由执行层 {@code CallExec} 物理化执行。
 *
 * <p>上下游关系：由 {@code IcebergSqlExtensionsAstBuilder} 在解析阶段构造；
 * 被分析期规则消费并最终转换为物理算子 {@code CallExec}。
 *
 * @param procedure 待调用的存储过程
 * @param args 实参表达式序列
 */
case class Call(procedure: Procedure, args: Seq[Expression]) extends LeafCommand {
  /** 输出属性由存储过程声明的 outputType 转换而来。 */
  override lazy val output: Seq[Attribute] = DataTypeUtils.toAttributes(procedure.outputType)

  /** 简要字符串表示，包含输出 schema 与存储过程描述，受 maxFields 限制。 */
  override def simpleString(maxFields: Int): String = {
    s"Call${truncatedString(output.toSeq, "[", ", ", "]", maxFields)} ${procedure.description}"
  }
}
