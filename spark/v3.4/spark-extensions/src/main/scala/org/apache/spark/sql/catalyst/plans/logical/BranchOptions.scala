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
 * 所属模块：iceberg-spark-extensions v3.4
 * <p>职责：分支选项载体，承载创建分支时的可选参数（如 retain、max-ref-age）。
 * <p>设计意图：以不可变选项对象集中传递分支创建参数。
 * <p>上下游关系：由 CreateOrReplaceBranch 使用。
 */

case class BranchOptions (snapshotId: Option[Long], numSnapshots: Option[Long],
                          snapshotRetain: Option[Long], snapshotRefRetain: Option[Long])
