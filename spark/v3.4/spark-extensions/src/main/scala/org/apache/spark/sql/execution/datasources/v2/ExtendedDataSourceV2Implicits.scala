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

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.connector.catalog.Table
import org.apache.spark.sql.connector.write.RowLevelOperationTable

/**
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：数据源 V2 扩展隐式转换对象，为 Iceberg 命令节点提供隐式增强方法。
 * <p>设计意图：以 Scala 隐式转换扩展 Spark 计划节点能力，便于链式调用。
 * <p>上下游关系：由扩展层规则与策略使用。
 */
object ExtendedDataSourceV2Implicits {
  implicit class TableHelper(table: Table) {
    /** 执行 asRowLevelOperationTable 相关操作。 */
    def asRowLevelOperationTable: RowLevelOperationTable = {
      table match {
        case rowLevelOperationTable: RowLevelOperationTable =>
          rowLevelOperationTable
        case _ =>
          throw new AnalysisException(s"Table ${table.name} is not a row-level operation table")
      }
    }
  }
}
