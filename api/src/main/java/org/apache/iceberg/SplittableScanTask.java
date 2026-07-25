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
 * 可切分扫描任务：可被拆分为多个更小扫描任务的任务。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：在 {@link ScanTask} 基础上提供 {@link #split(long)} 方法，把大任务按目标大小拆分， 用于生成大小均衡的输入分片。
 *
 * <p>设计意图：目标分片大小仅为指导值，实际分片可能偏大或偏小；文件格式（如 Parquet）可利用 row group 偏移信息在切分时对齐格式边界，避免拆坏行组。
 *
 * <p>上下游关系：被扫描规划器在 {@code planTasks} 阶段调用，把大文件任务切分为多个小任务。
 *
 * @param <ThisT> 子类型自身，用于 split 返回类型收敛
 */
public interface SplittableScanTask<ThisT> extends ScanTask {
  /**
   * 尝试把本扫描任务拆分为多个更小的任务，每个接近 {@code targetSplitSize} 大小。
   *
   * <p>目标大小仅为指导值，实际大小可能偏大或偏小。Parquet 等格式会利用 row group 偏移 信息在切分时对齐格式边界。
   *
   * @param targetSplitSize 每个新任务的目标大小（字节）
   * @return 拆分后的小任务迭代器
   */
  Iterable<ThisT> split(long targetSplitSize);
}
