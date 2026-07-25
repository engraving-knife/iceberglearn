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

import java.util.List;
import java.util.Map;
import org.apache.iceberg.exceptions.CherrypickAncestorCommitException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.PartitionSet;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.iceberg.util.WapUtil;

/**
 * Cherry-pick（拣选）操作的 core 实现：将指定快照的变更应用到当前表状态。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现快照 cherry-pick：将目标快照的数据变更（新增/删除文件）应用到当前表，生成新快照。
 *   <li>支持三种场景：APPEND 拣选、ReplacePartitions 拣选、快进（fast-forward）。
 *   <li>校验拣选可行性：目标快照不能是当前祖先、ReplacePartitions 的分区未被并发修改。
 *   <li>处理 WAP（Write-Audit-Publish）发布校验。
 * </ul>
 *
 * <p>设计意图：cherry-pick 不通过 Table API 暴露，而是作为 Transaction 的包私有部分， 供 {@link ManageSnapshots} 使用。继承
 * {@link MergingSnapshotProducer} 复用合并提交骨架。
 *
 * <p>上下游关系：由 {@link SnapshotManager} 通过 {@link BaseTransaction#cherryPick} 创建； 依赖 {@link
 * SnapshotUtil} 做祖先链判断、{@link WapUtil} 做 WAP 校验。
 */
class CherryPickOperation extends MergingSnapshotProducer<CherryPickOperation> {

  private final FileIO io;
  private final Map<Integer, PartitionSpec> specsById;
  private Snapshot cherrypickSnapshot = null;
  private boolean requireFastForward = false;
  private PartitionSet replacedPartitions = null;

  /**
   * 构造 cherry-pick 操作。
   *
   * @param tableName 表名
   * @param ops 表操作句柄
   */
  CherryPickOperation(String tableName, TableOperations ops) {
    super(tableName, ops);
    this.io = ops.io();
    this.specsById = ops.current().specsById();
  }

  /** 返回自身（fluent API）。 */
  @Override
  protected CherryPickOperation self() {
    return this;
  }

  /**
   * 返回本操作的数据操作类型：取自目标 cherry-pick 快照的操作类型。
   *
   * @return 目标快照的操作类型字符串
   */
  @Override
  protected String operation() {
    // snapshotOperation is used by SnapshotProducer when building and writing a new snapshot for
    // cherrypick
    Preconditions.checkNotNull(cherrypickSnapshot, "[BUG] Detected uninitialized operation");
    return cherrypickSnapshot.operation();
  }

