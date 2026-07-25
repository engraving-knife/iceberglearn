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
package org.apache.iceberg.spark.source;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionScanTask;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Scan;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionUtil;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Projections;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.SparkV2Filters;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.expressions.filter.Predicate;
import org.apache.spark.sql.connector.read.Statistics;
import org.apache.spark.sql.connector.read.SupportsRuntimeV2Filtering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spark 批量查询扫描：支持快照指定与运行时分区过滤的批量读取扫描。
 *
 * <p>所属模块：iceberg-spark（source 子包，Spark 数据源批量读取计划层）。
 *
 * <p>职责：基于 Iceberg 扫描构建批量读取任务，支持按 snapshotId、时间戳、tag/branch 选择快照，并实现 {@link
 * SupportsRuntimeV2Filtering} 在运行时接收广播的分区过滤条件 进一步裁剪任务。
 *
 * <p>设计意图：批量扫描通常在计划阶段应用静态过滤，但 join 场景下分区键值可能到运行时才确定， 因此实现运行时过滤接口，由 Spark 在运行时下发 IN
 * 过滤，扫描端按分区值二次裁剪任务， 避免无效数据读取。
 *
 * <p>上下游关系：继承 {@link SparkPartitioningAwareScan}，由 {@link SparkScanBuilder} 构造， 产出 {@link
 * SparkInputPartition} 供读取执行层消费。
 */
