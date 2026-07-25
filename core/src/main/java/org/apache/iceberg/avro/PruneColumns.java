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
package org.apache.iceberg.avro;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Avro Schema 列裁剪访问者，按选定字段 ID 集合裁剪 schema。
 *
 * <p>所属模块：iceberg-core（avro 子包）。
 *
 * <p>职责：遍历 Avro Schema，仅保留 selectedIds 中包含的字段及其必要父节点，返回裁剪后的 schema。用于读取时只解码需要的列，减少 IO。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>ID 驱动裁剪：基于字段 ID 而非字段名匹配，保证 schema 演进（重命名/重排）下裁剪稳定。
 *   <li>NameMapping 兜底：当文件 schema 无字段 ID（非 Iceberg 写入）时，用 nameMapping 由 字段名反查 ID。
 *   <li>保留必要结构：map/list 若 key 或 value 被选中需保留整体；空 record（无字段）也要保留以 表达"选中 record 本身"。
 *   <li>变更检测与复用：若裁剪结果与原 schema 一致则直接返回原对象，避免无谓拷贝。
 * </ul>
 *
 * <p>上下游关系：由 {@link AvroSchemaUtil#pruneColumns} 调用，进而服务于投影读取 （{@link ProjectionDatumReader}）。
 */
class PruneColumns extends AvroSchemaVisitor<Schema> {
  private static final Logger LOG = LoggerFactory.getLogger(PruneColumns.class);

  private final Set<Integer> selectedIds;
  private final NameMapping nameMapping;

  /**
   * 构造裁剪访问者。
   *
   * @param selectedIds 需保留的字段 ID 集合
   * @param nameMapping 字段名到 ID 的映射（可为 null）
   */
  PruneColumns(Set<Integer> selectedIds, NameMapping nameMapping) {
    Preconditions.checkNotNull(selectedIds, "Selected field ids cannot be null");
    this.selectedIds = selectedIds;
    this.nameMapping = nameMapping;
  }

  /**
   * 对根 record 执行裁剪；若结果为 null（所有字段被裁掉）则返回空 record 副本而非 null， 保证根 schema 始终有效。
   *
   * @param record 根 record schema
   * @return 裁剪后的 schema（绝不返回 null）
   */
  Schema rootSchema(Schema record) {
    Schema result = visit(record, this);
    if (result != null) {
      return result;
    }

    return copyRecord(record, ImmutableList.of());
  }

  /**
   * 处理 record 节点：按字段顺序遍历，依据 ID 决定保留/裁剪/重建。
   *
   * <p>逻辑步骤：
   *
   * <ol>
   *   <li>对每个字段解析 fieldId（优先 schema 内 ID，否则用 nameMapping 反查）。
   *   <li>无 ID 的字段直接裁掉。
   *   <li>若 ID 来自 nameMapping（schema 内无 ID），标记 hasChange 以便重建时补 ID。
   *   <li>处理 nullable 字段顺序：若 null 不在 union 第一位，标记重建并重排。
   *   <li>字段被选中或其子树有非 null 投影结果时保留该字段，否则裁掉。
   *   <li>无变更且字段数不变则返回原 record，否则按变更情况重建或返回 null。
   * </ol>
   *
   * @param record record schema
   * @param names 字段名列表
   * @param fields 各字段递归裁剪结果（可能含 null）
   * @return 裁剪后 schema，全裁掉返回 null
   */
  @Override
  public Schema record(Schema record, List<String> names, List<Schema> fields) {
    // Then this should access the record's fields by name
    List<Schema.Field> filteredFields = Lists.newArrayListWithExpectedSize(fields.size());
    boolean hasChange = false;
    for (Schema.Field field : record.getFields()) {
      Integer fieldId = AvroSchemaUtil.getFieldId(field, nameMapping, fieldNames());
      if (fieldId == null) {
        // Both the schema and the nameMapping does not have field id. We prune this field.
        continue;
      }

      if (!AvroSchemaUtil.hasFieldId(field)) {
        // fieldId was resolved from nameMapping, we updated hasChange
        // flag to make sure a new field is created with the field id
        hasChange = true;
      }

      if (isOptionSchemaWithNonNullFirstOption(field.schema())) {
        // if the field has an optional schema where the first option is not NULL,
        // we update hasChange flag to make sure we reorder the schema and make the
        // NULL option as the first
        hasChange = true;
      }

      Schema fieldSchema = fields.get(field.pos());
      // All primitives are selected by selecting the field, but map and list
      // types can be selected by projecting the keys, values, or elements. Empty
      // Structs can be selected by selecting the record itself instead of its children.
      // This creates two conditions where the field should be selected: if the
      // id is selected or if the result of the field is non-null. The only
      // case where the converted field is non-null is when a map or list is
      // selected by lower IDs.
      if (selectedIds.contains(fieldId)) {
        if (fieldSchema != null) {
          hasChange = true; // Sub-fields may be different
          filteredFields.add(copyField(field, fieldSchema, fieldId));
        } else {
          if (isRecord(field.schema())) {
            hasChange = true; // Sub-fields are now empty
            filteredFields.add(copyField(field, makeEmptyCopy(field.schema()), fieldId));
          } else {
            filteredFields.add(copyField(field, field.schema(), fieldId));
          }
        }
      } else if (fieldSchema != null) {
        hasChange = true; // Sub-fields may be different
        filteredFields.add(copyField(field, fieldSchema, fieldId));
      }
    }

    if (hasChange) {
      return copyRecord(record, filteredFields);
    } else if (filteredFields.size() == record.getFields().size()) {
      return record;
    } else if (!filteredFields.isEmpty()) {
      return copyRecord(record, filteredFields);
    }

    return null;
  }

  /**
   * 处理 union（Iceberg 仅允许 nullable option）节点。
   *
   * <p>逻辑步骤：取非 null 分支的递归结果；若非 null 分支被裁掉则整体返回 null； 否则与原 union 比较，有变化则重建为 option，无变化则返回原 union。
   *
   * @param union union schema
   * @param options 各分支递归结果
   * @return 裁剪后 schema 或 null（全裁掉）
   */
  @Override
  public Schema union(Schema union, List<Schema> options) {
    Preconditions.checkState(
        AvroSchemaUtil.isOptionSchema(union),
        "Invalid schema: non-option unions are not supported: %s",
        union);

    // only unions with null are allowed, and a null schema results in null
    Schema pruned = null;
    if (options.get(0) != null) {
      pruned = options.get(0);
    } else if (options.get(1) != null) {
      pruned = options.get(1);
    }

    if (pruned != null) {
      if (!Objects.equals(pruned, AvroSchemaUtil.fromOption(union))) {
        return AvroSchemaUtil.toOption(pruned);
      }
      return union;
    }

    return null;
  }

  /**
   * 处理 array 节点；区分 LogicalMap（map-as-array）与普通 array 两种语义。
   *
   * <p>逻辑步骤：
   *
   * <ul>
   *   <li>LogicalMap 情形：解析 key/value 的 ID，若任一被选中则整体保留并补 ID； 否则按 value 投影结果重建（key 不可被投影，需校验指纹一致）。
   *   <li>普通 array 情形：解析元素 ID，被选中则保留整体并补 ID；否则按元素投影结果重建。
   * </ul>
   *
   * <p>无法解析 ID 或无需投影时返回 null。
   *
   * @param array array schema
   * @param element 元素递归结果
   * @return 裁剪后 schema 或 null
   */
  @Override
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public Schema array(Schema array, Schema element) {
    if (array.getLogicalType() instanceof LogicalMap) {
      Schema keyValue = array.getElementType();
      Integer keyId =
          AvroSchemaUtil.getFieldId(keyValue.getField("key"), nameMapping, fieldNames());
      Integer valueId =
          AvroSchemaUtil.getFieldId(keyValue.getField("value"), nameMapping, fieldNames());
      if (keyId == null || valueId == null) {
        if (keyId != null || valueId != null) {
          LOG.warn("Map schema {} should have both key and value ids set or both unset", array);
        }
        return null;
      }

      // if either key or value is selected, the whole map must be projected
      if (selectedIds.contains(keyId) || selectedIds.contains(valueId)) {
        return complexMapWithIds(array, keyId, valueId);
      } else if (element != null) {
        Schema.Field keyProjectionField = element.getField("key");
        Schema valueProjection = element.getField("value").schema();
        // it is possible that key is not selected, and
        // key schemas can be different if new field ids were assigned to them
        if (keyProjectionField != null
            && !Objects.equals(keyValue.getField("key").schema(), keyProjectionField.schema())) {
          Preconditions.checkState(
              SchemaNormalization.parsingFingerprint64(keyValue.getField("key").schema())
                  == SchemaNormalization.parsingFingerprint64(keyProjectionField.schema()),
              "Map keys should not be projected");
          return AvroSchemaUtil.createMap(
              keyId, keyProjectionField.schema(), valueId, valueProjection);
        } else if (!Objects.equals(keyValue.getField("value").schema(), valueProjection)) {
          return AvroSchemaUtil.createMap(
              keyId, keyValue.getField("key").schema(), valueId, valueProjection);
        } else {
          return complexMapWithIds(array, keyId, valueId);
        }
      }

    } else {
      Integer elementId = AvroSchemaUtil.getElementId(array, nameMapping, fieldNames());
      if (elementId == null) {
        return null;
      }

      if (selectedIds.contains(elementId)) {
        return arrayWithId(array, elementId);
      } else if (element != null) {
        if (!Objects.equals(element, array.getElementType())) {
          // the element must be a projection
          return arrayWithId(Schema.createArray(element), elementId);
        }
        return arrayWithId(array, elementId);
      }
    }

    return null;
  }

  /**
   * 处理标准 Avro map 节点（非 LogicalMap）。
   *
   * <p>逻辑步骤：解析 key/value ID，若任一被选中则整体保留并补 ID；否则按 value 投影结果重建。
   *
   * @param map map schema
   * @param value value 递归结果
   * @return 裁剪后 schema 或 null
   */
  @Override
  public Schema map(Schema map, Schema value) {
    Integer keyId = AvroSchemaUtil.getKeyId(map, nameMapping, fieldNames());
    Integer valueId = AvroSchemaUtil.getValueId(map, nameMapping, fieldNames());
    if (keyId == null || valueId == null) {
      if (keyId != null || valueId != null) {
        LOG.warn("Map schema {} should have both key and value ids set or both unset", map);
      }
      return null;
    }

    // if either key or value is selected, the whole map must be projected
    if (selectedIds.contains(keyId) || selectedIds.contains(valueId)) {
      // Assign ids. Ids may not always be present in the schema,
      // e.g if we are reading data not written by Iceberg writers
      return mapWithIds(map, keyId, valueId);
    } else if (value != null) {
      if (!Objects.equals(value, map.getValueType())) {
        // the value must be a projection
        return mapWithIds(Schema.createMap(value), keyId, valueId);
      }
      return map;
    }

    return null;
  }

  /**
   * 若 array 已有元素 ID 属性则原样返回，否则重建 array 并补 ELEMENT_ID 属性。
   *
   * @param array array schema
   * @param elementId 元素 ID
   * @return 带 ID 的 array schema
   */
  private Schema arrayWithId(Schema array, Integer elementId) {
    if (!AvroSchemaUtil.hasProperty(array, AvroSchemaUtil.ELEMENT_ID_PROP)) {
      Schema result = Schema.createArray(array.getElementType());
      result.addProp(AvroSchemaUtil.ELEMENT_ID_PROP, elementId);
      return result;
    }
    return array;
  }

  /**
   * 为 LogicalMap（map-as-array）补 key/value 字段 ID；若已具备则原样返回。
   *
   * @param map map-as-array schema
   * @param keyId key 字段 ID
   * @param valueId value 字段 ID
   * @return 带 ID 的 map schema
   */
  private Schema complexMapWithIds(Schema map, Integer keyId, Integer valueId) {
    Schema keyValue = map.getElementType();
    if (!AvroSchemaUtil.hasFieldId(keyValue.getField("key"))
        || !AvroSchemaUtil.hasFieldId(keyValue.getField("value"))) {
      return AvroSchemaUtil.createMap(
          keyId, keyValue.getField("key").schema(),
          valueId, keyValue.getField("value").schema());
    }
    return map;
  }

  /**
   * 为标准 Avro map 补 KEY_ID/VALUE_ID 属性；若已具备则原样返回。
   *
   * @param map map schema
   * @param keyId key ID
   * @param valueId value ID
   * @return 带 ID 的 map schema
   */
  private Schema mapWithIds(Schema map, Integer keyId, Integer valueId) {
    if (!AvroSchemaUtil.hasProperty(map, AvroSchemaUtil.KEY_ID_PROP)
        || !AvroSchemaUtil.hasProperty(map, AvroSchemaUtil.VALUE_ID_PROP)) {
      Schema result = Schema.createMap(map.getValueType());
      result.addProp(AvroSchemaUtil.KEY_ID_PROP, keyId);
      result.addProp(AvroSchemaUtil.VALUE_ID_PROP, valueId);
      return result;
    }
    return map;
  }

  /**
   * 基本类型节点不直接被选中，统一返回 null（由父节点决定是否保留）。
   *
   * @param primitive 基本 schema
   * @return 始终 null
   */
  @Override
  public Schema primitive(Schema primitive) {
    // primitives are not selected directly
    return null;
  }

  /**
   * 拷贝 record 并替换字段列表，同时复制 record 的自定义属性。
   *
   * @param record 原 record schema
   * @param newFields 新字段列表
   * @return 新 record schema
   */
  private static Schema copyRecord(Schema record, List<Schema.Field> newFields) {
    Schema copy =
        Schema.createRecord(
            record.getName(), record.getDoc(), record.getNamespace(), record.isError(), newFields);

    for (Map.Entry<String, Object> prop : record.getObjectProps().entrySet()) {
      copy.addProp(prop.getKey(), prop.getValue());
    }

    return copy;
  }

  /**
   * 判断字段（可能是 option 包装）是否为 record 类型。
   *
   * @param field 字段 schema
   * @return 是否 record
   */
  private boolean isRecord(Schema field) {
    if (AvroSchemaUtil.isOptionSchema(field)) {
      return AvroSchemaUtil.fromOption(field).getType().equals(Schema.Type.RECORD);
    } else {
      return field.getType().equals(Schema.Type.RECORD);
    }
  }

  /**
   * 生成一个无字段的空 record 副本（用于"选中 record 本身但无具体子字段"场景）。
   *
   * <p>逻辑步骤：若 schema 是 option，先取内层 record 再建空 record 并重新包装为 option。
   *
   * @param field 原 record schema
   * @return 空 record schema
   */
  private static Schema makeEmptyCopy(Schema field) {
    if (AvroSchemaUtil.isOptionSchema(field)) {
      Schema innerSchema = AvroSchemaUtil.fromOption(field);
      Schema emptyRecord =
          Schema.createRecord(
              innerSchema.getName(),
              innerSchema.getDoc(),
              innerSchema.getNamespace(),
              innerSchema.isError(),
              Collections.emptyList());
      return AvroSchemaUtil.toOption(emptyRecord);
    } else {
      return Schema.createRecord(
          field.getName(),
          field.getDoc(),
          field.getNamespace(),
          field.isError(),
          Collections.emptyList());
    }
  }

  /**
   * 拷贝字段并替换 schema、补字段 ID，保证 nullable 字段 null 在前。
   *
   * <p>逻辑步骤：
   *
   * <ol>
   *   <li>若新 schema 是 nullable 且 null 不在前，重排为 null 在前。
   *   <li>创建新 Field（不复制 default，因文件 schema 已含值），nullable 时 default 设为 NULL。
   *   <li>复制字段自定义属性。
   *   <li>若原字段已有 ID 且与传入 ID 不符则报错；若无 ID 则补 FIELD_ID 属性。
   * </ol>
   *
   * @param field 原字段
   * @param newSchema 新 schema
   * @param fieldId 字段 ID
   * @return 拷贝后的新字段
   */
  private static Schema.Field copyField(Schema.Field field, Schema newSchema, Integer fieldId) {
    Schema newSchemaReordered;
    // if the newSchema is an optional schema, make sure the NULL option is always the first
    if (isOptionSchemaWithNonNullFirstOption(newSchema)) {
      newSchemaReordered = AvroSchemaUtil.toOption(AvroSchemaUtil.fromOption(newSchema));
    } else {
      newSchemaReordered = newSchema;
    }
    // do not copy over default values as the file is expected to have values for fields already in
    // the file schema
    Schema.Field copy =
        new Schema.Field(
            field.name(),
            newSchemaReordered,
            field.doc(),
            AvroSchemaUtil.isOptionSchema(newSchemaReordered) ? JsonProperties.NULL_VALUE : null,
            field.order());

    for (Map.Entry<String, Object> prop : field.getObjectProps().entrySet()) {
      copy.addProp(prop.getKey(), prop.getValue());
    }

    if (AvroSchemaUtil.hasFieldId(field)) {
      int existingFieldId = AvroSchemaUtil.getFieldId(field);
      Preconditions.checkArgument(
          existingFieldId == fieldId,
          "Existing field does match with that fetched from name mapping");
    } else {
      // field may not have a fieldId if the fieldId was fetched from nameMapping
      copy.addProp(AvroSchemaUtil.FIELD_ID_PROP, fieldId);
    }

    return copy;
  }

  /**
   * 判断 schema 是否为 nullable option 且 null 不在 union 第一位（需重排的场景）。
   *
   * @param schema 待判断 schema
   * @return true 表示是 nullable 且非 null 在前
   */
  private static boolean isOptionSchemaWithNonNullFirstOption(Schema schema) {
    return AvroSchemaUtil.isOptionSchema(schema)
        && schema.getTypes().get(0).getType() != Schema.Type.NULL;
  }
}
