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
package org.apache.iceberg;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.JsonUtil;

/**
 * Schema 的 JSON 序列化/反序列化器。
 *
 * <p>所属模块：iceberg-core。职责：把 {@link Schema} 与 {@link Type} 树与 JSON 互转， 是表元数据持久化的核心组件之一。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>类型分发：按 {@link Type.TypeID} 分发到 struct/list/map/基础类型的读写分支。
 *   <li>字段 id 持久化：每个字段都写 id，支持 schema 演化（重命名/重排）。
 *   <li>identifier-field-ids：单独保存主键字段 id 列表。
 *   <li>Caffeine 缓存：缓存类型到 JSON 片段，减少重复计算。
 * </ul>
 *
 * <p>上下游关系：被 {@link TableMetadataParser}、各 REST/序列化层调用；依赖 {@link JsonUtil}。
 */
public class SchemaParser {

  /** 私有构造：工具类禁止实例化。 */
  private SchemaParser() {}

  private static final String SCHEMA_ID = "schema-id";
  private static final String IDENTIFIER_FIELD_IDS = "identifier-field-ids";
  private static final String TYPE = "type";
  private static final String STRUCT = "struct";
  private static final String LIST = "list";
  private static final String MAP = "map";
  private static final String FIELDS = "fields";
  private static final String ELEMENT = "element";
  private static final String KEY = "key";
  private static final String VALUE = "value";
  private static final String DOC = "doc";
  private static final String NAME = "name";
  private static final String ID = "id";
  private static final String ELEMENT_ID = "element-id";
  private static final String KEY_ID = "key-id";
  private static final String VALUE_ID = "value-id";
  private static final String REQUIRED = "required";
  private static final String ELEMENT_REQUIRED = "element-required";
  private static final String VALUE_REQUIRED = "value-required";

  /**
   * 把 StructType 写入 JSON 生成器（不带 schema-id 与 identifier-field-ids）。
   *
   * @param struct struct 类型
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  private static void toJson(Types.StructType struct, JsonGenerator generator) throws IOException {
    toJson(struct, null, null, generator);
  }

  /**
   * 把 StructType 写入 JSON 生成器，可选附加 schema-id 与 identifier-field-ids。
   *
   * <p>输出格式：
   *
   * <pre>
   * {type:"struct", schema-id:?, identifier-field-ids:[?], fields:[{id,name,required,type,doc?}]}
   * </pre>
   */
  private static void toJson(
      Types.StructType struct,
      Integer schemaId,
      Set<Integer> identifierFieldIds,
      JsonGenerator generator)
      throws IOException {
    generator.writeStartObject();

    generator.writeStringField(TYPE, STRUCT);
    if (schemaId != null) {
      generator.writeNumberField(SCHEMA_ID, schemaId);
    }

    if (identifierFieldIds != null && !identifierFieldIds.isEmpty()) {
      JsonUtil.writeIntegerArray(IDENTIFIER_FIELD_IDS, identifierFieldIds, generator);
    }

    generator.writeArrayFieldStart(FIELDS);
    for (Types.NestedField field : struct.fields()) {
      generator.writeStartObject();
      generator.writeNumberField(ID, field.fieldId());
      generator.writeStringField(NAME, field.name());
      generator.writeBooleanField(REQUIRED, field.isRequired());
      generator.writeFieldName(TYPE);
      toJson(field.type(), generator);
      if (field.doc() != null) {
        generator.writeStringField(DOC, field.doc());
      }
      generator.writeEndObject();
    }
    generator.writeEndArray();

    generator.writeEndObject();
  }

