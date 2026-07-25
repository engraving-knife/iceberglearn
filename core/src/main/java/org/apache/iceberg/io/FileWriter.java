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

import java.io.Closeable;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;

/**
 * 文件级说明：单分区文件写入器接口。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：向一个预定义的 spec/partition 写入数据或删除记录，并在关闭后返回包含 Iceberg 元数据 的 {@link DataFile} 或 {@link
 * DeleteFile}。
 *
 * <p>设计意图：与 {@link FileAppender} 不同，FileWriter 不仅追加记录，还负责收集 Iceberg 所需的 文件级元数据（分区、排序序号、指标等），在 close
 * 后构造可提交的文件对象。实现类通常包装一个 FileAppender 并补充元信息。泛型 R 允许返回不同类型的结果（如 DataWriteResult /
 * DeleteWriteResult）。
 *
 * <p>上下游关系：由 {@link RollingFileWriter}、{@link ClusteredWriter}、{@link FanoutWriter}
 * 等作为底层写入单元使用；实现类包括 {@link DataWriter} 及各种 delete writer。
 *
 * @param <T> 行记录类型
 * @param <R> 结果类型
 */
public interface FileWriter<T, R> extends Closeable {

  /**
   * 批量写入多行记录到预定义的 spec/partition。
   *
   * @param rows 数据或删除记录集合
   */
  default void write(Iterable<T> rows) {
    for (T row : rows) {
      write(row);
    }
  }

  /**
   * 写入单行记录到预定义的 spec/partition。
   *
   * @param row 数据或删除记录
   */
  void write(T row);

  /**
   * 返回当前写入器已写入的字节数。
   *
   * @return 已写入字节数
   */
  long length();

  /**
   * 返回包含已写文件信息的结果。仅在写入器关闭后有效。
   *
   * @return 文件写入结果
   */
  R result();
}
