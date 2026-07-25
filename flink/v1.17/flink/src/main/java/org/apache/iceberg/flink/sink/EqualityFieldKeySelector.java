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

import java.util.List;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.Schema;
import org.apache.iceberg.flink.RowDataWrapper;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.util.StructLikeWrapper;
import org.apache.iceberg.util.StructProjection;

/**
 * 文件级说明：按等值字段（equality fields）对 RowData 进行分区的 KeySelector。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：实现 Flink {@link KeySelector}，对每条 RowData 抽取等值字段并计算哈希， 保证相同等值字段的记录被路由到同一个
 * writer，从而正确生成基于等值条件的删除文件。
 *
 * <p>设计意图：等值字段定义了 UPSERT 语义中的主键， 必须确保同一主键的 INSERT 与 DELETE 落在同一 writer 才能正确去重。
 *
 * <p>上下游关系：上游为 Flink keyBy 算子，下游为 {@code RowDataTaskWriterFactory} 中的 equality writer。
 */
class EqualityFieldKeySelector implements KeySelector<RowData, Integer> {

  private final Schema schema;
  private final RowType flinkSchema;
  private final Schema deleteSchema;

  private transient RowDataWrapper rowDataWrapper;
  private transient StructProjection structProjection;
  private transient StructLikeWrapper structLikeWrapper;

  /** 构造 KeySelector，按等值字段 ID 列表从 schema 中选出 delete schema。 */
  EqualityFieldKeySelector(Schema schema, RowType flinkSchema, List<Integer> equalityFieldIds) {
    this.schema = schema;
    this.flinkSchema = flinkSchema;
    this.deleteSchema = TypeUtil.select(schema, Sets.newHashSet(equalityFieldIds));
  }

  /**
   * 懒构造 {@link RowDataWrapper}。
   *
   * <p>说明：RowDataWrapper 内含不可序列化成员，故采用懒加载避免强制序列化。
   */
  protected RowDataWrapper lazyRowDataWrapper() {
    if (rowDataWrapper == null) {
      rowDataWrapper = new RowDataWrapper(flinkSchema, schema.asStruct());
    }
    return rowDataWrapper;
  }

  /** 懒构造 {@link StructProjection}，因不可序列化而延迟到任务端创建。 */
  protected StructProjection lazyStructProjection() {
    if (structProjection == null) {
      structProjection = StructProjection.create(schema, deleteSchema);
    }
    return structProjection;
  }

  /** 懒构造 {@link StructLikeWrapper}，因不可序列化而延迟到任务端创建。 */
  protected StructLikeWrapper lazyStructLikeWrapper() {
    if (structLikeWrapper == null) {
      structLikeWrapper = StructLikeWrapper.forType(deleteSchema.asStruct());
    }
    return structLikeWrapper;
  }

  /**
   * 取每条 RowData 的等值字段并计算哈希作为分区键。
   *
   * <p>逻辑：包装 RowData → 投影到等值字段 → 包装为 StructLike → 计算哈希。
   */
  @Override
  public Integer getKey(RowData row) {
    RowDataWrapper wrappedRowData = lazyRowDataWrapper().wrap(row);
    StructProjection projectedRowData = lazyStructProjection().wrap(wrappedRowData);
    StructLikeWrapper wrapper = lazyStructLikeWrapper().set(projectedRowData);
    return wrapper.hashCode();
  }
}
