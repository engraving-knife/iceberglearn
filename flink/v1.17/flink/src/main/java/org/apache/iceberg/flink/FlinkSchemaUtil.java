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
package org.apache.iceberg.flink;

import java.util.List;
import java.util.Set;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.utils.TypeConversions;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：Flink 类型与 Iceberg 类型之间的双向转换工具类。
 *
 * <p>所属模块：iceberg-flink（Flink 集成模块），提供 Schema/Type 级别的静态转换方法。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>Flink {@link TableSchema} / {@link RowType} → Iceberg {@link Schema} / {@link Type}。
 *   <li>Iceberg {@link Schema} / {@link Type} → Flink {@link RowType} / {@link LogicalType} /
 *       {@link TableSchema}。
 *   <li>基于已有 schema 重新分配字段 id（保持 id 一致性）。
 *   <li>处理主键/标识字段 id 的映射。
 * </ul>
 *
 * <p>设计意图：Flink 与 Iceberg 的类型系统不是 1:1 映射，双向转换会丢失部分信息。 以下类型存在不一致：
 *
 * <ul>
 *   <li>Iceberg UUID → Flink BinaryType(16)
 *   <li>Flink VarCharType(_)/CharType(_) → Iceberg String
 *   <li>Flink VarBinaryType(_) → Iceberg Binary
 *   <li>Flink TimeType(_) → Iceberg Time（微秒）
 *   <li>Flink TimestampType(_) → Iceberg Timestamp without zone（微秒）
 *   <li>Flink LocalZonedTimestampType(_) → Iceberg Timestamp with zone（微秒）
 *   <li>Flink MultiSetType → Iceberg Map(element, int)
 * </ul>
 *
 * <p>上下游关系：被 FlinkCatalog、FlinkDynamicTableFactory、sink/source 等广泛调用。
 */
public class FlinkSchemaUtil {

  private FlinkSchemaUtil() {}

  /**
   * 将 Flink {@link TableSchema} 转换为 Iceberg {@link Schema}（分配全新字段 id）。
   *
   * <p>逻辑：将 TableSchema 转为 RowType → 通过 {@link FlinkTypeToType} 访问器转为 Iceberg Type → 构建 Schema → 从
   * TableSchema 主键提取标识字段 id。
   *
   * @param schema Flink 表 schema
   * @return Iceberg schema
   */
  public static Schema convert(TableSchema schema) {
    LogicalType schemaType = schema.toRowDataType().getLogicalType();
    Preconditions.checkArgument(
        schemaType instanceof RowType, "Schema logical type should be RowType.");

    RowType root = (RowType) schemaType;
    Type converted = root.accept(new FlinkTypeToType(root));

    Schema iSchema = new Schema(converted.asStructType().fields());
    return freshIdentifierFieldIds(iSchema, schema);
  }

  /**
   * 从 Flink TableSchema 的主键中提取标识字段 id，附加到 Iceberg Schema。
   *
   * @param iSchema Iceberg schema
   * @param schema Flink 表 schema
   * @return 带标识字段 id 的 schema
   */
  private static Schema freshIdentifierFieldIds(Schema iSchema, TableSchema schema) {
    // Locate the identifier field id list.
    Set<Integer> identifierFieldIds = Sets.newHashSet();
    if (schema.getPrimaryKey().isPresent()) {
      for (String column : schema.getPrimaryKey().get().getColumns()) {
        Types.NestedField field = iSchema.findField(column);
        Preconditions.checkNotNull(
            field,
            "Cannot find field ID for the primary key column %s in schema %s",
            column,
            iSchema);
        identifierFieldIds.add(field.fieldId());
      }
    }

    return new Schema(iSchema.schemaId(), iSchema.asStruct().fields(), identifierFieldIds);
  }

