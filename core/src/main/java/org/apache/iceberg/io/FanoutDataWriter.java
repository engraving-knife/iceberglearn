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
import org.apache.iceberg.DataFile;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：扇出式数据写入器。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link FanoutWriter}，向多个 spec/partition 并行写入数据，每个分区维护一个 {@link
 * RollingDataWriter}（内部自带滚动），适用于输入未按分区聚簇的场景（如流式写入）。
 *
 * <p>设计意图：将 FanoutWriter 的模板方法落地到数据写入场景——newWriter 创建 RollingDataWriter， 使每个分区内的文件也能按目标大小滚动。
 *
 * <p>上下游关系：由引擎集成层在无法保证分区聚簇时创建；实现 {@link PartitioningWriter}。
 *
 * @param <T> 行记录类型
 */
public class FanoutDataWriter<T> extends FanoutWriter<T, DataWriteResult> {

  private final FileWriterFactory<T> writerFactory;
  private final OutputFileFactory fileFactory;
  private final FileIO io;
  private final long targetFileSizeInBytes;
  private final List<DataFile> dataFiles;

  /**
   * 构造扇出式数据写入器。
   *
   * @param writerFactory 写入器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSizeInBytes 目标文件大小
   */
  public FanoutDataWriter(
      FileWriterFactory<T> writerFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSizeInBytes) {
    this.writerFactory = writerFactory;
    this.fileFactory = fileFactory;
    this.io = io;
    this.targetFileSizeInBytes = targetFileSizeInBytes;
    this.dataFiles = Lists.newArrayList();
  }

  /** 为每个分区创建滚动数据写入器。 */
  @Override
  protected FileWriter<T, DataWriteResult> newWriter(PartitionSpec spec, StructLike partition) {
    return new RollingDataWriter<>(
        writerFactory, fileFactory, io, targetFileSizeInBytes, spec, partition);
  }

  /** 将分区写入结果中的数据文件加入聚合列表。 */
  @Override
  protected void addResult(DataWriteResult result) {
    dataFiles.addAll(result.dataFiles());
  }

  /** 返回所有数据文件的聚合结果。 */
  @Override
  protected DataWriteResult aggregatedResult() {
    return new DataWriteResult(dataFiles);
  }
}
