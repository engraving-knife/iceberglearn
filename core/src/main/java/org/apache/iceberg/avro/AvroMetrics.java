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
package org.apache.iceberg.avro;

import org.apache.avro.io.DatumWriter;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.Schema;

/**
 * 文件级说明：Avro 文件 metrics 收集工具类。
 *
 * <p>所属模块：iceberg-core（avro 包，写入完成后产出文件级统计信息）。
 *
 * <p>职责：基于 {@link DatumWriter} 在写入过程中收集到的字段级 metrics，汇总为 Iceberg {@link Metrics}（记录数、列级上下界/Null
 * 计数等），供元数据管理与查询裁剪使用。
 *
 * <p>设计意图：当前实现为占位版本——仅返回记录数，列级统计留待后续在 {@link MetricsAwareDatumWriter} 落地后补全（见方法内 TODO）。这保证写入链路先行打通，
 * metrics 能力可逐步增强而不影响主流程。
 *
 * <p>上下游关系：由 {@link AvroFileAppender#metrics()} 在文件关闭后调用；产出的 {@link Metrics} 会写入 manifest
 * 文件，供读取侧做文件裁剪。
 */
public class AvroMetrics {

  private AvroMetrics() {}

  /**
   * 从写入器收集 metrics 并构造 {@link Metrics}。
   *
   * <p>逻辑：当前仅使用传入的记录数构造 Metrics，列级统计（bounds/null-counts 等）暂未填充。 后续若 datumWriter 为 {@link
   * MetricsAwareDatumWriter}，可从其 {@code metrics()} 流中 聚合列级统计。
   *
   * @param datumWriter 实际写入数据的 DatumWriter
   * @param schema Iceberg Schema
   * @param numRecords 已写入记录数
   * @param inputMetricsConfig metrics 采集配置
   * @return 文件级 Metrics
   */
  static Metrics fromWriter(
      DatumWriter<?> datumWriter,
      Schema schema,
      long numRecords,
      MetricsConfig inputMetricsConfig) {
    // TODO will populate in following PRs if datum writer is a MetricsAwareDatumWriter
    return new Metrics(numRecords, null, null, null, null);
  }
}
