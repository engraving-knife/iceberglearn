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
import java.util.Arrays;
import java.util.Collection;
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
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.RewriteJobOrder;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.FileRewriter;
import org.apache.iceberg.actions.ImmutableRewriteDataFiles;
import org.apache.iceberg.actions.RewriteDataFiles;
import org.apache.iceberg.actions.RewriteDataFilesCommitManager;
import org.apache.iceberg.actions.RewriteFileGroup;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
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
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.StructLikeMap;
import org.apache.iceberg.util.Tasks;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Spark 的重写数据文件（RewriteDataFiles）action 实现。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），actions 子包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把小文件或布局不佳的数据文件重写为更优的文件集合（bin-pack / sort / zOrder 三种策略）。
 *   <li>按分区规划文件组（file group），每个组独立重写并可作为独立提交单元。
 *   <li>支持部分进度（partial progress）：把重写拆为多次提交，单次失败不影响其他成功组。
 *   <li>支持并发重写线程池、作业顺序控制、自定义过滤器等。
 * </ul>
 *
 * <p>设计意图：构造时关闭 Spark 自适应执行（AQE），保证写出的分区数与重写器预期一致，避免 文件布局被打乱；通过 {@link FileRewriter}
 * 抽象不同重写策略，commitManager 负责原子提交与冲突回退。 部分进度模式下用独立 CommitService 异步提交，提升并发度并隔离失败影响。
 *
 * <p>上下游关系：继承 {@link BaseSnapshotUpdateSparkAction}，实现 {@link RewriteDataFiles}； 被 Spark 过程 {@code
 * rewrite_data_files} 调用；依赖 SparkBinPackDataRewriter/SparkSortDataRewriter/ SparkZOrderDataRewriter
 * 等具体重写器与 Iceberg core 的提交管理器。
 */
