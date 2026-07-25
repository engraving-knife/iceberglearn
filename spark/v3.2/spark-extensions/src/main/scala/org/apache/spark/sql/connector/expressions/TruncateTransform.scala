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

package org.apache.spark.sql.connector.expressions

import org.apache.spark.sql.types.IntegerType

/**
 * 文件级说明：截断变换（truncate transform）的提取器对象，用于模式匹配。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。职责：将 Spark 的 NamedTransform("truncate", ...)
 * 解构为 (宽度, 字段引用) 元组，便于在分区/排序变换处理中识别截断变换。
 *
 * <p>设计意图：使用 Scala 的 unapply 提取器模式，将变换识别逻辑封装在对象中，
 * 使调用方可用 case TruncateTransform(width, ref) 语法简洁地匹配截断变换。
 * 支持字段在前、宽度在后和宽度在前、字段在后两种参数顺序。
 */
private[sql] object TruncateTransform {

  /**
   * 尝试将表达式解构为 (截断宽度, 字段引用)。
   *
   * <p>逻辑：
   * <ol>
   *   <li>若表达式是 Transform，进一步匹配 NamedTransform("truncate", ...)；</li>
   *   <li>支持两种参数顺序：Ref 在前 + Lit 在后，或 Lit 在前 + Ref 在后；</li>
   *   <li>匹配成功返回 Some((width, FieldReference(seq)))，否则返回 None。</li>
   * </ol>
   *
   * @param expr 待解构的表达式
   * @return 匹配成功返回 Some((截断宽度, 字段引用))，不匹配返回 None
   */
  def unapply(expr: Expression): Option[(Int, FieldReference)] = expr match {
    case transform: Transform =>
      transform match {
        case NamedTransform("truncate", Seq(Ref(seq: Seq[String]), Lit(value: Int, IntegerType))) =>
          Some((value, FieldReference(seq)))
        case NamedTransform("truncate", Seq(Lit(value: Int, IntegerType), Ref(seq: Seq[String]))) =>
          Some((value, FieldReference(seq)))
        case _ =>
          None
      }
    case _ =>
      None
  }
}
