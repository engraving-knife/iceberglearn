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
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.RewriteJobOrder;
import org.apache.iceberg.actions.RewriteDataFiles.FileGroupInfo;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件重写分组：表示一组待重写的文件扫描任务及其重写后产生的新文件。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link FileGroupInfo} 与原始 {@link FileScanTask} 列表。
 *   <li>在重写完成后通过 {@link #setOutputFiles(Set)} 记录新写入的文件。
 *   <li>提供 {@link #rewrittenFiles()}、{@link #addedFiles()} 供提交管理器组装提交操作。
 *   <li>提供 {@link #asResult()} 生成单组重写结果统计。
 * </ul>
 *
 * <p>设计意图：把原始文件与产出文件绑定在一个分组中，便于按组提交、清理与统计； 同时支持按 {@link RewriteJobOrder} 对分组排序以控制重写执行顺序。
 *
 * <p>上下游关系：由 {@link RewriteStrategy#planFileGroups(Iterable)} 规划产生，被 {@link
 * RewriteDataFilesCommitManager} 提交，由具体引擎的重写动作填充输出文件。
 */
public class RewriteFileGroup {
  /** 本分组的元信息。 */
  private final FileGroupInfo info;
  /** 本分组待重写的文件扫描任务列表。 */
  private final List<FileScanTask> fileScanTasks;

  /** 重写后新增的数据文件集合，重写完成前为空集。 */
  private Set<DataFile> addedFiles = Collections.emptySet();

  /**
   * 构造文件重写分组。
   *
   * @param info 分组元信息
   * @param fileScanTasks 待重写的文件扫描任务列表
   */
  public RewriteFileGroup(FileGroupInfo info, List<FileScanTask> fileScanTasks) {
    this.info = info;
    this.fileScanTasks = fileScanTasks;
  }

  /** 返回本分组的元信息。 */
  public FileGroupInfo info() {
    return info;
  }

  /** 返回本分组待重写的文件扫描任务列表。 */
  public List<FileScanTask> fileScans() {
    return fileScanTasks;
  }

  /**
   * 设置重写后产出的新文件集合。
   *
   * @param files 新写入的数据文件集合
   */
  public void setOutputFiles(Set<DataFile> files) {
    addedFiles = files;
  }

  /** 返回本分组中被重写的原始数据文件集合（取自文件扫描任务）。 */
  public Set<DataFile> rewrittenFiles() {
    return fileScans().stream().map(FileScanTask::file).collect(Collectors.toSet());
  }

  /** 返回重写后新增的数据文件集合。 */
  public Set<DataFile> addedFiles() {
    return addedFiles;
  }

  /**
   * 生成本分组的重写结果统计。
   *
   * <p>逻辑：校验已设置输出文件后，用 {@code ImmutableRewriteDataFiles.FileGroupRewriteResult}
   * 构建器填充分组信息、新增文件数、重写文件数与重写字节数。
   *
   * @return 本分组的重写结果
   * @throws IllegalStateException 若分组尚未重写（未设置输出文件）
   */
  public RewriteDataFiles.FileGroupRewriteResult asResult() {
    Preconditions.checkState(addedFiles != null, "Cannot get result, Group was never rewritten");
    return ImmutableRewriteDataFiles.FileGroupRewriteResult.builder()
        .info(info)
        .addedDataFilesCount(addedFiles.size())
        .rewrittenDataFilesCount(fileScanTasks.size())
        .rewrittenBytesCount(sizeInBytes())
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("info", info)
        .add("numRewrittenFiles", fileScanTasks.size())
        .add(
            "numAddedFiles",
            addedFiles == null ? "Rewrite Incomplete" : Integer.toString(addedFiles.size()))
        .add("numRewrittenBytes", sizeInBytes())
        .toString();
  }

  /** 返回本分组所有待重写文件的总长度（字节）。 */
  public long sizeInBytes() {
    return fileScanTasks.stream().mapToLong(FileScanTask::length).sum();
  }

  /** 返回本分组待重写文件的数量。 */
  public int numFiles() {
    return fileScanTasks.size();
  }

  /**
   * 根据 {@link RewriteJobOrder} 返回分组比较器，用于控制重写任务的执行顺序。
   *
   * <p>逻辑：按字节或文件数升序/降序返回对应比较器；未知顺序返回恒等比较器（保持原序）。
   *
   * @param rewriteJobOrder 重写任务排序方式
   * @return 对应的分组比较器
   */
  public static Comparator<RewriteFileGroup> comparator(RewriteJobOrder rewriteJobOrder) {
    switch (rewriteJobOrder) {
      case BYTES_ASC:
        return Comparator.comparing(RewriteFileGroup::sizeInBytes);
      case BYTES_DESC:
        return Comparator.comparing(RewriteFileGroup::sizeInBytes, Comparator.reverseOrder());
      case FILES_ASC:
        return Comparator.comparing(RewriteFileGroup::numFiles);
      case FILES_DESC:
        return Comparator.comparing(RewriteFileGroup::numFiles, Comparator.reverseOrder());
      default:
        return (fileGroupOne, fileGroupTwo) -> 0;
    }
  }
}
