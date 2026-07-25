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
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.CharSequenceSet;

/**
 * 文件级说明：position-delete 滚动写入器。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link RollingFileWriter}，在单个 spec/partition 内按目标文件大小滚动写入 position-delete 记录（数据文件路径 +
 * 行号），产出多个 {@link DeleteFile} 及被引用数据文件集合。
 *
 * <p>设计意图：position-delete 通过"文件路径 + 行号"定位被删行，因此需要收集被引用的数据文件路径 （referencedDataFiles），供提交时用于 CDC
 * 等场景。newWriter 委托给 {@link FileWriterFactory#newPositionDeleteWriter}。
 *
 * <p>上下游关系：由 {@link ClusteredPositionDeleteWriter} 和 {@link FanoutPositionOnlyDeleteWriter}
 * 作为单分区写入单元创建；内部使用 {@link PositionDeleteWriter}。
 *
 * @param <T> 行记录类型
 */
public class RollingPositionDeleteWriter<T>
    extends RollingFileWriter<PositionDelete<T>, PositionDeleteWriter<T>, DeleteWriteResult> {

  private final FileWriterFactory<T> writerFactory;
  private final List<DeleteFile> deleteFiles;
  private final CharSequenceSet referencedDataFiles;

  /**
   * 构造 position-delete 滚动写入器并立即打开第一个文件。
   *
   * @param writerFactory 写入器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSizeInBytes 目标文件大小
   * @param spec 分区规格
   * @param partition 分区值
   */
  public RollingPositionDeleteWriter(
      FileWriterFactory<T> writerFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSizeInBytes,
      PartitionSpec spec,
      StructLike partition) {
    super(fileFactory, io, targetFileSizeInBytes, spec, partition);
    this.writerFactory = writerFactory;
    this.deleteFiles = Lists.newArrayList();
    this.referencedDataFiles = CharSequenceSet.empty();
    openCurrentWriter();
  }

  /** 通过工厂创建 position-delete 写入器。 */
  @Override
  protected PositionDeleteWriter<T> newWriter(EncryptedOutputFile file) {
    return writerFactory.newPositionDeleteWriter(file, spec(), partition());
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
