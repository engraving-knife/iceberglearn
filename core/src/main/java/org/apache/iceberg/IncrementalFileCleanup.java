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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 增量式文件清理策略：在快照过期后，删除不再被引用的数据文件、manifest 与 manifest list。
 *
 * <p>所属模块：iceberg-core（核心实现层），是 {@link FileCleanupStrategy} 的一种实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>识别过期快照中已被删除且不属于当前表状态的数据文件并删除。
 *   <li>识别不再被任何有效快照引用的 manifest 文件并删除。
 *   <li>删除过期快照对应的 manifest list 文件。
 *   <li>处理被回滚（rolled back）或被 cherry-pick 的快照，避免误删当前表状态中的文件。
 * </ul>
 *
 * <p>设计意图：增量清理只删除“确定安全”的文件——
 *
 * <ul>
 *   <li>对祖先链中的过期快照：删除其 DELETED 条目指向的文件（这些文件确实不再需要）。
 *   <li>对非祖先的过期快照：删除其 ADDED 条目指向的文件（这些文件从未进入当前表状态）。
 *   <li>cherry-pick 快照：跳过清理，等待被 pick 的原始快照过期时再清理。
 * </ul>
 *
 * 通过 {@link Tasks} 框架并发执行读取与删除，并 suppressFailureWhenFinished 以尽量完成清理。
 *
 * <p>上下游关系：被 {@link RemoveSnapshots} 在快照过期后调用；底层使用 {@link FileIO} 删除文件、 {@link ManifestReader} 读取
 * manifest 条目。
 */
class IncrementalFileCleanup extends FileCleanupStrategy {
  private static final Logger LOG = LoggerFactory.getLogger(IncrementalFileCleanup.class);

  /**
   * 构造方法。
   *
   * @param fileIO 文件 IO
   * @param deleteExecutorService 删除执行线程池
   * @param planExecutorService 规划执行线程池（用于并发读取 manifest）
   * @param deleteFunc 删除回调（可选，可做统计或自定义删除）
   */
  IncrementalFileCleanup(
      FileIO fileIO,
      ExecutorService deleteExecutorService,
      ExecutorService planExecutorService,
      Consumer<String> deleteFunc) {
    super(fileIO, deleteExecutorService, planExecutorService, deleteFunc);
  }

