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

/**
 * 会产生快照更新的动作接口。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：在 {@link Action} 基础上扩展，允许调用方以键值对形式设置快照摘要属性 （snapshot summary properties），这些属性最终会写入提交产生的快照
 * summary 中。
 *
 * <p>设计意图：区分"只读/分析型动作"与"会产生新快照的写动作"，后者需要支持自定义 summary， 以便追踪动作来源与参数。泛型 {@code ThisT} 保证 {@code set}
 * 可链式返回子类型。
 *
 * <p>上下游关系：继承 {@link Action}（iceberg-api），由 {@link BaseSnapshotUpdateAction} 实现，
 * 被各写动作（重写数据文件、重写清单等）使用。
 */
public interface SnapshotUpdateAction<ThisT, R> extends Action<ThisT, R> {
  /**
   * 设置将写入快照摘要的属性。
   *
   * @param property 属性名
   * @param value 属性值
   * @return 当前动作实例（链式调用）
   */
  ThisT set(String property, String value);
}
