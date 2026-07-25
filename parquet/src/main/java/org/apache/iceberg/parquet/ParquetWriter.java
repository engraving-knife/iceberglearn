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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.Schema;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.parquet.column.ColumnWriteStore;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.crypto.FileEncryptionProperties;
import org.apache.parquet.crypto.InternalFileEncryptor;
import org.apache.parquet.hadoop.CodecFactory;
import org.apache.parquet.hadoop.ColumnChunkPageWriteStore;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：Iceberg Parquet 文件写入器，实现 {@link FileAppender}。
 *
 * <p>所属模块：iceberg-parquet（核心写入入口，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Iceberg 记录按行写入 Parquet 文件，按 targetRowGroupSize 自动切分行组。
 *   <li>管理 ColumnWriteStore 和 ColumnChunkPageWriteStore，协调列式写入与页面刷新。
 *   <li>支持压缩、加密、metrics 收集、列索引截断等配置。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>延迟初始化 writer：ensureWriterInitialized 在首次 flushRowGroup 时才创建 ParquetFileWriter， 避免空文件产生。
 *   <li>自适应行组检查：checkSize 按平均记录大小动态调整下次检查点，平衡检查频率与性能。
 *   <li>metrics 在 close 后才可获取：确保所有行组已 flush，footer 统计完整。
 * </ul>
 *
 * <p>上下游关系：实现 FileAppender，被 iceberg-core 的 Parquet 写入流程调用； 依赖
 * ParquetFileWriter、ParquetSchemaUtil、ParquetUtil。
 *
 * @param <T> 写入的记录类型
 */
class ParquetWriter<T> implements FileAppender<T>, Closeable {

  private static final Metrics EMPTY_METRICS = new Metrics(0L, null, null, null, null);

  private final long targetRowGroupSize;
  private final Map<String, String> metadata;
  private final ParquetProperties props;
  private final CodecFactory.BytesCompressor compressor;
  private final MessageType parquetSchema;
  private final ParquetValueWriter<T> model;
  private final MetricsConfig metricsConfig;
  private final int columnIndexTruncateLength;
  private final ParquetFileWriter.Mode writeMode;
  private final OutputFile output;
  private final Configuration conf;
  private final InternalFileEncryptor fileEncryptor;

  private ColumnChunkPageWriteStore pageStore = null;
  private ColumnWriteStore writeStore;
  private long recordCount = 0;
  private long nextCheckRecordCount = 10;
  private boolean closed;
  private ParquetFileWriter writer;
  private int rowGroupOrdinal;

  private static final String COLUMN_INDEX_TRUNCATE_LENGTH = "parquet.columnindex.truncate.length";
  private static final int DEFAULT_COLUMN_INDEX_TRUNCATE_LENGTH = 64;

  /**
   * 构造 Parquet 写入器。
   *
   * <p>逻辑：将 Iceberg schema 转为 Parquet schema，创建值写入器模型， 初始化压缩器、加密器等，并调用 startRowGroup() 开始第一个行组。
   *
   * @param conf Hadoop 配置
   * @param output 输出文件
   * @param schema Iceberg schema
   * @param rowGroupSize 目标行组大小（字节）
   * @param metadata 文件元数据键值对
   * @param createWriterFunc 创建值写入器的函数
   * @param codec 压缩编解码器
   * @param properties Parquet 属性
   * @param metricsConfig metrics 配置
   * @param writeMode 写入模式（CREATE/OVERWRITE）
   * @param encryptionProperties 加密属性
   */
  @SuppressWarnings("unchecked")
  ParquetWriter(
      Configuration conf,
      OutputFile output,
      Schema schema,
      long rowGroupSize,
      Map<String, String> metadata,
      Function<MessageType, ParquetValueWriter<?>> createWriterFunc,
      CompressionCodecName codec,
      ParquetProperties properties,
      MetricsConfig metricsConfig,
      ParquetFileWriter.Mode writeMode,
      FileEncryptionProperties encryptionProperties) {
    this.targetRowGroupSize = rowGroupSize;
    this.props = properties;
    this.metadata = ImmutableMap.copyOf(metadata);
    this.compressor =
        new ParquetCodecFactory(conf, props.getPageSizeThreshold()).getCompressor(codec);
    this.parquetSchema = ParquetSchemaUtil.convert(schema, "table");
    this.model = (ParquetValueWriter<T>) createWriterFunc.apply(parquetSchema);
    this.metricsConfig = metricsConfig;
    this.columnIndexTruncateLength =
        conf.getInt(COLUMN_INDEX_TRUNCATE_LENGTH, DEFAULT_COLUMN_INDEX_TRUNCATE_LENGTH);
    this.writeMode = writeMode;
    this.output = output;
    this.conf = conf;
    this.rowGroupOrdinal = 0;
    this.fileEncryptor =
        (encryptionProperties == null ? null : new InternalFileEncryptor(encryptionProperties));

    startRowGroup();
  }

