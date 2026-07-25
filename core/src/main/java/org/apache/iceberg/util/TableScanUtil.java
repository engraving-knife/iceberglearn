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
package org.apache.iceberg.util;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.iceberg.BaseCombinedScanTask;
import org.apache.iceberg.BaseScanTaskGroup;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.FileContent;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MergeableScanTask;
import org.apache.iceberg.PartitionData;
import org.apache.iceberg.PartitionScanTask;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.SplittableScanTask;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.FluentIterable;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.math.LongMath;
import org.apache.iceberg.types.Types;

/**
 * 表扫描任务相关静态工具方法集合。
 *
 * <p>所属模块：iceberg-core（util 子包）。职责：提供扫描任务的切分（split）、合并（merge）、 分组（combine）与 delete 文件匹配等通用能力。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>切分大文件：按目标 split 大小把 {@link SplittableScanTask} 切成多个子任务，最小 16MB。
 *   <li>合并小任务：把同分区的小 {@link MergeableScanTask} 合并为一个 {@link ScanTaskGroup}，减少任务调度开销。
 *   <li>delete 匹配：把 equality/position delete 文件与对应数据文件关联。
 *   <li>无状态工具类：构造函数私有。
 * </ul>
 *
 * <p>上下游关系：被各引擎的扫描计划层调用；依赖 {@link FileScanTask}、{@link ContentFile} 等。
 */
public class TableScanUtil {

  private static final long MIN_SPLIT_SIZE = 16 * 1024 * 1024; // 16 MB

  /** 私有构造：工具类禁止实例化。 */
  private TableScanUtil() {}

  /**
   * 判断组合扫描任务中是否包含 delete 文件。
   *
   * @param task 组合扫描任务
   * @return true 表示任一文件任务包含 delete 文件
   */
  public static boolean hasDeletes(CombinedScanTask task) {
    return task.files().stream().anyMatch(TableScanUtil::hasDeletes);
  }

  /**
   * 判断组合扫描任务中是否包含 equality delete 文件。
   *
   * <p>临时方法：在 equality delete 向量化读取完全实现前用于判断是否需要回退到非向量化读取。
   *
   * @param task 组合扫描任务
   * @return true 表示任一文件任务包含 equality delete 文件
   */
  public static boolean hasEqDeletes(CombinedScanTask task) {
    return task.files().stream()
        .anyMatch(
            t ->
                t.deletes().stream()
                    .anyMatch(
                        deleteFile -> deleteFile.content().equals(FileContent.EQUALITY_DELETES)));
  }

  /**
   * 判断文件扫描任务是否包含 delete 文件。
   *
   * @param task 文件扫描任务
   * @return true 表示该任务有 delete 文件
   */
  public static boolean hasDeletes(FileScanTask task) {
    return !task.deletes().isEmpty();
  }

  /**
   * 按目标 split 大小切分文件扫描任务。
   *
   * @param tasks 待切分的文件扫描任务
   * @param splitSize 目标 split 大小（字节）
   * @return 切分后的文件扫描任务迭代器
   */
  public static CloseableIterable<FileScanTask> splitFiles(
      CloseableIterable<FileScanTask> tasks, long splitSize) {
    Preconditions.checkArgument(splitSize > 0, "Split size must be > 0: %s", splitSize);

    Iterable<FileScanTask> splitTasks =
        FluentIterable.from(tasks).transformAndConcat(input -> input.split(splitSize));
    // Capture manifests which can be closed after scan planning
    return CloseableIterable.combine(splitTasks, tasks);
  }

