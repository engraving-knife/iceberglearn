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
 * 文件级说明：基于等值删除的增量写入器接口。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：支持向不同 spec/partition 插入数据行（insert）、按完整行删除（delete）和 按等值字段删除（deleteKey），用于实现 MERGE-INTO 等场景下的
 * upsert 语义。
 *
 * <p>设计意图：equality-delete 通过等值字段（如主键）匹配并删除已有行。delete 写入完整行， deleteKey 仅写入等值字段值。两者在读取端都需要与数据文件做 join
 * 匹配，成本高于 position-delete， 但不需要知道被删行的精确位置。{@link BaseTaskWriter.BaseEqualityDeltaWriter} 是其内部实现。
 *
 * <p>上下游关系：由引擎的 MERGE/DELETE 操作调用；core 中由 {@link BaseTaskWriter.BaseEqualityDeltaWriter} 实现。
 *
 * @param <T> 行记录类型
 */
public interface EqualityDeltaWriter<T> extends Closeable {

  /**
   * 向指定 spec/partition 插入一行数据。
   *
   * @param row 数据记录
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   */
  void insert(T row, PartitionSpec spec, StructLike partition);

  /**
   * 按完整行删除指定 spec/partition 中的匹配行。
   *
   * <p>删除记录使用与插入行相同的 schema。
   *
   * @param row 删除记录
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   */
  void delete(T row, PartitionSpec spec, StructLike partition);

  /**
   * 按等值字段删除指定 spec/partition 中的匹配行。
   *
   * <p>删除键仅包含等值字段的值。
   *
   * @param key 删除键（仅含等值字段）
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   */
  void deleteKey(T key, PartitionSpec spec, StructLike partition);

  /**
   * 返回包含已写数据文件和删除文件的结果。仅在写入器关闭后有效。
   *
   * @return 写入结果
   */
  WriteResult result();
}
