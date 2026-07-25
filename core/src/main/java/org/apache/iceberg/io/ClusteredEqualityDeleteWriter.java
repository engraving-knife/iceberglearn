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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：聚簇式 equality-delete 写入器。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link ClusteredWriter}，向多个 spec/partition 写入 equality-delete 记录， 要求输入按分区聚簇。每个分区内通过
 * {@link RollingEqualityDeleteWriter} 实现文件滚动。
 *
 * <p>设计意图：将 ClusteredWriter 模板方法落地到 equality-delete 场景。equality-delete 不引用 数据文件，因此 addResult 中校验
 * referencesDataFiles 为 false。
 *
 * <p>上下游关系：由引擎集成层在能保证分区聚簇时创建；实现 {@link PartitioningWriter}。
 *
 * @param <T> 行记录类型
 */
public class ClusteredEqualityDeleteWriter<T> extends ClusteredWriter<T, DeleteWriteResult> {

  private final FileWriterFactory<T> writerFactory;
  private final OutputFileFactory fileFactory;
  private final FileIO io;
  private final long targetFileSizeInBytes;
  private final List<DeleteFile> deleteFiles;

  /**
   * 构造聚簇式 equality-delete 写入器。
   *
   * @param writerFactory 写入器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSizeInBytes 目标文件大小
   */
  public ClusteredEqualityDeleteWriter(
      FileWriterFactory<T> writerFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSizeInBytes) {
    this.writerFactory = writerFactory;
    this.fileFactory = fileFactory;
    this.io = io;
    this.targetFileSizeInBytes = targetFileSizeInBytes;
    this.deleteFiles = Lists.newArrayList();
  }

  /** 为每个分区创建 equality-delete 滚动写入器。 */
  @Override
  protected FileWriter<T, DeleteWriteResult> newWriter(PartitionSpec spec, StructLike partition) {
    return new RollingEqualityDeleteWriter<>(
        writerFactory, fileFactory, io, targetFileSizeInBytes, spec, partition);
  }

  /**
   * 将删除文件加入聚合列表。
   *
   * @throws IllegalArgumentException 若结果引用了数据文件
   */
  @Override
  protected void addResult(DeleteWriteResult result) {
    Preconditions.checkArgument(
        !result.referencesDataFiles(), "Equality deletes cannot reference data files");
    deleteFiles.addAll(result.deleteFiles());
  }

  /** 返回所有删除文件的聚合结果。 */
  @Override
  protected DeleteWriteResult aggregatedResult() {
    return new DeleteWriteResult(deleteFiles);
  }
}
