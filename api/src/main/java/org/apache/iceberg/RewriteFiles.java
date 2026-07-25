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

import java.util.Set;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;

/**
 * 文件级说明：文件重写（替换）API 接口。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>累积文件的添加与删除操作，生成新的 {@link Snapshot} 并提交为当前快照。
 *   <li>支持数据文件与删除文件的重写（如 compaction、小文件合并等）。
 *   <li>支持指定数据序列号、校验快照等高级配置。
 * </ul>
 *
 * <p>设计意图：重写操作必须保证表状态逻辑等价（rewrite 前后数据不变，仅改变文件的物理布局/大小）。 提交时变更会应用到最新表快照；若发生冲突则重试。重试时若待删除文件已不在最新快照中，
 * 将抛出 {@link ValidationException}。通过 {@link #dataSequenceNumber(long)} 可为重写的数据文件 指定序列号，避免数据
 * compaction 与 equality delete 之间的提交冲突。
 *
 * <p>上下游关系：由 {@link Table#newRewrite()} 创建；被 compaction、维护作业等使用。
 */
public interface RewriteFiles extends SnapshotUpdate<RewriteFiles> {
  /**
   * 从当前表状态中移除一个数据文件。
   *
   * <p>重写操作可能改变数据文件的大小或布局。适用时，建议在重写数据文件时一并丢弃已被删除的记录。 但存活数据记录的集合绝不能改变。
   *
   * @param dataFile 待移除的（被重写的）数据文件
   * @return this，便于链式调用
   */
  default RewriteFiles deleteFile(DataFile dataFile) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement deleteFile");
  }

  /**
   * 从表状态中移除一个删除文件。
   *
   * <p>重写操作可能改变删除文件的大小或布局。适用时，建议丢弃不再属于表状态的文件对应的删除记录。 但适用的删除记录集合绝不能改变。
   *
   * @param deleteFile 待移除的（被重写的）删除文件
   * @return this，便于链式调用
   */
  default RewriteFiles deleteFile(DeleteFile deleteFile) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement deleteFile");
  }

  /**
   * 添加一个新数据文件。
   *
   * <p>重写操作可能改变数据文件的大小或布局。适用时，建议在重写时一并丢弃已被删除的记录。 但存活数据记录的集合绝不能改变。
   *
   * @param dataFile 新数据文件
   * @return this，便于链式调用
   */
  default RewriteFiles addFile(DataFile dataFile) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement addFile");
  }

  /**
   * 添加一个新删除文件。
   *
   * <p>重写操作可能改变删除文件的大小或布局。适用时，建议丢弃不再属于表状态的文件对应的删除记录。 但适用的删除记录集合绝不能改变。
   *
   * @param deleteFile 新删除文件
   * @return this，便于链式调用
   */
  default RewriteFiles addFile(DeleteFile deleteFile) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement addFile");
  }

  /**
   * 添加一个带有指定数据序列号的新删除文件。
   *
   * <p>重写操作可能改变删除文件的大小或布局。适用时，建议丢弃不再属于表状态的文件对应的删除记录。 但适用的删除记录集合绝不能改变。
   *
   * <p>为保证适用的删除记录集合等价，新删除文件的序列号必须等于其替换的删除文件中的最大序列号。 不允许重写属于不同序列号的 equality delete。
   *
   * @param deleteFile 新删除文件
   * @param dataSequenceNumber 附加到该文件的数据序列号
   * @return this，便于链式调用
   */
  default RewriteFiles addFile(DeleteFile deleteFile, long dataSequenceNumber) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement addFile");
  }

  /**
   * 为本次重写操作配置数据序列号。该序列号将用于本次重写中所有新增的数据文件。
   *
   * <p>此方法有助于避免数据 compaction 与新增 equality delete 之间的提交冲突。
   *
   * @param sequenceNumber 数据序列号
   * @return this，便于链式调用
   */
  default RewriteFiles dataSequenceNumber(long sequenceNumber) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement dataSequenceNumber");
  }

  /**
   * 添加一个重写操作：用一组包含相同数据的数据文件替换另一组数据文件。
   *
   * @param filesToDelete 待替换（删除）的文件，不能为 null 或空
   * @param filesToAdd 待添加的文件，不能为 null 或空
   * @return this，便于链式调用
   * @deprecated 自 1.3.0 起，将在 2.0.0 移除
   */
  @Deprecated
  default RewriteFiles rewriteFiles(Set<DataFile> filesToDelete, Set<DataFile> filesToAdd) {
    return rewriteFiles(filesToDelete, ImmutableSet.of(), filesToAdd, ImmutableSet.of());
  }

  /**
   * 添加一个重写操作：用一组数据文件替换另一组包含相同数据的数据文件，并为所有新增数据文件 使用指定的序列号。
   *
   * @param filesToDelete 待替换（删除）的文件，不能为 null 或空
   * @param filesToAdd 待添加的文件，不能为 null 或空
   * @param sequenceNumber 用于所有新增数据文件的序列号
   * @return this，便于链式调用
   * @deprecated 自 1.3.0 起，将在 2.0.0 移除
   */
  @Deprecated
  RewriteFiles rewriteFiles(
      Set<DataFile> filesToDelete, Set<DataFile> filesToAdd, long sequenceNumber);

  /**
   * 添加一个重写操作：用一组文件替换另一组包含相同数据的文件（含数据文件与删除文件）。
   *
   * @param dataFilesToReplace 待替换（删除）的数据文件
   * @param deleteFilesToReplace 待替换（删除）的删除文件
   * @param dataFilesToAdd 待添加的数据文件
   * @param deleteFilesToAdd 待添加的删除文件
   * @return this，便于链式调用
   * @deprecated 自 1.3.0 起，将在 2.0.0 移除
   */
  @Deprecated
  RewriteFiles rewriteFiles(
      Set<DataFile> dataFilesToReplace,
      Set<DeleteFile> deleteFilesToReplace,
      Set<DataFile> dataFilesToAdd,
      Set<DeleteFile> deleteFilesToAdd);

  /**
   * 设置本操作中任何读取所使用的快照 ID。
   *
   * <p>校验将检查该快照 ID 之后的变更。若不调用此方法，则校验从表初始快照到当前的所有祖先快照。
   *
   * @param snapshotId 快照 ID
   * @return this，便于链式调用
   */
  RewriteFiles validateFromSnapshot(long snapshotId);
}
