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

import java.io.Closeable;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;

/**
 * 文件级说明：多分区文件写入器接口。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：向多个不同的 spec/partition 写入数据或删除记录，并在关闭后返回聚合结果。
 *
 * <p>设计意图：与 {@link FileWriter} 不同，本接口不限于单个 spec/partition，每条记录写入时 需显式指定目标 spec 和 partition。实现类（如
 * {@link ClusteredWriter}、{@link FanoutWriter}） 内部通常为每个 spec/partition 维护一个 FileWriter。
 *
 * <p>上下游关系：由 {@link BasePositionDeltaWriter} 作为 data/delete 写入器使用； 实现类包括 {@link ClusteredWriter} 和
 * {@link FanoutWriter} 两大系列。
 *
 * @param <T> 行记录类型
 * @param <R> 结果类型
 */
public interface PartitioningWriter<T, R> extends Closeable {

  /**
   * 将一行记录写入指定的 spec/partition。
   *
   * @param row 数据或删除记录
   * @param spec 分区规格
   * @param partition 分区值，spec 为非分区表时传 null
   */
  void write(T row, PartitionSpec spec, StructLike partition);

  /**
   * 返回包含已写文件信息的结果。仅在写入器关闭后有效。
   *
   * @return 写入结果
   */
  R result();
}
