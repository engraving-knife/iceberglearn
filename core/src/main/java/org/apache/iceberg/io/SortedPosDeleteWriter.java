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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.util.CharSequenceSet;
import org.apache.iceberg.util.CharSequenceWrapper;

/**
 * 文件级说明：带排序的 position-delete 写入器。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：缓冲 position-delete 记录，在缓冲达到阈值或关闭时按 (path, pos) 排序后批量写入 position-delete 文件，确保产出的删除文件内记录有序。
 *
 * <p>设计意图：position-delete 文件在读取时需要与数据文件做关联查找，若删除记录按 (path, pos) 排序，读取端可使用更高效的查找算法。本类在内存中按 path
 * 分组缓冲记录，flush 时先排序 path， 再排序每个 path 内的 pos，然后顺序写入。缓冲达到 recordsNumThreshold（默认 10 万条）时 自动
 * flush，避免内存溢出。
 *
 * <p>上下游关系：被 {@link FanoutPositionOnlyDeleteWriter} 包装在 RollingPositionDeleteWriter 之外提供排序能力；也被
 * {@link BaseTaskWriter.BaseEqualityDeltaWriter} 用于写 pos-delete。
 */
class SortedPosDeleteWriter<T> implements FileWriter<PositionDelete<T>, DeleteWriteResult> {
  private static final long DEFAULT_RECORDS_NUM_THRESHOLD = 100_000L;

  private final Map<CharSequenceWrapper, List<PosRow<T>>> posDeletes = Maps.newHashMap();
  private final List<DeleteFile> completedFiles = Lists.newArrayList();
  private final CharSequenceSet referencedDataFiles = CharSequenceSet.empty();
  private final CharSequenceWrapper wrapper = CharSequenceWrapper.wrap(null);

  private final FileAppenderFactory<T> appenderFactory;
  private final OutputFileFactory fileFactory;
  private final FileFormat format;
  private final StructLike partition;
  private final long recordsNumThreshold;

  private int records = 0;
  private boolean closed = false;
  private Throwable failure;

  /**
   * 构造带排序的 position-delete 写入器。
   *
   * @param appenderFactory 追加器工厂
   * @param fileFactory 输出文件工厂
   * @param format 文件格式
   * @param partition 分区值
   * @param recordsNumThreshold 缓冲记录数阈值，达到后自动 flush
   */
  SortedPosDeleteWriter(
      FileAppenderFactory<T> appenderFactory,
      OutputFileFactory fileFactory,
      FileFormat format,
      StructLike partition,
      long recordsNumThreshold) {
    this.appenderFactory = appenderFactory;
    this.fileFactory = fileFactory;
    this.format = format;
    this.partition = partition;
    this.recordsNumThreshold = recordsNumThreshold;
  }

  /** 使用默认阈值（10 万条）构造。 */
  SortedPosDeleteWriter(
      FileAppenderFactory<T> appenderFactory,
      OutputFileFactory fileFactory,
      FileFormat format,
      StructLike partition) {
    this(appenderFactory, fileFactory, format, partition, DEFAULT_RECORDS_NUM_THRESHOLD);
  }

  /** 记录失败异常（仅首次）。 */
  protected void setFailure(Throwable throwable) {
    if (failure == null) {
      this.failure = throwable;
    }
  }

  /** 此写入器不支持 length 查询。 */
  @Override
  public long length() {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement length");
  }

  /** 从 PositionDelete payload 中提取 path/pos/row 并委托给 delete 方法。 */
  @Override
  public void write(PositionDelete<T> payload) {
    delete(payload.path(), payload.pos(), payload.row());
  }

  /** 缓冲一条 position-delete（不含行数据）。 */
  public void delete(CharSequence path, long pos) {
    delete(path, pos, null);
  }

