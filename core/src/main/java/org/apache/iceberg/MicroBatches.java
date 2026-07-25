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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：微批（MicroBatch）生成工具。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把一个快照的数据文件按目标字节大小切成多个“微批”，每个微批包含若干 {@link FileScanTask}。
 *   <li>支持基于 manifest 索引跳过已处理文件，实现增量消费 / 续读。
 *   <li>提供 {@link MicroBatchBuilder} 链式 API 配置大小写敏感、specsById 等。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Streaming 引擎（如 Flink）需要把一个 Iceberg 快照切成多个小批次逐步消费， 本类抽象出按字节大小切批的通用逻辑。
 *   <li>manifest 索引方案：每个 manifest 记录其起始文件序号，跳过 {@code < startFileIndex} 的文件，避免重复扫描已处理数据。
 *   <li>超限时回退最后一个 task：保证每个微批至少有一个 task，避免作业因空批卡死。
 * </ul>
 *
 * <p>上下游关系：被 Flink/Spark 流式 source 调用以按批读取 Iceberg 数据； 依赖 {@link Snapshot} 提供的 manifest 列表与 {@link
 * ManifestGroup} 进行文件扫描。
 */
public class MicroBatches {

  /** 私有构造：工具类禁止实例化。 */
  private MicroBatches() {}

  /**
   * 获取快照内（已跳过 {@code < startFileIndex} 的 manifest）的 manifest 索引列表。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>按 scanAllFiles 决定取快照全部 data manifest，还是只取 snapshotId 匹配的 manifest；
   *   <li>调用 {@link #indexManifests} 给每个 manifest 标注起始文件序号；
   *   <li>调用 {@link #skipManifests} 跳过起始序号小于 startFileIndex 的 manifest。
   * </ol>
   *
   * @param io 文件 IO
   * @param snapshot 目标快照
   * @param startFileIndex 起始文件序号（之前序号的文件视为已处理）
   * @param scanAllFiles 是否扫描全部数据文件（true）还是只扫描本快照新增的文件（false）
   * @return 跳过后剩余的 (manifest, 起始文件序号) 列表
   */
  public static List<Pair<ManifestFile, Integer>> skippedManifestIndexesFromSnapshot(
      FileIO io, Snapshot snapshot, long startFileIndex, boolean scanAllFiles) {
    List<ManifestFile> manifests =
        scanAllFiles
            ? snapshot.dataManifests(io)
            : snapshot.dataManifests(io).stream()
                .filter(m -> m.snapshotId().equals(snapshot.snapshotId()))
                .collect(Collectors.toList());

    List<Pair<ManifestFile, Integer>> manifestIndexes = indexManifests(manifests);

    return skipManifests(manifestIndexes, startFileIndex);
  }

  /**
   * 打开单个 manifest 并返回其中的 {@link FileScanTask} 迭代器。
   *
   * <p>当 {@code scanAllFiles=false} 时，仅保留本快照新增（ADDED）的文件， 通过 {@link
   * ManifestGroup#filterManifestEntries} 过滤并 {@code ignoreDeleted}。
   *
   * @param io 文件 IO
   * @param specsById 分区规范映射
   * @param caseSensitive 是否大小写敏感
   * @param snapshot 目标快照
   * @param manifestFile 待打开的 manifest
   * @param scanAllFiles 是否扫描所有文件，false 表示仅扫描本快照新增文件
   * @return manifest 内文件扫描任务的迭代器
   */
  public static CloseableIterable<FileScanTask> openManifestFile(
      FileIO io,
      Map<Integer, PartitionSpec> specsById,
      boolean caseSensitive,
      Snapshot snapshot,
      ManifestFile manifestFile,
      boolean scanAllFiles) {

    ManifestGroup manifestGroup =
        new ManifestGroup(io, ImmutableList.of(manifestFile))
            .specsById(specsById)
            .caseSensitive(caseSensitive);
    if (!scanAllFiles) {
      manifestGroup =
          manifestGroup
              .filterManifestEntries(
                  entry ->
                      entry.snapshotId() == snapshot.snapshotId()
                          && entry.status() == ManifestEntry.Status.ADDED)
              .ignoreDeleted();
    }

    return manifestGroup.planFiles();
  }

  /**
   * Method to index the data files for each manifest. For example, if manifest m1 has 3 data files,
   * manifest m2 has 2 data files, manifest m3 has 1 data file, then the index will be (m1, 0), (m2,
   * 3), (m3, 5).
   *
   * @param manifestFiles List of input manifests used to index.
   * @return a list pairing each manifest with the index number of the first data file entry in that
   *     manifest.
   */
  private static List<Pair<ManifestFile, Integer>> indexManifests(
      List<ManifestFile> manifestFiles) {
    int currentFileIndex = 0;
    List<Pair<ManifestFile, Integer>> manifestIndexes = Lists.newArrayList();

    for (ManifestFile manifest : manifestFiles) {
      manifestIndexes.add(Pair.of(manifest, currentFileIndex));
      currentFileIndex += manifest.addedFilesCount() + manifest.existingFilesCount();
    }

    return manifestIndexes;
  }

