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

import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.flink.RowDataWrapper;

/**
 * 按分区键选取 key 的选择器，用于将记录按分区键 shuffle，使每个分区/bucket 由单一任务写入。
 *
 * <p>所属模块：iceberg-flink（sink 侧），实现 Flink {@link KeySelector}。
 *
 * <p>职责：将 RowData 包装为 Iceberg {@link StructLike}，计算其 {@link PartitionKey} 并转为路径字符串， 作为 keyBy 的
 * key，从而减少分区扇出写出时的小文件数量。
 *
 * <p>设计意图：{@link RowDataWrapper} 含不可序列化成员，故懒构造（{@link #lazyRowDataWrapper()}）， 避免强制序列化；{@link
 * PartitionKey} 实例复用，每次 partition 调用覆盖其状态。
 *
 * <p>上下游关系：被 {@link FlinkSink} 分区扇出模式用作 keyBy 选择器。
 */
class PartitionKeySelector implements KeySelector<RowData, String> {

  private final Schema schema;
  private final PartitionKey partitionKey;
  private final RowType flinkSchema;

  private transient RowDataWrapper rowDataWrapper;

  /**
   * 构造选择器。
   *
   * @param spec 分区规格
   * @param schema 表 schema
   * @param flinkSchema Flink RowType
   */
  PartitionKeySelector(PartitionSpec spec, Schema schema, RowType flinkSchema) {
    this.schema = schema;
    this.partitionKey = new PartitionKey(spec, schema);
    this.flinkSchema = flinkSchema;
  }

  /** 懒构造 RowDataWrapper，因其部分成员不可序列化，避免在构造期强制序列化。 */
  private RowDataWrapper lazyRowDataWrapper() {
    if (rowDataWrapper == null) {
      rowDataWrapper = new RowDataWrapper(flinkSchema, schema.asStruct());
    }
    return rowDataWrapper;
  }

  /**
   * 计算行的分区键路径。
   *
   * <p>逻辑：用懒构造的 wrapper 包装 row，调用 partitionKey.partition 计算分区键，再转为路径字符串返回。
   *
   * @param row 输入行
   * @return 分区路径字符串
   */
  @Override
  public String getKey(RowData row) {
    partitionKey.partition(lazyRowDataWrapper().wrap(row));
    return partitionKey.toPath();
  }
}
