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

import java.util.concurrent.TimeUnit;
import org.immutables.value.Value;

/**
 * 扫描（scan）操作的度量指标集合。
 *
 * <p>所属模块：iceberg-core，位于度量（metrics）包内，负责表扫描/计划阶段的指标收集与上报。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载一次表扫描相关的全部运行时度量：计划耗时、结果文件数、扫描/跳过的清单与文件数、 索引的删除文件数、等值/位置删除文件数、文件总字节数等。
 *   <li>基于 {@link MetricsContext} 派生具体的 {@link Timer} 与 {@link Counter}， 使底层指标实现可替换（如对接引擎自带指标系统）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 Immutables 生成不可变实现，保证线程安全且可在并发扫描场景下安全共享。
 *   <li>通过 {@code @Value.Derived} 懒派生指标句柄，避免在构造期立即创建计数器， 使指标实例与具体 {@link MetricsContext} 解耦。
 *   <li>{@link #noop()} 返回基于空 MetricsContext 的实例，用于不关心指标的场景， 避免调用方处理 null。
 *   <li>度量名称常量集中声明，保证序列化键名与 {@link ScanMetricsResultParser} 一致。
 * </ul>
 *
 * <p>上下游关系：依赖 {@link MetricsContext}（定义于 iceberg-api）；被扫描计划流程 （如 {@code Scan}、{@code DataTableScan}
 * 等）使用，并由 {@link ScanMetricsResult} 收集结果后上报， 各计数器可由 {@link ScanMetricsUtil} 便捷递增。
 */
@Value.Immutable
public abstract class ScanMetrics {
  /** 扫描计划总耗时指标的名称键，单位为纳秒。 */
  public static final String TOTAL_PLANNING_DURATION = "total-planning-duration";
  /** 结果数据文件数的指标键名。 */
  public static final String RESULT_DATA_FILES = "result-data-files";
  /** 结果删除文件数的指标键名。 */
  public static final String RESULT_DELETE_FILES = "result-delete-files";
  /** 已扫描数据清单数的指标键名。 */
  public static final String SCANNED_DATA_MANIFESTS = "scanned-data-manifests";
  /** 已扫描删除清单数的指标键名。 */
  public static final String SCANNED_DELETE_MANIFESTS = "scanned-delete-manifests";
  /** 数据清单总数的指标键名。 */
  public static final String TOTAL_DATA_MANIFESTS = "total-data-manifests";
  /** 删除清单总数的指标键名。 */
  public static final String TOTAL_DELETE_MANIFESTS = "total-delete-manifests";
  /** 数据文件总大小（字节）的指标键名。 */
  public static final String TOTAL_FILE_SIZE_IN_BYTES = "total-file-size-in-bytes";
  /** 删除文件总大小（字节）的指标键名。 */
  public static final String TOTAL_DELETE_FILE_SIZE_IN_BYTES = "total-delete-file-size-in-bytes";
  /** 跳过的数据清单数的指标键名。 */
  public static final String SKIPPED_DATA_MANIFESTS = "skipped-data-manifests";
  /** 跳过的删除清单数的指标键名。 */
  public static final String SKIPPED_DELETE_MANIFESTS = "skipped-delete-manifests";
  /** 跳过的数据文件数的指标键名。 */
  public static final String SKIPPED_DATA_FILES = "skipped-data-files";
  /** 跳过的删除文件数的指标键名。 */
  public static final String SKIPPED_DELETE_FILES = "skipped-delete-files";
  /** 已索引删除文件数的指标键名。 */
  public static final String INDEXED_DELETE_FILES = "indexed-delete-files";
  /** 等值删除文件数的指标键名。 */
  public static final String EQUALITY_DELETE_FILES = "equality-delete-files";
  /** 位置删除文件数的指标键名。 */
  public static final String POSITIONAL_DELETE_FILES = "positional-delete-files";

  /**
   * 返回一个空实现（noop）的扫描度量实例，所有计数器/计时器均不产生实际记录。
   *
   * <p>设计意图：在不关心指标的场景下提供非 null 的占位对象，简化调用方判空逻辑。
   *
   * @return 基于 {@link MetricsContext#nullMetrics()} 的扫描度量实例
   */
  public static ScanMetrics noop() {
    return ScanMetrics.of(MetricsContext.nullMetrics());
  }