  /**
   * 把 ListType 写入 JSON 生成器。
   *
   * @param list list 类型
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  static void toJson(Types.ListType list, JsonGenerator generator) throws IOException {
    generator.writeStartObject();

    generator.writeStringField(TYPE, LIST);

    generator.writeNumberField(ELEMENT_ID, list.elementId());
    generator.writeFieldName(ELEMENT);
    toJson(list.elementType(), generator);
    generator.writeBooleanField(ELEMENT_REQUIRED, !list.isElementOptional());

    generator.writeEndObject();
  }

  /**
   * 把 MapType 写入 JSON 生成器。
   *
   * @param map map 类型
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  static void toJson(Types.MapType map, JsonGenerator generator) throws IOException {
    generator.writeStartObject();

    generator.writeStringField(TYPE, MAP);

    generator.writeNumberField(KEY_ID, map.keyId());
    generator.writeFieldName(KEY);
    toJson(map.keyType(), generator);

    generator.writeNumberField(VALUE_ID, map.valueId());
    generator.writeFieldName(VALUE);
    toJson(map.valueType(), generator);
    generator.writeBooleanField(VALUE_REQUIRED, !map.isValueOptional());

    generator.writeEndObject();
  }

  /**
   * 把原始类型写入 JSON 生成器（直接写字符串形式，如 "long"、"string"）。
   *
   * @param primitive 原始类型
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  static void toJson(Type.PrimitiveType primitive, JsonGenerator generator) throws IOException {
    generator.writeString(primitive.toString());
  }

  /**
   * 按类型分发到 struct/list/map/primitive 各自的写入方法。
   *
   * @param type 任意 Iceberg 类型
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  static void toJson(Type type, JsonGenerator generator) throws IOException {
    if (type.isPrimitiveType()) {
      toJson(type.asPrimitiveType(), generator);
    } else {
      Type.NestedType nested = type.asNestedType();
      switch (type.typeId()) {
        case STRUCT:
          toJson(nested.asStructType(), generator);
          break;
        case LIST:
          toJson(nested.asListType(), generator);
          break;
        case MAP:
          toJson(nested.asMapType(), generator);
          break;
        default:
          throw new IllegalArgumentException("Cannot write unknown type: " + type);
      }
    }
  }

  /**
   * 把 Schema 写入 JSON 生成器（带 schema-id 与 identifier-field-ids）。
   *
   * @param schema 表 schema
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  public static void toJson(Schema schema, JsonGenerator generator) throws IOException {
    toJson(schema.asStruct(), schema.schemaId(), schema.identifierFieldIds(), generator);
  }

  /**
   * 把 Schema 序列化为 JSON 字符串（紧凑形式）。
   *
   * @param schema 表 schema
   * @return JSON 字符串
   */
  public static String toJson(Schema schema) {
    return toJson(schema, false);
  }

  /**
   * 把 Schema 序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param schema 表 schema
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(Schema schema, boolean pretty) {
    return JsonUtil.generate(
        gen -> toJson(schema.asStruct(), schema.schemaId(), schema.identifierFieldIds(), gen),
        pretty);
  }

  /**
   * 从 JSON 节点解析出任意 {@link Type}。
   *
   * <p>步骤：文本节点视为原始类型；对象节点按 type 字段分发到 struct/list/map。
   *
   * @param json JSON 节点
   * @return 解析得到的类型
   * @throws IllegalArgumentException 当 JSON 无法识别为有效类型时
   */
  private static Type typeFromJson(JsonNode json) {
    if (json.isTextual()) {
      return Types.fromPrimitiveString(json.asText());
    } else if (json.isObject()) {
      JsonNode typeObj = json.get(TYPE);
      if (typeObj != null) {
        String type = typeObj.asText();
        if (STRUCT.equals(type)) {
          return structFromJson(json);
        } else if (LIST.equals(type)) {
          return listFromJson(json);
        } else if (MAP.equals(type)) {
          return mapFromJson(json);
        }
      }
    }

    throw new IllegalArgumentException("Cannot parse type from json: " + json);
  }

