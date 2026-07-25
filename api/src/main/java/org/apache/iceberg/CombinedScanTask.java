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
 * 组合扫描任务接口：由多个文件范围内的 {@link FileScanTask} 组成。
 *
 * <p>所属模块：iceberg-api（扫描任务抽象层）。
 *
 * <p>职责：把多个连续的文件扫描任务聚合为一个可被引擎当作单次执行单元（如 Spark 的 一个 task）处理的组合任务，从而减少任务调度开销。
 *
 * <p>设计意图：作为 {@link ScanTaskGroup} 的特化，固定元素类型为 {@link FileScanTask}， 并提供 {@link #files()}
 * 这一语义更明确的别名；默认方法 {@link #tasks()} 直接委托给 {@link #files()} 以同时满足父接口契约。
 *
 * <p>上下游关系：由扫描规划阶段生成，被引擎模块（Spark/Flink 等）拆分或合并后调度执行。
 */
public interface CombinedScanTask extends ScanTaskGroup<FileScanTask> {
  /**
   * 返回本组合任务包含的 {@link FileScanTask} 集合。
   *
   * @return 文件扫描任务集合
   */
  Collection<FileScanTask> files();

  /** 默认实现：直接返回 {@link #files()} 的结果，满足 {@link ScanTaskGroup#tasks()} 契约。 */
  @Override
  default Collection<FileScanTask> tasks() {
    return files();
  }

  /** 默认实现：返回自身，避免调用方再做类型判断。 */
  @Override
  default CombinedScanTask asCombinedScanTask() {
    return this;
  }
}
