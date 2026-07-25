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

import java.io.IOException;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;

/**
 * 文件级说明：基于位置删除的增量写入器基础实现。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：实现 {@link PositionDeltaWriter} 接口，内部维护三个 {@link PartitioningWriter}： insert 数据写入器、update
 * 数据写入器和 position-delete 写入器，分别处理插入、更新和删除操作。
 *
 * <p>设计意图：insert 和 update 可以共用同一个 writer（双参数构造器）或使用独立 writer （三参数构造器）。共用时数据文件混合存放；独立时 insert 和
 * update 的数据文件分开， 便于下游区分新增与更新记录。delete 操作通过复用的 {@link PositionDelete} 对象传递 (path, pos, row)
 * 三元组，避免每次创建新对象。result() 将三个 writer 的结果聚合为 {@link WriteResult}。
 *
 * <p>上下游关系：由引擎集成层在 MERGE/UPDATE/DELETE 场景创建；内部的 PartitioningWriter 通常是 {@link ClusteredDataWriter}
 * + {@link ClusteredPositionDeleteWriter} 或对应的 Fanout 版本。
 *
 * @param <T> 行记录类型
 */
public class BasePositionDeltaWriter<T> implements PositionDeltaWriter<T> {

  private final PartitioningWriter<T, DataWriteResult> insertWriter;
  private final PartitioningWriter<T, DataWriteResult> updateWriter;
  private final PartitioningWriter<PositionDelete<T>, DeleteWriteResult> deleteWriter;
  private final PositionDelete<T> positionDelete;

  private boolean closed;

  /**
   * 构造 PositionDeltaWriter，insert 和 update 共用同一个数据写入器。
   *
   * @param dataWriter 数据写入器（同时用于 insert 和 update）
   * @param deleteWriter position-delete 写入器
   */
  public BasePositionDeltaWriter(
      PartitioningWriter<T, DataWriteResult> dataWriter,
      PartitioningWriter<PositionDelete<T>, DeleteWriteResult> deleteWriter) {
    this(dataWriter, dataWriter, deleteWriter);
  }

  /**
   * 构造 PositionDeltaWriter，insert 和 update 使用独立的数据写入器。
   *
   * @param insertWriter 插入数据写入器
   * @param updateWriter 更新数据写入器
   * @param deleteWriter position-delete 写入器
   */
  public BasePositionDeltaWriter(
      PartitioningWriter<T, DataWriteResult> insertWriter,
      PartitioningWriter<T, DataWriteResult> updateWriter,
      PartitioningWriter<PositionDelete<T>, DeleteWriteResult> deleteWriter) {
    Preconditions.checkArgument(insertWriter != null, "Insert writer cannot be null");
    Preconditions.checkArgument(updateWriter != null, "Update writer cannot be null");
    Preconditions.checkArgument(deleteWriter != null, "Delete writer cannot be null");

    this.insertWriter = insertWriter;
    this.updateWriter = updateWriter;
    this.deleteWriter = deleteWriter;
    this.positionDelete = PositionDelete.create();
  }

  /** 插入一行数据到 insertWriter。 */
  @Override
  public void insert(T row, PartitionSpec spec, StructLike partition) {
    insertWriter.write(row, spec, partition);
  }

  /** 插入更新行到 updateWriter。 */
  @Override
  public void update(T row, PartitionSpec spec, StructLike partition) {
    updateWriter.write(row, spec, partition);
  }

  /**
   * 删除指定位置的数据行。
   *
   * <p>逻辑：复用 positionDelete 对象设置 (path, pos, row)，再委托 deleteWriter 写入。
   *
   * @param path 数据文件路径
   * @param pos 行位置
   * @param row 被删除的行数据，可为 null
   * @param spec 分区规格
   * @param partition 分区值
   */
  @Override
  public void delete(CharSequence path, long pos, T row, PartitionSpec spec, StructLike partition) {
    positionDelete.set(path, pos, row);
    deleteWriter.write(positionDelete, spec, partition);
  }

  /**
   * 返回包含数据文件和删除文件的聚合结果。
   *
   * <p>逻辑：从 deleteWriter 获取删除结果；从 insertWriter/updateWriter 获取数据文件 （若共用则取一次，若独立则合并两者）；组装为
   * WriteResult。
   *
   * @return 写入结果
   * @throws IllegalStateException 若写入器尚未关闭
   */
  @Override
  public WriteResult result() {
    Preconditions.checkState(closed, "Cannot get result from unclosed writer");

    DeleteWriteResult deleteWriteResult = deleteWriter.result();

    return WriteResult.builder()
        .addDataFiles(dataFiles())
        .addDeleteFiles(deleteWriteResult.deleteFiles())
        .addReferencedDataFiles(deleteWriteResult.referencedDataFiles())
        .build();
  }

  /**
   * 获取数据文件集合。
   *
   * <p>逻辑：若 insertWriter 和 updateWriter 是同一对象，直接取其结果；否则合并两者的数据文件。
   */
  private Iterable<DataFile> dataFiles() {
    if (insertWriter == updateWriter) {
      DataWriteResult result = insertWriter.result();
      return result.dataFiles();
    } else {
      DataWriteResult insertWriteResult = insertWriter.result();
      DataWriteResult updateWriteResult = updateWriter.result();
      return Iterables.concat(insertWriteResult.dataFiles(), updateWriteResult.dataFiles());
    }
  }

  /** 关闭所有写入器（insert、update、delete）。 */
  @Override
  public void close() throws IOException {
    if (!closed) {
      insertWriter.close();
      updateWriter.close();
      deleteWriter.close();

      this.closed = true;
    }
  }
}
