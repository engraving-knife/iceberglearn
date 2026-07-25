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

import org.apache.iceberg.spark.Spark3Util
import org.apache.iceberg.spark.SparkCatalog
import org.apache.iceberg.spark.SparkSessionCatalog
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.Strategy
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow
import org.apache.spark.sql.catalyst.expressions.PredicateHelper
import org.apache.spark.sql.catalyst.plans.logical.AddPartitionField
import org.apache.spark.sql.catalyst.plans.logical.Call
import org.apache.spark.sql.catalyst.plans.logical.CreateOrReplaceBranch
import org.apache.spark.sql.catalyst.plans.logical.CreateOrReplaceTag
import org.apache.spark.sql.catalyst.plans.logical.DropBranch
import org.apache.spark.sql.catalyst.plans.logical.DropIdentifierFields
import org.apache.spark.sql.catalyst.plans.logical.DropPartitionField
import org.apache.spark.sql.catalyst.plans.logical.DropTag
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.plans.logical.OrderAwareCoalesce
import org.apache.spark.sql.catalyst.plans.logical.ReplacePartitionField
import org.apache.spark.sql.catalyst.plans.logical.SetIdentifierFields
import org.apache.spark.sql.catalyst.plans.logical.SetWriteDistributionAndOrdering
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.catalog.TableCatalog
import org.apache.spark.sql.execution.OrderAwareCoalesceExec
import org.apache.spark.sql.execution.SparkPlan
import scala.jdk.CollectionConverters._

/**
 * Iceberg 扩展的 Spark V2 数据源策略，将 Iceberg 特有的逻辑命令转换为物理执行算子。
 *
 * <p>所属模块：iceberg-spark-extensions（Spark 3.5 Catalyst 扩展，在 Spark 的
 * Strategy 层注册 Iceberg 特有命令的物理计划转换规则）。
 *
 * <p>职责：
 * <ul>
 *   <li>将 Iceberg 扩展的逻辑命令（Call、AddPartitionField、CreateOrReplaceBranch、
 *       DropBranch、DropTag、DropPartitionField、ReplacePartitionField、
 *       SetIdentifierFields、DropIdentifierFields、SetWriteDistributionAndOrdering、
 *       OrderAwareCoalesce）转换为对应的物理执行算子。</li>
 *   <li>通过内部提取器 IcebergCatalogAndIdentifier 判断目标表是否属于 Iceberg
 *       catalog，仅对 Iceberg 表应用扩展策略。</li>
 * </ul>
 *
 * <p>设计意图：Spark V2 数据源原生不支持 Iceberg 的分支/标签/分区字段管理等
 * 特有操作，通过扩展 Strategy 在 Catalyst 物理计划生成阶段插入自定义转换，
 * 避免修改 Spark 内核。使用模式匹配 + 提取器使代码简洁且类型安全；
 * 不匹配的 plan 返回 Nil 交由 Spark 默认策略处理。
 *
 * <p>上下游关系：由 Iceberg 通过 SparkSessionExtensions 注册到 Spark 执行器；
 * 上游接收 Catalyst 优化后的逻辑计划，下游产出 SparkPlan 交由 Spark 调度执行。
 */
