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
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：equality-delete 滚动写入器。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link RollingFileWriter}，在单个 spec/partition 内按目标文件大小滚动写入 equality-delete 记录，产出多个 {@link
 * DeleteFile}。
 *
 * <p>设计意图：equality-delete 不引用具体数据文件（通过等值字段匹配），因此 addResult 中校验 referencesDataFiles 为
 * false。newWriter 委托给 {@link FileWriterFactory#newEqualityDeleteWriter}。
 *
 * <p>上下游关系：由 {@link ClusteredEqualityDeleteWriter} 作为单分区写入单元创建； 内部使用 {@link EqualityDeleteWriter}。
 *
 * @param <T> 行记录类型
 */
public class RollingEqualityDeleteWriter<T>
    extends RollingFileWriter<T, EqualityDeleteWriter<T>, DeleteWriteResult> {

  private final FileWriterFactory<T> writerFactory;
  private final List<DeleteFile> deleteFiles;

  /**
   * 构造 equality-delete 滚动写入器并立即打开第一个文件。
   *
   * @param writerFactory 写入器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSizeInBytes 目标文件大小
   * @param spec 分区规格
   * @param partition 分区值
   */
  public RollingEqualityDeleteWriter(
      FileWriterFactory<T> writerFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSizeInBytes,
      PartitionSpec spec,
      StructLike partition) {
    super(fileFactory, io, targetFileSizeInBytes, spec, partition);
    this.writerFactory = writerFactory;
    this.deleteFiles = Lists.newArrayList();
    openCurrentWriter();
  }

  /** 通过工厂创建 equality-delete 写入器。 */
  @Override
  protected EqualityDeleteWriter<T> newWriter(EncryptedOutputFile file) {
    return writerFactory.newEqualityDeleteWriter(file, spec(), partition());
  }

  /**
   * 将单个文件的删除文件加入聚合列表。
   *
   * @throws IllegalArgumentException 若结果引用了数据文件（equality-delete 不应引用）
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
