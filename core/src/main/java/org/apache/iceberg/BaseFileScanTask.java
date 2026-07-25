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
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;

/**
 * 数据文件扫描任务的标准实现：在 {@link BaseContentScanTask} 基础上扩展了"关联删除文件" 的管理，表示一次需要读取一个数据文件并应用若干删除文件的扫描单元。
 *
 * <p>所属模块：iceberg-core，是 {@link FileScanTask} 在 core 侧的默认实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有数据文件 {@link DataFile} 与对其生效的删除文件数组 {@code deletes}。
 *   <li>懒缓存删除文件列表与删除文件总字节数，供子 split 任务复用，避免重复计算。
 *   <li>实现 {@link #split(long)} 时复用父类切分逻辑，并为每个 split 注入删除文件大小。
 *   <li>提供 {@link SplitScanTask} 内部类，表示数据文件被切分后的子任务，支持合并。
 * </ul>
 *
 * <p>设计意图：删除文件大小（{@code deletesSizeBytes}）被预先计算并缓存，因为同一数据文件 的所有 split 共享相同的删除文件集合，缓存后避免每个 split
 * 重复累加。{@code deleteList} 与 {@code deletesSizeBytes} 标记 {@code transient volatile} 以兼顾序列化与可见性。
 *
 * <p>上下游关系：由扫描计划（如 {@code ManifestGroup}）创建，被引擎层 task 调度消费； {@link SplitScanTask} 实现 {@link
 * MergeableScanTask}，可被引擎合并以减少任务数。
 */
