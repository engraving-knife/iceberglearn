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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;

/**
 * Manifest 条目接口：表示 manifest 文件中的一条记录，描述一个文件的状态变更。
 *
 * <p>所属模块：iceberg-core，是 manifest 文件的最小逻辑单元，连接快照与文件。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>记录文件状态（EXISTING/ADDED/DELETED）、所属快照 ID、数据序列号、文件序列号。
 *   <li>持有 {@link ContentFile}（数据文件或删除文件）的引用。
 *   <li>定义 manifest 条目的 schema（status、snapshot_id、sequence_number 等）。
 * </ul>
 *
 * <p>设计意图：通过 {@link Status} 枚举区分文件在快照中的角色——新增、删除或既有， 配合序列号实现"哪个文件在哪个快照被添加/删除"的精确追踪。{@code
 * dataSequenceNumber} 与 {@code fileSequenceNumber} 分离，因为 compaction 等操作可能产生序列号与快照不一致的文件。
 *
 * <p>上下游关系：由 {@code ManifestReader} 从 manifest 文件读取，被 {@code ManifestWriter} 写入；被扫描计划、过期快照、manifest
 * 合并等流程消费。
 *
 * @param <F> 内容文件类型
 */
interface ManifestEntry<F extends ContentFile<F>> {
  /** 文件状态枚举：EXISTING 既有、ADDED 新增、DELETED 删除。 */
  enum Status {
    EXISTING(0),
    ADDED(1),
    DELETED(2);

    private final int id;

    Status(int id) {
      this.id = id;
    }

    public int id() {
      return id;
    }
  }

  // ids for data-file columns are assigned from 1000
  Types.NestedField STATUS = required(0, "status", Types.IntegerType.get());
  Types.NestedField SNAPSHOT_ID = optional(1, "snapshot_id", Types.LongType.get());
  Types.NestedField SEQUENCE_NUMBER = optional(3, "sequence_number", Types.LongType.get());
  Types.NestedField FILE_SEQUENCE_NUMBER =
      optional(4, "file_sequence_number", Types.LongType.get());
  int DATA_FILE_ID = 2;
  // next ID to assign: 5

  static Schema getSchema(StructType partitionType) {
    return wrapFileSchema(DataFile.getType(partitionType));
  }

  static Schema wrapFileSchema(StructType fileType) {
    return new Schema(
        STATUS,
        SNAPSHOT_ID,
        SEQUENCE_NUMBER,
        FILE_SEQUENCE_NUMBER,
        required(DATA_FILE_ID, "data_file", fileType));
  }

  /** 返回文件状态（EXISTING/ADDED/DELETED）。 */
  Status status();

  /** 返回本条目是否为活跃状态（ADDED 或 EXISTING，即非删除）。 */
  default boolean isLive() {
    return status() == Status.ADDED || status() == Status.EXISTING;
  }

  /** 返回文件被添加到表时的快照 ID。 */
  Long snapshotId();

  /**
   * 设置本条目的快照 ID。
   *
   * @param snapshotId 快照 ID
   */
  void setSnapshotId(long snapshotId);

  /**
   * 返回文件的数据序列号。
   *
   * <p>数据序列号表示文件"应该生效"的序列号，与文件被添加时的快照序列号可能不同 （如 compaction 产生的新文件属于较旧的序列号）。文件被标记删除时数据序列号不变。
   *
   * <p>可能返回 null：读取旧版本 Iceberg 写的 v2 manifest 时，DELETED 条目可能未持久化此值。
   *
   * @return 数据序列号，可能为 null
   */
  Long dataSequenceNumber();

  /**
   * 设置本条目的数据序列号。
   *
   * @param dataSequenceNumber 数据序列号
   */
  void setDataSequenceNumber(long dataSequenceNumber);

  /**
   * 返回文件的文件序列号。
   *
   * <p>文件序列号是文件被添加时所属快照的序列号，在提交时分配，不可显式设置。 文件序列号一旦分配不再改变，在 existing/deleted 条目中必须保留。
   *
   * <p>可能返回 null：读取旧版本 Iceberg 写的 v2 manifest 时，EXISTING/DELETED 条目可能未持久化此值。
   *
   * @return 文件序列号，可能为 null
   */
  Long fileSequenceNumber();

  /**
   * 设置本条目的文件序列号。
   *
   * @param fileSequenceNumber 文件序列号
   */
  void setFileSequenceNumber(long fileSequenceNumber);

  /** 返回本条目对应的文件。 */
  F file();

  /** 返回本条目的深拷贝。 */
  ManifestEntry<F> copy();

  /** 返回本条目的深拷贝，但去除文件统计信息以节省内存。 */
  ManifestEntry<F> copyWithoutStats();
}
