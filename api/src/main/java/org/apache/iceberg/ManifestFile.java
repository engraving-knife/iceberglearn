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

import java.nio.ByteBuffer;
import java.util.List;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：清单文件（manifest file）接口，可被扫描以发现表中的数据文件。
 *
 * <p>所属模块：iceberg-api（核心接口层，由 core 实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>描述一个 manifest 文件的全部元信息：路径、长度、分区 spec ID、内容类型（数据/删除）、 序列号、快照 ID、文件计数与行计数、分区字段统计、加密元数据等。
 *   <li>定义 manifest 文件的 {@link Schema}（字段 ID 500~519），用于 manifest 列表文件 （manifest list）的序列化。
 *   <li>提供文件拷贝能力与分区字段统计（{@link PartitionFieldSummary}）。
 * </ul>
 *
 * <p>设计意图：manifest 是 Iceberg 三层元数据（metadata file → manifest list → manifest → data file） 中的关键中间层。每个
 * manifest 对应一个分区 spec，记录该 spec 下一批数据/删除文件的条目， 并附带分区字段的上下界等统计信息，便于扫描时按分区裁剪。序列号与快照 ID 用于增量扫描与
 * 快照过期。字段以固定 ID（500+）持久化，保证跨版本兼容。
 *
 * <p>上下游关系：由 manifest list 引用，由 core 模块的 manifest 读取器解析； 被 {@link TableScan} 等扫描规划组件使用以定位数据文件。
 */
public interface ManifestFile {
  Types.NestedField PATH =
      required(500, "manifest_path", Types.StringType.get(), "Location URI with FS scheme");
  Types.NestedField LENGTH =
      required(501, "manifest_length", Types.LongType.get(), "Total file size in bytes");
  Types.NestedField SPEC_ID =
      required(502, "partition_spec_id", Types.IntegerType.get(), "Spec ID used to write");
  Types.NestedField MANIFEST_CONTENT =
      optional(
          517, "content", Types.IntegerType.get(), "Contents of the manifest: 0=data, 1=deletes");
  Types.NestedField SEQUENCE_NUMBER =
      optional(
          515,
          "sequence_number",
          Types.LongType.get(),
          "Sequence number when the manifest was added");
  Types.NestedField MIN_SEQUENCE_NUMBER =
      optional(
          516,
          "min_sequence_number",
          Types.LongType.get(),
          "Lowest sequence number in the manifest");
  Types.NestedField SNAPSHOT_ID =
      optional(
          503, "added_snapshot_id", Types.LongType.get(), "Snapshot ID that added the manifest");
  Types.NestedField ADDED_FILES_COUNT =
      optional(504, "added_data_files_count", Types.IntegerType.get(), "Added entry count");
  Types.NestedField EXISTING_FILES_COUNT =
      optional(505, "existing_data_files_count", Types.IntegerType.get(), "Existing entry count");
  Types.NestedField DELETED_FILES_COUNT =
      optional(506, "deleted_data_files_count", Types.IntegerType.get(), "Deleted entry count");
  Types.NestedField ADDED_ROWS_COUNT =
      optional(512, "added_rows_count", Types.LongType.get(), "Added rows count");
  Types.NestedField EXISTING_ROWS_COUNT =
      optional(513, "existing_rows_count", Types.LongType.get(), "Existing rows count");
  Types.NestedField DELETED_ROWS_COUNT =
      optional(514, "deleted_rows_count", Types.LongType.get(), "Deleted rows count");
  Types.StructType PARTITION_SUMMARY_TYPE =
      Types.StructType.of(
          required(
              509,
              "contains_null",
              Types.BooleanType.get(),
              "True if any file has a null partition value"),
          optional(
              518,
              "contains_nan",
              Types.BooleanType.get(),
              "True if any file has a nan partition value"),
          optional(
              510, "lower_bound", Types.BinaryType.get(), "Partition lower bound for all files"),
          optional(
              511, "upper_bound", Types.BinaryType.get(), "Partition upper bound for all files"));
  Types.NestedField PARTITION_SUMMARIES =
      optional(
          507,
          "partitions",
          Types.ListType.ofRequired(508, PARTITION_SUMMARY_TYPE),
          "Summary for each partition");
  Types.NestedField KEY_METADATA =
      optional(519, "key_metadata", Types.BinaryType.get(), "Encryption key metadata blob");
  // next ID to assign: 520

  Schema SCHEMA =
      new Schema(
          PATH,
          LENGTH,
          SPEC_ID,
          MANIFEST_CONTENT,
          SEQUENCE_NUMBER,
          MIN_SEQUENCE_NUMBER,
          SNAPSHOT_ID,
          ADDED_FILES_COUNT,
          EXISTING_FILES_COUNT,
          DELETED_FILES_COUNT,
          ADDED_ROWS_COUNT,
          EXISTING_ROWS_COUNT,
          DELETED_ROWS_COUNT,
          PARTITION_SUMMARIES,
          KEY_METADATA);

