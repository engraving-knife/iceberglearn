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
package org.apache.iceberg.flink.source.split;

/**
 * 文件级说明：Iceberg Source Split 的运行时状态。
 *
 * <p>所属模块：iceberg-flink（source/split 子包），跟踪 split 在 reader 端的处理状态。
 *
 * <p>职责：持有 {@link IcebergSourceSplit} 及其当前状态（{@link IcebergSourceSplitStatus}）， 用于在 enumerator 和
 * reader 之间同步 split 的处理进度。
 *
 * <p>设计意图：将 split 的不可变数据（split）与可变状态（status）分离， 避免修改 split 对象本身。状态包括 ASSIGNED/UNASSIGNED/COMPLETED
 * 等。
 *
 * <p>上下游关系：被 {@link SplitAssigner} 和 enumerator 使用，用于状态管理和恢复。
 */
public class IcebergSourceSplitState {
  private final IcebergSourceSplit split;
  private final IcebergSourceSplitStatus status;

  /**
   * 构造方法。
   *
   * @param split split 数据
   * @param status split 状态
   */
  public IcebergSourceSplitState(IcebergSourceSplit split, IcebergSourceSplitStatus status) {
    this.split = split;
    this.status = status;
  }

  public IcebergSourceSplit split() {
    return split;
  }

  public IcebergSourceSplitStatus status() {
    return status;
  }
}
