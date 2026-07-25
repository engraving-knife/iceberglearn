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
package org.apache.iceberg.spark.actions;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.MetadataTableUtils;
import org.apache.iceberg.Partitioning;
import org.apache.iceberg.PositionDeletesScanTask;
import org.apache.iceberg.PositionDeletesTable.PositionDeletesBatchScan;
import org.apache.iceberg.RewriteJobOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.ImmutableRewritePositionDeleteFiles;
import org.apache.iceberg.actions.RewritePositionDeleteFiles;
import org.apache.iceberg.actions.RewritePositionDeletesCommitManager;
import org.apache.iceberg.actions.RewritePositionDeletesCommitManager.CommitService;
import org.apache.iceberg.actions.RewritePositionDeletesGroup;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Queues;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.relocated.com.google.common.math.IntMath;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.MoreExecutors;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.PartitionUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.StructLikeMap;
import org.apache.iceberg.util.Tasks;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Spark 的位置删除文件重写动作：压缩并重写位置删除（position delete）文件。
 *
 * <p>所属模块：iceberg-spark（actions 子包，提供表维护动作的 Spark 实现）。
 *
 * <p>职责：扫描表中的位置删除文件，按分区分组并使用装箱策略重写，使删除文件更紧凑、 减少小文件，并在事务中提交重写结果。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>支持过滤条件限定重写范围；支持部分进度（partial progress）模式，将重写拆分为 多次较小提交，单次提交冲突不影响其他已成功的提交。
 *   <li>使用文件组（file group）作为重写与提交的基本单元，便于并发控制与结果统计。
 *   <li>无部分进度时，任一文件组重写失败即整体回滚清理，保证原子性。
 * </ul>
 *
 * <p>上下游关系：实现 {@link org.apache.iceberg.actions.RewritePositionDeleteFiles} 接口， 继承 {@link
 * BaseSnapshotUpdateSparkAction}；内部委托 {@link SparkBinPackPositionDeletesRewriter} 执行实际重写，由用户通过
 * Spark 过程或 API 触发。
 */
