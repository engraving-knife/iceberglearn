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

import java.io.Serializable;
import org.apache.flink.annotation.Internal;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.encryption.InputFilesDecryptor;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 文件级说明：把 {@link FileScanTask} 读取为 {@link CloseableIterator} 的接口。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source 子包）。
 *
 * <p>职责：定义从文件扫描 task 产生数据迭代器的标准接口， 由具体的 reader（如 {@link RowDataFileScanTaskReader}、{@link
 * DataTaskReader}）实现。
 *
 * <p>设计意图：抽象出统一接口，便于不同输出类型（RowData、Avro GenericRecord 等） 复用同一套扫描与上游调用流程。
 *
 * <p>上下游关系：上游为 {@link DataIterator}（调用 open 获取迭代器）， 下游为具体的 reader 实现类。
 *
 * @param <T> 迭代器输出的数据类型
 */
@Internal
public interface FileScanTaskReader<T> extends Serializable {
  /**
   * 打开文件扫描 task 的数据迭代器。
   *
   * @param fileScanTask 文件扫描任务
   * @param inputFilesDecryptor 输入文件解密器，用于读取加密文件
   * @return 数据迭代器
   */
  CloseableIterator<T> open(FileScanTask fileScanTask, InputFilesDecryptor inputFilesDecryptor);
}