  /**
   * 执行增量文件清理。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>计算过期快照集合（before 中有、after 中无）。
   *   <li>识别当前表状态的祖先链与 cherry-pick 来源快照，保护被 pick 的快照不误清理。
   *   <li>遍历有效快照的 manifest，收集仍被引用的 manifest 路径（validManifests）， 并把“由过期祖先快照写入且含删除条目”的 manifest
   *       加入待扫描集合。
   *   <li>遍历过期快照的 manifest，把不再被引用的 manifest 加入待删除集合； 祖先过期快照中含删除条目的 manifest 加入待扫描集合（删除其指向的数据文件）；
   *       非祖先过期快照中含新增条目的 manifest 加入待回滚集合（删除其新增的数据文件）。
   *   <li>调用 {@link #findFilesToDelete} 读取待扫描/待回滚 manifest 收集数据文件路径。
   *   <li>删除数据文件、manifest、manifest list、过期统计文件。
   * </ol>
   *
   * <p>设计要点：通过 {@link Tasks#foreach} 并发执行，suppressFailureWhenFinished 尽量完成清理， 避免因部分失败导致孤儿文件。
   *
   * @param beforeExpiration 过期前的表元数据
   * @param afterExpiration 过期后的表元数据
   */
  @Override
  @SuppressWarnings({"checkstyle:CyclomaticComplexity", "MethodLength"})
  public void cleanFiles(TableMetadata beforeExpiration, TableMetadata afterExpiration) {
    if (afterExpiration.refs().size() > 1) {
      throw new UnsupportedOperationException(
          "Cannot incrementally clean files for tables with more than 1 ref");
    }

    // clean up the expired snapshots:
    // 1. Get a list of the snapshots that were removed
    // 2. Delete any data files that were deleted by those snapshots and are not in the table
    // 3. Delete any manifests that are no longer used by current snapshots
    // 4. Delete the manifest lists

    Set<Long> validIds = Sets.newHashSet();
    for (Snapshot snapshot : afterExpiration.snapshots()) {
      validIds.add(snapshot.snapshotId());
    }

    Set<Long> expiredIds = Sets.newHashSet();
    for (Snapshot snapshot : beforeExpiration.snapshots()) {
      long snapshotId = snapshot.snapshotId();
      if (!validIds.contains(snapshotId)) {
        // the snapshot was expired
        LOG.info("Expired snapshot: {}", snapshot);
        expiredIds.add(snapshotId);
      }
    }

    if (expiredIds.isEmpty()) {
      // if no snapshots were expired, skip cleanup
      return;
    }

    SnapshotRef branchToCleanup = Iterables.getFirst(beforeExpiration.refs().values(), null);
    if (branchToCleanup == null) {
      return;
    }

    Snapshot latest = beforeExpiration.snapshot(branchToCleanup.snapshotId());
    List<Snapshot> snapshots = afterExpiration.snapshots();

    // this is the set of ancestors of the current table state. when removing snapshots, this must
    // only remove files that were deleted in an ancestor of the current table state to avoid
    // physically deleting files that were logically deleted in a commit that was rolled back.
    Set<Long> ancestorIds =
        Sets.newHashSet(SnapshotUtil.ancestorIds(latest, beforeExpiration::snapshot));

    Set<Long> pickedAncestorSnapshotIds = Sets.newHashSet();
    for (long snapshotId : ancestorIds) {
      String sourceSnapshotId =
          beforeExpiration
              .snapshot(snapshotId)
              .summary()
              .get(SnapshotSummary.SOURCE_SNAPSHOT_ID_PROP);
      if (sourceSnapshotId != null) {
        // protect any snapshot that was cherry-picked into the current table state
        pickedAncestorSnapshotIds.add(Long.parseLong(sourceSnapshotId));
      }
    }

    // find manifests to clean up that are still referenced by a valid snapshot, but written by an
    // expired snapshot
    Set<String> validManifests = Sets.newHashSet();
    Set<ManifestFile> manifestsToScan = Sets.newHashSet();

    // Reads and deletes are done using Tasks.foreach(...).suppressFailureWhenFinished to complete
    // as much of the delete work as possible and avoid orphaned data or manifest files.
    Tasks.foreach(snapshots)
        .retry(3)
        .suppressFailureWhenFinished()
        .onFailure(
            (snapshot, exc) ->
                LOG.warn(
                    "Failed on snapshot {} while reading manifest list: {}",
                    snapshot.snapshotId(),
                    snapshot.manifestListLocation(),
                    exc))
        .run(
            snapshot -> {
              try (CloseableIterable<ManifestFile> manifests = readManifests(snapshot)) {
                for (ManifestFile manifest : manifests) {
                  validManifests.add(manifest.path());

                  long snapshotId = manifest.snapshotId();
                  // whether the manifest was created by a valid snapshot (true) or an expired
                  // snapshot (false)
                  boolean fromValidSnapshots = validIds.contains(snapshotId);
                  // whether the snapshot that created the manifest was an ancestor of the table
                  // state
                  boolean isFromAncestor = ancestorIds.contains(snapshotId);
                  // whether the changes in this snapshot have been picked into the current table
                  // state
                  boolean isPicked = pickedAncestorSnapshotIds.contains(snapshotId);
                  // if the snapshot that wrote this manifest is no longer valid (has expired),
                  // then delete its deleted files. note that this is only for expired snapshots
                  // that are in the
                  // current table state
                  if (!fromValidSnapshots
                      && (isFromAncestor || isPicked)
                      && manifest.hasDeletedFiles()) {
                    manifestsToScan.add(manifest.copy());
                  }
                }

              } catch (IOException e) {
                throw new RuntimeIOException(
                    e, "Failed to close manifest list: %s", snapshot.manifestListLocation());
              }
            });

    // find manifests to clean up that were only referenced by snapshots that have expired
    Set<String> manifestListsToDelete = Sets.newHashSet();
    Set<String> manifestsToDelete = Sets.newHashSet();
    Set<ManifestFile> manifestsToRevert = Sets.newHashSet();
    Tasks.foreach(beforeExpiration.snapshots())
        .retry(3)
        .suppressFailureWhenFinished()
        .onFailure(
            (snapshot, exc) ->
                LOG.warn(
                    "Failed on snapshot {} while reading manifest list: {}",
                    snapshot.snapshotId(),
                    snapshot.manifestListLocation(),
                    exc))
        .run(
            snapshot -> {
              long snapshotId = snapshot.snapshotId();
              if (!validIds.contains(snapshotId)) {
                // determine whether the changes in this snapshot are in the current table state
                if (pickedAncestorSnapshotIds.contains(snapshotId)) {
                  // this snapshot was cherry-picked into the current table state, so skip cleaning
                  // it up.
                  // its changes will expire when the picked snapshot expires.
                  // A -- C -- D (source=B)
                  //  `- B <-- this commit
                  return;
                }

                long sourceSnapshotId =
                    PropertyUtil.propertyAsLong(
                        snapshot.summary(), SnapshotSummary.SOURCE_SNAPSHOT_ID_PROP, -1);
                if (ancestorIds.contains(sourceSnapshotId)) {
                  // this commit was cherry-picked from a commit that is in the current table state.
                  // do not clean up its changes because it would revert data file additions that
                  // are in the current
                  // table.
                  // A -- B -- C
                  //  `- D (source=B) <-- this commit
                  return;
                }

                if (pickedAncestorSnapshotIds.contains(sourceSnapshotId)) {
                  // this commit was cherry-picked from a commit that is in the current table state.
                  // do not clean up its changes because it would revert data file additions that
                  // are in the current
                  // table.
                  // A -- C -- E (source=B)
                  //  `- B `- D (source=B) <-- this commit
                  return;
                }

                // find any manifests that are no longer needed
                try (CloseableIterable<ManifestFile> manifests = readManifests(snapshot)) {
                  for (ManifestFile manifest : manifests) {
                    if (!validManifests.contains(manifest.path())) {
                      manifestsToDelete.add(manifest.path());

                      boolean isFromAncestor = ancestorIds.contains(manifest.snapshotId());
                      boolean isFromExpiringSnapshot = expiredIds.contains(manifest.snapshotId());

                      if (isFromAncestor && manifest.hasDeletedFiles()) {
                        // Only delete data files that were deleted in by an expired snapshot if
                        // that napshot is an ancestor of the current table state. Otherwise, a
                        // snapshot
                        // that deleted files and was rolled back will delete files that could be in
                        // the current
                        // table state.
                        manifestsToScan.add(manifest.copy());
                      }

                      if (!isFromAncestor && isFromExpiringSnapshot && manifest.hasAddedFiles()) {
                        // Because the manifest was written by a snapshot that is not an ancestor of
                        // the current table state, the files added in this manifest can be removed.
                        // The
                        // extra check whether the manifest was written by a known snapshot that was
                        // expired in this commit ensures that the full ancestor list between when
                        // the snapshot
                        // was written and this expiration is known and there is no missing history.
                        // If
                        // history were missing, then the snapshot could be an ancestor of the table
                        // state
                        // but the ancestor ID set would not contain it and this would be unsafe.
                        manifestsToRevert.add(manifest.copy());
                      }
                    }
                  }
                } catch (IOException e) {
                  throw new RuntimeIOException(
                      e, "Failed to close manifest list: %s", snapshot.manifestListLocation());
                }

                // add the manifest list to the delete set, if present
                if (snapshot.manifestListLocation() != null) {
                  manifestListsToDelete.add(snapshot.manifestListLocation());
                }
              }
            });

    Set<String> filesToDelete =
        findFilesToDelete(manifestsToScan, manifestsToRevert, validIds, afterExpiration);

    deleteFiles(filesToDelete, "data");
    deleteFiles(manifestsToDelete, "manifest");
    deleteFiles(manifestListsToDelete, "manifest list");

    if (!beforeExpiration.statisticsFiles().isEmpty()) {
      Set<String> expiredStatisticsFilesLocations =
          expiredStatisticsFilesLocations(beforeExpiration, afterExpiration);
      deleteFiles(expiredStatisticsFilesLocations, "statistics files");
    }
  }

