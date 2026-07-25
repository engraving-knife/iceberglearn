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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.CharSequenceSet;
import org.apache.iceberg.util.StructLikeMap;
import org.apache.iceberg.util.StructProjection;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;

/**
 * 文件级说明：任务写入器抽象基类（遗留 API）。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link TaskWriter} 接口，管理已完成的 data/delete 文件列表和被引用数据文件集合。
 *   <li>提供 abort（中止并清理文件）和 complete（正常完成并返回结果）的通用实现。
 *   <li>提供 {@link BaseEqualityDeltaWriter} 内部类，支持 equality-delete + position-delete 混合写入。
 *   <li>提供 {@link BaseRollingWriter} 内部类及其两个子类 {@link RollingFileWriter} 和 {@link
 *       RollingEqDeleteWriter}，实现单分区内的文件滚动写入。
 * </ul>
 *
 * <p>设计意图：这是早期版本的写入框架基类，被 {@link UnpartitionedWriter}、 {@link PartitionedWriter}、{@link
 * PartitionedFanoutWriter} 继承。新代码推荐使用 {@link ClusteredDataWriter} / {@link FanoutDataWriter} 等基于
 * PartitioningWriter 的实现。 failure 字段用于在写入过程中捕获异常，complete 时校验确保不返回失败结果。
 *
 * <p>上下游关系：被遗留的分区/非分区写入器继承；内部使用 FileAppenderFactory、OutputFileFactory 和 FileIO 创建和管理文件。
 *
 * @param <T> 行记录类型
 */
public abstract class BaseTaskWriter<T> implements TaskWriter<T> {
  private final List<DataFile> completedDataFiles = Lists.newArrayList();
  private final List<DeleteFile> completedDeleteFiles = Lists.newArrayList();
  private final CharSequenceSet referencedDataFiles = CharSequenceSet.empty();

  private final PartitionSpec spec;
  private final FileFormat format;
  private final FileAppenderFactory<T> appenderFactory;
  private final OutputFileFactory fileFactory;
  private final FileIO io;
  private final long targetFileSize;
  private Throwable failure;

  /**
   * 构造任务写入器。
   *
   * @param spec 分区规格
   * @param format 文件格式
   * @param appenderFactory 追加器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSize 目标文件大小
   */
  protected BaseTaskWriter(
      PartitionSpec spec,
      FileFormat format,
      FileAppenderFactory<T> appenderFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSize) {
    this.spec = spec;
    this.format = format;
    this.appenderFactory = appenderFactory;
    this.fileFactory = fileFactory;
    this.io = io;
    this.targetFileSize = targetFileSize;
  }

  /** 返回分区规格。 */
  protected PartitionSpec spec() {
    return spec;
  }

  /** 记录失败异常（仅首次），供 complete 时校验。 */
  protected void setFailure(Throwable throwable) {
    if (failure == null) {
      this.failure = throwable;
    }
  }

  /**
   * 中止写入并删除已生成的文件。
   *
   * <p>逻辑：先 close 写入器，再用线程池并行删除所有已完成的 data 和 delete 文件。
   *
   * @throws IOException 清理过程中发生 IO 错误
   */
  @Override
  public void abort() throws IOException {
    close();

    // clean up files created by this writer
    Tasks.foreach(Iterables.concat(completedDataFiles, completedDeleteFiles))
        .executeWith(ThreadPools.getWorkerPool())
        .throwFailureWhenFinished()
        .noRetry()
        .run(file -> io.deleteFile(file.path().toString()));
  }

  /**
   * 关闭写入器并返回完整结果。
   *
   * <p>逻辑：先 close 写入器；校验无失败异常；将已完成的 data/delete 文件和被引用数据文件 组装为 WriteResult 返回。
   *
   * @return 写入结果
   * @throws IOException 关闭写入器时发生 IO 错误
   * @throws IllegalStateException 若曾发生失败
   */
  @Override
  public WriteResult complete() throws IOException {
    close();

    Preconditions.checkState(failure == null, "Cannot return results from failed writer", failure);

    return WriteResult.builder()
        .addDataFiles(completedDataFiles)
        .addDeleteFiles(completedDeleteFiles)
        .addReferencedDataFiles(referencedDataFiles)
        .build();
  }

