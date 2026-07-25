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
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.Schema;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.source.DataIterator;
import org.apache.iceberg.flink.source.RowDataFileScanTaskReader;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：RowData 类型的读取器函数，为 FLIP-27 Source 提供 split 到 RowData 批量记录的读取。
 *
 * <p>所属模块：iceberg-flink（source/reader 子包），继承 {@link DataIteratorReaderFunction}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为每个 {@link IcebergSourceSplit} 创建 {@link DataIterator} 读取 RowData。
 *   <li>使用 {@link ArrayPoolDataIteratorBatcher} 做批量处理，配合 {@link RowDataRecordFactory} 复用数组。
 *   <li>支持 schema 投影、过滤条件、nameMapping、大小写敏感等配置。
 * </ul>
 *
 * <p>设计意图：将 RowData 读取的具体逻辑封装在单独的函数类中， 通过 {@link RowDataFileScanTaskReader} 执行底层文件读取。
 *
 * <p>上下游关系：被 {@link IcebergSourceSplitReader} 调用； 内部委托 {@link RowDataFileScanTaskReader} 和 {@link
 * ArrayPoolDataIteratorBatcher}。
 */
public class RowDataReaderFunction extends DataIteratorReaderFunction<RowData> {
  private final Schema tableSchema;
  private final Schema readSchema;
  private final String nameMapping;
  private final boolean caseSensitive;
  private final FileIO io;
  private final EncryptionManager encryption;
  private final List<Expression> filters;

  /**
   * 构造方法。
   *
   * @param config Flink 配置
   * @param tableSchema 表 schema
   * @param projectedSchema 投影 schema
   * @param nameMapping 名称映射
   * @param caseSensitive 是否大小写敏感
   * @param io 文件 IO
   * @param encryption 加密管理器
   * @param filters 过滤条件列表
   */
  public RowDataReaderFunction(
      ReadableConfig config,
      Schema tableSchema,
      Schema projectedSchema,
      String nameMapping,
      boolean caseSensitive,
      FileIO io,
      EncryptionManager encryption,
      List<Expression> filters) {
    super(
        new ArrayPoolDataIteratorBatcher<>(
            config,
            new RowDataRecordFactory(
                FlinkSchemaUtil.convert(readSchema(tableSchema, projectedSchema)))));
    this.tableSchema = tableSchema;
    this.readSchema = readSchema(tableSchema, projectedSchema);
    this.nameMapping = nameMapping;
    this.caseSensitive = caseSensitive;
    this.io = io;
    this.encryption = encryption;
    this.filters = filters;
  }

  @Override
  public DataIterator<RowData> createDataIterator(IcebergSourceSplit split) {
    return new DataIterator<>(
        new RowDataFileScanTaskReader(tableSchema, readSchema, nameMapping, caseSensitive, filters),
        split.task(),
        io,
        encryption);
  }

  private static Schema readSchema(Schema tableSchema, Schema projectedSchema) {
    Preconditions.checkNotNull(tableSchema, "Table schema can't be null");
    return projectedSchema == null ? tableSchema : projectedSchema;
  }
}
