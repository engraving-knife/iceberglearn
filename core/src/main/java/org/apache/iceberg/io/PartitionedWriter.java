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
import java.util.Set;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：分区聚簇写入器（遗留 API）。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link BaseTaskWriter}，要求输入按分区聚簇，任何时刻只维护一个分区的 writer， 适用于已按分区排序的批处理写入场景。
 *
 * <p>设计意图：这是早期版本的分区写入器，与 {@link PartitionedFanoutWriter} 互补。通过 completedPartitions
 * 集合检测违反聚簇假设的记录（已关闭的分区再次出现），并抛出异常引导用户 改用扇出模式。新代码推荐使用 {@link ClusteredDataWriter}。
 *
 * <p>上下游关系：由引擎集成层在能保证分区聚簇时创建；子类需实现 {@link #partition(Object)}。
 */
public abstract class PartitionedWriter<T> extends BaseTaskWriter<T> {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionedWriter.class);

  private final Set<PartitionKey> completedPartitions = Sets.newHashSet();

  private PartitionKey currentKey = null;
  private RollingFileWriter currentWriter = null;

  /**
   * 构造分区聚簇写入器。
   *
   * @param spec 分区规格
   * @param format 文件格式
   * @param appenderFactory 追加器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSize 目标文件大小
   */
  protected PartitionedWriter(
      PartitionSpec spec,
      FileFormat format,
      FileAppenderFactory<T> appenderFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSize) {
    super(spec, format, appenderFactory, fileFactory, io, targetFileSize);
  }

  /**
   * 从行记录中提取分区键。
   *
   * <p>返回的 PartitionKey 可以被实现复用（无需每次创建新对象）。
   *
   * @param row 数据行
   * @return 分区键
   */
  protected abstract PartitionKey partition(T row);

  /**
   * 写入一行记录到当前分区的 writer。
   *
   * <p>逻辑：提取分区键；若与当前分区不同则关闭旧 writer 并将旧分区键加入已完成集合； 若新分区已完成过则抛异常（违反聚簇）；否则拷贝分区键并创建新 writer；委托写入。
   *
   * @param row 数据行
   * @throws IOException 写入时发生 IO 错误
   * @throws IllegalStateException 若记录违反聚簇假设
   */
  @Override
  public void write(T row) throws IOException {
    PartitionKey key = partition(row);

    if (!key.equals(currentKey)) {
      if (currentKey != null) {
        // if the key is null, there was no previous current key and current writer.
        currentWriter.close();
        completedPartitions.add(currentKey);
      }

      if (completedPartitions.contains(key)) {
        // if rows are not correctly grouped, detect and fail the write
        PartitionKey existingKey = Iterables.find(completedPartitions, key::equals, null);
        LOG.warn("Duplicate key: {} == {}", existingKey, key);
        throw new IllegalStateException("Already closed files for partition: " + key.toPath());
      }

      currentKey = key.copy();
      currentWriter = new RollingFileWriter(currentKey);
    }

    currentWriter.write(row);
  }

  /** 关闭当前分区的 writer。 */
  @Override
  public void close() throws IOException {
    if (currentWriter != null) {
      currentWriter.close();
    }
  }
}
