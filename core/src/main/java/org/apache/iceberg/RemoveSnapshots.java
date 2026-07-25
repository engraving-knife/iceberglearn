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

import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.GC_ENABLED;
import static org.apache.iceberg.TableProperties.GC_ENABLED_DEFAULT;
import static org.apache.iceberg.TableProperties.MAX_REF_AGE_MS;
import static org.apache.iceberg.TableProperties.MAX_REF_AGE_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.MAX_SNAPSHOT_AGE_MS;
import static org.apache.iceberg.TableProperties.MAX_SNAPSHOT_AGE_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.MIN_SNAPSHOTS_TO_KEEP;
import static org.apache.iceberg.TableProperties.MIN_SNAPSHOTS_TO_KEEP_DEFAULT;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.relocated.com.google.common.util.concurrent.MoreExecutors;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 快照过期实现：根据保留策略（年龄、最小数量、ref 年龄）移除不再需要的快照及其文件。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 {@link ExpireSnapshots} API 的实现类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按 {@code max-snapshot-age-ms}、{@code min-snapshots-to-keep}、{@code max-ref-age-ms} 等表属性与
 *       ref 配置计算需保留的快照集合。
 *   <li>识别并移除所有分支/tag 不再引用的快照，更新表元数据。
 *   <li>提交元数据后，按策略（增量或可达性）清理对应的数据文件、manifest 与 manifest list。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>过期分两阶段：先 {@link #apply()} 计算并提交新元数据（移除快照引用）， 再 {@link #cleanExpiredSnapshots()}
 *       物理删除文件，保证元数据先行、清理可重试。
 *   <li>支持增量清理（{@link IncrementalFileCleanup}）与可达性清理（{@link ReachableFileCleanup}）两种策略： 单 ref
 *       时默认增量；多 ref 时使用可达性清理以保证安全。
 *   <li>GC 开关：构造方法校验 {@code gc.enabled}，避免误删被其他表引用的文件。
 * </ul>
 *
 * <p>上下游关系：被 {@link BaseTable#expireSnapshots()} 创建；底层依赖 {@link TableOperations} 提交元数据，依赖 {@link
 * FileCleanupStrategy} 执行物理文件删除。
 */
@SuppressWarnings("UnnecessaryAnonymousClass")
class RemoveSnapshots implements ExpireSnapshots {
  private static final Logger LOG = LoggerFactory.getLogger(RemoveSnapshots.class);

  // Creates an executor service that runs each task in the thread that invokes execute/submit.
  private static final ExecutorService DEFAULT_DELETE_EXECUTOR_SERVICE =
      MoreExecutors.newDirectExecutorService();

  private final Consumer<String> defaultDelete =
      new Consumer<String>() {
        @Override
        public void accept(String file) {
          ops.io().deleteFile(file);
        }
      };

  private final TableOperations ops;
  private final Set<Long> idsToRemove = Sets.newHashSet();
  private final long now;
  private final long defaultMaxRefAgeMs;
  private boolean cleanExpiredFiles = true;
  private TableMetadata base;
  private long defaultExpireOlderThan;
  private int defaultMinNumSnapshots;
  private Consumer<String> deleteFunc = defaultDelete;
  private ExecutorService deleteExecutorService = DEFAULT_DELETE_EXECUTOR_SERVICE;
  private ExecutorService planExecutorService = ThreadPools.getWorkerPool();
  private Boolean incrementalCleanup;

  /**
   * 构造方法。
   *
   * <p>逻辑：保存 ops 与当前元数据；校验 GC 已启用；从表属性读取默认过期阈值 （maxSnapshotAge、minSnapshots、maxRefAge）。
   *
   * @param ops 表操作接口
   * @throws ValidationException 当 GC 未启用时抛出
   */
  RemoveSnapshots(TableOperations ops) {
    this.ops = ops;
    this.base = ops.current();
    ValidationException.check(
        PropertyUtil.propertyAsBoolean(base.properties(), GC_ENABLED, GC_ENABLED_DEFAULT),
        "Cannot expire snapshots: GC is disabled (deleting files may corrupt other tables)");

    long defaultMaxSnapshotAgeMs =
        PropertyUtil.propertyAsLong(
            base.properties(), MAX_SNAPSHOT_AGE_MS, MAX_SNAPSHOT_AGE_MS_DEFAULT);

    this.now = System.currentTimeMillis();
    this.defaultExpireOlderThan = now - defaultMaxSnapshotAgeMs;
    this.defaultMinNumSnapshots =
        PropertyUtil.propertyAsInt(
            base.properties(), MIN_SNAPSHOTS_TO_KEEP, MIN_SNAPSHOTS_TO_KEEP_DEFAULT);

    this.defaultMaxRefAgeMs =
        PropertyUtil.propertyAsLong(base.properties(), MAX_REF_AGE_MS, MAX_REF_AGE_MS_DEFAULT);
  }

  /** 设置是否在过期快照后清理物理文件（默认 true）。 */
  @Override
  public ExpireSnapshots cleanExpiredFiles(boolean clean) {
    this.cleanExpiredFiles = clean;
    return this;
  }

  /** 指定要过期的具体快照 id。 */
  @Override
  public ExpireSnapshots expireSnapshotId(long expireSnapshotId) {
    LOG.info("Expiring snapshot with id: {}", expireSnapshotId);
    idsToRemove.add(expireSnapshotId);
    return this;
  }

  /** 过期时间戳早于指定值的快照。 */
  @Override
  public ExpireSnapshots expireOlderThan(long timestampMillis) {
    LOG.info(
        "Expiring snapshots older than: {} ({})",
        DateTimeUtil.formatTimestampMillis(timestampMillis),
        timestampMillis);
    this.defaultExpireOlderThan = timestampMillis;
    return this;
  }

  /** 每个分支至少保留最近 N 个快照。 */
  @Override
  public ExpireSnapshots retainLast(int numSnapshots) {
    Preconditions.checkArgument(
        1 <= numSnapshots,
        "Number of snapshots to retain must be at least 1, cannot be: %s",
        numSnapshots);
    this.defaultMinNumSnapshots = numSnapshots;
    return this;
  }

  /** 设置自定义删除回调（替代默认 fileIO.deleteFile）。 */
  @Override
  public ExpireSnapshots deleteWith(Consumer<String> newDeleteFunc) {
    this.deleteFunc = newDeleteFunc;
    return this;
  }

  /** 设置执行删除操作的线程池。 */
  @Override
  public ExpireSnapshots executeDeleteWith(ExecutorService executorService) {
    this.deleteExecutorService = executorService;
    return this;
  }

  /** 设置规划（读取 manifest）使用的线程池。 */
  @Override
  public ExpireSnapshots planWith(ExecutorService executorService) {
    this.planExecutorService = executorService;
    return this;
  }

  /**
   * 计算过期结果但不提交：返回被移除的快照列表。
   *
   * <p>逻辑：调用 {@link #internalApply()} 计算更新后的元数据，对比 before/after 快照列表得到差集。
   *
   * @return 被过期的快照列表
   */
  @Override
  public List<Snapshot> apply() {
    TableMetadata updated = internalApply();
    List<Snapshot> removed = Lists.newArrayList(base.snapshots());
    removed.removeAll(updated.snapshots());

    return removed;
  }

  /**
   * 计算更新后的表元数据（不提交）。
   *
   * <p>逻辑：计算保留的 ref、保留的分支快照、未被引用但未过期的快照； 把其余快照加入 idsToRemove 并从元数据构建器中移除。
   *
   * @return 更新后的表元数据
   */
  private TableMetadata internalApply() {
    this.base = ops.refresh();
    if (base.snapshots().isEmpty()) {
      return base;
    }

    Set<Long> idsToRetain = Sets.newHashSet();
    // Identify refs that should be removed
    Map<String, SnapshotRef> retainedRefs = computeRetainedRefs(base.refs());
    Map<Long, List<String>> retainedIdToRefs = Maps.newHashMap();
    for (Map.Entry<String, SnapshotRef> retainedRefEntry : retainedRefs.entrySet()) {
      long snapshotId = retainedRefEntry.getValue().snapshotId();
      retainedIdToRefs.putIfAbsent(snapshotId, Lists.newArrayList());
      retainedIdToRefs.get(snapshotId).add(retainedRefEntry.getKey());
      idsToRetain.add(snapshotId);
    }

    for (long idToRemove : idsToRemove) {
      List<String> refsForId = retainedIdToRefs.get(idToRemove);
      Preconditions.checkArgument(
          refsForId == null,
          "Cannot expire %s. Still referenced by refs: %s",
          idToRemove,
          refsForId);
    }

    idsToRetain.addAll(computeAllBranchSnapshotsToRetain(retainedRefs.values()));
    idsToRetain.addAll(unreferencedSnapshotsToRetain(retainedRefs.values()));

    TableMetadata.Builder updatedMetaBuilder = TableMetadata.buildFrom(base);

    base.refs().keySet().stream()
        .filter(ref -> !retainedRefs.containsKey(ref))
        .forEach(updatedMetaBuilder::removeRef);

    base.snapshots().stream()
        .map(Snapshot::snapshotId)
        .filter(snapshot -> !idsToRetain.contains(snapshot))
        .forEach(idsToRemove::add);
    updatedMetaBuilder.removeSnapshots(idsToRemove);

    return updatedMetaBuilder.build();
  }

  /** 计算保留的 ref：main 始终保留；其余按 ref 的 maxRefAgeMs 与当前时间比较决定是否保留。 */
  private Map<String, SnapshotRef> computeRetainedRefs(Map<String, SnapshotRef> refs) {
    Map<String, SnapshotRef> retainedRefs = Maps.newHashMap();
    for (Map.Entry<String, SnapshotRef> refEntry : refs.entrySet()) {
      String name = refEntry.getKey();
      SnapshotRef ref = refEntry.getValue();
      if (name.equals(SnapshotRef.MAIN_BRANCH)) {
        retainedRefs.put(name, ref);
        continue;
      }

      Snapshot snapshot = base.snapshot(ref.snapshotId());
      long maxRefAgeMs = ref.maxRefAgeMs() != null ? ref.maxRefAgeMs() : defaultMaxRefAgeMs;
      if (snapshot != null) {
        long refAgeMs = now - snapshot.timestampMillis();
        if (refAgeMs <= maxRefAgeMs) {
          retainedRefs.put(name, ref);
        }
      } else {
        LOG.warn("Removing invalid ref {}: snapshot {} does not exist", name, ref.snapshotId());
      }
    }

    return retainedRefs;
  }

  /** 汇总所有分支需保留的快照：对每个分支按其 maxSnapshotAgeMs/minSnapshotsToKeep 配置保留。 */
  private Set<Long> computeAllBranchSnapshotsToRetain(Collection<SnapshotRef> refs) {
    Set<Long> branchSnapshotsToRetain = Sets.newHashSet();
    for (SnapshotRef ref : refs) {
      if (ref.isBranch()) {
        long expireSnapshotsOlderThan =
            ref.maxSnapshotAgeMs() != null ? now - ref.maxSnapshotAgeMs() : defaultExpireOlderThan;
        int minSnapshotsToKeep =
            ref.minSnapshotsToKeep() != null ? ref.minSnapshotsToKeep() : defaultMinNumSnapshots;
        branchSnapshotsToRetain.addAll(
            computeBranchSnapshotsToRetain(
                ref.snapshotId(), expireSnapshotsOlderThan, minSnapshotsToKeep));
      }
    }

    return branchSnapshotsToRetain;
  }

  /** 沿祖先链保留快照：至少保留 minSnapshotsToKeep 个，或时间戳不早于 expireSnapshotsOlderThan 的。 */
  private Set<Long> computeBranchSnapshotsToRetain(
      long snapshot, long expireSnapshotsOlderThan, int minSnapshotsToKeep) {
    Set<Long> idsToRetain = Sets.newHashSet();
    for (Snapshot ancestor : SnapshotUtil.ancestorsOf(snapshot, base::snapshot)) {
      if (idsToRetain.size() < minSnapshotsToKeep
          || ancestor.timestampMillis() >= expireSnapshotsOlderThan) {
        idsToRetain.add(ancestor.snapshotId());
      } else {
        return idsToRetain;
      }
    }

    return idsToRetain;
  }

  /** 计算未被任何 ref 引用但仍需保留的快照：未被引用且时间戳不早于默认过期阈值。 */
  private Set<Long> unreferencedSnapshotsToRetain(Collection<SnapshotRef> refs) {
    Set<Long> referencedSnapshots = Sets.newHashSet();
    for (SnapshotRef ref : refs) {
      if (ref.isBranch()) {
        for (Snapshot snapshot : SnapshotUtil.ancestorsOf(ref.snapshotId(), base::snapshot)) {
          referencedSnapshots.add(snapshot.snapshotId());
        }
      } else {
        referencedSnapshots.add(ref.snapshotId());
      }
    }

    Set<Long> snapshotsToRetain = Sets.newHashSet();
    for (Snapshot snapshot : base.snapshots()) {
      if (!referencedSnapshots.contains(snapshot.snapshotId())
          && // unreferenced
          snapshot.timestampMillis() >= defaultExpireOlderThan) { // not old enough to expire
        snapshotsToRetain.add(snapshot.snapshotId());
      }
    }

    return snapshotsToRetain;
  }

  /**
   * 提交快照过期。
   *
   * <p>逻辑：使用 {@link Tasks} 重试机制提交 {@link #internalApply()} 计算的元数据； 提交成功后若 cleanExpiredFiles 为
   * true，则调用 {@link #cleanExpiredSnapshots()} 清理文件。
   *
   * @throws CommitFailedException 提交冲突时抛出（会重试）
   */
  @Override
  public void commit() {
    Tasks.foreach(ops)
        .retry(base.propertyAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
        .exponentialBackoff(
            base.propertyAsInt(COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
            2.0 /* exponential */)
        .onlyRetryOn(CommitFailedException.class)
        .run(
            item -> {
              TableMetadata updated = internalApply();
              ops.commit(base, updated);
            });
    LOG.info("Committed snapshot changes");

    if (cleanExpiredFiles) {
      cleanExpiredSnapshots();
    }
  }

  /** 显式指定是否使用增量清理策略。 */
  ExpireSnapshots withIncrementalCleanup(boolean useIncrementalCleanup) {
    this.incrementalCleanup = useIncrementalCleanup;
    return this;
  }

  /**
   * 执行物理文件清理。
   *
   * <p>逻辑：刷新当前元数据；若未显式指定 incrementalCleanup，则单 ref 时默认增量、多 ref 时使用可达性清理； 构造对应 {@link
   * FileCleanupStrategy} 并执行 cleanFiles。
   */
  private void cleanExpiredSnapshots() {
    TableMetadata current = ops.refresh();

    if (incrementalCleanup == null) {
      incrementalCleanup = current.refs().size() == 1;
    }

    LOG.info(
        "Cleaning up expired files (local, {})", incrementalCleanup ? "incremental" : "reachable");

    FileCleanupStrategy cleanupStrategy =
        incrementalCleanup
            ? new IncrementalFileCleanup(
                ops.io(), deleteExecutorService, planExecutorService, deleteFunc)
            : new ReachableFileCleanup(
                ops.io(), deleteExecutorService, planExecutorService, deleteFunc);

    cleanupStrategy.cleanFiles(base, current);
  }
}