  /**
   * 缓冲一条 position-delete 记录。
   *
   * <p>逻辑：按 path 在 Map 中查找对应的列表；若存在则追加，否则创建新列表。记录计数加一； 若达到阈值则触发 flush。
   *
   * @param path 数据文件路径
   * @param pos 行位置
   * @param row 被删除的行数据，可为 null
   */
  public void delete(CharSequence path, long pos, T row) {
    List<PosRow<T>> posRows = posDeletes.get(wrapper.set(path));
    if (posRows != null) {
      posRows.add(PosRow.of(pos, row));
    } else {
      posDeletes.put(CharSequenceWrapper.wrap(path), Lists.newArrayList(PosRow.of(pos, row)));
    }

    records += 1;

    // TODO Flush buffer based on the policy that checking whether whole heap memory size exceed the
    // threshold.
    if (records >= recordsNumThreshold) {
      flushDeletes();
    }
  }

  /**
   * 关闭写入器并返回已完成的删除文件列表。
   *
   * @return 已完成的删除文件列表
   * @throws IOException 关闭时发生 IO 错误
   * @throws IllegalStateException 若曾发生失败
   */
  public List<DeleteFile> complete() throws IOException {
    close();

    Preconditions.checkState(failure == null, "Cannot return results from failed writer", failure);

    return completedFiles;
  }

  /** 返回被引用的数据文件路径集合。 */
  public CharSequenceSet referencedDataFiles() {
    return referencedDataFiles;
  }

  /** 关闭写入器，触发最终 flush。 */
  @Override
  public void close() throws IOException {
    if (!closed) {
      this.closed = true;
      flushDeletes();
    }
  }

  /** 返回聚合结果，必须在 close 之后调用。 */
  @Override
  public DeleteWriteResult result() {
    Preconditions.checkState(closed, "Cannot get result from unclosed writer");
    return new DeleteWriteResult(completedFiles, referencedDataFiles);
  }

  /**
   * 将缓冲的 position-delete 记录排序后写入文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若缓冲为空则直接返回。
   *   <li>创建新的输出文件和 PositionDeleteWriter。
   *   <li>对所有 path 排序。
   *   <li>对每个 path 内的 pos 排序。
   *   <li>按排序顺序写入 (path, pos, row) 三元组。
   *   <li>清空缓冲，收集被引用数据文件和已完成删除文件。
   * </ol>
   */
  private void flushDeletes() {
    if (posDeletes.isEmpty()) {
      return;
    }

    // Create a new output file.
    EncryptedOutputFile outputFile;
    if (partition == null) {
      outputFile = fileFactory.newOutputFile();
    } else {
      outputFile = fileFactory.newOutputFile(partition);
    }

    PositionDeleteWriter<T> writer =
        appenderFactory.newPosDeleteWriter(outputFile, format, partition);
    PositionDelete<T> posDelete = PositionDelete.create();
    try (PositionDeleteWriter<T> closeableWriter = writer) {
      // Sort all the paths.
      List<CharSequence> paths = Lists.newArrayListWithCapacity(posDeletes.keySet().size());
      for (CharSequenceWrapper charSequenceWrapper : posDeletes.keySet()) {
        paths.add(charSequenceWrapper.get());
      }
      paths.sort(Comparators.charSequences());

      // Write all the sorted <path, pos, row> triples.
      for (CharSequence path : paths) {
        List<PosRow<T>> positions = posDeletes.get(wrapper.set(path));
        positions.sort(Comparator.comparingLong(PosRow::pos));

        positions.forEach(
            posRow -> closeableWriter.write(posDelete.set(path, posRow.pos(), posRow.row())));
      }
    } catch (IOException e) {
      setFailure(e);
      throw new UncheckedIOException(
          "Failed to write the sorted path/pos pairs to pos-delete file: "
              + outputFile.encryptingOutputFile().location(),
          e);
    }

    // Clear the buffered pos-deletions.
    posDeletes.clear();
    records = 0;

    // Add the referenced data files.
    referencedDataFiles.addAll(writer.referencedDataFiles());

    // Add the completed delete files.
    completedFiles.add(writer.toDeleteFile());
  }

  /** position-delete 记录的内部表示，持有行位置和可选的行数据。 */
  private static class PosRow<R> {
    private final long pos;
    private final R row;

    static <R> PosRow<R> of(long pos, R row) {
      return new PosRow<>(pos, row);
    }

    private PosRow(long pos, R row) {
      this.pos = pos;
      this.row = row;
    }

    long pos() {
      return pos;
    }

    R row() {
      return row;
    }
  }
}
