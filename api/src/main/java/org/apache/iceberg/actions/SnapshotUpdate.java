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

import org.apache.iceberg.Snapshot;

/**
 * 会产出快照（snapshot）的动作。本接口聚合所有创建新 {@link Snapshot} 的动作的公共方法。
 *
 * <p>所属模块：iceberg-api。继承自 {@link Action}，是 {@link RewriteDataFiles}、 {@link
 * RewriteManifests}、{@link ConvertEqualityDeleteFiles}、{@link RewritePositionDeleteFiles}
 * 等会生成新快照的动作的共同父接口。
 *
 * <p>职责：在 {@link Action} 基础上，增加对产出快照 summary 属性的设置能力。
 *
 * <p>设计意图：将"产出快照"这一共性能力抽取到独立接口，使具体动作无需各自重复定义快照属性 配置方法，保持 API 一致性。
 *
 * <p>上下游关系：由具体动作接口继承；最终快照提交由引擎实现完成。
 *
 * @param <ThisT> 子类型的 Java API 类，由链式方法返回以保持类型
 * @param <R> 本动作执行后产出的结果类型
 */
public interface SnapshotUpdate<ThisT, R> extends Action<ThisT, R> {
  /**
   * 为本动作产出的快照设置一个 summary 属性。
   *
   * @param property 快照属性名
   * @param value 快照属性值
   * @return this，便于链式调用
   */
  ThisT snapshotProperty(String property, String value);
}
