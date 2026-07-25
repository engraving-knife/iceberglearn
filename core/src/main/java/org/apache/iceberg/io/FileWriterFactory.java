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

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptedOutputFile;

/**
 * 文件级说明：数据与删除写入器工厂接口。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：统一创建 {@link DataWriter}、{@link EqualityDeleteWriter} 和 {@link PositionDeleteWriter}
 * 三种写入器，由引擎集成层提供具体实现。
 *
 * <p>设计意图：将写入器的构造逻辑从 core 的写入框架中解耦，使 core 无需依赖具体文件格式 （Parquet/ORC 等）。引擎通过实现此接口注入格式特定的 writer 创建逻辑。
 *
 * <p>上下游关系：被 {@link RollingDataWriter}、{@link RollingEqualityDeleteWriter}、 {@link
 * RollingPositionDeleteWriter} 等在创建新文件时调用。
 *
 * @param <T> 行记录类型
 */
public interface FileWriterFactory<T> {

  /**
   * 创建数据写入器。
   *
   * @param file 输出文件（可能加密）
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   * @return 构造的数据写入器
   */
  DataWriter<T> newDataWriter(EncryptedOutputFile file, PartitionSpec spec, StructLike partition);

  /**
   * 创建 equality-delete 写入器。
   *
   * @param file 输出文件（可能加密）
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   * @return 构造的 equality-delete 写入器
   */
  EqualityDeleteWriter<T> newEqualityDeleteWriter(
      EncryptedOutputFile file, PartitionSpec spec, StructLike partition);

  /**
   * 创建 position-delete 写入器。
   *
   * @param file 输出文件（可能加密）
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   * @return 构造的 position-delete 写入器
   */
  PositionDeleteWriter<T> newPositionDeleteWriter(
      EncryptedOutputFile file, PartitionSpec spec, StructLike partition);
}
