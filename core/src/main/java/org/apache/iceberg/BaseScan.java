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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 扫描任务的抽象基类：为 {@link Scan} 系列实现提供公共上下文管理与不可变 fluent 配置能力。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 DataTableScan、元数据表扫描等实现的共同父类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link Table}、{@link Schema}、{@link TableScanContext} 三元组，作为扫描的不可变上下文。
 *   <li>预定义 manifest 读取所需的列集合（{@code SCAN_COLUMNS}、{@code STATS_COLUMNS}、 {@code
 *       DELETE_SCAN_COLUMNS} 等），用于按需投影减少 IO。
 *   <li>实现所有 fluent 配置方法（option/project/select/filter/caseSensitive/ignoreResiduals/ planWith 等），通过
 *       {@link #newRefinedScan} 返回新的不可变实例，符合 builder 模式。
 *   <li>提供 schema 懒投影（{@link #lazyColumnProjection}）：合并 selectedColumns、过滤器绑定字段、 显式 projectedSchema
 *       三种来源。
 * </ul>
 *
 * <p>设计意图：泛型 {@code ThisT} 让 fluent 方法返回子类自身类型；{@code T/G} 区分任务与任务组类型。 所有 fluent
 * 方法均不可变，调用即返回新扫描实例，便于多线程并发使用与链式复用。
 *
 * <p>上下游关系：被 {@link DataTableScan}、{@code BaseAllMetadataTableScan} 等扫描实现继承； 上下文来自 {@link
 * TableScanContext}，配置项来自 {@link TableProperties} 与扫描时 option。
 */
abstract class BaseScan<ThisT, T extends ScanTask, G extends ScanTaskGroup<T>>
    implements Scan<ThisT, T, G> {

  /** 数据文件扫描时 manifest 必读列（不含列统计）。 */
  protected static final List<String> SCAN_COLUMNS =
      ImmutableList.of(
          "snapshot_id",
          "file_path",
          "file_ordinal",
          "file_format",
          "block_size_in_bytes",
          "file_size_in_bytes",
          "record_count",
          "partition",
          "key_metadata",
          "split_offsets");

  /** 列级统计列集合，可与 SCAN_COLUMNS 合并使用以返回完整统计。 */
  private static final List<String> STATS_COLUMNS =
      ImmutableList.of(
          "value_counts",
          "null_value_counts",
          "nan_value_counts",
          "lower_bounds",
          "upper_bounds",
          "column_sizes");

  /** 数据文件扫描 + 列统计的合并列集合。 */
  protected static final List<String> SCAN_WITH_STATS_COLUMNS =
      ImmutableList.<String>builder().addAll(SCAN_COLUMNS).addAll(STATS_COLUMNS).build();

  /** 删除文件扫描时 manifest 必读列（含 content 与 equality_ids）。 */
  protected static final List<String> DELETE_SCAN_COLUMNS =
      ImmutableList.of(
          "snapshot_id",
          "content",
          "file_path",
          "file_ordinal",
          "file_format",
          "block_size_in_bytes",
          "file_size_in_bytes",
          "record_count",
          "partition",
          "key_metadata",
          "split_offsets",
          "equality_ids");

  /** 删除文件扫描 + 列统计的合并列集合。 */
  protected static final List<String> DELETE_SCAN_WITH_STATS_COLUMNS =
      ImmutableList.<String>builder().addAll(DELETE_SCAN_COLUMNS).addAll(STATS_COLUMNS).build();

  /** 是否使用 worker 线程池规划扫描任务（由系统配置决定）。 */
  protected static final boolean PLAN_SCANS_WITH_WORKER_POOL =
      SystemConfigs.SCAN_THREAD_POOL_ENABLED.value();

  private final Table table;
  private final Schema schema;
  private final TableScanContext context;

  /**
   * 构造方法。
   *
   * @param table 目标表
   * @param schema 表 schema
   * @param context 扫描上下文
   */
  protected BaseScan(Table table, Schema schema, TableScanContext context) {
    this.table = table;
    this.schema = schema;
    this.context = context;
  }

  /** 返回目标表。 */
  public Table table() {
    return table;
  }

  /** 返回表对应的 {@link FileIO}。 */
  protected FileIO io() {
    return table.io();
  }

  /** 返回构造时传入的表 schema。 */
  protected Schema tableSchema() {
    return schema;
  }

  /** 返回扫描上下文。 */
  protected TableScanContext context() {
    return context;
  }

  /** 返回扫描时传入的 option 映射。 */
  protected Map<String, String> options() {
    return context().options();
  }

  /** 根据是否需要返回列统计，返回对应的 manifest 必读列集合。 */
  protected List<String> scanColumns() {
    return context.returnColumnStats() ? SCAN_WITH_STATS_COLUMNS : SCAN_COLUMNS;
  }

  /** 是否需要返回列统计。 */
  protected boolean shouldReturnColumnStats() {
    return context().returnColumnStats();
  }

  /** 是否忽略残留过滤（residual filter）。 */
  protected boolean shouldIgnoreResiduals() {
    return context().ignoreResiduals();
  }

  /** 返回残留过滤表达式：若忽略残留则返回 alwaysTrue，否则返回当前 row filter。 */
  protected Expression residualFilter() {
    return shouldIgnoreResiduals() ? Expressions.alwaysTrue() : filter();
  }

  /** 是否使用 worker 线程池规划扫描（系统配置或显式指定其一即可）。 */
  protected boolean shouldPlanWithExecutor() {
    return PLAN_SCANS_WITH_WORKER_POOL || context().planWithCustomizedExecutor();
  }

  /** 返回规划扫描使用的线程池。 */
  protected ExecutorService planExecutor() {
    return context().planExecutor();
  }

  /**
   * 子类实现：基于新的表/schema/上下文构造一个新的细化扫描实例。
   *
   * <p>设计要点：所有 fluent 方法均委托本方法生成不可变新实例，保证扫描配置链式不互相干扰。
   */
  protected abstract ThisT newRefinedScan(
      Table newTable, Schema newSchema, TableScanContext newContext);

  /** 设置一个扫描 option（不可变，返回新实例）。 */
  @Override
  public ThisT option(String property, String value) {
    return newRefinedScan(table, schema, context.withOption(property, value));
  }

  /** 设置投影 schema（不可变，返回新实例）。 */
  @Override
  public ThisT project(Schema projectedSchema) {
    return newRefinedScan(table, schema, context.project(projectedSchema));
  }

  /** 设置是否大小写敏感（不可变，返回新实例）。 */
  @Override
  public ThisT caseSensitive(boolean caseSensitive) {
    return newRefinedScan(table, schema, context.setCaseSensitive(caseSensitive));
  }

  /** 返回当前是否大小写敏感。 */
  @Override
  public boolean isCaseSensitive() {
    return context().caseSensitive();
  }

  /** 标记本次扫描需要返回列统计（不可变，返回新实例）。 */
  @Override
  public ThisT includeColumnStats() {
    return newRefinedScan(table, schema, context.shouldReturnColumnStats(true));
  }

  /** 选择扫描列（不可变，返回新实例）。 */
  @Override
  public ThisT select(Collection<String> columns) {
    return newRefinedScan(table, schema, context.selectColumns(columns));
  }

  /** 添加行过滤表达式（与现有 filter 做 AND 合并，不可变，返回新实例）。 */
  @Override
  public ThisT filter(Expression expr) {
    return newRefinedScan(
        table, schema, context.filterRows(Expressions.and(context.rowFilter(), expr)));
  }

  /** 返回当前行过滤表达式。 */
  @Override
  public Expression filter() {
    return context().rowFilter();
  }

  /** 标记忽略残留过滤（不可变，返回新实例）。 */
  @Override
  public ThisT ignoreResiduals() {
    return newRefinedScan(table, schema, context.ignoreResiduals(true));
  }

  /** 指定规划扫描的线程池（不可变，返回新实例）。 */
  @Override
  public ThisT planWith(ExecutorService executorService) {
    return newRefinedScan(table, schema, context.planWith(executorService));
  }

  /** 返回最终用于投影读取的 schema：懒计算 selectedColumns 与 filter 字段并集，或使用显式 projectedSchema。 */
  @Override
  public Schema schema() {
    return lazyColumnProjection(context, schema);
  }

  /** 返回目标 split 大小（字节数）：扫描 option 优先，回退到表属性，再回退到默认值。 */
  @Override
  public long targetSplitSize() {
    long tableValue =
        PropertyUtil.propertyAsLong(
            table().properties(), TableProperties.SPLIT_SIZE, TableProperties.SPLIT_SIZE_DEFAULT);
    return PropertyUtil.propertyAsLong(context.options(), TableProperties.SPLIT_SIZE, tableValue);
  }

  /** 返回 split lookback（合并 split 时的回看数量）：option 优先，回退到表属性默认值。 */
  @Override
  public int splitLookback() {
    int tableValue =
        PropertyUtil.propertyAsInt(
            table().properties(),
            TableProperties.SPLIT_LOOKBACK,
            TableProperties.SPLIT_LOOKBACK_DEFAULT);
    return PropertyUtil.propertyAsInt(
        context.options(), TableProperties.SPLIT_LOOKBACK, tableValue);
  }

  /** 返回打开 split 文件的成本阈值：option 优先，回退到表属性默认值。 */
  @Override
  public long splitOpenFileCost() {
    long tableValue =
        PropertyUtil.propertyAsLong(
            table().properties(),
            TableProperties.SPLIT_OPEN_FILE_COST,
            TableProperties.SPLIT_OPEN_FILE_COST_DEFAULT);
    return PropertyUtil.propertyAsLong(
        context.options(), TableProperties.SPLIT_OPEN_FILE_COST, tableValue);
  }

  /**
   * 懒计算最终投影 schema。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若设置了 selectedColumns：把过滤器绑定字段 id 与选中列字段 id 取并集，再 project 出 schema； 保证过滤所需字段即使未被显式选中也会被读取。
   *   <li>否则若设置了 projectedSchema，则直接返回它。
   *   <li>否则返回表完整 schema。
   * </ul>
   *
   * @param context 扫描上下文
   * @param schema 表 schema
   * @return 最终用于投影读取的 schema
   */
  private static Schema lazyColumnProjection(TableScanContext context, Schema schema) {
    Collection<String> selectedColumns = context.selectedColumns();
    if (selectedColumns != null) {
      Set<Integer> requiredFieldIds = Sets.newHashSet();

      // all of the filter columns are required
      requiredFieldIds.addAll(
          Binder.boundReferences(
              schema.asStruct(),
              Collections.singletonList(context.rowFilter()),
              context.caseSensitive()));

      // all of the projection columns are required
      Set<Integer> selectedIds;
      if (context.caseSensitive()) {
        selectedIds = TypeUtil.getProjectedIds(schema.select(selectedColumns));
      } else {
        selectedIds = TypeUtil.getProjectedIds(schema.caseInsensitiveSelect(selectedColumns));
      }
      requiredFieldIds.addAll(selectedIds);

      return TypeUtil.project(schema, requiredFieldIds);

    } else if (context.projectedSchema() != null) {
      return context.projectedSchema();
    }

    return schema;
  }

  /** 设置 metrics reporter（不可变，返回新实例）。 */
  @Override
  public ThisT metricsReporter(MetricsReporter reporter) {
    return newRefinedScan(table, schema, context.reportWith(reporter));
  }
}