  /**
   * 等值增量写入器基类，同时支持写入 insert 记录和 equality-delete。
   *
   * <p>设计意图：在同一个分区内，本写入器维护一个 insertedRowMap（等值字段 -> PathOffset）， 记录每个已插入行的位置。当收到 delete 请求时，先查
   * insertedRowMap：若找到匹配的已插入行， 则直接写 position-delete（成本更低）；否则写 equality-delete（需要读取端做 join 匹配）。
   * 这种"先尝试 pos-delete，回退到 eq-delete"的策略称为 position-equality-delete 优化。
   */
  protected abstract class BaseEqualityDeltaWriter implements Closeable {
    private final StructProjection structProjection;
    private RollingFileWriter dataWriter;
    private RollingEqDeleteWriter eqDeleteWriter;
    private SortedPosDeleteWriter<T> posDeleteWriter;
    private Map<StructLike, PathOffset> insertedRowMap;

    /**
     * 构造等值增量写入器。
     *
     * @param partition 分区值
     * @param schema 表 schema
     * @param deleteSchema 等值删除 schema（仅含等值字段）
     */
    protected BaseEqualityDeltaWriter(StructLike partition, Schema schema, Schema deleteSchema) {
      Preconditions.checkNotNull(schema, "Iceberg table schema cannot be null.");
      Preconditions.checkNotNull(deleteSchema, "Equality-delete schema cannot be null.");
      this.structProjection = StructProjection.create(schema, deleteSchema);

      this.dataWriter = new RollingFileWriter(partition);
      this.eqDeleteWriter = new RollingEqDeleteWriter(partition);
      this.posDeleteWriter =
          new SortedPosDeleteWriter<>(appenderFactory, fileFactory, format, partition);
      this.insertedRowMap = StructLikeMap.create(deleteSchema.asStruct());
    }

    /** 将数据行包装为 {@link StructLike}，由子类实现。 */
    protected abstract StructLike asStructLike(T data);

    /** 将删除键包装为 {@link StructLike}，由子类实现。 */
    protected abstract StructLike asStructLikeKey(T key);

    /**
     * 写入一行 insert 记录。
     *
     * <p>逻辑：记录当前行所在的文件路径和行号（PathOffset）；从行中投影出等值字段并深拷贝为 key； 将 key 和 PathOffset 存入 insertedRowMap；若
     * key 已存在（同一等值字段的行之前已插入）， 则对旧行写 position-delete；最后将行写入数据文件。
     *
     * @param row 数据行
     * @throws IOException 写入时发生 IO 错误
     */
    public void write(T row) throws IOException {
      PathOffset pathOffset = PathOffset.of(dataWriter.currentPath(), dataWriter.currentRows());

      // Create a copied key from this row.
      StructLike copiedKey = StructCopy.copy(structProjection.wrap(asStructLike(row)));

      // Adding a pos-delete to replace the old path-offset.
      PathOffset previous = insertedRowMap.put(copiedKey, pathOffset);
      if (previous != null) {
        // TODO attach the previous row if has a positional-delete row schema in appender factory.
        posDeleteWriter.delete(previous.path, previous.rowOffset, null);
      }

      dataWriter.write(row);
    }

    /**
     * 尝试对已插入的匹配行写 position-delete。
     *
     * <p>逻辑：从 insertedRowMap 中移除并获取 key 对应的 PathOffset；若存在则写 pos-delete 并返回 true，否则返回 false。
     *
     * @param key 等值字段值
     * @return true 表示已找到并删除已插入行，false 表示未找到
     */
    private boolean internalPosDelete(StructLike key) {
      PathOffset previous = insertedRowMap.remove(key);

      if (previous != null) {
        // TODO attach the previous row if has a positional-delete row schema in appender factory.
        posDeleteWriter.delete(previous.path, previous.rowOffset, null);
        return true;
      }

      return false;
    }

