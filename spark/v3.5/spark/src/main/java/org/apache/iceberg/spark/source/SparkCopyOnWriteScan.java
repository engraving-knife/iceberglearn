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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.iceberg.BatchScan;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.expressions.Expressions;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.connector.read.Statistics;
import org.apache.spark.sql.connector.read.SupportsRuntimeFiltering;
import org.apache.spark.sql.sources.Filter;
import org.apache.spark.sql.sources.In;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：COPY-ON-WRITE 扫描，构建受行级操作影响的数据扫描并附加更新计算。
 *
 * <p>设计意图：在普通扫描之上叠加 ComputeUpdateIterator，输出更新后的行。
 *
 * <p>上下游关系：由 SparkCopyOnWriteOperation 使用。
 */
class SparkCopyOnWriteScan extends SparkPartitioningAwareScan<FileScanTask>
    implements SupportsRuntimeFiltering {

  private static final Logger LOG = LoggerFactory.getLogger(SparkCopyOnWriteScan.class);

  private final Snapshot snapshot;
  private Set<String> filteredLocations = null;

  SparkCopyOnWriteScan(
      SparkSession spark,
      Table table,
      SparkReadConf readConf,
      Schema expectedSchema,
      List<Expression> filters,
      Supplier<ScanReport> scanReportSupplier) {
    this(spark, table, null, null, readConf, expectedSchema, filters, scanReportSupplier);
  }

  SparkCopyOnWriteScan(
      SparkSession spark,
      Table table,
      BatchScan scan,
      Snapshot snapshot,
      SparkReadConf readConf,
      Schema expectedSchema,
      List<Expression> filters,
      Supplier<ScanReport> scanReportSupplier) {
    super(spark, table, scan, readConf, expectedSchema, filters, scanReportSupplier);

    this.snapshot = snapshot;

    if (scan == null) {
      this.filteredLocations = Collections.emptySet();
    }
  }

  Long snapshotId() {
    return snapshot != null ? snapshot.snapshotId() : null;
  }
  /** 执行 taskJavaClass 相关操作。 */
  @Override
  protected Class<FileScanTask> taskJavaClass() {
    return FileScanTask.class;
  }
  /** 执行 estimateStatistics 相关操作。 */
  @Override
  public Statistics estimateStatistics() {
    return estimateStatistics(snapshot);
  }
  /** 执行 filterAttributes 相关操作。 */
  public NamedReference[] filterAttributes() {
    NamedReference file = Expressions.column(MetadataColumns.FILE_PATH.name());
    return new NamedReference[] {file};
  }
  /** 过滤。 */
  @Override
  public void filter(Filter[] filters) {
    Preconditions.checkState(
        Objects.equals(snapshotId(), currentSnapshotId()),
        "Runtime file filtering is not possible: the table has been concurrently modified. "
            + "Row-level operation scan snapshot ID: %s, current table snapshot ID: %s. "
            + "If an external process modifies the table, enable table caching in the catalog. "
            + "If multiple threads modify the table, use independent Spark sessions in each thread.",
        snapshotId(),
        currentSnapshotId());

    for (Filter filter : filters) {
      // Spark can only pass In filters at the moment
      if (filter instanceof In
          && ((In) filter).attribute().equalsIgnoreCase(MetadataColumns.FILE_PATH.name())) {
        In in = (In) filter;

        Set<String> fileLocations = Sets.newHashSet();
        for (Object value : in.values()) {
          fileLocations.add((String) value);
        }

        // Spark may call this multiple times for UPDATEs with subqueries
        // as such cases are rewritten using UNION and the same scan on both sides
        // so filter files only if it is beneficial
        if (filteredLocations == null || fileLocations.size() < filteredLocations.size()) {
          this.filteredLocations = fileLocations;
          List<FileScanTask> filteredTasks =
              tasks().stream()
                  .filter(file -> fileLocations.contains(file.file().path().toString()))
                  .collect(Collectors.toList());

          LOG.info(
              "{} of {} task(s) for table {} matched runtime file filter with {} location(s)",
              filteredTasks.size(),
              tasks().size(),
              table().name(),
              fileLocations.size());

          resetTasks(filteredTasks);
        }
      } else {
        LOG.warn("Unsupported runtime filter {}", filter);
      }
    }
  }
  /** 判断是否相等。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SparkCopyOnWriteScan that = (SparkCopyOnWriteScan) o;
    return table().name().equals(that.table().name())
        && readSchema().equals(that.readSchema()) // compare Spark schemas to ignore field ids
        && filterExpressions().toString().equals(that.filterExpressions().toString())
        && Objects.equals(snapshotId(), that.snapshotId())
        && Objects.equals(filteredLocations, that.filteredLocations);
  }
  /** 返回哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(
        table().name(),
        readSchema(),
        filterExpressions().toString(),
        snapshotId(),
        filteredLocations);
  }
  /** 返回字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "IcebergCopyOnWriteScan(table=%s, type=%s, filters=%s, caseSensitive=%s)",
        table(), expectedSchema().asStruct(), filterExpressions(), caseSensitive());
  }
  /** 执行 currentSnapshotId 相关操作。 */
  private Long currentSnapshotId() {
    Snapshot currentSnapshot = SnapshotUtil.latestSnapshot(table(), branch());
    return currentSnapshot != null ? currentSnapshot.snapshotId() : null;
  }
}