  private void ensureWriterInitialized() {
    if (writer == null) {
      try {
        this.writer =
            new ParquetFileWriter(
                ParquetIO.file(output, conf),
                parquetSchema,
                writeMode,
                targetRowGroupSize,
                0,
                columnIndexTruncateLength,
                ParquetProperties.DEFAULT_STATISTICS_TRUNCATE_LENGTH,
                ParquetProperties.DEFAULT_PAGE_WRITE_CHECKSUM_ENABLED,
                fileEncryptor);
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to create Parquet file", e);
      }

      try {
        writer.start();
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to start Parquet file writer", e);
      }
    }
  }

  /**
   * 写入一条记录：通过 model.write 将记录拆分为列值写入 ColumnWriteStore，然后检查是否需要 flush。
   *
   * @param value 要写入的记录
   */
  @Override
  public void add(T value) {
    recordCount += 1;
    model.write(0, value);
    writeStore.endRecord();
    checkSize();
  }

  /**
   * 返回文件 metrics（必须在 close 后调用）。
   *
   * @return 文件级 metrics
   * @throws IllegalStateException 若 writer 未关闭
   */
  @Override
  public Metrics metrics() {
    Preconditions.checkState(closed, "Cannot return metrics for unclosed writer");
    if (writer != null) {
      return ParquetUtil.footerMetrics(writer.getFooter(), model.metrics(), metricsConfig);
    }
    return EMPTY_METRICS;
  }

  /**
   * 返回输出文件的近似长度（close 前为近似值，close 后为精确值）。
   *
   * <p>逻辑：已 flush 到 writer 的长度 + 未 flush 的缓冲区大小。
   *
   * @return 文件长度（字节）
   */
  @Override
  public long length() {
    try {
      long length = 0L;

      if (writer != null) {
        length += writer.getPos();
      }

      if (!closed && recordCount > 0) {
        // recordCount > 0 when there are records in the write store that have not been flushed to
        // the Parquet file
        length += writeStore.getBufferedSize();
      }

      return length;

    } catch (IOException e) {
      throw new UncheckedIOException("Failed to get file length", e);
    }
  }

  @Override
  public List<Long> splitOffsets() {
    if (writer != null) {
      return ParquetUtil.getSplitOffsets(writer.getFooter());
    }
    return null;
  }

  /**
   * 检查缓冲区大小，决定是否 flush 行组。
   *
   * <p>逻辑：若缓冲区接近目标行组大小则 flush；否则按平均记录大小估算下次检查时机。
   */
  private void checkSize() {
    if (recordCount >= nextCheckRecordCount) {
      long bufferedSize = writeStore.getBufferedSize();
      double avgRecordSize = ((double) bufferedSize) / recordCount;

      if (bufferedSize > (targetRowGroupSize - 2 * avgRecordSize)) {
        flushRowGroup(false);
      } else {
        long remainingSpace = targetRowGroupSize - bufferedSize;
        long remainingRecords = (long) (remainingSpace / avgRecordSize);
        this.nextCheckRecordCount =
            recordCount
                + Math.min(
                    Math.max(remainingRecords / 2, props.getMinRowCountForPageSizeCheck()),
                    props.getMaxRowCountForPageSizeCheck());
      }
    }
  }

  /**
   * 将当前行组刷新到文件。
   *
   * <p>逻辑：若有记录则初始化 writer、写入行组开始标记、flush ColumnWriteStore、 将页面写入文件、写入行组结束标记；若未完成则关闭当前 store 并开始新行组。
   *
   * @param finished 是否是最后一次 flush（close 时调用）
   */
  private void flushRowGroup(boolean finished) {
    try {
      if (recordCount > 0) {
        ensureWriterInitialized();
        writer.startBlock(recordCount);
        writeStore.flush();
        pageStore.flushToFileWriter(writer);
        writer.endBlock();
        if (!finished) {
          writeStore.close();
          startRowGroup();
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to flush row group", e);
    }
  }

  /** 开始新的行组：创建 ColumnChunkPageWriteStore 和 ColumnWriteStore，重置记录计数。 */
  private void startRowGroup() {
    Preconditions.checkState(!closed, "Writer is closed");

    this.nextCheckRecordCount =
        Math.min(
            Math.max(recordCount / 2, props.getMinRowCountForPageSizeCheck()),
            props.getMaxRowCountForPageSizeCheck());
    this.recordCount = 0;

    this.pageStore =
        new ColumnChunkPageWriteStore(
            compressor,
            parquetSchema,
            props.getAllocator(),
            this.columnIndexTruncateLength,
            ParquetProperties.DEFAULT_PAGE_WRITE_CHECKSUM_ENABLED,
            fileEncryptor,
            rowGroupOrdinal);
    this.rowGroupOrdinal++;

    this.writeStore = props.newColumnWriteStore(parquetSchema, pageStore, pageStore);

    model.setColumnStore(writeStore);
  }

  /** 关闭写入器：flush 最后一个行组，关闭 store，写入文件 footer 和元数据，释放压缩器。 */
  @Override
  public void close() throws IOException {
    if (!closed) {
      this.closed = true;
      flushRowGroup(true);
      writeStore.close();
      if (writer != null) {
        writer.end(metadata);
      }
      if (compressor != null) {
        compressor.release();
      }
    }
  }
}
