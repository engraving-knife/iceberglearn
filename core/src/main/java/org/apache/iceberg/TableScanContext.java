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
package org.apache.iceberg;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.metrics.LoggingMetricsReporter;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.MetricsReporters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.ThreadPools;
import org.immutables.value.Value;

/**
 * 表扫描上下文，封装 {@link TableScan} 的可选参数集合。
 *
 * <p>所属模块：iceberg-core，定位为扫描配置的不可变值对象。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中承载扫描所需的全部可选项：目标快照 ID、行过滤表达式、列裁剪、投影 Schema、 分支、大小写敏感性、是否返回列统计、扫描区间等；
 *   <li>提供规划线程池与指标上报器的注入点；
 *   <li>通过 wither 方法返回修改后的不可变副本，支持链式配置。
 * </ul>
 *
 * <p>设计意图：使用 <a href="https://immutables.github.io/">Immutables</a> 框架生成不可变实现类 {@link
 * ImmutableTableScanContext}，保证扫描上下文在多线程环境下的安全共享。{@link Value.Default}
 * 标注的方法提供合理默认值（如行过滤为永真表达式、大小写敏感为 true），调用方仅需覆盖关心的字段。 {@code selectedColumns} 与 {@code
 * projectedSchema} 互斥，由 wither 方法中的前置条件保证。
 *
 * <p>上下游关系：由 {@code TableScan} 实现持有并传递给扫描规划逻辑；被 {@code Scan} API 的链式方法（如 {@code select}、{@code
 * filter}、{@code useBranch}）间接修改。
 */
@Value.Immutable
abstract class TableScanContext {

  /** 返回扫描目标快照 ID，为 null 表示使用表的当前快照。 */
  @Nullable
  public abstract Long snapshotId();

  /** 返回行过滤表达式，默认为永真表达式（即不过滤）。 */
  @Value.Default
  public Expression rowFilter() {
    return Expressions.alwaysTrue();
  }

  /** 返回是否忽略残余（residual）表达式，默认为 false。 */
  @Value.Default
  public boolean ignoreResiduals() {
    return false;
  }

  /** 返回是否区分大小写，默认为 true。 */
  @Value.Default
  public boolean caseSensitive() {
    return true;
  }

  /** 返回是否在扫描结果中携带列级统计信息，默认为 false。 */
  @Value.Default
  public boolean returnColumnStats() {
    return false;
  }

  /** 返回需读取的列名集合，为 null 表示读取全部列。 */
  @Nullable
  public abstract Collection<String> selectedColumns();

  /** 返回投影 Schema，为 null 表示不做投影。与 {@link #selectedColumns()} 互斥。 */
  @Nullable
  public abstract Schema projectedSchema();

  /** 返回扫描的运行时选项 Map，默认为空 Map。 */
  @Value.Default
  public Map<String, String> options() {
    return ImmutableMap.of();
  }

  /** 返回增量扫描的起始快照 ID（不含），为 null 表示非增量扫描。 */
  @Nullable
  public abstract Long fromSnapshotId();

  /** 返回起始快照是否包含在内，默认为 false（即排除起始快照）。 */
  @Value.Default
  public boolean fromSnapshotInclusive() {
    return false;
  }

  /** 返回增量扫描的结束快照 ID，为 null 表示无上界。 */
  @Nullable
  public abstract Long toSnapshotId();

  /** 返回用于扫描规划的线程池，默认为 Iceberg 共享 worker 线程池。 */
  @Value.Default
  public ExecutorService planExecutor() {
    return ThreadPools.getWorkerPool();
  }

  /** 派生属性：判断是否使用了自定义规划线程池（而非共享池）。 */
  @Value.Derived
  boolean planWithCustomizedExecutor() {
    return !planExecutor().equals(ThreadPools.getWorkerPool());
  }

  /** 返回扫描指标上报器，默认为日志上报器 {@link LoggingMetricsReporter}。 */
  @Value.Default
  public MetricsReporter metricsReporter() {
    return LoggingMetricsReporter.instance();
  }

  /** 返回扫描目标分支名，为 null 表示使用主分支。 */
  @Nullable
  public abstract String branch();

  /**
   * 返回一个将目标快照 ID 设置为指定值的不可变副本。
   *
   * @param scanSnapshotId 目标快照 ID
   * @return 修改后的上下文副本
   */
  TableScanContext useSnapshotId(Long scanSnapshotId) {
    return ImmutableTableScanContext.builder().from(this).snapshotId(scanSnapshotId).build();
  }

  /**
   * 返回一个将行过滤表达式设置为指定值的不可变副本。
   *
   * @param filter 行过滤表达式
   * @return 修改后的上下文副本
   */
  TableScanContext filterRows(Expression filter) {
    return ImmutableTableScanContext.builder().from(this).rowFilter(filter).build();
  }