public class BaseFileScanTask extends BaseContentScanTask<FileScanTask, DataFile>
    implements FileScanTask {
  private final DeleteFile[] deletes;
  private transient volatile List<DeleteFile> deleteList = null;
  private transient volatile long deletesSizeBytes = 0L;

  /**
   * 构造数据文件扫描任务。
   *
   * @param file 数据文件
   * @param deletes 关联的删除文件数组（可为 null，内部转为空数组）
   * @param schemaString 表 schema 的 JSON 字符串
   * @param specString 分区 spec 的 JSON 字符串
   * @param residuals 残余表达式求值器
   */
  public BaseFileScanTask(
      DataFile file,
      DeleteFile[] deletes,
      String schemaString,
      String specString,
      ResidualEvaluator residuals) {
    super(file, schemaString, specString, residuals);
    this.deletes = deletes != null ? deletes : new DeleteFile[0];
  }

  /** 自类型返回自身。 */
  @Override
  protected FileScanTask self() {
    return this;
  }

  /**
   * 创建 split 子任务，携带父任务删除文件总字节数以避免子任务重复计算。
   *
   * @param parentTask 父任务
   * @param offset 文件内起始偏移
   * @param length 读取长度
   * @return 新的 {@link SplitScanTask}
   */
  @Override
  protected FileScanTask newSplitTask(FileScanTask parentTask, long offset, long length) {
    return new SplitScanTask(offset, length, parentTask, deletesSizeBytes());
  }

  /**
   * 返回本任务关联的删除文件列表。
   *
   * <p>逻辑：首次访问时将数组拷贝为不可变列表并缓存。
   *
   * @return 删除文件不可变列表
   */
  @Override
  public List<DeleteFile> deletes() {
    if (deleteList == null) {
      this.deleteList = ImmutableList.copyOf(deletes);
    }

    return deleteList;
  }

  /**
   * 返回本任务总字节大小（数据文件读取长度 + 关联删除文件总大小）。
   *
   * @return 总字节数
   */
  @Override
  public long sizeBytes() {
    return length() + deletesSizeBytes();
  }

  /**
   * 返回本任务涉及的文件数（1 个数据文件 + N 个删除文件）。
   *
   * @return 文件数
   */
  @Override
  public int filesCount() {
    return 1 + deletes.length;
  }

  /** 返回表 schema（暴露为 public 以满足 {@link FileScanTask} 契约）。 */
  @Override
  public Schema schema() {
    return super.schema();
  }

  /**
   * 懒计算并缓存删除文件总字节数。
   *
   * <p>逻辑：仅当尚未缓存且存在删除文件时遍历累加，结果供所有 split 任务共享，避免重复计算。
   *
   * @return 删除文件总字节数（无删除文件时返回 0）
   */
  // lazily cache the size of deletes to reuse in all split tasks
  private long deletesSizeBytes() {
    if (deletesSizeBytes == 0L && deletes.length > 0) {
      long size = 0L;
      for (DeleteFile deleteFile : deletes) {
        size += deleteFile.fileSizeInBytes();
      }
      this.deletesSizeBytes = size;
    }

    return deletesSizeBytes;
  }

  /**
   * 数据文件 split 后的子任务实现：记录偏移与长度，并代理到父任务读取文件/schema 等共享信息。
   *
   * <p>设计意图：实现 {@link MergeableScanTask}，允许引擎将相邻的 split（同一文件、首尾偏移相连） 合并为更大任务，降低调度开销。每个 split
   * 自身缓存删除文件大小，避免重复统计。
   */
  @VisibleForTesting
  static final class SplitScanTask implements FileScanTask, MergeableScanTask<SplitScanTask> {
    private final long len;
    private final long offset;
    private final FileScanTask fileScanTask;
    private transient volatile long deletesSizeBytes = 0L;

    /**
     * 基础构造，未携带预计算的删除文件大小。
     *
     * @param offset 文件内起始偏移
     * @param len 读取长度
     * @param fileScanTask 父任务
     */
    SplitScanTask(long offset, long len, FileScanTask fileScanTask) {
      this.offset = offset;
      this.len = len;
      this.fileScanTask = fileScanTask;
    }

    /**
     * 携带预计算删除文件大小的构造，避免每个 split 重复统计。
     *
     * @param offset 文件内起始偏移
     * @param len 读取长度
     * @param fileScanTask 父任务
     * @param deletesSizeBytes 父任务预先计算的删除文件总字节数
     */
    SplitScanTask(long offset, long len, FileScanTask fileScanTask, long deletesSizeBytes) {
      this.offset = offset;
      this.len = len;
      this.fileScanTask = fileScanTask;
      this.deletesSizeBytes = deletesSizeBytes;
    }

    /** 返回数据文件（代理到父任务）。 */
    @Override
    public DataFile file() {
      return fileScanTask.file();
    }

    /** 返回关联的删除文件列表（代理到父任务）。 */
    @Override
    public List<DeleteFile> deletes() {
      return fileScanTask.deletes();
    }

    /** 返回表 schema（代理到父任务）。 */
    @Override
    public Schema schema() {
      return fileScanTask.schema();
    }

    /** 返回分区 spec（代理到父任务）。 */
    @Override
    public PartitionSpec spec() {
      return fileScanTask.spec();
    }

    /** 返回本 split 的起始偏移。 */
    @Override
    public long start() {
      return offset;
    }

    /** 返回本 split 的读取长度。 */
    @Override
    public long length() {
      return len;
    }

    /** 基于 split 长度估算行数。 */
    @Override
    public long estimatedRowsCount() {
      return BaseContentScanTask.estimateRowsCount(len, fileScanTask.file());
    }

    /** 返回本 split 总字节数（split 长度 + 删除文件大小）。 */
    @Override
    public long sizeBytes() {
      return len + deletesSizeBytes();
    }

    /** 返回文件数（代理到父任务）。 */
    @Override
    public int filesCount() {
      return fileScanTask.filesCount();
    }

    /** 返回残余表达式（代理到父任务）。 */
    @Override
    public Expression residual() {
      return fileScanTask.residual();
    }

    /**
     * 不支持再切分：split 任务已是原子单元。
     *
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public Iterable<FileScanTask> split(long splitSize) {
      throw new UnsupportedOperationException("Cannot split a task which is already split");
    }

    /**
     * 判断是否可与另一任务合并：仅当对方也是 {@link SplitScanTask}、指向同一文件且首尾偏移相邻时为真。
     *
     * @param other 待合并任务
     * @return 可合并返回 true
     */
    @Override
    public boolean canMerge(ScanTask other) {
      if (other instanceof SplitScanTask) {
        SplitScanTask that = (SplitScanTask) other;
        return file().equals(that.file()) && offset + len == that.start();
      } else {
        return false;
      }
    }

    /**
     * 合并相邻 split：长度相加，偏移取较小者，共享父任务与删除文件大小。
     *
     * @param other 待合并任务（须已通过 {@link #canMerge(ScanTask)} 校验）
     * @return 合并后的新 {@link SplitScanTask}
     */
    @Override
    public SplitScanTask merge(ScanTask other) {
      SplitScanTask that = (SplitScanTask) other;
      return new SplitScanTask(offset, len + that.length(), fileScanTask, deletesSizeBytes);
    }

    /**
     * 懒计算本 split 关联的删除文件总字节数。
     *
     * <p>逻辑：若未预先传入且父任务含多个文件（即有删除文件），则遍历父任务删除文件累加。
     *
     * @return 删除文件总字节数
     */
    private long deletesSizeBytes() {
      if (deletesSizeBytes == 0L && fileScanTask.filesCount() > 1) {
        long size = 0L;
        for (DeleteFile deleteFile : fileScanTask.deletes()) {
          size += deleteFile.fileSizeInBytes();
        }
        this.deletesSizeBytes = size;
      }

      return deletesSizeBytes;
    }
  }
}