public class RewriteDataFilesSparkAction
    extends BaseSnapshotUpdateSparkAction<RewriteDataFilesSparkAction> implements RewriteDataFiles {

  private static final Logger LOG = LoggerFactory.getLogger(RewriteDataFilesSparkAction.class);
  private static final Set<String> VALID_OPTIONS =
      ImmutableSet.of(
          MAX_CONCURRENT_FILE_GROUP_REWRITES,
          MAX_FILE_GROUP_SIZE_BYTES,
          PARTIAL_PROGRESS_ENABLED,
          PARTIAL_PROGRESS_MAX_COMMITS,
          TARGET_FILE_SIZE_BYTES,
          USE_STARTING_SEQUENCE_NUMBER,
          REWRITE_JOB_ORDER);

  private static final RewriteDataFilesSparkAction.Result EMPTY_RESULT =
      ImmutableRewriteDataFiles.Result.builder().rewriteResults(ImmutableList.of()).build();

  private final Table table;

  private Expression filter = Expressions.alwaysTrue();
  private int maxConcurrentFileGroupRewrites;
  private int maxCommits;
  private boolean partialProgressEnabled;
  private boolean useStartingSequenceNumber;
  private RewriteJobOrder rewriteJobOrder;
  private FileRewriter<FileScanTask, DataFile> rewriter = null;

  /**
   * 构造重写数据文件 action。
   *
   * <p>逻辑：克隆 SparkSession 避免污染调用方会话；关闭 AQE（自适应查询执行）， 因为 AQE 可能改变写出的分区数，破坏重写器的文件布局预期。
   *
   * @param spark SparkSession
   * @param table 目标表
   */
  RewriteDataFilesSparkAction(SparkSession spark, Table table) {
    super(spark.cloneSession());
    // Disable Adaptive Query Execution as this may change the output partitioning of our write
    spark().conf().set(SQLConf.ADAPTIVE_EXECUTION_ENABLED().key(), false);
    this.table = table;
  }
  /** 执行 self 相关操作。 */
  @Override
  protected RewriteDataFilesSparkAction self() {
    return this;
  }

  /** 使用 bin-pack（紧凑打包）策略重写，文件按大小合并。 */
  @Override
  public RewriteDataFilesSparkAction binPack() {
    Preconditions.checkArgument(
        rewriter == null, "Must use only one rewriter type (bin-pack, sort, zorder)");
    this.rewriter = new SparkBinPackDataRewriter(spark(), table);
    return this;
  }

  /** 使用指定排序规则 sort 策略重写。 */
  @Override
  public RewriteDataFilesSparkAction sort(SortOrder sortOrder) {
    Preconditions.checkArgument(
        rewriter == null, "Must use only one rewriter type (bin-pack, sort, zorder)");
    this.rewriter = new SparkSortDataRewriter(spark(), table, sortOrder);
    return this;
  }

  /** 使用表自带排序规则 sort 策略重写。 */
  @Override
  public RewriteDataFilesSparkAction sort() {
    Preconditions.checkArgument(
        rewriter == null, "Must use only one rewriter type (bin-pack, sort, zorder)");
    this.rewriter = new SparkSortDataRewriter(spark(), table);
    return this;
  }

  /** 使用指定列做 zOrder 策略重写，提升多维查询局部性。 */
  @Override
  public RewriteDataFilesSparkAction zOrder(String... columnNames) {
    Preconditions.checkArgument(
        rewriter == null, "Must use only one rewriter type (bin-pack, sort, zorder)");
    this.rewriter = new SparkZOrderDataRewriter(spark(), table, Arrays.asList(columnNames));
    return this;
  }

  /** 追加文件过滤表达式，只重写符合条件的文件。 */
  @Override
  public RewriteDataFilesSparkAction filter(Expression expression) {
    filter = Expressions.and(filter, expression);
    return this;
  }

  /**
   * 执行数据文件重写。
   *
   * <p>逻辑：取当前快照为起点；默认 bin-pack 策略；校验并初始化选项； 调 {@link #planFileGroups} 按分区规划文件组；若无文件可重写返回空结果； 否则按
   * partialProgressEnabled 选择部分进度或全量提交模式执行。
   *
   * @return 重写结果统计
   */
  @Override
  public RewriteDataFiles.Result execute() {
    if (table.currentSnapshot() == null) {
      return EMPTY_RESULT;
    }

    long startingSnapshotId = table.currentSnapshot().snapshotId();

    // Default to BinPack if no strategy selected
    if (this.rewriter == null) {
      this.rewriter = new SparkBinPackDataRewriter(spark(), table);
    }

    validateAndInitOptions();

    StructLikeMap<List<List<FileScanTask>>> fileGroupsByPartition =
        planFileGroups(startingSnapshotId);
    RewriteExecutionContext ctx = new RewriteExecutionContext(fileGroupsByPartition);

    if (ctx.totalGroupCount() == 0) {
      LOG.info("Nothing found to rewrite in {}", table.name());
      return EMPTY_RESULT;
    }

    Stream<RewriteFileGroup> groupStream = toGroupStream(ctx, fileGroupsByPartition);

    if (partialProgressEnabled) {
      return doExecuteWithPartialProgress(ctx, groupStream, commitManager(startingSnapshotId));
    } else {
      return doExecute(ctx, groupStream, commitManager(startingSnapshotId));
    }
  }

  /**
   * 规划待重写的文件组（包级可见，便于测试）。
   *
   * <p>逻辑：以 startingSnapshotId 扫描文件并应用 filter，按分区分组后再由 rewriter 切分为 多个文件组；返回 StructLikeMap（分区 ->
   * 文件组列表）。
   *
   * @param startingSnapshotId 起始快照 ID
   * @return 按分区组织的文件组映射
   */
  StructLikeMap<List<List<FileScanTask>>> planFileGroups(long startingSnapshotId) {
    CloseableIterable<FileScanTask> fileScanTasks =
        table
            .newScan()
            .useSnapshot(startingSnapshotId)
            .filter(filter)
            .ignoreResiduals()
            .planFiles();

    try {
      StructType partitionType = table.spec().partitionType();
      StructLikeMap<List<FileScanTask>> filesByPartition =
          groupByPartition(partitionType, fileScanTasks);
      return fileGroupsByPartition(filesByPartition);
    } finally {
      try {
        fileScanTasks.close();
      } catch (IOException io) {
        LOG.error("Cannot properly close file iterable while planning for rewrite", io);
      }
    }
  }

  /**
   * 把扫描任务按分区分组。
   *
   * <p>逻辑：遍历任务，若任务文件 specId 与当前表 spec 不一致（旧分区规格）， 视为未分区统一归到 emptyStruct，避免跨分区数据生成过多新文件。
   *
   * @param partitionType 分区类型
   * @param tasks 文件扫描任务
   * @return 分区到任务列表的映射
   */
  private StructLikeMap<List<FileScanTask>> groupByPartition(
      StructType partitionType, Iterable<FileScanTask> tasks) {
    StructLikeMap<List<FileScanTask>> filesByPartition = StructLikeMap.create(partitionType);
    StructLike emptyStruct = GenericRecord.create(partitionType);

    for (FileScanTask task : tasks) {
      // If a task uses an incompatible partition spec the data inside could contain values
      // which belong to multiple partitions in the current spec. Treating all such files as
      // un-partitioned and grouping them together helps to minimize new files made.
      StructLike taskPartition =
          task.file().specId() == table.spec().specId() ? task.file().partition() : emptyStruct;

      List<FileScanTask> files = filesByPartition.get(taskPartition);
      if (files == null) {
        files = Lists.newArrayList();
      }

      files.add(task);
      filesByPartition.put(taskPartition, files);
    }
    return filesByPartition;
  }

  /** 对每个分区的文件列表调用 rewriter.planFileGroups 切分为文件组。 */
  private StructLikeMap<List<List<FileScanTask>>> fileGroupsByPartition(
      StructLikeMap<List<FileScanTask>> filesByPartition) {
    return filesByPartition.transformValues(this::planFileGroups);
  }

  /** 委托 rewriter 把单个分区的任务切分为文件组。 */
  private List<List<FileScanTask>> planFileGroups(List<FileScanTask> tasks) {
    return ImmutableList.copyOf(rewriter.planFileGroups(tasks));
  }

  /**
   * 在 REWRITE-DATA-FILES 作业组下重写单个文件组（包级可见便于测试）。
   *
   * <p>逻辑：构造作业描述，在 JobGroupInfo 上下文中调用 rewriter.rewrite 得到新增文件集合， 设置到 fileGroup 上并返回。
   *
   * @param ctx 重写执行上下文
   * @param fileGroup 待重写文件组
   * @return 含输出文件的文件组
   */
  @VisibleForTesting
  RewriteFileGroup rewriteFiles(RewriteExecutionContext ctx, RewriteFileGroup fileGroup) {
    String desc = jobDesc(fileGroup, ctx);
    Set<DataFile> addedFiles =
        withJobGroupInfo(
            newJobGroupInfo("REWRITE-DATA-FILES", desc),
            () -> rewriter.rewrite(fileGroup.fileScans()));

    fileGroup.setOutputFiles(addedFiles);
    LOG.info("Rewrite Files Ready to be Committed - {}", desc);
    return fileGroup;
  }

  /** 构造固定大小的重写线程池，线程命名 Rewrite-Service-%d。 */
  private ExecutorService rewriteService() {
    return MoreExecutors.getExitingExecutorService(
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                maxConcurrentFileGroupRewrites,
                new ThreadFactoryBuilder().setNameFormat("Rewrite-Service-%d").build()));
  }

  /** 构造提交管理器（包级可见便于测试），用于原子提交重写结果或冲突回退。 */
  @VisibleForTesting
  RewriteDataFilesCommitManager commitManager(long startingSnapshotId) {
    return new RewriteDataFilesCommitManager(table, startingSnapshotId, useStartingSequenceNumber);
  }

  /**
   * 全量提交模式执行重写。
   *
   * <p>逻辑：用线程池并发重写所有文件组，任一组失败则中止并清理已重写组； 全部成功后调用 commitManager 一次性提交。提交冲突抛出带提示的 RuntimeException。
   *
   * @param ctx 执行上下文
   * @param groupStream 文件组流
   * @param commitManager 提交管理器
   * @return 重写结果
   */
  private Result doExecute(
      RewriteExecutionContext ctx,
      Stream<RewriteFileGroup> groupStream,
      RewriteDataFilesCommitManager commitManager) {
    ExecutorService rewriteService = rewriteService();

    ConcurrentLinkedQueue<RewriteFileGroup> rewrittenGroups = Queues.newConcurrentLinkedQueue();

    Tasks.Builder<RewriteFileGroup> rewriteTaskBuilder =
        Tasks.foreach(groupStream)
            .executeWith(rewriteService)
            .stopOnFailure()
            .noRetry()
            .onFailure(
                (fileGroup, exception) -> {
                  LOG.warn(
                      "Failure during rewrite process for group {}", fileGroup.info(), exception);
                });

    try {
      rewriteTaskBuilder.run(
          fileGroup -> {
            rewrittenGroups.add(rewriteFiles(ctx, fileGroup));
          });
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

      Tasks.foreach(rewrittenGroups)
          .suppressFailureWhenFinished()
          .run(commitManager::abortFileGroup);
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
        rewrittenGroups.stream().map(RewriteFileGroup::asResult).collect(Collectors.toList());
    return ImmutableRewriteDataFiles.Result.builder().rewriteResults(rewriteResults).build();
  }

  /**
   * 部分进度模式执行重写。
   *
   * <p>逻辑：启动独立 CommitService 异步按 groupsPerCommit 批次提交； 重写任务失败仅记录到 rewriteFailures 不中断；最后汇总成功与失败结果。
   *
   * @param ctx 执行上下文
   * @param groupStream 文件组流
   * @param commitManager 提交管理器
   * @return 含部分失败的重写结果
   */
  private Result doExecuteWithPartialProgress(
      RewriteExecutionContext ctx,
      Stream<RewriteFileGroup> groupStream,
      RewriteDataFilesCommitManager commitManager) {
    ExecutorService rewriteService = rewriteService();

    // start commit service
    int groupsPerCommit = IntMath.divide(ctx.totalGroupCount(), maxCommits, RoundingMode.CEILING);
    RewriteDataFilesCommitManager.CommitService commitService =
        commitManager.service(groupsPerCommit);
    commitService.start();

    Collection<FileGroupFailureResult> rewriteFailures = new ConcurrentLinkedQueue<>();
    // start rewrite tasks
    Tasks.foreach(groupStream)
        .suppressFailureWhenFinished()
        .executeWith(rewriteService)
        .noRetry()
        .onFailure(
            (fileGroup, exception) -> {
              LOG.error("Failure during rewrite group {}", fileGroup.info(), exception);
              rewriteFailures.add(
                  ImmutableRewriteDataFiles.FileGroupFailureResult.builder()
                      .info(fileGroup.info())
                      .dataFilesCount(fileGroup.numFiles())
                      .build());
            })
        .run(fileGroup -> commitService.offer(rewriteFiles(ctx, fileGroup)));
    rewriteService.shutdown();

    // stop commit service
    commitService.close();
    List<RewriteFileGroup> commitResults = commitService.results();
    if (commitResults.size() == 0) {
      LOG.error(
          "{} is true but no rewrite commits succeeded. Check the logs to determine why the individual "
              + "commits failed. If this is persistent it may help to increase {} which will break the rewrite operation "
              + "into smaller commits.",
          PARTIAL_PROGRESS_ENABLED,
          PARTIAL_PROGRESS_MAX_COMMITS);
    }

    List<FileGroupRewriteResult> rewriteResults =
        commitResults.stream().map(RewriteFileGroup::asResult).collect(Collectors.toList());
    return ImmutableRewriteDataFiles.Result.builder()
        .rewriteResults(rewriteResults)
        .rewriteFailures(rewriteFailures)
        .build();
  }

  /**
   * 把按分区的文件组映射展开为排序后的文件组流。
   *
   * <p>逻辑：过滤空分区，flatmap 每个分区的文件组为 RewriteFileGroup， 再按 rewriteJobOrder 排序（控制重写先后顺序，如先大后小）。
   */
  Stream<RewriteFileGroup> toGroupStream(
      RewriteExecutionContext ctx, Map<StructLike, List<List<FileScanTask>>> groupsByPartition) {
    return groupsByPartition.entrySet().stream()
        .filter(e -> e.getValue().size() != 0)
        .flatMap(
            e -> {
              StructLike partition = e.getKey();
              List<List<FileScanTask>> scanGroups = e.getValue();
              return scanGroups.stream().map(tasks -> newRewriteGroup(ctx, partition, tasks));
            })
        .sorted(RewriteFileGroup.comparator(rewriteJobOrder));
  }

  /** 构造新的 RewriteFileGroup，分配全局与分区内索引。 */
  private RewriteFileGroup newRewriteGroup(
      RewriteExecutionContext ctx, StructLike partition, List<FileScanTask> tasks) {
    int globalIndex = ctx.currentGlobalIndex();
    int partitionIndex = ctx.currentPartitionIndex(partition);
    FileGroupInfo info =
        ImmutableRewriteDataFiles.FileGroupInfo.builder()
            .globalIndex(globalIndex)
            .partitionIndex(partitionIndex)
            .partition(partition)
            .build();
    return new RewriteFileGroup(info, tasks);
  }

  /**
   * 校验选项合法性并初始化各运行参数。
   *
   * <p>逻辑：合并 action 与 rewriter 的合法选项集合，拒绝未知选项；从 options 读取 并发数、最大提交数、部分进度开关、起始序列号、作业顺序等，并做边界校验。
   */
  void validateAndInitOptions() {
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

    maxConcurrentFileGroupRewrites =
        PropertyUtil.propertyAsInt(
            options(),
            MAX_CONCURRENT_FILE_GROUP_REWRITES,
            MAX_CONCURRENT_FILE_GROUP_REWRITES_DEFAULT);

    maxCommits =
        PropertyUtil.propertyAsInt(
            options(), PARTIAL_PROGRESS_MAX_COMMITS, PARTIAL_PROGRESS_MAX_COMMITS_DEFAULT);

    partialProgressEnabled =
        PropertyUtil.propertyAsBoolean(
            options(), PARTIAL_PROGRESS_ENABLED, PARTIAL_PROGRESS_ENABLED_DEFAULT);

    useStartingSequenceNumber =
        PropertyUtil.propertyAsBoolean(
            options(), USE_STARTING_SEQUENCE_NUMBER, USE_STARTING_SEQUENCE_NUMBER_DEFAULT);

    rewriteJobOrder =
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

  /** 构造单个文件组重写的 Spark UI 作业描述，含文件数、组索引、分区、表名。 */
  private String jobDesc(RewriteFileGroup group, RewriteExecutionContext ctx) {
    StructLike partition = group.info().partition();
    if (partition.size() > 0) {
      return String.format(
          "Rewriting %d files (%s, file group %d/%d, %s (%d/%d)) in %s",
          group.rewrittenFiles().size(),
          rewriter.description(),
          group.info().globalIndex(),
          ctx.totalGroupCount(),
          partition,
          group.info().partitionIndex(),
          ctx.groupsInPartition(partition),
          table.name());
    } else {
      return String.format(
          "Rewriting %d files (%s, file group %d/%d) in %s",
          group.rewrittenFiles().size(),
          rewriter.description(),
          group.info().globalIndex(),
          ctx.totalGroupCount(),
          table.name());
    }
  }

  /**
   * 重写执行上下文（包级可见便于测试）。
   *
   * <p>设计意图：在并发重写时为每个文件组分配唯一的全局索引与分区内索引， 并提供总组数与分区内组数查询，用于作业描述与进度跟踪。使用 AtomicInteger 与
   * ConcurrentHashMap 保证线程安全。
   */
  @VisibleForTesting
  static class RewriteExecutionContext {
    private final StructLikeMap<Integer> numGroupsByPartition;
    private final int totalGroupCount;
    private final Map<StructLike, Integer> partitionIndexMap;
    private final AtomicInteger groupIndex;

    /** 构造执行上下文，统计各分区组数与总组数，初始化索引计数器。 */
    RewriteExecutionContext(StructLikeMap<List<List<FileScanTask>>> fileGroupsByPartition) {
      this.numGroupsByPartition = fileGroupsByPartition.transformValues(List::size);
      this.totalGroupCount = numGroupsByPartition.values().stream().reduce(Integer::sum).orElse(0);
      this.partitionIndexMap = Maps.newConcurrentMap();
      this.groupIndex = new AtomicInteger(1);
    }

    /** 原子地取下一个全局组索引。 */
    public int currentGlobalIndex() {
      return groupIndex.getAndIncrement();
    }

    /** 原子地取指定分区的下一个组内索引。 */
    public int currentPartitionIndex(StructLike partition) {
      return partitionIndexMap.merge(partition, 1, Integer::sum);
    }

    /** 返回指定分区的文件组总数。 */
    public int groupsInPartition(StructLike partition) {
      return numGroupsByPartition.get(partition);
    }

    /** 返回所有分区的文件组总数。 */
    public int totalGroupCount() {
      return totalGroupCount;
    }
  }
}
