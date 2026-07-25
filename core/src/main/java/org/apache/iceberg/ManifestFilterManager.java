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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.InclusiveMetricsEvaluator;
import org.apache.iceberg.expressions.ManifestEvaluator;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.expressions.StrictMetricsEvaluator;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.CharSequenceSet;
import org.apache.iceberg.util.CharSequenceWrapper;
import org.apache.iceberg.util.ManifestFileUtil;
import org.apache.iceberg.util.Pair;
import org.apache.iceberg.util.PartitionSet;
import org.apache.iceberg.util.StructLikeMap;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manifest 过滤管理器：在提交新快照时按删除规则过滤 manifest 中的文件条目。
 *
 * <p>所属模块：iceberg-core（manifest 重写与文件删除核心层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>支持多种删除方式：按行过滤表达式、按分区、按文件路径、按序列号清理旧删除文件。
 *   <li>对每个 manifest 应用过滤，必要时生成新的 manifest 文件（仅保留未删除条目）。
 *   <li>缓存过滤结果，避免提交失败重试时重复过滤；同时跟踪被删除文件用于快照摘要与校验。
 *   <li>提供校验能力：failAnyDelete（任何删除都报错）、failMissingDeletePaths（被删路径必须存在）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把"删除规则收集"与"manifest 过滤执行"解耦：调用方先注册删除意图，再统一调用 {@link #filterManifests} 执行。
 *   <li>使用 {@link ManifestEvaluator} 与分区/指标评估器在 manifest 级与文件级两层过滤， 避免无谓的 manifest 重写。
 *   <li>并行过滤多个 manifest 以提升大表性能。
 * </ul>
 *
 * <p>上下游关系：被 {@code MergingSnapshotProducer} 等提交生产者使用； 子类需提供 {@link #deleteFile(String)}、{@link
 * #newManifestWriter}、{@link #newManifestReader}。
 *
 * @param <F> 文件类型（{@link DataFile} 或 {@link DeleteFile}）
 */
abstract class ManifestFilterManager<F extends ContentFile<F>> {
  private static final Logger LOG = LoggerFactory.getLogger(ManifestFilterManager.class);
  private static final Joiner COMMA = Joiner.on(",");

  /** 删除校验异常：当 {@link #failAnyDelete} 为 true 且检测到删除时抛出，附带分区路径信息。 */
  protected static class DeleteException extends ValidationException {
    private final String partition;

    private DeleteException(String partition) {
      super("Operation would delete existing data");
      this.partition = partition;
    }

    /**
     * 返回触发异常的分区路径。
     *
     * @return 分区路径字符串
     */
    public String partition() {
      return partition;
    }
  }

  private final Map<Integer, PartitionSpec> specsById;
  private final PartitionSet deleteFilePartitions;
  private final PartitionSet dropPartitions;
  private final CharSequenceSet deletePaths = CharSequenceSet.empty();
  private Expression deleteExpression = Expressions.alwaysFalse();
  private long minSequenceNumber = 0;
  private boolean hasPathOnlyDeletes = false;
  private boolean failAnyDelete = false;
  private boolean failMissingDeletePaths = false;
  private int duplicateDeleteCount = 0;
  private boolean caseSensitive = true;

  // cache filtered manifests to avoid extra work when commits fail.
  private final Map<ManifestFile, ManifestFile> filteredManifests = Maps.newConcurrentMap();

  // tracking where files were deleted to validate retries quickly
  private final Map<ManifestFile, Iterable<F>> filteredManifestToDeletedFiles =
      Maps.newConcurrentMap();

  private final Supplier<ExecutorService> workerPoolSupplier;

  /**
   * 构造过滤管理器。
   *
   * @param specsById 表的所有分区 spec（按 id 索引）
   * @param executorSupplier 用于并行过滤 manifest 的线程池提供者
   */
  protected ManifestFilterManager(
      Map<Integer, PartitionSpec> specsById, Supplier<ExecutorService> executorSupplier) {
    this.specsById = specsById;
    this.deleteFilePartitions = PartitionSet.create(specsById);
    this.dropPartitions = PartitionSet.create(specsById);
    this.workerPoolSupplier = executorSupplier;
  }

  /** 子类实现：删除底层文件（按路径）。 */
  protected abstract void deleteFile(String location);

  /** 子类实现：创建指定 spec 的 manifest 写入器。 */
  protected abstract ManifestWriter<F> newManifestWriter(PartitionSpec spec);

  /** 子类实现：创建读取指定 manifest 的读取器。 */
  protected abstract ManifestReader<F> newManifestReader(ManifestFile manifest);

  /** 启用"任何删除都报错"模式：一旦检测到匹配删除规则的文件，立即抛出 {@link DeleteException}。 */
  protected void failAnyDelete() {
    this.failAnyDelete = true;
  }

  /** 启用"被删路径必须存在"模式：filterManifests 后会校验所有 deletePaths 都已被删除。 */
  protected void failMissingDeletePaths() {
    this.failMissingDeletePaths = true;
  }

  /**
   * 添加按行过滤表达式删除文件：当某文件所有行都匹配表达式时该文件被删除。
   *
   * <p>设计要点：多次调用会通过 OR 合并表达式；调用后失效过滤缓存。
   *
   * @param expr 行过滤表达式
   */
  protected void deleteByRowFilter(Expression expr) {
    Preconditions.checkNotNull(expr, "Cannot delete files using filter: null");
    invalidateFilteredCache();
    this.deleteExpression = Expressions.or(deleteExpression, expr);
  }

  /**
   * 添加待删除的分区：在过滤阶段把该分区下的所有文件删除。
   *
   * @param specId 分区 spec id
   * @param partition 分区值
   */
  protected void dropPartition(int specId, StructLike partition) {
    Preconditions.checkNotNull(partition, "Cannot delete files in invalid partition: null");
    invalidateFilteredCache();
    dropPartitions.add(specId, partition);
  }

  /**
   * 设置删除文件的最小序列号阈值：序列号小于该值的删除文件会被清理。
   *
   * <p>使用场景：把该值设为表中最老数据文件的序列号，可清理不再匹配任何现存数据的删除文件， 实现删除文件的自动回收。
   *
   * @param sequenceNumber 最小数据序列号阈值
   */
  protected void dropDeleteFilesOlderThan(long sequenceNumber) {
    Preconditions.checkArgument(
        sequenceNumber >= 0, "Invalid minimum data sequence number: %s", sequenceNumber);
    this.minSequenceNumber = sequenceNumber;
  }

  /**
   * 设置过滤表达式求值时是否区分字段名大小写。
   *
   * @param newCaseSensitive 是否大小写敏感
   */
  void caseSensitive(boolean newCaseSensitive) {
    this.caseSensitive = newCaseSensitive;
  }

  /**
   * 添加指定文件到删除集合：同时记录路径与所在分区，便于后续过滤。
   *
   * @param file 待删除文件
   */
  void delete(F file) {
    Preconditions.checkNotNull(file, "Cannot delete file: null");
    invalidateFilteredCache();
    deletePaths.add(file.path());
    deleteFilePartitions.add(file.specId(), file.partition());
  }

  /**
   * 添加指定路径到删除集合（仅路径，无分区信息）。
   *
   * <p>设计要点：设置 hasPathOnlyDeletes 标志，使后续 canContainDeletedFiles 跳过 分区级快速判断，强制做路径级匹配。
   *
   * @param path 待删除文件路径
   */
  void delete(CharSequence path) {
    Preconditions.checkNotNull(path, "Cannot delete file path: null");
    invalidateFilteredCache();
    this.hasPathOnlyDeletes = true;
    deletePaths.add(path);
  }

  /**
   * 返回是否注册了任何删除规则。
   *
   * @return true 表示存在删除意图
   */
  boolean containsDeletes() {
    return deletePaths.size() > 0
        || deleteExpression != Expressions.alwaysFalse()
        || dropPartitions.size() > 0;
  }

  /**
   * 对一组 manifest 应用过滤规则，返回过滤后的 manifest 列表。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>manifests 为空时直接校验必删路径并返回空列表。
   *   <li>使用 worker pool 并行对每个 manifest 调用 {@link #filterManifest(Schema, ManifestFile)}。
   *   <li>保持原顺序（按 index 写回数组）。
   *   <li>调用 {@link #validateRequiredDeletes} 校验必删路径是否都被删除。
   * </ol>
   *
   * @param tableSchema 当前表 schema
   * @param manifests 待过滤 manifest 列表
   * @return 过滤后的 manifest 列表
   */
  List<ManifestFile> filterManifests(Schema tableSchema, List<ManifestFile> manifests) {
    if (manifests == null || manifests.isEmpty()) {
      validateRequiredDeletes();
      return ImmutableList.of();
    }

    ManifestFile[] filtered = new ManifestFile[manifests.size()];
    // open all of the manifest files in parallel, use index to avoid reordering
    Tasks.range(filtered.length)
        .stopOnFailure()
        .throwFailureWhenFinished()
        .executeWith(workerPoolSupplier.get())
        .run(
            index -> {
              ManifestFile manifest = filterManifest(tableSchema, manifests.get(index));
              filtered[index] = manifest;
            });

    validateRequiredDeletes(filtered);

    return Arrays.asList(filtered);
  }

  /**
   * 基于过滤后 manifest 的删除文件列表构建快照摘要 builder。
   *
   * <p>逻辑：遍历每个过滤后 manifest 对应的被删文件，调用 {@link SnapshotSummary.Builder#deletedFile} 累计；最后叠加重复删除计数。
   *
   * @param manifests 过滤后的 manifest 集合
   * @return 已填充删除信息的快照摘要 builder
   */
  SnapshotSummary.Builder buildSummary(Iterable<ManifestFile> manifests) {
    SnapshotSummary.Builder summaryBuilder = SnapshotSummary.builder();

    for (ManifestFile manifest : manifests) {
      PartitionSpec manifestSpec = specsById.get(manifest.partitionSpecId());
      Iterable<F> manifestDeletes = filteredManifestToDeletedFiles.get(manifest);
      if (manifestDeletes != null) {
        for (F file : manifestDeletes) {
          summaryBuilder.deletedFile(manifestSpec, file);
        }
      }
    }

    summaryBuilder.incrementDuplicateDeletes(duplicateDeleteCount);

    return summaryBuilder;
  }

  /**
   * Throws a {@link ValidationException} if any deleted file was not present in a filtered
   * manifest.
   *
   * @param manifests a set of filtered manifests
   */
  @SuppressWarnings("CollectionUndefinedEquality")
  private void validateRequiredDeletes(ManifestFile... manifests) {
    if (failMissingDeletePaths) {
      CharSequenceSet deletedFiles = deletedFiles(manifests);
      ValidationException.check(
          deletedFiles.containsAll(deletePaths),
          "Missing required files to delete: %s",
          COMMA.join(Iterables.filter(deletePaths, path -> !deletedFiles.contains(path))));
    }
  }

  private CharSequenceSet deletedFiles(ManifestFile[] manifests) {
    CharSequenceSet deletedFiles = CharSequenceSet.empty();

    if (manifests != null) {
      for (ManifestFile manifest : manifests) {
        Iterable<F> manifestDeletes = filteredManifestToDeletedFiles.get(manifest);
        if (manifestDeletes != null) {
          for (F file : manifestDeletes) {
            deletedFiles.add(file.path());
          }
        }
      }
    }

    return deletedFiles;
  }

  /**
   * 清理未被提交的过滤后 manifest 文件：删除物理文件并从缓存移除。
   *
   * <p>逻辑：遍历 filteredManifests 缓存，若过滤后的 manifest 不在 committed 集合中， 且与原 manifest
   * 不同（即本类新建的），则删除其物理文件；同时移除缓存条目。
   *
   * @param committed 已提交的 manifest 集合
   */
  void cleanUncommitted(Set<ManifestFile> committed) {
    // iterate over a copy of entries to avoid concurrent modification
    List<Map.Entry<ManifestFile, ManifestFile>> filterEntries =
        Lists.newArrayList(filteredManifests.entrySet());

    for (Map.Entry<ManifestFile, ManifestFile> entry : filterEntries) {
      // remove any new filtered manifests that aren't in the committed list
      ManifestFile manifest = entry.getKey();
      ManifestFile filtered = entry.getValue();
      if (!committed.contains(filtered)) {
        // only delete if the filtered copy was created
        if (!manifest.equals(filtered)) {
          deleteFile(filtered.path());
        }

        // remove the entry from the cache
        filteredManifests.remove(manifest);
      }
    }
  }

  private void invalidateFilteredCache() {
    cleanUncommitted(SnapshotProducer.EMPTY_SET);
  }

  /**
   * 过滤单个 manifest，返回过滤后的 manifest（可能为原 manifest 或新写入的副本）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>优先使用缓存。
   *   <li>若 manifest 无活跃文件或不可能包含待删文件，直接返回原 manifest。
   *   <li>否则打开 reader，先用 {@link #manifestHasDeletedFiles} 快速判断是否含待删文件； 不含则返回原 manifest。
   *   <li>含待删文件则调用 {@link #filterManifestWithDeletedFiles} 重写 manifest。
   * </ol>
   *
   * @param tableSchema 当前表 schema
   * @param manifest 待过滤 manifest
   * @return 过滤后的 manifest
   */
  private ManifestFile filterManifest(Schema tableSchema, ManifestFile manifest) {
    ManifestFile cached = filteredManifests.get(manifest);
    if (cached != null) {
      return cached;
    }

    boolean hasLiveFiles = manifest.hasAddedFiles() || manifest.hasExistingFiles();
    if (!hasLiveFiles || !canContainDeletedFiles(manifest)) {
      filteredManifests.put(manifest, manifest);
      return manifest;
    }

    try (ManifestReader<F> reader = newManifestReader(manifest)) {
      PartitionSpec spec = reader.spec();
      PartitionAndMetricsEvaluator evaluator =
          new PartitionAndMetricsEvaluator(tableSchema, spec, deleteExpression);

      // this assumes that the manifest doesn't have files to remove and streams through the
      // manifest without copying data. if a manifest does have a file to remove, this will break
      // out of the loop and move on to filtering the manifest.
      boolean hasDeletedFiles = manifestHasDeletedFiles(evaluator, reader);
      if (!hasDeletedFiles) {
        filteredManifests.put(manifest, manifest);
        return manifest;
      }

      return filterManifestWithDeletedFiles(evaluator, manifest, reader);

    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to close manifest: %s", manifest);
    }
  }

  /**
   * 判断 manifest 是否可能包含待删文件（manifest 级快速过滤）。
   *
   * <p>逻辑：分别按表达式、待删分区、待删文件路径、序列号阈值四类规则评估， 任一命中即返回 true。用于避免对无关 manifest 打开 reader。
   *
   * @param manifest 待评估 manifest
   * @return true 表示可能包含待删文件
   */
  private boolean canContainDeletedFiles(ManifestFile manifest) {
    boolean canContainExpressionDeletes;
    if (deleteExpression != null && deleteExpression != Expressions.alwaysFalse()) {
      ManifestEvaluator manifestEvaluator =
          ManifestEvaluator.forRowFilter(
              deleteExpression, specsById.get(manifest.partitionSpecId()), caseSensitive);
      canContainExpressionDeletes = manifestEvaluator.eval(manifest);
    } else {
      canContainExpressionDeletes = false;
    }

    boolean canContainDroppedPartitions;
    if (dropPartitions.size() > 0) {
      canContainDroppedPartitions =
          ManifestFileUtil.canContainAny(manifest, dropPartitions, specsById);
    } else {
      canContainDroppedPartitions = false;
    }

    boolean canContainDroppedFiles;
    if (hasPathOnlyDeletes) {
      canContainDroppedFiles = true;
    } else if (deletePaths.size() > 0) {
      // because there were no path-only deletes, the set of deleted file partitions is valid
      canContainDroppedFiles =
          ManifestFileUtil.canContainAny(manifest, deleteFilePartitions, specsById);
    } else {
      canContainDroppedFiles = false;
    }

    boolean canContainDropBySeq =
        manifest.content() == ManifestContent.DELETES
            && manifest.minSequenceNumber() < minSequenceNumber;

    return canContainExpressionDeletes
        || canContainDroppedPartitions
        || canContainDroppedFiles
        || canContainDropBySeq;
  }

  /**
   * 流式扫描 manifest 条目，判断是否至少存在一个待删文件。
   *
   * <p>逻辑：遍历 liveEntries，对每个文件检查是否被路径/分区/序列号标记删除，或匹配表达式； 若匹配且全部行匹配（allRowsMatch）则视为待删；若
   * failAnyDelete 则抛异常； 一旦发现待删文件立即返回 true。
   *
   * <p>设计要点：删除文件（delete file）允许部分行不匹配表达式时被忽略，以避免误删。
   *
   * @param evaluator 分区与指标评估器
   * @param reader manifest 读取器
   * @return true 表示 manifest 含待删文件
   */
  @SuppressWarnings({"CollectionUndefinedEquality", "checkstyle:CyclomaticComplexity"})
  private boolean manifestHasDeletedFiles(
      PartitionAndMetricsEvaluator evaluator, ManifestReader<F> reader) {
    boolean isDelete = reader.isDeleteManifestReader();

    for (ManifestEntry<F> entry : reader.liveEntries()) {
      F file = entry.file();
      boolean markedForDelete =
          deletePaths.contains(file.path())
              || dropPartitions.contains(file.specId(), file.partition())
              || (isDelete
                  && entry.isLive()
                  && entry.dataSequenceNumber() > 0
                  && entry.dataSequenceNumber() < minSequenceNumber);

      if (markedForDelete || evaluator.rowsMightMatch(file)) {
        boolean allRowsMatch = markedForDelete || evaluator.rowsMustMatch(file);
        ValidationException.check(
            allRowsMatch
                || isDelete, // ignore delete files where some records may not match the expression
            "Cannot delete file where some, but not all, rows match filter %s: %s",
            this.deleteExpression,
            file.path());

        if (allRowsMatch) {
          if (failAnyDelete) {
            throw new DeleteException(reader.spec().partitionToPath(file.partition()));
          }

          // as soon as a deleted file is detected, stop scanning
          return true;
        }
      }
    }

    return false;
  }

  /**
   * 重写 manifest：把待删条目改为 DELETED 状态、未删条目改为 EXISTING，输出新 manifest。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>遍历所有条目，按路径/分区/序列号/表达式判断是否待删。
   *   <li>待删且全部行匹配：写为 DELETED，加入 deletedFiles 与 deletedPaths； 重复路径仅累计 duplicateDeleteCount。
   *   <li>待删但部分行匹配：仅删除文件（delete file）允许保留为 EXISTING；数据文件则报错。
   *   <li>未待删：写为 EXISTING。
   *   <li>关闭 writer，更新缓存 filteredManifests 与 filteredManifestToDeletedFiles。
   * </ol>
   *
   * @param evaluator 分区与指标评估器
   * @param manifest 原 manifest
   * @param reader manifest 读取器
   * @return 过滤后的新 manifest
   */
  @SuppressWarnings({"CollectionUndefinedEquality", "checkstyle:CyclomaticComplexity"})
  private ManifestFile filterManifestWithDeletedFiles(
      PartitionAndMetricsEvaluator evaluator, ManifestFile manifest, ManifestReader<F> reader) {
    boolean isDelete = reader.isDeleteManifestReader();
    // when this point is reached, there is at least one file that will be deleted in the
    // manifest. produce a copy of the manifest with all deleted files removed.
    List<F> deletedFiles = Lists.newArrayList();
    Set<CharSequenceWrapper> deletedPaths = Sets.newHashSet();

    try {
      ManifestWriter<F> writer = newManifestWriter(reader.spec());
      try {
        reader
            .entries()
            .forEach(
                entry -> {
                  F file = entry.file();
                  boolean markedForDelete =
                      deletePaths.contains(file.path())
                          || dropPartitions.contains(file.specId(), file.partition())
                          || (isDelete
                              && entry.isLive()
                              && entry.dataSequenceNumber() > 0
                              && entry.dataSequenceNumber() < minSequenceNumber);
                  if (entry.status() != ManifestEntry.Status.DELETED) {
                    if (markedForDelete || evaluator.rowsMightMatch(file)) {
                      boolean allRowsMatch = markedForDelete || evaluator.rowsMustMatch(file);
                      ValidationException.check(
                          allRowsMatch
                              || isDelete, // ignore delete files where some records may not match
                          // the expression
                          "Cannot delete file where some, but not all, rows match filter %s: %s",
                          this.deleteExpression,
                          file.path());

                      if (allRowsMatch) {
                        writer.delete(entry);

                        CharSequenceWrapper wrapper = CharSequenceWrapper.wrap(entry.file().path());
                        if (deletedPaths.contains(wrapper)) {
                          LOG.warn(
                              "Deleting a duplicate path from manifest {}: {}",
                              manifest.path(),
                              wrapper.get());
                          duplicateDeleteCount += 1;
                        } else {
                          // only add the file to deletes if it is a new delete
                          // this keeps the snapshot summary accurate for non-duplicate data
                          deletedFiles.add(entry.file().copyWithoutStats());
                        }
                        deletedPaths.add(wrapper);
                      } else {
                        writer.existing(entry);
                      }

                    } else {
                      writer.existing(entry);
                    }
                  }
                });
      } finally {
        writer.close();
      }

      // return the filtered manifest as a reader
      ManifestFile filtered = writer.toManifestFile();

      // update caches
      filteredManifests.put(manifest, filtered);
      filteredManifestToDeletedFiles.put(filtered, deletedFiles);

      return filtered;

    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to close manifest writer");
    }
  }

  /**
   * 分区与指标联合评估器：先用分区值对表达式做残余求值，再用文件指标评估残余表达式。
   *
   * <p>设计意图：
   *
   * <ul>
   *   <li>{@link ResidualEvaluator} 利用 strict/inclusive 投影把能由分区确定的部分消解掉， 得到 residual 表达式。
   *   <li>对 residual 用 {@link InclusiveMetricsEvaluator}（可能匹配）与 {@link
   *       StrictMetricsEvaluator}（必然匹配）做文件级评估。
   *   <li>按分区缓存评估器对，避免对同一分区重复构造。
   * </ul>
   */
  private class PartitionAndMetricsEvaluator {
    private final Schema tableSchema;
    private final ResidualEvaluator residualEvaluator;
    private final StructLikeMap<Pair<InclusiveMetricsEvaluator, StrictMetricsEvaluator>>
        metricsEvaluators;

    /**
     * 构造评估器。
     *
     * @param tableSchema 表 schema
     * @param spec 分区 spec
     * @param expr 行过滤表达式
     */
    PartitionAndMetricsEvaluator(Schema tableSchema, PartitionSpec spec, Expression expr) {
      this.tableSchema = tableSchema;
      this.residualEvaluator = ResidualEvaluator.of(spec, expr, caseSensitive);
      this.metricsEvaluators = StructLikeMap.create(spec.partitionType());
    }

    /**
     * 判断文件中是否可能存在匹配表达式的行（inclusive 评估）。
     *
     * @param file 待评估文件
     * @return true 表示可能匹配
     */
    boolean rowsMightMatch(F file) {
      Pair<InclusiveMetricsEvaluator, StrictMetricsEvaluator> evaluators = metricsEvaluators(file);
      InclusiveMetricsEvaluator inclusiveMetricsEvaluator = evaluators.first();
      return inclusiveMetricsEvaluator.eval(file);
    }

    /**
     * 判断文件中是否所有行都必然匹配表达式（strict 评估）。
     *
     * @param file 待评估文件
     * @return true 表示所有行必然匹配
     */
    boolean rowsMustMatch(F file) {
      Pair<InclusiveMetricsEvaluator, StrictMetricsEvaluator> evaluators = metricsEvaluators(file);
      StrictMetricsEvaluator strictMetricsEvaluator = evaluators.second();
      return strictMetricsEvaluator.eval(file);
    }

    /**
     * 获取或构造文件所在分区的评估器对（inclusive, strict）。
     *
     * <p>逻辑：先查缓存；未命中时用 {@link ResidualEvaluator#residualFor} 求残余表达式， 构造两个指标评估器并缓存（拷贝分区键以防容器复用）。
     *
     * @param file 文件
     * @return 评估器对
     */
    private Pair<InclusiveMetricsEvaluator, StrictMetricsEvaluator> metricsEvaluators(F file) {
      // ResidualEvaluator removes predicates in the expression using strict/inclusive projections
      // if strict projection returns true -> the pred would return true -> replace the pred with
      // true
      // if inclusive projection returns false -> the pred would return false -> replace the pred
      // with false
      // otherwise, keep the original predicate and proceed to other predicates in the expression
      // in other words, ResidualEvaluator returns a part of the expression that needs to be
      // evaluated
      // for rows in the given partition using metrics
      PartitionData partition = (PartitionData) file.partition();
      if (!metricsEvaluators.containsKey(partition)) {
        Expression residual = residualEvaluator.residualFor(partition);
        InclusiveMetricsEvaluator inclusive =
            new InclusiveMetricsEvaluator(tableSchema, residual, caseSensitive);
        StrictMetricsEvaluator strict =
            new StrictMetricsEvaluator(tableSchema, residual, caseSensitive);

        metricsEvaluators.put(
            partition.copy(), // The partition may be a re-used container so a copy is required
            Pair.of(inclusive, strict));
      }
      return metricsEvaluators.get(partition);
    }
  }
}
