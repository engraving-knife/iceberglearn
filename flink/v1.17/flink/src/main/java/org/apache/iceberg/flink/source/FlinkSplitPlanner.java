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
package org.apache.iceberg.flink.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.flink.annotation.Internal;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.IncrementalAppendScan;
import org.apache.iceberg.Scan;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.Tasks;

/**
 * 文件级说明：Flink Source 的 split 规划工具类。
 *
 * <p>所属模块：iceberg-flink（source 子包），提供静态方法规划 Iceberg 表的 split。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>planInputSplits：为旧版 InputFormat 生成 {@link FlinkInputSplit} 数组。
 *   <li>planIcebergSourceSplits：为 FLIP-27 新版 Source 生成 {@link IcebergSourceSplit} 列表。
 *   <li>planTasks：底层方法，执行 Iceberg 表扫描并合并为 {@link CombinedScanTask}。
 * </ul>
 *
 * <p>设计意图：统一封装 split 规划逻辑，支持本地性暴露（通过 blockLocations 获取主机名）。 使用线程池并行处理 split 的本地性信息获取。
 *
 * <p>上下游关系：被 {@link FlinkInputFormat} 和 FLIP-27 Source enumerator 调用； 内部委托 Iceberg core 的
 * TableScan/IncrementalAppendScan 执行文件扫描。
 */
@Internal
public class FlinkSplitPlanner {
  private FlinkSplitPlanner() {}

  /**
   * 为旧版 InputFormat 规划 input splits。
   *
   * <p>逻辑：扫描表获取 CombinedScanTask 列表 → 可选获取块位置信息 → 创建 FlinkInputSplit 数组。
   */
  static FlinkInputSplit[] planInputSplits(
      Table table, ScanContext context, ExecutorService workerPool) {
    try (CloseableIterable<CombinedScanTask> tasksIterable =
        planTasks(table, context, workerPool)) {
      List<CombinedScanTask> tasks = Lists.newArrayList(tasksIterable);
      FlinkInputSplit[] splits = new FlinkInputSplit[tasks.size()];
      boolean exposeLocality = context.exposeLocality();

      Tasks.range(tasks.size())
          .stopOnFailure()
          .executeWith(exposeLocality ? workerPool : null)
          .run(
              index -> {
                CombinedScanTask task = tasks.get(index);
                String[] hostnames = null;
                if (exposeLocality) {
                  hostnames = Util.blockLocations(table.io(), task);
                }
                splits[index] = new FlinkInputSplit(index, task, hostnames);
              });
      return splits;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to process tasks iterable", e);
    }
  }

  /**
   * 为 FLIP-27 新版 Source 规划 splits。
   *
   * @param table Iceberg 表
   * @param context 扫描上下文
   * @param workerPool 工作线程池
   * @return IcebergSourceSplit 列表
   */
  public static List<IcebergSourceSplit> planIcebergSourceSplits(
      Table table, ScanContext context, ExecutorService workerPool) {
    try (CloseableIterable<CombinedScanTask> tasksIterable =
        planTasks(table, context, workerPool)) {
      return Lists.newArrayList(
          CloseableIterable.transform(tasksIterable, IcebergSourceSplit::fromCombinedScanTask));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to process task iterable: ", e);
    }
  }

  static CloseableIterable<CombinedScanTask> planTasks(
      Table table, ScanContext context, ExecutorService workerPool) {
    ScanMode scanMode = checkScanMode(context);
    if (scanMode == ScanMode.INCREMENTAL_APPEND_SCAN) {
      IncrementalAppendScan scan = table.newIncrementalAppendScan();
      scan = refineScanWithBaseConfigs(scan, context, workerPool);

      if (context.startTag() != null) {
        Preconditions.checkArgument(
            table.snapshot(context.startTag()) != null,
            "Cannot find snapshot with tag %s",
            context.startTag());
        scan = scan.fromSnapshotExclusive(table.snapshot(context.startTag()).snapshotId());
      }

      if (context.startSnapshotId() != null) {
        Preconditions.checkArgument(
            context.startTag() == null, "START_SNAPSHOT_ID and START_TAG cannot both be set");
        scan = scan.fromSnapshotExclusive(context.startSnapshotId());
      }

      if (context.endTag() != null) {
        Preconditions.checkArgument(
            table.snapshot(context.endTag()) != null,
            "Cannot find snapshot with tag %s",
            context.endTag());
        scan = scan.toSnapshot(table.snapshot(context.endTag()).snapshotId());
      }

      if (context.endSnapshotId() != null) {
        Preconditions.checkArgument(
            context.endTag() == null, "END_SNAPSHOT_ID and END_TAG cannot both be set");
        scan = scan.toSnapshot(context.endSnapshotId());
      }

      return scan.planTasks();
    } else {
      TableScan scan = table.newScan();
      scan = refineScanWithBaseConfigs(scan, context, workerPool);

      if (context.snapshotId() != null) {
        scan = scan.useSnapshot(context.snapshotId());
      } else if (context.tag() != null) {
        scan = scan.useRef(context.tag());
      } else if (context.branch() != null) {
        scan = scan.useRef(context.branch());
      }

      if (context.asOfTimestamp() != null) {
        scan = scan.asOfTime(context.asOfTimestamp());
      }

      return scan.planTasks();
    }
  }

  @VisibleForTesting
  enum ScanMode {
    BATCH,
    INCREMENTAL_APPEND_SCAN
  }

  @VisibleForTesting
  static ScanMode checkScanMode(ScanContext context) {
    if (context.startSnapshotId() != null
        || context.endSnapshotId() != null
        || context.startTag() != null
        || context.endTag() != null) {
      return ScanMode.INCREMENTAL_APPEND_SCAN;
    } else {
      return ScanMode.BATCH;
    }
  }

  /** refine scan with common configs */
  private static <T extends Scan<T, FileScanTask, CombinedScanTask>> T refineScanWithBaseConfigs(
      T scan, ScanContext context, ExecutorService workerPool) {
    T refinedScan =
        scan.caseSensitive(context.caseSensitive()).project(context.project()).planWith(workerPool);

    if (context.includeColumnStats()) {
      refinedScan = refinedScan.includeColumnStats();
    }

    refinedScan = refinedScan.option(TableProperties.SPLIT_SIZE, context.splitSize().toString());

    refinedScan =
        refinedScan.option(TableProperties.SPLIT_LOOKBACK, context.splitLookback().toString());

    refinedScan =
        refinedScan.option(
            TableProperties.SPLIT_OPEN_FILE_COST, context.splitOpenFileCost().toString());

    if (context.filters() != null) {
      for (Expression filter : context.filters()) {
        refinedScan = refinedScan.filter(filter);
      }
    }

    return refinedScan;
  }
}
