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

import static org.apache.iceberg.TableProperties.MANIFEST_TARGET_SIZE_BYTES;
import static org.apache.iceberg.TableProperties.MANIFEST_TARGET_SIZE_BYTES_DEFAULT;
import static org.apache.iceberg.TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED;
import static org.apache.iceberg.TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.Pair;
import org.apache.iceberg.util.Tasks;

/**
 * Manifest 重写操作的 core 实现：把现有 manifest 重新组织为新 manifest，不改变数据文件内容。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link RewriteManifests}，支持两种模式：直接删除/添加 manifest，或按 clusterBy 函数重写。
 *   <li>重写时把各 manifest 中的活跃 entry 重新分桶写入新 manifest，受目标 manifest 大小限制。
 *   <li>支持 {@code rewriteIf} 谓词仅重写部分 manifest，保留其余不动。
 *   <li>提交后清理未提交的新 manifest，保留已提交的。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>纯元数据操作：不改变数据文件，只重组 manifest 以优化后续扫描（如按分区聚簇、控制 manifest 大小）。
 *   <li>并发重写：用 worker 池并行处理多个源 manifest，每个分区/spec/key 对应一个 {@link WriterWrapper}。
 *   <li>文件数校验：重写前后活跃文件数必须相等，保证不丢数据。
 *   <li>快照 id 继承：根据表属性决定是否继承快照 id，否则需拷贝 manifest 重写 snapshot id。
 * </ul>
 *
 * <p>上下游关系：继承 {@link SnapshotProducer}；被 {@link BaseTransaction#rewriteManifests} 等创建； 依赖 {@link
 * ManifestFiles} 读写 manifest、{@link ManifestReader}/{@link ManifestWriter}。
 */
