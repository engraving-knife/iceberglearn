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
package org.apache.iceberg.flink.source.enumerator;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.iceberg.flink.source.assigner.SplitAssigner;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;

/**
 * 批量执行场景下的静态枚举器，在启动时一次性枚举所有 split。
 *
 * <p>所属模块：iceberg-flink（source enumerator 侧），继承 {@link AbstractIcebergEnumerator}。
 *
 * <p>职责：持有预规划好的 split assigner，不等待更多 split；snapshotState 时持久化 assigner 状态。
 *
 * <p>设计意图：批量模式无需持续发现新 split，{@link #shouldWaitForMoreSplits()} 返回 false 通知 reader 无后续 split。
 *
 * <p>上下游关系：被 {@link IcebergSource} 批量模式创建。
 */
@Internal
public class StaticIcebergEnumerator extends AbstractIcebergEnumerator {
  private final SplitAssigner assigner;

  /**
   * 构造静态枚举器。
   *
   * @param enumeratorContext 枚举器上下文
   * @param assigner 已持有 split 的 assigner
   */
  public StaticIcebergEnumerator(
      SplitEnumeratorContext<IcebergSourceSplit> enumeratorContext, SplitAssigner assigner) {
    super(enumeratorContext, assigner);
    this.assigner = assigner;
  }

  /** 启动枚举器，委托父类。 */
  @Override
  public void start() {
    super.start();
  }

  /** 批量模式不等待更多 split，返回 false。 */
  @Override
  protected boolean shouldWaitForMoreSplits() {
    return false;
  }

  /** 快照状态：无连续位置，仅持久化 assigner 状态与空 reader 数组。 */
  @Override
  public IcebergEnumeratorState snapshotState(long checkpointId) {
    return new IcebergEnumeratorState(null, assigner.state(), new int[0]);
  }
}
