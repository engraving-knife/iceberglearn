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
 * 增量扫描配置 API。
 *
 * <p>所属模块：iceberg-api（扫描接口层）。
 *
 * <p>职责：在 {@link Scan} 基础上扩展增量扫描语义，允许指定起止快照范围（含 inclusive / exclusive 两种起点语义），从而只扫描两个快照之间发生变更的数据。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>起点支持 inclusive 与 exclusive 两种语义，分别用于"包含该快照本身的变更"与 "从该快照之后开始"两种增量场景。
 *   <li>同时支持直接传快照 ID 与传 ref 名称（默认方法抛 UnsupportedOperationException， 由具体实现覆盖），便于 ref 化的表使用。
 *   <li>未配置起点时默认取终点快照的最老祖先（inclusive）；未配置终点时默认取当前表快照。
 * </ul>
 *
 * <p>上下游关系：继承 {@link Scan}；由 core 模块实现，被引擎用于增量拉取/CDC 等场景。
 *
 * @param <ThisT> 扫描具体类型（CRTP 风格，便于链式返回子类型）
 * @param <T> 扫描任务类型
 * @param <G> 扫描任务分组类型
 */
public interface IncrementalScan<ThisT, T extends ScanTask, G extends ScanTaskGroup<T>>
    extends Scan<ThisT, T, G> {
  /**
   * 指定从某个快照开始（inclusive）查找变更。
   *
   * <p>若未配置起点，默认取终点快照的最老祖先（inclusive）。
   *
   * @param fromSnapshotId 起点快照 ID（包含）
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若起点快照不是终点快照的祖先
   */
  ThisT fromSnapshotInclusive(long fromSnapshotId);

  /**
   * 指定从某个 ref 指向的快照开始（inclusive）查找变更。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由具体实现覆盖。
   *
   * @param ref 起点快照 ref 名称（包含）
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若起点快照不是终点快照的祖先
   */
  default ThisT fromSnapshotInclusive(String ref) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement fromSnapshotInclusive");
  }

  /**
   * 指定从某个快照之后（exclusive）开始查找变更。
   *
   * <p>若未配置起点，默认取终点快照的最老祖先（inclusive）。
   *
   * @param fromSnapshotId 起点快照 ID（不包含）
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若起点快照不是终点快照的祖先
   */
  ThisT fromSnapshotExclusive(long fromSnapshotId);

  /**
   * 指定从某个 ref 指向的快照之后（exclusive）开始查找变更。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由具体实现覆盖。
   *
   * @param ref 起点快照 ref 名称（不包含）
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若起点快照不是终点快照的祖先
   */
  default ThisT fromSnapshotExclusive(String ref) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement fromSnapshotExclusive");
  }

  /**
   * 指定扫描到某个快照为止（inclusive）。
   *
   * <p>若未配置终点，默认取当前表快照（inclusive）。
   *
   * @param toSnapshotId 终点快照 ID（包含）
   * @return this，便于链式调用
   */
  ThisT toSnapshot(long toSnapshotId);

  /**
   * 指定扫描到某个 ref 指向的快照为止（inclusive）。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由具体实现覆盖。
   *
   * @param ref 终点快照 ref 名称（包含）
   * @return this，便于链式调用
   */
  default ThisT toSnapshot(String ref) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement toSnapshot");
  }

  /**
   * 指定增量扫描所使用的分支。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由具体实现覆盖。
   *
   * @param branch 分支名
   * @return this，便于链式调用
   */
  default ThisT useBranch(String branch) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement useBranch");
  }
}
