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
package org.apache.iceberg.actions;

import java.util.Set;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.RewriteFiles;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据文件重写动作的提交管理器。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将若干 {@link RewriteFileGroup} 的重写结果合并为一次 {@link RewriteFiles} 提交：删除旧数据文件、 添加新数据文件。
 *   <li>提供提交失败后的文件清理（abort）能力，以及提交或清理的组合入口 {@link #commitOrClean(Set)}。
 *   <li>提供异步批量提交服务 {@link CommitService}（基于 {@link BaseCommitService}）。
 * </ul>
 *
 * <p>设计意图：把"如何提交重写结果"与"如何执行重写"解耦，使不同平台（Spark/Flink 等）的 RewriteDataFiles 动作复用同一套提交与清理逻辑。通过 {@code
 * startingSnapshotId} 做乐观锁校验， 可选地使用起始序列号以保证新文件序列号语义。
 *
 * <p>上下游关系：被各平台的 {@link RewriteDataFiles} 动作调用；内部使用 {@link Table#newRewrite()} 生成提交操作，依赖 {@link
 * BaseCommitService} 提供异步提交能力。
 */
public class RewriteDataFilesCommitManager {
  private static final Logger LOG = LoggerFactory.getLogger(RewriteDataFilesCommitManager.class);

  private final Table table;
  private final long startingSnapshotId;
  private final boolean useStartingSequenceNumber;

  /** 测试用构造器，以表当前快照作为起始快照。 */
  public RewriteDataFilesCommitManager(Table table) {
    this(table, table.currentSnapshot().snapshotId());
  }

  /**
   * 构造提交管理器，使用默认的起始序列号策略。
   *
   * @param table 目标表
   * @param startingSnapshotId 用于乐观锁校验的起始快照 ID
   */
  public RewriteDataFilesCommitManager(Table table, long startingSnapshotId) {
    this(table, startingSnapshotId, RewriteDataFiles.USE_STARTING_SEQUENCE_NUMBER_DEFAULT);
  }

  /**
   * 构造提交管理器。
   *
   * @param table 目标表
   * @param startingSnapshotId 用于乐观锁校验的起始快照 ID
   * @param useStartingSequenceNumber 是否使用起始快照的序列号写入新文件
   */
  public RewriteDataFilesCommitManager(
      Table table, long startingSnapshotId, boolean useStartingSequenceNumber) {
    this.table = table;
    this.startingSnapshotId = startingSnapshotId;
    this.useStartingSequenceNumber = useStartingSequenceNumber;
  }

  /**
   * 将一组文件组的重写结果合并为一次提交：删除被重写的旧数据文件、添加新写入的数据文件。
   *
   * <p>逻辑：汇总所有组的 rewrittenFiles 与 addedFiles；以 {@code startingSnapshotId} 校验创建 {@link
   * RewriteFiles}；若启用起始序列号则用起始快照的序列号，否则使用默认序列号语义；最后提交。
   *
   * @param fileGroups 待提交的文件组集合
   */
  public void commitFileGroups(Set<RewriteFileGroup> fileGroups) {
    Set<DataFile> rewrittenDataFiles = Sets.newHashSet();
    Set<DataFile> addedDataFiles = Sets.newHashSet();
    for (RewriteFileGroup group : fileGroups) {
      rewrittenDataFiles.addAll(group.rewrittenFiles());
      addedDataFiles.addAll(group.addedFiles());
    }

    RewriteFiles rewrite = table.newRewrite().validateFromSnapshot(startingSnapshotId);
    if (useStartingSequenceNumber) {
      long sequenceNumber = table.snapshot(startingSnapshotId).sequenceNumber();
      rewrite.rewriteFiles(rewrittenDataFiles, addedDataFiles, sequenceNumber);
    } else {
      rewrite.rewriteFiles(rewrittenDataFiles, addedDataFiles);
    }

    rewrite.commit();
  }

  /**
   * 清理指定文件组产生的新文件，不应抛出异常。
   *
   * <p>逻辑：遍历 addedFiles 逐个删除，删除失败仅告警不抛出。
   *
   * @param fileGroup 已重写的文件组
   */
  public void abortFileGroup(RewriteFileGroup fileGroup) {
    Preconditions.checkState(
        fileGroup.addedFiles() != null, "Cannot abort a fileGroup that was not rewritten");

    Tasks.foreach(fileGroup.addedFiles())
        .noRetry()
        .suppressFailureWhenFinished()
        .onFailure((dataFile, exc) -> LOG.warn("Failed to delete: {}", dataFile.path(), exc))
        .run(dataFile -> table.io().deleteFile(dataFile.path().toString()));
  }

  /**
   * 提交一组文件组，失败时清理已写入的新文件。
   *
   * <p>逻辑：尝试提交；遇到 {@link CommitStateUnknownException}（提交状态未知，可能已成功）时不清理 直接抛出；遇到其他异常则清理所有组的新文件后重新抛出。
   *
   * @param rewriteGroups 待提交的文件组集合
   */
  public void commitOrClean(Set<RewriteFileGroup> rewriteGroups) {
    try {
      commitFileGroups(rewriteGroups);
    } catch (CommitStateUnknownException e) {
      LOG.error(
          "Commit state unknown for {}, cannot clean up files because they may have been committed successfully.",
          rewriteGroups,
          e);
      throw e;
    } catch (Exception e) {
      LOG.error("Cannot commit groups {}, attempting to clean up written files", rewriteGroups, e);
      rewriteGroups.forEach(this::abortFileGroup);
      throw e;
    }
  }

  /**
   * 创建一个异步提交服务，支持部分进度：各文件组重写完成后陆续提交，提交失败不影响其他组。
   *
   * @param rewritesPerCommit 单次提交包含的文件组数量
   * @return 异步提交服务 {@link CommitService}
   */
  public CommitService service(int rewritesPerCommit) {
    return new CommitService(rewritesPerCommit);
  }

  /**
   * 数据文件重写的异步提交服务，继承 {@link BaseCommitService}，将提交与清理委托给 外层 {@link RewriteDataFilesCommitManager}。
   */
  public class CommitService extends BaseCommitService<RewriteFileGroup> {

    CommitService(int rewritesPerCommit) {
      super(table, rewritesPerCommit);
    }

    @Override
    protected void commitOrClean(Set<RewriteFileGroup> batch) {
      RewriteDataFilesCommitManager.this.commitOrClean(batch);
    }

    @Override
    protected void abortFileGroup(RewriteFileGroup group) {
      RewriteDataFilesCommitManager.this.abortFileGroup(group);
    }
  }
}
