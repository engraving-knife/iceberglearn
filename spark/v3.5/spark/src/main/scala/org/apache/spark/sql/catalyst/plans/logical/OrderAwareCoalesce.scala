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

import org.apache.spark.rdd.PartitionCoalescer
import org.apache.spark.rdd.PartitionGroup
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.expressions.Attribute

// this node doesn't extend RepartitionOperation on purpose to keep this logic isolated
// and ignore it in optimizer rules such as CollapseRepartition
/**
 * 所属模块：iceberg-spark v3.5
 * <p>职责：感知顺序的合并逻辑计划节点，在合并分区时保持数据顺序。
 * <p>设计意图：作为 Iceberg 写入分布优化中保序合并的节点，避免破坏写入排序要求。
 * <p>上下游关系：由 SetWriteDistributionAndOrdering 等使用；由 OrderAwareCoalesceExec 执行。
 */
case class OrderAwareCoalesce(
    numPartitions: Int,
    coalescer: PartitionCoalescer,
    child: LogicalPlan) extends OrderPreservingUnaryNode {
  /** 执行 output 相关操作。 */

  override def output: Seq[Attribute] = child.output
  /** 返回带 NewChildInternal 设置的副本。 */

  override protected def withNewChildInternal(newChild: LogicalPlan): LogicalPlan = {
    copy(child = newChild)
  }
}

class OrderAwareCoalescer(val groupSize: Int) extends PartitionCoalescer with Serializable {
  /** 执行 coalesce 相关操作。 */

  override def coalesce(maxPartitions: Int, parent: RDD[_]): Array[PartitionGroup] = {
    val partitionBins = parent.partitions.grouped(groupSize)
    partitionBins.map { partitions =>
      val group = new PartitionGroup()
      group.partitions ++= partitions
      group
    }.toArray
  }
}
