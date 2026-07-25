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
 * Flink 类型与 Iceberg 类型之间的转换工具类。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：在 Flink {@link TableSchema}/{@link RowType} 与 Iceberg {@link
 * Schema}/{@link Type} 之间双向转换。注意转换并非 1:1 可逆， 部分信息（如精度、UUID 与 Fixed 的区别）可能在往返转换中丢失。
 *
 * <p>设计意图：工具类 + 静态方法；内部委托给 {@link FlinkTypeToType} 与 {@link TypeToFlinkType}。
 *
 * <p>不一致的类型映射：
 *
 * <ul>
 *   <li>Iceberg UUID -> Flink BinaryType(16)
 *   <li>Flink VarCharType(_)/CharType(_) -> Iceberg String
 *   <li>Flink VarBinaryType(_) -> Iceberg Binary
 *   <li>Flink TimeType(_) -> Iceberg Time（微秒）
 *   <li>Flink TimestampType(_) -> Iceberg Timestamp 不带时区（微秒）
 *   <li>Flink LocalZonedTimestampType(_) -> Iceberg Timestamp 带时区（微秒）
 *   <li>Flink MultiSetType -> Iceberg Map(element, int)
 * </ul>
 */
public class FlinkSchemaUtil {

  private FlinkSchemaUtil() {}

  /** 将 Flink TableSchema 转换为 Iceberg Schema，并保留主键作为 identifier 字段。 */
  public static Schema convert(TableSchema schema) {
    LogicalType schemaType = schema.toRowDataType().getLogicalType();
    Preconditions.checkArgument(
        schemaType instanceof RowType, "Schema logical type should be RowType.");

    RowType root = (RowType) schemaType;
    Type converted = root.accept(new FlinkTypeToType(root));

    Schema iSchema = new Schema(converted.asStructType().fields());
    return freshIdentifierFieldIds(iSchema, schema);
  }

  /** 将 Flink 主键列转换为 Iceberg 的 identifier 字段 id 集合，构造新 Schema。 */
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
   * 基于 baseSchema 的字段 id，把 Flink TableSchema 转换为 Iceberg Schema。
   *
   * <p>逻辑：先转换为全新 id 的 schema，再按 baseSchema 重分配 id 与 doc， 最后通过 {@link FlinkFixupTypes} 修正 UUID/Fixed
   * 类型。可能产生与 baseSchema 不兼容的 schema。
   *
   * @param baseSchema 作为 id 来源的基线 schema
   * @param flinkSchema Flink TableSchema
   * @return 等价的 Iceberg Schema
   * @throws IllegalArgumentException 类型不可转换或缺少 id
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
   * @param schema Iceberg Schema
   * @return 等价的 Flink RowType
   * @throws IllegalArgumentException 类型无法转换为 Flink
   */
  public static RowType convert(Schema schema) {
    return (RowType) TypeUtil.visit(schema, new TypeToFlinkType());
  }

  /**
   * 将 Iceberg {@link Type} 转换为 Flink {@link LogicalType}。
   *
   * @param type Iceberg Type
   * @return 等价的 Flink LogicalType
   * @throws IllegalArgumentException 类型无法转换为 Flink
   */
  public static LogicalType convert(Type type) {
    return TypeUtil.visit(type, new TypeToFlinkType());
  }

  /**
   * 将 Flink {@link RowType} 转换为 {@link TableSchema}。
   *
   * @param rowType Flink RowType
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
   * 将 Iceberg {@link Schema} 转换为 {@link TableSchema}，并把 identifier 字段映射为主键。
   *
   * @param schema Iceberg Schema
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
