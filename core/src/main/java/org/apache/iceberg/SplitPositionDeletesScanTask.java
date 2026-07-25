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
package org.apache.iceberg;

import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * 位置删除扫描任务的分片实现（iceberg-core 扫描层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将一个 {@link PositionDeletesScanTask} 按偏移量与长度切分为更小的分片；
 *   <li>实现 {@link MergeableScanTask}，支持将相邻分片重新合并以减少任务碎片。
 * </ul>
 *
 * <p>设计意图：分片用于并行扫描删除文件的不同区间，提升读取并发度； 合并能力则在小分片过多时将连续区间重新组合，平衡并行度与调度开销。
 *
 * <p>上下游关系：由位置删除扫描的 split 逻辑创建，被扫描任务调度器消费； 依赖父任务提供文件、分区规格、残余表达式等信息。
 */
class SplitPositionDeletesScanTask
    implements PositionDeletesScanTask, MergeableScanTask<PositionDeletesScanTask> {

  private final PositionDeletesScanTask parentTask;
  private final long offset;
  private final long length;

  /**
   * 构造一个分片任务。
   *
   * @param parentTask 被切分的父任务
   * @param offset 该分片在删除文件中的起始偏移量
   * @param length 该分片的字节长度
   */
  protected SplitPositionDeletesScanTask(
      PositionDeletesScanTask parentTask, long offset, long length) {
    this.parentTask = parentTask;
    this.offset = offset;
    this.length = length;
  }

  /** 返回该分片对应的删除文件（委托给父任务）。 */
  @Override
  public DeleteFile file() {
    return parentTask.file();
  }

  /** 返回该分片对应的分区规格（委托给父任务）。 */
  @Override
  public PartitionSpec spec() {
    return parentTask.spec();
  }

  /** 返回该分片在文件中的起始偏移量。 */
  @Override
  public long start() {
    return offset;
  }

  /** 返回该分片的字节长度。 */
  @Override
  public long length() {
    return length;
  }

  /** 返回该分片的残余表达式（委托给父任务）。 */
  @Override
  public Expression residual() {
    return parentTask.residual();
  }

  /**
   * 判断当前分片是否可与另一个扫描任务合并。
   *
   * <p>逻辑：仅当对方同为 {@link SplitPositionDeletesScanTask}、指向同一删除文件、 且当前分片末尾恰好与对方起始偏移量衔接时，才可合并。
   *
   * @param other 待判断的另一个扫描任务
   * @return 可合并返回 true
   */
  @Override
  public boolean canMerge(org.apache.iceberg.ScanTask other) {
    if (other instanceof SplitPositionDeletesScanTask) {
      SplitPositionDeletesScanTask that = (SplitPositionDeletesScanTask) other;
      return file().equals(that.file()) && offset + length == that.start();
    } else {
      return false;
    }
  }

  /**
   * 将当前分片与另一个相邻分片合并为更大的分片。
   *
   * @param other 相邻的下一个分片，须已通过 {@link #canMerge} 校验
   * @return 合并后的新 {@link SplitPositionDeletesScanTask}
   */
  @Override
  public SplitPositionDeletesScanTask merge(org.apache.iceberg.ScanTask other) {
    SplitPositionDeletesScanTask that = (SplitPositionDeletesScanTask) other;
    return new SplitPositionDeletesScanTask(parentTask, offset, length + that.length());
  }

  /** 返回该分片的字符串描述。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("file", file().path())
        .add("partition_data", file().partition())
        .add("offset", offset)
        .add("length", length)
        .add("residual", residual())
        .toString();
  }
}
