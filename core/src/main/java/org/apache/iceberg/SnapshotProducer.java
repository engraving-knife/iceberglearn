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
import static org.apache.iceberg.TableProperties.MANIFEST_TARGET_SIZE_BYTES;
import static org.apache.iceberg.TableProperties.MANIFEST_TARGET_SIZE_BYTES_DEFAULT;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.iceberg.events.CreateSnapshotEvent;
import org.apache.iceberg.events.Listeners;
import org.apache.iceberg.exceptions.CleanableFailure;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.CommitMetrics;
import org.apache.iceberg.metrics.CommitMetricsResult;
import org.apache.iceberg.metrics.DefaultMetricsContext;
import org.apache.iceberg.metrics.ImmutableCommitReport;
import org.apache.iceberg.metrics.LoggingMetricsReporter;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.metrics.Timer.Timed;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.Exceptions;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnnecessaryAnonymousClass")

/**
 * 快照提交生产者基类（创建新快照的提交骨架）。
 *
 * <p>所属模块：iceberg-core。职责：为所有"产生新快照"的写操作（append/overwrite/rewrite 等）提供统一 的提交流程：生成新 manifest、写入
 * manifest-list、构造新 {@link Snapshot}、提交 {@link TableOperations} 并处理冲突重试。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法：子类实现 {@code apply} 等钩子，本类负责提交编排、冲突处理、指标上报。
 *   <li>OCC 重试：捕获 {@link CommitFailedException}，刷新元数据后基于新基线重试。
 *   <li>任务指标：通过 {@link CommitMetrics} 上报提交耗时/重试次数。
 *   <li>事件通知：提交成功后通过 {@link Listeners} 触发 {@link CreateSnapshotEvent}。
 *   <li>失败清理：对 {@link CleanableFailure} 清理已写入的 manifest 文件。
 * </ul>
 *
 * <p>上下游关系：被 {@link MergeAppendFiles}、{@link OverwriteData} 等具体写操作继承； 依赖 {@link
 * TableOperations}、{@link ManifestList}、{@link ManifestWriter}。
 */
abstract class SnapshotProducer<ThisT> implements SnapshotUpdate<ThisT> {
  private static final Logger LOG = LoggerFactory.getLogger(SnapshotProducer.class);
  static final Set<ManifestFile> EMPTY_SET = Sets.newHashSet();

  /** Default callback used to delete files. */
  private final Consumer<String> defaultDelete =
      new Consumer<String>() {
        @Override
        public void accept(String file) {
          ops.io().deleteFile(file);
        }
      };

  /** Cache used to enrich ManifestFile instances that are written to a ManifestListWriter. */
  private final LoadingCache<ManifestFile, ManifestFile> manifestsWithMetadata;

  private final TableOperations ops;
  private final boolean strictCleanup;
  private final String commitUUID = UUID.randomUUID().toString();
  private final AtomicInteger manifestCount = new AtomicInteger(0);
  private final AtomicInteger attempt = new AtomicInteger(0);
  private final List<String> manifestLists = Lists.newArrayList();
  private final long targetManifestSizeBytes;
  private MetricsReporter reporter = LoggingMetricsReporter.instance();
  private volatile Long snapshotId = null;
  private TableMetadata base;
  private boolean stageOnly = false;
  private Consumer<String> deleteFunc = defaultDelete;

  private ExecutorService workerPool = ThreadPools.getWorkerPool();
  private String targetBranch = SnapshotRef.MAIN_BRANCH;
  private CommitMetrics commitMetrics;

  /**
   * 构造方法：初始化 SnapshotProducer 实例。
   *
   * @param ops 参数
   */
  protected SnapshotProducer(TableOperations ops) {
    this.ops = ops;
    this.strictCleanup = ops.requireStrictCleanup();
    this.base = ops.current();
    this.manifestsWithMetadata =
        Caffeine.newBuilder()
            .build(
                file -> {
                  if (file.snapshotId() != null) {
                    return file;
                  }
                  return addMetadata(ops, file);
                });
    this.targetManifestSizeBytes =
        ops.current()
            .propertyAsLong(MANIFEST_TARGET_SIZE_BYTES, MANIFEST_TARGET_SIZE_BYTES_DEFAULT);
  }

