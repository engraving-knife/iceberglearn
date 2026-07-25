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
package org.apache.iceberg.mr.hive.vector;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：从 Parquet 文件 schema 中收集顶层字段名，并将期望 schema 的字段名
 * 翻译为 Parquet 文件中可匹配的字段名，以支持列重命名场景。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块的向量化读取子包）。
 *
 * <p>职责：
 * <ul>
 *   <li>遍历 Iceberg 期望 schema 与 Parquet 文件 schema，建立字段 ID 到 Parquet Type 的映射。</li>
 *   <li>对期望 schema 中存在但文件中不存在的字段，输出新字段名或占位字段名，
 *       强制 Hive Parquet 读取器对该列返回 null。</li>
 *   <li>产出逗号分隔的列名列表，供 Hive 向量化 Parquet 读取器使用。</li>
 * </ul>
 *
 * <p>设计意图：Hive 的 Parquet 读取器依赖列名匹配而非字段 ID，当 Iceberg 表发生列重命名
 * 或新增字段时，需要本访问器把"期望字段名"翻译成"文件中实际存在的字段名"，从而
 * 在保留列重命名兼容性的同时正确读取历史文件。
 *
 * <p>上下游关系：上游为 {@code HiveVectorizedReader} 的 schema 剪枝逻辑，
 * 下游为 Hive Parquet 读取器的列名配置。
 */
class ParquetSchemaFieldNameVisitor extends TypeWithSchemaVisitor<Type> {
  private final MessageType originalFileSchema;
  private final Map<Integer, Type> typesById = Maps.newHashMap();
  private StringBuilder sb = new StringBuilder();
  private static final String DUMMY_COL_NAME = "<<DUMMY_FOR_RECREATED_FIELD_IN_FILESCHEMA>>";

  /** 构造访问器，传入 Parquet 原始文件 schema 用于字段名查找。 */
  ParquetSchemaFieldNameVisitor(MessageType originalFileSchema) {
    this.originalFileSchema = originalFileSchema;
  }

  /** 处理消息类型根节点，委托给 struct 实现。 */
  @Override
  public Type message(Types.StructType expected, MessageType prunedFileSchema, List<Type> fields) {
    return this.struct(expected, prunedFileSchema.asGroupType(), fields);
  }

  /**
   * 处理 struct 类型节点，按期望 schema 字段顺序输出对应的文件字段名。
   *
   * <p>逻辑：对每个期望字段，若文件中已存在该 ID 则使用原文件字段名；
   * 若文件中找不到该 ID 但能按字段名匹配，说明字段被重建，写入占位名以触发 null；
   * 若字段名也不存在，说明是新增字段，直接使用期望字段名。
   */
  @Override
  public Type struct(Types.StructType expected, GroupType struct, List<Type> fields) {
    boolean isMessageType = struct instanceof MessageType;

    List<Types.NestedField> expectedFields =
        expected != null ? expected.fields() : ImmutableList.of();
    List<Type> types = Lists.newArrayListWithExpectedSize(expectedFields.size());

    for (Types.NestedField field : expectedFields) {
      int id = field.fieldId();
      if (MetadataColumns.metadataFieldIds().contains(id)) {
        continue;
      }

      Type fieldInPrunedFileSchema = typesById.get(id);
      if (fieldInPrunedFileSchema == null) {
        if (!originalFileSchema.containsField(field.name())) {
          // Must be a new field - it isn't in this parquet file yet, so add the new field name
          // instead of null
          appendToColNamesList(isMessageType, field.name());
        } else {
          // This field is found in the parquet file with a different ID, so it must have been
          // recreated since.
          // Inserting a dummy col name to force Hive Parquet reader returning null for this column.
          appendToColNamesList(isMessageType, DUMMY_COL_NAME);
        }
      } else {
        // Already present column in this parquet file, add the original name
        types.add(fieldInPrunedFileSchema);
        appendToColNamesList(isMessageType, fieldInPrunedFileSchema.getName());
      }
    }

    if (!isMessageType) {
      GroupType groupType = new GroupType(Type.Repetition.REPEATED, fieldNames.peek(), types);
      typesById.put(struct.getId().intValue(), groupType);
      return groupType;
    } else {
      return new MessageType("table", types);
    }
  }

  /** 仅在顶层 MessageType 节点将字段名追加到列名列表中，使用逗号分隔。 */
  private void appendToColNamesList(boolean isMessageType, String colName) {
    if (isMessageType) {
      sb.append(colName).append(',');
    }
  }

  /** 访问叶子节点，将 Parquet 原始类型按字段 ID 注册到映射表。 */
  @Override
  public Type primitive(
      org.apache.iceberg.types.Type.PrimitiveType expected,
      org.apache.parquet.schema.PrimitiveType primitive) {
    typesById.put(primitive.getId().intValue(), primitive);
    return primitive;
  }

  /** 访问 list 节点，将数组 group 类型按字段 ID 注册到映射表。 */
  @Override
  public Type list(Types.ListType iList, GroupType array, Type element) {
    typesById.put(array.getId().intValue(), array);
    return array;
  }

  /** 访问 map 节点，将 map group 类型按字段 ID 注册到映射表。 */
  @Override
  public Type map(Types.MapType iMap, GroupType map, Type key, Type value) {
    typesById.put(map.getId().intValue(), map);
    return map;
  }

  /**
   * 取出累积的列名列表字符串，去掉末尾多余的逗号。
   *
   * @return 逗号分隔的列名列表
   */
  public String retrieveColumnNameList() {
    sb.setLength(sb.length() - 1);
    return sb.toString();
  }
}