  /**
   * 配置要 cherry-pick 的目标快照，并收集其文件变更。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>APPEND：校验 WAP 发布，设置源快照 id，收集新增数据文件；
   *   <li>ReplacePartitions：校验父快照是当前祖先，校验 WAP，收集新增/删除文件并记录替换分区；
   *   <li>其它：尝试快进，标记 requireFastForward，后续 apply 时校验。
   * </ul>
   *
   * @param snapshotId 目标快照 id
   * @return this，便于链式调用
   * @throws ValidationException 若快照不存在或不可 cherry-pick
   */
  public CherryPickOperation cherrypick(long snapshotId) {
    TableMetadata current = current();
    this.cherrypickSnapshot = current.snapshot(snapshotId);
    ValidationException.check(
        cherrypickSnapshot != null, "Cannot cherry-pick unknown snapshot ID: %s", snapshotId);

    if (cherrypickSnapshot.operation().equals(DataOperations.APPEND)) {
      // this property is set on target snapshot that will get published
      String wapId = WapUtil.validateWapPublish(current, snapshotId);
      if (wapId != null) {
        set(SnapshotSummary.PUBLISHED_WAP_ID_PROP, wapId);
      }

      // link the snapshot about to be published on commit with the picked snapshot
      set(SnapshotSummary.SOURCE_SNAPSHOT_ID_PROP, String.valueOf(snapshotId));

      // Pick modifications from the snapshot
      for (DataFile addedFile : cherrypickSnapshot.addedDataFiles(io)) {
        add(addedFile);
      }

    } else if (cherrypickSnapshot.operation().equals(DataOperations.OVERWRITE)
        && PropertyUtil.propertyAsBoolean(
            cherrypickSnapshot.summary(), SnapshotSummary.REPLACE_PARTITIONS_PROP, false)) {
      // the operation was ReplacePartitions. this can be cherry-picked iff the partitions have not
      // been modified.
      // detecting modification requires finding the new files since the parent was committed, so
      // the parent must be an
      // ancestor of the current state, or null if the overwrite was based on an empty table.
      ValidationException.check(
          cherrypickSnapshot.parentId() == null
              || isCurrentAncestor(current, cherrypickSnapshot.parentId()),
          "Cannot cherry-pick overwrite not based on an ancestor of the current state: %s",
          snapshotId);

      // this property is set on target snapshot that will get published
      String wapId = WapUtil.validateWapPublish(current, snapshotId);
      if (wapId != null) {
        set(SnapshotSummary.PUBLISHED_WAP_ID_PROP, wapId);
      }

      // link the snapshot about to be published on commit with the picked snapshot
      set(SnapshotSummary.SOURCE_SNAPSHOT_ID_PROP, String.valueOf(snapshotId));

      // check that all deleted files are still in the table
      failMissingDeletePaths();

      // copy adds from the picked snapshot
      this.replacedPartitions = PartitionSet.create(specsById);
      for (DataFile addedFile : cherrypickSnapshot.addedDataFiles(io)) {
        add(addedFile);
        replacedPartitions.add(addedFile.specId(), addedFile.partition());
      }

      // copy deletes from the picked snapshot
      for (DataFile deletedFile : cherrypickSnapshot.removedDataFiles(io)) {
        delete(deletedFile);
      }

    } else {
      // attempt to fast-forward
      ValidationException.check(
          isFastForward(current),
          "Cannot cherry-pick snapshot %s: not append, dynamic overwrite, or fast-forward",
          cherrypickSnapshot.snapshotId());
      this.requireFastForward = true;
    }

    return this;
  }

  /**
   * 返回更新事件：快进时无新快照返回 null，否则委托父类。
   *
   * @return 更新事件，或 null（无操作/快进时）
   */
  @Override
  public Object updateEvent() {
    if (cherrypickSnapshot == null) {
      // NOOP operation, no snapshot created
      return null;
    }

    TableMetadata tableMetadata = refresh();
    long snapshotId = tableMetadata.currentSnapshot().snapshotId();
    if (cherrypickSnapshot.snapshotId() == snapshotId) {
      // No new snapshot is created for fast-forward
      return null;
    } else {
      // New snapshot created, we rely on super class to fire a CreateSnapshotEvent
      return super.updateEvent();
    }
  }

  /**
   * 提交前校验：非快进场景下校验目标快照非祖先、替换分区未被修改、WAP 发布。
   *
   * @param base 基础表元数据
   * @param snapshot 当前快照
   * @throws CherrypickAncestorCommitException 若目标快照已是当前祖先
   * @throws ValidationException 若替换分区已被并发修改
   */
  @Override
  protected void validate(TableMetadata base, Snapshot snapshot) {
    // this is only called after apply() passes off to super, but check fast-forward status just in
    // case
    if (!isFastForward(base)) {
      validateNonAncestor(base, cherrypickSnapshot.snapshotId());
      validateReplacedPartitions(base, cherrypickSnapshot.parentId(), replacedPartitions, io);
      WapUtil.validateWapPublish(base, cherrypickSnapshot.snapshotId());
    }
  }

  /**
   * 判断是否可快进：目标快照的父快照是当前快照（或两者都为 null）。
   *
   * @param base 基础表元数据
   * @return true 表示可快进
   */
  private boolean isFastForward(TableMetadata base) {
    if (base.currentSnapshot() != null) {
      // can fast-forward if the cherry-picked snapshot's parent is the current snapshot
      return cherrypickSnapshot.parentId() != null
          && base.currentSnapshot().snapshotId() == cherrypickSnapshot.parentId();
    } else {
      // ... or if the parent and current snapshot are both null
      return cherrypickSnapshot.parentId() == null;
    }
  }