  /**
   * 返回当前实例（用于链式调用）。
   *
   * @return 返回值
   */
  protected abstract ThisT self();

  /**
   * 设置是否仅 stage（不设为 current）。
   *
   * @return 返回值
   */
  @Override
  public ThisT stageOnly() {
    this.stageOnly = true;
    return self();
  }

  /**
   * 使用指定线程池规划 manifest 文件。
   *
   * @param executorService 参数
   * @return 返回值
   */
  @Override
  public ThisT scanManifestsWith(ExecutorService executorService) {
    this.workerPool = executorService;
    return self();
  }

  /**
   * 提交变更到底层 TableOperations。
   *
   * @return 返回值
   */
  protected CommitMetrics commitMetrics() {
    if (commitMetrics == null) {
      this.commitMetrics = CommitMetrics.of(new DefaultMetricsContext());
    }

    return commitMetrics;
  }

  /**
   * 设置提交报告回调。
   *
   * @param newReporter 参数
   * @return 返回值
   */
  protected ThisT reportWith(MetricsReporter newReporter) {
    this.reporter = newReporter;
    return self();
  }

  /**
   * 构造方法：初始化 targetBranch 实例。
   *
   * <p>A setter for the target branch on which snapshot producer operation should be performed
   *
   * @param branch to set as target branch
   */
  protected void targetBranch(String branch) {
    Preconditions.checkArgument(branch != null, "Invalid branch name: null");
    boolean refExists = base.ref(branch) != null;
    Preconditions.checkArgument(
        !refExists || base.ref(branch).isBranch(),
        "%s is a tag, not a branch. Tags cannot be targets for producing snapshots",
        branch);
    this.targetBranch = branch;
  }

  /**
   * 设置目标分支。
   *
   * @return 返回值
   */
  protected String targetBranch() {
    return targetBranch;
  }

  /**
   * 设置工作线程池。
   *
   * @return 返回值
   */
  protected ExecutorService workerPool() {
    return this.workerPool;
  }

  /**
   * 移除With。
   *
   * @param deleteCallback 参数
   * @return 返回值
   */
  @Override
  public ThisT deleteWith(Consumer<String> deleteCallback) {
    Preconditions.checkArgument(
        this.deleteFunc == defaultDelete, "Cannot set delete callback more than once");
    this.deleteFunc = deleteCallback;
    return self();
  }

  /**
   * 构造方法：初始化 cleanUncommitted 实例。
   *
   * <p>Clean up any uncommitted manifests that were created.
   *
   * <p>Manifests may not be committed if apply is called more because a commit conflict has
   * occurred. Implementations may keep around manifests because the same changes will be made by
   * both apply calls. This method instructs the implementation to clean up those manifests and
   * passes the paths of the manifests that were actually committed.
   *
   * @param committed a set of manifest paths that were actually committed
   */
  protected abstract void cleanUncommitted(Set<ManifestFile> committed);

  /**
   * 返回操作类型。
   *
   * <p>A string that describes the action that produced the new snapshot.
   *
   * @return a string operation
   */
  protected abstract String operation();

  /**
   * 构造方法：初始化 validate 实例。
   *
   * <p>Validate the current metadata.
   *
   * <p>Child operations can override this to add custom validation.
   *
   * @param currentMetadata current table metadata to validate
   * @param snapshot ending snapshot on the lineage which is being validated
   */
  protected void validate(TableMetadata currentMetadata, Snapshot snapshot) {}

  /**
   * 应用累积变更，生成新的表元数据。
   *
   * <p>Apply the update's changes to the given metadata and snapshot. Return the new manifest list.
   *
   * @param metadataToUpdate the base table metadata to apply changes to
   * @param snapshot snapshot to apply the changes to
   * @return a manifest list for the new snapshot.
   */
  protected abstract List<ManifestFile> apply(TableMetadata metadataToUpdate, Snapshot snapshot);