class SparkBatchQueryScan extends SparkPartitioningAwareScan<PartitionScanTask>
    implements SupportsRuntimeV2Filtering {

  private static final Logger LOG = LoggerFactory.getLogger(SparkBatchQueryScan.class);

  private final Long snapshotId;
  private final Long startSnapshotId;
  private final Long endSnapshotId;
  private final Long asOfTimestamp;
  private final String tag;
  private final List<Expression> runtimeFilterExpressions;

  SparkBatchQueryScan(
      SparkSession spark,
      Table table,
      Scan<?, ? extends ScanTask, ? extends ScanTaskGroup<?>> scan,
      SparkReadConf readConf,
      Schema expectedSchema,
      List<Expression> filters,
      Supplier<ScanReport> scanReportSupplier) {
    super(spark, table, scan, readConf, expectedSchema, filters, scanReportSupplier);

    this.snapshotId = readConf.snapshotId();
    this.startSnapshotId = readConf.startSnapshotId();
    this.endSnapshotId = readConf.endSnapshotId();
    this.asOfTimestamp = readConf.asOfTimestamp();
    this.tag = readConf.tag();
    this.runtimeFilterExpressions = Lists.newArrayList();
  }

  Long snapshotId() {
    return snapshotId;
  }
  /** 执行 taskJavaClass 相关操作。 */
  @Override
  protected Class<PartitionScanTask> taskJavaClass() {
    return PartitionScanTask.class;
  }

  /**
   * 返回可参与运行时过滤的分区字段引用。
   *
   * <p>设计要点：扫描已计划，运行时过滤只能作用于投影中存在的分区源字段； 优化器会在 join 中寻找与这些属性的等值条件作为广播过滤。
   */
  @Override
  public NamedReference[] filterAttributes() {
    Set<Integer> partitionFieldSourceIds = Sets.newHashSet();

    for (PartitionSpec spec : specs()) {
      for (PartitionField field : spec.fields()) {
        partitionFieldSourceIds.add(field.sourceId());
      }
    }

    Map<Integer, String> quotedNameById = SparkSchemaUtil.indexQuotedNameById(expectedSchema());

    // the optimizer will look for an equality condition with filter attributes in a join
    // as the scan has been already planned, filtering can only be done on projected attributes
    // that's why only partition source fields that are part of the read schema can be reported

    return partitionFieldSourceIds.stream()
        .filter(fieldId -> expectedSchema().findField(fieldId) != null)
        .map(fieldId -> Spark3Util.toNamedReference(quotedNameById.get(fieldId)))
        .toArray(NamedReference[]::new);
  }

  /**
   * 应用运行时分区过滤，裁剪不匹配的扫描任务。
   *
   * <p>逻辑：将谓词转换为 Iceberg 表达式后，按各分区规格做 inclusive 投影并构造求值器；
   * 对每个任务按其分区值求值，保留匹配任务；若过滤有效则重置任务集，并记录过滤表达式用于 equals/hashCode。
   *
   * @param predicates Spark 运行时下发的 V2 谓词数组
   */
  @Override
  public void filter(Predicate[] predicates) {
    Expression runtimeFilterExpr = convertRuntimeFilters(predicates);

    if (runtimeFilterExpr != Expressions.alwaysTrue()) {
      Map<Integer, Evaluator> evaluatorsBySpecId = Maps.newHashMap();

      for (PartitionSpec spec : specs()) {
        Expression inclusiveExpr =
            Projections.inclusive(spec, caseSensitive()).project(runtimeFilterExpr);
        Evaluator inclusive = new Evaluator(spec.partitionType(), inclusiveExpr);
        evaluatorsBySpecId.put(spec.specId(), inclusive);
      }

      List<PartitionScanTask> filteredTasks =
          tasks().stream()
              .filter(
                  task -> {
                    Evaluator evaluator = evaluatorsBySpecId.get(task.spec().specId());
                    return evaluator.eval(task.partition());
                  })
              .collect(Collectors.toList());

      LOG.info(
          "{} of {} task(s) for table {} matched runtime filter {}",
          filteredTasks.size(),
          tasks().size(),
          table().name(),
          ExpressionUtil.toSanitizedString(runtimeFilterExpr));

      // don't invalidate tasks if the runtime filter had no effect to avoid planning splits again
      if (filteredTasks.size() < tasks().size()) {
        resetTasks(filteredTasks);
      }

      // save the evaluated filter for equals/hashCode
      runtimeFilterExpressions.add(runtimeFilterExpr);
    }
  }

  /**
   * 将 Spark V2 谓词转换为绑定到期望 schema 的 Iceberg 运行时过滤表达式。
   *
   * <p>设计要点：当前 Spark 仅下发单属性的 IN 过滤，多属性会拆分为多个 IN 过滤； 绑定失败或不支持的谓词会被跳过并告警。
   */
  // at this moment, Spark can only pass IN filters for a single attribute
  // if there are multiple filter attributes, Spark will pass two separate IN filters
  private Expression convertRuntimeFilters(Predicate[] predicates) {
    Expression runtimeFilterExpr = Expressions.alwaysTrue();

    for (Predicate predicate : predicates) {
      Expression expr = SparkV2Filters.convert(predicate);
      if (expr != null) {
        try {
          Binder.bind(expectedSchema().asStruct(), expr, caseSensitive());
          runtimeFilterExpr = Expressions.and(runtimeFilterExpr, expr);
        } catch (ValidationException e) {
          LOG.warn("Failed to bind {} to expected schema, skipping runtime filter", expr, e);
        }
      } else {
        LOG.warn("Unsupported runtime filter {}", predicate);
      }
    }

    return runtimeFilterExpr;
  }

  /**
   * 估算扫描统计信息（行数、字节数），用于 Spark 优化器成本估算。
   *
   * <p>逻辑：按优先级解析目标快照——显式 snapshotId、asOfTimestamp、branch、tag， 最后回退到当前快照，并基于该快照估算。
   */
  @Override
  public Statistics estimateStatistics() {
    if (scan() == null) {
      return estimateStatistics(null);

    } else if (snapshotId != null) {
      Snapshot snapshot = table().snapshot(snapshotId);
      return estimateStatistics(snapshot);

    } else if (asOfTimestamp != null) {
      long snapshotIdAsOfTime = SnapshotUtil.snapshotIdAsOfTime(table(), asOfTimestamp);
      Snapshot snapshot = table().snapshot(snapshotIdAsOfTime);
      return estimateStatistics(snapshot);

    } else if (branch() != null) {
      Snapshot snapshot = table().snapshot(branch());
      return estimateStatistics(snapshot);

    } else if (tag != null) {
      Snapshot snapshot = table().snapshot(tag);
      return estimateStatistics(snapshot);

    } else {
      Snapshot snapshot = table().currentSnapshot();
      return estimateStatistics(snapshot);
    }
  }
  /** 判断是否相等。 */
  @Override
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SparkBatchQueryScan that = (SparkBatchQueryScan) o;
    return table().name().equals(that.table().name())
        && Objects.equals(branch(), that.branch())
        && readSchema().equals(that.readSchema()) // compare Spark schemas to ignore field ids
        && filterExpressions().toString().equals(that.filterExpressions().toString())
        && runtimeFilterExpressions.toString().equals(that.runtimeFilterExpressions.toString())
        && Objects.equals(snapshotId, that.snapshotId)
        && Objects.equals(startSnapshotId, that.startSnapshotId)
        && Objects.equals(endSnapshotId, that.endSnapshotId)
        && Objects.equals(asOfTimestamp, that.asOfTimestamp)
        && Objects.equals(tag, that.tag);
  }
  /** 返回哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(
        table().name(),
        branch(),
        readSchema(),
        filterExpressions().toString(),
        runtimeFilterExpressions.toString(),
        snapshotId,
        startSnapshotId,
        endSnapshotId,
        asOfTimestamp,
        tag);
  }
  /** 返回字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "IcebergScan(table=%s, branch=%s, type=%s, filters=%s, runtimeFilters=%s, caseSensitive=%s)",
        table(),
        branch(),
        expectedSchema().asStruct(),
        filterExpressions(),
        runtimeFilterExpressions,
        caseSensitive());
  }
}
