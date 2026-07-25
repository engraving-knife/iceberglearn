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

import java.util.List;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.BinaryType;
import org.apache.iceberg.types.Types.IntegerType;
import org.apache.iceberg.types.Types.ListType;
import org.apache.iceberg.types.Types.LongType;
import org.apache.iceberg.types.Types.MapType;
import org.apache.iceberg.types.Types.StringType;
import org.apache.iceberg.types.Types.StructType;

/**
 * 表 manifest 中列出的数据文件接口。
 *
 * <p>所属模块：iceberg-api（表数据文件抽象层）。
 *
 * <p>职责：定义一个数据文件（含数据/位置删除/等值删除三类内容）在 manifest 中持久化的 元数据字段及其 schema，包括文件路径、格式、记录数、文件大小、列级统计（大小、值计数、
 * null 计数、NaN 计数、上下界）、加密 key 元数据、可拆分偏移、等值删除字段 ID、排序 ID、 分区 spec ID 等。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>每个字段通过常量 {@link Types.NestedField} 定义固定的字段 ID（如 100=FILE_PATH、 134=CONTENT），保证 manifest
 *       文件格式跨版本稳定，便于前向/后向兼容。
 *   <li>字段 ID 预留了区间（100 起步，留出 ManifestEntry 的 ID 空间），新增字段时按 {@code NEXT ID TO ASSIGN} 顺序分配，避免 ID
 *       冲突。
 *   <li>列级统计与上下界用于查询时按谓词下推裁剪文件，减少 IO。
 * </ul>
 *
 * <p>上下游关系：继承 {@link ContentFile}；被 manifest 读写、扫描规划、查询优化等模块 广泛使用。
 */
public interface DataFile extends ContentFile<DataFile> {
  // fields for adding delete data files
  /** 文件内容类型字段：0=数据，1=位置删除，2=等值删除。 */
  Types.NestedField CONTENT =
      optional(
          134,
          "content",
          IntegerType.get(),
          "Contents of the file: 0=data, 1=position deletes, 2=equality deletes");
  /** 文件路径字段（含文件系统 scheme 的 URI）。 */
  Types.NestedField FILE_PATH =
      required(100, "file_path", StringType.get(), "Location URI with FS scheme");
  /** 文件格式字段：avro、orc 或 parquet。 */
  Types.NestedField FILE_FORMAT =
      required(101, "file_format", StringType.get(), "File format name: avro, orc, or parquet");
  /** 文件记录数字段。 */
  Types.NestedField RECORD_COUNT =
      required(103, "record_count", LongType.get(), "Number of records in the file");
  /** 文件总大小（字节）字段。 */
  Types.NestedField FILE_SIZE =
      required(104, "file_size_in_bytes", LongType.get(), "Total file size in bytes");
  /** 列 ID 到该列在磁盘上的总大小的映射。 */
  Types.NestedField COLUMN_SIZES =
      optional(
          108,
          "column_sizes",
          MapType.ofRequired(117, 118, IntegerType.get(), LongType.get()),
          "Map of column id to total size on disk");
  /** 列 ID 到该列值总数（含 null 与 NaN）的映射。 */
  Types.NestedField VALUE_COUNTS =
      optional(
          109,
          "value_counts",
          MapType.ofRequired(119, 120, IntegerType.get(), LongType.get()),
          "Map of column id to total count, including null and NaN");
  /** 列 ID 到该列 null 值计数的映射。 */
  Types.NestedField NULL_VALUE_COUNTS =
      optional(
          110,
          "null_value_counts",
          MapType.ofRequired(121, 122, IntegerType.get(), LongType.get()),
          "Map of column id to null value count");
  /** 列 ID 到该列 NaN 值计数的映射。 */
  Types.NestedField NAN_VALUE_COUNTS =
      optional(
          137,
          "nan_value_counts",
          MapType.ofRequired(138, 139, IntegerType.get(), LongType.get()),
          "Map of column id to number of NaN values in the column");
  /** 列 ID 到该列下界的映射（二进制编码）。 */
  Types.NestedField LOWER_BOUNDS =
      optional(
          125,
          "lower_bounds",
          MapType.ofRequired(126, 127, IntegerType.get(), BinaryType.get()),
          "Map of column id to lower bound");
  /** 列 ID 到该列上界的映射（二进制编码）。 */
  Types.NestedField UPPER_BOUNDS =
      optional(
          128,
          "upper_bounds",
          MapType.ofRequired(129, 130, IntegerType.get(), BinaryType.get()),
          "Map of column id to upper bound");
  /** 加密 key 元数据 blob 字段。 */
  Types.NestedField KEY_METADATA =
      optional(131, "key_metadata", BinaryType.get(), "Encryption key metadata blob");
  /** 文件可拆分偏移列表字段（用于并行读取同一文件的多个分片）。 */
  Types.NestedField SPLIT_OFFSETS =
      optional(
          132, "split_offsets", ListType.ofRequired(133, LongType.get()), "Splittable offsets");
  /** 等值删除所比较的字段 ID 列表字段。 */
  Types.NestedField EQUALITY_IDS =
      optional(
          135,
          "equality_ids",
          ListType.ofRequired(136, IntegerType.get()),
          "Equality comparison field IDs");
  /** 文件写入时使用的排序 ID 字段。 */
  Types.NestedField SORT_ORDER_ID =
      optional(140, "sort_order_id", IntegerType.get(), "Sort order ID");
  /** 文件所属分区 spec ID 字段。 */
  Types.NestedField SPEC_ID = optional(141, "spec_id", IntegerType.get(), "Partition spec ID");

