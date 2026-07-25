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

package org.apache.spark.sql.catalyst.expressions

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.SQLConfHelper
import org.apache.spark.sql.catalyst.plans.logical.Assignment
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.catalyst.util.CharVarcharUtils
import org.apache.spark.sql.types.DataType

/**
 * Spark 表达式与 Iceberg 表达式之间的转换。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：对象 AssignmentUtils。
 */
object AssignmentUtils extends SQLConfHelper {

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  def aligned(table: LogicalPlan, assignments: Seq[Assignment]): Boolean = {
    val sameSize = table.output.size == assignments.size
    sameSize && table.output.zip(assignments).forall { case (attr, assignment) =>
      val key = assignment.key
      val value = assignment.value
      val refsEqual = toAssignmentRef(attr).zip(toAssignmentRef(key))
        .forall{ case (attrRef, keyRef) => conf.resolver(attrRef, keyRef)}

      refsEqual &&
        DataType.equalsIgnoreCompatibleNullability(value.dataType, attr.dataType) &&
        (attr.nullable || !value.nullable)
    }
  }

  /**
   * 转换为assignmentref。
   * @return 结果对象
   */
  def toAssignmentRef(expr: Expression): Seq[String] = expr match {
    case attr: AttributeReference =>
      Seq(attr.name)
    case Alias(child, _) =>
      toAssignmentRef(child)
    case GetStructField(child, _, Some(name)) =>
      toAssignmentRef(child) :+ name
    case other: ExtractValue =>
      throw new AnalysisException(s"Updating nested fields is only supported for structs: $other")
    case other =>
      throw new AnalysisException(s"Cannot convert to a reference, unsupported expression: $other")
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  def handleCharVarcharLimits(assignment: Assignment): Assignment = {
    val key = assignment.key
    val value = assignment.value

    val rawKeyType = key.transform {
      case attr: AttributeReference =>
        CharVarcharUtils.getRawType(attr.metadata)
          .map(attr.withDataType)
          .getOrElse(attr)
    }.dataType

    if (CharVarcharUtils.hasCharVarchar(rawKeyType)) {
      val newKey = key.transform {
        case attr: AttributeReference => CharVarcharUtils.cleanAttrMetadata(attr)
      }
      val newValue = CharVarcharUtils.stringLengthCheck(value, rawKeyType)
      Assignment(newKey, newValue)
    } else {
      assignment
    }
  }
}
