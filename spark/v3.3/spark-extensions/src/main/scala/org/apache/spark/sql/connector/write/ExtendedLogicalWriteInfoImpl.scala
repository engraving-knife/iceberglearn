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

package org.apache.spark.sql.connector.write

import org.apache.spark.sql.connector.iceberg.write.ExtendedLogicalWriteInfo
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.util.CaseInsensitiveStringMap

/**
 * 文件级说明：Spark DataSource V2 写入操作的扩展逻辑信息实现类。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。职责：封装写入操作所需的查询 ID、
 * Schema、选项以及行 ID Schema 和元数据 Schema，供 Iceberg 写入器在 Spark 引擎中
 * 获取完整的写入上下文信息。
 *
 * <p>设计意图：以 case class 实现 {@link ExtendedLogicalWriteInfo}，利用 Scala
 * 样例类自动生成 equals/hashCode/toString，简化不可变值对象的构造与传递。
 * rowIdSchema 与 metadataSchema 默认为 null，向后兼容不使用行级操作的写入场景。
 */
private[sql] case class ExtendedLogicalWriteInfoImpl(
    queryId: String,
    schema: StructType,
    options: CaseInsensitiveStringMap,
    rowIdSchema: StructType = null,
    metadataSchema: StructType = null) extends ExtendedLogicalWriteInfo
