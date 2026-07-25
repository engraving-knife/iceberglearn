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

import java.util.List;
import java.util.Map;

/**
 * 统计信息或索引 blob 的元数据接口。
 *
 * <p>所属模块：iceberg-api（表元数据抽象层）。
 *
 * <p>职责：描述一个持久化在 {@link StatisticsFile} 中的 blob 的基本信息，包括其类型、 来源快照、参与计算的列字段 ID 列表及附加属性，供查询规划时按需读取对应
 * blob。
 *
 * <p>设计意图：将 blob 的元信息与 blob 二进制内容分离存储，使规划阶段无需读取大块 二进制即可判断是否需要加载某个 blob（如 Bloom Filter / 列统计）。
 *
 * <p>上下游关系：由 {@link StatisticsFile} 持有一组 {@code BlobMetadata}；被 core 模块 在写入统计文件时构造，在查询优化时读取。
 */
public interface BlobMetadata {
  /** 返回 blob 的类型标识，永不返回 null。 */
  String type();

  /** 返回计算该 blob 所基于的 Iceberg 表快照 ID。 */
  long sourceSnapshotId();

  /** 返回计算该 blob 所基于的 Iceberg 表快照序列号。 */
  long sourceSnapshotSequenceNumber();

  /** 返回参与计算该 blob 的字段 ID 有序列表，永不返回 null。 */
  List<Integer> fields();

  /** 返回该 blob 的附加属性（具体内容由 blob 类型决定），永不返回 null。 */
  Map<String, String> properties();
}
