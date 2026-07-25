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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Supplier;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：滚动式清单文件写入器，与 {@link ManifestWriter} 不同，可在写入过程中产生多个清单文件。
 *
 * <p>所属模块：iceberg-core（表元数据写入层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>包装 {@link ManifestWriter}，按目标文件大小滚动切分，输出多个 {@link ManifestFile}。
 *   <li>支持添加（add）、已存在（existing）、删除（delete）三种清单条目。
 *   <li>在达到行数与文件大小阈值时自动关闭当前 writer 并创建新 writer。
 * </ul>
 *
 * <p>设计意图：单个清单文件过大会影响读取性能，滚动写入器通过 {@link #shouldRollToNewFile} 判断 是否切分：每 {@link #ROWS_DIVISOR}
 * 行检查一次文件大小，超过目标大小时滚动到新文件。 这样既避免频繁切分，又控制了单个清单文件的大小上限。
 *
 * <p>上下游关系：由数据写入/compaction 流程创建，依赖 {@link ManifestWriter} 供应器； 产出的 {@link ManifestFile} 列表用于快照提交。
 */
public class RollingManifestWriter<F extends ContentFile<F>> implements Closeable {
  private static final int ROWS_DIVISOR = 250;

  private final Supplier<ManifestWriter<F>> manifestWriterSupplier;
  private final long targetFileSizeInBytes;
  private final List<ManifestFile> manifestFiles;

  private long currentFileRows = 0;
  private ManifestWriter<F> currentWriter = null;

  private boolean closed = false;

  /**
   * 构造滚动写入器。
   *
   * @param manifestWriterSupplier 用于按需创建新 {@link ManifestWriter} 的供应器
   * @param targetFileSizeInBytes 单个清单文件的目标大小上限（字节）
   */
  public RollingManifestWriter(
      Supplier<ManifestWriter<F>> manifestWriterSupplier, long targetFileSizeInBytes) {
    this.manifestWriterSupplier = manifestWriterSupplier;
    this.targetFileSizeInBytes = targetFileSizeInBytes;
    this.manifestFiles = Lists.newArrayList();
  }

  /**
   * 添加一个"新增"条目。
   *
   * <p>条目的快照 ID 为当前清单的快照 ID，数据与文件序列号在提交时分配。
   *
   * @param addedFile 新增的数据文件
   */
  public void add(F addedFile) {
    currentWriter().add(addedFile);
    currentFileRows++;
  }

  /**
   * 添加一个"新增"条目并指定数据序列号。
   *
   * <p>条目的快照 ID 为当前清单的快照 ID，数据序列号为传入值，文件序列号在提交时分配。
   *
   * @param addedFile 新增的数据文件
   * @param dataSequenceNumber 该文件的数据序列号
   */
  public void add(F addedFile, long dataSequenceNumber) {
    currentWriter().add(addedFile, dataSequenceNumber);
    currentFileRows++;
  }

  /**
   * 添加一个"已存在"条目。
   *
   * <p>原始的数据序列号、文件序列号与快照 ID（提交时分配的）必须保留。
   *
   * @param existingFile 已存在的数据文件
   * @param fileSnapshotId 该数据文件加入表时的快照 ID
   * @param dataSequenceNumber 该文件的数据序列号（加入时分配）
   * @param fileSequenceNumber 该文件的文件序列号（加入时分配）
   */
  public void existing(
      F existingFile, long fileSnapshotId, long dataSequenceNumber, Long fileSequenceNumber) {
    currentWriter().existing(existingFile, fileSnapshotId, dataSequenceNumber, fileSequenceNumber);
    currentFileRows++;
  }

  /**
   * 添加一个"删除"条目。
   *
   * <p>条目的快照 ID 为当前清单的快照 ID，但原始的数据与文件序列号必须保留。
   *
   * @param deletedFile 待删除的数据文件
   * @param dataSequenceNumber 该文件的数据序列号（加入时分配）
   * @param fileSequenceNumber 该文件的文件序列号（加入时分配）
   */
  public void delete(F deletedFile, long dataSequenceNumber, Long fileSequenceNumber) {
    currentWriter().delete(deletedFile, dataSequenceNumber, fileSequenceNumber);
    currentFileRows++;
  }

  /**
   * 获取当前活跃的 {@link ManifestWriter}，必要时滚动创建新 writer。
   *
   * <p>逻辑：若当前 writer 为 null 则新建；若达到滚动条件则先关闭当前 writer 再新建。
   *
   * @return 当前活跃的清单写入器
   */
  private ManifestWriter<F> currentWriter() {
    if (currentWriter == null) {
      this.currentWriter = manifestWriterSupplier.get();
    } else if (shouldRollToNewFile()) {
      closeCurrentWriter();
      this.currentWriter = manifestWriterSupplier.get();
    }

    return currentWriter;
  }

  /**
   * 判断是否应滚动到新文件：当前行数为 {@link #ROWS_DIVISOR} 的倍数且文件大小达到目标上限。
   *
   * @return 是否应滚动
   */
  private boolean shouldRollToNewFile() {
    return currentFileRows % ROWS_DIVISOR == 0 && currentWriter.length() >= targetFileSizeInBytes;
  }

  /**
   * 关闭当前 writer，将其转为 {@link ManifestFile} 加入列表并重置行计数。
   *
   * <p>逻辑：关闭 writer、收集产物、清空 currentWriter 与 currentFileRows。
   *
   * @throws UncheckedIOException 关闭时发生 IO 异常则包装为UncheckedIOException 抛出
   */
  private void closeCurrentWriter() {
    if (currentWriter != null) {
      try {
        currentWriter.close();
        ManifestFile currentFile = currentWriter.toManifestFile();
        manifestFiles.add(currentFile);
        this.currentWriter = null;
        this.currentFileRows = 0;
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to close current writer", e);
      }
    }
  }

  /**
   * 关闭滚动写入器，关闭当前 writer 并标记为已关闭。重复调用安全。
   *
   * @throws IOException 关闭时发生的 IO 异常
   */
  @Override
  public void close() throws IOException {
    if (!closed) {
      closeCurrentWriter();
      this.closed = true;
    }
  }

  /**
   * 返回已生成的所有清单文件列表。必须在写入器关闭后调用。
   *
   * @return 清单文件列表
   * @throws IllegalStateException 写入器未关闭时抛出
   */
  public List<ManifestFile> toManifestFiles() {
    Preconditions.checkState(closed, "Cannot get ManifestFile list from unclosed writer");
    return manifestFiles;
  }
}
