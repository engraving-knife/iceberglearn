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
package org.apache.iceberg.flink.sink;

import java.io.IOException;
import java.util.List;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.RowDataWrapper;
import org.apache.iceberg.flink.data.RowDataProjection;
import org.apache.iceberg.io.BaseTaskWriter;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;

/**
 * 文件级说明：Flink RowData 的增量任务写入器基类。
 *
 * <p>所属模块：iceberg-flink（sink 子包），继承 Iceberg core 的 {@link BaseTaskWriter}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Flink {@link RowData} 按 RowKind（INSERT/UPDATE/DELETE）分发到数据文件或删除文件。
 *   <li>支持 UPSERT 模式：INSERT 前先写 equality delete，DELETE 只写 delete key。
 *   <li>提供 {@link RowDataDeltaWriter} 内部类，将 RowData 转为 StructLike 供基类使用。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>RowDataWrapper 将 Flink RowData 包装为 Iceberg StructLike，复用 core 的写入逻辑。
 *   <li>keyProjection 从完整 schema 投影出 equality 字段，用于 equality delete 写入。
 *   <li>UPSERT 模式下 UPDATE_BEFORE 不写删除（避免重复删除），DELETE 只写 key。
 * </ul>
 *
 * <p>上下游关系：被 {@link PartitionedDeltaWriter} 和 {@link UnpartitionedDeltaWriter} 继承； 由 {@link
 * TaskWriterFactory} 创建。
 */
abstract class BaseDeltaTaskWriter extends BaseTaskWriter<RowData> {

  private final Schema schema;
  private final Schema deleteSchema;
  private final RowDataWrapper wrapper;
  private final RowDataWrapper keyWrapper;
  private final RowDataProjection keyProjection;
  private final boolean upsert;

  /**
   * 构造方法。
   *
   * @param spec 分区规范
   * @param format 文件格式
   * @param appenderFactory 文件 appender 工厂
   * @param fileFactory 输出文件工厂
   * @param io 文件 IO
   * @param targetFileSize 目标文件大小
   * @param schema 表 schema
   * @param flinkSchema Flink 行类型
   * @param equalityFieldIds equality 字段 id 列表
   * @param upsert 是否启用 UPSERT 模式
   */
  BaseDeltaTaskWriter(
      PartitionSpec spec,
      FileFormat format,
      FileAppenderFactory<RowData> appenderFactory,
      OutputFileFactory fileFactory,
      FileIO io,
      long targetFileSize,
      Schema schema,
      RowType flinkSchema,
      List<Integer> equalityFieldIds,
      boolean upsert) {
    super(spec, format, appenderFactory, fileFactory, io, targetFileSize);
    this.schema = schema;
    this.deleteSchema = TypeUtil.select(schema, Sets.newHashSet(equalityFieldIds));
    this.wrapper = new RowDataWrapper(flinkSchema, schema.asStruct());
    this.keyWrapper =
        new RowDataWrapper(FlinkSchemaUtil.convert(deleteSchema), deleteSchema.asStruct());
    this.keyProjection =
        RowDataProjection.create(flinkSchema, schema.asStruct(), deleteSchema.asStruct());
    this.upsert = upsert;
  }

  /** 根据行数据路由到对应的 DeltaWriter（由子类实现分区/非分区路由逻辑）。 */
  abstract RowDataDeltaWriter route(RowData row);

  /** 返回 RowDataWrapper（将 RowData 包装为 StructLike）。 */
  RowDataWrapper wrapper() {
    return wrapper;
  }

  /**
   * 写入一条 RowData，根据 RowKind 分发到数据文件或删除文件。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>INSERT/UPDATE_AFTER：UPSERT 模式下先写 delete key，再写数据行。
   *   <li>UPDATE_BEFORE：UPSERT 模式下跳过（避免重复删除），否则写位置删除。
   *   <li>DELETE：UPSERT 模式下写 delete key，否则写位置删除。
   * </ul>
   *
   * @param row Flink 行数据
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(RowData row) throws IOException {
    RowDataDeltaWriter writer = route(row);

    switch (row.getRowKind()) {
      case INSERT:
      case UPDATE_AFTER:
        if (upsert) {
          writer.deleteKey(keyProjection.wrap(row));
        }
        writer.write(row);
        break;

      case UPDATE_BEFORE:
        if (upsert) {
          break; // UPDATE_BEFORE is not necessary for UPSERT, we do nothing to prevent delete one
          // row twice
        }
        writer.delete(row);
        break;
      case DELETE:
        if (upsert) {
          writer.deleteKey(keyProjection.wrap(row));
        } else {
          writer.delete(row);
        }
        break;

      default:
        throw new UnsupportedOperationException("Unknown row kind: " + row.getRowKind());
    }
  }

  /**
   * RowData 的增量写入器，继承 {@link BaseEqualityDeltaWriter}。
   *
   * <p>设计要点：将 RowData 通过 wrapper 转为 StructLike，供基类的数据/删除写入逻辑使用。
   */
  protected class RowDataDeltaWriter extends BaseEqualityDeltaWriter {
    RowDataDeltaWriter(PartitionKey partition) {
      super(partition, schema, deleteSchema);
    }

    /** 将 RowData 转为 StructLike（完整行）。 */
    @Override
    protected StructLike asStructLike(RowData data) {
      return wrapper.wrap(data);
    }

    /** 将 RowData 转为 StructLike（仅 equality key 字段）。 */
    @Override
    protected StructLike asStructLikeKey(RowData data) {
      return keyWrapper.wrap(data);
    }
  }
}
