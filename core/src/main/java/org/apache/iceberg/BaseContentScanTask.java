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
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.util.ArrayUtil;

/**
 * 内容扫描任务的抽象基类：封装单个 {@link ContentFile} 及其相关 schema、分区 spec、 残余表达式，提供文件级读取、分片（split）与行数估算能力。
 *
 * <p>所属模块：iceberg-core，是 {@link ContentScanTask} 与 {@link SplittableScanTask} 在 core 侧的统一实现基类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有扫描所需的文件句柄、序列化后的 schema/spec 字符串与残余表达式，便于跨进程序列化。
 *   <li>懒解析 schema 与 PartitionSpec（双重检查锁），避免反序列化开销。
 *   <li>根据文件可拆分性与 splitOffsets 选择 {@link OffsetsAwareSplitScanTaskIterator} 或 {@link
 *       FixedSizeSplitScanTaskIterator} 进行切分。
 *   <li>提供基于文件大小与记录数的行数估算。
 * </ul>
 *
 * <p>设计意图：schemaString/specString 以 JSON 字符串形式存储，是因为扫描任务可能被 序列化到分布式节点执行，而 Schema/PartitionSpec
 * 含不可序列化的引用；解析延迟到首次访问， 既减少不必要的反序列化，也保证线程安全。泛型 {@code ThisT} 配合 {@code self()} 实现 "自类型"模式，使子类 split
 * 出来的任务类型保持一致。
 *
 * <p>上下游关系：被 {@link BaseFileScanTask} 等具体扫描任务继承；split 生成的子任务由 引擎层（Spark/Flink）的 task 调度器消费。
 *
 * @param <ThisT> 子类自身的任务类型
 * @param <F> 内容文件类型（如 {@link DataFile}、{@link DeleteFile}）
 */
