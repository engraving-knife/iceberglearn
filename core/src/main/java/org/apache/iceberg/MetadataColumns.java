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

import java.util.Map;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.NestedField;

/**
 * 元数据列定义：集中声明 Iceberg 表的隐藏列（_file、_pos、_deleted 等）及其字段 ID。
 *
 * <p>所属模块：iceberg-core，定义引擎层在扫描时可访问的元数据列。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>声明面向用户的元数据列：_file（文件路径）、_pos（行位置）、_deleted（是否删除）、 _spec_id（分区 spec ID）、_partition（分区值）。
 *   <li>声明面向删除文件的内部列：file_path、pos、row、_change_type 等。
 *   <li>提供判断某列名/ID 是否为元数据列的工具方法。
 * </ul>
 *
 * <p>设计意图：元数据列 ID 从 {@code Integer.MAX_VALUE} 递减分配，避免与用户列 ID 冲突； 分区列类型依赖表的所有 spec，故通过 {@link
 * #metadataColumn(Table, String)} 动态构建。 区分"元数据列"（1-100 区段）与"保留列"（101-200 区段）两段 ID 空间。
 *
 * <p>上下游关系：被扫描计划、引擎层（Spark/Flink）在投影元数据列时引用； {@link #isMetadataColumn(int)} 被 schema 处理逻辑用于排除元数据列。
 */
public class MetadataColumns {

  private MetadataColumns() {}

  // IDs Integer.MAX_VALUE - (1-100) are used for metadata columns
  public static final int FILE_PATH_COLUMN_ID = Integer.MAX_VALUE - 1;
  public static final String FILE_PATH_COLUMN_DOC = "Path of the file in which a row is stored";
  public static final NestedField FILE_PATH =
      NestedField.required(
          FILE_PATH_COLUMN_ID, "_file", Types.StringType.get(), FILE_PATH_COLUMN_DOC);
  public static final NestedField ROW_POSITION =
      NestedField.required(
          Integer.MAX_VALUE - 2,
          "_pos",
          Types.LongType.get(),
          "Ordinal position of a row in the source data file");
  public static final NestedField IS_DELETED =
      NestedField.required(
          Integer.MAX_VALUE - 3,
          "_deleted",
          Types.BooleanType.get(),
          "Whether the row has been deleted");
  public static final int SPEC_ID_COLUMN_ID = Integer.MAX_VALUE - 4;
  public static final String SPEC_ID_COLUMN_DOC = "Spec ID used to track the file containing a row";
  public static final NestedField SPEC_ID =
      NestedField.required(
          SPEC_ID_COLUMN_ID, "_spec_id", Types.IntegerType.get(), SPEC_ID_COLUMN_DOC);
  // the partition column type is not static and depends on all specs in the table
  public static final int PARTITION_COLUMN_ID = Integer.MAX_VALUE - 5;
  public static final String PARTITION_COLUMN_NAME = "_partition";
  public static final String PARTITION_COLUMN_DOC = "Partition to which a row belongs to";

  // IDs Integer.MAX_VALUE - (101-200) are used for reserved columns
  public static final NestedField DELETE_FILE_PATH =
      NestedField.required(
          Integer.MAX_VALUE - 101,
          "file_path",
          Types.StringType.get(),
          "Path of a file in which a deleted row is stored");
  public static final NestedField DELETE_FILE_POS =
      NestedField.required(
          Integer.MAX_VALUE - 102,
          "pos",
          Types.LongType.get(),
          "Ordinal position of a deleted row in the data file");
  public static final String DELETE_FILE_ROW_FIELD_NAME = "row";
  public static final int DELETE_FILE_ROW_FIELD_ID = Integer.MAX_VALUE - 103;
  public static final String DELETE_FILE_ROW_DOC = "Deleted row values";
  public static final NestedField CHANGE_TYPE =
      NestedField.required(
          Integer.MAX_VALUE - 104,
          "_change_type",
          Types.StringType.get(),
          "Record type in changelog");
  public static final NestedField CHANGE_ORDINAL =
      NestedField.optional(
          Integer.MAX_VALUE - 105,
          "_change_ordinal",
          Types.IntegerType.get(),
          "Change ordinal in changelog");
  public static final NestedField COMMIT_SNAPSHOT_ID =
      NestedField.optional(
          Integer.MAX_VALUE - 106,
          "_commit_snapshot_id",
          Types.LongType.get(),
          "Commit snapshot ID");

  private static final Map<String, NestedField> META_COLUMNS =
      ImmutableMap.of(
          FILE_PATH.name(), FILE_PATH,
          ROW_POSITION.name(), ROW_POSITION,
          IS_DELETED.name(), IS_DELETED,
          SPEC_ID.name(), SPEC_ID);

  private static final Set<Integer> META_IDS =
      ImmutableSet.of(
          FILE_PATH.fieldId(),
          ROW_POSITION.fieldId(),
          IS_DELETED.fieldId(),
          SPEC_ID.fieldId(),
          PARTITION_COLUMN_ID);

  /** 返回所有元数据列的字段 ID 集合。 */
  public static Set<Integer> metadataFieldIds() {
    return META_IDS;
  }

  /**
   * 按名称获取元数据列定义；分区列类型依赖表的所有 spec 动态构建。
   *
   * @param table 表
   * @param name 列名
   * @return 元数据列定义，非元数据列返回 null
   */
  public static NestedField metadataColumn(Table table, String name) {
    if (name.equals(PARTITION_COLUMN_NAME)) {
      return Types.NestedField.optional(
          PARTITION_COLUMN_ID,
          PARTITION_COLUMN_NAME,
          Partitioning.partitionType(table),
          PARTITION_COLUMN_DOC);
    } else {
      return META_COLUMNS.get(name);
    }
  }

  /** 判断给定列名是否为元数据列。 */
  public static boolean isMetadataColumn(String name) {
    return name.equals(PARTITION_COLUMN_NAME) || META_COLUMNS.containsKey(name);
  }

  /** 判断给定字段 ID 是否为元数据列。 */
  public static boolean isMetadataColumn(int id) {
    return META_IDS.contains(id);
  }

  /** 判断给定列名是否非元数据列。 */
  public static boolean nonMetadataColumn(String name) {
    return !isMetadataColumn(name);
  }
}