public class RewritePositionDeleteFilesSparkAction
    extends BaseSnapshotUpdateSparkAction<RewritePositionDeleteFilesSparkAction>
    implements RewritePositionDeleteFiles {

  private static final Logger LOG =
      LoggerFactory.getLogger(RewritePositionDeleteFilesSparkAction.class);
  private static final Set<String> VALID_OPTIONS =
      ImmutableSet.of(
          MAX_CONCURRENT_FILE_GROUP_REWRITES,
          PARTIAL_PROGRESS_ENABLED,
          PARTIAL_PROGRESS_MAX_COMMITS,
          REWRITE_JOB_ORDER);
  private static final Result EMPTY_RESULT =
      ImmutableRewritePositionDeleteFiles.Result.builder().build();

  private final Table table;
  private final SparkBinPackPositionDeletesRewriter rewriter;
  private Expression filter = Expressions.alwaysTrue();

  private int maxConcurrentFileGroupRewrites;
  private int maxCommits;
  private boolean partialProgressEnabled;
  private RewriteJobOrder rewriteJobOrder;

  RewritePositionDeleteFilesSparkAction(SparkSession spark, Table table) {
    super(spark);
    this.table = table;
    this.rewriter = new SparkBinPackPositionDeletesRewriter(spark(), table);
  }
  /** 执行 self 相关操作。 */
  @Override
  protected RewritePositionDeleteFilesSparkAction self() {
    return this;
  }
  /** 过滤。 */
  @Override
  public RewritePositionDeleteFilesSparkAction filter(Expression expression) {
    filter = Expressions.and(filter, expression);
    return this;
  }

  /**
   * 执行位置删除文件重写动作。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>空表或无可重写内容时直接返回空结果。
   *   <li>校验并初始化选项。
   *   <li>规划文件组并构建执行上下文，统计总组数；为 0 则返回空结果。
   *   <li>根据是否启用部分进度，选择 {@link #doExecuteWithPartialProgress} 或 {@link #doExecute}。
   * </ol>
   *
   * @return 重写结果，含每个文件组的重写统计
   */
  @Override
  public RewritePositionDeleteFiles.Result execute() {
    if (table.currentSnapshot() == null) {
      LOG.info("Nothing found to rewrite in empty table {}", table.name());
      return EMPTY_RESULT;
    }

    validateAndInitOptions();

    StructLikeMap<List<List<PositionDeletesScanTask>>> fileGroupsByPartition = planFileGroups();
    RewriteExecutionContext ctx = new RewriteExecutionContext(fileGroupsByPartition);

    if (ctx.totalGroupCount() == 0) {
      LOG.info("Nothing found to rewrite in {}", table.name());
      return EMPTY_RESULT;
    }

    Stream<RewritePositionDeletesGroup> groupStream = toGroupStream(ctx, fileGroupsByPartition);

    if (partialProgressEnabled) {
      return doExecuteWithPartialProgress(ctx, groupStream, commitManager());
    } else {
      return doExecute(ctx, groupStream, commitManager());
    }
  }
  /** 执行 planFileGroups 相关操作。 */
  private StructLikeMap<List<List<PositionDeletesScanTask>>> planFileGroups() {
    CloseableIterable<PositionDeletesScanTask> fileTasks = planFiles();

    try {
      StructType partitionType = Partitioning.partitionType(table);
      StructLikeMap<List<PositionDeletesScanTask>> fileTasksByPartition =
          groupByPartition(partitionType, fileTasks);
      return fileGroupsByPartition(fileTasksByPartition);
    } finally {
      try {
        fileTasks.close();
      } catch (IOException io) {
        LOG.error("Cannot properly close file iterable while planning for rewrite", io);
      }
    }
  }
  /** 执行 planFiles 相关操作。 */
  private CloseableIterable<PositionDeletesScanTask> planFiles() {
    Table deletesTable =
        MetadataTableUtils.createMetadataTableInstance(table, MetadataTableType.POSITION_DELETES);

    PositionDeletesBatchScan scan = (PositionDeletesBatchScan) deletesTable.newBatchScan();
    return CloseableIterable.transform(
        scan.baseTableFilter(filter).ignoreResiduals().planFiles(),
        task -> (PositionDeletesScanTask) task);
  }
  /** 执行 groupByPartition 相关操作。 */
  private StructLikeMap<List<PositionDeletesScanTask>> groupByPartition(
      StructType partitionType, Iterable<PositionDeletesScanTask> tasks) {
    StructLikeMap<List<PositionDeletesScanTask>> filesByPartition =
        StructLikeMap.create(partitionType);

    for (PositionDeletesScanTask task : tasks) {
      StructLike coerced = coercePartition(task, partitionType);

      List<PositionDeletesScanTask> partitionTasks = filesByPartition.get(coerced);
      if (partitionTasks == null) {
        partitionTasks = Lists.newArrayList();
      }
      partitionTasks.add(task);
      filesByPartition.put(coerced, partitionTasks);
    }

    return filesByPartition;
  }
  /** 执行 fileGroupsByPartition 相关操作。 */
  private StructLikeMap<List<List<PositionDeletesScanTask>>> fileGroupsByPartition(
      StructLikeMap<List<PositionDeletesScanTask>> filesByPartition) {
    return filesByPartition.transformValues(this::planFileGroups);
  }
  /** 执行 planFileGroups 相关操作。 */
  private List<List<PositionDeletesScanTask>> planFileGroups(List<PositionDeletesScanTask> tasks) {
    return ImmutableList.copyOf(rewriter.planFileGroups(tasks));
  }
  /** 执行 rewriteDeleteFiles 相关操作。 */
  private RewritePositionDeletesGroup rewriteDeleteFiles(
      RewriteExecutionContext ctx, RewritePositionDeletesGroup fileGroup) {
    String desc = jobDesc(fileGroup, ctx);
    Set<DeleteFile> addedFiles =
        withJobGroupInfo(
            newJobGroupInfo("REWRITE-POSITION-DELETES", desc),
            () -> rewriter.rewrite(fileGroup.tasks()));

    fileGroup.setOutputFiles(addedFiles);
    LOG.info("Rewrite position deletes ready to be committed - {}", desc);
    return fileGroup;
  }
  /** 执行 rewriteService 相关操作。 */
  private ExecutorService rewriteService() {
    return MoreExecutors.getExitingExecutorService(
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                maxConcurrentFileGroupRewrites,
                new ThreadFactoryBuilder()
                    .setNameFormat("Rewrite-Position-Delete-Service-%d")
                    .build()));
  }
  /** 执行 commitManager 相关操作。 */
  private RewritePositionDeletesCommitManager commitManager() {
    return new RewritePositionDeletesCommitManager(table);
  }

  /**
   * 非部分进度模式下的重写执行：所有文件组重写完成后一次性提交。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>使用线程池并发重写各文件组，任一失败则停止并清理已完成的重写产物。
   *   <li>全部成功后统一提交；提交遇到校验或冲突异常则抛出，并提示开启部分进度。
   *   <li>汇总各文件组重写结果返回。
   * </ol>
   *
   * @param ctx 重写执行上下文，提供组索引统计
   * @param groupStream 文件组流
   * @param commitManager 提交管理器
   * @return 重写结果
   */
  private Result doExecute(
      RewriteExecutionContext ctx,
      Stream<RewritePositionDeletesGroup> groupStream,
      RewritePositionDeletesCommitManager commitManager) {
    ExecutorService rewriteService = rewriteService();

    ConcurrentLinkedQueue<RewritePositionDeletesGroup> rewrittenGroups =
        Queues.newConcurrentLinkedQueue();

    Tasks.Builder<RewritePositionDeletesGroup> rewriteTaskBuilder =
        Tasks.foreach(groupStream)
            .executeWith(rewriteService)
            .stopOnFailure()
            .noRetry()
            .onFailure(
                (fileGroup, exception) ->
                    LOG.warn(
                        "Failure during rewrite process for group {}",
                        fileGroup.info(),
                        exception));

    try {
      rewriteTaskBuilder.run(fileGroup -> rewrittenGroups.add(rewriteDeleteFiles(ctx, fileGroup)));
    } catch (Exception e) {
      // At least one rewrite group failed, clean up all completed rewrites
      LOG.error(
          "Cannot complete rewrite, {} is not enabled and one of the file set groups failed to "
              + "be rewritten. This error occurred during the writing of new files, not during the commit process. This "
              + "indicates something is wrong that doesn't involve conflicts with other Iceberg operations. Enabling "
              + "{} may help in this case but the root cause should be investigated. Cleaning up {} groups which finished "
              + "being written.",
          PARTIAL_PROGRESS_ENABLED,
          PARTIAL_PROGRESS_ENABLED,
          rewrittenGroups.size(),
          e);

      Tasks.foreach(rewrittenGroups).suppressFailureWhenFinished().run(commitManager::abort);
      throw e;
    } finally {
      rewriteService.shutdown();
    }

    try {
      commitManager.commitOrClean(Sets.newHashSet(rewrittenGroups));
    } catch (ValidationException | CommitFailedException e) {
      String errorMessage =
          String.format(
              "Cannot commit rewrite because of a ValidationException or CommitFailedException. This usually means that "
                  + "this rewrite has conflicted with another concurrent Iceberg operation. To reduce the likelihood of "
                  + "conflicts, set %s which will break up the rewrite into multiple smaller commits controlled by %s. "
                  + "Separate smaller rewrite commits can succeed independently while any commits that conflict with "
                  + "another Iceberg operation will be ignored. This mode will create additional snapshots in the table "
                  + "history, one for each commit.",
              PARTIAL_PROGRESS_ENABLED, PARTIAL_PROGRESS_MAX_COMMITS);
      throw new RuntimeException(errorMessage, e);
    }

    List<FileGroupRewriteResult> rewriteResults =
        rewrittenGroups.stream()
            .map(RewritePositionDeletesGroup::asResult)
            .collect(Collectors.toList());

    return ImmutableRewritePositionDeleteFiles.Result.builder()
        .rewriteResults(rewriteResults)
        .build();
  }

  /**
   * 部分进度模式下的重写执行：将文件组拆分为多次较小提交，单次冲突不影响其他成功提交。
   *
   * <p>逻辑：启动提交服务按组数与最大提交数计算每次提交的组数；并发重写文件组， 完成的组送入提交服务分批提交；最终汇总所有成功提交的重写结果。
   *
   * @param ctx 重写执行上下文
   * @param groupStream 文件组流
   * @param commitManager 提交管理器
   * @return 重写结果
   */
  private Result doExecuteWithPartialProgress(
      RewriteExecutionContext ctx,
      Stream<RewritePositionDeletesGroup> groupStream,
      RewritePositionDeletesCommitManager commitManager) {
    ExecutorService rewriteService = rewriteService();

    // start commit service
    int groupsPerCommit = IntMath.divide(ctx.totalGroupCount(), maxCommits, RoundingMode.CEILING);
    CommitService commitService = commitManager.service(groupsPerCommit);
    commitService.start();

    // start rewrite tasks
    Tasks.foreach(groupStream)
        .suppressFailureWhenFinished()
        .executeWith(rewriteService)
        .noRetry()
        .onFailure(
            (fileGroup, exception) ->
                LOG.error("Failure during rewrite group {}", fileGroup.info(), exception))
        .run(fileGroup -> commitService.offer(rewriteDeleteFiles(ctx, fileGroup)));
    rewriteService.shutdown();

    // stop commit service
    commitService.close();
    List<RewritePositionDeletesGroup> commitResults = commitService.results();
    if (commitResults.size() == 0) {
      LOG.error(
          "{} is true but no rewrite commits succeeded. Check the logs to determine why the individual "
              + "commits failed. If this is persistent it may help to increase {} which will break the rewrite operation "
              + "into smaller commits.",
          PARTIAL_PROGRESS_ENABLED,
          PARTIAL_PROGRESS_MAX_COMMITS);
    }

    List<FileGroupRewriteResult> rewriteResults =
        commitResults.stream()
            .map(RewritePositionDeletesGroup::asResult)
            .collect(Collectors.toList());
    return ImmutableRewritePositionDeleteFiles.Result.builder()
        .rewriteResults(rewriteResults)
        .build();
  }
  /** 转换为 GroupStream。 */
  private Stream<RewritePositionDeletesGroup> toGroupStream(
      RewriteExecutionContext ctx,
      Map<StructLike, List<List<PositionDeletesScanTask>>> groupsByPartition) {
    return groupsByPartition.entrySet().stream()
        .filter(e -> e.getValue().size() != 0)
        .flatMap(
            e -> {
              StructLike partition = e.getKey();
              List<List<PositionDeletesScanTask>> scanGroups = e.getValue();
              return scanGroups.stream().map(tasks -> newRewriteGroup(ctx, partition, tasks));
            })
        .sorted(RewritePositionDeletesGroup.comparator(rewriteJobOrder));
  }
  /** 创建 RewriteGroup 实例。 */
  private RewritePositionDeletesGroup newRewriteGroup(
      RewriteExecutionContext ctx, StructLike partition, List<PositionDeletesScanTask> tasks) {
    int globalIndex = ctx.currentGlobalIndex();
    int partitionIndex = ctx.currentPartitionIndex(partition);
    FileGroupInfo info =
        ImmutableRewritePositionDeleteFiles.FileGroupInfo.builder()
            .globalIndex(globalIndex)
            .partitionIndex(partitionIndex)
            .partition(partition)
            .build();
    return new RewritePositionDeletesGroup(info, tasks);
  }
  /** 执行 validateAndInitOptions 相关操作。 */
  private void validateAndInitOptions() {
    Set<String> validOptions = Sets.newHashSet(rewriter.validOptions());
    validOptions.addAll(VALID_OPTIONS);

    Set<String> invalidKeys = Sets.newHashSet(options().keySet());
    invalidKeys.removeAll(validOptions);

    Preconditions.checkArgument(
        invalidKeys.isEmpty(),
        "Cannot use options %s, they are not supported by the action or the rewriter %s",
        invalidKeys,
        rewriter.description());

    rewriter.init(options());

    this.maxConcurrentFileGroupRewrites =
        PropertyUtil.propertyAsInt(
            options(),
            MAX_CONCURRENT_FILE_GROUP_REWRITES,
            MAX_CONCURRENT_FILE_GROUP_REWRITES_DEFAULT);

    this.maxCommits =
        PropertyUtil.propertyAsInt(
            options(), PARTIAL_PROGRESS_MAX_COMMITS, PARTIAL_PROGRESS_MAX_COMMITS_DEFAULT);

    this.partialProgressEnabled =
        PropertyUtil.propertyAsBoolean(
            options(), PARTIAL_PROGRESS_ENABLED, PARTIAL_PROGRESS_ENABLED_DEFAULT);

    this.rewriteJobOrder =
        RewriteJobOrder.fromName(
            PropertyUtil.propertyAsString(options(), REWRITE_JOB_ORDER, REWRITE_JOB_ORDER_DEFAULT));

    Preconditions.checkArgument(
        maxConcurrentFileGroupRewrites >= 1,
        "Cannot set %s to %s, the value must be positive.",
        MAX_CONCURRENT_FILE_GROUP_REWRITES,
        maxConcurrentFileGroupRewrites);

    Preconditions.checkArgument(
        !partialProgressEnabled || maxCommits > 0,
        "Cannot set %s to %s, the value must be positive when %s is true",
        PARTIAL_PROGRESS_MAX_COMMITS,
        maxCommits,
        PARTIAL_PROGRESS_ENABLED);
  }
  /** 执行 jobDesc 相关操作。 */
  private String jobDesc(RewritePositionDeletesGroup group, RewriteExecutionContext ctx) {
    StructLike partition = group.info().partition();
    if (partition.size() > 0) {
      return String.format(
          "Rewriting %d position delete files (%s, file group %d/%d, %s (%d/%d)) in %s",
          group.rewrittenDeleteFiles().size(),
          rewriter.description(),
          group.info().globalIndex(),
          ctx.totalGroupCount(),
          partition,
          group.info().partitionIndex(),
          ctx.groupsInPartition(partition),
          table.name());
    } else {
      return String.format(
          "Rewriting %d position files (%s, file group %d/%d) in %s",
          group.rewrittenDeleteFiles().size(),
          rewriter.description(),
          group.info().globalIndex(),
          ctx.totalGroupCount(),
          table.name());
    }
  }

  static class RewriteExecutionContext {
    private final StructLikeMap<Integer> numGroupsByPartition;
    private final int totalGroupCount;
    private final Map<StructLike, Integer> partitionIndexMap;
    private final AtomicInteger groupIndex;

    RewriteExecutionContext(
        StructLikeMap<List<List<PositionDeletesScanTask>>> fileTasksByPartition) {
      this.numGroupsByPartition = fileTasksByPartition.transformValues(List::size);
      this.totalGroupCount = numGroupsByPartition.values().stream().reduce(Integer::sum).orElse(0);
      this.partitionIndexMap = Maps.newConcurrentMap();
      this.groupIndex = new AtomicInteger(1);
    }
    /** 执行 currentGlobalIndex 相关操作。 */
    public int currentGlobalIndex() {
      return groupIndex.getAndIncrement();
    }
    /** 执行 currentPartitionIndex 相关操作。 */
    public int currentPartitionIndex(StructLike partition) {
      return partitionIndexMap.merge(partition, 1, Integer::sum);
    }
    /** 执行 groupsInPartition 相关操作。 */
    public int groupsInPartition(StructLike partition) {
      return numGroupsByPartition.get(partition);
    }
    /** 执行 totalGroupCount 相关操作。 */
    public int totalGroupCount() {
      return totalGroupCount;
    }
  }
  /** 执行 coercePartition 相关操作。 */
  private StructLike coercePartition(PositionDeletesScanTask task, StructType partitionType) {
    return PartitionUtil.coercePartition(partitionType, task.spec(), task.partition());
  }
}