public class BaseRewriteManifests extends SnapshotProducer<RewriteManifests>
    implements RewriteManifests {
  private static final String KEPT_MANIFESTS_COUNT = "manifests-kept";
  private static final String CREATED_MANIFESTS_COUNT = "manifests-created";
  private static final String REPLACED_MANIFESTS_COUNT = "manifests-replaced";
  private static final String PROCESSED_ENTRY_COUNT = "entries-processed";

  private final TableOperations ops;
  private final Map<Integer, PartitionSpec> specsById;
  private final long manifestTargetSizeBytes;
  private final boolean snapshotIdInheritanceEnabled;

  private final Set<ManifestFile> deletedManifests = Sets.newHashSet();
  private final List<ManifestFile> addedManifests = Lists.newArrayList();
  private final List<ManifestFile> rewrittenAddedManifests = Lists.newArrayList();

  private final Collection<ManifestFile> keptManifests = new ConcurrentLinkedQueue<>();
  private final Collection<ManifestFile> newManifests = new ConcurrentLinkedQueue<>();
  private final Set<ManifestFile> rewrittenManifests = Sets.newConcurrentHashSet();
  private final Map<Object, WriterWrapper> writers = Maps.newConcurrentMap();

  private final AtomicLong entryCount = new AtomicLong(0);

  private Function<DataFile, Object> clusterByFunc;
  private Predicate<ManifestFile> predicate;

  private final SnapshotSummary.Builder summaryBuilder = SnapshotSummary.builder();

  /**
   * 构造 manifest 重写操作。
   *
   * <p>逻辑：读取表属性获取 manifest 目标大小与是否启用快照 id 继承。
   *
   * @param ops 表操作句柄
   */
  BaseRewriteManifests(TableOperations ops) {
    super(ops);
    this.ops = ops;
    this.specsById = ops.current().specsById();
    this.manifestTargetSizeBytes =
        ops.current()
            .propertyAsLong(MANIFEST_TARGET_SIZE_BYTES, MANIFEST_TARGET_SIZE_BYTES_DEFAULT);
    this.snapshotIdInheritanceEnabled =
        ops.current()
            .propertyAsBoolean(
                SNAPSHOT_ID_INHERITANCE_ENABLED, SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT);
  }

  /** 返回自身（fluent API）。 */
  @Override
  protected RewriteManifests self() {
    return this;
  }

  /** 返回本操作的数据操作类型（REPLACE，纯元数据替换）。 */
  @Override
  protected String operation() {
    return DataOperations.REPLACE;
  }

  /**
   * 设置自定义快照 summary 属性。
   *
   * @param property 属性名
   * @param value 属性值
   * @return this，便于链式调用
   */
  @Override
  public RewriteManifests set(String property, String value) {
    summaryBuilder.set(property, value);
    return this;
  }

  /**
   * 构建快照 summary，包含创建/保留/替换的 manifest 数与处理 entry 数。
   *
   * <p>逻辑：统计新创建、保留、被替换的 manifest 数与已处理 entry 数，写入 summary； 因数据未变化，设置分区 summary 上限为 0 以不包含分区
   * summary。
   *
   * @return 快照 summary 映射
   */
  @Override
  protected Map<String, String> summary() {
    int createdManifestsCount =
        newManifests.size() + addedManifests.size() + rewrittenAddedManifests.size();
    summaryBuilder.set(CREATED_MANIFESTS_COUNT, String.valueOf(createdManifestsCount));
    summaryBuilder.set(KEPT_MANIFESTS_COUNT, String.valueOf(keptManifests.size()));
    summaryBuilder.set(
        REPLACED_MANIFESTS_COUNT,
        String.valueOf(rewrittenManifests.size() + deletedManifests.size()));
    summaryBuilder.set(PROCESSED_ENTRY_COUNT, String.valueOf(entryCount.get()));
    summaryBuilder.setPartitionSummaryLimit(
        0); // do not include partition summaries because data did not change
    return summaryBuilder.build();
  }

  /**
   * 设置按数据文件聚簇的函数：相同 key 的文件写入同一 manifest。
   *
   * @param func 聚簇函数
   * @return this，便于链式调用
   */
  @Override
  public RewriteManifests clusterBy(Function<DataFile, Object> func) {
    this.clusterByFunc = func;
    return this;
  }

  /**
   * 设置谓词：只重写满足谓词的 manifest，其余保留。
   *
   * @param pred manifest 谓词
   * @return this，便于链式调用
   */
  @Override
  public RewriteManifests rewriteIf(Predicate<ManifestFile> pred) {
    this.predicate = pred;
    return this;
  }

  /**
   * 标记一个待删除的 manifest。
   *
   * @param manifest 待删除 manifest
   * @return this，便于链式调用
   */
  @Override
  public RewriteManifests deleteManifest(ManifestFile manifest) {
    deletedManifests.add(manifest);
    return this;
  }

  /**
   * 添加一个外部已生成的新 manifest（不含 added/deleted 文件，snapshot id 待分配）。
   *
   * <p>逻辑：校验 manifest 不含 added/deleted 文件、snapshot id 为 null 或 -1、sequence 为 -1； 若启用快照 id 继承且
   * snapshot id 为 null，直接加入 addedManifests；否则需拷贝重写 snapshot id 后加入 rewrittenAddedManifests。
   *
   * @param manifest 待添加 manifest
   * @return this，便于链式调用
   * @throws IllegalArgumentException 若 manifest 含 added/deleted 文件或 snapshot/sequence 已分配
   */
  @Override
  public RewriteManifests addManifest(ManifestFile manifest) {
    Preconditions.checkArgument(!manifest.hasAddedFiles(), "Cannot add manifest with added files");
    Preconditions.checkArgument(
        !manifest.hasDeletedFiles(), "Cannot add manifest with deleted files");
    Preconditions.checkArgument(
        manifest.snapshotId() == null || manifest.snapshotId() == -1,
        "Snapshot id must be assigned during commit");
    Preconditions.checkArgument(
        manifest.sequenceNumber() == -1, "Sequence must be assigned during commit");

    if (snapshotIdInheritanceEnabled && manifest.snapshotId() == null) {
      addedManifests.add(manifest);
    } else {
      // the manifest must be rewritten with this update's snapshot ID
      ManifestFile copiedManifest = copyManifest(manifest);
      rewrittenAddedManifests.add(copiedManifest);
    }

    return this;
  }

  /**
   * 把 manifest 拷贝为新文件，并赋予当前快照 id。
   *
   * <p>逻辑：用 {@link ManifestFiles#copyRewriteManifest} 读旧 manifest、写新 manifest， 并把 snapshot id
   * 设为本次提交的快照 id。
   *
   * @param manifest 待拷贝 manifest
   * @return 拷贝后的新 manifest
   */
  private ManifestFile copyManifest(ManifestFile manifest) {
    TableMetadata current = ops.current();
    InputFile toCopy = ops.io().newInputFile(manifest.path());
    OutputFile newFile = newManifestOutput();
    return ManifestFiles.copyRewriteManifest(
        current.formatVersion(),
        manifest.partitionSpecId(),
        toCopy,
        specsById,
        newFile,
        snapshotId(),
        summaryBuilder);
  }

  /**
   * 计算提交后的 manifest 列表：新 manifest 置前，保留 manifest 居中，删除 manifest 保留在后。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>取当前快照的数据 manifest 集合，校验待删除 manifest 仍存在；
   *   <li>若需要重写（有 clusterBy 函数且未处理过），执行 {@link #performRewrite}；否则 {@link #keepActiveManifests}；
   *   <li>校验文件数一致；
   *   <li>把新 manifest（newManifests+addedManifests+rewrittenAddedManifests）赋予 snapshot id 置前， 加上
   *       keptManifests，再加上当前快照的删除 manifest（不变）。
   * </ol>
   *
   * @param base 基础表元数据
   * @param snapshot 当前快照
   * @return 提交后的 manifest 列表
   */
  @Override
  public List<ManifestFile> apply(TableMetadata base, Snapshot snapshot) {
    List<ManifestFile> currentManifests = base.currentSnapshot().dataManifests(ops.io());
    Set<ManifestFile> currentManifestSet = ImmutableSet.copyOf(currentManifests);

    validateDeletedManifests(currentManifestSet);

    if (requiresRewrite(currentManifestSet)) {
      performRewrite(currentManifests);
    } else {
      keepActiveManifests(currentManifests);
    }

    validateFilesCounts();

    Iterable<ManifestFile> newManifestsWithMetadata =
        Iterables.transform(
            Iterables.concat(newManifests, addedManifests, rewrittenAddedManifests),
            manifest -> GenericManifestFile.copyOf(manifest).withSnapshotId(snapshotId()).build());

    // put new manifests at the beginning
    List<ManifestFile> apply = Lists.newArrayList();
    Iterables.addAll(apply, newManifestsWithMetadata);
    apply.addAll(keptManifests);
    apply.addAll(base.currentSnapshot().deleteManifests(ops.io()));

    return apply;
  }

  /**
   * 判断是否需要执行重写。
   *
   * <p>逻辑：无 clusterBy 函数则不需重写（直接删/加）；已处理过 manifest 则检查是否所有已处理 manifest
   * 仍在当前集合中，若有不在则需全量重写；否则（未处理过）需全量重写。
   *
   * @param currentManifests 当前 manifest 集合
   * @return true 表示需要重写
   */
  private boolean requiresRewrite(Set<ManifestFile> currentManifests) {
    if (clusterByFunc == null) {
      // manifests are deleted and added directly so don't perform a rewrite
      return false;
    }

    if (rewrittenManifests.size() == 0) {
      // nothing yet processed so perform a full rewrite
      return true;
    }

    // if any processed manifest is not in the current manifest list, perform a full rewrite
    return rewrittenManifests.stream().anyMatch(manifest -> !currentManifests.contains(manifest));
  }

  /**
   * 保留未被处理、未被删除的现有 manifest。
   *
   * <p>逻辑：清空 keptManifests，把当前 manifest 中既不在 rewrittenManifests 也不在 deletedManifests 的加入
   * keptManifests。
   *
   * @param currentManifests 当前 manifest 列表
   */
  private void keepActiveManifests(List<ManifestFile> currentManifests) {
    // keep any existing manifests as-is that were not processed
    keptManifests.clear();
    currentManifests.stream()
        .filter(
            manifest ->
                !rewrittenManifests.contains(manifest) && !deletedManifests.contains(manifest))
        .forEach(keptManifests::add);
  }

  /**
   * 重置重写过程中的中间状态，并清理未提交的新 manifest。
   *
   * <p>逻辑：清理 newManifests，重置 entry 计数、kept/rewritten/new 列表与 writers 映射。
   */
  private void reset() {
    cleanUncommitted(newManifests, ImmutableSet.of());
    entryCount.set(0);
    keptManifests.clear();
    rewrittenManifests.clear();
    newManifests.clear();
    writers.clear();
  }

  /**
   * 执行重写：把当前 manifest 中（未删除）的活跃 entry 按 clusterBy 重新分桶写入新 manifest。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>reset 清理中间状态；
   *   <li>过滤掉待删除 manifest 得到 remainingManifests；
   *   <li>用 worker 池并行处理每个 manifest：若 predicate 不匹配则保留，否则标记为 rewritten， 读取其活跃 entry，按 clusterBy
   *       函数分桶追加到对应 {@link WriterWrapper}；
   *   <li>最终并行关闭所有 writer。
   * </ol>
   *
   * @param currentManifests 当前 manifest 列表
   */
  private void performRewrite(List<ManifestFile> currentManifests) {
    reset();

    List<ManifestFile> remainingManifests =
        currentManifests.stream()
            .filter(manifest -> !deletedManifests.contains(manifest))
            .collect(Collectors.toList());

    try {
      Tasks.foreach(remainingManifests)
          .executeWith(workerPool())
          .run(
              manifest -> {
                if (predicate != null && !predicate.test(manifest)) {
                  keptManifests.add(manifest);
                } else {
                  rewrittenManifests.add(manifest);
                  try (ManifestReader<DataFile> reader =
                      ManifestFiles.read(manifest, ops.io(), ops.current().specsById())
                          .select(Arrays.asList("*"))) {
                    reader
                        .liveEntries()
                        .forEach(
                            entry ->
                                appendEntry(
                                    entry,
                                    clusterByFunc.apply(entry.file()),
                                    manifest.partitionSpecId()));

                  } catch (IOException x) {
                    throw new RuntimeIOException(x);
                  }
                }
              });
    } finally {
      Tasks.foreach(writers.values()).executeWith(workerPool()).run(WriterWrapper::close);
    }
  }

  /**
   * 校验待删除 manifest 仍存在于当前快照。
   *
   * <p>逻辑：找出 deletedManifests 中不在当前集合的 manifest，若有则抛 {@link ValidationException}。
   *
   * @param currentManifests 当前 manifest 集合
   * @throws ValidationException 若有待删除 manifest 已不存在
   */
  private void validateDeletedManifests(Set<ManifestFile> currentManifests) {
    // directly deleted manifests must be still present in the current snapshot
    deletedManifests.stream()
        .filter(manifest -> !currentManifests.contains(manifest))
        .findAny()
        .ifPresent(
            manifest -> {
              throw new ValidationException("Manifest is missing: %s", manifest.path());
            });
  }

  /**
   * 校验重写前后活跃文件数一致。
   *
   * <p>逻辑：分别统计新创建与被替换 manifest 的活跃文件数（added+existing），不一致则抛异常。
   *
   * @throws ValidationException 若文件数不一致
   */
  private void validateFilesCounts() {
    Iterable<ManifestFile> createdManifests =
        Iterables.concat(newManifests, addedManifests, rewrittenAddedManifests);
    int createdManifestsFilesCount = activeFilesCount(createdManifests);

    Iterable<ManifestFile> replacedManifests =
        Iterables.concat(rewrittenManifests, deletedManifests);
    int replacedManifestsFilesCount = activeFilesCount(replacedManifests);

    if (createdManifestsFilesCount != replacedManifestsFilesCount) {
      throw new ValidationException(
          "Replaced and created manifests must have the same number of active files: %d (new), %d (old)",
          createdManifestsFilesCount, replacedManifestsFilesCount);
    }
  }

  /**
   * 统计一组 manifest 的活跃文件数（added + existing）。
   *
   * @param manifests manifest 集合
   * @return 活跃文件总数
   */
  private int activeFilesCount(Iterable<ManifestFile> manifests) {
    int activeFilesCount = 0;

    for (ManifestFile manifest : manifests) {
      Preconditions.checkNotNull(
          manifest.addedFilesCount(), "Missing file counts in %s", manifest.path());
      Preconditions.checkNotNull(
          manifest.existingFilesCount(), "Missing file counts in %s", manifest.path());
      activeFilesCount += manifest.addedFilesCount();
      activeFilesCount += manifest.existingFilesCount();
    }

    return activeFilesCount;
  }

  /**
   * 把一个 entry 追加到对应的 writer（按 clusterBy key 与 spec id 分桶）。
   *
   * <p>逻辑：用 (key, specId) 从 writers 取或创建 {@link WriterWrapper}，追加 entry 并递增计数。
   *
   * @param entry manifest entry
   * @param key 聚簇 key
   * @param partitionSpecId 分区 spec id
   */
  private void appendEntry(ManifestEntry<DataFile> entry, Object key, int partitionSpecId) {
    Preconditions.checkNotNull(entry, "Manifest entry cannot be null");
    Preconditions.checkNotNull(key, "Key cannot be null");

    WriterWrapper writer = getWriter(key, partitionSpecId);
    writer.addEntry(entry);
    entryCount.incrementAndGet();
  }

  /**
   * 按 (key, specId) 获取或创建 writer。
   *
   * @param key 聚簇 key
   * @param partitionSpecId 分区 spec id
   * @return 对应的 {@link WriterWrapper}
   */
  private WriterWrapper getWriter(Object key, int partitionSpecId) {
    return writers.computeIfAbsent(
        Pair.of(key, partitionSpecId), k -> new WriterWrapper(specsById.get(partitionSpecId)));
  }

  /**
   * 清理未提交的新 manifest：committed 集合之外的 newManifests 与 rewrittenAddedManifests 删除文件。
   *
   * <p>设计要点：addedManifests 不清理，因其由外部添加、不属于本表拥有、不做压缩。
   *
   * @param committed 已提交的 manifest 集合
   */
  @Override
  protected void cleanUncommitted(Set<ManifestFile> committed) {
    cleanUncommitted(newManifests, committed);
    // clean up only rewrittenAddedManifests as they are always owned by the table
    // don't clean up addedManifests as they are added to the manifest list and are not compacted
    cleanUncommitted(rewrittenAddedManifests, committed);
  }

  /**
   * 删除给定 manifest 集合中未被提交的 manifest 文件。
   *
   * @param manifests 待检查 manifest
   * @param committedManifests 已提交 manifest 集合
   */
  private void cleanUncommitted(
      Iterable<ManifestFile> manifests, Set<ManifestFile> committedManifests) {
    for (ManifestFile manifest : manifests) {
      if (!committedManifests.contains(manifest)) {
        deleteFile(manifest.path());
      }
    }
  }

  /** 返回 manifest 目标大小（字节），供 {@link WriterWrapper} 判断是否切分。 */
  long getManifestTargetSizeBytes() {
    return manifestTargetSizeBytes;
  }

  /**
   * Manifest 写入封装：按目标大小自动切分新 manifest，线程安全。
   *
   * <p>设计意图：同一 (key, specId) 的 entry 可能很多，超过目标大小时自动关闭当前 writer、 新建 writer，保证单个 manifest
   * 不超过目标大小。addEntry 与 close 加锁保证并发安全。
   */
  class WriterWrapper {
    private final PartitionSpec spec;
    private ManifestWriter<DataFile> writer;

    /**
     * 构造 writer 封装。
     *
     * @param spec 分区 spec
     */
    WriterWrapper(PartitionSpec spec) {
      this.spec = spec;
    }

    /**
     * 追加一个 entry，必要时切分新 manifest。
     *
     * <p>逻辑：若 writer 为 null 则新建；若当前 writer 长度已达目标大小则先 close 再新建； 最后以 EXISTING 状态写入
     * entry（重写场景下都是现存文件）。
     *
     * @param entry 待追加 entry
     */
    synchronized void addEntry(ManifestEntry<DataFile> entry) {
      if (writer == null) {
        writer = newManifestWriter(spec);
      } else if (writer.length() >= getManifestTargetSizeBytes()) {
        close();
        writer = newManifestWriter(spec);
      }
      writer.existing(entry);
    }

    /**
     * 关闭当前 writer 并把生成的 manifest 加入 newManifests。
     *
     * <p>逻辑：若 writer 非 null，则关闭它并收集 {@link ManifestWriter#toManifestFile} 结果。
     *
     * @throws RuntimeIOException 若关闭时发生 IO 异常
     */
    synchronized void close() {
      if (writer != null) {
        try {
          writer.close();
          newManifests.add(writer.toManifestFile());
        } catch (IOException x) {
          throw new RuntimeIOException(x);
        }
      }
    }
  }
}
