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

import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.flink.source.AvroGenericRecordFileScanTaskReader;
import org.apache.iceberg.flink.source.DataIterator;
import org.apache.iceberg.flink.source.RowDataFileScanTaskReader;
import org.apache.iceberg.flink.source.RowDataToAvroGenericRecordConverter;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：把 Iceberg 行读取为 Avro {@link GenericRecord} 的 ReaderFunction。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/reader 子包）。
 *
 * <p>职责：基于 {@link RowDataFileScanTaskReader} 读取 RowData， 再通过 {@link
 * RowDataToAvroGenericRecordConverter} 转为 Avro GenericRecord， 供需要 Avro 格式输出的下游使用。
 *
 * <p>设计意图：复用现有的 RowData 读取流程，仅在末端加一层 RowData→Avro 转换， 避免重复实现文件扫描逻辑；converter 为 transient
 * 字段，懒加载以避免序列化问题。
 *
 * <p>上下游关系：上游为 {@link IcebergSourceSplit}（含文件扫描 task）， 下游为 {@link DataIterator}（产生 GenericRecord）。
 */
public class AvroGenericRecordReaderFunction extends DataIteratorReaderFunction<GenericRecord> {
  private final String tableName;
  private final Schema readSchema;
  private final FileIO io;
  private final EncryptionManager encryption;
  private final RowDataFileScanTaskReader rowDataReader;

  private transient RowDataToAvroGenericRecordConverter converter;

  /**
   * 从表对象构造不投影、不名称映射、列名大小写不敏感的 reader function。
   *
   * @param table Iceberg 表
   * @return reader function
   */
  public static AvroGenericRecordReaderFunction fromTable(Table table) {
    return new AvroGenericRecordReaderFunction(
        table.name(),
        new Configuration(),
        table.schema(),
        null,
        null,
        false,
        table.io(),
        table.encryption(),
        null);
  }

  /**
   * 构造 reader function。
   *
   * @param tableName 表名
   * @param config Flink 配置
   * @param tableSchema 表 schema
   * @param projectedSchema 投影 schema，可为空
   * @param nameMapping 名称映射，可为空
   * @param caseSensitive 是否大小写敏感
   * @param io 文件 IO
   * @param encryption 加密管理器
   * @param filters 过滤表达式列表
   */
  public AvroGenericRecordReaderFunction(
      String tableName,
      ReadableConfig config,
      Schema tableSchema,
      Schema projectedSchema,
      String nameMapping,
      boolean caseSensitive,
      FileIO io,
      EncryptionManager encryption,
      List<Expression> filters) {
    super(new ListDataIteratorBatcher<>(config));
    this.tableName = tableName;
    this.readSchema = readSchema(tableSchema, projectedSchema);
    this.io = io;
    this.encryption = encryption;
    this.rowDataReader =
        new RowDataFileScanTaskReader(tableSchema, readSchema, nameMapping, caseSensitive, filters);
  }

  /**
   * 创建 GenericRecord DataIterator。
   *
   * <p>逻辑：用 {@link AvroGenericRecordFileScanTaskReader} 包装 RowData 读取器与转换器， 构造 {@link
   * DataIterator}。
   *
   * @param split Iceberg source split
   * @return GenericRecord 的 DataIterator
   */
  @Override
  protected DataIterator<GenericRecord> createDataIterator(IcebergSourceSplit split) {
    return new DataIterator<>(
        new AvroGenericRecordFileScanTaskReader(rowDataReader, lazyConverter()),
        split.task(),
        io,
        encryption);
  }

  /** 懒加载 RowData→Avro 转换器，避免在序列化阶段创建。 */
  private RowDataToAvroGenericRecordConverter lazyConverter() {
    if (converter == null) {
      this.converter = RowDataToAvroGenericRecordConverter.fromIcebergSchema(tableName, readSchema);
    }
    return converter;
  }

  /**
   * 选择实际读取 schema，未提供投影时使用表 schema。
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