  /**
   * 基于已有 schema 将 Flink {@link TableSchema} 转换为 Iceberg {@link Schema}。
   *
   * <p>逻辑：先做全新 id 转换 → 通过 {@link TypeUtil#reassignIds} 按 baseSchema 重新分配 id → 重新分配 doc → 通过 {@link
   * FlinkFixupTypes#fixup} 修复 UUID/Fixed 歧义 → 附加标识字段 id。
   *
   * <p>设计要点：不分配新 id，而是复用 baseSchema 的 id，确保 schema 演进时字段 id 一致。
   *
   * @param baseSchema 参考 schema（提供 id 和类型信息）
   * @param flinkSchema Flink 表 schema
   * @return 等价的 Iceberg schema
   * @throws IllegalArgumentException 若类型无法转换或 id 缺失
   */
  public static Schema convert(Schema baseSchema, TableSchema flinkSchema) {
    // convert to a type with fresh ids
    Types.StructType struct = convert(flinkSchema).asStruct();
    // reassign ids to match the base schema
    Schema schema = TypeUtil.reassignIds(new Schema(struct.fields()), baseSchema);
    // reassign doc to match the base schema
    schema = TypeUtil.reassignDoc(schema, baseSchema);

    // fix types that can't be represented in Flink (UUID)
    Schema fixedSchema = FlinkFixupTypes.fixup(schema, baseSchema);
    return freshIdentifierFieldIds(fixedSchema, flinkSchema);
  }

  /**
   * 将 Iceberg {@link Schema} 转换为 Flink {@link RowType}。
   *
   * @param schema Iceberg schema
   * @return 等价的 Flink RowType
   * @throws IllegalArgumentException 若类型无法转换为 Flink
   */
  public static RowType convert(Schema schema) {
    return (RowType) TypeUtil.visit(schema, new TypeToFlinkType());
  }

  /**
   * 将 Iceberg {@link Type} 转换为 Flink {@link LogicalType}。
   *
   * @param type Iceberg 类型
   * @return 等价的 Flink LogicalType
   * @throws IllegalArgumentException 若类型无法转换为 Flink
   */
  public static LogicalType convert(Type type) {
    return TypeUtil.visit(type, new TypeToFlinkType());
  }

  /**
   * 将 Flink {@link LogicalType} 转换为 Iceberg {@link Type}。
   *
   * @param flinkType Flink 逻辑类型
   * @return 等价的 Iceberg 类型
   */
  public static Type convert(LogicalType flinkType) {
    return flinkType.accept(new FlinkTypeToType());
  }

  /**
   * 将 Flink {@link RowType} 转换为 {@link TableSchema}（不含主键）。
   *
   * @param rowType Flink 行类型
   * @return Flink TableSchema
   */
  public static TableSchema toSchema(RowType rowType) {
    TableSchema.Builder builder = TableSchema.builder();
    for (RowType.RowField field : rowType.getFields()) {
      builder.field(field.getName(), TypeConversions.fromLogicalToDataType(field.getType()));
    }
    return builder.build();
  }

  /**
   * 将 Iceberg {@link Schema} 转换为 Flink {@link TableSchema}（含主键）。
   *
   * <p>逻辑：先转为 RowType → 逐字段构建 TableSchema → 从标识字段 id 提取主键列名并设置主键。
   *
   * @param schema Iceberg schema
   * @return Flink TableSchema
   */
  public static TableSchema toSchema(Schema schema) {
    TableSchema.Builder builder = TableSchema.builder();

    // Add columns.
    for (RowType.RowField field : convert(schema).getFields()) {
      builder.field(field.getName(), TypeConversions.fromLogicalToDataType(field.getType()));
    }

    // Add primary key.
    Set<Integer> identifierFieldIds = schema.identifierFieldIds();
    if (!identifierFieldIds.isEmpty()) {
      List<String> columns = Lists.newArrayListWithExpectedSize(identifierFieldIds.size());
      for (Integer identifierFieldId : identifierFieldIds) {
        String columnName = schema.findColumnName(identifierFieldId);
        Preconditions.checkNotNull(
            columnName, "Cannot find field with id %s in schema %s", identifierFieldId, schema);

        columns.add(columnName);
      }
      builder.primaryKey(columns.toArray(new String[0]));
    }

    return builder.build();
  }
}
