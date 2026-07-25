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

import org.apache.avro.generic.GenericRecord;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.encryption.InputFilesDecryptor;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 文件级说明：将 FileScanTask 读取为 Avro {@link GenericRecord} 的读取器。
 *
 * <p>所属模块：iceberg-flink（source 子包），实现 {@link FileScanTaskReader} 接口。
 *
 * <p>职责：将 Iceberg 文件扫描任务的数据先读取为 Flink RowData，再转换为 Avro GenericRecord。
 *
 * <p>设计意图：复用 {@link RowDataFileScanTaskReader} 的读取能力，通过 {@link RowDataToAvroGenericRecordConverter}
 * 做类型转换，避免重复实现文件读取逻辑。
 *
 * <p>上下游关系：被 source reader 调用；内部委托 {@link RowDataFileScanTaskReader} 读取 RowData， 再通过 converter 转为
 * GenericRecord。
 */
public class AvroGenericRecordFileScanTaskReader implements FileScanTaskReader<GenericRecord> {
  private final RowDataFileScanTaskReader rowDataReader;
  private final RowDataToAvroGenericRecordConverter converter;

  /**
   * 构造方法。
   *
   * @param rowDataReader RowData 读取器
   * @param converter RowData 到 GenericRecord 的转换器
   */
  public AvroGenericRecordFileScanTaskReader(
      RowDataFileScanTaskReader rowDataReader, RowDataToAvroGenericRecordConverter converter) {
    this.rowDataReader = rowDataReader;
    this.converter = converter;
  }

  /**
   * 打开文件扫描任务，返回 GenericRecord 迭代器。
   *
   * <p>逻辑：通过 rowDataReader 打开文件获取 RowData 迭代器 → 使用 converter 逐条转换为 GenericRecord。
   *
   * @param fileScanTask 文件扫描任务
   * @param inputFilesDecryptor 文件解密器
   * @return GenericRecord 迭代器
   */
  @Override
  public CloseableIterator<GenericRecord> open(
      FileScanTask fileScanTask, InputFilesDecryptor inputFilesDecryptor) {
    return CloseableIterator.transform(
        rowDataReader.open(fileScanTask, inputFilesDecryptor), converter);
  }
}
