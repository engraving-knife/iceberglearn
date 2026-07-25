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
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.RewriteFiles;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 位置删除文件重写动作的提交管理器。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将若干 {@link RewritePositionDeletesGroup} 的重写结果合并为一次 {@link RewriteFiles} 提交：
 *       删除旧的位置删除文件、添加新的位置删除文件。
 *   <li>提供提交失败后的文件清理（abort）能力，以及提交或清理的组合入口 {@link #commitOrClean(Set)}。
 *   <li>提供异步批量提交服务 {@link CommitService}（基于 {@link BaseCommitService}）。
 * </ul>
 *
 * <p>设计意图：与 {@link RewriteDataFilesCommitManager} 类似，把"如何提交位置删除重写结果"与
 * "如何执行重写"解耦，供不同平台复用。新删除文件以组的最大重写数据序列号写入，保证删除文件 仅对序列号不超过该值的快照可见，维持正确性。
 *
 * <p>上下游关系：被各平台的 {@link RewritePositionDeleteFiles} 动作调用；内部使用 {@link Table#newRewrite()} 生成提交操作。
 */
public class RewritePositionDeletesCommitManager {
  private static final Logger LOG =
      LoggerFactory.getLogger(RewritePositionDeletesCommitManager.class);

  private final Table table;
  private final long startingSnapshotId;

  /**
   * 构造提交管理器，以表当前快照作为起始快照。
   *
   * @param table 目标表
   */
  public RewritePositionDeletesCommitManager(Table table) {
    this.table = table;
    this.startingSnapshotId = table.currentSnapshot().snapshotId();
  }

  /**
   * 将一组文件组的重写结果合并为一次提交：删除旧的位置删除文件、添加新写入的位置删除文件。
   *
   * <p>逻辑：以 {@code startingSnapshotId} 校验创建 {@link RewriteFiles}；遍历每个组，将其 rewrittenDeleteFiles
   * 标记为删除、addedDeleteFiles 以组的最大重写数据序列号添加；最后提交。
   *
   * @param fileGroups 待提交的文件组集合
   */
  public void commit(Set<RewritePositionDeletesGroup> fileGroups) {
    RewriteFiles rewriteFiles = table.newRewrite().validateFromSnapshot(startingSnapshotId);

    for (RewritePositionDeletesGroup group : fileGroups) {
      for (DeleteFile file : group.rewrittenDeleteFiles()) {
        rewriteFiles.deleteFile(file);
      }

      for (DeleteFile file : group.addedDeleteFiles()) {
        rewriteFiles.addFile(file, group.maxRewrittenDataSequenceNumber());
      }
    }

    rewriteFiles.commit();
  }

  /**
   * 清理指定文件组产生的新位置删除文件，不应抛出异常。
   *
   * <p>逻辑：将 addedDeleteFiles 路径收集后，委托 {@link CatalogUtil#deleteFiles} 批量删除。
   *
   * @param fileGroup 已重写的文件组
   */
  public void abort(RewritePositionDeletesGroup fileGroup) {
    Preconditions.checkState(
        fileGroup.addedDeleteFiles() != null, "Cannot abort a fileGroup that was not rewritten");

    Iterable<String> filePaths =
        Iterables.transform(fileGroup.addedDeleteFiles(), f -> f.path().toString());
    CatalogUtil.deleteFiles(table.io(), filePaths, "position delete", true);
  }

  /**
   * 提交一组文件组，失败时清理已写入的新文件。
   *
   * <p>逻辑：尝试提交；遇到 {@link CommitStateUnknownException}（提交状态未知，可能已成功）时不清理 直接抛出；遇到其他异常则清理所有组的新文件后重新抛出。
   *
   * @param rewriteGroups 待提交的文件组集合
   */
  public void commitOrClean(Set<RewritePositionDeletesGroup> rewriteGroups) {
    try {
      commit(rewriteGroups);
    } catch (CommitStateUnknownException e) {
      LOG.error(
          "Commit state unknown for {}, cannot clean up files because they may have been committed successfully.",
          rewriteGroups,
          e);
      throw e;
    } catch (Exception e) {
      LOG.error("Cannot commit groups {}, attempting to clean up written files", rewriteGroups, e);
      rewriteGroups.forEach(this::abort);
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
   * 位置删除文件重写的异步提交服务，继承 {@link BaseCommitService}，将提交与清理委托给 外层 {@link
   * RewritePositionDeletesCommitManager}。
   */
  public class CommitService extends BaseCommitService<RewritePositionDeletesGroup> {

    CommitService(int rewritesPerCommit) {
      super(table, rewritesPerCommit);
    }

    @Override
    protected void commitOrClean(Set<RewritePositionDeletesGroup> batch) {
      RewritePositionDeletesCommitManager.this.commitOrClean(batch);
    }

    @Override
    protected void abortFileGroup(RewritePositionDeletesGroup group) {
      RewritePositionDeletesCommitManager.this.abort(group);
    }
  }
}
