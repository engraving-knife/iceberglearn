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
import java.util.Map;
import java.util.Set;
import org.apache.avro.JsonProperties;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.iceberg.mapping.MappedField;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：Iceberg Schema 与 Avro Schema 之间的转换工具集（静态方法集合）。
 *
 * <p>所属模块：iceberg-core（avro 子包）。职责：提供 Iceberg Type/Schema 与 Avro Schema 的双向转换、列裁剪、投影构建、字段 ID
 * 读写、schema 拷贝等核心工具方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>作为 avro 子包的"门面"工具类，把 schema 转换/裁剪/投影的复杂逻辑集中暴露， 供读写器、manifest 解析等上层逻辑调用。
 *   <li>字段 ID 以 Avro 自定义属性（FIELD_ID_PROP / ELEMENT_ID_PROP 等）挂在 schema 节点上， 兼容原生 Avro 库。
 *   <li>支持 NameMapping：当文件 schema 无 ID 时，按字段名映射重建 ID。
 *   <li>map 类型区分 string key（Avro 原生 map）与非 string key（key-value array + LogicalMap）。
 * </ul>
 *
 * <p>上下游关系：被 {@link GenericAvroReader}、{@link GenericAvroWriter}、{@link PruneColumns}、 {@link
 * ProjectionDatumReader} 等广泛依赖。
 */
public class AvroSchemaUtil {

  private AvroSchemaUtil() {}

  // 原始 Iceberg 字段名（对应被 sanitize 后的 Avro 名）
  public static final String ICEBERG_FIELD_NAME_PROP = "iceberg-field-name";
  /** Avro schema 上记录字段 ID 的属性名。 */
  public static final String FIELD_ID_PROP = "field-id";
  /** map key 字段 ID 属性名。 */
  public static final String KEY_ID_PROP = "key-id";
  /** map value 字段 ID 属性名。 */
  public static final String VALUE_ID_PROP = "value-id";
  /** array 元素 ID 属性名。 */
  public static final String ELEMENT_ID_PROP = "element-id";
  /** timestamp 是否需调整到 UTC 的属性名。 */
  public static final String ADJUST_TO_UTC_PROP = "adjust-to-utc";

  private static final Schema NULL = Schema.create(Schema.Type.NULL);
  private static final Schema.Type MAP = Schema.Type.MAP;
  private static final Schema.Type ARRAY = Schema.Type.ARRAY;
  private static final Schema.Type UNION = Schema.Type.UNION;
  private static final Schema.Type RECORD = Schema.Type.RECORD;

  /**
   * 把 Iceberg Schema 转为 Avro schema，用表名命名根 record。
   *
   * @param schema Iceberg schema
   * @param tableName 表名（用于根 record 命名）
   * @return Avro schema
   */
  public static Schema convert(org.apache.iceberg.Schema schema, String tableName) {
    return convert(schema, ImmutableMap.of(schema.asStruct(), tableName));
  }

  /**
   * 把 Iceberg Schema 转为 Avro schema，用 names 映射命名各 struct。
   *
   * @param schema Iceberg schema
   * @param names struct 类型到 record 名的映射
   * @return Avro schema
   */
  public static Schema convert(
      org.apache.iceberg.Schema schema, Map<Types.StructType, String> names) {
    return TypeUtil.visit(schema, new TypeToSchema(names));
  }

  /**
   * 把 Iceberg Type 转为 Avro schema（无命名映射）。
   *
   * @param type Iceberg 类型
   * @return Avro schema
   */
  public static Schema convert(Type type) {
    return convert(type, ImmutableMap.of());
  }

  /**
   * 把 Iceberg StructType 转为 Avro schema，用 name 命名该 struct。
   *
   * @param type struct 类型
   * @param name record 名
   * @return Avro schema
   */
  public static Schema convert(Types.StructType type, String name) {
    return convert(type, ImmutableMap.of(type, name));
  }

  /**
   * 把 Iceberg Type 转为 Avro schema，用 names 映射命名各 struct。
   *
   * @param type Iceberg 类型
   * @param names struct 到 record 名映射
   * @return Avro schema
   */
  public static Schema convert(Type type, Map<Types.StructType, String> names) {
    return TypeUtil.visit(type, new TypeToSchema(names));
  }