  /**
   * 返回该实例所依赖的 {@link MetricsContext}，所有指标句柄均通过它派生。
   *
   * @return 度量上下文
   */
  public abstract MetricsContext metricsContext();

  /**
   * 派生扫描计划总耗时的 {@link Timer}（单位：纳秒）。
   *
   * @return 扫描计划总耗时计时器
   */
  @Value.Derived
  public Timer totalPlanningDuration() {
    return metricsContext().timer(TOTAL_PLANNING_DURATION, TimeUnit.NANOSECONDS);
  }

  /** 返回 结果数据文件计数器。 */
  @Value.Derived
  public Counter resultDataFiles() {
    return metricsContext().counter(RESULT_DATA_FILES);
  }

  /** 返回 结果删除文件计数器。 */
  @Value.Derived
  public Counter resultDeleteFiles() {
    return metricsContext().counter(RESULT_DELETE_FILES);
  }

  /** 返回 已扫描数据清单计数器。 */
  @Value.Derived
  public Counter scannedDataManifests() {
    return metricsContext().counter(SCANNED_DATA_MANIFESTS);
  }

  /** 返回 数据清单总数计数器。 */
  @Value.Derived
  public Counter totalDataManifests() {
    return metricsContext().counter(TOTAL_DATA_MANIFESTS);
  }

  /** 返回 删除清单总数计数器。 */
  @Value.Derived
  public Counter totalDeleteManifests() {
    return metricsContext().counter(TOTAL_DELETE_MANIFESTS);
  }

  /** 返回 数据文件总大小（字节）计数器。 */
  @Value.Derived
  public Counter totalFileSizeInBytes() {
    return metricsContext().counter(TOTAL_FILE_SIZE_IN_BYTES, MetricsContext.Unit.BYTES);
  }

  /** 返回 删除文件总大小（字节）计数器。 */
  @Value.Derived
  public Counter totalDeleteFileSizeInBytes() {
    return metricsContext().counter(TOTAL_DELETE_FILE_SIZE_IN_BYTES, MetricsContext.Unit.BYTES);
  }

  /** 返回 跳过的数据清单计数器。 */
  @Value.Derived
  public Counter skippedDataManifests() {
    return metricsContext().counter(SKIPPED_DATA_MANIFESTS);
  }

  /** 返回 跳过的数据文件计数器。 */
  @Value.Derived
  public Counter skippedDataFiles() {
    return metricsContext().counter(SKIPPED_DATA_FILES);
  }

  /** 返回 跳过的删除文件计数器。 */
  @Value.Derived
  public Counter skippedDeleteFiles() {
    return metricsContext().counter(SKIPPED_DELETE_FILES);
  }

  /** 返回 已扫描删除清单计数器。 */
  @Value.Derived
  public Counter scannedDeleteManifests() {
    return metricsContext().counter(SCANNED_DELETE_MANIFESTS);
  }

  /** 返回 跳过的删除清单计数器。 */
  @Value.Derived
  public Counter skippedDeleteManifests() {
    return metricsContext().counter(SKIPPED_DELETE_MANIFESTS);
  }

  /** 返回 已索引删除文件计数器。 */
  @Value.Derived
  public Counter indexedDeleteFiles() {
    return metricsContext().counter(INDEXED_DELETE_FILES);
  }

  /** 返回 等值删除文件计数器。 */
  @Value.Derived
  public Counter equalityDeleteFiles() {
    return metricsContext().counter(EQUALITY_DELETE_FILES);
  }

  /** 返回 位置删除文件计数器。 */
  @Value.Derived
  public Counter positionalDeleteFiles() {
    return metricsContext().counter(POSITIONAL_DELETE_FILES);
  }

  /**
   * 基于给定 {@link MetricsContext} 构造一个不可变的 {@link ScanMetrics} 实例。
   *
   * @param metricsContext 度量上下文，决定指标的具体实现后端
   * @return 新构建的扫描度量实例
   */
  public static ScanMetrics of(MetricsContext metricsContext) {
    return ImmutableScanMetrics.builder().metricsContext(metricsContext).build();
  }
}