  /**
   * 返回一个设置是否忽略残余表达式的不可变副本。
   *
   * @param shouldIgnoreResiduals 是否忽略残余
   * @return 修改后的上下文副本
   */
  TableScanContext ignoreResiduals(boolean shouldIgnoreResiduals) {
    return ImmutableTableScanContext.builder()
        .from(this)
        .ignoreResiduals(shouldIgnoreResiduals)
        .build();
  }

  /**
   * 返回一个设置大小写敏感性的不可变副本。
   *
   * @param isCaseSensitive 是否区分大小写
   * @return 修改后的上下文副本
   */
  TableScanContext setCaseSensitive(boolean isCaseSensitive) {
    return ImmutableTableScanContext.builder().from(this).caseSensitive(isCaseSensitive).build();
  }

  /**
   * 返回一个设置是否返回列统计的不可变副本。
   *
   * @param returnColumnStats 是否返回列统计
   * @return 修改后的上下文副本
   */
  TableScanContext shouldReturnColumnStats(boolean returnColumnStats) {
    return ImmutableTableScanContext.builder()
        .from(this)
        .returnColumnStats(returnColumnStats)
        .build();
  }

  /**
   * 返回一个设置列裁剪集合的不可变副本。
   *
   * <p>逻辑：若已设置投影 Schema 则抛出异常，保证列裁剪与投影互斥。
   *
   * @param columns 需读取的列名集合
   * @return 修改后的上下文副本
   */
  TableScanContext selectColumns(Collection<String> columns) {
    Preconditions.checkState(
        projectedSchema() == null, "Cannot select columns when projection schema is set");
    return ImmutableTableScanContext.builder().from(this).selectedColumns(columns).build();
  }

  /**
   * 返回一个设置投影 Schema 的不可变副本。
   *
   * <p>逻辑：若已设置列裁剪集合则抛出异常，保证投影与列裁剪互斥。
   *
   * @param schema 投影 Schema
   * @return 修改后的上下文副本
   */
  TableScanContext project(Schema schema) {
    Preconditions.checkState(
        selectedColumns() == null, "Cannot set projection schema when columns are selected");
    return ImmutableTableScanContext.builder().from(this).projectedSchema(schema).build();
  }

  /**
   * 返回一个追加单个运行时选项的不可变副本。
   *
   * @param property 选项键
   * @param value 选项值
   * @return 修改后的上下文副本
   */
  TableScanContext withOption(String property, String value) {
    return ImmutableTableScanContext.builder().from(this).putOptions(property, value).build();
  }

  /**
   * 返回一个设置增量扫描起始快照（排除该快照）的不可变副本。
   *
   * @param id 起始快照 ID
   * @return 修改后的上下文副本
   */
  TableScanContext fromSnapshotIdExclusive(long id) {
    return ImmutableTableScanContext.builder()
        .from(this)
        .fromSnapshotId(id)
        .fromSnapshotInclusive(false)
        .build();
  }

  /**
   * 返回一个设置增量扫描起始快照（包含该快照）的不可变副本。
   *
   * @param id 起始快照 ID
   * @return 修改后的上下文副本
   */
  TableScanContext fromSnapshotIdInclusive(long id) {
    return ImmutableTableScanContext.builder()
        .from(this)
        .fromSnapshotId(id)
        .fromSnapshotInclusive(true)
        .build();
  }

  /**
   * 返回一个设置增量扫描结束快照的不可变副本。
   *
   * @param id 结束快照 ID
   * @return 修改后的上下文副本
   */
  TableScanContext toSnapshotId(long id) {
    return ImmutableTableScanContext.builder().from(this).toSnapshotId(id).build();
  }

  /**
   * 返回一个设置自定义规划线程池的不可变副本。
   *
   * @param executor 规划线程池
   * @return 修改后的上下文副本
   */
  TableScanContext planWith(ExecutorService executor) {
    return ImmutableTableScanContext.builder().from(this).planExecutor(executor).build();
  }

  /**
   * 返回一个组合新指标上报器的不可变副本。
   *
   * <p>逻辑：若当前上报器为默认的 {@link LoggingMetricsReporter}，则直接替换为传入的 reporter； 否则使用 {@link
   * MetricsReporters#combine} 将两者组合，使多个上报器同时接收指标。
   *
   * @param reporter 待添加的指标上报器
   * @return 修改后的上下文副本
   */
  TableScanContext reportWith(MetricsReporter reporter) {
    return ImmutableTableScanContext.builder()
        .from(this)
        .metricsReporter(
            metricsReporter() instanceof LoggingMetricsReporter
                ? reporter
                : MetricsReporters.combine(metricsReporter(), reporter))
        .build();
  }

  /**
   * 返回一个设置扫描目标分支的不可变副本。
   *
   * @param ref 分支名
   * @return 修改后的上下文副本
   */
  TableScanContext useBranch(String ref) {
    return ImmutableTableScanContext.builder().from(this).branch(ref).build();
  }

  /** 返回一个所有字段均为默认值的空上下文实例。 */
  public static TableScanContext empty() {
    return ImmutableTableScanContext.builder().build();
  }
}