  /**
   * 基于切分后的文件任务规划组合扫描任务（bin-packing）。
   *
   * <p>权重函数考虑数据文件和 delete 文件的大小，避免因 delete 文件过小导致装箱不均衡。
   *
   * @param splitFiles 已切分的文件扫描任务
   * @param splitSize 目标 split 大小
   * @param lookback 装箱回溯窗口大小
   * @param openFileCost 每个文件的打开成本（字节）
   * @return 组合扫描任务迭代器
   */
  public static CloseableIterable<CombinedScanTask> planTasks(
      CloseableIterable<FileScanTask> splitFiles, long splitSize, int lookback, long openFileCost) {

    validatePlanningArguments(splitSize, lookback, openFileCost);

    // Check the size of delete file as well to avoid unbalanced bin-packing
    Function<FileScanTask, Long> weightFunc =
        file ->
            Math.max(
                file.length()
                    + file.deletes().stream().mapToLong(ContentFile::fileSizeInBytes).sum(),
                (1 + file.deletes().size()) * openFileCost);

    return CloseableIterable.transform(
        CloseableIterable.combine(
            new BinPacking.PackingIterable<>(splitFiles, splitSize, lookback, weightFunc, true),
            splitFiles),
        BaseCombinedScanTask::new);
  }

  public static <T extends ScanTask> List<ScanTaskGroup<T>> planTaskGroups(
      List<T> tasks, long splitSize, int lookback, long openFileCost) {
    return Lists.newArrayList(
        planTaskGroups(CloseableIterable.withNoopClose(tasks), splitSize, lookback, openFileCost));
  }

  @SuppressWarnings("unchecked")
  public static <T extends ScanTask> CloseableIterable<ScanTaskGroup<T>> planTaskGroups(
      CloseableIterable<T> tasks, long splitSize, int lookback, long openFileCost) {

    validatePlanningArguments(splitSize, lookback, openFileCost);

    // capture manifests which can be closed after scan planning
    CloseableIterable<T> splitTasks =
        CloseableIterable.combine(
            FluentIterable.from(tasks)
                .transformAndConcat(
                    task -> {
                      if (task instanceof SplittableScanTask<?>) {
                        return ((SplittableScanTask<? extends T>) task).split(splitSize);
                      } else {
                        return ImmutableList.of(task);
                      }
                    }),
            tasks);

    Function<T, Long> weightFunc =
        task -> Math.max(task.sizeBytes(), task.filesCount() * openFileCost);

    return CloseableIterable.transform(
        CloseableIterable.combine(
            new BinPacking.PackingIterable<>(splitTasks, splitSize, lookback, weightFunc, true),
            splitTasks),
        combinedTasks -> new BaseScanTaskGroup<>(mergeTasks(combinedTasks)));
  }

  @SuppressWarnings("unchecked")
  public static <T extends PartitionScanTask> List<ScanTaskGroup<T>> planTaskGroups(
      List<T> tasks,
      long splitSize,
      int lookback,
      long openFileCost,
      Types.StructType groupingKeyType) {

    validatePlanningArguments(splitSize, lookback, openFileCost);

    Function<T, Long> weightFunc =
        task -> Math.max(task.sizeBytes(), task.filesCount() * openFileCost);

    Map<Integer, StructProjection> groupingKeyProjectionsBySpec = Maps.newHashMap();

    // group tasks by grouping keys derived from their partition tuples
    StructLikeMap<List<T>> tasksByGroupingKey = StructLikeMap.create(groupingKeyType);

    for (T task : tasks) {
      PartitionSpec spec = task.spec();
      StructLike partition = task.partition();
      StructProjection groupingKeyProjection =
          groupingKeyProjectionsBySpec.computeIfAbsent(
              spec.specId(),
              specId -> StructProjection.create(spec.partitionType(), groupingKeyType));
      List<T> groupingKeyTasks =
          tasksByGroupingKey.computeIfAbsent(
              projectGroupingKey(groupingKeyProjection, groupingKeyType, partition),
              groupingKey -> Lists.newArrayList());
      if (task instanceof SplittableScanTask<?>) {
        ((SplittableScanTask<? extends T>) task).split(splitSize).forEach(groupingKeyTasks::add);
      } else {
        groupingKeyTasks.add(task);
      }
    }

    List<ScanTaskGroup<T>> taskGroups = Lists.newArrayList();

    for (Map.Entry<StructLike, List<T>> entry : tasksByGroupingKey.entrySet()) {
      StructLike groupingKey = entry.getKey();
      List<T> groupingKeyTasks = entry.getValue();
      Iterables.addAll(
          taskGroups,
          toTaskGroupIterable(groupingKey, groupingKeyTasks, splitSize, lookback, weightFunc));
    }

    return taskGroups;
  }

