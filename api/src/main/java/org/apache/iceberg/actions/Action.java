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

/**
 * 对表执行的某个动作（Action）。
 *
 * <p>所属模块：iceberg-api。该模块定义 Iceberg 的公共 API 契约，是 core 与各引擎集成模块 （如 Spark/Flink）共同依赖的稳定接口层。本接口处于
 * actions 体系的最顶层，是所有表维护动作 的根抽象。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义"动作"的通用骨架：通过链式配置（option/options）设定执行参数，再调用 {@link #execute()} 触发执行并产出结果 R。
 *   <li>为 {@link SnapshotUpdate}、{@link RewriteDataFiles}、{@link ExpireSnapshots} 等具体动作
 *       提供统一的泛型基类与链式 API 约定。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用 Builder/链式风格：ThisT 指向子类型自身，使各具体动作的配置方法能返回精确的子类型， 避免类型丢失，提升调用方体验。
 *   <li>option/options 提供默认抛 {@link UnsupportedOperationException} 实现：并非所有动作都
 *       支持自定义选项，未支持时安全降级而非静默忽略，便于及早暴露调用错误。
 *   <li>execute 不接受参数：所有配置通过链式方法完成，执行时一次性产出结果，便于在分布式引擎中 将"配置"与"执行"解耦。
 * </ul>
 *
 * <p>上下游关系：本接口被 {@link ActionsProvider} 间接暴露给使用方，具体实现由 iceberg-core 及各引擎模块（如 iceberg-spark）提供。
 *
 * @param <ThisT> 子类型的 Java API 类，由链式方法返回以保持类型
 * @param <R> 本动作执行后产出的结果类型
 */
public interface Action<ThisT, R> {
  /**
   * 为本动作追加一个额外的执行选项。
   *
   * <p>部分动作允许通过选项控制其执行细节（如并行度、目标文件大小等）。默认实现抛出 {@link UnsupportedOperationException}，表示当前动作不支持自定义选项。
   *
   * @param name 选项名
   * @param value 选项值
   * @return this，便于链式调用
   */
  default ThisT option(String name, String value) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement option");
  }

  /**
   * 为本动作追加一组额外的执行选项。
   *
   * <p>语义同 {@link #option(String, String)}，但一次性传入多个键值对。默认实现抛出 {@link
   * UnsupportedOperationException}，表示当前动作不支持自定义选项。
   *
   * @param options 选项键值对集合
   * @return this，便于链式调用
   */
  default ThisT options(Map<String, String> options) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement options");
  }

  /**
   * 执行本动作并返回结果。
   *
   * <p>调用前应通过链式方法完成全部配置。实现可在此处触发实际的（可能是分布式的）计算与提交。
   *
   * @return 动作执行结果
   */
  R execute();
}
