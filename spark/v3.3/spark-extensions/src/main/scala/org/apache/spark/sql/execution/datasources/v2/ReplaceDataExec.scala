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

import org.apache.spark.sql.catalyst.expressions.AttributeSet
import org.apache.spark.sql.connector.write.Write
import org.apache.spark.sql.execution.SparkPlan

/**
 * Spark 物理执行相关组件。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：样例类 ReplaceDataExec。
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
case class ReplaceDataExec(
    query: SparkPlan,
    refreshCache: () => Unit,
    write: Write) extends V2ExistingTableWriteExec {

  override lazy val references: AttributeSet = query.outputSet
  override lazy val stringArgs: Iterator[Any] = Iterator(query, write)

  /**
   * 返回带新设置的副本。
   * @return 结果对象
   */
  override protected def withNewChildInternal(newChild: SparkPlan): ReplaceDataExec = {
    copy(query = newChild)
  }
}
