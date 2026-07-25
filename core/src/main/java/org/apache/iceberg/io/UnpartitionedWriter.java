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
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;

/**
 * 文件级说明：非分区表写入器（遗留 API）。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：继承 {@link BaseTaskWriter}，为非分区表写入数据。由于无分区，仅需维护一个 {@link
 * BaseTaskWriter.RollingFileWriter}（分区键传 null），通过滚动机制控制文件大小。
 *
 * <p>设计意图：这是早期版本的非分区写入器。新代码推荐使用 {@link ClusteredDataWriter} 或 {@link FanoutDataWriter}。
 *
 * <p>上下游关系：由引擎集成层为非分区表创建。
 */
public class UnpartitionedWriter<T> extends BaseTaskWriter<T> {

  private final RollingFileWriter currentWriter;

  /**
   * 构造非分区写入器，分区键传 null。
   *
   * @param spec 分区规格（应为非分区规格）
   * @param format 文件格式
   * @param appenderFactory 追加器工厂
   * @param fileFactory 输出文件工厂
   * @param io FileIO 实例
   * @param targetFileSize 目标文件大小
   */
  public UnpartitionedWriter(
      PartitionSpec spec,
      FileFormat format,
      FileAppenderFactory<T> appenderFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSize) {
    super(spec, format, appenderFactory, fileFactory, io, targetFileSize);
    currentWriter = new RollingFileWriter(null);
  }

  /** 写入一行记录。 */
  @Override
  public void write(T record) throws IOException {
    currentWriter.write(record);
  }

  /** 关闭写入器。 */
  @Override
  public void close() throws IOException {
    currentWriter.close();
  }
}
