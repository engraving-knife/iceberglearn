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
 * 可合并扫描任务：可与其它扫描任务合并以减少任务数量的扫描任务。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：在 {@link ScanTask} 基础上提供 {@link #canMerge(ScanTask)} 与 {@link #merge(ScanTask)}
 * 两个方法，使扫描规划器可以把相邻、同源的小任务合并成较大的任务，从而降低调度与读取开销。
 *
 * <p>设计意图：通过"先询问 canMerge 再调用 merge"的两阶段协议，把合并合法性判断与 实际合并动作解耦，避免无效合并。泛型 {@code ThisT} 用于让 merge
 * 返回类型保持具体子类型。
 *
 * <p>上下游关系：被扫描规划器（如 core 模块的任务合并器）在 {@code planTasks} 阶段调用。
 *
 * @param <ThisT> 子类型自身，用于 merge 返回类型收敛
 */
public interface MergeableScanTask<ThisT> extends ScanTask {
  /**
   * 判断本任务是否可以与给定任务合并。
   *
   * @param other 另一个任务
   * @return 是否可合并
   */
  boolean canMerge(ScanTask other);

  /**
   * 把本任务与给定任务合并，返回合并后的新任务。
   *
   * <p>本方法仅会在 {@link #canMerge(ScanTask)} 返回 true 时被调用。
   *
   * @param other 另一个任务
   * @return 合并后的新任务
   */
  ThisT merge(ScanTask other);
}
