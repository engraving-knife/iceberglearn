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

import java.util.Collection;

/**
 * 文件级说明：扫描任务组接口，可能包含部分输入文件、多个输入文件或两者兼有。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将多个 {@link ScanTask} 聚合为一个组，便于引擎按组调度执行。
 *   <li>提供 grouping key（分组键），描述本组所有任务产出行的公共值（如分桶序号）。
 *   <li>聚合组内所有任务的统计信息（字节数、行数、文件数）。
 * </ul>
 *
 * <p>设计意图：扫描规划阶段可将多个相关任务（如同一分桶的数据文件任务）组合为一个组， 便于引擎按分组键做本地化处理（如广播 join 优化）。分组键类型在规划时确定，同一扫描产出的
 * 所有任务组共享相同的分组键类型。若分组随机或未知，实现应返回空 struct。
 *
 * <p>上下游关系：由 {@link Scan} 规划产出（如 {@link CombinedScanTask} 是其特化）； 被引擎读取器消费。
 *
 * @param <T> 扫描任务的类型
 */
public interface ScanTaskGroup<T extends ScanTask> extends ScanTask {
  /**
   * 返回本任务组的分组键。
   *
   * <p>分组键是本组所有任务产出行共有的值集合，可以是底层数据经变换后的结果。例如，分组键可以 是对底层行列应用 bucket 变换后计算出的分桶序号。分组键类型在规划时确定，同一扫描产出的
   * 所有任务组共享相同的分组键类型。
   *
   * <p>若数据分组是随机的或未知，实现应返回空 struct。
   *
   * @return 本任务组的分组键
   */
  default StructLike groupingKey() {
    return EmptyStructLike.get();
  }

  /** 返回本组中的扫描任务集合。 */
  Collection<T> tasks();

  /** 返回本组所有任务的字节数总和。 */
  @Override
  default long sizeBytes() {
    return tasks().stream().mapToLong(ScanTask::sizeBytes).sum();
  }

  /** 返回本组所有任务的估计行数总和。 */
  @Override
  default long estimatedRowsCount() {
    return tasks().stream().mapToLong(ScanTask::estimatedRowsCount).sum();
  }

  /** 返回本组所有任务的文件数总和。 */
  @Override
  default int filesCount() {
    return tasks().stream().mapToInt(ScanTask::filesCount).sum();
  }
}