  /**
   * 把分区值投影为 grouping key 值。
   *
   * @param groupingKeyProjection 分区到 grouping key 的投影
   * @param groupingKeyType grouping key 类型
   * @param partition 分区值
   * @return 投影后的 grouping key
   */
  private static StructLike projectGroupingKey(
      StructProjection groupingKeyProjection,
      Types.StructType groupingKeyType,
      StructLike partition) {

    PartitionData groupingKey = new PartitionData(groupingKeyType);

    groupingKeyProjection.wrap(partition);

    for (int pos = 0; pos < groupingKeyProjection.size(); pos++) {
      Class<?> javaClass = groupingKey.getType(pos).typeId().javaClass();
      groupingKey.set(pos, groupingKeyProjection.get(pos, javaClass));
    }

    return groupingKey;
  }

  private static <T extends ScanTask> Iterable<ScanTaskGroup<T>> toTaskGroupIterable(
      StructLike groupingKey,
      Iterable<T> tasks,
      long splitSize,
      int lookback,
      Function<T, Long> weightFunc) {

    return Iterables.transform(
        new BinPacking.PackingIterable<>(tasks, splitSize, lookback, weightFunc, true),
        combinedTasks -> new BaseScanTaskGroup<>(groupingKey, mergeTasks(combinedTasks)));
  }

  @SuppressWarnings("unchecked")
  public static <T extends ScanTask> List<T> mergeTasks(List<T> tasks) {
    List<T> mergedTasks = Lists.newArrayList();

    T lastTask = null;

    for (T task : tasks) {
      if (lastTask != null) {
        if (lastTask instanceof MergeableScanTask<?>) {
          MergeableScanTask<? extends T> mergeableLastTask =
              (MergeableScanTask<? extends T>) lastTask;
          if (mergeableLastTask.canMerge(task)) {
            lastTask = mergeableLastTask.merge(task);
          } else {
            mergedTasks.add(lastTask);
            lastTask = task;
          }
        } else {
          mergedTasks.add(lastTask);
          lastTask = task;
        }
      } else {
        lastTask = task;
      }
    }

    if (lastTask != null) {
      mergedTasks.add(lastTask);
    }

    return mergedTasks;
  }

  /**
   * 根据扫描总大小与并行度调整 split 大小。
   *
   * <p>若配置的 splitSize 产生的 split 数不足以填满并行度，则适当缩小 split 大小 （但不低于 16MB），以提高并行度。
   *
   * @param scanSize 扫描总大小（字节）
   * @param parallelism 目标并行度
   * @param splitSize 配置的 split 大小
   * @return 调整后的 split 大小
   */
  public static long adjustSplitSize(long scanSize, int parallelism, long splitSize) {
    // use the configured split size if it produces at least one split per slot
    // otherwise, adjust the split size to target parallelism with a reasonable minimum
    // increasing the split size may cause expensive spills and is not done automatically
    long splitCount = LongMath.divide(scanSize, splitSize, RoundingMode.CEILING);
    long adjustedSplitSize = Math.max(scanSize / parallelism, Math.min(MIN_SPLIT_SIZE, splitSize));
    return splitCount < parallelism ? adjustedSplitSize : splitSize;
  }

  /**
   * 校验任务规划参数的合法性。
   *
   * @param splitSize split 大小，必须大于 0
   * @param lookback 回溯窗口，必须大于 0
   * @param openFileCost 文件打开成本，必须大于等于 0
   */
  private static void validatePlanningArguments(long splitSize, int lookback, long openFileCost) {
    Preconditions.checkArgument(splitSize > 0, "Split size must be > 0: %s", splitSize);
    Preconditions.checkArgument(lookback > 0, "Split planning lookback must be > 0: %s", lookback);
    Preconditions.checkArgument(openFileCost >= 0, "File open cost must be >= 0: %s", openFileCost);
  }
}
