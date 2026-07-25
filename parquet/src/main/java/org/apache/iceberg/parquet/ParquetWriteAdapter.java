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

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;

/**
 * 文件级说明：基于 hadoop {@link ParquetWriter} 的 {@link FileAppender} 适配器（已废弃）。
 *
 * <p>所属模块：iceberg-parquet（写入侧，包装 parquet-mr ParquetWriter 以符合 Iceberg FileAppender 接口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 Iceberg {@link FileAppender} 接口适配到 parquet-mr {@link ParquetWriter}。
 *   <li>在 close 后从 footer 提取 Metrics 与 split offsets。
 * </ul>
 *
 * <p>设计意图：早期 Iceberg 通过此适配器使用 parquet-mr ParquetWriter；现已被原生 {@link
 * org.apache.iceberg.parquet.ParquetWriter} 取代，生产环境应使用后者。
 *
 * <p>上下游关系：被 {@link Parquet.WriteBuilder} 在未提供 createWriterFunc 时作为回退构造； 依赖 parquet-mr
 * ParquetWriter 与 {@link ParquetUtil}。
 *
 * @param <D> 数据类型
 * @deprecated 请使用 {@link org.apache.iceberg.parquet.ParquetWriter}
 */
@Deprecated
public class ParquetWriteAdapter<D> implements FileAppender<D> {
  private ParquetWriter<D> writer;
  private MetricsConfig metricsConfig;
  private ParquetMetadata footer;

  /**
   * 构造写入适配器。
   *
   * @param writer parquet-mr ParquetWriter
   * @param metricsConfig 指标采集配置
   */
  public ParquetWriteAdapter(ParquetWriter<D> writer, MetricsConfig metricsConfig) {
    this.writer = writer;
    this.metricsConfig = metricsConfig;
  }

  /**
   * 追加一条记录。
   *
   * @param datum 待写入记录
   * @throws RuntimeIOException 写入失败
   */
  @Override
  public void add(D datum) {
    try {
      writer.write(datum);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to write record %s", datum);
    }
  }

  /**
   * 返回文件指标（min/max/null 计数等）。
   *
   * <p>注意：此方法返回的指标不完整，缺少 footer 中未包含的信息（如 NaN 计数）。 必须在 close 后调用。
   *
   * @return 文件指标
   * @throws IllegalStateException 未 close 时调用
   */
  @Override
  public Metrics metrics() {
    Preconditions.checkState(footer != null, "Cannot produce metrics until closed");
    return ParquetUtil.footerMetrics(footer, Stream.empty(), metricsConfig);
  }

  /** 返回已写入数据的字节长度。 */
  @Override
  public long length() {
    return writer.getDataSize();
  }

  /** 返回 row group 的 split 偏移量列表，供 split 规划使用。 */
  @Override
  public List<Long> splitOffsets() {
    return ParquetUtil.getSplitOffsets(writer.getFooter());
  }

  /**
   * 关闭写入器并保存 footer。
   *
   * @throws IOException 关闭失败
   */
  @Override
  public void close() throws IOException {
    if (writer != null) {
      writer.close();
      this.footer = writer.getFooter();
      this.writer = null;
    }
  }
}
