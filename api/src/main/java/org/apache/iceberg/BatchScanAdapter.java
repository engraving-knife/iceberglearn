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
import java.util.concurrent.ExecutorService;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.metrics.MetricsReporter;

/**
 * 适配器：把 {@link TableScan} 适配为 {@link BatchScan} 接口。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一个 {@link TableScan} 实例，把 {@link BatchScan} 的所有方法委托给它执行。
 *   <li>所有"配置类"方法都返回一个新的 {@code BatchScanAdapter}（包裹新的 TableScan）， 以保持 Scan 的不可变语义。
 * </ul>
 *
 * <p>设计意图：{@link TableScan} 与 {@link BatchScan} 接口高度相似但分属两条 API 线， 通过适配器模式让旧的 TableScan 实现可直接暴露为
 * BatchScan，避免重复实现扫描逻辑。 采用委托 + 不可变返回的方式，保证多次链式调用之间互不影响。
 *
 * <p>上下游关系：被 core 模块在 {@link TableScan#planTasks()} 等场景内部使用，将 TableScan 包装成 BatchScan 暴露给引擎。
 */
class BatchScanAdapter implements BatchScan {

  private final TableScan scan;

  BatchScanAdapter(TableScan scan) {
    this.scan = scan;
  }

  @Override
  public Table table() {
    return scan.table();
  }

  @Override
  public BatchScan useSnapshot(long snapshotId) {
    return new BatchScanAdapter(scan.useSnapshot(snapshotId));
  }

  @Override
  public BatchScan useRef(String ref) {
    return new BatchScanAdapter(scan.useRef(ref));
  }

  @Override
  public BatchScan asOfTime(long timestampMillis) {
    return new BatchScanAdapter(scan.asOfTime(timestampMillis));
  }

  @Override
  public Snapshot snapshot() {
    return scan.snapshot();
  }

  @Override
  public BatchScan option(String property, String value) {
    return new BatchScanAdapter(scan.option(property, value));
  }

  @Override
  public BatchScan project(Schema schema) {
    return new BatchScanAdapter(scan.project(schema));
  }

  @Override
  public BatchScan caseSensitive(boolean caseSensitive) {
    return new BatchScanAdapter(scan.caseSensitive(caseSensitive));
  }

  @Override
  public boolean isCaseSensitive() {
    return scan.isCaseSensitive();
  }

  @Override
  public BatchScan includeColumnStats() {
    return new BatchScanAdapter(scan.includeColumnStats());
  }

  @Override
  public BatchScan select(Collection<String> columns) {
    return new BatchScanAdapter(scan.select(columns));
  }

  @Override
  public BatchScan filter(Expression expr) {
    return new BatchScanAdapter(scan.filter(expr));
  }

  @Override
  public Expression filter() {
    return scan.filter();
  }

  @Override
  public BatchScan ignoreResiduals() {
    return new BatchScanAdapter(scan.ignoreResiduals());
  }

  @Override
  public BatchScan planWith(ExecutorService executorService) {
    return new BatchScanAdapter(scan.planWith(executorService));
  }

  @Override
  public Schema schema() {
    return scan.schema();
  }

  @SuppressWarnings("unchecked")
  @Override
  public CloseableIterable<ScanTask> planFiles() {
    CloseableIterable<? extends ScanTask> tasks = scan.planFiles();
    return (CloseableIterable<ScanTask>) tasks;
  }

  @SuppressWarnings("unchecked")
  @Override
  public CloseableIterable<ScanTaskGroup<ScanTask>> planTasks() {
    CloseableIterable<? extends ScanTaskGroup<? extends ScanTask>> taskGroups = scan.planTasks();
    return (CloseableIterable<ScanTaskGroup<ScanTask>>) taskGroups;
  }

  @Override
  public long targetSplitSize() {
    return scan.targetSplitSize();
  }

  @Override
  public int splitLookback() {
    return scan.splitLookback();
  }

  @Override
  public long splitOpenFileCost() {
    return scan.splitOpenFileCost();
  }

  @Override
  public BatchScan metricsReporter(MetricsReporter reporter) {
    return new BatchScanAdapter(scan.metricsReporter(reporter));
  }
}
