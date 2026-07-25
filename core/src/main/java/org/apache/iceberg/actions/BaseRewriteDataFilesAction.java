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
package org.apache.iceberg.actions;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.RewriteFiles;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.ListMultimap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Multimaps;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.StructLikeWrapper;
import org.apache.iceberg.util.TableScanUtil;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 重写数据文件动作的抽象基类（旧版 Action API）。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>扫描表中数据文件，按分区分组并合并为 {@link CombinedScanTask}。
 *   <li>委托子类 {@link #rewriteDataForTasks(List)} 执行实际重写，再用 {@link RewriteFiles} 以删除旧文件、添加新文件的方式提交。
 *   <li>提供大小写敏感、分区规格、过滤表达式、目标文件大小、切分参数等配置入口。
 * </ul>
 *
 * <p>设计意图：作为旧版 {@code Action} API 的基类，把扫描、分组、提交的通用流程固定在基类， 子类只需实现引擎相关的文件写入（{@link
 * #rewriteDataForTasks(List)}）与 FileIO 提供。 提交失败时清理已写入的新文件，保证不留垃圾；{@link
 * CommitStateUnknownException} 因可能已成功而不清理。
 *
 * <p>上下游关系：继承 {@link BaseSnapshotUpdateAction}，被各引擎（Spark 等）的具体重写动作继承； 依赖 {@link Table}
 * 扫描与提交，{@link TableScanUtil} 做切分与任务规划。
 */
public abstract class BaseRewriteDataFilesAction<ThisT>
    extends BaseSnapshotUpdateAction<ThisT, RewriteDataFilesActionResult> {

  private static final Logger LOG = LoggerFactory.getLogger(BaseRewriteDataFilesAction.class);

  private final Table table;
  private final FileIO fileIO;
  private final EncryptionManager encryptionManager;
  private boolean caseSensitive;
  private PartitionSpec spec;
  private Expression filter;
  private long targetSizeInBytes;
  private int splitLookback;
  private long splitOpenFileCost;
  private boolean useStartingSequenceNumber;

  /**
   * 构造重写数据文件动作，从表属性初始化切分大小、目标文件大小、lookback 与打开文件成本等参数。
   *
   * @param table 待重写的目标表
   */
  protected BaseRewriteDataFilesAction(Table table) {
    this.table = table;
    this.spec = table.spec();
    this.filter = Expressions.alwaysTrue();
    this.caseSensitive = false;
    this.useStartingSequenceNumber = false;

    long splitSize =
        PropertyUtil.propertyAsLong(
            table.properties(), TableProperties.SPLIT_SIZE, TableProperties.SPLIT_SIZE_DEFAULT);
    long targetFileSize =
        PropertyUtil.propertyAsLong(
            table.properties(),
            TableProperties.WRITE_TARGET_FILE_SIZE_BYTES,
            TableProperties.WRITE_TARGET_FILE_SIZE_BYTES_DEFAULT);
    this.targetSizeInBytes = Math.min(splitSize, targetFileSize);

    this.splitLookback =
        PropertyUtil.propertyAsInt(
            table.properties(),
            TableProperties.SPLIT_LOOKBACK,
            TableProperties.SPLIT_LOOKBACK_DEFAULT);
    this.splitOpenFileCost =
        PropertyUtil.propertyAsLong(
            table.properties(),
            TableProperties.SPLIT_OPEN_FILE_COST,
            TableProperties.SPLIT_OPEN_FILE_COST_DEFAULT);

    this.fileIO = fileIO();
    this.encryptionManager = table.encryption();
  }

  /** 返回目标表。 */
  @Override
  protected Table table() {
    return table;
  }

  /** 返回重写输出使用的分区规格。 */
  protected PartitionSpec spec() {
    return spec;
  }

  /** 返回表的加密管理器。 */
  protected EncryptionManager encryptionManager() {
    return encryptionManager;
  }

  /** 返回是否大小写敏感。 */
  protected boolean caseSensitive() {
    return caseSensitive;
  }

  /**
   * 设置是否大小写敏感。
   *
   * @param newCaseSensitive 是否大小写敏感
   * @return 当前动作实例（链式调用）
   */
  public BaseRewriteDataFilesAction<ThisT> caseSensitive(boolean newCaseSensitive) {
    this.caseSensitive = newCaseSensitive;
    return this;
  }

  /**
   * 指定重写输出使用的分区规格 ID。
   *
   * @param specId 分区规格 ID
   * @return 当前动作实例（链式调用）
   */
  public BaseRewriteDataFilesAction<ThisT> outputSpecId(int specId) {
    Preconditions.checkArgument(table.specs().containsKey(specId), "Invalid spec id %s", specId);
    this.spec = table.specs().get(specId);
    return this;
  }

  /**
   * 指定重写后数据文件的目标大小（字节）。
   *
   * @param targetSize 目标文件大小（字节）
   * @return 当前动作实例（链式调用）
   */
  public BaseRewriteDataFilesAction<ThisT> targetSizeInBytes(long targetSize) {
    Preconditions.checkArgument(
        targetSize > 0L, "Invalid target rewrite data file size in bytes %s", targetSize);
    this.targetSizeInBytes = targetSize;
    return this;
  }

  /**
   * 指定把下一个文件分片打包进任务时考虑的箱（bin）数量。
   *
   * <p>增大该值通常使任务更均匀，但会带来额外规划开销。该配置可能重排文件区域顺序， 若需保留文件元数据的上下界顺序，可将 lookback 设为 1。
   *
   * @param lookback 打包时考虑的箱数量
   * @return 当前动作实例（链式调用）
   */
  public BaseRewriteDataFilesAction<ThisT> splitLookback(int lookback) {
    Preconditions.checkArgument(lookback > 0L, "Invalid split lookback %s", lookback);
    this.splitLookback = lookback;
    return this;
  }

  /**
   * 指定打包进单个箱时按多少字节计数的下限；当实际读取文件小于该阈值时按此值计数。
   *
   * <p>该配置控制每个任务合并的文件数，值越小合并越激进，默认 4MB。
   *
   * @param openFileCost 单个箱的最小计数字节
   * @return 当前动作实例（链式调用）
   */
  public BaseRewriteDataFilesAction<ThisT> splitOpenFileCost(long openFileCost) {
    Preconditions.checkArgument(openFileCost > 0L, "Invalid split openFileCost %s", openFileCost);
    this.splitOpenFileCost = openFileCost;
    return this;
  }

  /**
   * 传入行级表达式过滤待重写的数据文件。注意：可能包含匹配数据的所有文件都会被重写。
   *
   * @param expr 过滤表达式
   * @return 当前动作实例（链式调用）
   */
  public BaseRewriteDataFilesAction<ThisT> filter(Expression expr) {
    this.filter = Expressions.and(filter, expr);
    return this;
  }

  /**
   * 是否使用压缩开始时快照的序列号写入新数据文件，而非使用新产生快照的序列号。
   *
   * <p>这样可以避免与更高序列号的等值删除更新产生提交冲突。
   *
   * @param useStarting 为 true 时使用起始序列号
   * @return 当前动作实例（链式调用）
   */
  public BaseRewriteDataFilesAction<ThisT> useStartingSequenceNumber(boolean useStarting) {
    this.useStartingSequenceNumber = useStarting;
    return this;
  }

  /**
   * 执行数据文件重写。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>表无快照时直接返回空结果；
   *   <li>以当前快照扫描文件（应用过滤、大小写敏感设置），扫描完成后关闭迭代器；
   *   <li>按分区分组并仅保留文件数大于 1 的分区；
   *   <li>对每个分区用 {@link TableScanUtil} 切分文件并规划为 {@link CombinedScanTask}， 过滤出含多文件或部分文件扫描的任务；
   *   <li>委托 {@link #rewriteDataForTasks(List)} 写出新文件，收集新旧文件后通过 {@link #replaceDataFiles(Iterable,
   *       Iterable, long)} 提交。
   * </ul>
   *
   * @return 包含被删除与新增文件列表的结果
   */
  @Override
  public RewriteDataFilesActionResult execute() {
    CloseableIterable<FileScanTask> fileScanTasks = null;
    if (table.currentSnapshot() == null) {
      return RewriteDataFilesActionResult.empty();
    }

    long startingSnapshotId = table.currentSnapshot().snapshotId();
    try {
      fileScanTasks =
          table
              .newScan()
              .useSnapshot(startingSnapshotId)
              .caseSensitive(caseSensitive)
              .ignoreResiduals()
              .filter(filter)
              .planFiles();
    } finally {
      try {
        if (fileScanTasks != null) {
          fileScanTasks.close();
        }
      } catch (IOException ioe) {
        LOG.warn("Failed to close task iterable", ioe);
      }
    }

    Map<StructLikeWrapper, Collection<FileScanTask>> groupedTasks =
        groupTasksByPartition(fileScanTasks.iterator());
    Map<StructLikeWrapper, Collection<FileScanTask>> filteredGroupedTasks =
        groupedTasks.entrySet().stream()
            .filter(kv -> kv.getValue().size() > 1)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // Nothing to rewrite if there's only one DataFile in each partition.
    if (filteredGroupedTasks.isEmpty()) {
      return RewriteDataFilesActionResult.empty();
    }
    // Split and combine tasks under each partition
    List<CombinedScanTask> combinedScanTasks =
        filteredGroupedTasks.values().stream()
            .map(
                scanTasks -> {
                  CloseableIterable<FileScanTask> splitTasks =
                      TableScanUtil.splitFiles(
                          CloseableIterable.withNoopClose(scanTasks), targetSizeInBytes);
                  return TableScanUtil.planTasks(
                      splitTasks, targetSizeInBytes, splitLookback, splitOpenFileCost);
                })
            .flatMap(Streams::stream)
            .filter(task -> task.files().size() > 1 || isPartialFileScan(task))
            .collect(Collectors.toList());

    if (combinedScanTasks.isEmpty()) {
      return RewriteDataFilesActionResult.empty();
    }

    List<DataFile> addedDataFiles = rewriteDataForTasks(combinedScanTasks);
    List<DataFile> currentDataFiles =
        combinedScanTasks.stream()
            .flatMap(tasks -> tasks.files().stream().map(FileScanTask::file))
            .collect(Collectors.toList());
    replaceDataFiles(currentDataFiles, addedDataFiles, startingSnapshotId);

    return new RewriteDataFilesActionResult(currentDataFiles, addedDataFiles);
  }

  /**
   * 按分区对文件扫描任务分组。
   *
   * <p>逻辑：用 {@link StructLikeWrapper} 作为分区键，遍历任务迭代器将每个任务归入其分区对应的 多值映射；遍历完成后关闭迭代器。
   *
   * @param tasksIter 文件扫描任务迭代器
   * @return 分区键到任务集合的映射
   */
  private Map<StructLikeWrapper, Collection<FileScanTask>> groupTasksByPartition(
      CloseableIterator<FileScanTask> tasksIter) {
    ListMultimap<StructLikeWrapper, FileScanTask> tasksGroupedByPartition =
        Multimaps.newListMultimap(Maps.newHashMap(), Lists::newArrayList);
    StructLikeWrapper partitionWrapper = StructLikeWrapper.forType(spec.partitionType());
    try (CloseableIterator<FileScanTask> iterator = tasksIter) {
      iterator.forEachRemaining(
          task -> {
            StructLikeWrapper structLike = partitionWrapper.copyFor(task.file().partition());
            tasksGroupedByPartition.put(structLike, task);
          });
    } catch (IOException e) {
      LOG.warn("Failed to close task iterator", e);
    }
    return tasksGroupedByPartition.asMap();
  }

  /**
   * 用新文件替换旧文件并提交，失败时清理已写入的新文件。
   *
   * <p>逻辑：调用 {@link #doReplace(Iterable, Iterable, long)} 提交；遇到 {@link
   * CommitStateUnknownException}（可能已成功）时不清理直接抛出；遇到其他异常则删除所有 新增文件后重新抛出。
   *
   * @param deletedDataFiles 被删除的旧数据文件
   * @param addedDataFiles 新添加的数据文件
   * @param startingSnapshotId 起始快照 ID，用于乐观锁校验
   */
  private void replaceDataFiles(
      Iterable<DataFile> deletedDataFiles,
      Iterable<DataFile> addedDataFiles,
      long startingSnapshotId) {
    try {
      doReplace(deletedDataFiles, addedDataFiles, startingSnapshotId);
    } catch (CommitStateUnknownException e) {
      LOG.warn("Commit state unknown, cannot clean up files that may have been committed", e);
      throw e;
    } catch (Exception e) {
      LOG.warn("Failed to commit rewrite, cleaning up rewritten files", e);
      Tasks.foreach(Iterables.transform(addedDataFiles, f -> f.path().toString()))
          .noRetry()
          .suppressFailureWhenFinished()
          .onFailure((location, exc) -> LOG.warn("Failed to delete: {}", location, exc))
          .run(fileIO::deleteFile);
      throw e;
    }
  }

  /**
   * 实际执行文件替换提交。
   *
   * <p>逻辑：创建 {@link RewriteFiles} 并以起始快照校验；逐个添加待删除与待新增文件；若启用起始 序列号则设置数据序列号；最后通过 {@link
   * #commit(SnapshotUpdate)} 提交。
   *
   * @param deletedDataFiles 被删除的旧数据文件
   * @param addedDataFiles 新添加的数据文件
   * @param startingSnapshotId 起始快照 ID
   */
  @VisibleForTesting
  void doReplace(
      Iterable<DataFile> deletedDataFiles,
      Iterable<DataFile> addedDataFiles,
      long startingSnapshotId) {
    RewriteFiles rewriteFiles = table.newRewrite().validateFromSnapshot(startingSnapshotId);

    for (DataFile dataFile : deletedDataFiles) {
      rewriteFiles.deleteFile(dataFile);
    }

    for (DataFile dataFile : addedDataFiles) {
      rewriteFiles.addFile(dataFile);
    }

    if (useStartingSequenceNumber) {
      long sequenceNumber = table.snapshot(startingSnapshotId).sequenceNumber();
      rewriteFiles.dataSequenceNumber(sequenceNumber);
    }

    commit(rewriteFiles);
  }

  /**
   * 判断任务是否为单文件的部分扫描（即只扫描了文件的一部分）。
   *
   * <p>逻辑：仅含一个文件扫描任务且其扫描长度不等于文件总大小时视为部分扫描。
   *
   * @param task 组合扫描任务
   * @return 是否为部分文件扫描
   */
  private boolean isPartialFileScan(CombinedScanTask task) {
    if (task.files().size() == 1) {
      FileScanTask fileScanTask = task.files().iterator().next();
      return fileScanTask.file().fileSizeInBytes() != fileScanTask.length();
    } else {
      return false;
    }
  }

  /**
   * 返回用于读写文件的 {@link FileIO}，由子类提供。
   *
   * @return 文件 IO 实例
   */
  protected abstract FileIO fileIO();

  /**
   * 对一组组合扫描任务执行实际重写并返回新写入的数据文件列表，由子类（引擎相关实现）提供。
   *
   * @param combinedScanTask 待重写的组合扫描任务列表
   * @return 新写入的数据文件列表
   */
  protected abstract List<DataFile> rewriteDataForTasks(List<CombinedScanTask> combinedScanTask);
}