  /** 分区字段的固定 ID。 */
  int PARTITION_ID = 102;
  /** 分区字段名。 */
  String PARTITION_NAME = "partition";
  /** 分区字段文档说明。 */
  String PARTITION_DOC = "Partition data tuple, schema based on the partition spec";
  // NEXT ID TO ASSIGN: 142

  /**
   * 根据 partition 类型构造数据文件 manifest 中使用的完整 struct 类型。
   *
   * <p>逻辑：将上述所有字段常量按固定顺序组装为 {@link StructType}，分区字段使用传入的 partitionType（ID 固定为 {@link
   * #PARTITION_ID}）。字段 ID 从 100 起，留出 ManifestEntry 的 ID 调整空间。
   *
   * @param partitionType 分区数据元组的 struct 类型
   * @return 数据文件在 manifest 中的 struct 类型
   */
  static StructType getType(StructType partitionType) {
    // IDs start at 100 to leave room for changes to ManifestEntry
    return StructType.of(
        CONTENT,
        FILE_PATH,
        FILE_FORMAT,
        SPEC_ID,
        required(PARTITION_ID, PARTITION_NAME, partitionType, PARTITION_DOC),
        RECORD_COUNT,
        FILE_SIZE,
        COLUMN_SIZES,
        VALUE_COUNTS,
        NULL_VALUE_COUNTS,
        NAN_VALUE_COUNTS,
        LOWER_BOUNDS,
        UPPER_BOUNDS,
        KEY_METADATA,
        SPLIT_OFFSETS,
        EQUALITY_IDS,
        SORT_ORDER_ID);
  }

  /**
   * 返回文件存储的内容类型。
   *
   * <p>默认实现：数据文件默认返回 {@link FileContent#DATA}。
   *
   * @return 文件内容类型，取值为 DATA、POSITION_DELETES 或 EQUALITY_DELETES 之一
   */
  @Override
  default FileContent content() {
    return FileContent.DATA;
  }

  /**
   * 返回等值删除所比较的字段 ID 列表。
   *
   * <p>默认实现：数据文件无等值删除字段，返回 null。
   *
   * @return 等值字段 ID 列表，数据文件返回 null
   */
  @Override
  default List<Integer> equalityFieldIds() {
    return null;
  }
}