  /**
   * 应用累积变更，生成新的表元数据。
   *
   * @return 返回值
   */
  @Override
  public Snapshot apply() {
    refresh();
    Snapshot parentSnapshot = SnapshotUtil.latestSnapshot(base, targetBranch);

    long sequenceNumber = base.nextSequenceNumber();
    Long parentSnapshotId = parentSnapshot == null ? null : parentSnapshot.snapshotId();

    validate(base, parentSnapshot);
    List<ManifestFile> manifests = apply(base, parentSnapshot);

    OutputFile manifestList = manifestListPath();

    try (ManifestListWriter writer =
        ManifestLists.write(
            ops.current().formatVersion(),
            manifestList,
            snapshotId(),
            parentSnapshotId,
            sequenceNumber)) {

      // keep track of the manifest lists created
      manifestLists.add(manifestList.location());

      ManifestFile[] manifestFiles = new ManifestFile[manifests.size()];

      Tasks.range(manifestFiles.length)
          .stopOnFailure()
          .throwFailureWhenFinished()
          .executeWith(workerPool)
          .run(index -> manifestFiles[index] = manifestsWithMetadata.get(manifests.get(index)));

      writer.addAll(Arrays.asList(manifestFiles));

    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to write manifest list file");
    }

    return new BaseSnapshot(
        sequenceNumber,
        snapshotId(),
        parentSnapshotId,
        System.currentTimeMillis(),
        operation(),
        summary(base),
        base.currentSchemaId(),
        manifestList.location());
  }

  protected abstract Map<String, String> summary();

  /** Returns the snapshot summary from the implementation and updates totals. */
  private Map<String, String> summary(TableMetadata previous) {
    Map<String, String> summary = summary();

    if (summary == null) {
      return ImmutableMap.of();
    }

    Map<String, String> previousSummary;
    SnapshotRef previousBranchHead = previous.ref(targetBranch);
    if (previousBranchHead != null) {
      if (previous.snapshot(previousBranchHead.snapshotId()).summary() != null) {
        previousSummary = previous.snapshot(previousBranchHead.snapshotId()).summary();
      } else {
        // previous snapshot had no summary, use an empty summary
        previousSummary = ImmutableMap.of();
      }
    } else {
      // if there was no previous snapshot, default the summary to start totals at 0
      ImmutableMap.Builder<String, String> summaryBuilder = ImmutableMap.builder();
      summaryBuilder
          .put(SnapshotSummary.TOTAL_RECORDS_PROP, "0")
          .put(SnapshotSummary.TOTAL_FILE_SIZE_PROP, "0")
          .put(SnapshotSummary.TOTAL_DATA_FILES_PROP, "0")
          .put(SnapshotSummary.TOTAL_DELETE_FILES_PROP, "0")
          .put(SnapshotSummary.TOTAL_POS_DELETES_PROP, "0")
          .put(SnapshotSummary.TOTAL_EQ_DELETES_PROP, "0");
      previousSummary = summaryBuilder.build();
    }

    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    // copy all summary properties from the implementation
    builder.putAll(summary);

    updateTotal(
        builder,
        previousSummary,
        SnapshotSummary.TOTAL_RECORDS_PROP,
        summary,
        SnapshotSummary.ADDED_RECORDS_PROP,
        SnapshotSummary.DELETED_RECORDS_PROP);
    updateTotal(
        builder,
        previousSummary,
        SnapshotSummary.TOTAL_FILE_SIZE_PROP,
        summary,
        SnapshotSummary.ADDED_FILE_SIZE_PROP,
        SnapshotSummary.REMOVED_FILE_SIZE_PROP);
    updateTotal(
        builder,
        previousSummary,
        SnapshotSummary.TOTAL_DATA_FILES_PROP,
        summary,
        SnapshotSummary.ADDED_FILES_PROP,
        SnapshotSummary.DELETED_FILES_PROP);
    updateTotal(
        builder,
        previousSummary,
        SnapshotSummary.TOTAL_DELETE_FILES_PROP,
        summary,
        SnapshotSummary.ADDED_DELETE_FILES_PROP,
        SnapshotSummary.REMOVED_DELETE_FILES_PROP);
    updateTotal(
        builder,
        previousSummary,
        SnapshotSummary.TOTAL_POS_DELETES_PROP,
        summary,
        SnapshotSummary.ADDED_POS_DELETES_PROP,
        SnapshotSummary.REMOVED_POS_DELETES_PROP);
    updateTotal(
        builder,
        previousSummary,
        SnapshotSummary.TOTAL_EQ_DELETES_PROP,
        summary,
        SnapshotSummary.ADDED_EQ_DELETES_PROP,
        SnapshotSummary.REMOVED_EQ_DELETES_PROP);

    return builder.build();
  }

