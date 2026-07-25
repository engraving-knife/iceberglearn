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
 * 基于 Spark 执行的 Iceberg 表维护动作，执行快照过期、文件清理、数据压缩等表维护操作。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 RewritePositionDeleteFilesSparkAction。
 *
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
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

  /** 执行该方法的具体逻辑。 */
  @Override
  protected RewritePositionDeleteFilesSparkAction self() {
    return this;
  }

  /**
   * 按条件过滤。
   *
   * @param expression 参数
   * @return 结果对象
   */
  @Override
  public RewritePositionDeleteFilesSparkAction filter(Expression expression) {
    filter = Expressions.and(filter, expression);
    return this;
  }

  /**
   * 执行具体逻辑。
   *
   * @return 结果对象
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

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  private CloseableIterable<PositionDeletesScanTask> planFiles() {
    Table deletesTable =
        MetadataTableUtils.createMetadataTableInstance(table, MetadataTableType.POSITION_DELETES);

    PositionDeletesBatchScan scan = (PositionDeletesBatchScan) deletesTable.newBatchScan();
    return CloseableIterable.transform(
        scan.baseTableFilter(filter).ignoreResiduals().planFiles(),
        task -> (PositionDeletesScanTask) task);
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  private StructLikeMap<List<List<PositionDeletesScanTask>>> fileGroupsByPartition(
      StructLikeMap<List<PositionDeletesScanTask>> filesByPartition) {
    return filesByPartition.transformValues(this::planFileGroups);
  }

  /** 执行该方法的具体逻辑。 */
  private List<List<PositionDeletesScanTask>> planFileGroups(List<PositionDeletesScanTask> tasks) {
    return ImmutableList.copyOf(rewriter.planFileGroups(tasks));
  }

  /** 重写计划或文件。 */
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

  /** 重写计划或文件。 */
  private ExecutorService rewriteService() {
    return MoreExecutors.getExitingExecutorService(
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                maxConcurrentFileGroupRewrites,
                new ThreadFactoryBuilder()
                    .setNameFormat("Rewrite-Position-Delete-Service-%d")
                    .build()));
  }

  /** 提交事务或写入结果。 */
  private RewritePositionDeletesCommitManager commitManager() {
    /** 重写计划或文件。 */
    return new RewritePositionDeletesCommitManager(table);
  }

  /** 执行该方法的具体逻辑。 */
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
      /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
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

  /** 转换为groupstream。 */
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

  /** 执行该方法的具体逻辑。 */
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
    /** 重写计划或文件。 */
    return new RewritePositionDeletesGroup(info, tasks);
  }

  /** 校验前置条件或参数。 */
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

  /** 执行该方法的具体逻辑。 */
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

  /**
   * 基于 Spark 执行的 Iceberg 表维护动作的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 RewriteExecutionContext。
   *
   * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
   *
   * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
   */
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

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public int currentGlobalIndex() {
      return groupIndex.getAndIncrement();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param partition 参数
     * @return 结果对象
     */
    public int currentPartitionIndex(StructLike partition) {
      return partitionIndexMap.merge(partition, 1, Integer::sum);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param partition 参数
     * @return 结果对象
     */
    public int groupsInPartition(StructLike partition) {
      return numGroupsByPartition.get(partition);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public int totalGroupCount() {
      return totalGroupCount;
    }
  }

  /** 执行该方法的具体逻辑。 */
  private StructLike coercePartition(PositionDeletesScanTask task, StructType partitionType) {
    return PartitionUtil.coercePartition(partitionType, task.spec(), task.partition());
  }
}
