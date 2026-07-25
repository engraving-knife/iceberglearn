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

import org.apache.flink.api.common.functions.Partitioner;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 基于 bucket 分区规格的 Flink 分区器，将记录确定性路由到 writer。
 *
 * <p>所属模块：iceberg-flink（sink 侧），实现 Flink {@link Partitioner}。
 *
 * <p>职责：根据 bucket id 与 writer（分区）数量关系，决定记录写入哪个 writer 子任务， 以优化写出文件大小——writer 数 ≤ bucket 数时一个 writer
 * 负责多个 bucket，反之多个 writer 共享一个 bucket。
 *
 * <p>设计意图：注意当前实现仅支持分区规格中包含单个 bucket 字段。通过模运算与轮询偏移， 在不同 writer/bucket 比例下尽量均衡负载并控制文件数量。
 *
 * <p>上下游关系：被 {@link FlinkSink} 在 bucket 分布模式下用作 keyBy 后的分区器。
 */
class BucketPartitioner implements Partitioner<Integer> {

  static final String BUCKET_NULL_MESSAGE = "bucketId cannot be null";
  static final String BUCKET_LESS_THAN_LOWER_BOUND_MESSAGE =
      "Invalid bucket ID %s: must be non-negative.";
  static final String BUCKET_GREATER_THAN_UPPER_BOUND_MESSAGE =
      "Invalid bucket ID %s: must be less than bucket limit: %s.";

  private final int maxNumBuckets;

  // To hold the OFFSET of the next writer to use for any bucket, only used when writers > the
  // number of buckets
  private final int[] currentBucketWriterOffset;

  /**
   * 构造分区器。
   *
   * @param partitionSpec 分区规格（须含单个 bucket 字段）
   */
  BucketPartitioner(PartitionSpec partitionSpec) {
    this.maxNumBuckets = BucketPartitionerUtil.getMaxNumBuckets(partitionSpec);
    this.currentBucketWriterOffset = new int[maxNumBuckets];
  }

  /**
   * 根据 bucket id 与分区数计算目标分区（writer）。
   *
   * <p>逻辑：writer 数 ≤ bucket 数时，直接取 bucketId % numPartitions（一个 writer 负责多 bucket）； writer 数 >
   * bucket 数时，委托 {@link #getPartitionWithMoreWritersThanBuckets} 轮询分配。
   *
   * @param bucketId 记录的 bucket id
   * @param numPartitions 总分区（writer）数
   * @return 目标分区 id
   */
  @Override
  public int partition(Integer bucketId, int numPartitions) {
    Preconditions.checkNotNull(bucketId, BUCKET_NULL_MESSAGE);
    Preconditions.checkArgument(bucketId >= 0, BUCKET_LESS_THAN_LOWER_BOUND_MESSAGE, bucketId);
    Preconditions.checkArgument(
        bucketId < maxNumBuckets, BUCKET_GREATER_THAN_UPPER_BOUND_MESSAGE, bucketId, maxNumBuckets);

    if (numPartitions <= maxNumBuckets) {
      return bucketId % numPartitions;
    } else {
      return getPartitionWithMoreWritersThanBuckets(bucketId, numPartitions);
    }
  }

  /**
   * 当 writer 数量大于 bucket 数量时的分区分配逻辑。
   *
   * <p>逻辑：每个 bucket 维护一个轮询偏移（currentBucketWriterOffset），将该 bucket 的多个 writer 尽量均匀分配并轮询使用，保证每个
   * writer 始终只写一个 bucket（多 writer → 一 bucket）。 例如 numPartitions=5、maxBuckets=2 时：bucket 0 在 writer
   * 0/2/4 间轮询，bucket 1 固定用 writer 1/3。
   *
   * <p>要点：
   *
   * <ul>
   *   <li>maxNumWritersPerBucket 达到上限时将偏移重置为 0。
   *   <li>numPartitions 不能被 maxBuckets 整除时，部分 bucket 多分一个 writer（extraWriter）。
   * </ul>
   *
   * @return 目标分区索引（writer 子任务 id）
   */
  private int getPartitionWithMoreWritersThanBuckets(int bucketId, int numPartitions) {
    int currentOffset = currentBucketWriterOffset[bucketId];
    // Determine if this bucket requires an "extra writer"
    int extraWriter = bucketId < (numPartitions % maxNumBuckets) ? 1 : 0;
    // The max number of writers this bucket can have
    int maxNumWritersPerBucket = (numPartitions / maxNumBuckets) + extraWriter;

    // Increment the writer offset or reset if it's reached the max for this bucket
    int nextOffset = currentOffset == maxNumWritersPerBucket - 1 ? 0 : currentOffset + 1;
    currentBucketWriterOffset[bucketId] = nextOffset;

    return bucketId + (maxNumBuckets * currentOffset);
  }
}
