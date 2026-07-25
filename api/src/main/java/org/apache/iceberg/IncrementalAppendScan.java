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
 * 文件级说明：仅针对 append 类型快照的增量表扫描配置接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：配置并执行增量扫描，仅读取指定快照区间内由 append 操作新增的数据文件， 产出 {@link FileScanTask}（组合为 {@link
 * CombinedScanTask}）。
 *
 * <p>设计意图：增量扫描场景下，许多下游只需关注“新增的数据”，无需处理 delete/overwrite。 本接口通过限定为 append
 * 操作，简化增量扫描的语义与实现，避免处理删除文件的复杂逻辑。
 *
 * <p>上下游关系：由 {@link Table#newIncrementalAppendScan()} 创建；被引擎增量读取器消费。
 */
public interface IncrementalAppendScan
    extends IncrementalScan<IncrementalAppendScan, FileScanTask, CombinedScanTask> {}
