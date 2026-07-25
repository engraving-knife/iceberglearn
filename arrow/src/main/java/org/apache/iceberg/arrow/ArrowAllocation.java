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
package org.apache.iceberg.arrow;

import org.apache.arrow.memory.RootAllocator;

/**
 * 文件级说明：Arrow 内存分配器的全局持有类。
 *
 * <p>所属模块：iceberg-arrow（将 Parquet 数据向量化读入 Arrow 内存列式格式的桥接模块， 位于 Iceberg 读取链路的中游，承接 core 的扫描任务、向下对接
 * Arrow 向量与 Parquet 读取器）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在类加载时创建一个全局共享的 {@link RootAllocator}，上限设为 {@link Long#MAX_VALUE} （即不主动设限，交由上游框架/引擎控制）。
 *   <li>对外暴露该根分配器，供 Arrow 向量读写、Parquet 向量化读取等场景申请堆外内存。
 * </ul>
 *
 * <p>设计意图：Arrow 的内存模型要求所有向量内存必须由 Allocator 分配并纳入引用计数管理。 集中持有一个 RootAllocator
 * 可以避免各处自行创建导致内存难以统一回收；静态初始化块 保证线程安全的单例创建。
 *
 * <p>上下游关系：被本模块内的向量化读取器（VectorizedArrowReader 等）以及外部引擎集成 （Spark/Flink 读取 Iceberg）调用以获取 Arrow 内存分配器。
 */
public class ArrowAllocation {
  static {
    ROOT_ALLOCATOR = new RootAllocator(Long.MAX_VALUE);
  }

  private static final RootAllocator ROOT_ALLOCATOR;

  private ArrowAllocation() {}

  /**
   * 返回全局共享的 Arrow 根内存分配器。
   *
   * @return 全局 {@link RootAllocator} 实例
   */
  public static RootAllocator rootAllocator() {
    return ROOT_ALLOCATOR;
  }
}
