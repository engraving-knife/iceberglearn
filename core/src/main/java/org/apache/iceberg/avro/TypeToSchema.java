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

import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.apache.avro.JsonProperties;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：Iceberg Type → Avro Schema 的转换器（visitor 模式）。
 *
 * <p>所属模块：iceberg-core（avro 子包）。职责：遍历 Iceberg 类型树并生成对应的 Avro schema， 在每个节点上附加 Iceberg 私有的字段/元素 ID
 * 属性，以支持 schema 演化。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>与 {@link SchemaToType} 形成双向转换对。
 *   <li>预创建常用 primitive schema 常量（BOOLEAN_SCHEMA、INTEGER_SCHEMA 等）， 避免重复创建对象。
 *   <li>对 map 类型区分 string key（用 Avro 原生 map）和非 string key（用 key-value array + {@link
 *       LogicalMap}），兼顾性能与通用性。
 *   <li>通过 results 缓存已转换的 schema，支持递归类型的共享引用。
 *   <li>字段名非法时做 sanitize，并在 ICEBERG_FIELD_NAME_PROP 中保留原名。
 * </ul>
 *
 * <p>上下游关系：被 {@link AvroSchemaUtil#convert(org.apache.iceberg.types.Type, String)} 调用； 输出 Avro
 * Schema 供 Avro 读写器使用。
 */
class TypeToSchema extends TypeUtil.SchemaVisitor<Schema> {
  private static final Schema BOOLEAN_SCHEMA = Schema.create(Schema.Type.BOOLEAN);
  private static final Schema INTEGER_SCHEMA = Schema.create(Schema.Type.INT);
  private static final Schema LONG_SCHEMA = Schema.create(Schema.Type.LONG);
  private static final Schema FLOAT_SCHEMA = Schema.create(Schema.Type.FLOAT);
  private static final Schema DOUBLE_SCHEMA = Schema.create(Schema.Type.DOUBLE);
  private static final Schema DATE_SCHEMA =
      LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
  private static final Schema TIME_SCHEMA =
      LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG));
  private static final Schema TIMESTAMP_SCHEMA =
      LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
  private static final Schema TIMESTAMPTZ_SCHEMA =
      LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
  private static final Schema STRING_SCHEMA = Schema.create(Schema.Type.STRING);
  private static final Schema UUID_SCHEMA =
      LogicalTypes.uuid().addToSchema(Schema.createFixed("uuid_fixed", null, null, 16));
  private static final Schema BINARY_SCHEMA = Schema.create(Schema.Type.BYTES);

  static {
    TIMESTAMP_SCHEMA.addProp(AvroSchemaUtil.ADJUST_TO_UTC_PROP, false);
    TIMESTAMPTZ_SCHEMA.addProp(AvroSchemaUtil.ADJUST_TO_UTC_PROP, true);
  }

  private final Deque<Integer> fieldIds = Lists.newLinkedList();
  private final Map<Type, Schema> results = Maps.newHashMap();
  private final Map<Types.StructType, String> names;

  /**
   * 构造转换器，传入 struct 类型到 record 名的映射（用于命名生成的 record）。
   *
   * @param names struct 类型到 record 名的映射
   */
  TypeToSchema(Map<Types.StructType, String> names) {
    this.names = names;
  }

  /**
   * 返回类型到已转换 schema 的缓存映射（支持递归类型共享引用）。
   *
   * @return 转换缓存
   */
  Map<Type, Schema> getConversionMap() {
    return results;
  }

  /**
   * 处理根 Iceberg Schema：直接返回转换后的 struct schema。
   *
   * @param schema Iceberg 根 schema
   * @param structSchema 已转换的 struct schema
   * @return 根 Avro schema
   */
  @Override
  public Schema schema(org.apache.iceberg.Schema schema, Schema structSchema) {
    return structSchema;
  }

  /**
   * 进入字段前压入当前字段 ID，供子节点命名/记录使用。
   *
   * @param field 当前字段
   */
  @Override
  public void beforeField(Types.NestedField field) {
    fieldIds.push(field.fieldId());
  }

  /**
   * 离开字段后弹出字段 ID，恢复栈状态。
   *
   * @param field 当前字段
   */
  @Override
  public void afterField(Types.NestedField field) {
    fieldIds.pop();
  }

  /**
   * 处理 struct 节点：构建 Avro record schema。
   *
   * <p>逻辑步骤：
   *
   * <ol>
   *   <li>先查 results 缓存，命中则直接返回（支持递归类型）。
   *   <li>确定 record 名：优先 names 映射，否则用 "r"+父字段 ID。
   *   <li>逐字段构造 Avro Field：非法名做 sanitize 并保留原名到 ICEBERG_FIELD_NAME_PROP； 绑定 FIELD_ID_PROP；optional
   *       字段 default 设为 NULL。
   *   <li>创建 record 并放入缓存。
   * </ol>
   *
   * @param struct Iceberg struct 类型
   * @param fieldSchemas 各字段已转换 schema
   * @return Avro record schema
   */
  @Override
  public Schema struct(Types.StructType struct, List<Schema> fieldSchemas) {
    Schema recordSchema = results.get(struct);
    if (recordSchema != null) {
      return recordSchema;
    }

    String recordName = names.get(struct);
    if (recordName == null) {
      recordName = "r" + fieldIds.peek();
    }

    List<Types.NestedField> structFields = struct.fields();
    List<Schema.Field> fields = Lists.newArrayListWithExpectedSize(fieldSchemas.size());
    for (int i = 0; i < structFields.size(); i += 1) {
      Types.NestedField structField = structFields.get(i);
      String origFieldName = structField.name();
      boolean isValidFieldName = AvroSchemaUtil.validAvroName(origFieldName);
      String fieldName = isValidFieldName ? origFieldName : AvroSchemaUtil.sanitize(origFieldName);
      Schema.Field field =
          new Schema.Field(
              fieldName,
              fieldSchemas.get(i),
              structField.doc(),
              structField.isOptional() ? JsonProperties.NULL_VALUE : null);
      if (!isValidFieldName) {
        field.addProp(AvroSchemaUtil.ICEBERG_FIELD_NAME_PROP, origFieldName);
      }
      field.addProp(AvroSchemaUtil.FIELD_ID_PROP, structField.fieldId());
      fields.add(field);
    }

    recordSchema = Schema.createRecord(recordName, null, null, false, fields);

    results.put(struct, recordSchema);

    return recordSchema;
  }

  /**
   * 处理字段：optional 字段包装为 nullable union，required 字段原样返回。
   *
   * @param field Iceberg 字段
   * @param fieldSchema 字段已转换 schema
   * @return 包装后的字段 schema
   */
  @Override
  public Schema field(Types.NestedField field, Schema fieldSchema) {
    if (field.isOptional()) {
      return AvroSchemaUtil.toOption(fieldSchema);
    } else {
      return fieldSchema;
    }
  }

  /**
   * 处理 list 节点：构建 Avro array schema 并附 ELEMENT_ID_PROP。
   *
   * <p>逻辑步骤：查缓存；元素 optional 则包装为 option；附元素 ID；放入缓存。
   *
   * @param list Iceberg list 类型
   * @param elementSchema 元素已转换 schema
   * @return Avro array schema
   */
  @Override
  public Schema list(Types.ListType list, Schema elementSchema) {
    Schema listSchema = results.get(list);
    if (listSchema != null) {
      return listSchema;
    }

    if (list.isElementOptional()) {
      listSchema = Schema.createArray(AvroSchemaUtil.toOption(elementSchema));
    } else {
      listSchema = Schema.createArray(elementSchema);
    }

    listSchema.addProp(AvroSchemaUtil.ELEMENT_ID_PROP, list.elementId());

    results.put(list, listSchema);

    return listSchema;
  }

  /**
   * 处理 map 节点：string key 用 Avro 原生 map，其他 key 用 key-value array + LogicalMap。
   *
   * <p>逻辑步骤：
   *
   * <ul>
   *   <li>查缓存命中则返回。
   *   <li>string key：用 Avro 原生 map，附 KEY_ID/VALUE_ID。
   *   <li>非 string key：用 {@link AvroSchemaUtil#createMap} 构造 key-value array 形式 （保留插入顺序），value
   *       optional 则包装 option。
   *   <li>放入缓存。
   * </ul>
   *
   * @param map Iceberg map 类型
   * @param keySchema key 已转换 schema
   * @param valueSchema value 已转换 schema
   * @return Avro map schema
   */
  @Override
  public Schema map(Types.MapType map, Schema keySchema, Schema valueSchema) {
    Schema mapSchema = results.get(map);
    if (mapSchema != null) {
      return mapSchema;
    }

    if (keySchema.getType() == Schema.Type.STRING) {
      // if the map has string keys, use Avro's map type
      mapSchema =
          Schema.createMap(
              map.isValueOptional() ? AvroSchemaUtil.toOption(valueSchema) : valueSchema);
      mapSchema.addProp(AvroSchemaUtil.KEY_ID_PROP, map.keyId());
      mapSchema.addProp(AvroSchemaUtil.VALUE_ID_PROP, map.valueId());

    } else {
      mapSchema =
          AvroSchemaUtil.createMap(
              map.keyId(),
              keySchema,
              map.valueId(),
              map.isValueOptional() ? AvroSchemaUtil.toOption(valueSchema) : valueSchema);
    }

    results.put(map, mapSchema);

    return mapSchema;
  }

  /**
   * 处理基本类型节点：按 Iceberg 类型 ID 路由到预创建的 schema 常量或动态构造。
   *
   * <p>逻辑步骤：BOOLEAN/INT/LONG/FLOAT/DOUBLE/STRING/BINARY 用常量；DATE/TIME/TIMESTAMP 用带 logicalType
   * 的常量（TIMESTAMP 按 shouldAdjustToUTC 选 with/without zone）； UUID 用 fixed(16)+uuid
   * 逻辑类型；FIXED/DECIMAL 按长度/精度动态构造；放入缓存。
   *
   * @param primitive Iceberg 基本类型
   * @return Avro primitive schema
   * @throws UnsupportedOperationException 不支持的类型时抛出
   */
  @Override
  public Schema primitive(Type.PrimitiveType primitive) {
    Schema primitiveSchema;
    switch (primitive.typeId()) {
      case BOOLEAN:
        primitiveSchema = BOOLEAN_SCHEMA;
        break;
      case INTEGER:
        primitiveSchema = INTEGER_SCHEMA;
        break;
      case LONG:
        primitiveSchema = LONG_SCHEMA;
        break;
      case FLOAT:
        primitiveSchema = FLOAT_SCHEMA;
        break;
      case DOUBLE:
        primitiveSchema = DOUBLE_SCHEMA;
        break;
      case DATE:
        primitiveSchema = DATE_SCHEMA;
        break;
      case TIME:
        primitiveSchema = TIME_SCHEMA;
        break;
      case TIMESTAMP:
        if (((Types.TimestampType) primitive).shouldAdjustToUTC()) {
          primitiveSchema = TIMESTAMPTZ_SCHEMA;
        } else {
          primitiveSchema = TIMESTAMP_SCHEMA;
        }
        break;
      case STRING:
        primitiveSchema = STRING_SCHEMA;
        break;
      case UUID:
        primitiveSchema = UUID_SCHEMA;
        break;
      case FIXED:
        Types.FixedType fixed = (Types.FixedType) primitive;
        primitiveSchema = Schema.createFixed("fixed_" + fixed.length(), null, null, fixed.length());
        break;
      case BINARY:
        primitiveSchema = BINARY_SCHEMA;
        break;
      case DECIMAL:
        Types.DecimalType decimal = (Types.DecimalType) primitive;
        primitiveSchema =
            LogicalTypes.decimal(decimal.precision(), decimal.scale())
                .addToSchema(
                    Schema.createFixed(
                        "decimal_" + decimal.precision() + "_" + decimal.scale(),
                        null,
                        null,
                        TypeUtil.decimalRequiredBytes(decimal.precision())));
        break;
      default:
        throw new UnsupportedOperationException("Unsupported type ID: " + primitive.typeId());
    }

    results.put(primitive, primitiveSchema);

    return primitiveSchema;
  }
}
