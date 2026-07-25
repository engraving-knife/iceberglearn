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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

/**
 * 文件级说明：按字段 ID 集合裁剪 Parquet schema 的列裁剪访问器。
 *
 * <p>所属模块：iceberg-parquet（列裁剪，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：遍历 Parquet schema 树，仅保留 selectedIds 中指定的字段（及其祖先）， 裁剪掉不需要的列，减少读取时的 IO。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>按 ID 裁剪：通过字段 ID 匹配，支持 schema 演进（字段重命名/重排）。
 *   <li>保留结构：被选中的 struct 的祖先节点会被保留（即使本身不在 selectedIds 中）， 确保嵌套结构完整。
 *   <li>返回 null 表示裁剪：子节点返回 null 表示该列不需要，父节点据此过滤字段列表。
 *   <li>list/map 特殊处理：list 的元素和 map 的 key/value 只要有一个被选中就保留整个 list/map。
 * </ul>
 *
 * <p>上下游关系：被 {@link ParquetSchemaUtil#pruneColumns} 调用； 依赖 ParquetTypeVisitor（遍历框架）。
 */
class PruneColumns extends ParquetTypeVisitor<Type> {
  private final Set<Integer> selectedIds;

  PruneColumns(Set<Integer> selectedIds) {
    Preconditions.checkNotNull(selectedIds, "Selected field ids cannot be null");
    this.selectedIds = selectedIds;
  }

  /**
   * 处理顶层 message：按 selectedIds 过滤顶层字段。
   *
   * <p>逻辑：遍历字段，若字段 ID 在 selectedIds 中则保留（含子树裁剪结果）； 若不在但子树有被选中的字段也保留。无变化时返回原 message。
   */
  @Override
  public Type message(MessageType message, List<Type> fields) {
    Types.MessageTypeBuilder builder = Types.buildMessage();

    boolean hasChange = false;
    int fieldCount = 0;
    for (int i = 0; i < fields.size(); i += 1) {
      Type originalField = message.getType(i);
      Type field = fields.get(i);
      Integer fieldId = getId(originalField);
      if (fieldId != null && selectedIds.contains(fieldId)) {
        if (field != null) {
          hasChange = true;
          builder.addField(field);
        } else {
          if (isStruct(originalField)) {
            hasChange = true;
            builder.addField(originalField.asGroupType().withNewFields(Collections.emptyList()));
          } else {
            builder.addField(originalField);
          }
        }
        fieldCount += 1;
      } else if (field != null) {
        hasChange = true;
        builder.addField(field);
        fieldCount += 1;
      }
    }

    if (hasChange) {
      return builder.named(message.getName());
    } else if (message.getFieldCount() == fieldCount) {
      return message;
    }

    return builder.named(message.getName());
  }

  /** 处理 struct：按 selectedIds 过滤字段，返回裁剪后的 struct。若无字段保留则返回 null。 */
  @Override
  public Type struct(GroupType struct, List<Type> fields) {
    boolean hasChange = false;
    List<Type> filteredFields = Lists.newArrayListWithExpectedSize(fields.size());
    for (int i = 0; i < fields.size(); i += 1) {
      Type originalField = struct.getType(i);
      Type field = fields.get(i);
      Integer fieldId = getId(originalField);
      if (fieldId != null && selectedIds.contains(fieldId)) {
        filteredFields.add(originalField);
      } else if (field != null) {
        filteredFields.add(originalField);
        hasChange = true;
      }
    }

    if (hasChange) {
      return struct.withNewFields(filteredFields);
    } else if (struct.getFieldCount() == filteredFields.size()) {
      return struct;
    } else if (!filteredFields.isEmpty()) {
      return struct.withNewFields(filteredFields);
    }

    return null;
  }

  /** 处理 list：若元素 ID 在 selectedIds 中或子树有选中字段则保留，否则返回 null。 */
  @Override
  public Type list(GroupType list, Type element) {
    Type repeated = list.getType(0);
    Type originalElement = ParquetSchemaUtil.determineListElementType(list);
    Integer elementId = getId(originalElement);

    if (elementId != null && selectedIds.contains(elementId)) {
      return list;
    } else if (element != null) {
      if (!Objects.equal(element, originalElement)) {
        if (originalElement.isRepetition(Type.Repetition.REPEATED)) {
          return list.withNewFields(element);
        } else {
          return list.withNewFields(repeated.asGroupType().withNewFields(element));
        }
      }
      return list;
    }

    return null;
  }

  /** 处理 map：若 key 或 value 的 ID 在 selectedIds 中或子树有选中字段则保留，否则返回 null。 */
  @Override
  public Type map(GroupType map, Type key, Type value) {
    GroupType repeated = map.getType(0).asGroupType();
    Type originalKey = repeated.getType(0);
    Type originalValue = repeated.getType(1);

    Integer keyId = getId(originalKey);
    Integer valueId = getId(originalValue);

    if ((keyId != null && selectedIds.contains(keyId))
        || (valueId != null && selectedIds.contains(valueId))) {
      return map;
    } else if (value != null) {
      if (!Objects.equal(value, originalValue)) {
        return map.withNewFields(repeated.withNewFields(originalKey, value));
      }
      return map;
    }

    return null;
  }

  /** 原始类型始终返回 null（是否保留由父级 struct/message 按 ID 决定）。 */
  @Override
  public Type primitive(PrimitiveType primitive) {
    return null;
  }

  private Integer getId(Type type) {
    return type.getId() == null ? null : type.getId().intValue();
  }

  private boolean isStruct(Type field) {
    if (field.isPrimitive()) {
      return false;
    } else {
      GroupType groupType = field.asGroupType();
      LogicalTypeAnnotation logicalTypeAnnotation = groupType.getLogicalTypeAnnotation();
      return !LogicalTypeAnnotation.mapType().equals(logicalTypeAnnotation)
          && !LogicalTypeAnnotation.listType().equals(logicalTypeAnnotation);
    }
  }
}
