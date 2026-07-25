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
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.parquet.ParquetValueReader;
import org.apache.iceberg.parquet.ParquetValueReaders.StructReader;
import org.apache.iceberg.types.Types.StructType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：基于 {@link GenericRecord} 的 Parquet 读取器实现。
 *
 * <p>所属模块：iceberg-parquet（org.apache.iceberg.data.parquet 子包，面向内存数据模型）。
 *
 * <p>职责：继承 {@link BaseParquetReaders}，将读取结果实例化为 Iceberg 通用 {@link GenericRecord}。提供静态工厂方法 {@link
 * #buildReader} 供外部构造读取器树。
 *
 * <p>设计意图：单例（INSTANCE）+ 静态方法封装，避免重复创建构建器；通过实现 {@link #createStructReader} 提供 {@link
 * RecordReader}（内部类），将字段读取器收集到 GenericRecord 中。采用模板方法模式复用父类的类型映射逻辑。
 *
 * <p>上下游关系：依赖 BaseParquetReaders（类型映射）、GenericRecord（内存模型）； 被 iceberg-core 的 Parquet 读取流程及测试代码调用。
 */
public class GenericParquetReaders extends BaseParquetReaders<Record> {

  private static final GenericParquetReaders INSTANCE = new GenericParquetReaders();

  private GenericParquetReaders() {}

  /**
   * 构造 Parquet 读取器（不带常量列）。
   *
   * @param expectedSchema 期望读取的 Iceberg schema
   * @param fileSchema Parquet 文件实际 schema
   * @return 读取结果为 {@link Record} 的读取器
   */
  public static ParquetValueReader<Record> buildReader(
      Schema expectedSchema, MessageType fileSchema) {
    return INSTANCE.createReader(expectedSchema, fileSchema);
  }

  /**
   * 构造 Parquet 读取器（支持注入常量列）。
   *
   * @param expectedSchema 期望读取的 Iceberg schema
   * @param fileSchema Parquet 文件实际 schema
   * @param idToConstant 字段 ID 到常量值的映射，用于注入投影常量列
   * @return 读取结果为 {@link Record} 的读取器
   */
  public static ParquetValueReader<Record> buildReader(
      Schema expectedSchema, MessageType fileSchema, Map<Integer, ?> idToConstant) {
    return INSTANCE.createReader(expectedSchema, fileSchema, idToConstant);
  }

  /**
   * 创建 struct 读取器：返回 {@link RecordReader}，将各字段读取器汇聚到 GenericRecord。
   *
   * @param types 各字段对应的 Parquet 类型
   * @param fieldReaders 各字段读取器
   * @param structType 期望的 Iceberg struct 类型
   * @return RecordReader 实例
   */
  @Override
  protected ParquetValueReader<Record> createStructReader(
      List<Type> types, List<ParquetValueReader<?>> fieldReaders, StructType structType) {
    return new RecordReader(types, fieldReaders, structType);
  }

  /**
   * Record 读取器内部类：将 Parquet 列式读取结果组装为 {@link GenericRecord}。
   *
   * <p>设计意图：继承 {@link StructReader}，复用其字段位置管理与 null 处理逻辑； 通过预置 template（{@link
   * GenericRecord#create}）+ copy() 方式创建新行， 避免每次访问 NAME_MAP_CACHE，提升性能。
   */
  private static class RecordReader extends StructReader<Record, Record> {
    private final GenericRecord template;

    RecordReader(List<Type> types, List<ParquetValueReader<?>> readers, StructType struct) {
      super(types, readers);
      this.template = struct != null ? GenericRecord.create(struct) : null;
    }

    /**
     * 创建中间 struct 数据容器。
     *
     * <p>设计要点：若调用方传入可复用对象则直接复用；否则通过 template.copy() 创建， 比 GenericRecord.create(struct) 更快（省去
     * NAME_MAP_CACHE 查找）。
     *
     * @param reuse 调用方传入的可复用 Record，可为 null
     * @return 用于写入字段的 Record 容器
     */
    @Override
    protected Record newStructData(Record reuse) {
      if (reuse != null) {
        return reuse;
      } else {
        // GenericRecord.copy() 比 GenericRecord.create(StructType) 性能更好，
        // 因为省去了 NAME_MAP_CACHE 访问。这里用 copy 提升性能。
        return template.copy();
      }
    }

    /** 从中间容器中按位置取字段值。 */
    @Override
    protected Object getField(Record intermediate, int pos) {
      return intermediate.get(pos);
    }

    /** 构建最终 struct 结果：直接返回已填充的 Record。 */
    @Override
    protected Record buildStruct(Record struct) {
      return struct;
    }

    /** 按位置写入字段值到 Record。 */
    @Override
    protected void set(Record struct, int pos, Object value) {
      struct.set(pos, value);
    }
  }
}
