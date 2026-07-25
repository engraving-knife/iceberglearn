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
import java.util.List;
import java.util.Map;
import org.apache.iceberg.io.FileIO;

/**
 * 表在某一时刻的数据快照。
 *
 * <p>所属模块：iceberg-api（表元数据核心抽象层）。
 *
 * <p>职责：表示表在某个时间点的完整数据状态，由一个或多个文件 manifest 组成，表的完整 内容即这些 manifest 中所有数据文件的并集。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>快照由表操作（如 {@link AppendFiles}、{@link RewriteFiles}）创建并提交，每个快照 持有唯一的快照 ID 与单调递增的序列号。
 *   <li>通过 parentId 形成快照链，支持时间旅行与增量扫描。
 *   <li>实现 {@link Serializable} 以支持序列化传递。
 * </ul>
 *
 * <p>上下游关系：由表元数据持有；被扫描、过期、回滚等操作使用。
 */
public interface Snapshot extends Serializable {
  /**
   * 返回本快照的序列号。
   *
   * <p>设计要点：序列号在快照提交时分配，用于增量扫描与数据/文件序列号过滤。
   *
   * @return 快照序列号
   */
  long sequenceNumber();

  /**
   * 返回本快照的 ID。
   *
   * @return 快照 ID
   */
  long snapshotId();

  /**
   * 返回本快照的父快照 ID。
   *
   * @return 父快照 ID，无父快照时返回 null
   */
  Long parentId();

  /**
   * 返回本快照的时间戳（毫秒）。
   *
   * <p>设计要点：时间戳由 {@link System#currentTimeMillis()} 产生，用于过期判断与时间旅行。
   *
   * @return 毫秒时间戳
   */
  long timestampMillis();

  /**
   * 返回本快照中所有数据与删除 manifest 的 {@link ManifestFile} 列表。
   *
   * @param io 用于读取存储文件的 {@link FileIO}
   * @return manifest 文件列表
   */
  List<ManifestFile> allManifests(FileIO io);

  /**
   * 返回本快照中所有数据 manifest 的 {@link ManifestFile} 列表。
   *
   * @param io 用于读取存储文件的 {@link FileIO}
   * @return 数据 manifest 文件列表
   */
  List<ManifestFile> dataManifests(FileIO io);

  /**
   * 返回本快照中所有删除 manifest 的 {@link ManifestFile} 列表。
   *
   * @param io 用于读取存储文件的 {@link FileIO}
   * @return 删除 manifest 文件列表
   */
  List<ManifestFile> deleteManifests(FileIO io);

  /**
   * 返回生成本快照的 {@link DataOperations 数据操作} 名称。
   *
   * @return 操作名，未知时返回 null
   * @see DataOperations
   */
  String operation();

  /**
   * 返回生成本快照的操作的概要信息映射。
   *
   * @return 字符串键值对概要
   */
  Map<String, String> summary();

  /**
   * 返回本快照中新增的所有数据文件。
   *
   * <p>设计要点：返回的文件含 file_path、file_format、partition、record_count、 file_size_in_bytes
   * 列，数据/文件序列号已填充，其余列为 null。
   *
   * @param io 用于读取存储文件的 {@link FileIO}
   * @return 本快照新增的数据文件迭代器
   */
  Iterable<DataFile> addedDataFiles(FileIO io);

  /**
   * 返回本快照中移除的所有数据文件。
   *
   * <p>设计要点：返回的文件含 file_path、file_format、partition、record_count、 file_size_in_bytes
   * 列，数据/文件序列号已填充，其余列为 null。
   *
   * @param io 用于读取存储文件的 {@link FileIO}
   * @return 本快照移除的数据文件迭代器
   */
  Iterable<DataFile> removedDataFiles(FileIO io);

  /**
   * 返回本快照中新增的所有删除文件。
   *
   * <p>设计要点：返回的文件含 file_path、file_format、partition、record_count、 file_size_in_bytes 列，其余列为 null。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由具体实现覆盖。
   *
   * @param io 用于读取存储文件的 {@link FileIO}
   * @return 本快照新增的删除文件迭代器
   */
  default Iterable<DeleteFile> addedDeleteFiles(FileIO io) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement addedDeleteFiles");
  }

  /**
   * 返回本快照中移除的所有删除文件。
   *
   * <p>设计要点：返回的文件含 file_path、file_format、partition、record_count、 file_size_in_bytes 列，其余列为 null。
   *
   * <p>默认实现：抛 {@link UnsupportedOperationException}，由具体实现覆盖。
   *
   * @param io 用于读取存储文件的 {@link FileIO}
   * @return 本快照移除的删除文件迭代器
   */
  default Iterable<DeleteFile> removedDeleteFiles(FileIO io) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement removedDeleteFiles");
  }

  /**
   * 返回本快照 manifest 列表文件的存储位置。
   *
   * @return manifest 列表文件位置，无独立文件时返回 null
   */
  String manifestListLocation();

  /**
   * 返回创建本快照时所用 schema 的 ID。
   *
   * <p>默认实现：返回 null（信息不可用时）。
   *
   * @return 与本快照关联的 schema ID
   */
  default Integer schemaId() {
    return null;
  }
}
