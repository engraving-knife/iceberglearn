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
 * 文件级说明：基于位置删除的增量写入器接口。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：支持向不同 spec/partition 插入数据行（insert）和按位置删除行（delete）， 用于实现 COPY-ON-WRITE 或 MERGE-INTO 等场景下的
 * upsert 语义。
 *
 * <p>设计意图：position-delete 通过"数据文件路径 + 行号"定位被删除的行，适合上游已知精确位置的场景 （如 CDC update）。update 方法是 insert
 * 的语义别名，调用方需自行调用 delete 删除旧行位置。 delete 的 row 参数可选，用于在删除文件中保存被删行的原始数据（供 CDC 下游使用）。
 *
 * <p>上下游关系：由 {@link BasePositionDeltaWriter} 实现；被引擎的 MERGE/UPDATE 操作调用。
 *
 * @param <T> 行记录类型
 */
public interface PositionDeltaWriter<T> extends Closeable {

  /**
   * 向指定 spec/partition 插入一行数据。
   *
   * @param row 数据记录
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   */
  void insert(T row, PartitionSpec spec, StructLike partition);

  /**
   * 插入已有行的新版本（update 语义）。
   *
   * <p>此方法让写入器区分新增与更新。调用方必须另行调用 {@link #delete(CharSequence, long, PartitionSpec, StructLike)}
   * 删除旧行的位置。
   *
   * @param row 已有行的新版本
   * @param spec 新分区规格
   * @param partition 新分区值，非分区表传 null
   */
  default void update(T row, PartitionSpec spec, StructLike partition) {
    insert(row, spec, partition);
  }

  /**
   * 在指定 spec/partition 中删除某个位置（不保存被删行数据）。
   *
   * @param path 数据文件路径
   * @param pos 行位置
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   */
  default void delete(CharSequence path, long pos, PartitionSpec spec, StructLike partition) {
    delete(path, pos, null, spec, partition);
  }

  /**
   * 在指定 spec/partition 中删除某个位置，并在删除文件中记录被删行数据。
   *
   * @param path 数据文件路径
   * @param pos 行位置
   * @param row 被删除的行数据，可为 null
   * @param spec 分区规格
   * @param partition 分区值，非分区表传 null
   */
  void delete(CharSequence path, long pos, T row, PartitionSpec spec, StructLike partition);

  /**
   * 返回包含已写数据文件和删除文件的结果。仅在写入器关闭后有效。
   *
   * @return 写入结果
   */
  WriteResult result();
}
