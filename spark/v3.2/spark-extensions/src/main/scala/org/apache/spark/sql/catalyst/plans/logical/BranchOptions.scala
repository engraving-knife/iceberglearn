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

/**
 * Spark Catalyst 逻辑计划节点，承载配置项。
 *
 * <p>所属模块：iceberg-spark-extensions v3.2。
 * 类型：样例类 BranchOptions。
 * <p>上下游：由解析器构造，被分析/优化规则处理。
 */
case class BranchOptions(snapshotId: Option[Long], numSnapshots: Option[Long],
                         snapshotRetain: Option[Long], snapshotRefRetain: Option[Long])