  /**
   * Method to skip the manifest file whose index is smaller than startFileIndex. For example, if
   * the index list is : (m1, 0), (m2, 3), (m3, 5), and startFileIndex is 4, then the returned
   * manifest index list is: (m2, 3), (m3, 5).
   *
   * @param indexedManifests List of input manifests.
   * @param startFileIndex Index used to skip all manifests with an index less than or equal to this
   *     value.
   * @return a sub-list of manifest file index which only contains the manifest indexes larger than
   *     the startFileIndex.
   */
  private static List<Pair<ManifestFile, Integer>> skipManifests(
      List<Pair<ManifestFile, Integer>> indexedManifests, long startFileIndex) {
    if (startFileIndex == 0) {
      return indexedManifests;
    }

    int manifestIndex = 0;
    for (Pair<ManifestFile, Integer> manifest : indexedManifests) {
      if (manifest.second() > startFileIndex) {
        break;
      }

      manifestIndex++;
    }

    return indexedManifests.subList(Math.max(manifestIndex - 1, 0), indexedManifests.size());
  }

  /**
   * 微批次结果：包含一组文件扫描任务及其在快照中的序号区间。
   *
   * <p>不可变值对象；通过 {@link MicroBatchBuilder} 构造。
   */
  public static class MicroBatch {
    private final long snapshotId;
    private final long startFileIndex;
    private final long endFileIndex;
    private final long sizeInBytes;
    private final List<FileScanTask> tasks;
    private final boolean lastIndexOfSnapshot;

    private MicroBatch(
        long snapshotId,
        long startFileIndex,
        long endFileIndex,
        long sizeInBytes,
        List<FileScanTask> tasks,
        boolean lastIndexOfSnapshot) {
      this.snapshotId = snapshotId;
      this.startFileIndex = startFileIndex;
      this.endFileIndex = endFileIndex;
      this.sizeInBytes = sizeInBytes;
      this.tasks = tasks;
      this.lastIndexOfSnapshot = lastIndexOfSnapshot;
    }

    /** 返回 本微批所属快照 id。 */
    public long snapshotId() {
      return snapshotId;
    }

    /** 返回 本微批起始文件序号（含）。 */
    public long startFileIndex() {
      return startFileIndex;
    }

    /** 返回 本微批结束文件序号（不含）。 */
    public long endFileIndex() {
      return endFileIndex;
    }

    /** 返回 本微批所有文件的总字节数。 */
    public long sizeInBytes() {
      return sizeInBytes;
    }

    /** 返回 本微批包含的文件扫描任务列表。 */
    public List<FileScanTask> tasks() {
      return tasks;
    }

    /** 返回 true 表示本微批已是该快照最后一批，引擎可推进到下一个快照。 */
    public boolean lastIndexOfSnapshot() {
      return lastIndexOfSnapshot;
    }
  }

  /**
   * 工厂入口：基于快照与 IO 创建 {@link MicroBatchBuilder}。
   *
   * @param snapshot 目标快照
   * @param io 文件 IO
   * @return 新的 builder 实例
   */
  public static MicroBatchBuilder from(Snapshot snapshot, FileIO io) {
    return new MicroBatchBuilder(snapshot, io);
  }

  /**
   * 微批次构建器：链式配置后通过 {@link #generate} 生成 {@link MicroBatch}。
   *
   * <p>默认大小写敏感；specsById 可选。
   */
  public static class MicroBatchBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(MicroBatchBuilder.class);

    private final Snapshot snapshot;
    private final FileIO io;
    private boolean caseSensitive;
    private Map<Integer, PartitionSpec> specsById;

    private MicroBatchBuilder(Snapshot snapshot, FileIO io) {
      this.snapshot = snapshot;
      this.io = io;
      this.caseSensitive = true;
    }

    /**
     * 设置大小写敏感标志。
     *
     * @param sensitive 是否大小写敏感
     * @return 当前 builder
     */
    public MicroBatchBuilder caseSensitive(boolean sensitive) {
      this.caseSensitive = sensitive;
      return this;
    }

    /**
     * 设置分区规范映射。
     *
     * @param specs id 到 PartitionSpec 的映射
     * @return 当前 builder
     */
    public MicroBatchBuilder specsById(Map<Integer, PartitionSpec> specs) {
      this.specsById = specs;
      return this;
    }

    /**
     * 生成一个微批：以快照新增文件总数作为 endFileIndex。
     *
     * @param startFileIndex 起始文件序号
     * @param targetSizeInBytes 目标字节大小
     * @param scanAllFiles 是否扫描所有文件
     * @return 生成的微批
     */
    public MicroBatch generate(long startFileIndex, long targetSizeInBytes, boolean scanAllFiles) {
      return generate(
          startFileIndex,
          Iterables.size(snapshot.addedDataFiles(io)),
          targetSizeInBytes,
          scanAllFiles);
    }

