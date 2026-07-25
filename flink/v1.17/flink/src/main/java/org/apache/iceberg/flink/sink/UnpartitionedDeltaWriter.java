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
package org.apache.iceberg.flink.sink;

import java.io.IOException;
import java.util.List;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;

/**
 * 非分区表的 delta 任务写入器。
 *
 * <p>所属模块：iceberg-flink（sink 侧），继承 {@link BaseDeltaTaskWriter}。
 *
 * <p>职责：为非分区表写出数据与删除文件，所有行路由到同一个 {@link RowDataDeltaWriter}（分区为 null）。
 *
 * <p>设计意图：相比分区写入器，无需按分区键分发，单一 writer 即可；复用父类的文件滚动、upsert 等通用逻辑。
 *
 * <p>上下游关系：被 {@link FlinkSink} 写入算子在非分区场景下创建使用。
 */
class UnpartitionedDeltaWriter extends BaseDeltaTaskWriter {
  private final RowDataDeltaWriter writer;

  /**
   * 构造非分区 delta 写入器。
   *
   * @param spec 分区规格
   * @param format 文件格式
   * @param appenderFactory 文件 appender 工厂
   * @param fileFactory 输出文件工厂
   * @param io 文件 IO
   * @param targetFileSize 目标文件大小
   * @param schema 表 schema
   * @param flinkSchema Flink RowType
   * @param equalityFieldIds equality 字段 id
   * @param upsert 是否 upsert 模式
   */
  UnpartitionedDeltaWriter(
      PartitionSpec spec,
      FileFormat format,
      FileAppenderFactory<RowData> appenderFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSize,
      Schema schema,
      RowType flinkSchema,
      List<Integer> equalityFieldIds,
      boolean upsert) {
    super(
        spec,
        format,
        appenderFactory,
        fileFactory,
        io,
        targetFileSize,
        schema,
        flinkSchema,
        equalityFieldIds,
        upsert);
    this.writer = new RowDataDeltaWriter(null);
  }

  /** 非分区场景下所有行都路由到唯一的 writer。 */
  @Override
  RowDataDeltaWriter route(RowData row) {
    return writer;
  }

  /** 关闭底层 writer。 */
  @Override
  public void close() throws IOException {
    writer.close();
  }
}
