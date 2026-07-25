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

package org.apache.spark.sql.execution.datasources.v2

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.util.truncatedString
import org.apache.spark.sql.connector.iceberg.catalog.Procedure
import scala.collection.compat.immutable.ArraySeq

/**
 * CALL 存储过程语句的物理执行算子。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark v3.5 扩展模块），位于 Spark SQL 物理执行层。
 *
 * <p>职责：在执行阶段调用 Iceberg 存储过程 {@link Procedure#call}，把返回的行数组包装为
 * Spark {@link InternalRow} 序列作为结果。
 *
 * <p>设计意图：作为 {@code LeafV2CommandExec} 的叶子物理命令，直接在 driver 端同步执行存储过程，
 * 不涉及分布式任务调度。使用 {@code ArraySeq.unsafeWrapArray} 零拷贝包装数组以避免额外开销。
 *
 * <p>上下游关系：由 {@code ExtendedDataSourceV2Strategy} 从逻辑节点 {@code Call} 转换而来；
 * 上游接收已解析好的存储过程实例与参数行，下游产出结果行供 Spark 输出。
 *
 * @param output 输出属性 schema
 * @param procedure 待调用的存储过程
 * @param input 封装了实参的输入行
 */
case class CallExec(
    output: Seq[Attribute],
    procedure: Procedure,
    input: InternalRow) extends LeafV2CommandExec {

  /**
   * 执行存储过程并返回结果行序列。
   *
   * <p>逻辑：调用 {@code procedure.call(input)} 得到 InternalRow 数组，
   * 再用 {@code ArraySeq.unsafeWrapArray} 包装为 Scala Seq。
   *
   * @return 存储过程返回的行序列
   */
  override protected def run(): Seq[InternalRow] = {
    ArraySeq.unsafeWrapArray(procedure.call(input))
  }

  /** 简要字符串表示，包含输出 schema 与存储过程描述。 */
  override def simpleString(maxFields: Int): String = {
    s"CallExec${truncatedString(output, "[", ", ", "]", maxFields)} ${procedure.description}"
  }
}