    /**
     * 删除等值字段匹配的行。若未找到已插入行则写 equality-delete。
     *
     * <p>逻辑：先尝试 internalPosDelete；若返回 false（未找到已插入行）则将完整行写入 equality-delete 文件。
     *
     * @param row 待删除的行
     * @throws IOException 写入时发生 IO 错误
     */
    public void delete(T row) throws IOException {
      if (!internalPosDelete(structProjection.wrap(asStructLike(row)))) {
        eqDeleteWriter.write(row);
      }
    }

    /**
     * 按键删除。若未找到已插入行则写 equality-delete（仅含等值字段）。
     *
     * <p>逻辑：先尝试 internalPosDelete；若返回 false 则将键写入 equality-delete 文件。
     *
     * @param key 删除键（仅含等值字段值）
     * @throws IOException 写入时发生 IO 错误
     */
    public void deleteKey(T key) throws IOException {
      if (!internalPosDelete(asStructLikeKey(key))) {
        eqDeleteWriter.write(key);
      }
    }

    /**
     * 关闭写入器并收集结果。
     *
     * <p>逻辑：依次关闭 dataWriter、eqDeleteWriter，清空 insertedRowMap，最后通过 posDeleteWriter.complete() 收集
     * pos-delete 文件和被引用数据文件。任何异常都会 通过 setFailure 记录。
     *
     * @throws IOException 关闭时发生 IO 错误
     */
    @Override
    public void close() throws IOException {
      try {
        // Close data writer and add completed data files.
        if (dataWriter != null) {
          try {
            dataWriter.close();
          } finally {
            dataWriter = null;
          }
        }

        // Close eq-delete writer and add completed equality-delete files.
        if (eqDeleteWriter != null) {
          try {
            eqDeleteWriter.close();
          } finally {
            eqDeleteWriter = null;
          }
        }

        if (insertedRowMap != null) {
          insertedRowMap.clear();
          insertedRowMap = null;
        }

        // Add the completed pos-delete files.
        if (posDeleteWriter != null) {
          try {
            // complete will call close
            completedDeleteFiles.addAll(posDeleteWriter.complete());
            referencedDataFiles.addAll(posDeleteWriter.referencedDataFiles());
          } finally {
            posDeleteWriter = null;
          }
        }
      } catch (IOException | RuntimeException e) {
        setFailure(e);
        throw e;
      }
    }
  }

  /** 记录已插入行所在的数据文件路径和行号，用于后续 position-delete。 */
  private static class PathOffset {
    private final CharSequence path;
    private final long rowOffset;

    private PathOffset(CharSequence path, long rowOffset) {
      this.path = path;
      this.rowOffset = rowOffset;
    }

    /** 工厂方法。 */
    private static PathOffset of(CharSequence path, long rowOffset) {
      return new PathOffset(path, rowOffset);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("path", path)
          .add("row_offset", rowOffset)
          .toString();
    }
  }

  /**
   * 滚动写入器基类，在单个分区内按目标文件大小自动滚动到新文件。
   *
   * <p>设计意图：每 1000 行检查一次文件大小，达到目标值则关闭当前文件并打开新文件。 空文件（0 行）在关闭时被删除。子类通过实现
   * newWriter/length/write/complete 四个抽象方法 适配 data 和 equality-delete 场景。
   *
   * @param <W> 底层写入器类型
   */
  private abstract class BaseRollingWriter<W extends Closeable> implements Closeable {
    private static final int ROWS_DIVISOR = 1000;
    private final StructLike partitionKey;

    private EncryptedOutputFile currentFile = null;
    private W currentWriter = null;
    private long currentRows = 0;

    private BaseRollingWriter(StructLike partitionKey) {
      this.partitionKey = partitionKey;
      openCurrent();
    }

    /** 创建新的底层写入器，由子类实现。 */
    abstract W newWriter(EncryptedOutputFile file, StructLike partition);

    /** 返回写入器当前已写入的字节数，由子类实现。 */
    abstract long length(W writer);

    /** 委托底层写入器写入一条记录，由子类实现。 */
    abstract void write(W writer, T record);

    /** 处理已关闭写入器的结果（如加入已完成文件列表），由子类实现。 */
    abstract void complete(W closedWriter);

