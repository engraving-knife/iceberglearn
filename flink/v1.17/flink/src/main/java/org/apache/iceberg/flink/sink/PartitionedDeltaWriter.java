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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.Tasks;

/**
 * 文件级说明：分区表的增量任务写入器。
 *
 * <p>所属模块：iceberg-flink（sink 子包），继承 {@link BaseDeltaTaskWriter}。
 *
 * <p>职责：为分区表维护每个分区一个 {@link RowDataDeltaWriter}，根据行数据的分区值路由到对应 writer。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 {@code Map<PartitionKey, RowDataDeltaWriter>} 缓存各分区的 writer。
 *   <li>route() 时复制 PartitionKey 作为 Map key，避免后续 partitionKey 变动影响已缓存的 key。
 *   <li>close() 时遍历所有分区的 writer 逐个关闭。
 * </ul>
 *
 * <p>上下游关系：由 {@link TaskWriterFactory} 创建；继承 {@link BaseDeltaTaskWriter} 的写入逻辑。
 */
class PartitionedDeltaWriter extends BaseDeltaTaskWriter {

  private final PartitionKey partitionKey;

  private final Map<PartitionKey, RowDataDeltaWriter> writers = Maps.newHashMap();

  /**
   * 构造方法。
   *
   * @param spec 分区规范
   * @param format 文件格式
   * @param appenderFactory 文件 appender 工厂
   * @param fileFactory 输出文件工厂
   * @param io 文件 IO
   * @param targetFileSize 目标文件大小
   * @param schema 表 schema
   * @param flinkSchema Flink 行类型
   * @param equalityFieldIds equality 字段 id 列表
   * @param upsert 是否启用 UPSERT 模式
   */
  PartitionedDeltaWriter(
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
    this.partitionKey = new PartitionKey(spec, schema);
  }

  /**
   * 根据行数据的分区值路由到对应的 DeltaWriter。
   *
   * <p>逻辑：计算行数据的 PartitionKey → 从 writers map 中查找 → 不存在则创建新 writer 并放入 map（注意需复制 PartitionKey 作为
   * key）。
   *
   * @param row Flink 行数据
   * @return 对应分区的 RowDataDeltaWriter
   */
  @Override
  RowDataDeltaWriter route(RowData row) {
    partitionKey.partition(wrapper().wrap(row));

    RowDataDeltaWriter writer = writers.get(partitionKey);
    if (writer == null) {
      // NOTICE: we need to copy a new partition key here, in case of messing up the keys in
      // writers.
      PartitionKey copiedKey = partitionKey.copy();
      writer = new RowDataDeltaWriter(copiedKey);
      writers.put(copiedKey, writer);
    }

    return writer;
  }

  /**
   * 关闭所有分区的 writer。
   *
   * <p>逻辑：遍历 writers map 中所有 writer 逐个关闭（不重试），清空 map。
   */
  @Override
  public void close() {
    try {
      Tasks.foreach(writers.values())
          .throwFailureWhenFinished()
          .noRetry()
          .run(RowDataDeltaWriter::close, IOException.class);

      writers.clear();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to close equality delta writer", e);
    }
  }
}
