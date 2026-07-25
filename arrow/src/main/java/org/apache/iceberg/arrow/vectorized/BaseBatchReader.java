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
package org.apache.iceberg.arrow.vectorized;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.parquet.VectorizedReader;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ColumnPath;

/**
 * 文件级说明：批读取器的抽象基类，封装跨列读取器的公共逻辑。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一组 {@link VectorizedArrowReader}（按列）和对应的 {@link VectorHolder} 数组。
 *   <li>提供行组信息设置、批大小设置、向量关闭等公共方法，逐列转发给各列读取器。
 * </ul>
 *
 * <p>设计意图：将“按列分发”的模板逻辑下沉到基类，子类（如 {@link ArrowBatchReader}） 只需实现具体的 {@code read} 组装逻辑。字段以 protected
 * 暴露给同包子类以减少访问器开销。
 *
 * <p>上下游关系：实现 {@link VectorizedReader}，被 {@link ArrowBatchReader} 继承； 上游由 {@link
 * VectorizedReaderBuilder} 构造。
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class BaseBatchReader<T> implements VectorizedReader<T> {
  protected final VectorizedArrowReader[] readers;
  protected final VectorHolder[] vectorHolders;

  /**
   * 构造基类，将读取器列表转为数组并初始化向量持有者数组。
   *
   * @param readers 按列排列的向量化读取器列表
   */
  protected BaseBatchReader(List<VectorizedReader<?>> readers) {
    this.readers =
        readers.stream()
            .map(VectorizedArrowReader.class::cast)
            .toArray(VectorizedArrowReader[]::new);
    this.vectorHolders = new VectorHolder[readers.size()];
  }

  @Override
  /**
   * 设置当前行组的页存储与元数据，并转发给所有列读取器。
   *
   * @param pageStore 页读取存储
   * @param metaData 列块元数据映射
   * @param rowPosition 当前行组在文件中的起始行位置
   */
  public void setRowGroupInfo(
      PageReadStore pageStore, Map<ColumnPath, ColumnChunkMetaData> metaData, long rowPosition) {
    for (VectorizedArrowReader reader : readers) {
      if (reader != null) {
        reader.setRowGroupInfo(pageStore, metaData, rowPosition);
      }
    }
  }

  /** 关闭并清空所有向量持有者中的 Arrow 向量，释放内存。 */
  protected void closeVectors() {
    for (int i = 0; i < vectorHolders.length; i++) {
      if (vectorHolders[i] != null) {
        // Release any resources used by the vector
        if (vectorHolders[i].vector() != null) {
          vectorHolders[i].vector().close();
        }
        vectorHolders[i] = null;
      }
    }
  }

  @Override
  /** 关闭所有列读取器并释放向量资源。 */
  public void close() {
    for (VectorizedReader<?> reader : readers) {
      if (reader != null) {
        reader.close();
      }
    }
    closeVectors();
  }

  @Override
  /**
   * 设置批大小并转发给所有列读取器。
   *
   * @param batchSize 每批最大行数
   */
  public void setBatchSize(int batchSize) {
    for (VectorizedArrowReader reader : readers) {
      if (reader != null) {
        reader.setBatchSize(batchSize);
      }
    }
  }
}
