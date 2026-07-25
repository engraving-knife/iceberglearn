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
package org.apache.iceberg.parquet;

import java.util.Map;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ColumnPath;

/**
 * 文件级说明：Iceberg 向量化 Parquet 读取器接口。
 *
 * <p>所属模块：iceberg-parquet（向量化读取侧抽象，供 Spark 等引擎按批读取列式数据）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按批次（numRows）读取数据，返回类型 T 的批量记录。
 *   <li>设置批次大小、row group 信息（页源、列元数据、行偏移）。
 *   <li>提供资源释放接口。
 * </ul>
 *
 * <p>设计意图：与 {@link ParquetValueReader} 的逐行读取不同，向量化读取一次返回多行， 适配列式引擎（如 Spark 的
 * ColumnarBatch）以减少虚函数调用与对象分配开销。
 *
 * <p>上下游关系：被 {@link VectorizedParquetReader} 与各引擎向量化读取实现使用； 依赖 {@link PageReadStore} 与 {@link
 * ColumnChunkMetaData}。
 */
public interface VectorizedReader<T> {

  /**
   * 读取一批数据。
   *
   * @param reuse 可复用的容器，用于上一批对象复用以减少分配；可为 null
   * @param numRows 要读取的行数
   * @return 包含 numRows 行记录的批次对象
   */
  T read(T reuse, int numRows);

  /**
   * 设置目标批次大小，读取器据此预分配缓冲区。
   *
   * @param batchSize 批次大小（行数）
   */
  void setBatchSize(int batchSize);

  /**
   * 设置当前 row group 的信息。
   *
   * <p>逻辑：把页源、列元数据与行偏移下发给读取器，使其准备读取该 row group 的数据。
   *
   * @param pages 当前 row group 的页存储
   * @param metadata ColumnPath 到 ColumnChunkMetaData 的映射
   * @param rowPosition 当前 row group 在文件中的行偏移
   */
  void setRowGroupInfo(
      PageReadStore pages, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition);

  /** 释放读取器分配的所有资源。 */
  void close();
}
