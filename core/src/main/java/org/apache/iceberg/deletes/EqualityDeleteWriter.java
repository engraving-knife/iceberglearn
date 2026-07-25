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
package org.apache.iceberg.deletes;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileMetadata;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.io.DeleteWriteResult;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.FileWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 相等删除文件写入器：将相等删除（按字段值匹配删除）写入删除文件。
 *
 * <p>所属模块：iceberg-core，deletes 包内删除文件写入的实现之一。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>委托底层 {@link FileAppender} 写入相等删除记录。
 *   <li>在 close 时产出 {@link DeleteFile} 元数据（含格式、路径、分区、加密元数据、指标、 排序序与相等字段 ID）。
 * </ul>
 *
 * <p>设计意图：相等删除按指定字段值匹配要删除的行，写入时通常需要对删除键排序以利于读取侧合并。 本类作为 FileWriter 适配器，将 FileAppender 的写入能力封装为统一的
 * DeleteWriteResult 产出， 与 {@link PositionDeleteWriter} 共同构成删除写入的两种实现。close 内通过 deleteFile==null
 * 保证幂等，避免重复关闭产生不一致元数据。
 *
 * <p>上下游关系：依赖 {@link FileAppender}（列式追加器）、{@link FileMetadata}（删除文件元数据构建）； 被各任务写入流程调用，产出 {@link
 * DeleteWriteResult} 提交给提交链路。
 *
 * @param <T> 删除记录的行类型
 */
public class EqualityDeleteWriter<T> implements FileWriter<T, DeleteWriteResult> {
  private final FileAppender<T> appender;
  private final FileFormat format;
  private final String location;
  private final PartitionSpec spec;
  private final StructLike partition;
  private final ByteBuffer keyMetadata;
  private final int[] equalityFieldIds;
  private final SortOrder sortOrder;
  private DeleteFile deleteFile = null;

  /**
   * 构造相等删除写入器。
   *
   * @param appender 底层列式追加器
   * @param format 文件格式（如 Parquet/ORC/Avro）
   * @param location 删除文件输出路径
   * @param spec 分区规格
   * @param partition 分区值
   * @param keyMetadata 加密密钥元数据，可为 null
   * @param sortOrder 删除记录的排序序，用于读取侧优化合并
   * @param equalityFieldIds 相等删除所依据的字段 ID 列表
   */
  public EqualityDeleteWriter(
      FileAppender<T> appender,
      FileFormat format,
      String location,
      PartitionSpec spec,
      StructLike partition,
      EncryptionKeyMetadata keyMetadata,
      SortOrder sortOrder,
      int... equalityFieldIds) {
    this.appender = appender;
    this.format = format;
    this.location = location;
    this.spec = spec;
    this.partition = partition;
    this.keyMetadata = keyMetadata != null ? keyMetadata.buffer() : null;
    this.sortOrder = sortOrder;
    this.equalityFieldIds = equalityFieldIds;
  }

  /**
   * 写入一条相等删除记录。
   *
   * @param row 待写入的删除记录
   */
  @Override
  public void write(T row) {
    appender.add(row);
  }

  /** 返回当前已写入字节数。 */
  @Override
  public long length() {
    return appender.length();
  }

  /**
   * 关闭写入器并产出删除文件元数据。
   *
   * <p>逻辑：若尚未产出 deleteFile，则先关闭底层 appender，再通过 {@link FileMetadata#deleteFileBuilder}
   * 构建相等删除文件元数据，包含格式、路径、分区、 加密元数据、文件大小、指标、split 偏移与排序序。
   *
   * <p>设计要点：通过 deleteFile==null 判定保证幂等，多次调用 close 不会重复构建。
   *
   * @throws IOException 关闭 appender 时发生 IO 异常
   */
  @Override
  public void close() throws IOException {
    if (deleteFile == null) {
      appender.close();
      this.deleteFile =
          FileMetadata.deleteFileBuilder(spec)
              .ofEqualityDeletes(equalityFieldIds)
              .withFormat(format)
              .withPath(location)
              .withPartition(partition)
              .withEncryptionKeyMetadata(keyMetadata)
              .withFileSizeInBytes(appender.length())
              .withMetrics(appender.metrics())
              .withSplitOffsets(appender.splitOffsets())
              .withSortOrder(sortOrder)
              .build();
    }
  }

  /**
   * 返回已产出的删除文件元数据。
   *
   * @return 删除文件元数据
   * @throws IllegalStateException 若写入器尚未关闭（deleteFile 为 null）
   */
  public DeleteFile toDeleteFile() {
    Preconditions.checkState(deleteFile != null, "Cannot create delete file from unclosed writer");
    return deleteFile;
  }

  /** 返回写入结果，包含删除文件元数据。 */
  @Override
  public DeleteWriteResult result() {
    return new DeleteWriteResult(toDeleteFile());
  }
}
