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
package org.apache.iceberg.parquet;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.iceberg.Schema;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types.MessageTypeBuilder;

/**
 * 文件级说明：Iceberg Schema 与 Parquet MessageType 之间的转换工具集。
 *
 * <p>所属模块：iceberg-parquet（schema 转换工具，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>Iceberg Schema → Parquet MessageType（{@link #convert(Schema, String)}）。
 *   <li>Parquet MessageType → Iceberg Schema（{@link #convert(MessageType)} / {@link
 *       #convertAndPrune}）。
 *   <li>列裁剪：按期望 schema 裁剪 Parquet 文件 schema（{@link #pruneColumns} / {@link #pruneColumnsFallback}）。
 *   <li>NameMapping 应用、字段 ID 检测与回退分配、List 元素类型判断等。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>双向转换：Iceberg 以字段 ID 为核心，Parquet 以字段名为核心，本类在两者间搭建桥梁。
 *   <li>兼容性处理：无 ID 的旧文件通过 addFallbackIds 分配序号 ID（≥1），或通过 pruneColumnsFallback 按列序号匹配；List
 *       的旧版编码（2-level）通过 isOldListElementType 兼容。
 * </ul>
 *
 * <p>上下游关系：被 Parquet 读取器/写入器、BaseParquetReaders 等广泛使用； 依赖 ParquetTypeVisitor（遍历框架）、TypeUtil（Iceberg
 * 类型工具）。
 */
public class ParquetSchemaUtil {

  private ParquetSchemaUtil() {}

  /**
   * 将 Iceberg Schema 转为 Parquet MessageType。
   *
   * @param schema Iceberg schema
   * @param name MessageType 名称
   * @return Parquet MessageType
   */
  public static MessageType convert(Schema schema, String name) {
    return new TypeToMessageType().convert(schema, name);
  }

  /**
   * 将 Parquet MessageType 转为 Iceberg Schema。
   *
   * <p>逻辑：若无字段 ID，先通过 addFallbackIds 分配序号 ID（从 1 开始）， 嵌套字段 ID 从 1000 开始以避免与裁剪逻辑冲突。
   *
   * @param parquetSchema Parquet schema
   * @return 对应的 Iceberg Schema
   */
  public static Schema convert(MessageType parquetSchema) {
    // if the Parquet schema does not contain ids, we assign fallback ids to top-level fields
    // all remaining fields will get ids >= 1000 to avoid pruning columns without ids
    MessageType parquetSchemaWithIds =
        hasIds(parquetSchema) ? parquetSchema : addFallbackIds(parquetSchema);
    AtomicInteger nextId = new AtomicInteger(1000);
    return convertInternal(parquetSchemaWithIds, name -> nextId.getAndIncrement());
  }

  /**
   * 将 Parquet schema 转为 Iceberg Schema 并裁剪无 ID 的字段。
   *
   * @param parquetSchema Parquet schema
   * @return 裁剪后的 Iceberg Schema
   */
  public static Schema convertAndPrune(MessageType parquetSchema) {
    return convertInternal(parquetSchema, name -> null);
  }

  private static Schema convertInternal(
      MessageType parquetSchema, Function<String[], Integer> nameToIdFunc) {
    MessageTypeToType converter = new MessageTypeToType(nameToIdFunc);
    return new Schema(
        ParquetTypeVisitor.visit(parquetSchema, converter).asNestedType().fields(),
        converter.getAliases());
  }

  /**
   * 按期望 schema 裁剪 Parquet 文件 schema 的列。
   *
   * <p>逻辑：通过 TypeUtil.getProjectedIds 获取需保留的字段 ID 集合， 用 PruneColumns 访问器裁剪文件 schema。
   *
   * @param fileSchema Parquet 文件 schema
   * @param expectedSchema 期望的 Iceberg schema
   * @return 裁剪后的 Parquet MessageType
   */
  public static MessageType pruneColumns(MessageType fileSchema, Schema expectedSchema) {
    // column order must match the incoming type, so it doesn't matter that the ids are unordered
    Set<Integer> selectedIds = TypeUtil.getProjectedIds(expectedSchema);
    return (MessageType) ParquetTypeVisitor.visit(fileSchema, new PruneColumns(selectedIds));
  }

  /**
   * 裁剪无字段 ID 的 Parquet 文件 schema（按列序号匹配）。
   *
   * <p>设计意图：无 ID 的旧文件假设 schema 演进保持列顺序不变（不允许删列）， 因此按列序号（ordinal）与期望 schema 的字段 ID 对应。结果 schema
   * 的列顺序与文件一致。
   *
   * @param fileSchema 无字段 ID 的 Parquet 文件 schema
   * @param expectedSchema 期望 schema
   * @return 裁剪后的 Parquet MessageType
   */
  public static MessageType pruneColumnsFallback(MessageType fileSchema, Schema expectedSchema) {
    Set<Integer> selectedIds = Sets.newHashSet();

    for (Types.NestedField field : expectedSchema.columns()) {
      selectedIds.add(field.fieldId());
    }

    MessageTypeBuilder builder = org.apache.parquet.schema.Types.buildMessage();

    int ordinal = 1;
    for (Type type : fileSchema.getFields()) {
      if (selectedIds.contains(ordinal)) {
        builder.addField(type.withId(ordinal));
      }
      ordinal += 1;
    }

    return builder.named(fileSchema.getName());
  }

