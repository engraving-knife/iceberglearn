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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MetricsReporter} 的默认实现：将收到的 {@link MetricsReport} 通过 SLF4J 输出到日志。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为指标上报的兜底实现，将指标报告以日志形式落盘，便于排查与审计。
 *   <li>提供单例访问，避免重复创建无状态实例。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用饿汉式单例（{@link #INSTANCE}），无状态、线程安全，无需同步开销。
 *   <li>仅做日志记录不做指标解析，保持实现极简，作为用户未配置自定义 reporter 时的默认行为。
 * </ul>
 *
 * <p>上下游关系：实现 {@link MetricsReporter}；被 core 模块在未指定自定义 reporter 时使用， 接收来自各操作生成的 {@link
 * MetricsReport}。
 */
public class LoggingMetricsReporter implements MetricsReporter {
  private static final Logger LOG = LoggerFactory.getLogger(LoggingMetricsReporter.class);
  private static final LoggingMetricsReporter INSTANCE = new LoggingMetricsReporter();

  /**
   * 返回全局唯一的 {@link LoggingMetricsReporter} 实例。
   *
   * @return 单例实例
   */
  public static LoggingMetricsReporter instance() {
    return INSTANCE;
  }

  /**
   * 将指标报告以 INFO 级别写入日志。
   *
   * @param report 待上报的指标报告
   */
  @Override
  public void report(MetricsReport report) {
    LOG.info("Received metrics report: {}", report);
  }
}
