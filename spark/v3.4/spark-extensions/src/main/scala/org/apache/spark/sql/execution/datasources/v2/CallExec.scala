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
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：存储过程调用的物理执行节点，执行已解析的 Procedure 并以行形式返回结果。
 * <p>设计意图：实现 Call 的物理执行，调用 Procedure.call 并包装结果为 RDD。
 * <p>上下游关系：由 ExtendedDataSourceV2Strategy 从 Call 创建。
 */

case class CallExec(
    output: Seq[Attribute],
    procedure: Procedure,
    input: InternalRow) extends LeafV2CommandExec {
  /** 执行任务。 */

  override protected def run(): Seq[InternalRow] = {
    ArraySeq.unsafeWrapArray(procedure.call(input))
  }
  /** 执行 simpleString 相关操作。 */

  override def simpleString(maxFields: Int): String = {
    s"CallExec${truncatedString(output, "[", ", ", "]", maxFields)} ${procedure.description}"
  }
}
