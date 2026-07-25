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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.events.Listeners;
import org.apache.iceberg.events.ScanEvent;
import org.apache.iceberg.expressions.ExpressionUtil;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.metrics.DefaultMetricsContext;
import org.apache.iceberg.metrics.ImmutableScanReport;
import org.apache.iceberg.metrics.ScanMetrics;
import org.apache.iceberg.metrics.ScanMetricsResult;
import org.apache.iceberg.metrics.ScanReport;
import org.apache.iceberg.metrics.Timer;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.SnapshotUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于特定快照扫描的抽象基类，为不同 BaseScan 实现提供共享代码。
 *
 * <p>所属模块：iceberg-core（扫描实现层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供快照选择能力（useSnapshot、useRef、asOfTime），支持时间旅行扫描；
 *   <li>在 {@link #planFiles()} 中记录扫描日志、通知 {@link ScanEvent} 监听器、收集扫描指标， 并在完成后生成 {@link ScanReport}
 *       上报。
 * </ul>
 *
 * <p>设计意图：把快照级扫描的通用流程（日志、事件、指标、报告）集中到基类， 子类只需实现 {@link #doPlanFiles} 产出具体任务。
 *
 * <p>上下游关系：被 {@link DataScan}、元数据表扫描等继承；底层使用 {@link SnapshotUtil} 解析快照与 schema。
 *
 * @param <ThisT> 实际扫描实现类类型
 * @param <T> 返回的 ScanTask 类型
 * @param <G> 返回的 ScanTaskGroup 类型
 */
public abstract class SnapshotScan<ThisT, T extends ScanTask, G extends ScanTaskGroup<T>>
    extends BaseScan<ThisT, T, G> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotScan.class);

  private ScanMetrics scanMetrics;

  /**
   * 构造快照扫描实例。
   *
   * @param table 表
   * @param schema 扫描 schema
   * @param context 扫描上下文
   */
  protected SnapshotScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
  }

  /** 返回上下文中设置的快照 id（可能为 null 表示使用当前快照）。 */
  protected Long snapshotId() {
    return context().snapshotId();
  }

  /**
   * 子类实现：实际产出扫描任务。
   *
   * @return 扫描任务集合
   */
  protected abstract CloseableIterable<T> doPlanFiles();

  /**
   * 时间旅行时是否使用快照对应的 schema（而非当前表 schema）。
   *
   * <p>设计要点：默认返回 false（使用当前 schema），子类（如 {@link DataScan}）可覆盖为 true。
   *
   * @return 是否使用快照 schema
   */
  protected boolean useSnapshotSchema() {
    return false;
  }

  /**
   * 返回扫描指标收集器，惰性初始化。
   *
   * @return {@link ScanMetrics}
   */
  protected ScanMetrics scanMetrics() {
    if (scanMetrics == null) {
      this.scanMetrics = ScanMetrics.of(new DefaultMetricsContext());
    }

    return scanMetrics;
  }

  /**
   * 指定按某个快照 id 扫描（时间旅行）。
   *
   * @param scanSnapshotId 快照 id
   * @return 当前扫描
   */
  public ThisT useSnapshot(long scanSnapshotId) {
    Preconditions.checkArgument(
        snapshotId() == null, "Cannot override snapshot, already set snapshot id=%s", snapshotId());
    Preconditions.checkArgument(
        table().snapshot(scanSnapshotId) != null,
        "Cannot find snapshot with ID %s",
        scanSnapshotId);
    Schema newSchema =
        useSnapshotSchema() ? SnapshotUtil.schemaFor(table(), scanSnapshotId) : tableSchema();
    TableScanContext newContext = context().useSnapshotId(scanSnapshotId);
    return newRefinedScan(table(), newSchema, newContext);
  }

  /**
   * 按引用名（分支或标签）扫描。
   *
   * <p>逻辑：若为 main 分支则直接使用当前 schema；否则解析引用对应的快照，并用该快照的 schema。
   *
   * @param name 引用名
   * @return 当前扫描
   */
  public ThisT useRef(String name) {
    if (SnapshotRef.MAIN_BRANCH.equals(name)) {
      return newRefinedScan(table(), tableSchema(), context());
    }

    Preconditions.checkArgument(
        snapshotId() == null, "Cannot override ref, already set snapshot id=%s", snapshotId());
    Snapshot snapshot = table().snapshot(name);
    Preconditions.checkArgument(snapshot != null, "Cannot find ref %s", name);
    TableScanContext newContext = context().useSnapshotId(snapshot.snapshotId());
    return newRefinedScan(table(), SnapshotUtil.schemaFor(table(), name), newContext);
  }

  /**
   * 按时间戳扫描：使用该时间戳之前最新的快照。
   *
   * @param timestampMillis 时间戳（毫秒）
   * @return 当前扫描
   */
  public ThisT asOfTime(long timestampMillis) {
    Preconditions.checkArgument(
        snapshotId() == null, "Cannot override snapshot, already set snapshot id=%s", snapshotId());

    return useSnapshot(SnapshotUtil.snapshotIdAsOfTime(table(), timestampMillis));
  }

  /**
   * 执行扫描文件计划。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>获取目标快照，若为空表则记录日志并返回空；
   *   <li>记录扫描信息日志，通知 {@link ScanEvent} 监听器；
   *   <li>启动计划耗时计时器；
   *   <li>调用 {@link #doPlanFiles} 产出任务，并在完成后停止计时、构造 {@link ScanReport} 上报。
   * </ol>
   *
   * @return 扫描任务集合
   */
  @Override
  public CloseableIterable<T> planFiles() {
    Snapshot snapshot = snapshot();

    if (snapshot == null) {
      LOG.info("Scanning empty table {}", table());
      return CloseableIterable.empty();
    }

    LOG.info(
        "Scanning table {} snapshot {} created at {} with filter {}",
        table(),
        snapshot.snapshotId(),
        DateTimeUtil.formatTimestampMillis(snapshot.timestampMillis()),
        ExpressionUtil.toSanitizedString(filter()));

    Listeners.notifyAll(new ScanEvent(table().name(), snapshot.snapshotId(), filter(), schema()));
    List<Integer> projectedFieldIds = Lists.newArrayList(TypeUtil.getProjectedIds(schema()));
    List<String> projectedFieldNames =
        projectedFieldIds.stream().map(schema()::findColumnName).collect(Collectors.toList());

    Timer.Timed planningDuration = scanMetrics().totalPlanningDuration().start();

    return CloseableIterable.whenComplete(
        doPlanFiles(),
        () -> {
          planningDuration.stop();
          Map<String, String> metadata = Maps.newHashMap(context().options());
          metadata.putAll(EnvironmentContext.get());
          ScanReport scanReport =
              ImmutableScanReport.builder()
                  .schemaId(schema().schemaId())
                  .projectedFieldIds(projectedFieldIds)
                  .projectedFieldNames(projectedFieldNames)
                  .tableName(table().name())
                  .snapshotId(snapshot.snapshotId())
                  .filter(
                      ExpressionUtil.sanitize(
                          schema().asStruct(), filter(), context().caseSensitive()))
                  .scanMetrics(ScanMetricsResult.fromScanMetrics(scanMetrics()))
                  .metadata(metadata)
                  .build();
          context().metricsReporter().report(scanReport);
        });
  }

  /**
   * 返回目标快照：若设置了 snapshotId 则取该快照，否则取当前快照。
   *
   * @return 目标快照
   */
  public Snapshot snapshot() {
    return snapshotId() != null ? table().snapshot(snapshotId()) : table().currentSnapshot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("table", table())
        .add("projection", schema().asStruct())
        .add("filter", filter())
        .add("ignoreResiduals", shouldIgnoreResiduals())
        .add("caseSensitive", isCaseSensitive())
        .toString();
  }
}
