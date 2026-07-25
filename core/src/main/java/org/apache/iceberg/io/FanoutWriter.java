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
import java.util.Map;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.StructLikeMap;

/**
 * 文件级说明：扇出式多分区写入器抽象基类。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：向多个 spec/partition 写入记录，为每个已出现的 spec/partition 对保持一个打开的 FileWriter，直到整体关闭。
 *
 * <p>设计意图：与 {@link ClusteredWriter} 相反，FanoutWriter 不要求输入按 spec/partition 聚簇，
 * 适用于流式写入等无法预排序的场景。代价是同时打开大量文件句柄，内存开销较高。分区键在放入 StructLikeMap 前通过 {@link StructCopy#copy}
 * 深拷贝，因为上游可能复用同一个 key 对象。
 *
 * <p>上下游关系：由 {@link FanoutDataWriter}、{@link FanoutPositionOnlyDeleteWriter} 继承； 实现 {@link
 * PartitioningWriter} 接口，被 {@link BasePositionDeltaWriter} 使用。
 *
 * @param <T> 行记录类型
 * @param <R> 结果类型
 */
abstract class FanoutWriter<T, R> implements PartitioningWriter<T, R> {

  private final Map<Integer, StructLikeMap<FileWriter<T, R>>> writers = Maps.newHashMap();
  private boolean closed = false;

  /** 为指定 spec/partition 创建新的 FileWriter，由子类实现。 */
  protected abstract FileWriter<T, R> newWriter(PartitionSpec spec, StructLike partition);

  /** 将单个 writer 的结果加入聚合，由子类实现。 */
  protected abstract void addResult(R result);

  /** 返回所有 writer 的聚合结果，由子类实现。 */
  protected abstract R aggregatedResult();

  /**
   * 将一行记录写入指定 spec/partition。
   *
   * <p>逻辑：查找或创建对应 spec/partition 的 FileWriter，委托写入。
   *
   * @param row 行记录
   * @param spec 分区规格
   * @param partition 分区值
   */
  @Override
  public void write(T row, PartitionSpec spec, StructLike partition) {
    FileWriter<T, R> writer = writer(spec, partition);
    writer.write(row);
  }

  /**
   * 查找或创建指定 spec/partition 的 FileWriter。
   *
   * <p>逻辑：按 specId 在外层 Map 中查找内层 StructLikeMap；若不存在则创建。在内层按 partition 查找 writer；若不存在则深拷贝分区键后创建新
   * writer 并存入。
   *
   * @param spec 分区规格
   * @param partition 分区值（可能被上游复用，需拷贝后存储）
   * @return 对应的 FileWriter
   */
  private FileWriter<T, R> writer(PartitionSpec spec, StructLike partition) {
    Map<StructLike, FileWriter<T, R>> specWriters =
        writers.computeIfAbsent(spec.specId(), id -> StructLikeMap.create(spec.partitionType()));
    FileWriter<T, R> writer = specWriters.get(partition);

    if (writer == null) {
      // copy the partition key as the key object may be reused
      StructLike copiedPartition = StructCopy.copy(partition);
      writer = newWriter(spec, copiedPartition);
      specWriters.put(copiedPartition, writer);
    }

    return writer;
  }

  /** 关闭写入器，关闭所有分区的 writer 并聚合结果。 */
  @Override
  public void close() throws IOException {
    if (!closed) {
      closeWriters();
      this.closed = true;
    }
  }

  /**
   * 关闭所有 writer 并收集结果。
   *
   * <p>逻辑：遍历每个 spec 下的每个 partition writer，逐一关闭并调用 addResult 收集结果， 然后清空内层和外层 Map。
   */
  private void closeWriters() throws IOException {
    for (Map<StructLike, FileWriter<T, R>> specWriters : writers.values()) {
      for (FileWriter<T, R> writer : specWriters.values()) {
        writer.close();
        addResult(writer.result());
      }

      specWriters.clear();
    }

    writers.clear();
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