  /** 检查 Parquet schema 是否包含字段 ID。 */
  public static boolean hasIds(MessageType fileSchema) {
    return ParquetTypeVisitor.visit(fileSchema, new HasIds());
  }

  /**
   * 为无 ID 的 Parquet schema 顶层字段分配序号 ID（从 1 开始）。
   *
   * @param fileSchema 无 ID 的 Parquet schema
   * @return 带 fallback ID 的 Parquet schema
   */
  public static MessageType addFallbackIds(MessageType fileSchema) {
    MessageTypeBuilder builder = org.apache.parquet.schema.Types.buildMessage();

    int ordinal = 1; // ids are assigned starting at 1
    for (Type type : fileSchema.getFields()) {
      builder.addField(type.withId(ordinal));
      ordinal += 1;
    }

    return builder.named(fileSchema.getName());
  }

  /**
   * 将 NameMapping 应用到 Parquet schema，为字段补充 ID。
   *
   * @param fileSchema Parquet 文件 schema
   * @param nameMapping 字段名路径→ID 映射
   * @return 带 ID 的 Parquet schema
   */
  public static MessageType applyNameMapping(MessageType fileSchema, NameMapping nameMapping) {
    return (MessageType) ParquetTypeVisitor.visit(fileSchema, new ApplyNameMapping(nameMapping));
  }

  /** 访问器：检查 Parquet schema 树中是否有任何字段带 ID。 */
  public static class HasIds extends ParquetTypeVisitor<Boolean> {
    @Override
    public Boolean message(MessageType message, List<Boolean> fields) {
      return struct(message, fields);
    }

    @Override
    public Boolean struct(GroupType struct, List<Boolean> hasIds) {
      for (Boolean hasId : hasIds) {
        if (hasId) {
          return true;
        }
      }
      return struct.getId() != null;
    }

    @Override
    public Boolean list(GroupType array, Boolean hasId) {
      return hasId || array.getId() != null;
    }

    @Override
    public Boolean map(GroupType map, Boolean keyHasId, Boolean valueHasId) {
      return keyHasId || valueHasId || map.getId() != null;
    }

    @Override
    public Boolean primitive(PrimitiveType primitive) {
      return primitive.getId() != null;
    }
  }

  /**
   * 确定 Parquet LIST group 的元素类型，兼容旧版 2-level 编码。
   *
   * @param array Parquet LIST group
   * @return 元素类型
   */
  public static Type determineListElementType(GroupType array) {
    Type repeated = array.getFields().get(0);
    boolean isOldListElementType = isOldListElementType(array);

    return isOldListElementType ? repeated : repeated.asGroupType().getType(0);
  }

  // Parquet LIST backwards-compatibility rules.
  // https://github.com/apache/parquet-format/blob/master/LogicalTypes.md#backward-compatibility-rules
  /**
   * 判断是否为旧版 2-level LIST 编码（无中间 group）。
   *
   * <p>逻辑：按 Parquet 向后兼容规则判断——元素为原始类型、或 group 字段数>1、 或名称为 "array"（parquet-avro 旧版）、或名称为
   * "父名_tuple"（parquet-thrift）。
   *
   * @param list Parquet LIST group
   * @return true 表示旧版 2-level 编码
   */
  static boolean isOldListElementType(GroupType list) {
    Type repeatedType = list.getFields().get(0);
    String parentName = list.getName();

    return
    // For legacy 2-level list types with primitive element type, e.g.:
    //
    //    // ARRAY<INT> (nullable list, non-null elements)
    //    optional group my_list (LIST) {
    //      repeated int32 element;
    //    }
    //
    repeatedType.isPrimitive()
        ||
        // For legacy 2-level list types whose element type is a group type with 2 or more fields,
        // e.g.:
        //
        //    // ARRAY<STRUCT<str: STRING, num: INT>> (nullable list, non-null elements)
        //    optional group my_list (LIST) {
        //      repeated group element {
        //        required binary str (UTF8);
        //        required int32 num;
        //      };
        //    }
        //
        repeatedType.asGroupType().getFieldCount() > 1
        ||
        // For legacy 2-level list types generated by parquet-avro (Parquet version < 1.6.0), e.g.:
        //
        //    // ARRAY<STRUCT<str: STRING>> (nullable list, non-null elements)
        //    optional group my_list (LIST) {
        //      repeated group array {
        //        required binary str (UTF8);
        //      };
        //    }
        repeatedType.getName().equals("array")
        ||
        // For Parquet data generated by parquet-thrift, e.g.:
        //
        //    // ARRAY<STRUCT<str: STRING>> (nullable list, non-null elements)
        //    optional group my_list (LIST) {
        //      repeated group my_list_tuple {
        //        required binary str (UTF8);
        //      };
        //    }
        //
        repeatedType.getName().equals(parentName + "_tuple");
  }
}
