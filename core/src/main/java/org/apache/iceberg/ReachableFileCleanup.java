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

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 所属模块：iceberg-core；快照过期清理（File Cleanup）策略层。
 *
 * <p>职责：在快照过期时，基于"可达性（reachability）"判断哪些元数据与数据文件不再被任何存留快照 引用，从而可以安全删除。具体包括：
 *
 * <ul>
 *   <li>对比过期前后的表状态，识别被淘汰的快照及其 Manifest 列表
 *   <li>通过内存引用集合剔除仍被现有快照引用的 Manifest，得到待删除 Manifest
 *   <li>进一步判断 Manifest 中哪些数据文件不再被任何当前 Manifest 引用
 *   <li>并行删除孤立的 data 文件、Manifest 文件、Manifest 列表与统计文件
 * </ul>
 *
 * <p>设计意图：使用内存中的引用集合（而非重命名等外部手段）来判断可达性，策略简单且无副作用； 通过 {@link ExecutorService} 并行读取与删除以提升大表清理性能；任务通过
 * {@link Tasks} 框架 提供重试与失败抑制能力，保证部分失败不影响整体流程。
 *
 * <p>上下游关系：继承 {@link FileCleanupStrategy}，由快照过期流程调用；依赖 {@link FileIO}、 {@link ManifestFiles}
 * 等读取与删除文件。
 */
