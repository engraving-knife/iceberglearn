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
import java.util.Map;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：分区扇出写入器（遗留 API）。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link BaseTaskWriter}，为每个分区保持一个打开的 {@link BaseTaskWriter.RollingFileWriter}，
 * 适用于输入未按分区聚簇的分区表写入场景。
 *
 * <p>设计意图：这是早期版本的分区写入器，基于 BaseTaskWriter 内部的 RollingFileWriter 实现。 新代码推荐使用 {@link
 * FanoutDataWriter}。分区键在放入 Map 前必须 copy，因为 partition() 返回的 PartitionKey 可能被调用方复用，直接存入会导致后续写入覆盖已有键。
 *
 * <p>上下游关系：由引擎集成层在流式或未排序写入场景创建；子类需实现 {@link #partition(Object)} 提供分区键提取逻辑。
 */
public abstract class PartitionedFanoutWriter<T> extends BaseTaskWriter<T> {
  private final Map<PartitionKey, RollingFileWriter> writers = Maps.newHashMap();

  /**
   * 构造分区扇出写入器。
   *
   * @param spec 分区规格
   * @param format 文件格式
   * @param appenderFactory 追加器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSize 目标文件大小
   */
  protected PartitionedFanoutWriter(
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
   * 写入一行记录到对应分区的 writer。
   *
   * <p>逻辑：提取分区键；在 Map 中查找对应 writer；若不存在则拷贝分区键并创建新 writer； 委托 writer 写入。
   *
   * @param row 数据行
   * @throws IOException 写入时发生 IO 错误
   */
  @Override
  public void write(T row) throws IOException {
    PartitionKey partitionKey = partition(row);

    RollingFileWriter writer = writers.get(partitionKey);
    if (writer == null) {
      // NOTICE: we need to copy a new partition key here, in case of messing up the keys in
      // writers.
      PartitionKey copiedKey = partitionKey.copy();
      writer = new RollingFileWriter(copiedKey);
      writers.put(copiedKey, writer);
    }

    writer.write(row);
  }

  /** 关闭所有分区的 writer。 */
  @Override
  public void close() throws IOException {
    if (!writers.isEmpty()) {
      for (PartitionKey key : writers.keySet()) {
        writers.get(key).close();
      }
      writers.clear();
    }
  }
}
