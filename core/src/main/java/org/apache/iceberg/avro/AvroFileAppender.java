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
package org.apache.iceberg.avro;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumWriter;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Avro 文件追加写入器：实现 Iceberg 的 {@link FileAppender} 接口，把数据以 Avro 文件容器 （{@code DataFileWriter}）形式写入
 * {@link OutputFile}。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 写入链路的核心实现，被 {@link Avro.WriteBuilder} 构建并返回给上层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>管理输出流与 Avro {@link DataFileWriter} 的生命周期（创建、追加、关闭）。
 *   <li>在 {@link #metrics()} 中委托 {@link AvroMetrics} 从底层 DatumWriter 收集 列级上下界/Null 计数等
 *       metrics（需在关闭后调用）。
 *   <li>维护已写入记录数，供 metrics 计算使用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把 Avro 的 {@code DataFileWriter} 适配为 Iceberg 统一的 {@link FileAppender} 抽象， 使上层不感知具体文件格式。
 *   <li>把压缩、元数据等细节下沉到构造参数（{@link CodecFactory}、metadata map）， 保持本类专注于“追加 + metrics”。
 *   <li>支持 overwrite 模式，决定调用 {@code createOrOverwrite} 还是 {@code create}。
 * </ul>
 *
 * <p>上下游关系：由 {@link Avro.WriteBuilder#build()} 构造；被 {@link DataWriter}、 {@link
 * EqualityDeleteWriter}、{@link PositionDeleteWriter} 包装使用；metrics 由 {@link AvroMetrics} 计算后回传给上层
 * manifest 写入流程。
 *
 * @param <D> 写入的数据类型
 */
class AvroFileAppender<D> implements FileAppender<D> {
  private PositionOutputStream stream;
  private DataFileWriter<D> writer;
  private DatumWriter<?> datumWriter;
  private org.apache.iceberg.Schema icebergSchema;
  private MetricsConfig metricsConfig;
  private long numRecords = 0L;
  private boolean isClosed = false;

  /**
   * 构造 Avro 文件追加写入器。
   *
   * <p>逻辑：根据 overwrite 选择创建或覆写输出流；通过 createWriterFunc 创建 DatumWriter；调用 {@link #newAvroWriter} 创建
   * Avro {@link DataFileWriter}， 设置 codec 与元数据后写入文件头。
   *
   * @param icebergSchema Iceberg schema（用于 metrics 计算）
   * @param schema Avro schema（写入文件头）
   * @param file 目标输出文件
   * @param createWriterFunc DatumWriter 创建函数
   * @param codec 压缩编解码器
   * @param metadata 文件元数据键值对
   * @param metricsConfig metrics 配置
   * @param overwrite 是否覆写已存在文件
   * @throws IOException 若创建流或写入器失败
   */
  AvroFileAppender(
      org.apache.iceberg.Schema icebergSchema,
      Schema schema,
      OutputFile file,
      Function<Schema, DatumWriter<?>> createWriterFunc,
      CodecFactory codec,
      Map<String, String> metadata,
      MetricsConfig metricsConfig,
      boolean overwrite)
      throws IOException {
    this.icebergSchema = icebergSchema;
    this.stream = overwrite ? file.createOrOverwrite() : file.create();
    this.datumWriter = createWriterFunc.apply(schema);
    this.writer = newAvroWriter(schema, stream, datumWriter, codec, metadata);
    this.metricsConfig = metricsConfig;
  }

  /**
   * 追加一条记录。
   *
   * <p>设计要点：累加记录数，委托 {@link DataFileWriter#append(Object)}；把 IOException 包装为 {@link
   * RuntimeIOException}，简化调用方异常处理。
   *
   * @param datum 待写入记录
   */
  @Override
  public void add(D datum) {
    try {
      numRecords += 1L;
      writer.append(datum);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * 返回当前文件的 metrics。
   *
   * <p>前置条件：文件必须已关闭（isClosed=true），否则抛出 IllegalStateException。 实现：委托 {@link
   * AvroMetrics#fromWriter}，传入底层 DatumWriter、Iceberg schema、 记录数与 metrics 配置。
   *
   * @return 文件 metrics
   */
  @Override
  public Metrics metrics() {
    Preconditions.checkState(isClosed, "Cannot return metrics while appending to an open file.");

    return AvroMetrics.fromWriter(datumWriter, icebergSchema, numRecords, metricsConfig);
  }

  /**
   * 返回当前已写入的字节长度。
   *
   * <p>设计要点：直接读取底层流的当前位置；流不存在或读取失败时抛出 {@link RuntimeIOException}。
   *
   * @return 已写入字节数
   */
  @Override
  public long length() {
    if (stream != null) {
      try {
        return stream.getPos();
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to get stream length");
      }
    }
    throw new RuntimeIOException("Failed to get stream length: no open stream");
  }

  /**
   * 关闭写入器并标记已关闭状态。
   *
   * <p>设计要点：仅当 writer 非空时关闭，避免重复关闭；关闭后置空 writer 并设置 isClosed=true，使 {@link #metrics()} 可用。
   *
   * @throws IOException 若关闭失败
   */
  @Override
  public void close() throws IOException {
    if (writer != null) {
      writer.close();
      this.writer = null;
      isClosed = true;
    }
  }

  /**
   * 创建 Avro {@link DataFileWriter} 并初始化文件头。
   *
   * <p>逻辑：包装传入的 metrics 感知 DatumWriter；设置压缩 codec；遍历 metadata 写入 文件元数据；最后调用 {@code create(schema,
   * stream)} 写入文件头并返回 writer。
   *
   * @param schema Avro 文件 schema
   * @param stream 输出流
   * @param metricsAwareDatumWriter metrics 感知的 DatumWriter
   * @param codec 压缩编解码器
   * @param metadata 文件元数据
   * @param <D> 数据类型
   * @return 已初始化的 {@link DataFileWriter}
   * @throws IOException 若创建失败
   */
  @SuppressWarnings("unchecked")
  private static <D> DataFileWriter<D> newAvroWriter(
      Schema schema,
      PositionOutputStream stream,
      DatumWriter<?> metricsAwareDatumWriter,
      CodecFactory codec,
      Map<String, String> metadata)
      throws IOException {
    DataFileWriter<D> writer = new DataFileWriter<>((DatumWriter<D>) metricsAwareDatumWriter);

    writer.setCodec(codec);

    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      writer.setMeta(entry.getKey(), entry.getValue());
    }

    return writer.create(schema, stream);
  }
}
