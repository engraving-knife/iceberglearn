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
import java.io.UncheckedIOException;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：单 spec/partition 内的滚动写入器抽象基类。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：在同一个 spec/partition 内，当当前文件达到目标大小时自动关闭并打开新文件， 将输入记录拆分为多个文件，避免单个文件过大。
 *
 * <p>设计意图：Iceberg 推荐数据文件大小在 256MB~1GB 之间，过大或过小都会影响读取性能。 RollingFileWriter 通过周期性检查（每 1000
 * 行检查一次文件大小）实现滚动写入，兼顾检查频率与 及时性。空文件（0 行）会在关闭时被删除以避免产生垃圾文件。子类通过实现 newWriter / addResult /
 * aggregatedResult 来适配 data / equality-delete / position-delete 三种场景。
 *
 * <p>上下游关系：由 {@link RollingDataWriter}、{@link RollingEqualityDeleteWriter}、 {@link
 * RollingPositionDeleteWriter} 继承；被 {@link ClusteredWriter}、{@link FanoutWriter} 作为单分区写入单元使用。
 *
 * @param <T> 行记录类型
 * @param <W> 底层 FileWriter 类型
 * @param <R> 结果类型
 */
abstract class RollingFileWriter<T, W extends FileWriter<T, R>, R> implements FileWriter<T, R> {
  private static final int ROWS_DIVISOR = 1000;

  private final OutputFileFactory fileFactory;
  private final FileIO io;
  private final long targetFileSizeInBytes;
  private final PartitionSpec spec;
  private final StructLike partition;

  private EncryptedOutputFile currentFile = null;
  private long currentFileRows = 0;
  private W currentWriter = null;

  private boolean closed = false;

  /**
   * 构造滚动写入器。
   *
   * @param fileFactory 输出文件工厂，用于生成新文件
   * @param io FileIO 实例，用于删除空文件
   * @param targetFileSizeInBytes 目标文件大小（字节），达到此大小后滚动
   * @param spec 分区规格
   * @param partition 分区值
   */
  protected RollingFileWriter(
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSizeInBytes,
      PartitionSpec spec,
      StructLike partition) {
    this.fileFactory = fileFactory;
    this.io = io;
    this.targetFileSizeInBytes = targetFileSizeInBytes;
    this.spec = spec;
    this.partition = partition;
  }

  /** 创建底层 FileWriter，由子类实现以适配 data / delete 场景。 */
  protected abstract W newWriter(EncryptedOutputFile file);

  /** 将单个文件的结果加入聚合列表，由子类实现。 */
  protected abstract void addResult(R result);

  /** 返回所有文件的聚合结果，由子类实现。 */
  protected abstract R aggregatedResult();

  /** 返回分区规格。 */
  protected PartitionSpec spec() {
    return spec;
  }

  /** 返回分区值。 */
  protected StructLike partition() {
    return partition;
  }

  /** 返回当前正在写入的文件路径。 */
  public CharSequence currentFilePath() {
    return currentFile.encryptingOutputFile().location();
  }

  /** 返回当前文件已写入的行数。 */
  public long currentFileRows() {
    return currentFileRows;
  }

  /** 滚动写入器不直接支持 length 查询。 */
  @Override
  public long length() {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement length");
  }

  /**
   * 写入一行记录，并在达到滚动条件时切换到新文件。
   *
   * <p>逻辑：委托给当前 writer 写入；行计数加一；每 1000 行检查一次当前文件大小是否达到目标值， 若达到则关闭当前 writer 并打开新文件。
   *
   * @param row 待写入的行记录
   */
  @Override
  public void write(T row) {
    currentWriter.write(row);
    currentFileRows++;

    if (shouldRollToNewFile()) {
      closeCurrentWriter();
      openCurrentWriter();
    }
  }

  /** 判断是否应滚动到新文件：行数为 1000 的倍数且当前文件大小已达到目标值。 */
  private boolean shouldRollToNewFile() {
    return currentFileRows % ROWS_DIVISOR == 0 && currentWriter.length() >= targetFileSizeInBytes;
  }

  /**
   * 打开新的输出文件和 writer。
   *
   * <p>逻辑：通过 fileFactory 创建新的 EncryptedOutputFile（根据分区有无选择分区或非分区路径）， 重置行计数，调用 newWriter 创建底层
   * writer。
   */
  protected void openCurrentWriter() {
    Preconditions.checkState(currentWriter == null, "Current writer has been already initialized");

    this.currentFile = newFile();
    this.currentFileRows = 0;
    this.currentWriter = newWriter(currentFile);
  }

  private EncryptedOutputFile newFile() {
    if (spec.isUnpartitioned() || partition == null) {
      return fileFactory.newOutputFile();
    } else {
      return fileFactory.newOutputFile(spec, partition);
    }
  }

  /**
   * 关闭当前 writer 并处理结果。
   *
   * <p>逻辑：关闭 writer；若当前文件为空（0 行）则删除该文件避免产生垃圾；否则将 writer 的结果 加入聚合列表。最后重置状态字段。
   */
  private void closeCurrentWriter() {
    if (currentWriter != null) {
      try {
        currentWriter.close();
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to close current writer", e);
      }

      if (currentFileRows == 0L) {
        try {
          io.deleteFile(currentFile.encryptingOutputFile());
        } catch (UncheckedIOException e) {
          // the file may not have been created, and it isn't worth failing the job to clean up,
          // skip deleting
        }
      } else {
        addResult(currentWriter.result());
      }

      this.currentFile = null;
      this.currentFileRows = 0;
      this.currentWriter = null;
    }
  }

  /** 关闭写入器，关闭最后一个文件。 */
  @Override
  public void close() throws IOException {
    if (!closed) {
      closeCurrentWriter();
      this.closed = true;
    }
  }

  /** 返回聚合结果，必须在 close 之后调用。 */
  @Override
  public final R result() {
    Preconditions.checkState(closed, "Cannot get result from unclosed writer");
    return aggregatedResult();
  }
}
