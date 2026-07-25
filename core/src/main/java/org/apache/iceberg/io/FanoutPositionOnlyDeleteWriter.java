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

import java.util.List;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.deletes.SortingPositionOnlyDeleteWriter;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.CharSequenceSet;

/**
 * 文件级说明：扇出式 position-only-delete 写入器。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link FanoutWriter}，向多个 spec/partition 写入 position-delete 记录， 不要求输入按分区聚簇。每个分区内通过 {@link
 * SortingPositionOnlyDeleteWriter} 包装 {@link RollingPositionDeleteWriter}，在写入前对 position 进行排序。
 *
 * <p>设计意图：position-delete 文件在读取时需要与数据文件做关联，若删除记录按 (path, pos) 排序， 读取效率更高。由于扇出模式下输入未排序，每个分区的 writer
 * 内部增加排序层 （{@link SortingPositionOnlyDeleteWriter}）。注意此类仅存储位置，不存储被删行数据。 若输入已排序，应使用 {@link
 * ClusteredPositionDeleteWriter} 以避免排序开销。
 *
 * <p>上下游关系：由引擎集成层在无法保证分区聚簇时创建；实现 {@link PartitioningWriter}。
 *
 * @param <T> 行记录类型
 */
public class FanoutPositionOnlyDeleteWriter<T>
    extends FanoutWriter<PositionDelete<T>, DeleteWriteResult> {

  private final FileWriterFactory<T> writerFactory;
  private final OutputFileFactory fileFactory;
  private final FileIO io;
  private final long targetFileSizeInBytes;
  private final List<DeleteFile> deleteFiles;
  private final CharSequenceSet referencedDataFiles;

  /**
   * 构造扇出式 position-only-delete 写入器。
   *
   * @param writerFactory 写入器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSizeInBytes 目标文件大小
   */
  public FanoutPositionOnlyDeleteWriter(
      FileWriterFactory<T> writerFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSizeInBytes) {
    this.writerFactory = writerFactory;
    this.fileFactory = fileFactory;
    this.io = io;
    this.targetFileSizeInBytes = targetFileSizeInBytes;
    this.deleteFiles = Lists.newArrayList();
    this.referencedDataFiles = CharSequenceSet.empty();
  }

  /**
   * 为每个分区创建带排序的 position-delete 写入器。
   *
   * <p>逻辑：先创建 {@link RollingPositionDeleteWriter}，再用 {@link SortingPositionOnlyDeleteWriter}
   * 包装，使写入前自动排序。
   */
  @Override
  protected FileWriter<PositionDelete<T>, DeleteWriteResult> newWriter(
      PartitionSpec spec, StructLike partition) {
    FileWriter<PositionDelete<T>, DeleteWriteResult> delegate =
        new RollingPositionDeleteWriter<>(
            writerFactory, fileFactory, io, targetFileSizeInBytes, spec, partition);
    return new SortingPositionOnlyDeleteWriter<>(delegate);
  }

  /** 将删除文件和被引用数据文件路径加入聚合集合。 */
  @Override
  protected void addResult(DeleteWriteResult result) {
    deleteFiles.addAll(result.deleteFiles());
    referencedDataFiles.addAll(result.referencedDataFiles());
  }

  /** 返回包含删除文件和被引用数据文件的聚合结果。 */
  @Override
  protected DeleteWriteResult aggregatedResult() {
    return new DeleteWriteResult(deleteFiles, referencedDataFiles);
  }
}
