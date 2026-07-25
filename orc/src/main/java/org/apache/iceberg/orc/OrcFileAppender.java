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
package org.apache.iceberg.orc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.hadoop.HadoopOutputFile;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.orc.OrcFile;
import org.apache.orc.StripeInformation;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ORC 文件追加写入器：实现 {@link FileAppender}，把行数据批量写入 ORC 文件。
 *
 * <p>所属模块：iceberg-orc。是 {@link ORC.WriteBuilder#build()} 的产物， 负责实际的行→列向量→ORC stripe 写入流程。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护 {@link VectorizedRowBatch}，逐行通过 {@link OrcRowWriter} 写入列向量。
 *   <li>batch 满时调用 {@link Writer#addRowBatch} 刷入 ORC writer。
 *   <li>提供 metrics()（列级统计）、length()（估算文件长度）、splitOffsets()（stripe 偏移）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>avgRowByteSize 通过 {@link EstimateOrcAvgWidthVisitor} 预估，用于 length() 估算未刷入数据的大小。
 *   <li>length() 返回的是估算值（已写 stripe + 内存中未刷数据×0.2 系数），非精确值。
 *   <li>close() 时刷入剩余 batch 再关 writer，保证数据完整。
 * </ul>
 *
 * <p>上下游关系：由 {@link ORC.WriteBuilder} 创建；内部委托 {@link OrcRowWriter} 做行写入， {@link OrcMetrics} 做
 * metrics 收集。
 */
class OrcFileAppender<D> implements FileAppender<D> {
  private static final Logger LOG = LoggerFactory.getLogger(OrcFileAppender.class);

  private final int batchSize;
  private final OutputFile file;
  private final Writer writer;
  private final VectorizedRowBatch batch;
  private final int avgRowByteSize;
  private final OrcRowWriter<D> valueWriter;
  private boolean isClosed = false;
  private final Configuration conf;
  private final MetricsConfig metricsConfig;

  /**
   * 构造 ORC 文件追加写入器。
   *
   * <p>逻辑：把 Iceberg Schema 转为 ORC schema；用 EstimateOrcAvgWidthVisitor 估算平均行宽； 创建
   * VectorizedRowBatch；配置 WriterOptions（UTC 时间戳、FileSystem、schema）； 创建底层 ORC Writer 并写入 metadata；通过
   * createWriterFunc 构造行写入器。
   */
  OrcFileAppender(
      Schema schema,
      OutputFile file,
      BiFunction<Schema, TypeDescription, OrcRowWriter<?>> createWriterFunc,
      Configuration conf,
      Map<String, byte[]> metadata,
      int batchSize,
      MetricsConfig metricsConfig) {
    this.conf = conf;
    this.file = file;
    this.batchSize = batchSize;
    this.metricsConfig = metricsConfig;

    TypeDescription orcSchema = ORCSchemaUtil.convert(schema);

    this.avgRowByteSize =
        OrcSchemaVisitor.visitSchema(orcSchema, new EstimateOrcAvgWidthVisitor()).stream()
            .reduce(Integer::sum)
            .orElse(0);
    if (avgRowByteSize == 0) {
      LOG.warn("The average length of the rows appears to be zero.");
    }

    this.batch = orcSchema.createRowBatch(this.batchSize);

    OrcFile.WriterOptions options = OrcFile.writerOptions(conf).useUTCTimestamp(true);
    if (file instanceof HadoopOutputFile) {
      options.fileSystem(((HadoopOutputFile) file).getFileSystem());
    }
    options.setSchema(orcSchema);
    this.writer = ORC.newFileWriter(file, options, metadata);
    this.valueWriter = newOrcRowWriter(schema, orcSchema, createWriterFunc);
  }

  @Override
  /**
   * 追加一行数据。
   *
   * <p>逻辑：通过 valueWriter 写入 batch；batch 满时调 addRowBatch 刷入 writer 并 reset。
   *
   * @param datum 待写入行
   * @throws UncheckedIOException 写入失败
   */
  public void add(D datum) {
    try {
      valueWriter.write(datum, batch);
      if (batch.size == this.batchSize) {
        writer.addRowBatch(batch);
        batch.reset();
      }
    } catch (IOException ioe) {
      throw new UncheckedIOException(
          String.format("Problem writing to ORC file %s", file.location()), ioe);
    }
  }

  @Override
  /**
   * 返回文件 metrics（需先 close）。
   *
   * @throws IllegalStateException 文件未关闭
   */
  public Metrics metrics() {
    Preconditions.checkState(isClosed, "Cannot return metrics while appending to an open file.");
    return OrcMetrics.fromWriter(writer, valueWriter.metrics(), metricsConfig);
  }

  @Override
  /**
   * 返回当前文件长度的估算值。
   *
   * <p>逻辑：已关闭则返回实际文件长度；否则取已写 stripe 末尾偏移 + 内存估算 （writer 内存 + batch 中未刷数据 × avgRowByteSize）× 0.2 系数。
   *
   * @return 估算文件长度（字节）
   */
  public long length() {
    if (isClosed) {
      return file.toInputFile().getLength();
    }

    long estimateMemory = writer.estimateMemory();

    long dataLength = 0;
    try {
      List<StripeInformation> stripes = writer.getStripes();
      if (!stripes.isEmpty()) {
        StripeInformation stripeInformation = stripes.get(stripes.size() - 1);
        dataLength =
            stripeInformation != null
                ? stripeInformation.getOffset() + stripeInformation.getLength()
                : 0;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format(
              "Can't get Stripe's length from the file writer with path: %s.", file.location()),
          e);
    }

    // This value is estimated, not actual.
    return (long)
        Math.ceil(dataLength + (estimateMemory + (long) batch.size * avgRowByteSize) * 0.2);
  }

  @Override
  /**
   * 返回各 stripe 的起始偏移列表（需先 close），用于 split 规划。
   *
   * @throws IllegalStateException 文件未关闭
   */
  public List<Long> splitOffsets() {
    Preconditions.checkState(isClosed, "File is not yet closed");
    try {
      List<StripeInformation> stripes = writer.getStripes();
      return Collections.unmodifiableList(Lists.transform(stripes, StripeInformation::getOffset));
    } catch (IOException e) {
      throw new RuntimeIOException(
          e, "Failed to get stripe information from writer for: %s", file.location());
    }
  }

  @Override
  /**
   * 关闭写入器：刷入剩余 batch 后关闭底层 writer。
   *
   * <p>设计要点：用 try-finally 保证即使刷入失败也会关闭 writer 并置 isClosed。
   */
  public void close() throws IOException {
    if (!isClosed) {
      try {
        if (batch.size > 0) {
          writer.addRowBatch(batch);
          batch.reset();
        }
      } finally {
        writer.close();
        this.isClosed = true;
      }
    }
  }

  @SuppressWarnings("unchecked")
  /** 通过 createWriterFunc 构造行写入器并强转为目标类型。 */
  private static <D> OrcRowWriter<D> newOrcRowWriter(
      Schema schema,
      TypeDescription orcSchema,
      BiFunction<Schema, TypeDescription, OrcRowWriter<?>> createWriterFunc) {
    return (OrcRowWriter<D>) createWriterFunc.apply(schema, orcSchema);
  }
}