  /** 返回 manifest 文件的 {@link Schema}，用于 manifest list 的序列化与反序列化。 */
  static Schema schema() {
    return SCHEMA;
  }

  /** 返回 manifest 文件的完全限定路径，可直接用于构造 Hadoop {@code Path}。 */
  String path();

  /** 返回 manifest 文件的长度（字节数）。 */
  long length();

  /** 返回写入该 manifest 文件时所使用的 {@link PartitionSpec} 的 ID。 */
  int partitionSpecId();

  /** 返回该 manifest 存储的内容类型：{@link ManifestContent#DATA}（数据）或 {@link ManifestContent#DELETES}（删除）。 */
  ManifestContent content();

  /** 返回添加该 manifest 文件的提交所对应的序列号。 */
  long sequenceNumber();

  /** 返回该 manifest 中所有存活文件的最小数据序列号。 */
  long minSequenceNumber();

  /** 返回将该 manifest 文件添加到表元数据的快照 ID。 */
  Long snapshotId();

  /**
   * 返回该 manifest 是否包含状态为 ADDED 的条目；若计数未知也返回 true。
   *
   * @return 是否包含 ADDED 状态的条目
   */
  default boolean hasAddedFiles() {
    return addedFilesCount() == null || addedFilesCount() > 0;
  }

  /** 返回该 manifest 中状态为 ADDED 的数据文件数量。 */
  Integer addedFilesCount();

  /** 返回该 manifest 中所有状态为 ADDED 的数据文件的行数总和。 */
  Long addedRowsCount();

  /**
   * 返回该 manifest 是否包含状态为 EXISTING 的条目；若计数未知也返回 true。
   *
   * @return 是否包含 EXISTING 状态的条目
   */
  default boolean hasExistingFiles() {
    return existingFilesCount() == null || existingFilesCount() > 0;
  }

  /** 返回该 manifest 中状态为 EXISTING 的数据文件数量。 */
  Integer existingFilesCount();

  /** 返回该 manifest 中所有状态为 EXISTING 的数据文件的行数总和。 */
  Long existingRowsCount();

  /**
   * 返回该 manifest 是否包含状态为 DELETED 的条目；若计数未知也返回 true。
   *
   * @return 是否包含 DELETED 状态的条目
   */
  default boolean hasDeletedFiles() {
    return deletedFilesCount() == null || deletedFilesCount() > 0;
  }

  /** 返回该 manifest 中状态为 DELETED 的数据文件数量。 */
  Integer deletedFilesCount();

  /** 返回该 manifest 中所有状态为 DELETED 的数据文件的行数总和。 */
  Long deletedRowsCount();

  /**
   * 返回 {@link PartitionFieldSummary 分区字段统计}列表。
   *
   * <p>每个统计对应 manifest 文件分区 spec 中的一个字段（按顺序）。例如分区 spec [ ts_day=date(ts), type=identity(type) ] 将有
   * 2 个统计：第一个对应 ts_day 分区字段， 第二个对应 type 分区字段。
   *
   * @return 分区字段统计列表，每个元素对应 manifest spec 中的一个分区字段
   */
  List<PartitionFieldSummary> partitions();

  /** 返回该 manifest 文件的加密元数据；若文件为明文存储则返回 null。 */
  default ByteBuffer keyMetadata() {
    return null;
  }

  /**
   * 拷贝本 {@link ManifestFile manifest 文件}。读取器可能复用 manifest 文件实例， 应使用本方法做防御性拷贝。
   *
   * @return 本 manifest 文件的拷贝
   */
  ManifestFile copy();

  /** 文件级说明：manifest 文件中单个分区字段值的统计摘要（内部接口）。 */
  interface PartitionFieldSummary {
    /** 返回该分区字段在 manifest 中对应的 {@link Types.StructType} 类型定义。 */
    static Types.StructType getType() {
      return PARTITION_SUMMARY_TYPE;
    }

    /** 返回 manifest 中是否至少有一个数据文件在该分区字段上取值为 null。 */
    boolean containsNull();

    /**
     * 返回 manifest 中是否至少有一个数据文件在该分区字段上取值为 NaN；若信息不存在则返回 null。
     *
     * <p>默认返回 null 以保证向后兼容。
     */
    default Boolean containsNaN() {
      return null;
    }

    /** 返回一个 {@link ByteBuffer}，包含序列化后小于该字段所有值的一个下界。 */
    ByteBuffer lowerBound();

    /** 返回一个 {@link ByteBuffer}，包含序列化后大于该字段所有值的一个上界。 */
    ByteBuffer upperBound();

    /**
     * 拷贝本 {@link PartitionFieldSummary 统计摘要}。读取器可能复用实例，应使用本方法做防御性拷贝。
     *
     * @return 本分区字段统计摘要的拷贝
     */
    PartitionFieldSummary copy();
  }
}
