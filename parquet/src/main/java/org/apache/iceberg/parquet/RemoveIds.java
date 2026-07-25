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
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

/**
 * 文件级说明：从 Parquet schema 中移除所有字段 ID 的访问器。
 *
 * <p>所属模块：iceberg-parquet（schema 处理工具，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：遍历 Parquet schema 树，重建一棵不带字段 ID 的等价 schema 树。
 *
 * <p>设计意图：某些 Parquet 读取器（如旧版 Hive/Spark）不支持字段 ID， 写入时需要移除 ID 以保证兼容性。通过 ParquetTypeVisitor 遍历并重建类型树，
 * 保留原始类型信息（原始类型名、逻辑类型、repetition、长度等）但去掉 ID。
 *
 * <p>上下游关系：被 Parquet 写入流程在需要时调用；依赖 ParquetTypeVisitor（遍历框架）。
 */
public class RemoveIds extends ParquetTypeVisitor<Type> {

  @Override
  public Type message(MessageType message, List<Type> fields) {
    Types.MessageTypeBuilder builder = Types.buildMessage();
    for (Type field : struct(message.asGroupType(), fields).asGroupType().getFields()) {
      builder.addField(field);
    }
    return builder.named(message.getName());
  }

  @Override
  public Type struct(GroupType struct, List<Type> fields) {
    Types.GroupBuilder<GroupType> builder = Types.buildGroup(struct.getRepetition());
    for (Type field : fields) {
      builder.addField(field);
    }
    return builder.named(struct.getName());
  }

  @Override
  public Type list(GroupType array, Type item) {
    Types.GroupBuilder<GroupType> listBuilder =
        Types.buildGroup(array.getRepetition()).as(LogicalTypeAnnotation.listType());
    final Type listElement = ParquetSchemaUtil.determineListElementType(array);
    if (listElement.isRepetition(Type.Repetition.REPEATED)) {
      listBuilder.addFields(item);
    } else {
      listBuilder.repeatedGroup().addFields(item).named(array.getFieldName(0));
    }
    return listBuilder.named(array.getName());
  }

  @Override
  public Type map(GroupType map, Type key, Type value) {
    return Types.buildGroup(map.getRepetition())
        .as(LogicalTypeAnnotation.mapType())
        .repeatedGroup()
        .addFields(key, value)
        .named(map.getFieldName(0))
        .named(map.getName());
  }

  @Override
  public Type primitive(PrimitiveType primitive) {
    return Types.primitive(primitive.getPrimitiveTypeName(), primitive.getRepetition())
        .length(primitive.getTypeLength())
        .as(primitive.getLogicalTypeAnnotation())
        .named(primitive.getName());
  }

  /**
   * 移除 Parquet MessageType 中所有字段的 ID。
   *
   * @param type 带 ID 的 Parquet schema
   * @return 不带 ID 的 Parquet schema
   */
  public static MessageType removeIds(MessageType type) {
    return (MessageType) ParquetTypeVisitor.visit(type, new RemoveIds());
  }
}
