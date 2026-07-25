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

/**
 * 内容文件扫描任务：表示对一个内容文件（数据文件/删除文件）中某段字节区间进行的扫描。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>暴露被扫描的 {@link ContentFile}、起始偏移 {@link #start()} 与扫描长度 {@link #length()}。
 *   <li>提供分区值（默认取自文件本身）与字节大小（默认等于扫描长度）。
 *   <li>提供 {@link #residual()} 残留表达式，用于在文件内部对行做进一步过滤。
 * </ul>
 *
 * <p>设计意图：同时继承 {@link ScanTask} 与 {@link PartitionScanTask}，把"文件扫描"和
 * "分区扫描"两个抽象合并为一个统一的任务视图，避免引擎层处理多套任务类型。泛型 F 允许 同一接口同时承载数据文件和删除文件。
 *
 * <p>上下游关系：由 {@link Scan#planFiles()} 规划产出，被引擎层切分为输入分片后读取。
 *
 * @param <F> 内容文件的 Java 类型
 */
public interface ContentScanTask<F extends ContentFile<F>> extends ScanTask, PartitionScanTask {
  /**
   * 返回本任务要扫描的内容文件。
   *
   * @return 被扫描的文件
   */
  F file();

  /**
   * 返回本任务所属分区值，默认取自文件自身的分区数据。
   *
   * @return 分区值
   */
  @Override
  default StructLike partition() {
    return file().partition();
  }

  /**
   * 返回本任务的字节大小，默认等于扫描长度 {@link #length()}。
   *
   * @return 字节大小
   */
  @Override
  default long sizeBytes() {
    return length();
  }

  /**
   * 返回本扫描区间在文件中的起始字节位置。
   *
   * @return 起始偏移
   */
  long start();

  /**
   * 返回从 {@link #start()} 起需要扫描的字节数。
   *
   * @return 扫描长度（字节）
   */
  long length();

  /**
   * 返回需要在本文件行上继续应用的残留过滤表达式。
   *
   * <p>逻辑：扫描的过滤条件会先用文件分区数据部分求值，剩余无法用分区数据消解的部分即为 残留表达式，需要由读取器在读取行后继续应用，以保证结果正确。
   *
   * @return 残留表达式
   */
  Expression residual();

  /**
   * 估算本扫描任务覆盖的行数。
   *
   * <p>逻辑：用扫描长度占文件有效数据长度（文件总大小减去首个 split 偏移）的比例， 乘以文件的记录数，得到估算行数。用于在不实际读取文件的情况下做调度与统计。
   *
   * @return 估算行数
   */
  @Override
  default long estimatedRowsCount() {
    long splitOffset = (file().splitOffsets() != null) ? file().splitOffsets().get(0) : 0L;
    double scannedFileFraction = ((double) length()) / (file().fileSizeInBytes() - splitOffset);
    return (long) (scannedFileFraction * file().recordCount());
  }
}