  /**
   * 把 Avro schema 转为 Iceberg Type。
   *
   * @param schema Avro schema
   * @return Iceberg Type
   */
  public static Type convert(Schema schema) {
    return AvroSchemaVisitor.visit(schema, new SchemaToType(schema));
  }

  /**
   * 把 Avro schema 转为 Iceberg Schema（取根 record 字段构造）。
   *
   * @param schema Avro schema
   * @return Iceberg Schema
   */
  public static org.apache.iceberg.Schema toIceberg(Schema schema) {
    final List<Types.NestedField> fields = convert(schema).asNestedType().asStructType().fields();
    return new org.apache.iceberg.Schema(fields);
  }

  /**
   * 判断 Avro schema 是否携带 Iceberg ID 属性。
   *
   * @param schema Avro schema
   * @return true 表示含 ID
   */
  static boolean hasIds(Schema schema) {
    return AvroCustomOrderSchemaVisitor.visit(schema, new HasIds());
  }

  /**
   * Check if any of the nodes in a given avro schema is missing an ID
   *
   * <p>To have an ID for a node:
   *
   * <ul>
   *   <li>a field node under struct (record) schema should have {@link #FIELD_ID_PROP} property
   *   <li>an element node under list (array) schema should have {@link #ELEMENT_ID_PROP} property
   *   <li>a pair of key and value node under map schema should have {@link #KEY_ID_PROP} and {@link
   *       #VALUE_ID_PROP} respectively
   *   <li>a primitive node is not assigned any ID properties
   * </ul>
   *
   * <p>
   *
   * @param schema an Avro Schema
   * @return true if any of the nodes of the given Avro Schema is missing an ID property, false
   *     otherwise
   */
  static boolean missingIds(Schema schema) {
    return AvroCustomOrderSchemaVisitor.visit(schema, new MissingIds());
  }

  /**
   * 把 Iceberg StructType 转换为 Avro Schema，返回类型映射表。
   *
   * @param type Iceberg StructType
   * @param name 根 record 名称
   * @return 类型映射表
   */
  public static Map<Type, Schema> convertTypes(Types.StructType type, String name) {
    TypeToSchema converter = new TypeToSchema(ImmutableMap.of(type, name));
    TypeUtil.visit(type, converter);
    return ImmutableMap.copyOf(converter.getConversionMap());
  }

  /**
   * 按选定字段 ID 集合裁剪 Avro Schema，支持 NameMapping。
   *
   * @param schema 原 Avro Schema
   * @param selectedIds 需要保留的字段 ID 集合
   * @param nameMapping 字段名到 ID 的映射（可为 null）
   * @return 裁剪后的 Avro Schema
   */
  public static Schema pruneColumns(
      Schema schema, Set<Integer> selectedIds, NameMapping nameMapping) {
    return new PruneColumns(selectedIds, nameMapping).rootSchema(schema);
  }

  /**
   * 基于裁剪后的 schema 和期望 schema 构建投影 schema，处理字段重命名。
   *
   * @param schema 裁剪后的 Avro Schema
   * @param expected 期望的 Iceberg Schema
   * @param renames 字段重命名映射
   * @return 投影后的 Avro Schema
   */
  public static Schema buildAvroProjection(
      Schema schema, org.apache.iceberg.Schema expected, Map<String, String> renames) {
    return AvroCustomOrderSchemaVisitor.visit(schema, new BuildAvroProjection(expected, renames));
  }

  /**
   * 判断 schema 是否为带时区的 timestamp 类型。
   *
   * @param schema Avro Schema
   * @return true 如果是 timestamptz
   */
  public static boolean isTimestamptz(Schema schema) {
    LogicalType logicalType = schema.getLogicalType();
    if (logicalType instanceof LogicalTypes.TimestampMillis
        || logicalType instanceof LogicalTypes.TimestampMicros) {
      // timestamptz is adjusted to UTC
      Object value = schema.getObjectProp(ADJUST_TO_UTC_PROP);

      if (value == null) {
        // not all avro timestamp logical types will have the adjust_to_utc prop, default to
        // timestamp without timezone
        return false;
      } else if (value instanceof Boolean) {
        return (Boolean) value;
      } else if (value instanceof String) {
        return Boolean.parseBoolean((String) value);
      }
    }

    return false;
  }