abstract class BaseContentScanTask<ThisT extends ContentScanTask<F>, F extends ContentFile<F>>
    implements ContentScanTask<F>, SplittableScanTask<ThisT> {

  private final F file;
  private final String schemaString;
  private final String specString;
  private final ResidualEvaluator residuals;

  private transient volatile Schema schema = null;
  private transient volatile PartitionSpec spec = null;

  /**
   * 构造内容扫描任务。
   *
   * @param file 被扫描的内容文件
   * @param schemaString 表 schema 的 JSON 序列化字符串（用于懒解析）
   * @param specString 分区 spec 的 JSON 序列化字符串
   * @param residuals 残余表达式求值器，用于评估过滤条件在文件内部的剩余部分
   */
  BaseContentScanTask(F file, String schemaString, String specString, ResidualEvaluator residuals) {
    this.file = file;
    this.schemaString = schemaString;
    this.specString = specString;
    this.residuals = residuals;
  }

  /** 返回子类自身实例（自类型模式）。 */
  protected abstract ThisT self();

  /**
   * 创建一个指定偏移与长度的子任务（split）。
   *
   * @param parentTask 父任务，提供 schema/spec 等共享上下文
   * @param offset 文件内起始偏移
   * @param length 本次 split 读取的字节长度
   * @return 新的子任务
   */
  protected abstract ThisT newSplitTask(ThisT parentTask, long offset, long length);

  /** 返回本任务对应的文件。 */
  @Override
  public F file() {
    return file;
  }

  /**
   * 懒解析并返回表 schema。
   *
   * <p>逻辑：采用双重检查锁（DCL）模式，仅在首次访问时从 {@code schemaString} 反序列化， 之后缓存到 {@code schema} 字段。{@code
   * transient volatile} 保证多线程可见性与序列化安全。
   *
   * @return 表 schema
   */
  protected Schema schema() {
    if (schema == null) {
      synchronized (this) {
        if (schema == null) {
          this.schema = SchemaParser.fromJson(schemaString);
        }
      }
    }

    return schema;
  }

  /**
   * 懒解析并返回分区 spec。
   *
   * <p>逻辑：与 {@link #schema()} 同样采用 DCL，依赖 {@link #schema()} 先解析 schema， 再用 {@link
   * PartitionSpecParser#fromJson(Schema, String)} 完成 spec 绑定。
   *
   * @return 分区 spec
   */
  @Override
  public PartitionSpec spec() {
    if (spec == null) {
      synchronized (this) {
        if (spec == null) {
          this.spec = PartitionSpecParser.fromJson(schema(), specString);
        }
      }
    }
    return spec;
  }

  /** 整文件任务起始偏移为 0。 */
  @Override
  public long start() {
    return 0;
  }

  /** 任务读取长度等于文件本身大小。 */
  @Override
  public long length() {
    return file.fileSizeInBytes();
  }

  /**
   * 计算该文件分区在过滤条件下的残余表达式。
   *
   * @return 经 {@link ResidualEvaluator#residualFor(StructLike)} 针对本文件分区求得的残余
   */
  @Override
  public Expression residual() {
    return residuals.residualFor(file.partition());
  }

  /**
   * 估算本任务覆盖的行数。
   *
   * @return 估算行数
   */
  @Override
  public long estimatedRowsCount() {
    return estimateRowsCount(length(), file);
  }

  /**
   * 将当前任务按目标大小切分为多个子任务。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若文件格式可拆分（{@link org.apache.iceberg.FileFormat#isSplittable()}）：
   *       <ul>
   *         <li>若文件提供了严格递增的 splitOffsets，使用 {@link OffsetsAwareSplitScanTaskIterator}
   *             按文件内部块边界切分，避免读到块边界之外。
   *         <li>否则使用 {@link FixedSizeSplitScanTaskIterator} 按固定大小切分。
   *       </ul>
   *   <li>否则文件不可拆分（如某些压缩格式），返回不可再分的单元素列表。
   * </ul>
   *
   * @param targetSplitSize 目标分片大小（字节）
   * @return 子任务可迭代集合
   */
  @Override
  public Iterable<ThisT> split(long targetSplitSize) {
    if (file.format().isSplittable()) {
      long[] splitOffsets = splitOffsets(file);
      if (splitOffsets != null && ArrayUtil.isStrictlyAscending(splitOffsets)) {
        return () ->
            new OffsetsAwareSplitScanTaskIterator<>(
                self(), length(), splitOffsets, this::newSplitTask);
      } else {
        return () ->
            new FixedSizeSplitScanTaskIterator<>(
                self(), length(), targetSplitSize, this::newSplitTask);
      }
    }

    return ImmutableList.of(self());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("file", file().path())
        .add("partition_data", file().partition())
        .add("residual", residual())
        .toString();
  }

  /**
   * 静态工具：根据已读取长度与文件统计信息估算行数。
   *
   * <p>逻辑：用已扫描长度占（文件总大小 - 首个 split 偏移）的比例乘以文件记录数， 得到本 split 的近似行数。首个 split 偏移用于剔除文件头部不参与计数的部分。
   *
   * @param length 本次扫描的字节长度
   * @param file 内容文件
   * @return 估算行数
   */
  static long estimateRowsCount(long length, ContentFile<?> file) {
    long[] splitOffsets = splitOffsets(file);
    long splitOffset = splitOffsets != null ? splitOffsets[0] : 0L;
    double scannedFileFraction = ((double) length) / (file.fileSizeInBytes() - splitOffset);
    return (long) (scannedFileFraction * file.recordCount());
  }

  /**
   * 获取文件的 splitOffsets 数组，优先使用 {@link BaseFile} 的内部数组以避免装箱开销。
   *
   * @param file 内容文件
   * @return split 偏移数组，可能为 null
   */
  private static long[] splitOffsets(ContentFile<?> file) {
    if (file instanceof BaseFile) {
      return ((BaseFile<?>) file).splitOffsetArray();
    } else {
      return ArrayUtil.toLongArray(file.splitOffsets());
    }
  }
}
