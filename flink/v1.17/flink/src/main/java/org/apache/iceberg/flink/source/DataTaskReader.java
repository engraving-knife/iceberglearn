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
package org.apache.iceberg.flink.source;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.encryption.InputFilesDecryptor;
import org.apache.iceberg.flink.data.StructRowData;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 文件级说明：读取 Iceberg 元数据表数据任务的 reader。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source 子包）。
 *
 * <p>职责：把 {@link FileScanTask#asDataTask()} 提供的元数据行（{@code StructLike}） 转换为 Flink {@link
 * RowData}，复用同一个 {@link StructRowData} 实例以降低分配开销。
 *
 * <p>设计意图：元数据表数据已经在内存中，无需走 Parquet/AVRO/ORC 解码流程， 直接通过 {@link StructRowData} 适配为 Flink 行格式。
 *
 * <p>上下游关系：上游为 {@link org.apache.iceberg.flink.source.reader.MetaDataReaderFunction}， 下游为 {@link
 * DataIterator}（迭代产生 RowData）。
 */
@Internal
public class DataTaskReader implements FileScanTaskReader<RowData> {

  private final Schema readSchema;

  /** 构造 reader，传入读取 schema。 */
  public DataTaskReader(Schema readSchema) {
    this.readSchema = readSchema;
  }

  /**
   * 打开数据 task 的迭代器。
   *
   * <p>逻辑：复用 {@link StructRowData}，把每个 {@code StructLike} 行设置进去后转换为 RowData。
   *
   * @param task 文件扫描任务（必须是 data task）
   * @param inputFilesDecryptor 输入文件解密器（元数据 task 不使用）
   * @return RowData 迭代器
   */
  @Override
  public CloseableIterator<RowData> open(
      FileScanTask task, InputFilesDecryptor inputFilesDecryptor) {
    StructRowData row = new StructRowData(readSchema.asStruct());
    CloseableIterable<RowData> iterable =
        CloseableIterable.transform(task.asDataTask().rows(), row::setStruct);
    return iterable.iterator();
  }
}
