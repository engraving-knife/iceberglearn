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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Locale;
import org.apache.iceberg.metrics.CommitReport;
import org.apache.iceberg.metrics.CommitReportParser;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.metrics.ScanReportParser;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.rest.requests.ReportMetricsRequest.ReportType;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link ReportMetricsRequest} 的 JSON 序列化/反序列化解析器。
 *
 * <p>所属模块：iceberg-core，REST 请求解析层（{@code rest.requests} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link ReportMetricsRequest} 对象序列化为 REST 协议规定的 JSON 字符串；
 *   <li>将 JSON 字符串或 {@link JsonNode} 反序列化为 {@link ReportMetricsRequest} 对象；
 *   <li>根据 {@link ReportType}（扫描报告 / 提交报告）分派到对应的子解析器 （{@link ScanReportParser} / {@link
 *       CommitReportParser}）；
 *   <li>负责报告类型枚举与 JSON 字符串之间的格式转换（连字符 vs 下划线）。
 * </ul>
 *
 * <p>设计意图：采用工具类 + 静态方法模式，构造器私有化禁止实例化；JSON 序列化时 先写外层对象与类型字段，再委托子解析器以 withoutStartEnd 形式写入内嵌内容，
 * 避免重复写起始/结束标记，提升性能与代码复用。
 *
 * <p>上下游关系：被 REST Catalog 在 {@code /metrics} 接口上报指标时调用； 底层依赖 {@link JsonUtil}、{@link
 * ScanReportParser}、{@link CommitReportParser}。
 */
public class ReportMetricsRequestParser {

  private static final String REPORT_TYPE = "report-type";

  /** 私有构造器，禁止实例化该工具类。 */
  private ReportMetricsRequestParser() {}

  /**
   * 将请求序列化为紧凑格式 JSON 字符串。
   *
   * @param request 待序列化的指标上报请求
   * @return JSON 字符串
   */
  public static String toJson(ReportMetricsRequest request) {
    return toJson(request, false);
  }

  /**
   * 将请求序列化为 JSON 字符串，可指定是否美化输出。
   *
   * @param request 待序列化的指标上报请求
   * @param pretty 是否以缩进格式输出
   * @return JSON 字符串
   */
  public static String toJson(ReportMetricsRequest request, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(request, gen), pretty);
  }

  /**
   * 将请求直接写入给定的 {@link JsonGenerator}，用于流式输出场景。
   *
   * <p>逻辑：先断言请求非 null，写起始对象标记与 {@code report-type} 字段；随后根据 {@link ReportType} 分派到 {@link
   * ScanReportParser} 或 {@link CommitReportParser} 的 toJsonWithoutStartEnd 方法写入内嵌内容；最后写结束对象标记。
   *
   * @param request 待序列化的请求，不可为 null
   * @param gen Jackson 生成器
   * @throws IOException 写入失败时抛出
   */
  public static void toJson(ReportMetricsRequest request, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != request, "Invalid metrics request: null");

    gen.writeStartObject();

    gen.writeStringField(REPORT_TYPE, fromReportType(request.reportType()));

    if (ReportType.SCAN_REPORT == request.reportType()) {
      ScanReportParser.toJsonWithoutStartEnd((ScanReport) request.report(), gen);
    }

    if (ReportType.COMMIT_REPORT == request.reportType()) {
      CommitReportParser.toJsonWithoutStartEnd((CommitReport) request.report(), gen);
    }

    gen.writeEndObject();
  }

  /**
   * 将 {@link ReportType} 枚举转换为 JSON 中的字符串表示。
   *
   * <p>逻辑：取枚举名称，将下划线 {@code _} 替换为连字符 {@code -}，再转为小写。 例如 {@code SCAN_REPORT} 转为 {@code
   * scan-report}。
   *
   * @param reportType 报告类型枚举
   * @return JSON 字符串形式的报告类型
   */
  private static String fromReportType(ReportType reportType) {
    return reportType.name().replaceAll("_", "-").toLowerCase(Locale.ENGLISH);
  }

  /**
   * 将 JSON 字符串形式的报告类型转换为 {@link ReportType} 枚举。
   *
   * <p>逻辑：将连字符 {@code -} 替换为下划线 {@code _}，再委托 {@link ReportType#fromString(String)} 解析为枚举。
   *
   * @param type JSON 字符串形式的报告类型
   * @return 对应的 {@link ReportType} 枚举
   */
  private static ReportType toReportType(String type) {
    return ReportType.fromString(type.replaceAll("-", "_"));
  }

  /**
   * 从 JSON 字符串反序列化为 {@link ReportMetricsRequest}。
   *
   * @param json JSON 字符串
   * @return 反序列化得到的请求对象
   */
  public static ReportMetricsRequest fromJson(String json) {
    return JsonUtil.parse(json, ReportMetricsRequestParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 反序列化为 {@link ReportMetricsRequest}。
   *
   * <p>逻辑：先断言输入非 null 且为对象类型；读取 {@code report-type} 字段并转换为 {@link ReportType} 枚举；根据类型分派到 {@link
   * ScanReportParser} 或 {@link CommitReportParser} 解析内嵌报告；若类型未知则返回 {@link
   * ReportMetricsRequest#unknown()}。
   *
   * @param json JSON 节点，不可为 null 且必须为对象
   * @return 反序列化得到的请求对象
   */
  public static ReportMetricsRequest fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse metrics request from null object");
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse metrics request from non-object: %s", json);

    ReportType type = toReportType(JsonUtil.getString(REPORT_TYPE, json));
    if (ReportType.SCAN_REPORT == type) {
      return ImmutableReportMetricsRequest.builder()
          .reportType(type)
          .report(ScanReportParser.fromJson(json))
          .build();
    }

    if (ReportType.COMMIT_REPORT == type) {
      return ImmutableReportMetricsRequest.builder()
          .reportType(type)
          .report(CommitReportParser.fromJson(json))
          .build();
    }

    return ReportMetricsRequest.unknown();
  }
}