  /**
   * 判断 schema 是否为 nullable union（null, T）。
   *
   * @param schema Avro Schema
   * @return true 如果是 option schema
   */
  public static boolean isOptionSchema(Schema schema) {
    if (schema.getType() == UNION && schema.getTypes().size() == 2) {
      if (schema.getTypes().get(0).getType() == Schema.Type.NULL) {
        return true;
      } else if (schema.getTypes().get(1).getType() == Schema.Type.NULL) {
        return true;
      }
    }
    return false;
  }

  static Schema toOption(Schema schema) {
    if (schema.getType() == UNION) {
      Preconditions.checkArgument(
          isOptionSchema(schema), "Union schemas are not supported: %s", schema);
      return schema;
    } else {
      return Schema.createUnion(NULL, schema);
    }
  }

  static Schema fromOption(Schema schema) {
    Preconditions.checkArgument(
        schema.getType() == UNION, "Expected union schema but was passed: %s", schema);
    Preconditions.checkArgument(
        schema.getTypes().size() == 2, "Expected optional schema, but was passed: %s", schema);
    if (schema.getTypes().get(0).getType() == Schema.Type.NULL) {
      return schema.getTypes().get(1);
    } else {
      return schema.getTypes().get(0);
    }
  }

  static Schema fromOptions(List<Schema> options) {
    Preconditions.checkArgument(
        options.size() == 2, "Expected two schemas, but was passed: %s options", options.size());
    if (options.get(0).getType() == Schema.Type.NULL) {
      return options.get(1);
    } else {
      return options.get(0);
    }
  }

  /**
   * 判断 schema 是否为合法的 key-value 二字段 record。
   *
   * @param schema Avro Schema
   * @return true 如果是 key-value record
   */
  public static boolean isKeyValueSchema(Schema schema) {
    return schema.getType() == RECORD && schema.getFields().size() == 2;
  }

  static Schema createMap(int keyId, Schema keySchema, int valueId, Schema valueSchema) {
    String keyValueName = "k" + keyId + "_v" + valueId;

    Schema.Field keyField = new Schema.Field("key", keySchema, null, (Object) null);
    keyField.addProp(FIELD_ID_PROP, keyId);

    Schema.Field valueField =
        new Schema.Field(
            "value",
            valueSchema,
            null,
            isOptionSchema(valueSchema) ? JsonProperties.NULL_VALUE : null);
    valueField.addProp(FIELD_ID_PROP, valueId);

    return LogicalMap.get()
        .addToSchema(
            Schema.createArray(
                Schema.createRecord(
                    keyValueName, null, null, false, ImmutableList.of(keyField, valueField))));
  }

  static Schema createProjectionMap(
      String recordName,
      int keyId,
      String keyName,
      Schema keySchema,
      int valueId,
      String valueName,
      Schema valueSchema) {
    String keyValueName = "k" + keyId + "_v" + valueId;

    Schema.Field keyField = new Schema.Field("key", keySchema, null, (Object) null);
    if (!"key".equals(keyName)) {
      keyField.addAlias(keyName);
    }
    keyField.addProp(FIELD_ID_PROP, keyId);

    Schema.Field valueField =
        new Schema.Field(
            "value",
            valueSchema,
            null,
            isOptionSchema(valueSchema) ? JsonProperties.NULL_VALUE : null);
    valueField.addProp(FIELD_ID_PROP, valueId);
    if (!"value".equals(valueName)) {
      valueField.addAlias(valueName);
    }

    Schema keyValueRecord =
        Schema.createRecord(
            keyValueName, null, null, false, ImmutableList.of(keyField, valueField));
    if (!keyValueName.equals(recordName)) {
      keyValueRecord.addAlias(recordName);
    }

    return LogicalMap.get().addToSchema(Schema.createArray(keyValueRecord));
  }

