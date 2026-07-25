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
package org.apache.iceberg.rest.requests;

import java.util.Locale;
import org.apache.iceberg.metrics.CommitReport;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.rest.RESTRequest;
import org.immutables.value.Value;

/**
 * 文件级说明：上报指标报告的 REST 请求模型（Immutable 接口）。
 *
 * <p>所属模块：iceberg-core（REST Catalog 请求模型层）。
 *
 * <p>职责：封装扫描或提交的指标报告（{@link org.apache.iceberg.metrics.MetricsReport}）， 包含报告类型与报告内容，用于向服务端上报操作统计信息。
 *
 * <p>设计意图：使用 Immutables 框架自动生成不可变实现；ReportType 枚举区分扫描报告与提交报告； 提供 {@link #of(MetricsReport)}
 * 工厂方法自动推断报告类型。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.rest.RESTMetricsReporter} 构造并发送。
 */
@Value.Immutable
public interface ReportMetricsRequest extends RESTRequest {

  enum ReportType {
    UNKNOWN,
    SCAN_REPORT,
    COMMIT_REPORT;

    static ReportType fromString(String reportType) {
      Preconditions.checkArgument(null != reportType, "Invalid report type: null");
      try {
        return ReportType.valueOf(reportType.toUpperCase(Locale.ENGLISH));
      } catch (IllegalArgumentException e) {
        return UNKNOWN;
      }
    }
  }

  ReportType reportType();

  MetricsReport report();

  @Override
  default void validate() {
    // nothing to do here as it's not possible to create a ReportMetricsRequest where
    // report/reportType is null
  }

  static ReportMetricsRequest of(MetricsReport report) {
    ReportType reportType = ReportType.UNKNOWN;
    if (report instanceof ScanReport) {
      reportType = ReportType.SCAN_REPORT;
    } else if (report instanceof CommitReport) {
      reportType = ReportType.COMMIT_REPORT;
    }

    return ImmutableReportMetricsRequest.builder().reportType(reportType).report(report).build();
  }

  static ReportMetricsRequest unknown() {
    return ImmutableReportMetricsRequest.builder()
        .reportType(ReportType.UNKNOWN)
        .report(new MetricsReport() {})
        .build();
  }
}
