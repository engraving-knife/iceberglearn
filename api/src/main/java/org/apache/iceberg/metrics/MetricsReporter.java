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
package org.apache.iceberg.metrics;

import java.util.Map;

/**
 * 指标上报器接口：定义将针对表操作的指标报告上报出去的基础 API。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：接收 {@link MetricsReport} 并将其转发到日志、外部监控系统或引擎原生指标系统。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>标记为 {@link FunctionalInterface}：核心仅需实现 {@link #report(MetricsReport)}， 便于以 lambda 形式提供简单实现。
 *   <li>自定义实现必须提供无参构造器（由框架反射实例化），随后通过 {@link #initialize(Map)} 完成配置注入，分离实例化与初始化两阶段。
 * </ul>
 *
 * <p>上下游关系：被 core 模块持有；接收来自各操作的 {@link MetricsReport} 并上报；默认实现为 {@link LoggingMetricsReporter}。
 */
@FunctionalInterface
public interface MetricsReporter {

  /**
   * 初始化上报器。
   *
   * <p>自定义实现必须有无参构造器（先被调用），随后本方法被调用以完成初始化。
   *
   * @param properties 初始化属性
   */
  default void initialize(Map<String, String> properties) {}

  /**
   * 上报一次操作的指标报告，表示该操作已完成。
   *
   * @param report 待上报的 {@link MetricsReport}
   */
  void report(MetricsReport report);
}
