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

import static org.apache.iceberg.PlanningMode.AUTO;
import static org.apache.iceberg.TableProperties.DATA_PLANNING_MODE;
import static org.apache.iceberg.TableProperties.DELETE_PLANNING_MODE;
import static org.apache.iceberg.TableProperties.PLANNING_MODE_DEFAULT;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ManifestEvaluator;
import org.apache.iceberg.expressions.Projections;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.metrics.ScanMetricsUtil;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.ParallelIterable;
import org.apache.iceberg.util.TableScanUtil;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 批量数据扫描的抽象基类：支持将扫描计划下推到集群分布式执行。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为可在集群上远程读取/过滤 manifest 的批量扫描提供通用骨架。
 *   <li>当元数据规模超出本地处理阈值时切换到远程规划；否则在本地完成规划。
 *   <li>协调数据文件规划与删除文件（delete files）索引构建，可并发执行以加速。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>本地/远程自适应：通过 {@link PlanningMode}（LOCAL/DISTRIBUTED/AUTO）和阈值判断，
 *       在本地线程池与集群并行度之间择优，兼顾小表开销与大表吞吐。
 *   <li>数据与删除解耦：data 与 delete 的规划各自独立判断本地/远程，并可用 CompletableFuture 并发执行，删除索引构建与数据文件分组互不阻塞。
 *   <li>引擎无关抽象：具体远程规划逻辑由子类（如 Spark/Flink 集成）实现， 本类只负责编排与本地分支。
 * </ul>
 *
 * <p>上下游关系：实现 {@link BatchScan}；被各引擎的扫描实现继承；依赖 {@link ManifestGroup}、 {@link DeleteFileIndex}
 * 完成本地规划。
 *
 * <p>注意：本类仍在演进，小版本也可能变更。
 */
