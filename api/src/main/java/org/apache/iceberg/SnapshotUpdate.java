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

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 会生成新快照的表变更 API。本接口汇总所有创建新 {@link Snapshot} 的更新操作的公共方法。
 *
 * <p>所属模块：iceberg-api（表更新操作接口层）。
 *
 * <p>职责：为所有产生快照的更新操作（Append/Delete/Rewrite 等）提供统一的辅助能力， 包括设置快照 summary、自定义删除回调、仅暂存不提交、并行扫描
 * manifest、提交到分支等。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>通过泛型 {@code <ThisT>}（CRTP 风格）让链式方法返回具体子类型，避免调用方强转。
 *   <li>{@link #stageOnly()} 支持"暂存快照但不切换当前快照 ID"的 WAP（Write-Audit-Publish） 场景。
 *   <li>{@link #toBranch(String)} 等默认方法以抛 UnsupportedOperationException 的方式提供 可选能力，由具体实现覆盖。
 * </ul>
 *
 * <p>上下游关系：继承 {@link PendingUpdate}；被 {@link AppendFiles}、{@link DeleteFiles}、 {@link
 * RewriteManifests} 等接口继承；由 core 模块实现。
 *
 * @param <ThisT> 子 API 类型，用于链式方法返回
 */
public interface SnapshotUpdate<ThisT> extends PendingUpdate<Snapshot> {
  /**
   * 在本更新产生的快照中设置一个 summary 属性。
   *
   * @param property 属性名
   * @param value 属性值
   * @return this，便于链式调用
   */
  ThisT set(String property, String value);

  /**
   * 设置自定义文件删除回调，替代表默认的删除实现。
   *
   * @param deleteFunc 用于删除文件位置的消费者
   * @return this，便于链式调用
   */
  ThisT deleteWith(Consumer<String> deleteFunc);

  /**
   * 仅把快照暂存到表元数据中，不更新当前快照 ID。
   *
   * <p>设计意图：用于 WAP（Write-Audit-Publish）场景，先暂存待审计后再发布。
   *
   * @return this，便于链式调用
   */
  ThisT stageOnly();

  /**
   * 指定用于扫描 manifest 的执行器。未调用时使用默认 worker 池。
   *
   * @param executorService 提供的执行器
   * @return this，便于链式调用
   */
  ThisT scanManifestsWith(ExecutorService executorService);

  /**
   * 把本次操作提交到指定分支。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由支持分支的具体实现覆盖。
   *
   * @param branch 分支名（类型为 branch 的 SnapshotRef 名）
   * @return this，便于链式调用
   */
  default ThisT toBranch(String branch) {
    throw new UnsupportedOperationException(
        String.format(
            "Cannot commit to branch %s: %s does not support branch commits",
            branch, this.getClass().getName()));
  }
}
