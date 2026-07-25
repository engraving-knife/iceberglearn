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

import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：删除文件 Schema 构造工具。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：为 position-delete 文件构造所需的 Schema，包含数据文件路径字段（_file_path）、 行号字段（_pos）以及可选的行数据字段（_row）。
 *
 * <p>设计意图：position-delete 文件的物理 Schema 由 Iceberg 元数据列（DELETE_FILE_PATH、 DELETE_FILE_POS）和可选的原始行
 * Schema 组合而成。本工具类统一构造该 Schema，避免各写入器 重复拼装。当不需要保存被删除行的原始数据时（如仅记录位置），可使用不含 row 字段的精简 Schema。
 *
 * <p>上下游关系：被 {@link FileAppenderFactory} 的实现类在创建 position-delete writer 时调用， 用于确定写入文件的列结构。
 */
public class DeleteSchemaUtil {
  private DeleteSchemaUtil() {}

  /**
   * 构造含 row 字段的 path-pos Schema：path + pos + row（行数据为嵌套 struct）。
   *
   * @param rowSchema 原始行 Schema
   * @return 包含 path、pos、row 三列的 Schema
   */
  private static Schema pathPosSchema(Schema rowSchema) {
    return new Schema(
        MetadataColumns.DELETE_FILE_PATH,
        MetadataColumns.DELETE_FILE_POS,
        Types.NestedField.required(
            MetadataColumns.DELETE_FILE_ROW_FIELD_ID,
            "row",
            rowSchema.asStruct(),
            MetadataColumns.DELETE_FILE_ROW_DOC));
  }

  /**
   * 构造不含 row 字段的精简 path-pos Schema：仅 path + pos。
   *
   * @return 仅包含 path、pos 两列的 Schema
   */
  public static Schema pathPosSchema() {
    return new Schema(MetadataColumns.DELETE_FILE_PATH, MetadataColumns.DELETE_FILE_POS);
  }

  /**
   * 根据 rowSchema 是否为 null 选择完整或精简的 position-delete Schema。
   *
   * @param rowSchema 原始行 Schema，null 表示不保存行数据
   * @return position-delete 文件的 Schema
   */
  public static Schema posDeleteSchema(Schema rowSchema) {
    return rowSchema == null ? pathPosSchema() : pathPosSchema(rowSchema);
  }
}