  /**
   * 返回当前表元数据。
   *
   * @return 返回值
   */
  protected TableMetadata current() {
    return base;
  }

  /**
   * 刷新表元数据。
   *
   * @return 返回值
   */
  protected TableMetadata refresh() {
    this.base = ops.refresh();
    return base;
  }

  @Override
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public void commit() {
    // this is always set to the latest commit attempt's snapshot id.
    AtomicLong newSnapshotId = new AtomicLong(-1L);
    Timed totalDuration = commitMetrics().totalDuration().start();
    try {
      Tasks.foreach(ops)
          .retry(base.propertyAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
          .exponentialBackoff(
              base.propertyAsInt(COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
              base.propertyAsInt(COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
              base.propertyAsInt(COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
              2.0 /* exponential */)
          .onlyRetryOn(CommitFailedException.class)
          .countAttempts(commitMetrics().attempts())
          .run(
              taskOps -> {
                Snapshot newSnapshot = apply();
                newSnapshotId.set(newSnapshot.snapshotId());
                TableMetadata.Builder update = TableMetadata.buildFrom(base);
                if (base.snapshot(newSnapshot.snapshotId()) != null) {
                  // this is a rollback operation
                  update.setBranchSnapshot(newSnapshot.snapshotId(), targetBranch);
                } else if (stageOnly) {
                  update.addSnapshot(newSnapshot);
                } else {
                  update.setBranchSnapshot(newSnapshot, targetBranch);
                }

                TableMetadata updated = update.build();
                if (updated.changes().isEmpty()) {
                  // do not commit if the metadata has not changed. for example, this may happen
                  // when setting the current
                  // snapshot to an ID that is already current. note that this check uses identity.
                  return;
                }

                // if the table UUID is missing, add it here. the UUID will be re-created each time
                // this operation retries
                // to ensure that if a concurrent operation assigns the UUID, this operation will
                // not fail.
                taskOps.commit(base, updated.withUUID());
              });

    } catch (CommitStateUnknownException commitStateUnknownException) {
      throw commitStateUnknownException;
    } catch (RuntimeException e) {
      if (!strictCleanup || e instanceof CleanableFailure) {
        Exceptions.suppressAndThrow(e, this::cleanAll);
      }

      throw e;
    }

    try {
      LOG.info("Committed snapshot {} ({})", newSnapshotId.get(), getClass().getSimpleName());

      // at this point, the commit must have succeeded. after a refresh, the snapshot is loaded by
      // id in case another commit was added between this commit and the refresh.
      Snapshot saved = ops.refresh().snapshot(newSnapshotId.get());
      if (saved != null) {
        cleanUncommitted(Sets.newHashSet(saved.allManifests(ops.io())));
        // also clean up unused manifest lists created by multiple attempts
        for (String manifestList : manifestLists) {
          if (!saved.manifestListLocation().equals(manifestList)) {
            deleteFile(manifestList);
          }
        }
      } else {
        // saved may not be present if the latest metadata couldn't be loaded due to eventual
        // consistency problems in refresh. in that case, don't clean up.
        LOG.warn("Failed to load committed snapshot, skipping manifest clean-up");
      }

    } catch (Throwable e) {
      LOG.warn(
          "Failed to load committed table metadata or during cleanup, skipping further cleanup", e);
    }

    totalDuration.stop();

    try {
      notifyListeners();
    } catch (Throwable e) {
      LOG.warn("Failed to notify event listeners", e);
    }
  }

  private void notifyListeners() {
    try {
      Object event = updateEvent();
      if (event != null) {
        Listeners.notifyAll(event);

        if (event instanceof CreateSnapshotEvent) {
          CreateSnapshotEvent createSnapshotEvent = (CreateSnapshotEvent) event;

          reporter.report(
              ImmutableCommitReport.builder()
                  .tableName(createSnapshotEvent.tableName())
                  .snapshotId(createSnapshotEvent.snapshotId())
                  .operation(createSnapshotEvent.operation())
                  .sequenceNumber(createSnapshotEvent.sequenceNumber())
                  .metadata(EnvironmentContext.get())
                  .commitMetrics(
                      CommitMetricsResult.from(commitMetrics(), createSnapshotEvent.summary()))
                  .build());
        }
      }
    } catch (RuntimeException e) {
      LOG.warn("Failed to notify listeners", e);
    }
  }

  protected void cleanAll() {
    for (String manifestList : manifestLists) {
      deleteFile(manifestList);
    }
    manifestLists.clear();
    cleanUncommitted(EMPTY_SET);
  }

  /**
   * 构造方法：初始化 deleteFile 实例。
   *
   * @param path 参数
   */
  protected void deleteFile(String path) {
    deleteFunc.accept(path);
  }

  /**
   * 返回 manifest 列表文件路径。
   *
   * @return 返回值
   */
  protected OutputFile manifestListPath() {
    return ops.io()
        .newOutputFile(
            ops.metadataFileLocation(
                FileFormat.AVRO.addExtension(
                    String.format(
                        "snap-%d-%d-%s", snapshotId(), attempt.incrementAndGet(), commitUUID))));
  }

  /**
   * 创建新的 manifest 输出文件。
   *
   * @return 返回值
   */
  protected OutputFile newManifestOutput() {
    return ops.io()
        .newOutputFile(
            ops.metadataFileLocation(
                FileFormat.AVRO.addExtension(commitUUID + "-m" + manifestCount.getAndIncrement())));
  }

  /**
   * 创建新的 manifest writer。
   *
   * @param spec 参数
   * @return {@code ManifestWriter<DataFile>} 返回值
   */
  protected ManifestWriter<DataFile> newManifestWriter(PartitionSpec spec) {
    return ManifestFiles.write(
        ops.current().formatVersion(), spec, newManifestOutput(), snapshotId());
  }

  /**
   * 创建新的删除文件 manifest writer。
   *
   * @param spec 参数
   * @return {@code ManifestWriter<DeleteFile>} 返回值
   */
  protected ManifestWriter<DeleteFile> newDeleteManifestWriter(PartitionSpec spec) {
    return ManifestFiles.writeDeleteManifest(
        ops.current().formatVersion(), spec, newManifestOutput(), snapshotId());
  }

  /**
   * 创建滚动写入的 manifest writer。
   *
   * @param spec 参数
   * @return {@code RollingManifestWriter<DataFile>} 返回值
   */
  protected RollingManifestWriter<DataFile> newRollingManifestWriter(PartitionSpec spec) {
    return new RollingManifestWriter<>(() -> newManifestWriter(spec), targetManifestSizeBytes);
  }

  /**
   * 创建滚动写入的删除文件 manifest writer。
   *
   * @param spec 参数
   * @return {@code RollingManifestWriter<DeleteFile>} 返回值
   */
  protected RollingManifestWriter<DeleteFile> newRollingDeleteManifestWriter(PartitionSpec spec) {
    return new RollingManifestWriter<>(
        () -> newDeleteManifestWriter(spec), targetManifestSizeBytes);
  }

  /**
   * 创建 manifest reader。
   *
   * @param manifest 参数
   * @return {@code ManifestReader<DataFile>} 返回值
   */
  protected ManifestReader<DataFile> newManifestReader(ManifestFile manifest) {
    return ManifestFiles.read(manifest, ops.io(), ops.current().specsById());
  }

  /**
   * 创建删除文件 manifest reader。
   *
   * @param manifest 参数
   * @return {@code ManifestReader<DeleteFile>} 返回值
   */
  protected ManifestReader<DeleteFile> newDeleteManifestReader(ManifestFile manifest) {
    return ManifestFiles.readDeleteManifest(manifest, ops.io(), ops.current().specsById());
  }

  /**
   * 返回快照 id。
   *
   * @return 返回值
   */
  protected long snapshotId() {
    if (snapshotId == null) {
      synchronized (this) {
        while (snapshotId == null || ops.current().snapshot(snapshotId) != null) {
          this.snapshotId = ops.newSnapshotId();
        }
      }
    }
    return snapshotId;
  }

  /**
   * 添加Metadata。
   *
   * @param ops 参数
   * @param manifest 参数
   * @return 返回值
   */
  private static ManifestFile addMetadata(TableOperations ops, ManifestFile manifest) {
    try (ManifestReader<DataFile> reader =
        ManifestFiles.read(manifest, ops.io(), ops.current().specsById())) {
      PartitionSummary stats = new PartitionSummary(ops.current().spec(manifest.partitionSpecId()));
      int addedFiles = 0;
      long addedRows = 0L;
      int existingFiles = 0;
      long existingRows = 0L;
      int deletedFiles = 0;
      long deletedRows = 0L;

      Long snapshotId = null;
      long maxSnapshotId = Long.MIN_VALUE;
      for (ManifestEntry<DataFile> entry : reader.entries()) {
        if (entry.snapshotId() > maxSnapshotId) {
          maxSnapshotId = entry.snapshotId();
        }

        switch (entry.status()) {
          case ADDED:
            addedFiles += 1;
            addedRows += entry.file().recordCount();
            if (snapshotId == null) {
              snapshotId = entry.snapshotId();
            }
            break;
          case EXISTING:
            existingFiles += 1;
            existingRows += entry.file().recordCount();
            break;
          case DELETED:
            deletedFiles += 1;
            deletedRows += entry.file().recordCount();
            if (snapshotId == null) {
              snapshotId = entry.snapshotId();
            }
            break;
        }

        stats.update(entry.file().partition());
      }

      if (snapshotId == null) {
        // if no files were added or deleted, use the largest snapshot ID in the manifest
        snapshotId = maxSnapshotId;
      }

      return new GenericManifestFile(
          manifest.path(),
          manifest.length(),
          manifest.partitionSpecId(),
          ManifestContent.DATA,
          manifest.sequenceNumber(),
          manifest.minSequenceNumber(),
          snapshotId,
          addedFiles,
          addedRows,
          existingFiles,
          existingRows,
          deletedFiles,
          deletedRows,
          stats.summaries(),
          null);

    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to read manifest: %s", manifest.path());
    }
  }

  private static void updateTotal(
      ImmutableMap.Builder<String, String> summaryBuilder,
      Map<String, String> previousSummary,
      String totalProperty,
      Map<String, String> currentSummary,
      String addedProperty,
      String deletedProperty) {
    String totalStr = previousSummary.get(totalProperty);
    if (totalStr != null) {
      try {
        long newTotal = Long.parseLong(totalStr);

        String addedStr = currentSummary.get(addedProperty);
        if (newTotal >= 0 && addedStr != null) {
          newTotal += Long.parseLong(addedStr);
        }

        String deletedStr = currentSummary.get(deletedProperty);
        if (newTotal >= 0 && deletedStr != null) {
          newTotal -= Long.parseLong(deletedStr);
        }

        if (newTotal >= 0) {
          summaryBuilder.put(totalProperty, String.valueOf(newTotal));
        }

      } catch (NumberFormatException e) {
        // ignore and do not add total
      }
    }
  }
}
