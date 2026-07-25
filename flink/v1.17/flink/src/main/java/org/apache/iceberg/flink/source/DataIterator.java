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
package org.apache.iceberg.flink.source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import org.apache.flink.annotation.Internal;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.encryption.InputFilesDecryptor;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 将 {@link CombinedScanTask} 读取为 {@link CloseableIterator} 的 Flink 数据迭代器。
 *
 * <p>所属模块：iceberg-flink（source 侧）。
 *
 * <p>职责：按顺序遍历合并扫描任务中的各 {@link FileScanTask}，逐文件打开读取迭代器并串联输出； 支持 seek 到指定文件/记录偏移以实现断点续读。
 *
 * <p>设计意图：维护 fileOffset 与 recordOffset 以追踪读取进度，便于在 checkpoint 恢复时定位； 当前迭代器耗尽时自动切换到下一个文件。
 *
 * <p>上下游关系：被 source reader 的批处理逻辑调用；上游为 CombinedScanTask，下游为具体 FileScanTaskReader。
 *
 * @param <T> 迭代器输出的数据类型
 */
@Internal
public class DataIterator<T> implements CloseableIterator<T> {

  private final FileScanTaskReader<T> fileScanTaskReader;

  private final InputFilesDecryptor inputFilesDecryptor;
  private final CombinedScanTask combinedTask;

  private Iterator<FileScanTask> tasks;
  private CloseableIterator<T> currentIterator;
  private int fileOffset;
  private long recordOffset;

  /**
   * 构造迭代器。
   *
   * @param fileScanTaskReader 单文件读取器
   * @param task 合并扫描任务
   * @param io 文件 IO
   * @param encryption 加密管理器
   */
  public DataIterator(
      FileScanTaskReader<T> fileScanTaskReader,
      CombinedScanTask task,
      FileIO io,
      EncryptionManager encryption) {
    this.fileScanTaskReader = fileScanTaskReader;

    this.inputFilesDecryptor = new InputFilesDecryptor(task, io, encryption);
    this.combinedTask = task;

    this.tasks = task.files().iterator();
    this.currentIterator = CloseableIterator.empty();

    // fileOffset starts at -1 because we started
    // from an empty iterator that is not from the split files.
    this.fileOffset = -1;
    // record offset points to the record that next() should return when called
    this.recordOffset = 0L;
  }

  /**
   * 定位到指定文件与记录偏移，使后续 next() 从该处返回。
   *
   * <p>逻辑：先跳过起始文件偏移之前的文件，再在起始文件内跳过起始记录偏移之前的记录。 必须在任何其它迭代操作之前调用。例如 seek(0,1) 后 next() 返回文件 0 的第 2 行。
   *
   * @param startingFileOffset 起始文件偏移
   * @param startingRecordOffset 起始记录偏移
   */
  public void seek(int startingFileOffset, long startingRecordOffset) {
    Preconditions.checkState(
        fileOffset == -1, "Seek should be called before any other iterator actions");
    // skip files
    Preconditions.checkState(
        startingFileOffset < combinedTask.files().size(),
        "Invalid starting file offset %s for combined scan task with %s files: %s",
        startingFileOffset,
        combinedTask.files().size(),
        combinedTask);
    for (long i = 0L; i < startingFileOffset; ++i) {
      tasks.next();
    }

    updateCurrentIterator();
    // skip records within the file
    for (long i = 0; i < startingRecordOffset; ++i) {
      if (currentFileHasNext() && hasNext()) {
        next();
      } else {
        throw new IllegalStateException(
            String.format(
                "Invalid starting record offset %d for file %d from CombinedScanTask: %s",
                startingRecordOffset, startingFileOffset, combinedTask));
      }
    }

    fileOffset = startingFileOffset;
    recordOffset = startingRecordOffset;
  }

  /** 是否还有下一条记录。 */
  @Override
  public boolean hasNext() {
    updateCurrentIterator();
    return currentIterator.hasNext();
  }

  /** 返回下一条记录并推进记录偏移。 */
  @Override
  public T next() {
    updateCurrentIterator();
    recordOffset += 1;
    return currentIterator.next();
  }

  /** 当前文件是否还有记录。 */
  public boolean currentFileHasNext() {
    return currentIterator.hasNext();
  }

  /** 确保当前迭代器未耗尽：当前文件读完时关闭并打开下一个文件的迭代器，重置记录偏移。 */
  private void updateCurrentIterator() {
    try {
      while (!currentIterator.hasNext() && tasks.hasNext()) {
        currentIterator.close();
        currentIterator = openTaskIterator(tasks.next());
        fileOffset += 1;
        recordOffset = 0L;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 打开单个 FileScanTask 的读取迭代器。 */
  private CloseableIterator<T> openTaskIterator(FileScanTask scanTask) {
    return fileScanTaskReader.open(scanTask, inputFilesDecryptor);
  }

  /** 关闭当前迭代器并释放资源。 */
  @Override
  public void close() throws IOException {
    // close the current iterator
    currentIterator.close();
    tasks = null;
  }

  /** 返回当前文件偏移。 */
  public int fileOffset() {
    return fileOffset;
  }

  /** 返回当前记录偏移。 */
  public long recordOffset() {
    return recordOffset;
  }
}
