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
package org.apache.iceberg.flink.source.reader;

import org.apache.flink.annotation.Internal;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.Schema;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.source.DataIterator;
import org.apache.iceberg.flink.source.DataTaskReader;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：用于读取 Iceberg 元数据表（如 snapshots、manifests 等）的 ReaderFunction。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/reader 子包）。
 *
 * <p>职责：基于 {@link DataIteratorReaderFunction}，把 Iceberg 元数据 task 包装为 可被 FLIP-27 source 读取的 {@link
 * DataIterator}。
 *
 * <p>设计意图：元数据表与普通数据表读取流程不同，需要复用 Iceberg 的 {@link DataTaskReader} 而非基于数据文件的扫描 reader，因此单独提供该函数。
 *
 * <p>上下游关系：上游为 {@link IcebergSourceSplit}（含元数据 task）， 下游为 {@link DataIterator} 与 {@link
 * ArrayPoolDataIteratorBatcher}。
 */
@Internal
public class MetaDataReaderFunction extends DataIteratorReaderFunction<RowData> {
  private final Schema readSchema;
  private final FileIO io;
  private final EncryptionManager encryption;

  /**
   * 构造元数据读取函数。
   *
   * @param config Flink 配置
   * @param tableSchema 表 schema
   * @param projectedSchema 投影 schema，可为空
   * @param io 文件 IO
   * @param encryption 加密管理器
   */
  public MetaDataReaderFunction(
      ReadableConfig config,
      Schema tableSchema,
      Schema projectedSchema,
      FileIO io,
      EncryptionManager encryption) {
    super(
        new ArrayPoolDataIteratorBatcher<>(
            config,
            new RowDataRecordFactory(
                FlinkSchemaUtil.convert(readSchema(tableSchema, projectedSchema)))));
    this.readSchema = readSchema(tableSchema, projectedSchema);
    this.io = io;
    this.encryption = encryption;
  }

  /**
   * 创建元数据 DataIterator。
   *
   * <p>逻辑：用 {@link DataTaskReader} 包装元数据 task，构造 {@link DataIterator}。
   *
   * @param split Iceberg source split
   * @return 元数据 DataIterator
   */
  @Override
  public DataIterator<RowData> createDataIterator(IcebergSourceSplit split) {
    return new DataIterator<>(new DataTaskReader(readSchema), split.task(), io, encryption);
  }

  /**
   * 选择实际读取的 schema，若未提供投影则使用表 schema。
   *
   * @param tableSchema 表 schema
   * @param projectedSchema 投影 schema，可为空
   * @return 实际读取 schema
   */
  private static Schema readSchema(Schema tableSchema, Schema projectedSchema) {
    Preconditions.checkNotNull(tableSchema, "Table schema can't be null");
    return projectedSchema == null ? tableSchema : projectedSchema;
  }
}
