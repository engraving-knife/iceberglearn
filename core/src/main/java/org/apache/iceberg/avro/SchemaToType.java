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

import java.util.List;
import java.util.Objects;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：Avro Schema 到 Iceberg Type 的转换访问者。
 *
 * <p>所属模块：iceberg-core（avro 子包）。
 *
 * <p>职责：遍历 Avro Schema，把每个节点映射为对应的 Iceberg {@link Type} （StructType/MapType/ListType/基本类型），还原字段 ID。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>ID 还原：优先读取 Avro schema 上的 Iceberg ID 属性（FIELD_ID/ELEMENT_ID/KEY_ID/VALUE_ID）； 缺失时调用 {@link
 *       #allocateId} 按递增方式分配，保证读非 Iceberg 写入文件时也有唯一 ID。
 *   <li>根 record ID 起点重置：根 record 的字段从 0 开始分配，子节点续接，确保 ID 在整棵树唯一。
 *   <li>逻辑类型映射：Avro logicalType（decimal/date/time/timestamp/uuid）转 Iceberg 对应类型。
 *   <li>map-as-array 识别：携带 {@link LogicalMap} 标记的 array 解析为 MapType（保留插入顺序语义）。
 * </ul>
 *
 * <p>上下游关系：由 {@link AvroSchemaUtil#convert(Schema)} 调用，用于把外部 Avro schema 转为 Iceberg schema；与 {@link
 * TypeToSchema} 互为逆操作。
 */
class SchemaToType extends AvroSchemaVisitor<Type> {
  private final Schema root;

  /**
   * 构造转换器，记录根 schema 并初始化 ID 分配起点。
   *
   * <p>逻辑步骤：若根是 record，则 nextId 起点设为根字段数（预留根字段 ID 段），后续子节点续接。
   *
   * @param root 根 Avro schema
   */
  SchemaToType(Schema root) {
    this.root = root;
    if (root.getType() == Schema.Type.RECORD) {
      this.nextId = root.getFields().size();
    }
  }

  private int nextId = 1;

  /**
   * 取 array 元素 ID，缺失则分配新 ID。
   *
   * @param schema array schema
   * @return 元素 ID
   */
  private int getElementId(Schema schema) {
    if (schema.getObjectProp(AvroSchemaUtil.ELEMENT_ID_PROP) != null) {
      return AvroSchemaUtil.getElementId(schema);
    } else {
      return allocateId();
    }
  }

  /**
   * 取 map key ID，缺失则分配新 ID。
   *
   * @param schema map schema
   * @return key ID
   */
  private int getKeyId(Schema schema) {
    if (schema.getObjectProp(AvroSchemaUtil.KEY_ID_PROP) != null) {
      return AvroSchemaUtil.getKeyId(schema);
    } else {
      return allocateId();
    }
  }

  /**
   * 取 map value ID，缺失则分配新 ID。
   *
   * @param schema map schema
   * @return value ID
   */
  private int getValueId(Schema schema) {
    if (schema.getObjectProp(AvroSchemaUtil.VALUE_ID_PROP) != null) {
      return AvroSchemaUtil.getValueId(schema);
    } else {
      return allocateId();
    }
  }

  /**
   * 取字段 ID，缺失则分配新 ID。
   *
   * @param field Avro 字段
   * @return 字段 ID
   */
  private int getId(Schema.Field field) {
    if (field.getObjectProp(AvroSchemaUtil.FIELD_ID_PROP) != null) {
      return AvroSchemaUtil.getFieldId(field);
    } else {
      return allocateId();
    }
  }

  /**
   * 分配并返回下一个递增 ID。
   *
   * @return 新分配的 ID
   */
  private int allocateId() {
    int current = nextId;
    nextId += 1;
    return current;
  }

  /**
   * 处理 record 节点：构建 {@link Types.StructType}。
   *
   * <p>逻辑步骤：
   *
   * <ol>
   *   <li>若当前 record 是根 record，把 nextId 重置为 0，使根字段 ID 从 0 开始。
   *   <li>遍历字段，按 nullable 与否构建 optional/required NestedField，绑定字段 ID。
   * </ol>
   *
   * @param record record schema
   * @param names 字段名列表
   * @param fieldTypes 各字段转换后的 Iceberg 类型
   * @return StructType
   */
  @Override
  public Type record(Schema record, List<String> names, List<Type> fieldTypes) {
    List<Schema.Field> fields = record.getFields();
    List<Types.NestedField> newFields = Lists.newArrayListWithExpectedSize(fields.size());

    if (Objects.equals(root, record)) {
      this.nextId = 0;
    }

    for (int i = 0; i < fields.size(); i += 1) {
      Schema.Field field = fields.get(i);
      Type fieldType = fieldTypes.get(i);
      int fieldId = getId(field);

      if (AvroSchemaUtil.isOptionSchema(field.schema())) {
        newFields.add(Types.NestedField.optional(fieldId, field.name(), fieldType, field.doc()));
      } else {
        newFields.add(Types.NestedField.required(fieldId, field.name(), fieldType, field.doc()));
      }
    }

    return Types.StructType.of(newFields);
  }

  /**
   * 处理 union（仅 nullable option）：返回非 null 分支的类型。
   *
   * @param union union schema
   * @param options 各分支类型
   * @return 非 null 分支类型
   */
  @Override
  public Type union(Schema union, List<Type> options) {
    Preconditions.checkArgument(
        AvroSchemaUtil.isOptionSchema(union), "Unsupported type: non-option union: %s", union);
    // records, arrays, and maps will check nullability later
    if (options.get(0) == null) {
      return options.get(1);
    } else {
      return options.get(0);
    }
  }

  /**
   * 处理 array 节点：携带 {@link LogicalMap} 标记的转为 MapType，否则转 ListType。
   *
   * <p>逻辑步骤：
   *
   * <ul>
   *   <li>LogicalMap 情形：取 key/value struct 的两个字段，按 value 是否 nullable 构建 optional/required MapType。
   *   <li>普通 array 情形：取元素 ID，按元素是否 nullable 构建 optional/required ListType。
   * </ul>
   *
   * @param array array schema
   * @param elementType 元素转换结果
   * @return MapType 或 ListType
   */
  @Override
  public Type array(Schema array, Type elementType) {
    if (array.getLogicalType() instanceof LogicalMap) {
      // map stored as an array
      Schema keyValueSchema = array.getElementType();
      Preconditions.checkArgument(
          AvroSchemaUtil.isKeyValueSchema(keyValueSchema),
          "Invalid key-value pair schema: %s",
          keyValueSchema);

      Types.StructType keyValueType = elementType.asStructType();
      Types.NestedField keyField = keyValueType.field("key");
      Types.NestedField valueField = keyValueType.field("value");

      if (keyValueType.field("value").isOptional()) {
        return Types.MapType.ofOptional(
            keyField.fieldId(), valueField.fieldId(), keyField.type(), valueField.type());
      } else {
        return Types.MapType.ofRequired(
            keyField.fieldId(), valueField.fieldId(), keyField.type(), valueField.type());
      }

    } else {
      // normal array
      Schema elementSchema = array.getElementType();
      int id = getElementId(array);
      if (AvroSchemaUtil.isOptionSchema(elementSchema)) {
        return Types.ListType.ofOptional(id, elementType);
      } else {
        return Types.ListType.ofRequired(id, elementType);
      }
    }
  }

  /**
   * 处理标准 Avro map 节点：按 value 是否 nullable 构建 optional/required MapType，key 固定为字符串。
   *
   * @param map map schema
   * @param valueType value 转换结果
   * @return MapType
   */
  @Override
  public Type map(Schema map, Type valueType) {
    Schema valueSchema = map.getValueType();
    int keyId = getKeyId(map);
    int valueId = getValueId(map);

    if (AvroSchemaUtil.isOptionSchema(valueSchema)) {
      return Types.MapType.ofOptional(keyId, valueId, Types.StringType.get(), valueType);
    } else {
      return Types.MapType.ofRequired(keyId, valueId, Types.StringType.get(), valueType);
    }
  }

  /**
   * 处理基本类型节点：先按 Avro logicalType 映射 Iceberg 类型，再按 Avro 基础类型映射。
   *
   * <p>逻辑步骤：
   *
   * <ul>
   *   <li>优先识别 decimal/date/time/timestamp(with/without zone)/uuid 等逻辑类型。
   *   <li>否则按 Avro 基础类型（BOOLEAN/INT/LONG/FLOAT/DOUBLE/STRING/FIXED/BYTES 等）映射。
   *   <li>NULL 返回 null（由父节点处理 nullable 语义）。
   * </ul>
   *
   * @param primitive 基本 schema
   * @return Iceberg Type
   * @throws UnsupportedOperationException 不支持的类型时抛出
   */
  @Override
  public Type primitive(Schema primitive) {
    // first check supported logical types
    LogicalType logical = primitive.getLogicalType();
    if (logical != null) {
      String name = logical.getName();
      if (logical instanceof LogicalTypes.Decimal) {
        return Types.DecimalType.of(
            ((LogicalTypes.Decimal) logical).getPrecision(),
            ((LogicalTypes.Decimal) logical).getScale());

      } else if (logical instanceof LogicalTypes.Date) {
        return Types.DateType.get();

      } else if (logical instanceof LogicalTypes.TimeMillis
          || logical instanceof LogicalTypes.TimeMicros) {
        return Types.TimeType.get();

      } else if (logical instanceof LogicalTypes.TimestampMillis
          || logical instanceof LogicalTypes.TimestampMicros) {
        if (AvroSchemaUtil.isTimestamptz(primitive)) {
          return Types.TimestampType.withZone();
        } else {
          return Types.TimestampType.withoutZone();
        }

      } else if (LogicalTypes.uuid().getName().equals(name)) {
        return Types.UUIDType.get();
      }
    }

    switch (primitive.getType()) {
      case BOOLEAN:
        return Types.BooleanType.get();
      case INT:
        return Types.IntegerType.get();
      case LONG:
        return Types.LongType.get();
      case FLOAT:
        return Types.FloatType.get();
      case DOUBLE:
        return Types.DoubleType.get();
      case STRING:
      case ENUM:
        return Types.StringType.get();
      case FIXED:
        return Types.FixedType.ofLength(primitive.getFixedSize());
      case BYTES:
        return Types.BinaryType.get();
      case NULL:
        return null;
    }

    throw new UnsupportedOperationException("Unsupported primitive type: " + primitive);
  }
}