    /**
     * 生成微批：在 [startFileIndex, endFileIndex) 区间内累加文件直至达到目标字节大小。
     *
     * <p>步骤：
     *
     * <ol>
     *   <li>校验 endFileIndex、startFileIndex、targetSizeInBytes 非负/为正；
     *   <li>通过 {@link #skippedManifestIndexesFromSnapshot} 取跳过已处理后的 manifest 索引；
     *   <li>调用 {@link #generateMicroBatch} 实际生成。
     * </ol>
     *
     * @param startFileIndex 起始文件序号（含）
     * @param endFileIndex 结束文件序号（不含）
     * @param targetSizeInBytes 目标字节大小
     * @param scanAllFiles 是否扫描所有文件
     * @return 生成的微批
     */
    public MicroBatch generate(
        long startFileIndex, long endFileIndex, long targetSizeInBytes, boolean scanAllFiles) {
      Preconditions.checkArgument(endFileIndex >= 0, "endFileIndex is unexpectedly smaller than 0");
      Preconditions.checkArgument(
          startFileIndex >= 0, "startFileIndex is unexpectedly smaller than 0");
      Preconditions.checkArgument(
          targetSizeInBytes > 0, "targetSizeInBytes should be larger than 0");

      return generateMicroBatch(
          skippedManifestIndexesFromSnapshot(io, snapshot, startFileIndex, scanAllFiles),
          startFileIndex,
          endFileIndex,
          targetSizeInBytes,
          scanAllFiles);
    }

    /**
     * Method to generate MicroBatch of this snapshot based on the indexed manifests, controlled by
     * targetSizeInBytes.
     *
     * @param indexedManifests A list of indexed manifests to generate MicroBatch
     * @param startFileIndex A startFileIndex used to skip processed files.
     * @param endFileIndex An endFileIndex used to find files to include, exclusive.
     * @param targetSizeInBytes Used to control the size of MicroBatch, the processed file bytes
     *     must be smaller than this size.
     * @param scanAllFiles Used to check whether all the data files should be processed, or only
     *     added files.
     * @return A MicroBatch.
     */
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private MicroBatch generateMicroBatch(
        List<Pair<ManifestFile, Integer>> indexedManifests,
        long startFileIndex,
        long endFileIndex,
        long targetSizeInBytes,
        boolean scanAllFiles) {
      if (indexedManifests.isEmpty()) {
        return new MicroBatch(
            snapshot.snapshotId(), startFileIndex, endFileIndex, 0L, Collections.emptyList(), true);
      }

      long currentSizeInBytes = 0L;
      int currentFileIndex = 0;
      boolean isLastIndex = false;
      List<FileScanTask> tasks = Lists.newArrayList();

      for (int idx = 0; idx < indexedManifests.size(); idx++) {
        currentFileIndex = indexedManifests.get(idx).second();

        try (CloseableIterable<FileScanTask> taskIterable =
                openManifestFile(
                    io,
                    specsById,
                    caseSensitive,
                    snapshot,
                    indexedManifests.get(idx).first(),
                    scanAllFiles);
            CloseableIterator<FileScanTask> taskIter = taskIterable.iterator()) {
          while (taskIter.hasNext()) {
            FileScanTask task = taskIter.next();
            // want to read [startFileIndex ... endFileIndex)
            if (currentFileIndex >= startFileIndex && currentFileIndex < endFileIndex) {
              // Make sure there's at least one task in each MicroBatch to void job to be stuck,
              // always add task
              // firstly.
              tasks.add(task);
              currentSizeInBytes += task.length();
            }

            currentFileIndex++;
            if (currentSizeInBytes >= targetSizeInBytes || currentFileIndex >= endFileIndex) {
              break;
            }
          }

          if (idx + 1 == indexedManifests.size() && !taskIter.hasNext()) {
            // If this is the last file scan task in last manifest, set the flag to true.
            isLastIndex = true;
          }
        } catch (IOException ioe) {
          LOG.warn("Failed to close task iterable", ioe);
        }

        if (currentSizeInBytes >= targetSizeInBytes) {
          if (tasks.size() > 1 && currentSizeInBytes > targetSizeInBytes) {
            // If there's more than 1 task in this batch, and the size exceeds the limit, we should
            // revert last
            // task to make sure we don't exceed the size limit.
            FileScanTask extraTask = tasks.remove(tasks.size() - 1);
            currentSizeInBytes -= extraTask.length();
            currentFileIndex--;
            isLastIndex = false;
          }

          break;
        }
      }

      // [startFileIndex ....currentFileIndex)
      return new MicroBatch(
          snapshot.snapshotId(),
          startFileIndex,
          currentFileIndex,
          currentSizeInBytes,
          tasks,
          isLastIndex);
    }
  }
}