  /**
   * 应用 cherry-pick：快进则直接返回目标快照，否则委托父类生成新快照。
   *
   * <p>逻辑：刷新元数据；若无目标快照则 NOOP；若 requireFastForward 或可快进则返回目标快照； 否则走父类 apply（会触发 validate）。
   *
   * @return 结果快照
   * @throws ValidationException 若不可 cherry-pick
   */
  @Override
  public Snapshot apply() {
    TableMetadata base = refresh();

    if (cherrypickSnapshot == null) {
      // if no target snapshot was configured then NOOP by returning current state
      return base.currentSnapshot();
    }

    boolean isFastForward = isFastForward(base);
    if (requireFastForward || isFastForward) {
      ValidationException.check(
          isFastForward,
          "Cannot cherry-pick snapshot %s: not append, dynamic overwrite, or fast-forward",
          cherrypickSnapshot.snapshotId());
      return base.snapshot(cherrypickSnapshot.snapshotId());
    } else {
      // validate(TableMetadata) is called in apply(TableMetadata) after this apply refreshes the
      // table state
      return super.apply();
    }
  }

  /**
   * 校验目标快照不是当前祖先（直接或通过 source snapshot 关联）。
   *
   * @param meta 表元数据
   * @param snapshotId 目标快照 id
   * @throws CherrypickAncestorCommitException 若目标快照已是祖先
   */
  private static void validateNonAncestor(TableMetadata meta, long snapshotId) {
    if (isCurrentAncestor(meta, snapshotId)) {
      throw new CherrypickAncestorCommitException(snapshotId);
    }

    Long ancestorId = lookupAncestorBySourceSnapshot(meta, snapshotId);
    if (ancestorId != null) {
      throw new CherrypickAncestorCommitException(snapshotId, ancestorId);
    }
  }

  /**
   * 校验替换分区在父快照到当前快照之间未被并发修改。
   *
   * <p>逻辑：遍历父快照到当前快照之间新增的文件，检查是否有文件落在替换分区内。
   *
   * @param meta 表元数据
   * @param parentId 父快照 id
   * @param replacedPartitions 被替换的分区集合
   * @param io 文件 IO
   * @throws ValidationException 若有分区被并发修改
   */
  private static void validateReplacedPartitions(
      TableMetadata meta, Long parentId, PartitionSet replacedPartitions, FileIO io) {
    if (replacedPartitions != null && meta.currentSnapshot() != null) {
      ValidationException.check(
          parentId == null || isCurrentAncestor(meta, parentId),
          "Cannot cherry-pick overwrite, based on non-ancestor of the current state: %s",
          parentId);
      List<DataFile> newFiles =
          SnapshotUtil.newFiles(parentId, meta.currentSnapshot().snapshotId(), meta::snapshot, io);
      for (DataFile newFile : newFiles) {
        ValidationException.check(
            !replacedPartitions.contains(newFile.specId(), newFile.partition()),
            "Cannot cherry-pick replace partitions with changed partition: %s",
            newFile.partition());
      }
    }
  }

  /**
   * 在当前祖先链中查找是否有快照的 source snapshot id 等于给定 id（即已被 cherry-pick 过）。
   *
   * @param meta 表元数据
   * @param snapshotId 目标快照 id
   * @return 已 cherry-pick 该快照的祖先 id，或 null
   */
  private static Long lookupAncestorBySourceSnapshot(TableMetadata meta, long snapshotId) {
    String snapshotIdStr = String.valueOf(snapshotId);
    for (long ancestorId : currentAncestors(meta)) {
      Map<String, String> summary = meta.snapshot(ancestorId).summary();
      if (summary != null
          && snapshotIdStr.equals(summary.get(SnapshotSummary.SOURCE_SNAPSHOT_ID_PROP))) {
        return ancestorId;
      }
    }

    return null;
  }

  /** 返回当前快照的祖先 id 列表。 */
  private static List<Long> currentAncestors(TableMetadata meta) {
    return SnapshotUtil.ancestorIds(meta.currentSnapshot(), meta::snapshot);
  }

  /** 判断给定快照 id 是否是当前快照的祖先。 */
  private static boolean isCurrentAncestor(TableMetadata meta, long snapshotId) {
    return currentAncestors(meta).contains(snapshotId);
  }
}