class ReachableFileCleanup extends FileCleanupStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(ReachableFileCleanup.class);

  /**
   * 构造可达性文件清理器。
   *
   * @param fileIO 用于读写文件
   * @param deleteExecutorService 执行删除任务的线程池
   * @param planExecutorService 执行规划（读取 Manifest）任务的线程池
   * @param deleteFunc 实际执行文件删除的回调
   */
  ReachableFileCleanup(
      FileIO fileIO,
      ExecutorService deleteExecutorService,
      ExecutorService planExecutorService,
      Consumer<String> deleteFunc) {
    super(fileIO, deleteExecutorService, planExecutorService, deleteFunc);
  }

  /**
   * 清理过期快照不再可达的文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>比较 before/after 表状态的快照集合，找出已过期快照并收集其 Manifest 列表路径
   *   <li>读取过期快照的所有 Manifest 作为删除候选
   *   <li>遍历存留快照的 Manifest，从候选集中剔除仍被引用的（同时收集当前 Manifest 集合）
   *   <li>对剩余待删除 Manifest，找出其中不再被任何当前 Manifest 引用的数据文件并删除
   *   <li>删除待删除 Manifest 自身、过期快照的 Manifest 列表与已过期的统计文件
   * </ol>
   *
   * @param beforeExpiration 过期前的表元数据
   * @param afterExpiration 过期后的表元数据
   */
  @Override
  public void cleanFiles(TableMetadata beforeExpiration, TableMetadata afterExpiration) {
    Set<String> manifestListsToDelete = Sets.newHashSet();

    Set<Snapshot> snapshotsBeforeExpiration = Sets.newHashSet(beforeExpiration.snapshots());
    Set<Snapshot> snapshotsAfterExpiration = Sets.newHashSet(afterExpiration.snapshots());
    Set<Snapshot> expiredSnapshots = Sets.newHashSet();
    for (Snapshot snapshot : snapshotsBeforeExpiration) {
      if (!snapshotsAfterExpiration.contains(snapshot)) {
        expiredSnapshots.add(snapshot);
        if (snapshot.manifestListLocation() != null) {
          manifestListsToDelete.add(snapshot.manifestListLocation());
        }
      }
    }
    Set<ManifestFile> deletionCandidates = readManifests(expiredSnapshots);

    if (!deletionCandidates.isEmpty()) {
      Set<ManifestFile> currentManifests = ConcurrentHashMap.newKeySet();
      Set<ManifestFile> manifestsToDelete =
          pruneReferencedManifests(
              snapshotsAfterExpiration, deletionCandidates, currentManifests::add);

      if (!manifestsToDelete.isEmpty()) {
        Set<String> dataFilesToDelete = findFilesToDelete(manifestsToDelete, currentManifests);
        deleteFiles(dataFilesToDelete, "data");
        Set<String> manifestPathsToDelete =
            manifestsToDelete.stream().map(ManifestFile::path).collect(Collectors.toSet());
        deleteFiles(manifestPathsToDelete, "manifest");
      }
    }

    deleteFiles(manifestListsToDelete, "manifest list");

    if (!beforeExpiration.statisticsFiles().isEmpty()) {
      deleteFiles(
          expiredStatisticsFilesLocations(beforeExpiration, afterExpiration), "statistics files");
    }
  }

  /**
   * 从删除候选集合中剔除仍被指定快照集合引用的 Manifest，返回真正可删除的 Manifest 集合。
   *
   * <p>逻辑：将候选集合放入并发集合中；并行遍历每个存留快照的 Manifest，从候选集中移除被引用者； 同时通过 {@code currentManifestCallback} 回收当前
   * Manifest 副本，供后续数据文件可达性判断使用。 候选集为空时提前返回。
   *
   * @param snapshots 当前存留的快照集合
   * @param deletionCandidates 待删除的 Manifest 候选集合
   * @param currentManifestCallback 当前（被引用）Manifest 的回调，用于收集到 currentManifests
   * @return 真正可删除的 Manifest 集合
   */
  private Set<ManifestFile> pruneReferencedManifests(
      Set<Snapshot> snapshots,
      Set<ManifestFile> deletionCandidates,
      Consumer<ManifestFile> currentManifestCallback) {
    Set<ManifestFile> candidateSet = ConcurrentHashMap.newKeySet();
    candidateSet.addAll(deletionCandidates);
    Tasks.foreach(snapshots)
        .retry(3)
        .stopOnFailure()
        .throwFailureWhenFinished()
        .executeWith(planExecutorService)
        .onFailure(
            (snapshot, exc) ->
                LOG.warn(
                    "Failed to determine manifests for snapshot {}", snapshot.snapshotId(), exc))
        .run(
            snapshot -> {
              try (CloseableIterable<ManifestFile> manifestFiles = readManifests(snapshot)) {
                for (ManifestFile manifestFile : manifestFiles) {
                  candidateSet.remove(manifestFile);
                  if (candidateSet.isEmpty()) {
                    return;
                  }

                  currentManifestCallback.accept(manifestFile.copy());
                }
              } catch (IOException e) {
                throw new RuntimeIOException(
                    e, "Failed to close manifest list: %s", snapshot.manifestListLocation());
              }
            });

    return candidateSet;
  }

  /**
   * 并行读取一组快照的所有 Manifest 文件，返回其拷贝集合。
   *
   * <p>逻辑：对每个快照调用 {@link #readManifests(Snapshot)} 读取其 Manifest 列表，并复制后放入 并发集合；IO 异常包装为 {@link
   * RuntimeIOException} 抛出。任务带 3 次重试。
   *
   * @param snapshots 待读取的快照集合
   * @return 所有快照的 Manifest 拷贝集合
   */
  private Set<ManifestFile> readManifests(Set<Snapshot> snapshots) {
    Set<ManifestFile> manifestFiles = ConcurrentHashMap.newKeySet();
    Tasks.foreach(snapshots)
        .retry(3)
        .stopOnFailure()
        .throwFailureWhenFinished()
        .executeWith(planExecutorService)
        .onFailure(
            (snapshot, exc) ->
                LOG.warn(
                    "Failed to determine manifests for snapshot {}", snapshot.snapshotId(), exc))
        .run(
            snapshot -> {
              try (CloseableIterable<ManifestFile> manifests = readManifests(snapshot)) {
                for (ManifestFile manifestFile : manifests) {
                  manifestFiles.add(manifestFile.copy());
                }
              } catch (IOException e) {
                throw new RuntimeIOException(
                    e, "Failed to close manifest list: %s", snapshot.manifestListLocation());
              }
            });

    return manifestFiles;
  }

  /**
   * 计算待删除 Manifest 中的数据文件里，哪些不再被任何当前 Manifest 引用。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>并行读取所有待删除 Manifest 的文件路径，加入候选删除集合
   *   <li>若候选集合为空，直接返回
   *   <li>并行遍历当前 Manifest，从候选集合中移除仍被引用的路径
   *   <li>若读取当前 Manifest 失败，则记录日志并返回空集合（保守不删除）
   * </ol>
   *
   * <p>设计要点：使用 {@code suppressFailureWhenFinished} 读取待删除 Manifest 的路径，即使部分失败 仍尽量收集候选；而当前 Manifest
   * 的读取使用 {@code throwFailureWhenFinished}，一旦失败则放弃 本次删除以防误删。
   *
   * @param manifestFilesToDelete 待删除的 Manifest 集合
   * @param currentManifestFiles 当前仍被引用的 Manifest 集合
   * @return 可安全删除的数据文件路径集合
   */
  // Helper to determine data files to delete
  private Set<String> findFilesToDelete(
      Set<ManifestFile> manifestFilesToDelete, Set<ManifestFile> currentManifestFiles) {
    Set<String> filesToDelete = ConcurrentHashMap.newKeySet();

    Tasks.foreach(manifestFilesToDelete)
        .retry(3)
        .suppressFailureWhenFinished()
        .executeWith(planExecutorService)
        .onFailure(
            (item, exc) ->
                LOG.warn(
                    "Failed to determine live files in manifest {}. Retrying", item.path(), exc))
        .run(
            manifest -> {
              try (CloseableIterable<String> paths = ManifestFiles.readPaths(manifest, fileIO)) {
                paths.forEach(filesToDelete::add);
              } catch (IOException e) {
                throw new RuntimeIOException(e, "Failed to read manifest file: %s", manifest);
              }
            });

    if (filesToDelete.isEmpty()) {
      return filesToDelete;
    }

    try {
      Tasks.foreach(currentManifestFiles)
          .retry(3)
          .stopOnFailure()
          .throwFailureWhenFinished()
          .executeWith(planExecutorService)
          .onFailure(
              (item, exc) ->
                  LOG.warn(
                      "Failed to determine live files in manifest {}. Retrying", item.path(), exc))
          .run(
              manifest -> {
                if (filesToDelete.isEmpty()) {
                  return;
                }

                // Remove all the live files from the candidate deletion set
                try (CloseableIterable<String> paths = ManifestFiles.readPaths(manifest, fileIO)) {
                  paths.forEach(filesToDelete::remove);
                } catch (IOException e) {
                  throw new RuntimeIOException(e, "Failed to read manifest file: %s", manifest);
                }
              });

    } catch (Throwable e) {
      LOG.warn("Failed to list all reachable files", e);
      return Sets.newHashSet();
    }

    return filesToDelete;
  }
}
