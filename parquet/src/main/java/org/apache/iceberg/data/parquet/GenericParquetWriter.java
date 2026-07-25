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
package org.apache.iceberg.data.parquet;

import java.util.List;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.parquet.ParquetValueWriter;
import org.apache.iceberg.parquet.ParquetValueWriters.StructWriter;
import org.apache.parquet.schema.MessageType;

/**
 * 文件级说明：面向 Iceberg 通用 {@link Record} 模型的 Parquet 写入器入口。
 *
 * <p>所属模块：iceberg-parquet（data 子包内针对 Iceberg 内置 Record 类型的写入实现）。
 *
 * <p>职责：作为 {@link BaseParquetWriter} 的具体子类，为 {@link Record} 提供 Parquet 写入器构造入口，并定义 struct 字段如何通过索引从
 * Record 取值。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>单例（INSTANCE）：写入器构造逻辑无状态，复用单例避免重复创建。
 *   <li>委托基类：复用 {@link BaseParquetWriter} 的 schema 遍历与 logical type 处理， 只在最后一步提供 {@link
 *       RecordWriter} 的字段取数策略。
 * </ul>
 *
 * <p>上下游关系：被 iceberg-core / 各引擎集成层在写入 Iceberg Generic Record 数据时调用。
 */
public class GenericParquetWriter extends BaseParquetWriter<Record> {
  private static final GenericParquetWriter INSTANCE = new GenericParquetWriter();

  private GenericParquetWriter() {}

  /**
   * 根据 Parquet 消息类型构造 {@link Record} 写入器。
   *
   * @param type Parquet 文件消息类型
   * @return 可写入 Record 的写入器根节点
   */
  public static ParquetValueWriter<Record> buildWriter(MessageType type) {
    return INSTANCE.createWriter(type);
  }

  /**
   * 创建 Record 的 struct 写入器。
   *
   * @param writers struct 各字段子写入器列表
   * @return {@link RecordWriter} 实例
   */
  @Override
  protected StructWriter<Record> createStructWriter(List<ParquetValueWriter<?>> writers) {
    return new RecordWriter(writers);
  }

  /**
   * Record 的 struct 写入器：按字段索引从 {@link Record} 中取值交给子写入器。
   *
   * <p>设计意图：仅覆盖 {@code get} 方法，复用父类对定义级别/重复级别的处理逻辑。
   */
  private static class RecordWriter extends StructWriter<Record> {
    private RecordWriter(List<ParquetValueWriter<?>> writers) {
      super(writers);
    }

    /**
     * 按 {@code index} 从 struct（Record）中取字段值。
     *
     * @param struct 当前 Record
     * @param index 字段索引
     * @return 字段值
     */
    @Override
    protected Object get(Record struct, int index) {
      return struct.get(index);
    }
  }
}