  private static Integer getId(Schema schema, String propertyName) {
    Integer id = getId(schema, propertyName, null, null);
    Preconditions.checkNotNull(id, "Missing expected '%s' property", propertyName);
    return id;
  }

  private static Integer getId(
      Schema schema, String propertyName, NameMapping nameMapping, List<String> names) {
    if (schema.getType() == UNION) {
      return getId(fromOption(schema), propertyName, nameMapping, names);
    }

    Object id = schema.getObjectProp(propertyName);
    if (id != null) {
      return toInt(id);
    } else if (nameMapping != null) {
      MappedField mappedField = nameMapping.find(names);
      if (mappedField != null) {
        return mappedField.id();
      }
    }

    return null;
  }

  static boolean hasProperty(Schema schema, String propertyName) {
    if (schema.getType() == UNION) {
      return hasProperty(fromOption(schema), propertyName);
    }
    return schema.getObjectProp(propertyName) != null;
  }

  /**
   * 获取 Avro map schema 的 key ID。
   *
   * @param schema Avro map schema
   * @return key 字段 ID
   */
  public static int getKeyId(Schema schema) {
    Preconditions.checkArgument(
        schema.getType() == MAP, "Cannot get map key id for non-map schema: %s", schema);
    return getId(schema, KEY_ID_PROP);
  }

  static Integer getKeyId(
      Schema schema, NameMapping nameMapping, Iterable<String> parentFieldNames) {
    Preconditions.checkArgument(
        schema.getType() == MAP, "Cannot get map key id for non-map schema: %s", schema);
    List<String> names = Lists.newArrayList(parentFieldNames);
    names.add("key");
    return getId(schema, KEY_ID_PROP, nameMapping, names);
  }

  /**
   * 获取 Avro map schema 的 value ID。
   *
   * @param schema Avro map schema
   * @return value 字段 ID
   */
  public static int getValueId(Schema schema) {
    Preconditions.checkArgument(
        schema.getType() == MAP, "Cannot get map value id for non-map schema: %s", schema);
    return getId(schema, VALUE_ID_PROP);
  }

  static Integer getValueId(
      Schema schema, NameMapping nameMapping, Iterable<String> parentFieldNames) {
    Preconditions.checkArgument(
        schema.getType() == MAP, "Cannot get map value id for non-map schema: %s", schema);
    List<String> names = Lists.newArrayList(parentFieldNames);
    names.add("value");
    return getId(schema, VALUE_ID_PROP, nameMapping, names);
  }

  /**
   * 获取 Avro array schema 的元素 ID。
   *
   * @param schema Avro array schema
   * @return 元素 ID
   */
  public static int getElementId(Schema schema) {
    Preconditions.checkArgument(
        schema.getType() == ARRAY, "Cannot get array element id for non-array schema: %s", schema);
    return getId(schema, ELEMENT_ID_PROP);
  }

  static Integer getElementId(
      Schema schema, NameMapping nameMapping, Iterable<String> parentFieldNames) {
    Preconditions.checkArgument(
        schema.getType() == ARRAY, "Cannot get array element id for non-array schema: %s", schema);
    List<String> names = Lists.newArrayList(parentFieldNames);
    names.add("element");
    return getId(schema, ELEMENT_ID_PROP, nameMapping, names);
  }

  /**
   * 获取 Avro 字段的 ID。
   *
   * @param field Avro 字段
   * @return 字段 ID
   */
  public static int getFieldId(Schema.Field field) {
    Integer id = getFieldId(field, null, null);
    Preconditions.checkNotNull(id, "Missing expected '%s' property", FIELD_ID_PROP);
    return id;
  }

  static Integer getFieldId(
      Schema.Field field, NameMapping nameMapping, Iterable<String> parentFieldNames) {
    Object id = field.getObjectProp(FIELD_ID_PROP);
    if (id != null) {
      return toInt(id);
    } else if (nameMapping != null) {
      List<String> names = Lists.newArrayList(parentFieldNames);
      names.add(field.name());
      MappedField mappedField = nameMapping.find(names);
      if (mappedField != null) {
        return mappedField.id();
      }
    }

    return null;
  }

