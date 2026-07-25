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
package org.apache.iceberg.rest;

import java.util.Map;
import java.util.function.Supplier;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.rest.requests.ReportMetricsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：通过 REST 接口将指标上报到 Catalog 服务的 {@link MetricsReporter} 实现。
 *
 * <p>所属模块：iceberg-core（REST Catalog 指标上报组件）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link MetricsReporter} 接口，把扫描/提交产生的 {@link MetricsReport} 上报到 REST 服务端。
 *   <li>将指标包装为 {@link ReportMetricsRequest}，通过 {@link RESTClient#post} 发送到指定的 metrics 端点。
 *   <li>上报失败时仅打印告警日志，不影响主流程。
 * </ul>
 *
 * <p>设计意图：指标上报属于旁路操作，不应阻塞或影响数据读写主流程，因此采用"尽力而为"策略， 捕获所有异常并降级为日志，避免因指标服务不可用而影响任务。
 *
 * <p>上下游关系：由 {@link RESTSessionCatalog#metricsReporter} 创建并组合到 {@link
 * org.apache.iceberg.metrics.MetricsReporters#combine} 中；依赖 {@link RESTClient} 与 {@link
 * ErrorHandlers#defaultErrorHandler}。
 */
class RESTMetricsReporter implements MetricsReporter {
  private static final Logger LOG = LoggerFactory.getLogger(RESTMetricsReporter.class);

  private final RESTClient client;
  private final String metricsEndpoint;
  private final Supplier<Map<String, String>> headers;

  /**
   * 构造 REST 指标上报器。
   *
   * @param client 用于发送 POST 请求的 HTTP 客户端
   * @param metricsEndpoint 指标上报的 REST 路径（由 {@link ResourcePaths#metrics} 生成）
   * @param headers 请求头供应器，通常携带表级会话认证头
   */
  RESTMetricsReporter(
      RESTClient client, String metricsEndpoint, Supplier<Map<String, String>> headers) {
    this.client = client;
    this.metricsEndpoint = metricsEndpoint;
    this.headers = headers;
  }

  /**
   * 将指标报告上报到 REST 服务端。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 report 非空，为 null 时打印告警并直接返回。
   *   <li>将 report 包装为 {@link ReportMetricsRequest} 并以 POST 方式发送到 metricsEndpoint。
   *   <li>捕获所有异常并降级为日志告警，避免影响主流程。
   * </ol>
   *
   * @param report 待上报的指标报告，可为 null
   */
  @Override
  public void report(MetricsReport report) {
    if (null == report) {
      LOG.warn("Received invalid metrics report: null");
      return;
    }

    try {
      client.post(
          metricsEndpoint,
          ReportMetricsRequest.of(report),
          null,
          headers,
          ErrorHandlers.defaultErrorHandler());
    } catch (Exception e) {
      LOG.warn("Failed to report metrics to REST endpoint {}", metricsEndpoint, e);
    }
  }
}
