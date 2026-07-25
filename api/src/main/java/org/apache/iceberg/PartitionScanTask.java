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
package org.apache.iceberg;

/**
 * 分区扫描任务：表示对某个特定分区内的数据进行的扫描任务。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：在 {@link ScanTask} 基础上额外暴露分区规范 {@link #spec()} 与分区值 {@link
 * #partition()}，使下游能够按分区对任务分组、统计或裁剪。
 *
 * <p>设计意图：把"分区信息"从具体文件扫描任务中抽离为独立接口，让所有针对分区的扫描任务 （无论是文件扫描还是其它任务）共用同一套分区访问契约。
 *
 * <p>上下游关系：被 {@link ContentScanTask} 等继承；被扫描规划器与引擎层用于按分区聚合任务。
 */
public interface PartitionScanTask extends ScanTask {
  /** 返回本任务所属分区的分区规范。 */
  PartitionSpec spec();

  /** 返回本任务的分区值。 */
  StructLike partition();
}