  /**
   * 判断 Avro 字段是否携带字段 ID 属性。
   *
   * @param field Avro 字段
   * @return true 如果有字段 ID
   */
  public static boolean hasFieldId(Schema.Field field) {
    return field.getObjectProp(FIELD_ID_PROP) != null;
  }

  private static int toInt(Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    } else if (value instanceof String) {
      return Integer.parseInt((String) value);
    }

    throw new UnsupportedOperationException("Cannot coerce value to int: " + value);
  }

  static Schema copyRecord(Schema record, List<Schema.Field> newFields, String newName) {
    Schema copy;
    if (newName != null) {
      copy = Schema.createRecord(newName, record.getDoc(), null, record.isError(), newFields);
      // the namespace is defaulted to the record's namespace if it is null, which causes renames
      // without the namespace to fail. using "" instead of null changes this behavior to match the
      // original schema.
      copy.addAlias(record.getName(), record.getNamespace() == null ? "" : record.getNamespace());
    } else {
      copy =
          Schema.createRecord(
              record.getName(),
              record.getDoc(),
              record.getNamespace(),
              record.isError(),
              newFields);
    }

    for (Map.Entry<String, Object> prop : record.getObjectProps().entrySet()) {
      copy.addProp(prop.getKey(), prop.getValue());
    }

    return copy;
  }

  static Schema.Field copyField(Schema.Field field, Schema newSchema, String newName) {
    Schema.Field copy =
        new Schema.Field(newName, newSchema, field.doc(), field.defaultVal(), field.order());

    for (Map.Entry<String, Object> prop : field.getObjectProps().entrySet()) {
      copy.addProp(prop.getKey(), prop.getValue());
    }

    if (!newName.equals(field.name())) {
      copy.addAlias(field.name());
    }

    return copy;
  }

  static Schema replaceElement(Schema array, Schema elementSchema) {
    Preconditions.checkArgument(
        array.getType() == ARRAY, "Cannot invoke replaceElement on non array schema: %s", array);
    Schema copy = Schema.createArray(elementSchema);
    for (Map.Entry<String, Object> prop : array.getObjectProps().entrySet()) {
      copy.addProp(prop.getKey(), prop.getValue());
    }
    return copy;
  }

  static Schema replaceValue(Schema map, Schema valueSchema) {
    Preconditions.checkArgument(
        map.getType() == MAP, "Cannot invoke replaceValue on non map schema: %s", map);
    Schema copy = Schema.createMap(valueSchema);
    for (Map.Entry<String, Object> prop : map.getObjectProps().entrySet()) {
      copy.addProp(prop.getKey(), prop.getValue());
    }
    return copy;
  }

  /**
   * 把字段名转换为 Avro 兼容的合法名称。
   *
   * @param name 原始字段名
   * @return Avro 兼容的字段名
   */
  public static String makeCompatibleName(String name) {
    if (!validAvroName(name)) {
      return sanitize(name);
    }
    return name;
  }

  static boolean validAvroName(String name) {
    int length = name.length();
    Preconditions.checkArgument(length > 0, "Empty name");
    char first = name.charAt(0);
    if (!(Character.isLetter(first) || first == '_')) {
      return false;
    }

    for (int i = 1; i < length; i++) {
      char character = name.charAt(i);
      if (!(Character.isLetterOrDigit(character) || character == '_')) {
        return false;
      }
    }
    return true;
  }

  static String sanitize(String name) {
    int length = name.length();
    StringBuilder sb = new StringBuilder(name.length());
    char first = name.charAt(0);
    if (!(Character.isLetter(first) || first == '_')) {
      sb.append(sanitize(first));
    } else {
      sb.append(first);
    }

    for (int i = 1; i < length; i++) {
      char character = name.charAt(i);
      if (!(Character.isLetterOrDigit(character) || character == '_')) {
        sb.append(sanitize(character));
      } else {
        sb.append(character);
      }
    }
    return sb.toString();
  }

  private static String sanitize(char character) {
    if (Character.isDigit(character)) {
      return "_" + character;
    }
    return "_x" + Integer.toHexString(character).toUpperCase();
  }
}
