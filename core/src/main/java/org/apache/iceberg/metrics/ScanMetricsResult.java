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

import javax.annotation.Nullable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.immutables.value.Value;

/**
 * {@link ScanMetrics} 的可序列化结果视图，承载扫描产生的各项最终度量结果。
 *
 * <p>所属模块：iceberg-core，度量包内用于"扫描后汇总"的只读数据结构。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>汇总一次扫描的计划耗时、结果文件数、扫描/跳过的清单与文件数、 索引/等值/位置删除文件数、文件总字节数等计数结果。
 *   <li>提供从运行时 {@link ScanMetrics} 装配结果的工厂方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用 Immutables 生成不可变接口实现，便于序列化与跨进程传输。
 *   <li>各字段允许为 null（{@code @Nullable}），以兼容不同扫描场景下未必全部存在的指标， 避免强制填充零值造成歧义。
 * </ul>
 *
 * <p>上下游关系：由 {@link #fromScanMetrics(ScanMetrics)} 从扫描度量构建； 被 {@link ScanReport} 持有并经 {@link
 * ScanMetricsResultParser} 序列化为 JSON 上报。
 */
@Value.Immutable
public interface ScanMetricsResult {
  /** 扫描计划总耗时结果，可能为 null。 */
  @Nullable
  TimerResult totalPlanningDuration();

  /** 结果数据文件计数结果，可能为 null。 */
  @Nullable
  CounterResult resultDataFiles();

  /** 结果删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult resultDeleteFiles();

  /** 数据清单总数计数结果，可能为 null。 */
  @Nullable
  CounterResult totalDataManifests();

  /** 删除清单总数计数结果，可能为 null。 */
  @Nullable
  CounterResult totalDeleteManifests();

  /** 已扫描数据清单计数结果，可能为 null。 */
  @Nullable
  CounterResult scannedDataManifests();

  /** 跳过的数据清单计数结果，可能为 null。 */
  @Nullable
  CounterResult skippedDataManifests();

  /** 数据文件总大小（字节）计数结果，可能为 null。 */
  @Nullable
  CounterResult totalFileSizeInBytes();

  /** 删除文件总大小（字节）计数结果，可能为 null。 */
  @Nullable
  CounterResult totalDeleteFileSizeInBytes();

  /** 跳过的数据文件计数结果，可能为 null。 */
  @Nullable
  CounterResult skippedDataFiles();

  /** 跳过的删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult skippedDeleteFiles();

  /** 已扫描删除清单计数结果，可能为 null。 */
  @Nullable
  CounterResult scannedDeleteManifests();

  /** 跳过的删除清单计数结果，可能为 null。 */
  @Nullable
  CounterResult skippedDeleteManifests();

  /** 已索引删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult indexedDeleteFiles();

  /** 等值删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult equalityDeleteFiles();

  /** 位置删除文件计数结果，可能为 null。 */
  @Nullable
  CounterResult positionalDeleteFiles();

  /**
   * 从运行时扫描度量装配出可序列化的 {@link ScanMetricsResult}。
   *
   * <p>逻辑：对 {@link ScanMetrics} 的每个计数器/计时器调用对应的 from 方法提取结果 （noop 指标会被过滤为 null），最终由
   * ImmutableBuilder 一次性构建。
   *
   * @param scanMetrics 扫描运行时度量，不能为 null
   * @return 装配完成的扫描度量结果
   */
  static ScanMetricsResult fromScanMetrics(ScanMetrics scanMetrics) {
    Preconditions.checkArgument(null != scanMetrics, "Invalid scan metrics: null");
    return ImmutableScanMetricsResult.builder()
        .totalPlanningDuration(TimerResult.fromTimer(scanMetrics.totalPlanningDuration()))
        .resultDataFiles(CounterResult.fromCounter(scanMetrics.resultDataFiles()))
        .resultDeleteFiles(CounterResult.fromCounter(scanMetrics.resultDeleteFiles()))
        .totalDataManifests(CounterResult.fromCounter(scanMetrics.totalDataManifests()))
        .totalDeleteManifests(CounterResult.fromCounter(scanMetrics.totalDeleteManifests()))
        .scannedDataManifests(CounterResult.fromCounter(scanMetrics.scannedDataManifests()))
        .skippedDataManifests(CounterResult.fromCounter(scanMetrics.skippedDataManifests()))
        .totalFileSizeInBytes(CounterResult.fromCounter(scanMetrics.totalFileSizeInBytes()))
        .totalDeleteFileSizeInBytes(
            CounterResult.fromCounter(scanMetrics.totalDeleteFileSizeInBytes()))
        .skippedDataFiles(CounterResult.fromCounter(scanMetrics.skippedDataFiles()))
        .skippedDeleteFiles(CounterResult.fromCounter(scanMetrics.skippedDeleteFiles()))
        .scannedDeleteManifests(CounterResult.fromCounter(scanMetrics.scannedDeleteManifests()))
        .skippedDeleteManifests(CounterResult.fromCounter(scanMetrics.skippedDeleteManifests()))
        .indexedDeleteFiles(CounterResult.fromCounter(scanMetrics.indexedDeleteFiles()))
        .equalityDeleteFiles(CounterResult.fromCounter(scanMetrics.equalityDeleteFiles()))
        .positionalDeleteFiles(CounterResult.fromCounter(scanMetrics.positionalDeleteFiles()))
        .build();
  }
}
