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

import java.util.stream.IntStream;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.flink.RowDataWrapper;

/**
 * 文件级说明：从数据行的 bucket 分区中提取 bucketId 作为 key 的 {@link KeySelector}。
 *
 * <p>所属模块：iceberg-flink（sink 子包），配合 {@link BucketPartitioner} 使用。
 *
 * <p>职责：对每条 RowData 计算其分区键，从中提取 bucket 字段的值作为 Flink key， 使得相同 bucket 的数据被分发到同一个并行子任务。
 *
 * <p>设计意图：Iceberg 的 bucket 分区策略需要与 Flink 的 key 分发对齐， 通过 KeySelector 提取 bucketId，再由
 * BucketPartitioner 将数据路由到对应分区。
 *
 * <p>上下游关系：被 {@link FlinkSink} 在 distributeDataStream 中使用； 依赖 {@link PartitionKey} 计算分区值，{@link
 * RowDataWrapper} 包装行数据。
 */
class BucketPartitionKeySelector implements KeySelector<RowData, Integer> {

  private final Schema schema;
  private final PartitionKey partitionKey;
  private final RowType flinkSchema;
  private final int bucketFieldPosition;

  private transient RowDataWrapper rowDataWrapper;

  /**
   * 构造方法。
   *
   * @param partitionSpec 分区规范
   * @param schema Iceberg schema
   * @param flinkSchema Flink 行类型
   */
  BucketPartitionKeySelector(PartitionSpec partitionSpec, Schema schema, RowType flinkSchema) {
    this.schema = schema;
    this.partitionKey = new PartitionKey(partitionSpec, schema);
    this.flinkSchema = flinkSchema;
    this.bucketFieldPosition = getBucketFieldPosition(partitionSpec);
  }

  /**
   * 获取 bucket 分区字段在 PartitionSpec 中的位置索引。
   *
   * @param partitionSpec 分区规范
   * @return bucket 字段的位置索引
   */
  private int getBucketFieldPosition(PartitionSpec partitionSpec) {
    int bucketFieldId = BucketPartitionerUtil.getBucketFieldId(partitionSpec);
    return IntStream.range(0, partitionSpec.fields().size())
        .filter(i -> partitionSpec.fields().get(i).fieldId() == bucketFieldId)
        .toArray()[0];
  }

  /** 懒加载 RowDataWrapper（transient 字段，序列化后需重新创建）。 */
  private RowDataWrapper lazyRowDataWrapper() {
    if (rowDataWrapper == null) {
      rowDataWrapper = new RowDataWrapper(flinkSchema, schema.asStruct());
    }

    return rowDataWrapper;
  }

  /**
   * 从 RowData 提取 bucketId 作为 key。
   *
   * <p>逻辑：通过 RowDataWrapper 包装行数据 → 计算分区键 → 取出 bucket 字段位置的值。
   *
   * @param rowData Flink 行数据
   * @return bucketId
   */
  @Override
  public Integer getKey(RowData rowData) {
    partitionKey.partition(lazyRowDataWrapper().wrap(rowData));
    return partitionKey.get(bucketFieldPosition, Integer.class);
  }
}
