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

package org.apache.spark.sql.execution

import org.apache.spark.rdd.PartitionCoalescer
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.expressions.SortOrder
import org.apache.spark.sql.catalyst.plans.physical.Partitioning
import org.apache.spark.sql.catalyst.plans.physical.SinglePartition
import org.apache.spark.sql.catalyst.plans.physical.UnknownPartitioning
/**
 * 所属模块：iceberg-spark v3.4
 * <p>职责：感知顺序的合并物理执行节点，在合并分区时保持数据顺序。
 * <p>设计意图：实现保序合并，避免破坏 Iceberg 写入排序要求。
 * <p>上下游关系：由 Spark 物理计划策略从 OrderAwareCoalesce 创建。
 */

case class OrderAwareCoalesceExec(
    numPartitions: Int,
    coalescer: PartitionCoalescer,
    child: SparkPlan) extends UnaryExecNode {
  /** 执行 output 相关操作。 */

  override def output: Seq[Attribute] = child.output
  /** 执行 outputOrdering 相关操作。 */

  override def outputOrdering: Seq[SortOrder] = child.outputOrdering
  /** 执行 outputPartitioning 相关操作。 */

  override def outputPartitioning: Partitioning = {
    if (numPartitions == 1) SinglePartition else UnknownPartitioning(numPartitions)
  }
  /** 执行 doExecute 相关操作。 */

  protected override def doExecute(): RDD[InternalRow] = {
    val result = child.execute()
    if (numPartitions == 1 && result.getNumPartitions < 1) {
      // make sure we don't output an RDD with 0 partitions,
      // when claiming that we have a `SinglePartition`
      // see CoalesceExec in Spark
      new CoalesceExec.EmptyRDDWithPartitions(sparkContext, numPartitions)
    } else {
      result.coalesce(numPartitions, shuffle = false, Some(coalescer))
    }
  }
  /** 返回带 NewChildInternal 设置的副本。 */

  override protected def withNewChildInternal(newChild: SparkPlan): SparkPlan = {
    copy(child = newChild)
  }
}
