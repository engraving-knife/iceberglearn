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

import java.io.Serializable;

/**
 * 扫描任务接口。
 *
 * <p>所属模块：iceberg-api（扫描任务抽象层）。
 *
 * <p>职责：表示扫描规划产生的一个可执行单元，提供该任务的字节数、估算行数、文件数等 统计信息，以及类型判断与转型的便捷方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable} 以支持在引擎中序列化传递（如 Spark 任务序列化）。
 *   <li>统计方法提供保守默认值（4MB / 10 万行 / 1 文件），具体实现按真实情况覆盖。
 *   <li>类型判断方法（isXxx/asXxx）以默认实现抛异常的方式提供"空对象"风格转型，避免 调用方到处 instanceof。
 * </ul>
 *
 * <p>上下游关系：是 {@link FileScanTask}、{@link CombinedScanTask}、{@link DataTask} 等 的父接口；被 {@link
 * ScanTaskGroup} 聚合，被引擎调度执行。
 */
public interface ScanTask extends Serializable {
  /**
   * 返回本扫描任务预期读取的字节数。
   *
   * <p>默认实现：4 MB。
   *
   * @return 待读取字节数
   */
  default long sizeBytes() {
    return 4 * 1028 * 1028; // 4 MB
  }

  /**
   * 返回本扫描任务预期产出的行数估算值。
   *
   * <p>默认实现：10 万行。
   *
   * @return 估算行数
   */
  default long estimatedRowsCount() {
    return 100_000;
  }

  /**
   * 返回本扫描任务将打开的文件数。
   *
   * <p>默认实现：1 个文件。
   *
   * @return 待打开文件数
   */
  default int filesCount() {
    return 1;
  }

  /** 判断是否为 {@link FileScanTask}，默认返回 false。 */
  default boolean isFileScanTask() {
    return false;
  }

  /**
   * 若本任务是 {@link FileScanTask} 则转型返回，否则抛异常。
   *
   * @return 转型后的 {@link FileScanTask}
   * @throws IllegalStateException 若本任务不是 {@link FileScanTask}
   */
  default FileScanTask asFileScanTask() {
    throw new IllegalStateException("Not a FileScanTask: " + this);
  }

  /** 判断是否为 {@link DataTask}，默认返回 false。 */
  default boolean isDataTask() {
    return false;
  }

  /**
   * 若本任务是 {@link DataTask} 则转型返回，否则抛异常。
   *
   * @return 转型后的 {@link DataTask}
   * @throws IllegalStateException 若本任务不是 {@link DataTask}
   */
  default DataTask asDataTask() {
    throw new IllegalStateException("Not a DataTask: " + this);
  }

  /**
   * 若本任务是 {@link CombinedScanTask} 则转型返回，否则抛异常。
   *
   * @return 转型后的 {@link CombinedScanTask}
   * @throws IllegalStateException 若本任务不是 {@link CombinedScanTask}
   */
  default CombinedScanTask asCombinedScanTask() {
    throw new IllegalStateException("Not a CombinedScanTask: " + this);
  }
}