case class ExtendedDataSourceV2Strategy(spark: SparkSession) extends Strategy with PredicateHelper {

  /**
   * 将逻辑计划转换为物理执行计划序列。
   *
   * <p>逻辑：通过模式匹配逐一识别 Iceberg 扩展的逻辑命令节点：
   * <ul>
   *   <li>{@code Call} -> CallExec（调用存储过程）</li>
   *   <li>{@code AddPartitionField} -> AddPartitionFieldExec</li>
   *   <li>{@code CreateOrReplaceBranch} -> CreateOrReplaceBranchExec</li>
   *   <li>{@code CreateOrReplaceTag} -> CreateOrReplaceTagExec</li>
   *   <li>{@code DropBranch/DropTag} -> DropBranchExec/DropTagExec</li>
   *   <li>{@code DropPartitionField} -> DropPartitionFieldExec</li>
   *   <li>{@code ReplacePartitionField} -> ReplacePartitionFieldExec</li>
   *   <li>{@code SetIdentifierFields/DropIdentifierFields} -> 对应 Exec</li>
   *   <li>{@code SetWriteDistributionAndOrdering} -> 对应 Exec</li>
   *   <li>{@code OrderAwareCoalesce} -> OrderAwareCoalesceExec</li>
   * </ul>
   * 其中大部分命令通过 IcebergCatalogAndIdentifier 提取器限定仅对 Iceberg 表生效。
   * 不匹配的计划返回 Nil。
   *
   * @param plan 待转换的逻辑计划
   * @return 物理计划序列，空序列表示不处理
   */
  override def apply(plan: LogicalPlan): Seq[SparkPlan] = plan match {
    case c @ Call(procedure, args) =>
      val input = buildInternalRow(args)
      CallExec(c.output, procedure, input) :: Nil

    case AddPartitionField(IcebergCatalogAndIdentifier(catalog, ident), transform, name) =>
      AddPartitionFieldExec(catalog, ident, transform, name) :: Nil

    case CreateOrReplaceBranch(
        IcebergCatalogAndIdentifier(catalog, ident), branch, branchOptions, create, replace, ifNotExists) =>
      CreateOrReplaceBranchExec(catalog, ident, branch, branchOptions, create, replace, ifNotExists) :: Nil

    case CreateOrReplaceTag(
    IcebergCatalogAndIdentifier(catalog, ident), tag, tagOptions, create, replace, ifNotExists) =>
      CreateOrReplaceTagExec(catalog, ident, tag, tagOptions, create, replace, ifNotExists) :: Nil

    case DropBranch(IcebergCatalogAndIdentifier(catalog, ident), branch, ifExists) =>
      DropBranchExec(catalog, ident, branch, ifExists) :: Nil

    case DropTag(IcebergCatalogAndIdentifier(catalog, ident), tag, ifExists) =>
      DropTagExec(catalog, ident, tag, ifExists) :: Nil

    case DropPartitionField(IcebergCatalogAndIdentifier(catalog, ident), transform) =>
      DropPartitionFieldExec(catalog, ident, transform) :: Nil

    case ReplacePartitionField(IcebergCatalogAndIdentifier(catalog, ident), transformFrom, transformTo, name) =>
      ReplacePartitionFieldExec(catalog, ident, transformFrom, transformTo, name) :: Nil

    case SetIdentifierFields(IcebergCatalogAndIdentifier(catalog, ident), fields) =>
      SetIdentifierFieldsExec(catalog, ident, fields) :: Nil

    case DropIdentifierFields(IcebergCatalogAndIdentifier(catalog, ident), fields) =>
      DropIdentifierFieldsExec(catalog, ident, fields) :: Nil

    case SetWriteDistributionAndOrdering(
        IcebergCatalogAndIdentifier(catalog, ident), distributionMode, ordering) =>
      SetWriteDistributionAndOrderingExec(catalog, ident, distributionMode, ordering) :: Nil

    case OrderAwareCoalesce(numPartitions, coalescer, child) =>
      OrderAwareCoalesceExec(numPartitions, coalescer, planLater(child)) :: Nil

    case _ => Nil
  }

  /**
   * 将一组表达式求值为 Spark 内部行（InternalRow），用于 Call 命令的参数传递。
   *
   * <p>逻辑：对每个表达式调用 eval() 求值（表达式应为常量或可折叠的），
   * 将结果填入数组后包装为 GenericInternalRow 返回。
   *
   * @param exprs 待求值的表达式序列
   * @return 包含所有表达式求值结果的内部行
   */
  private def buildInternalRow(exprs: Seq[Expression]): InternalRow = {
    val values = new Array[Any](exprs.size)
    for (index <- exprs.indices) {
      values(index) = exprs(index).eval()
    }
    new GenericInternalRow(values)
  }

  /**
   * 提取器对象：从表标识符序列中解析出 Iceberg catalog 与 Identifier。
   *
   * <p>逻辑：调用 {@link org.apache.iceberg.spark.Spark3Util#catalogAndIdentifier}
   * 将多段标识符解析为 catalog + identifier，然后检查 catalog 是否为
   * {@link org.apache.iceberg.spark.SparkCatalog} 或
   * {@link org.apache.iceberg.spark.SparkSessionCatalog}，是则返回 Some，
   * 否则返回 None 使模式匹配落入默认分支。
   *
   * <p>设计意图：作为模式匹配提取器使用，使 ExtendedDataSourceV2Strategy 的
   * case 分支能简洁地同时完成"标识符解析 + Iceberg catalog 校验"两步操作。
   */
  private object IcebergCatalogAndIdentifier {
    /** 执行 unapply 相关操作。 */
    def unapply(identifier: Seq[String]): Option[(TableCatalog, Identifier)] = {
      val catalogAndIdentifier = Spark3Util.catalogAndIdentifier(spark, identifier.asJava)
      catalogAndIdentifier.catalog match {
        case icebergCatalog: SparkCatalog =>
          Some((icebergCatalog, catalogAndIdentifier.identifier))
        case icebergCatalog: SparkSessionCatalog[_] =>
          Some((icebergCatalog, catalogAndIdentifier.identifier))
        case _ =>
          None
      }
    }
  }
}
