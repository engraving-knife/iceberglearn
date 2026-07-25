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

import org.apache.iceberg.FileFormat;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;

/**
 * 文件级说明：FileAppender 与各类型写入器的底层工厂接口。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：创建 {@link FileAppender}（纯追加器）以及封装了 appender 的 {@link DataWriter}、{@link
 * EqualityDeleteWriter}、{@link PositionDeleteWriter}。
 *
 * <p>设计意图：这是比 {@link FileWriterFactory} 更底层的工厂，直接接收 FileFormat 和 partition
 * 参数，负责选择具体的文件格式实现（Parquet/ORC 等）并配置 schema、压缩等参数。引擎集成层 提供具体实现。{@link FileWriterFactory}
 * 通常内部委托给本接口。
 *
 * <p>上下游关系：被 {@link FileWriterFactory} 的实现类以及 {@link BaseTaskWriter} 中的 {@link
 * SortedPosDeleteWriter} 调用。
 *
 * @param <T> 行记录的数据类型
 */
public interface FileAppenderFactory<T> {

  /**
   * 创建一个新的 {@link FileAppender}。
   *
   * @param outputFile 用于创建输出流的输出文件
   * @param fileFormat 文件格式
   * @return 新创建的 {@link FileAppender}
   */
  FileAppender<T> newAppender(OutputFile outputFile, FileFormat fileFormat);

  /**
   * 创建数据写入器。
   *
   * @param outputFile 输出文件（可能加密）
   * @param format 文件格式
   * @param partition 分区值元组
   * @return 新创建的数据写入器
   */
  DataWriter<T> newDataWriter(
      EncryptedOutputFile outputFile, FileFormat format, StructLike partition);

  /**
   * 创建 equality-delete 写入器。
   *
   * @param outputFile 输出文件（可能加密）
   * @param format 文件格式
   * @param partition 分区值元组
   * @return 新创建的 equality-delete 写入器
   */
  EqualityDeleteWriter<T> newEqDeleteWriter(
      EncryptedOutputFile outputFile, FileFormat format, StructLike partition);

  /**
   * 创建 position-delete 写入器。
   *
   * @param outputFile 输出文件（可能加密）
   * @param format 文件格式
   * @param partition 分区值元组
   * @return 新创建的 position-delete 写入器
   */
  PositionDeleteWriter<T> newPosDeleteWriter(
      EncryptedOutputFile outputFile, FileFormat format, StructLike partition);
}
