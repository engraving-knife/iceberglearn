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

import org.apache.iceberg.io.CloseableIterable;

/**
 * 数据任务：直接以 {@link StructLike} 行的形式返回数据，而非告诉调用方去哪里读取数据。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：在 {@link FileScanTask} 基础上额外提供 {@link #rows()} 方法，把"扫描结果" 直接以行迭代器形式暴露，而不是返回文件路径+偏移让调用方自行读取。
 *
 * <p>设计意图：用于元数据表（如 snapshots、manifests 等系统表）这类不需要读取数据文件、 直接由 Iceberg
 * 内部构造结果行的场景。把这种"内存数据"任务与"文件读取"任务统一在 {@link FileScanTask} 体系下，方便引擎层用同一套调度通道处理。
 *
 * <p>上下游关系：由 core 模块的元数据表扫描实现产出，被引擎层当作普通扫描任务消费。
 */
public interface DataTask extends FileScanTask {
  /** 标识本任务为数据任务。 */
  @Override
  default boolean isDataTask() {
    return true;
  }

  /** 把自身作为 {@link DataTask} 返回。 */
  @Override
  default DataTask asDataTask() {
    return this;
  }

  /**
   * 返回本任务产出的行迭代器。
   *
   * @return 行的可关闭迭代器
   */
  CloseableIterable<StructLike> rows();
}
