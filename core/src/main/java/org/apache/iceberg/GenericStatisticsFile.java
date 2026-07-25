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
import java.util.Objects;
import java.util.StringJoiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 文件级说明：{@link StatisticsFile} 的通用实现类，描述一个统计文件的完整元信息。
 *
 * <p>所属模块：iceberg-core。职责：实现 {@link StatisticsFile} 接口，持有快照 ID、 文件路径、文件大小、footer 大小和其中各 Blob
 * 的元信息列表。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>作为 StatisticsFile 接口的"值对象"实现，不可变。
 *   <li>持有 {@link BlobMetadata} 列表，形成"统计文件 → 多个 Blob"的层级。
 * </ul>
 *
 * <p>上下游关系：被表元数据（TableMetadata）持有；由统计文件写入器创建。
 */
public class GenericStatisticsFile implements StatisticsFile {
  private final long snapshotId;
  private final String path;
  private final long fileSizeInBytes;
  private final long fileFooterSizeInBytes;
  private final List<BlobMetadata> blobMetadata;

  /**
   * 构造统计文件元信息实例。
   *
   * @param snapshotId 关联的快照 ID
   * @param path 统计文件路径
   * @param fileSizeInBytes 文件总大小（字节）
   * @param fileFooterSizeInBytes footer 大小（字节）
   * @param blobMetadata 文件内各 Blob 的元信息列表
   */
  public GenericStatisticsFile(
      long snapshotId,
      String path,
      long fileSizeInBytes,
      long fileFooterSizeInBytes,
      List<BlobMetadata> blobMetadata) {
    Preconditions.checkNotNull(path, "path is null");
    Preconditions.checkNotNull(blobMetadata, "blobMetadata is null");
    this.snapshotId = snapshotId;
    this.path = path;
    this.fileSizeInBytes = fileSizeInBytes;
    this.fileFooterSizeInBytes = fileFooterSizeInBytes;
    this.blobMetadata = ImmutableList.copyOf(blobMetadata);
  }

  @Override
  /**
   * 返回关联的快照 ID。
   *
   * @return 快照 ID
   */
  public long snapshotId() {
    return snapshotId;
  }

  @Override
  /**
   * 返回统计文件路径。
   *
   * @return 文件路径字符串
   */
  public String path() {
    return path;
  }

  @Override
  /**
   * 返回文件总大小。
   *
   * @return 文件大小（字节）
   */
  public long fileSizeInBytes() {
    return fileSizeInBytes;
  }

  @Override
  /**
   * 返回文件 footer 大小。
   *
   * @return footer 大小（字节）
   */
  public long fileFooterSizeInBytes() {
    return fileFooterSizeInBytes;
  }

  @Override
  /**
   * 返回文件内各 Blob 的元信息列表。
   *
   * @return Blob 元信息列表
   */
  public List<BlobMetadata> blobMetadata() {
    return blobMetadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenericStatisticsFile that = (GenericStatisticsFile) o;
    return snapshotId == that.snapshotId
        && fileSizeInBytes == that.fileSizeInBytes
        && fileFooterSizeInBytes == that.fileFooterSizeInBytes
        && Objects.equals(path, that.path)
        && Objects.equals(blobMetadata, that.blobMetadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(snapshotId, path, fileSizeInBytes, fileFooterSizeInBytes, blobMetadata);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", GenericStatisticsFile.class.getSimpleName() + "[", "]")
        .add("snapshotId=" + snapshotId)
        .add("path='" + path + "'")
        .add("fileSizeInBytes=" + fileSizeInBytes)
        .add("fileFooterSizeInBytes=" + fileFooterSizeInBytes)
        .add("blobMetadata=" + blobMetadata)
        .toString();
  }
}
