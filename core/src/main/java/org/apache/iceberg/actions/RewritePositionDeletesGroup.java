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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.PositionDeletesScanTask;
import org.apache.iceberg.RewriteJobOrder;
import org.apache.iceberg.actions.RewritePositionDeleteFiles.FileGroupInfo;
import org.apache.iceberg.actions.RewritePositionDeleteFiles.FileGroupRewriteResult;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 位置删除文件重写分组：表示一组待重写的位置删除文件扫描任务及其重写后产生的新文件。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link FileGroupInfo}、原始 {@link PositionDeletesScanTask} 列表与最大重写数据序列号。
 *   <li>在重写完成后通过 {@link #setOutputFiles(Set)} 记录新写入的删除文件。
 *   <li>提供 {@link #rewrittenDeleteFiles()}、{@link #addedDeleteFiles()} 供提交管理器组装提交操作。
 *   <li>提供 {@link #asResult()} 生成单组重写结果统计。
 * </ul>
 *
 * <p>设计意图：把原始删除文件与产出删除文件绑定在一个分组中，便于按组提交、清理与统计； 最大重写数据序列号用于保证新删除文件仅对序列号不超过该值的快照可见，维持正确性； 同时支持按
 * {@link RewriteJobOrder} 排序以控制重写执行顺序。
 *
 * <p>上下游关系：由 {@link FileRewriter#planFileGroups(Iterable)} 规划产生，被 {@link
 * RewritePositionDeletesCommitManager} 提交，由具体引擎的重写动作填充输出文件。
 */
public class RewritePositionDeletesGroup {
  private final FileGroupInfo info;
  private final List<PositionDeletesScanTask> tasks;
  private final long maxRewrittenDataSequenceNumber;

  private Set<DeleteFile> addedDeleteFiles = Collections.emptySet();

  /**
   * 构造位置删除文件重写分组。
   *
   * <p>逻辑：校验任务非空后保存信息与任务，并计算所有任务文件数据序列号的最大值作为 最大重写数据序列号。
   *
   * @param info 分组元信息
   * @param tasks 待重写的位置删除文件扫描任务列表
   */
  public RewritePositionDeletesGroup(FileGroupInfo info, List<PositionDeletesScanTask> tasks) {
    Preconditions.checkArgument(tasks.size() > 0, "Tasks must not be empty");
    this.info = info;
    this.tasks = tasks;
    this.maxRewrittenDataSequenceNumber =
        tasks.stream().mapToLong(t -> t.file().dataSequenceNumber()).max().getAsLong();
  }

  /** 返回本分组的元信息。 */
  public FileGroupInfo info() {
    return info;
  }

  /** 返回本分组待重写的位置删除文件扫描任务列表。 */
  public List<PositionDeletesScanTask> tasks() {
    return tasks;
  }

  /**
   * 设置重写后产出的新删除文件集合。
   *
   * @param files 新写入的删除文件集合
   */
  public void setOutputFiles(Set<DeleteFile> files) {
    addedDeleteFiles = files;
  }

  /** 返回本分组的最大重写数据序列号，用于新删除文件的可见性约束。 */
  public long maxRewrittenDataSequenceNumber() {
    return maxRewrittenDataSequenceNumber;
  }

  /** 返回本分组中被重写的原始位置删除文件集合。 */
  public Set<DeleteFile> rewrittenDeleteFiles() {
    return tasks().stream().map(PositionDeletesScanTask::file).collect(Collectors.toSet());
  }

  /** 返回重写后新增的位置删除文件集合。 */
  public Set<DeleteFile> addedDeleteFiles() {
    return addedDeleteFiles;
  }

  /**
   * 生成本分组的重写结果统计。
   *
   * <p>逻辑：校验已设置输出文件后，用 {@code ImmutableRewritePositionDeleteFiles.FileGroupRewriteResult}
   * 构建器填充分组信息、新增/重写删除文件数与字节数。
   *
   * @return 本分组的重写结果
   * @throws IllegalStateException 若分组尚未重写
   */
  public FileGroupRewriteResult asResult() {
    Preconditions.checkState(
        addedDeleteFiles != null, "Cannot get result, Group was never rewritten");

    return ImmutableRewritePositionDeleteFiles.FileGroupRewriteResult.builder()
        .info(info)
        .addedDeleteFilesCount(addedDeleteFiles.size())
        .rewrittenDeleteFilesCount(tasks.size())
        .rewrittenBytesCount(rewrittenBytes())
        .addedBytesCount(addedBytes())
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("info", info)
        .add("numRewrittenPositionDeleteFiles", tasks.size())
        .add(
            "numAddedPositionDeleteFiles",
            addedDeleteFiles == null
                ? "Rewrite Incomplete"
                : Integer.toString(addedDeleteFiles.size()))
        .add("numAddedBytes", addedBytes())
        .add("numRewrittenBytes", rewrittenBytes())
        .toString();
  }

  /** 返回本分组所有待重写位置删除文件的总长度（字节）。 */
  public long rewrittenBytes() {
    return tasks.stream().mapToLong(PositionDeletesScanTask::length).sum();
  }

  /** 返回本分组新增位置删除文件的总大小（字节）。 */
  public long addedBytes() {
    return addedDeleteFiles.stream().mapToLong(DeleteFile::fileSizeInBytes).sum();
  }

  /** 返回本分组待重写位置删除文件的数量。 */
  public int numRewrittenDeleteFiles() {
    return tasks.size();
  }

  /**
   * 根据 {@link RewriteJobOrder} 返回分组比较器，用于控制重写任务的执行顺序。
   *
   * <p>逻辑：按字节或文件数升序/降序返回对应比较器；未知顺序返回恒等比较器（保持原序）。
   *
   * @param order 重写任务排序方式
   * @return 对应的分组比较器
   */
  public static Comparator<RewritePositionDeletesGroup> comparator(RewriteJobOrder order) {
    switch (order) {
      case BYTES_ASC:
        return Comparator.comparing(RewritePositionDeletesGroup::rewrittenBytes);
      case BYTES_DESC:
        return Comparator.comparing(
            RewritePositionDeletesGroup::rewrittenBytes, Comparator.reverseOrder());
      case FILES_ASC:
        return Comparator.comparing(RewritePositionDeletesGroup::numRewrittenDeleteFiles);
      case FILES_DESC:
        return Comparator.comparing(
            RewritePositionDeletesGroup::numRewrittenDeleteFiles, Comparator.reverseOrder());
      default:
        return (fileGroupOne, fileGroupTwo) -> 0;
    }
  }
}