  /**
   * 从 JSON 节点解析 StructType。
   *
   * <p>步骤：取 fields 数组，逐字段解析 id/name/type/doc/required 并构造 NestedField。
   *
   * @param json JSON 节点
   * @return 解析得到的 StructType
   */
  private static Types.StructType structFromJson(JsonNode json) {
    JsonNode fieldArray = JsonUtil.get(FIELDS, json);
    Preconditions.checkArgument(
        fieldArray.isArray(), "Cannot parse struct fields from non-array: %s", fieldArray);

    List<Types.NestedField> fields = Lists.newArrayListWithExpectedSize(fieldArray.size());
    Iterator<JsonNode> iterator = fieldArray.elements();
    while (iterator.hasNext()) {
      JsonNode field = iterator.next();
      Preconditions.checkArgument(
          field.isObject(), "Cannot parse struct field from non-object: %s", field);

      int id = JsonUtil.getInt(ID, field);
      String name = JsonUtil.getString(NAME, field);
      Type type = typeFromJson(JsonUtil.get(TYPE, field));

      String doc = JsonUtil.getStringOrNull(DOC, field);
      boolean isRequired = JsonUtil.getBool(REQUIRED, field);
      if (isRequired) {
        fields.add(Types.NestedField.required(id, name, type, doc));
      } else {
        fields.add(Types.NestedField.optional(id, name, type, doc));
      }
    }

    return Types.StructType.of(fields);
  }

  /**
   * 从 JSON 节点解析 ListType。
   *
   * @param json JSON 节点
   * @return 解析得到的 ListType
   */
  private static Types.ListType listFromJson(JsonNode json) {
    int elementId = JsonUtil.getInt(ELEMENT_ID, json);
    Type elementType = typeFromJson(JsonUtil.get(ELEMENT, json));
    boolean isRequired = JsonUtil.getBool(ELEMENT_REQUIRED, json);

    if (isRequired) {
      return Types.ListType.ofRequired(elementId, elementType);
    } else {
      return Types.ListType.ofOptional(elementId, elementType);
    }
  }

  /**
   * 从 JSON 节点解析 MapType。
   *
   * @param json JSON 节点
   * @return 解析得到的 MapType
   */
  private static Types.MapType mapFromJson(JsonNode json) {
    int keyId = JsonUtil.getInt(KEY_ID, json);
    Type keyType = typeFromJson(JsonUtil.get(KEY, json));

    int valueId = JsonUtil.getInt(VALUE_ID, json);
    Type valueType = typeFromJson(JsonUtil.get(VALUE, json));

    boolean isRequired = JsonUtil.getBool(VALUE_REQUIRED, json);

    if (isRequired) {
      return Types.MapType.ofRequired(keyId, valueId, keyType, valueType);
    } else {
      return Types.MapType.ofOptional(keyId, valueId, keyType, valueType);
    }
  }

  /**
   * 从 JSON 节点解析 Schema。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>调用 {@link #typeFromJson} 取类型，校验必须是 struct；
   *   <li>读取可选的 schema-id 与 identifier-field-ids；
   *   <li>schema-id 为 null 时构造无 id 的 Schema，否则带 id 构造。
   * </ol>
   *
   * @param json JSON 节点
   * @return 解析得到的 Schema
   * @throws IllegalArgumentException 当解析出的类型不是 struct 时
   */
  public static Schema fromJson(JsonNode json) {
    Type type = typeFromJson(json);
    Preconditions.checkArgument(
        type.isNestedType() && type.asNestedType().isStructType(),
        "Cannot create schema, not a struct type: %s",
        type);
    Integer schemaId = JsonUtil.getIntOrNull(SCHEMA_ID, json);
    Set<Integer> identifierFieldIds = JsonUtil.getIntegerSetOrNull(IDENTIFIER_FIELD_IDS, json);

    if (schemaId == null) {
      return new Schema(type.asNestedType().asStructType().fields(), identifierFieldIds);
    } else {
      return new Schema(schemaId, type.asNestedType().asStructType().fields(), identifierFieldIds);
    }
  }

  /** Schema 反序列化结果缓存。键为 JSON 字符串，值为弱引用 Schema，避免热路径重复解析。 */
  private static final Cache<String, Schema> SCHEMA_CACHE =
      Caffeine.newBuilder().weakValues().build();

  /**
   * 从 JSON 字符串解析 Schema（带缓存）。
   *
   * @param json JSON 字符串
   * @return 解析得到的 Schema（可能来自缓存）
   */
  public static Schema fromJson(String json) {
    return SCHEMA_CACHE.get(json, jsonKey -> JsonUtil.parse(json, SchemaParser::fromJson));
  }
}
