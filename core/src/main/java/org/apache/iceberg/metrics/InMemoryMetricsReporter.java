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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 内存版度量报告器，将最近一次上报的 {@link MetricsReport} 保存在内存中供测试断言使用。
 *
 * <p>所属模块：iceberg-core，度量包中实现 {@link MetricsReporter} 的轻量级测试辅助类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link MetricsReporter#report(MetricsReport)}，把报告缓存到实例字段。
 *   <li>提供 {@link #scanReport()} 便捷地把缓存的报告作为 {@link ScanReport} 取出。
 * </ul>
 *
 * <p>设计意图：仅保留最后一次报告，适用于单元测试中验证扫描/提交度量是否被正确上报， 避免引入外部指标系统的依赖。非线程安全，仅供单线程测试场景使用。
 *
 * <p>上下游关系：实现 {@link MetricsReporter}；被测试代码注册为报告器，随后通过 {@link #scanReport()} 读取并断言度量内容。
 */
public class InMemoryMetricsReporter implements MetricsReporter {

  private MetricsReport metricsReport;

  /**
   * 缓存传入的度量报告，覆盖此前缓存的报告。
   *
   * @param report 度量报告
   */
  @Override
  public void report(MetricsReport report) {
    this.metricsReport = report;
  }

  /**
   * 以 {@link ScanReport} 形式返回缓存的度量报告。
   *
   * <p>设计要点：仅当缓存为 null 或确为 {@link ScanReport} 时才允许强转，否则抛出异常， 避免类型不匹配导致的静默错误。
   *
   * @return 缓存的扫描报告（可能为 null）
   * @throws IllegalArgumentException 当缓存的报告不是 {@link ScanReport} 时
   */
  public ScanReport scanReport() {
    Preconditions.checkArgument(
        metricsReport == null || metricsReport instanceof ScanReport,
        "Metrics report is not a scan report");
    return (ScanReport) metricsReport;
  }
}
