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
package org.apache.iceberg.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.Set;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.StructLikeSet;

/**
 * 文件级说明：聚簇式多分区写入器抽象基类。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：向多个 spec/partition 写入记录，要求输入按 spec 和 partition 聚簇（即同一分区的记录 连续到达），任何时刻最多打开一个文件，以降低内存和句柄开销。
 *
 * <p>设计意图：与 {@link FanoutWriter} 互补。当上游能保证分区聚簇（如已排序的批处理写入）时， ClusteredWriter 只需维护一个当前
 * writer，内存开销远低于扇出模式。通过 completedSpecIds 和 completedPartitions 集合检测违反聚簇假设的记录并抛出异常，引导用户改用
 * FanoutWriter。 分区键在存入集合前深拷贝，防止上游复用 key 对象导致误判。
 *
 * <p>上下游关系：由 {@link ClusteredDataWriter}、{@link ClusteredEqualityDeleteWriter}、 {@link
 * ClusteredPositionDeleteWriter} 继承；实现 {@link PartitioningWriter} 接口。
 *
 * @param <T> 行记录类型
 * @param <R> 结果类型
 */
abstract class ClusteredWriter<T, R> implements PartitioningWriter<T, R> {

  private static final String NOT_CLUSTERED_ROWS_ERROR_MSG_TEMPLATE =
      "Incoming records violate the writer assumption that records are clustered by spec and "
          + "by partition within each spec. Either cluster the incoming records or switch to fanout writers.\n"
          + "Encountered records that belong to already closed files:\n";

  private final Set<Integer> completedSpecIds = Sets.newHashSet();

  private PartitionSpec currentSpec = null;
  private Comparator<StructLike> partitionComparator = null;
  private Set<StructLike> completedPartitions = null;
  private StructLike currentPartition = null;
  private FileWriter<T, R> currentWriter = null;

  private boolean closed = false;

  /** 为指定 spec/partition 创建新的 FileWriter，由子类实现。 */
  protected abstract FileWriter<T, R> newWriter(PartitionSpec spec, StructLike partition);

  /** 将单个 writer 的结果加入聚合，由子类实现。 */
  protected abstract void addResult(R result);

  /** 返回所有 writer 的聚合结果，由子类实现。 */
  protected abstract R aggregatedResult();

  /**
   * 写入一行记录到指定 spec/partition。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若 spec 变化：关闭当前 writer，记录已完成的 specId；若新 spec 已完成过则抛异常 （违反聚簇）；否则初始化新 spec
   *       的比较器、已完成分区集合，深拷贝分区键并创建 writer。
   *   <li>若 spec 不变但 partition 变化：关闭当前 writer，将旧分区加入已完成集合；若新分区 已完成过则抛异常；否则深拷贝新分区键并创建 writer。
   *   <li>spec 和 partition 均不变：直接写入当前 writer。
   * </ul>
   *
   * @param row 行记录
   * @param spec 分区规格
   * @param partition 分区值
   * @throws IllegalStateException 若记录违反聚簇假设（已关闭的分区再次出现）
   */
  @Override
  public void write(T row, PartitionSpec spec, StructLike partition) {
    if (!spec.equals(currentSpec)) {
      if (currentSpec != null) {
        closeCurrentWriter();
        completedSpecIds.add(currentSpec.specId());
        completedPartitions.clear();
      }

      if (completedSpecIds.contains(spec.specId())) {
        String errorCtx = String.format("spec %s", spec);
        throw new IllegalStateException(NOT_CLUSTERED_ROWS_ERROR_MSG_TEMPLATE + errorCtx);
      }

      StructType partitionType = spec.partitionType();

      this.currentSpec = spec;
      this.partitionComparator = Comparators.forType(partitionType);
      this.completedPartitions = StructLikeSet.create(partitionType);
      // copy the partition key as the key object may be reused
      this.currentPartition = StructCopy.copy(partition);
      this.currentWriter = newWriter(currentSpec, currentPartition);

    } else if (partition != currentPartition
        && partitionComparator.compare(partition, currentPartition) != 0) {
      closeCurrentWriter();
      completedPartitions.add(currentPartition);

      if (completedPartitions.contains(partition)) {
        String errorCtx =
            String.format("partition '%s' in spec %s", spec.partitionToPath(partition), spec);
        throw new IllegalStateException(NOT_CLUSTERED_ROWS_ERROR_MSG_TEMPLATE + errorCtx);
      }

      // copy the partition key as the key object may be reused
      this.currentPartition = StructCopy.copy(partition);
      this.currentWriter = newWriter(currentSpec, currentPartition);
    }

    currentWriter.write(row);
  }

  /** 关闭写入器，关闭当前 writer。 */
  @Override
  public void close() throws IOException {
    if (!closed) {
      closeCurrentWriter();
      this.closed = true;
    }
  }

  /** 关闭当前 writer 并收集结果。 */
  private void closeCurrentWriter() {
    if (currentWriter != null) {
      try {
        currentWriter.close();
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to close current writer", e);
      }

      addResult(currentWriter.result());

      this.currentWriter = null;
    }
  }

  /** 返回聚合结果，必须在 close 之后调用。 */
  @Override
  public final R result() {
    Preconditions.checkState(closed, "Cannot get result from unclosed writer");
    return aggregatedResult();
  }

  /**
   * 创建输出文件的辅助方法。
   *
   * @deprecated 将在 1.5.0 移除，子类应直接使用 OutputFileFactory
   */
  @Deprecated
  protected EncryptedOutputFile newOutputFile(
      OutputFileFactory fileFactory, PartitionSpec spec, StructLike partition) {
    Preconditions.checkArgument(
        spec.isUnpartitioned() || partition != null,
        "Partition must not be null when creating output file for partitioned spec");
    if (spec.isUnpartitioned() || partition == null) {
      return fileFactory.newOutputFile();
    } else {
      return fileFactory.newOutputFile(spec, partition);
    }
  }
}