    /**
     * 写入一条记录，达到滚动条件时切换到新文件。
     *
     * @param record 待写入的记录
     * @throws IOException 写入时发生 IO 错误
     */
    public void write(T record) throws IOException {
      write(currentWriter, record);
      this.currentRows++;

      if (shouldRollToNewFile()) {
        closeCurrent();
        openCurrent();
      }
    }

    /** 返回当前正在写入的文件路径。 */
    public CharSequence currentPath() {
      Preconditions.checkNotNull(currentFile, "The currentFile shouldn't be null");
      return currentFile.encryptingOutputFile().location();
    }

    /** 返回当前文件已写入的行数。 */
    public long currentRows() {
      return currentRows;
    }

    /** 打开新的输出文件和写入器。 */
    private void openCurrent() {
      if (partitionKey == null) {
        // unpartitioned
        this.currentFile = fileFactory.newOutputFile();
      } else {
        // partitioned
        this.currentFile = fileFactory.newOutputFile(partitionKey);
      }
      this.currentWriter = newWriter(currentFile, partitionKey);
      this.currentRows = 0;
    }

    /** 判断是否应滚动：行数为 1000 的倍数且文件大小达到目标值。 */
    private boolean shouldRollToNewFile() {
      return currentRows % ROWS_DIVISOR == 0 && length(currentWriter) >= targetFileSize;
    }

    /**
     * 关闭当前写入器并处理结果。
     *
     * <p>逻辑：关闭写入器；若当前文件为空则删除；否则调用 complete 处理结果。 异常通过 setFailure 记录。
     *
     * @throws IOException 关闭时发生 IO 错误
     */
    private void closeCurrent() throws IOException {
      if (currentWriter != null) {
        try {
          currentWriter.close();

          if (currentRows == 0L) {
            try {
              io.deleteFile(currentFile.encryptingOutputFile());
            } catch (UncheckedIOException e) {
              // the file may not have been created, and it isn't worth failing the job to clean up,
              // skip deleting
            }
          } else {
            complete(currentWriter);
          }

        } catch (IOException | RuntimeException e) {
          setFailure(e);
          throw e;

        } finally {
          this.currentFile = null;
          this.currentWriter = null;
          this.currentRows = 0;
        }
      }
    }

    /** 关闭写入器。 */
    @Override
    public void close() throws IOException {
      closeCurrent();
    }
  }

  /** 数据文件的滚动写入器，基于 {@link BaseRollingWriter} 适配 data 写入场景。 */
  protected class RollingFileWriter extends BaseRollingWriter<DataWriter<T>> {
    public RollingFileWriter(StructLike partitionKey) {
      super(partitionKey);
    }

    @Override
    DataWriter<T> newWriter(EncryptedOutputFile file, StructLike partitionKey) {
      return appenderFactory.newDataWriter(file, format, partitionKey);
    }

    @Override
    long length(DataWriter<T> writer) {
      return writer.length();
    }

    @Override
    void write(DataWriter<T> writer, T record) {
      writer.write(record);
    }

    @Override
    void complete(DataWriter<T> closedWriter) {
      completedDataFiles.add(closedWriter.toDataFile());
    }
  }

  /** equality-delete 文件的滚动写入器，基于 {@link BaseRollingWriter} 适配 eq-delete 写入场景。 */
  protected class RollingEqDeleteWriter extends BaseRollingWriter<EqualityDeleteWriter<T>> {
    RollingEqDeleteWriter(StructLike partitionKey) {
      super(partitionKey);
    }

    @Override
    EqualityDeleteWriter<T> newWriter(EncryptedOutputFile file, StructLike partitionKey) {
      return appenderFactory.newEqDeleteWriter(file, format, partitionKey);
    }

    @Override
    long length(EqualityDeleteWriter<T> writer) {
      return writer.length();
    }

    @Override
    void write(EqualityDeleteWriter<T> writer, T record) {
      writer.write(record);
    }

    @Override
    void complete(EqualityDeleteWriter<T> closedWriter) {
      completedDeleteFiles.add(closedWriter.toDeleteFile());
    }
  }
}
