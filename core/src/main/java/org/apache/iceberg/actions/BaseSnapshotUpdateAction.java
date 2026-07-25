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
package org.apache.iceberg.actions;

import java.util.Map;
import org.apache.iceberg.SnapshotUpdate;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 会产生快照更新的动作的抽象基类。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在 {@link BaseAction} 基础上，维护一组快照摘要属性（summary）。
 *   <li>提供 {@link #commit(SnapshotUpdate)} 方法，将累积的摘要属性写入快照更新操作并提交。
 * </ul>
 *
 * <p>设计意图：子类在执行写动作时产生的 {@link SnapshotUpdate} 统一经由 {@link #commit} 提交， 保证调用方通过 {@link #set}
 * 设置的属性能被透传到快照 summary 中。{@code self()} 由子类实现， 返回具体子类型以支持链式调用。
 *
 * <p>上下游关系：继承 {@link BaseAction}，实现 {@link SnapshotUpdateAction}；被各具体写动作 （如 {@link
 * BaseRewriteDataFiles}、{@link BaseRewriteManifests} 等）继承。
 */
abstract class BaseSnapshotUpdateAction<ThisT, R> extends BaseAction<ThisT, R>
    implements SnapshotUpdateAction<ThisT, R> {

  private final Map<String, String> summary = Maps.newHashMap();

  /**
   * 返回当前动作实例自身，用于链式调用返回正确子类型。
   *
   * @return 当前动作实例（this，类型为 ThisT）
   */
  protected abstract ThisT self();

  /**
   * 设置快照摘要属性并返回当前动作实例以支持链式调用。
   *
   * @param property 属性名
   * @param value 属性值
   * @return 当前动作实例
   */
  @Override
  public ThisT set(String property, String value) {
    summary.put(property, value);
    return self();
  }

  /**
   * 将累积的摘要属性应用到快照更新操作并提交。
   *
   * <p>逻辑：遍历 summary 中所有键值对，逐个调用 {@link SnapshotUpdate#set} 写入， 随后执行 {@link
   * SnapshotUpdate#commit()} 完成提交。
   *
   * @param update 待提交的快照更新操作
   */
  protected void commit(SnapshotUpdate<?> update) {
    summary.forEach(update::set);
    update.commit();
  }
}