  /**
   * 读取待扫描与待回滚的 manifest，收集需要删除的数据文件路径。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>manifestsToScan：读取其中的 DELETED 条目，若条目快照 id 已失效则把文件路径加入删除集合。
   *   <li>manifestsToRevert：读取其中的 ADDED 条目，全部加入删除集合（因为这些文件来自被回滚的快照）。
   * </ul>
   *
   * 通过 {@link Tasks} 并发执行，使用 ConcurrentHashMap.newKeySet 保证线程安全。
   *
   * @param manifestsToScan 待扫描删除条目的 manifest 集合
   * @param manifestsToRevert 待回滚新增条目的 manifest 集合
   * @param validIds 仍然有效的快照 id 集合
   * @param current 当前表元数据
   * @return 待删除的数据文件路径集合
   */
  private Set<String> findFilesToDelete(
      Set<ManifestFile> manifestsToScan,
      Set<ManifestFile> manifestsToRevert,
      Set<Long> validIds,
      TableMetadata current) {
    Set<String> filesToDelete = ConcurrentHashMap.newKeySet();
    Tasks.foreach(manifestsToScan)
        .retry(3)
        .suppressFailureWhenFinished()
        .executeWith(planExecutorService)
        .onFailure(
            (item, exc) ->
                LOG.warn("Failed to get deleted files: this may cause orphaned data files", exc))
        .run(
            manifest -> {
              // the manifest has deletes, scan it to find files to delete
              try (ManifestReader<?> reader =
                  ManifestFiles.open(manifest, fileIO, current.specsById())) {
                for (ManifestEntry<?> entry : reader.entries()) {
                  // if the snapshot ID of the DELETE entry is no longer valid, the data can be
                  // deleted
                  if (entry.status() == ManifestEntry.Status.DELETED
                      && !validIds.contains(entry.snapshotId())) {
                    // use toString to ensure the path will not change (Utf8 is reused)
                    filesToDelete.add(entry.file().path().toString());
                  }
                }
              } catch (IOException e) {
                throw new RuntimeIOException(e, "Failed to read manifest file: %s", manifest);
              }
            });

    Tasks.foreach(manifestsToRevert)
        .retry(3)
        .suppressFailureWhenFinished()
        .executeWith(planExecutorService)
        .onFailure(
            (item, exc) ->
                LOG.warn("Failed to get added files: this may cause orphaned data files", exc))
        .run(
            manifest -> {
              // the manifest has deletes, scan it to find files to delete
              try (ManifestReader<?> reader =
                  ManifestFiles.open(manifest, fileIO, current.specsById())) {
                for (ManifestEntry<?> entry : reader.entries()) {
                  // delete any ADDED file from manifests that were reverted
                  if (entry.status() == ManifestEntry.Status.ADDED) {
                    // use toString to ensure the path will not change (Utf8 is reused)
                    filesToDelete.add(entry.file().path().toString());
                  }
                }
              } catch (IOException e) {
                throw new RuntimeIOException(e, "Failed to read manifest file: %s", manifest);
              }
            });

    return filesToDelete;
  }
}
