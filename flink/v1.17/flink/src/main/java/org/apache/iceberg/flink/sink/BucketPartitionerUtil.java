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
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.transforms.PartitionSpecVisitor;

/**
 * 文件级说明：Bucket 分区器辅助工具。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>判断分区 schema 是否有且仅有一个 Bucket 字段。
 *   <li>提取 Bucket 字段的字段 ID 与桶数上限。
 *   <li>供 {@code BucketPartitioner} 决定如何按桶字段对数据进行分区。
 * </ul>
 *
 * <p>设计意图：通过访问器模式遍历 PartitionSpec，提取 Bucket 定义， 仅当恰好存在一个 Bucket 字段时才允许使用 Bucket 分区器。
 *
 * <p>上下游关系：上游为 {@code BucketPartitioner}，下游为 Iceberg 的 {@link PartitionSpecVisitor}。
 */
final class BucketPartitionerUtil {
  /** Bucket 数量不合法时的错误信息模板。 */
  static final String BAD_NUMBER_OF_BUCKETS_ERROR_MESSAGE =
      "Invalid number of buckets: %s (must be 1)";

  /** 私有构造，工具类禁止实例化。 */
  private BucketPartitionerUtil() {}

  /**
   * 判断分区 schema 是否有且仅有一个 Bucket 字段。
   *
   * @param partitionSpec 待检查的分区 schema
   * @return 若存在且仅存在一个 Bucket 字段则返回 true
   */
  static boolean hasOneBucketField(PartitionSpec partitionSpec) {
    List<Tuple2<Integer, Integer>> bucketFields = getBucketFields(partitionSpec);
    return bucketFields != null && bucketFields.size() == 1;
  }

  /**
   * 从分区 schema 中提取唯一的 Bucket 字段信息。
   *
   * @param partitionSpec 待检查的分区 schema
   * @return 二元组 (字段ID, 桶数)
   */
  private static Tuple2<Integer, Integer> getBucketFieldInfo(PartitionSpec partitionSpec) {
    List<Tuple2<Integer, Integer>> bucketFields = getBucketFields(partitionSpec);
    Preconditions.checkArgument(
        bucketFields.size() == 1,
        BucketPartitionerUtil.BAD_NUMBER_OF_BUCKETS_ERROR_MESSAGE,
        bucketFields.size());
    return bucketFields.get(0);
  }

  /** 返回 Bucket 字段的字段 ID。 */
  static int getBucketFieldId(PartitionSpec partitionSpec) {
    return getBucketFieldInfo(partitionSpec).f0;
  }

  /** 返回 Bucket 字段的最大桶数。 */
  static int getMaxNumBuckets(PartitionSpec partitionSpec) {
    return getBucketFieldInfo(partitionSpec).f1;
  }

  /** 遍历分区 schema，收集所有 Bucket 字段信息，过滤掉非 Bucket 字段（返回 null 的项）。 */
  private static List<Tuple2<Integer, Integer>> getBucketFields(PartitionSpec spec) {
    return PartitionSpecVisitor.visit(spec, new BucketPartitionSpecVisitor()).stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /** 内部访问器：仅对 bucket 变换返回字段信息，其他变换返回 null。 */
  private static class BucketPartitionSpecVisitor
      implements PartitionSpecVisitor<Tuple2<Integer, Integer>> {
    @Override
    public Tuple2<Integer, Integer> identity(int fieldId, String sourceName, int sourceId) {
      return null;
    }

    @Override
    public Tuple2<Integer, Integer> bucket(
        int fieldId, String sourceName, int sourceId, int numBuckets) {
      return new Tuple2<>(fieldId, numBuckets);
    }

    @Override
    public Tuple2<Integer, Integer> truncate(
        int fieldId, String sourceName, int sourceId, int width) {
      return null;
    }

    @Override
    public Tuple2<Integer, Integer> year(int fieldId, String sourceName, int sourceId) {
      return null;
    }

    @Override
    public Tuple2<Integer, Integer> month(int fieldId, String sourceName, int sourceId) {
      return null;
    }

    @Override
    public Tuple2<Integer, Integer> day(int fieldId, String sourceName, int sourceId) {
      return null;
    }

    @Override
    public Tuple2<Integer, Integer> hour(int fieldId, String sourceName, int sourceId) {
      return null;
    }

    @Override
    public Tuple2<Integer, Integer> alwaysNull(int fieldId, String sourceName, int sourceId) {
      return null;
    }

    @Override
    public Tuple2<Integer, Integer> unknown(
        int fieldId, String sourceName, int sourceId, String transform) {
      return null;
    }
  }
}
