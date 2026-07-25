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

import static org.apache.iceberg.TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED;
import static org.apache.iceberg.TableProperties.SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.events.CreateSnapshotEvent;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 快速追加实现：每次写入直接生成一个新的 manifest 文件并追加到快照，不做 manifest 合并。
 *
 * <p>所属模块：iceberg-core（快照生产层，实现 {@link AppendFiles} 接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把新数据文件写入一个新的 manifest（rolling writer），并把该 manifest 追加到快照的 manifest list；
 *   <li>支持追加已有 manifest（{@link #appendManifest}），按需重写以补全 snapshotId/sequenceNumber；
 *   <li>提交失败时清理未提交的 manifest 文件。
 * </ul>
 *
 * <p>设计意图："快速"指不触发 manifest 合并（与 {@link MergeAppend} 相对），适合小批量追加； 代价是 manifest 数量会增长，后续需通过
 * compaction 整理。提交会重试最多 5 次。
 *
 * <p>上下游关系：由表 API（{@code table.newFastAppend()}）构造；底层依赖 {@link SnapshotProducer} 的提交框架与 {@link
 * RollingManifestWriter} 写 manifest。
 */
class FastAppend extends SnapshotProducer<AppendFiles> implements AppendFiles {
  private final String tableName;
  private final TableOperations ops;
  private final PartitionSpec spec;
  private final boolean snapshotIdInheritanceEnabled;
  private final SnapshotSummary.Builder summaryBuilder = SnapshotSummary.builder();
  private final List<DataFile> newFiles = Lists.newArrayList();
  private final List<ManifestFile> appendManifests = Lists.newArrayList();
  private final List<ManifestFile> rewrittenAppendManifests = Lists.newArrayList();
  private List<ManifestFile> newManifests = null;
  private boolean hasNewFiles = false;

  /**
   * 构造一个快速追加操作。
   *
   * @param tableName 表名
   * @param ops 表操作接口
   */
  FastAppend(String tableName, TableOperations ops) {
    super(ops);
    this.tableName = tableName;
    this.ops = ops;
    this.spec = ops.current().spec();
    this.snapshotIdInheritanceEnabled =
        ops.current()
            .propertyAsBoolean(
                SNAPSHOT_ID_INHERITANCE_ENABLED, SNAPSHOT_ID_INHERITANCE_ENABLED_DEFAULT);
  }

  /** 返回自身，用于泛型 self() 模式。 */
  @Override
  protected AppendFiles self() {
    return this;
  }

  /**
   * 设置快照摘要中的自定义属性。
   *
   * @param property 属性名
   * @param value 属性值
   * @return 当前操作
   */
  @Override
  public AppendFiles set(String property, String value) {
    summaryBuilder.set(property, value);
    return this;
  }

  /** 返回该操作对应的快照操作类型，追加为 APPEND。 */
  @Override
  protected String operation() {
    return DataOperations.APPEND;
  }

  /**
   * 构建快照摘要，含分区级摘要限制。
   *
   * @return 摘要属性 map
   */
  @Override
  protected Map<String, String> summary() {
    summaryBuilder.setPartitionSummaryLimit(
        ops.current()
            .propertyAsInt(
                TableProperties.WRITE_PARTITION_SUMMARY_LIMIT,
                TableProperties.WRITE_PARTITION_SUMMARY_LIMIT_DEFAULT));
    return summaryBuilder.build();
  }

  /**
   * 追加一个数据文件到本次提交。
   *
   * @param file 待追加的数据文件
   * @return 当前操作
   */
  @Override
  public FastAppend appendFile(DataFile file) {
    this.hasNewFiles = true;
    newFiles.add(file);
    summaryBuilder.addedFile(spec, file);
    return this;
  }

  /**
   * 指定提交目标分支。
   *
   * @param branch 分支名
   * @return 当前操作
   */
  @Override
  public FastAppend toBranch(String branch) {
    targetBranch(branch);
    return this;
  }

  /**
   * 追加一个已有 manifest 到本次提交。
   *
   * <p>逻辑：校验 manifest 不含 existing/deleted 文件、snapshotId 与 sequenceNumber 未分配； 若启用了
   * snapshotIdInheritance 且 manifest 的 snapshotId 为 null，直接复用；否则重写 manifest 以补全本次提交的 snapshotId。
   *
   * @param manifest 待追加的 manifest
   * @return 当前操作
   */
  @Override
  public FastAppend appendManifest(ManifestFile manifest) {
    Preconditions.checkArgument(
        !manifest.hasExistingFiles(), "Cannot append manifest with existing files");
    Preconditions.checkArgument(
        !manifest.hasDeletedFiles(), "Cannot append manifest with deleted files");
    Preconditions.checkArgument(
        manifest.snapshotId() == null || manifest.snapshotId() == -1,
        "Snapshot id must be assigned during commit");
    Preconditions.checkArgument(
        manifest.sequenceNumber() == -1, "Sequence number must be assigned during commit");

    if (snapshotIdInheritanceEnabled && manifest.snapshotId() == null) {
      summaryBuilder.addedManifest(manifest);
      appendManifests.add(manifest);
    } else {
      // the manifest must be rewritten with this update's snapshot ID
      ManifestFile copiedManifest = copyManifest(manifest);
      rewrittenAppendManifests.add(copiedManifest);
    }

    return this;
  }

  /**
   * 复制 manifest 并补全本次提交的 snapshotId。
   *
   * @param manifest 原始 manifest
   * @return 复制后的 manifest
   */
  private ManifestFile copyManifest(ManifestFile manifest) {
    TableMetadata current = ops.current();
    InputFile toCopy = ops.io().newInputFile(manifest.path());
    OutputFile newManifestPath = newManifestOutput();
    return ManifestFiles.copyAppendManifest(
        current.formatVersion(),
        manifest.partitionSpecId(),
        toCopy,
        current.specsById(),
        newManifestPath,
        snapshotId(),
        summaryBuilder);
  }

  /**
   * 计算本次提交后的 manifest 列表。
   *
   * <p>逻辑：写入新数据文件对应的 manifest；追加已有 manifest（补全 snapshotId）；保留父快照的 全部 manifest。
   *
   * @param base 基线元数据
   * @param snapshot 父快照
   * @return 合并后的 manifest 列表
   */
  @Override
  public List<ManifestFile> apply(TableMetadata base, Snapshot snapshot) {
    List<ManifestFile> manifests = Lists.newArrayList();

    try {
      List<ManifestFile> newWrittenManifests = writeNewManifests();
      if (newWrittenManifests != null) {
        manifests.addAll(newWrittenManifests);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to write manifest");
    }

    Iterable<ManifestFile> appendManifestsWithMetadata =
        Iterables.transform(
            Iterables.concat(appendManifests, rewrittenAppendManifests),
            manifest -> GenericManifestFile.copyOf(manifest).withSnapshotId(snapshotId()).build());
    Iterables.addAll(manifests, appendManifestsWithMetadata);

    if (snapshot != null) {
      manifests.addAll(snapshot.allManifests(ops.io()));
    }

    return manifests;
  }

  /**
   * 构造提交成功后的事件，用于通知监听器。
   *
   * @return {@link CreateSnapshotEvent}
   */
  @Override
  public Object updateEvent() {
    long snapshotId = snapshotId();
    Snapshot snapshot = ops.current().snapshot(snapshotId);
    long sequenceNumber = snapshot.sequenceNumber();
    return new CreateSnapshotEvent(
        tableName, operation(), snapshotId, sequenceNumber, snapshot.summary());
  }

  /**
   * 清理未提交的 manifest 文件。
   *
   * <p>逻辑：删除本次写入但未被提交的 manifest；删除重写过但未被提交的 manifest。 appendManifests 不清理（它们由调用方管理）。
   *
   * @param committed 已提交的 manifest 集合
   */
  @Override
  protected void cleanUncommitted(Set<ManifestFile> committed) {
    if (newManifests != null) {
      boolean hasDeletes = false;
      for (ManifestFile manifest : newManifests) {
        if (!committed.contains(manifest)) {
          deleteFile(manifest.path());
          hasDeletes = true;
        }
      }
      if (hasDeletes) {
        this.newManifests = null;
      }
    }

    // clean up only rewrittenAppendManifests as they are always owned by the table
    // don't clean up appendManifests as they are added to the manifest list and are not compacted
    for (ManifestFile manifest : rewrittenAppendManifests) {
      if (!committed.contains(manifest)) {
        deleteFile(manifest.path());
      }
    }
  }

  /**
   * 写入新数据文件对应的 manifest（rolling writer）。
   *
   * <p>逻辑：若有新文件且之前已写过 manifest，先删旧再重写；用 rolling writer 把所有新数据文件 写入一个或多个 manifest，并返回 manifest 列表。
   *
   * @return 写入的 manifest 列表，若无新文件返回 null
   * @throws IOException 写入失败
   */
  private List<ManifestFile> writeNewManifests() throws IOException {
    if (hasNewFiles && newManifests != null) {
      newManifests.forEach(file -> deleteFile(file.path()));
      newManifests = null;
    }

    if (newManifests == null && newFiles.size() > 0) {
      RollingManifestWriter<DataFile> writer = newRollingManifestWriter(spec);
      try {
        newFiles.forEach(writer::add);
      } finally {
        writer.close();
      }

      this.newManifests = writer.toManifestFiles();
      hasNewFiles = false;
    }

    return newManifests;
  }
}
