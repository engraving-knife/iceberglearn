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

/**
 * 表示一个 Puffin 格式的统计文件，可用于更高效地读取表数据。
 *
 * <p>所属模块：iceberg-api（表元数据抽象层）。
 *
 * <p>职责：描述一个持久化在 Puffin 文件中的统计信息文件，包括其来源快照 ID、文件路径、 文件大小、Puffin footer 大小，以及文件内包含的 blob 元数据列表。
 *
 * <p>设计意图：统计信息是"信息性"的，读取方可选择忽略；统计支持不是正确读取表的前提。 通过本接口将统计文件与表快照关联，便于按快照查找统计。
 *
 * <p>上下游关系：被表元数据持有；由 {@link UpdateStatistics} 更新；被查询优化读取。
 */
public interface StatisticsFile {
  /** 返回计算该统计所基于的 Iceberg 表快照 ID。 */
  long snapshotId();

  /** 返回统计文件的完全限定路径，可用于构造 Hadoop Path，永不返回 null。 */
  String path();

  /** 返回统计文件大小（字节）。 */
  long fileSizeInBytes();

  /** 返回 Puffin footer 的大小（字节）。 */
  long fileFooterSizeInBytes();

  /** 返回文件中包含的统计 blob 元数据列表，永不返回 null。 */
  List<BlobMetadata> blobMetadata();
}
