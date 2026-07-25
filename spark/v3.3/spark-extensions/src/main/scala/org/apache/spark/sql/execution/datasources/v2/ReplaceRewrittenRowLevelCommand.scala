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

import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.RowLevelCommand
import org.apache.spark.sql.catalyst.rules.Rule

/**
 * Spark 物理执行相关组件。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：对象 ReplaceRewrittenRowLevelCommand。
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
object ReplaceRewrittenRowLevelCommand extends Rule[LogicalPlan] {
  /**
   * 执行核心逻辑。
   * @return 结果对象
   */
  override def apply(plan: LogicalPlan): LogicalPlan = plan transformDown {
    case c: RowLevelCommand if c.rewritePlan.isDefined =>
      c.rewritePlan.get
  }
}
