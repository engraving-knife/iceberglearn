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
package org.apache.iceberg.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：数据文件写入器。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：包装一个 {@link FileAppender}，向单个 spec/partition 写入数据行，并在关闭时 构造包含完整 Iceberg
 * 元数据（格式、路径、分区、加密密钥、文件大小、指标、split 偏移、排序序号） 的 {@link DataFile}。
 *
 * <p>设计意图：FileAppender 只负责追加记录和收集文件级指标，不感知 Iceberg 元数据。DataWriter 在其之上补充分区、加密、排序等上下文，使得 close 后产出的
 * DataFile 可直接提交到表元数据。 dataFile 字段延迟到 close 时才赋值，避免半成品状态。
 *
 * <p>上下游关系：由 {@link FileWriterFactory} / {@link FileAppenderFactory} 创建； 被 {@link
 * RollingFileWriter}（经 {@link RollingDataWriter}）和 {@link BaseTaskWriter} 使用。
 *
 * @param <T> 行记录类型
 */
public class DataWriter<T> implements FileWriter<T, DataWriteResult> {
  private final FileAppender<T> appender;
  private final FileFormat format;
  private final String location;
  private final PartitionSpec spec;
  private final StructLike partition;
  private final ByteBuffer keyMetadata;
  private final SortOrder sortOrder;
  private DataFile dataFile = null;

  /**
   * 构造数据写入器（不含排序序号）。
   *
   * @param appender 底层文件追加器
   * @param format 文件格式
   * @param location 文件路径
   * @param spec 分区规格
   * @param partition 分区值
   * @param keyMetadata 加密密钥元数据
   */
  public DataWriter(
      FileAppender<T> appender,
      FileFormat format,
      String location,
      PartitionSpec spec,
      StructLike partition,
      EncryptionKeyMetadata keyMetadata) {
    this(appender, format, location, spec, partition, keyMetadata, null);
  }

  /**
   * 构造数据写入器（含排序序号）。
   *
   * @param appender 底层文件追加器
   * @param format 文件格式
   * @param location 文件路径
   * @param spec 分区规格
   * @param partition 分区值
   * @param keyMetadata 加密密钥元数据
   * @param sortOrder 排序序号
   */
  public DataWriter(
      FileAppender<T> appender,
      FileFormat format,
      String location,
      PartitionSpec spec,
      StructLike partition,
      EncryptionKeyMetadata keyMetadata,
      SortOrder sortOrder) {
    this.appender = appender;
    this.format = format;
    this.location = location;
    this.spec = spec;
    this.partition = partition;
    this.keyMetadata = keyMetadata != null ? keyMetadata.buffer() : null;
    this.sortOrder = sortOrder;
  }

  /** 追加一行数据到底层 appender。 */
  @Override
  public void write(T row) {
    appender.add(row);
  }

  /** 返回已写入字节数。 */
  @Override
  public long length() {
    return appender.length();
  }

  /**
   * 关闭写入器并构造 {@link DataFile}。
   *
   * <p>逻辑：先关闭底层 appender（触发指标收集和文件刷盘），再用收集到的格式、路径、分区、 加密密钥、文件大小、metrics、split 偏移和排序序号构建
   * DataFile。仅首次调用有效（幂等）。
   *
   * @throws IOException 关闭 appender 时发生 IO 错误
   */
  @Override
  public void close() throws IOException {
    if (dataFile == null) {
      appender.close();
      this.dataFile =
          DataFiles.builder(spec)
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
   * 返回已构造的 DataFile，必须在 close 之后调用。
   *
   * @return 数据文件元数据对象
   * @throws IllegalStateException 若写入器尚未关闭
   */
  public DataFile toDataFile() {
    Preconditions.checkState(dataFile != null, "Cannot create data file from unclosed writer");
    return dataFile;
  }

  /** 返回写入结果。 */
  @Override
  public DataWriteResult result() {
    return new DataWriteResult(toDataFile());
  }
}