abstract class BaseDistributedDataScan
    extends DataScan<BatchScan, ScanTask, ScanTaskGroup<ScanTask>> implements BatchScan {

  private static final Logger LOG = LoggerFactory.getLogger(BaseDistributedDataScan.class);
  private static final long LOCAL_PLANNING_MAX_SLOT_SIZE = 128L * 1024 * 1024; // 128 MB
  private static final int MONITOR_POOL_SIZE = 2;

  private final int localParallelism;
  private final long localPlanningSizeThreshold;

  /**
   * 构造分布式扫描基类。
   *
   * <p>逻辑：根据是否启用 worker 池决定本地并行度（启用则用 worker 池大小，否则为 1）， 并据此计算本地规划的总大小阈值 = 本地并行度 ×
   * 128MB。超过该阈值时倾向于远程规划。
   *
   * @param table 底层物理表
   * @param schema 输出 schema
   * @param context 扫描上下文
   */
  protected BaseDistributedDataScan(Table table, Schema schema, TableScanContext context) {
    super(table, schema, context);
    this.localParallelism = PLAN_SCANS_WITH_WORKER_POOL ? ThreadPools.WORKER_THREAD_POOL_SIZE : 1;
    this.localPlanningSizeThreshold = localParallelism * LOCAL_PLANNING_MAX_SLOT_SIZE;
  }

  /**
   * 返回集群并行度：集群能并发处理 manifest 的最大数量。
   *
   * <p>实现应综合考虑当前可用处理槽位及动态分配能力。该值与本地线程池大小比较以判断远程规划是否值得。 若 planning mode 显式设为 LOCAL 或
   * DISTRIBUTED，则此值被忽略。
   *
   * @return 集群可用的并发度
   */
  protected abstract int remoteParallelism();

  /**
   * 返回数据规划使用的 planning mode。
   *
   * <p>逻辑：读取表属性 {@value org.apache.iceberg.TableProperties#DATA_PLANNING_MODE}， 缺省时使用 {@value
   * org.apache.iceberg.TableProperties#PLANNING_MODE_DEFAULT}，并解析为枚举。
   *
   * @return 数据 planning mode
   */
  protected PlanningMode dataPlanningMode() {
    Map<String, String> properties = table().properties();
    String modeName = properties.getOrDefault(DATA_PLANNING_MODE, PLANNING_MODE_DEFAULT);
    return PlanningMode.fromName(modeName);
  }

  /**
   * 控制是否对远程规划得到的数据文件做防御性拷贝。
   *
   * <p>设计要点：默认对每个远程规划的数据文件做拷贝，假设返回的迭代器可能是惰性且复用对象的。 若实现能保证数据文件对象可安全加入集合，可重写此方法返回 false 以省去拷贝开销。
   *
   * @return 默认 true，表示需要拷贝
   */
  protected boolean shouldCopyRemotelyPlannedDataFiles() {
    return true;
  }

  /**
   * 远程规划数据文件：子类实现应在集群上读取并过滤给定 manifest，返回按组划分的数据文件。
   *
   * <p>鼓励返回"组"以便本类并发处理后续步骤，对等值删除（equality deletes）尤其有利， 因为其删除索引查找需要比较边界、典型受益于并行化。
   *
   * <p>若结果迭代器会复用对象，则 {@link #shouldCopyRemotelyPlannedDataFiles()} 必须返回 true。 输入 manifest
   * 已基于扫描过滤做过初步筛选，实现需进一步过滤并只返回可能匹配的数据文件。
   *
   * @param dataManifests 已初步筛选的数据 manifest
   * @param withColumnStats 是否加载列统计
   * @return 按组划分的数据文件可关闭迭代器
   */
  protected abstract Iterable<CloseableIterable<DataFile>> planDataRemotely(
      List<ManifestFile> dataManifests, boolean withColumnStats);

  /**
   * 返回删除文件规划使用的 planning mode。
   *
   * <p>逻辑：读取表属性 {@value org.apache.iceberg.TableProperties#DELETE_PLANNING_MODE}， 缺省时使用 {@value
   * org.apache.iceberg.TableProperties#PLANNING_MODE_DEFAULT}，并解析为枚举。
   *
   * @return 删除 planning mode
   */
  protected PlanningMode deletePlanningMode() {
    Map<String, String> properties = table().properties();
    String modeName = properties.getOrDefault(DELETE_PLANNING_MODE, PLANNING_MODE_DEFAULT);
    return PlanningMode.fromName(modeName);
  }

  /**
   * 远程规划删除文件：在集群上进一步过滤删除 manifest，构建并返回删除文件索引。
   *
   * <p>输入 manifest 已基于扫描过滤做过初步筛选，实现需进一步过滤并只返回可能匹配的删除文件。
   *
   * @param deleteManifests 已初步筛选的删除 manifest
   * @return 远程规划得到的删除文件索引
   */
  protected abstract DeleteFileIndex planDeletesRemotely(List<ManifestFile> deleteManifests);

  /**
   * 执行文件扫描规划，产出文件扫描任务。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从快照找匹配的删除 manifest，判断是否可能含等值删除，并决定删除规划本地/远程；
   *   <li>从快照找匹配的数据 manifest，判断是否需加载列统计，并决定数据规划本地/远程；
   *   <li>若数据与删除都本地规划，则直接走 {@link #planFileTasksLocally} 返回；
   *   <li>否则创建 monitor 线程池，用 CompletableFuture 并发启动数据与删除规划；
   *   <li>数据规划完成后，对每组数据文件 join 删除索引，组装成 {@link BaseFileScanTask}；
   *   <li>按是否需要 executor 选择 {@link ParallelIterable} 或串行拼接返回；
   *   <li>异常时取消两个 future，最终关闭 monitor 池。
   * </ol>
   *
   * @return 文件扫描任务的可关闭迭代器
   */
  @Override
  protected CloseableIterable<ScanTask> doPlanFiles() {
    Snapshot snapshot = snapshot();

    List<ManifestFile> deleteManifests = findMatchingDeleteManifests(snapshot);
    boolean mayHaveEqualityDeletes = deleteManifests.size() > 0 && mayHaveEqualityDeletes(snapshot);
    boolean planDeletesLocally = shouldPlanDeletesLocally(deleteManifests, mayHaveEqualityDeletes);

    List<ManifestFile> dataManifests = findMatchingDataManifests(snapshot);
    boolean loadColumnStats = mayHaveEqualityDeletes || shouldReturnColumnStats();
    boolean planDataLocally = shouldPlanDataLocally(dataManifests, loadColumnStats);
    boolean copyDataFiles = shouldCopyDataFiles(planDataLocally, loadColumnStats);

    if (planDataLocally && planDeletesLocally) {
      return planFileTasksLocally(dataManifests, deleteManifests);
    }

    ExecutorService monitorPool = newMonitorPool();

    CompletableFuture<DeleteFileIndex> deletesFuture =
        newDeletesFuture(deleteManifests, planDeletesLocally, monitorPool);

    CompletableFuture<Iterable<CloseableIterable<DataFile>>> dataFuture =
        newDataFuture(dataManifests, planDataLocally, loadColumnStats, monitorPool);

    try {
      Iterable<CloseableIterable<ScanTask>> fileTasks =
          toFileTasks(dataFuture, deletesFuture, copyDataFiles);

      if (shouldPlanWithExecutor() && (planDataLocally || mayHaveEqualityDeletes)) {
        return new ParallelIterable<>(fileTasks, planExecutor());
      } else {
        return CloseableIterable.concat(fileTasks);
      }

    } catch (CompletionException e) {
      deletesFuture.cancel(true /* may interrupt */);
      dataFuture.cancel(true /* may interrupt */);
      throw new RuntimeException("Failed to plan files", e);

    } finally {
      monitorPool.shutdown();
    }
  }

  /**
   * 将文件扫描任务规划为任务组（split）。
   *
   * <p>逻辑：先调用 {@link #planFiles()} 得到任务，再按目标 split 大小、回看窗口、打开文件成本 委托 {@link
   * TableScanUtil#planTaskGroups} 聚合成 {@link ScanTaskGroup}。
   *
   * @return 扫描任务组的可关闭迭代器
   */
  @Override
  public CloseableIterable<ScanTaskGroup<ScanTask>> planTasks() {
    return TableScanUtil.planTaskGroups(
        planFiles(), targetSplitSize(), splitLookback(), splitOpenFileCost());
  }

  /**
   * 从快照找匹配的数据 manifest 并更新扫描指标。
   *
   * <p>逻辑：取快照全部数据 manifest，记录总数；用 {@link #filterManifests} 过滤后记录跳过数。
   *
   * @param snapshot 当前快照
   * @return 过滤后匹配的数据 manifest 列表
   */
  private List<ManifestFile> findMatchingDataManifests(Snapshot snapshot) {
    List<ManifestFile> dataManifests = snapshot.dataManifests(io());
    scanMetrics().totalDataManifests().increment(dataManifests.size());

    List<ManifestFile> matchingDataManifests = filterManifests(dataManifests);
    int skippedDataManifestsCount = dataManifests.size() - matchingDataManifests.size();
    scanMetrics().skippedDataManifests().increment(skippedDataManifestsCount);

    return matchingDataManifests;
  }

  /**
   * 从快照找匹配的删除 manifest 并更新扫描指标。
   *
   * <p>逻辑：取快照全部删除 manifest，记录总数；用 {@link #filterManifests} 过滤后记录跳过数。
   *
   * @param snapshot 当前快照
   * @return 过滤后匹配的删除 manifest 列表
   */
  private List<ManifestFile> findMatchingDeleteManifests(Snapshot snapshot) {
    List<ManifestFile> deleteManifests = snapshot.deleteManifests(io());
    scanMetrics().totalDeleteManifests().increment(deleteManifests.size());

    List<ManifestFile> matchingDeleteManifests = filterManifests(deleteManifests);
    int skippedDeleteManifestsCount = deleteManifests.size() - matchingDeleteManifests.size();
    scanMetrics().skippedDeleteManifests().increment(skippedDeleteManifestsCount);

    return matchingDeleteManifests;
  }

  /**
   * 用分区评估器过滤 manifest：保留有新增/现存文件且通过 manifest 级过滤的项。
   *
   * <p>逻辑：先按 spec id 缓存 {@link ManifestEvaluator}（避免重复构建），再过滤掉 既无 added 又无 existing 文件的
   * manifest，最后用对应 spec 的评估器评估 manifest 元数据。
   *
   * @param manifests 待过滤 manifest
   * @return 通过过滤的 manifest 列表
   */
  private List<ManifestFile> filterManifests(List<ManifestFile> manifests) {
    Map<Integer, ManifestEvaluator> evalCache = specCache(this::newManifestEvaluator);

    return manifests.stream()
        .filter(manifest -> manifest.hasAddedFiles() || manifest.hasExistingFiles())
        .filter(manifest -> evalCache.get(manifest.partitionSpecId()).eval(manifest))
        .collect(Collectors.toList());
  }

  /**
   * 判断删除规划是否应本地执行。
   *
   * <p>逻辑：AUTO 模式下若可能含等值删除则本地；否则按 {@link #shouldPlanLocally} 判断。
   *
   * @param deleteManifests 删除 manifest
   * @param mayHaveEqualityDeletes 是否可能含等值删除
   * @return true 表示应本地规划
   */
  private boolean shouldPlanDeletesLocally(
      List<ManifestFile> deleteManifests, boolean mayHaveEqualityDeletes) {
    PlanningMode mode = deletePlanningMode();
    return (mode == AUTO && mayHaveEqualityDeletes) || shouldPlanLocally(mode, deleteManifests);
  }

  /**
   * 判断数据规划是否应本地执行。
   *
   * <p>逻辑：AUTO 模式下若需加载列统计则本地；否则按 {@link #shouldPlanLocally} 判断。
   *
   * @param dataManifests 数据 manifest
   * @param loadColumnStats 是否需加载列统计
   * @return true 表示应本地规划
   */
  private boolean shouldPlanDataLocally(List<ManifestFile> dataManifests, boolean loadColumnStats) {
    PlanningMode mode = dataPlanningMode();
    return (mode == AUTO && loadColumnStats) || shouldPlanLocally(mode, dataManifests);
  }

  /**
   * 依据 planning mode 与 manifest 规模判断是否本地规划。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>自定义 executor 时强制本地；
   *   <li>LOCAL：始终本地；
   *   <li>DISTRIBUTED：仅当 manifest 为空时本地；
   *   <li>AUTO：当集群并行度不大于本地、manifest 数不大于 2 倍本地并行度、或总大小不大于阈值时本地。
   * </ul>
   *
   * @param mode planning mode
   * @param manifests 待规划的 manifest
   * @return true 表示应本地规划
   */
  private boolean shouldPlanLocally(PlanningMode mode, List<ManifestFile> manifests) {
    if (context().planWithCustomizedExecutor()) {
      return true;
    }

    switch (mode) {
      case LOCAL:
        return true;

      case DISTRIBUTED:
        return manifests.isEmpty();

      case AUTO:
        return remoteParallelism() <= localParallelism
            || manifests.size() <= 2 * localParallelism
            || totalSize(manifests) <= localPlanningSizeThreshold;

      default:
        throw new IllegalArgumentException("Unknown planning mode: " + mode);
    }
  }

  /** 计算 manifest 列表的总长度（字节）。 */
  private long totalSize(List<ManifestFile> manifests) {
    return manifests.stream().mapToLong(ManifestFile::length).sum();
  }

  /**
   * 判断是否需要拷贝数据文件。
   *
   * <p>逻辑：本地规划时拷贝；或远程规划且 {@link #shouldCopyRemotelyPlannedDataFiles()} 为 true 时拷贝；
   * 或需加载列统计但调用方不需要返回统计时拷贝（用于内部消费统计后丢弃）。
   *
   * @param planDataLocally 是否本地规划数据
   * @param loadColumnStats 是否加载了列统计
   * @return true 表示需要拷贝数据文件
   */
  private boolean shouldCopyDataFiles(boolean planDataLocally, boolean loadColumnStats) {
    return planDataLocally
        || shouldCopyRemotelyPlannedDataFiles()
        || (loadColumnStats && !shouldReturnColumnStats());
  }

  /**
   * 在本地完成文件任务规划。
   *
   * <p>逻辑：构造 {@link ManifestGroup}（含数据与删除 manifest），直接调用其 {@code planFiles} 得到文件任务并强转返回。
   *
   * @param dataManifests 数据 manifest
   * @param deleteManifests 删除 manifest
   * @return 文件扫描任务的可关闭迭代器
   */
  @SuppressWarnings("unchecked")
  private CloseableIterable<ScanTask> planFileTasksLocally(
      List<ManifestFile> dataManifests, List<ManifestFile> deleteManifests) {
    LOG.info("Planning file tasks locally for table {}", table().name());
    ManifestGroup manifestGroup = newManifestGroup(dataManifests, deleteManifests);
    CloseableIterable<? extends ScanTask> fileTasks = manifestGroup.planFiles();
    return (CloseableIterable<ScanTask>) fileTasks;
  }

  /**
   * 构建删除规划的异步 future。
   *
   * <p>逻辑：在 monitor 池上提交任务，根据 {@code planLocally} 选择 {@link #planDeletesLocally} 或 {@link
   * #planDeletesRemotely}。
   *
   * @param deleteManifests 删除 manifest
   * @param planLocally 是否本地规划
   * @param monitorPool 监控线程池
   * @return 删除文件索引的 future
   */
  private CompletableFuture<DeleteFileIndex> newDeletesFuture(
      List<ManifestFile> deleteManifests, boolean planLocally, ExecutorService monitorPool) {

    return CompletableFuture.supplyAsync(
        () -> {
          if (planLocally) {
            LOG.info("Planning deletes locally for table {}", table().name());
            return planDeletesLocally(deleteManifests);
          } else {
            LOG.info("Planning deletes remotely for table {}", table().name());
            return planDeletesRemotely(deleteManifests);
          }
        },
        monitorPool);
  }

  /**
   * 本地构建删除文件索引。
   *
   * <p>逻辑：用 {@link DeleteFileIndex.Builder} 加载 specs、数据过滤、大小写敏感、扫描指标； 若启用 executor 且 manifest 数大于
   * 1，则用 planExecutor 加速。
   *
   * @param deleteManifests 删除 manifest
   * @return 构建好的删除文件索引
   */
  private DeleteFileIndex planDeletesLocally(List<ManifestFile> deleteManifests) {
    DeleteFileIndex.Builder builder = DeleteFileIndex.builderFor(io(), deleteManifests);

    if (shouldPlanWithExecutor() && deleteManifests.size() > 1) {
      builder.planWith(planExecutor());
    }

    return builder
        .specsById(table().specs())
        .filterData(filter())
        .caseSensitive(isCaseSensitive())
        .scanMetrics(scanMetrics())
        .build();
  }

  /**
   * 构建数据规划的异步 future。
   *
   * <p>逻辑：在 monitor 池上提交任务，根据 {@code planLocally} 选择本地 {@link ManifestGroup#fileGroups} 或远程 {@link
   * #planDataRemotely}。
   *
   * @param dataManifests 数据 manifest
   * @param planLocally 是否本地规划
   * @param withColumnStats 是否加载列统计
   * @param monitorPool 监控线程池
   * @return 按组划分的数据文件可关闭迭代器的 future
   */
  private CompletableFuture<Iterable<CloseableIterable<DataFile>>> newDataFuture(
      List<ManifestFile> dataManifests,
      boolean planLocally,
      boolean withColumnStats,
      ExecutorService monitorPool) {

    return CompletableFuture.supplyAsync(
        () -> {
          if (planLocally) {
            LOG.info("Planning data locally for table {}", table().name());
            ManifestGroup manifestGroup = newManifestGroup(dataManifests, withColumnStats);
            return manifestGroup.fileGroups();
          } else {
            LOG.info("Planning data remotely for table {}", table().name());
            return planDataRemotely(dataManifests, withColumnStats);
          }
        },
        monitorPool);
  }

  /**
   * 将数据文件组与删除索引组合成文件任务组。
   *
   * <p>逻辑：先缓存 schema 串、spec 串、残留评估器（按 spec id），再 join 数据 future 得到数据组， 最后用 {@link
   * Iterables#transform} 把每组数据文件映射为对应的文件任务迭代器。
   *
   * @param dataFuture 数据文件组的 future
   * @param deletesFuture 删除索引的 future
   * @param copyDataFiles 是否拷贝数据文件
   * @return 文件任务组的可迭代对象
   */
  private Iterable<CloseableIterable<ScanTask>> toFileTasks(
      CompletableFuture<Iterable<CloseableIterable<DataFile>>> dataFuture,
      CompletableFuture<DeleteFileIndex> deletesFuture,
      boolean copyDataFiles) {

    String schemaString = SchemaParser.toJson(tableSchema());
    Map<Integer, String> specStringCache = specCache(PartitionSpecParser::toJson);
    Map<Integer, ResidualEvaluator> residualCache = specCache(this::newResidualEvaluator);

    Iterable<CloseableIterable<DataFile>> dataFileGroups = dataFuture.join();

    return Iterables.transform(
        dataFileGroups,
        dataFiles ->
            toFileTasks(
                dataFiles,
                deletesFuture,
                copyDataFiles,
                schemaString,
                specStringCache,
                residualCache));
  }

  /**
   * 对单个数据文件组，逐文件 join 删除索引并组装 {@link BaseFileScanTask}。
   *
   * <p>逻辑：对每个数据文件，从删除索引取其关联的删除文件，按 spec 取残留评估器， 更新扫描指标，按需拷贝数据文件（控制是否保留列统计），构造任务返回。
   *
   * @param dataFiles 数据文件迭代器
   * @param deletesFuture 删除索引的 future
   * @param copyDataFiles 是否拷贝数据文件
   * @param schemaString schema 的 JSON 串
   * @param specStringCache 按 spec id 缓存的 spec JSON 串
   * @param residualCache 按 spec id 缓存的残留评估器
   * @return 文件扫描任务的可关闭迭代器
   */
  private CloseableIterable<ScanTask> toFileTasks(
      CloseableIterable<DataFile> dataFiles,
      CompletableFuture<DeleteFileIndex> deletesFuture,
      boolean copyDataFiles,
      String schemaString,
      Map<Integer, String> specStringCache,
      Map<Integer, ResidualEvaluator> residualCache) {

    return CloseableIterable.transform(
        dataFiles,
        dataFile -> {
          DeleteFile[] deleteFiles = deletesFuture.join().forDataFile(dataFile);

          String specString = specStringCache.get(dataFile.specId());
          ResidualEvaluator residuals = residualCache.get(dataFile.specId());

          ScanMetricsUtil.fileTask(scanMetrics(), dataFile, deleteFiles);

          return new BaseFileScanTask(
              copyDataFiles ? dataFile.copy(shouldReturnColumnStats()) : dataFile,
              deleteFiles,
              schemaString,
              specString,
              residuals);
        });
  }

  /**
   * 为指定分区 spec 构造 manifest 评估器。
   *
   * <p>逻辑：把扫描过滤投影到分区（inclusive），再用投影后的过滤构造 {@link ManifestEvaluator}。
   *
   * @param spec 分区 spec
   * @return manifest 评估器
   */
  private ManifestEvaluator newManifestEvaluator(PartitionSpec spec) {
    Expression projection = Projections.inclusive(spec, isCaseSensitive()).project(filter());
    return ManifestEvaluator.forPartitionFilter(projection, spec, isCaseSensitive());
  }

  /**
   * 为指定分区 spec 构造残留评估器（用于评估分区后剩余的过滤条件）。
   *
   * @param spec 分区 spec
   * @return 残留评估器
   */
  private ResidualEvaluator newResidualEvaluator(PartitionSpec spec) {
    return ResidualEvaluator.of(spec, residualFilter(), isCaseSensitive());
  }

  /**
   * 按 spec id 缓存加载结果：对表的所有 spec 应用加载函数，构造 spec id → 结果的映射。
   *
   * @param load 加载函数
   * @param <R> 结果类型
   * @return spec id 到加载结果的映射
   */
  private <R> Map<Integer, R> specCache(Function<PartitionSpec, R> load) {
    Map<Integer, R> cache = Maps.newHashMap();
    table().specs().forEach((specId, spec) -> cache.put(specId, load.apply(spec)));
    return cache;
  }

  /**
   * 判断快照是否可能含等值删除。
   *
   * <p>逻辑：读取快照 summary 中等值删除总数，为 null 或不等于 "0" 时认为可能含等值删除。
   *
   * @param snapshot 当前快照
   * @return true 表示可能含等值删除
   */
  private boolean mayHaveEqualityDeletes(Snapshot snapshot) {
    String count = snapshot.summary().get(SnapshotSummary.TOTAL_EQ_DELETES_PROP);
    return count == null || !count.equals("0");
  }

  /**
   * 创建监控线程池：使数据与删除规划在远程规划时可并发执行。
   *
   * <p>设计要点：池大小为 {@value #MONITOR_POOL_SIZE}，刚好容纳数据与删除两个 future 并发。
   *
   * @return 新的监控线程池
   */
  // a monitor pool that enables planing data and deletes concurrently if remote planning is used
  private ExecutorService newMonitorPool() {
    return ThreadPools.newWorkerPool("iceberg-planning-monitor-service", MONITOR_POOL_SIZE);
  }
}
