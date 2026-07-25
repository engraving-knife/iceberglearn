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

import java.util.List;
import java.util.Map;
import org.apache.iceberg.expressions.Expression;
import org.immutables.value.Value;

/**
 * 扫描报告，承载一次表扫描的全部相关信息。
 *
 * <p>所属模块：iceberg-core，度量包中面向"扫描"事件的对外只读报告，实现 {@link MetricsReport}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>携带表名、快照 ID、过滤条件、schema ID、投影字段等扫描元信息。
 *   <li>携带该次扫描产生的 {@link ScanMetricsResult} 度量结果及附加 metadata。
 * </ul>
 *
 * <p>设计意图：采用 Immutables 生成不可变实现，便于序列化与上报；通过实现 {@link MetricsReport} 统一被 {@link MetricsReporter}
 * 处理，与 {@link CommitReport} 共享同一上报通道。
 *
 * <p>上下游关系：由扫描流程在扫描完成后构建；经 {@link ScanReportParser} 序列化为 JSON， 最终由 {@link MetricsReporter}（如 REST
 * 上报器、{@link InMemoryMetricsReporter}）消费。
 */
@Value.Immutable
public interface ScanReport extends MetricsReport {

  /** 返回 本次扫描所属表名。 */
  String tableName();

  /** 返回 本次扫描所基于的快照 ID。 */
  long snapshotId();

  /** 返回 本次扫描使用的过滤表达式。 */
  Expression filter();

  /** 返回 本次扫描使用的 schema ID。 */
  int schemaId();

  /** 返回 本次扫描投影的字段 ID 列表。 */
  List<Integer> projectedFieldIds();

  /** 返回 本次扫描投影的字段名称列表。 */
  List<String> projectedFieldNames();

  /** 返回 本次扫描的度量结果。 */
  ScanMetricsResult scanMetrics();

  /** 返回 附加元数据键值对，可能为空。 */
  Map<String, String> metadata();
}
