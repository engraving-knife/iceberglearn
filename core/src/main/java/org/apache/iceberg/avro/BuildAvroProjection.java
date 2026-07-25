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
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 基于当前表 schema 对 Avro schema 做字段重命名与别名映射，构造读取投影 schema。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 文件读取时的 schema 投影/演进工具）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>依据当前表 schema 生成读取 schema，把文件中的字段名正确翻译为当前表 schema 的字段名， 以支持 schema 演进（字段重命名、增删、重排）。
 *   <li>对缺失的必填字段补充默认 null 的占位字段，确保读取不报错。
 *   <li>对 Avro record 做重命名（{@link #renames}），以支持自定义读取类。
 *   <li>处理类型提升（int→long、float→double）及 map-as-array（LogicalMap）投影。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link AvroCustomOrderSchemaVisitor}，按 Avro 文件 schema 自定义顺序遍历， 同时用 {@link #current}
 *       指针跟踪当前对应的 Iceberg 类型，实现两边对齐。
 *   <li>仅在 schema 确有变化时才返回新 schema，避免无谓拷贝；字段拷贝是必要的，因为 Avro {@link Schema.Field} 不可复用于不同 schema。
 *   <li>非线程安全：{@link #current} 为可变状态，仅供单次遍历使用。
 * </ul>
 *
 * <p>上下游关系：被 {@link AvroSchemaProjection} 等用于构造投影 schema；上游为 Avro 读取器 在打开文件时调用。
 */
class BuildAvroProjection extends AvroCustomOrderSchemaVisitor<Schema, Schema.Field> {
  /** record 全名到新名称的映射，用于支持自定义读取类时的 record 重命名。 */
  private final Map<String, String> renames;
  /** 当前正在处理的 Iceberg 类型指针，随遍历推进而更新，用于与 Avro 节点对齐。 */
  private Type current;

  /**
   * 以期望的表 schema 与 record 重命名映射构造投影器。
   *
   * @param expectedSchema 期望的表 schema
   * @param renames record 全名到新名称的映射
   */
  BuildAvroProjection(org.apache.iceberg.Schema expectedSchema, Map<String, String> renames) {
    this.renames = renames;
    this.current = expectedSchema.asStruct();
  }

  /**
   * 以期望的 Iceberg 类型与 record 重命名映射构造投影器。
   *
   * @param expectedType 期望的 Iceberg 类型
   * @param renames record 全名到新名称的映射
   */
  BuildAvroProjection(Type expectedType, Map<String, String> renames) {
    this.renames = renames;
    this.current = expectedType;
  }

  /**
   * 处理 record 节点：按当前表 schema 的字段顺序重排字段，处理重命名、缺失字段补充与变化检测。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验当前 Iceberg 类型为 struct；遍历文件字段，收集投影后的字段并标记是否有变化 （字段 schema/名称改变或字段被裁剪）。
   *   <li>按期望 struct 字段顺序构造结果字段列表：若文件中存在同名（兼容名）字段则复用投影结果， 否则对可选字段或元数据字段补一个默认 null
   *       的占位字段（带唯一后缀以防被误投影）。
   *   <li>检测字段重排；若存在变化或 record 需要重命名，则通过 {@link AvroSchemaUtil#copyRecord} 拷贝出新 record，否则原样返回。
   * </ol>
   *
   * @param record 文件 Avro record schema
   * @param names 字段名列表
   * @param schemaIterable 各字段投影结果
   * @return 投影后的 record schema
   */
  @Override
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public Schema record(Schema record, List<String> names, Iterable<Schema.Field> schemaIterable) {
    Preconditions.checkArgument(
        current.isNestedType() && current.asNestedType().isStructType(),
        "Cannot project non-struct: %s",
        current);

    Types.StructType struct = current.asNestedType().asStructType();

    boolean hasChange = false;
    List<Schema.Field> fields = record.getFields();
    List<Schema.Field> fieldResults = Lists.newArrayList(schemaIterable);

    Map<String, Schema.Field> updateMap = Maps.newHashMap();
    for (int i = 0; i < fields.size(); i += 1) {
      Schema.Field field = fields.get(i);
      Schema.Field updatedField = fieldResults.get(i);

      if (updatedField != null) {
        updateMap.put(updatedField.name(), updatedField);

        if (!updatedField.schema().equals(field.schema())
            || !updatedField.name().equals(field.name())) {
          hasChange = true;
        }
      } else {
        hasChange = true; // column was not projected
      }
    }

    // construct the schema using the expected order
    List<Schema.Field> updatedFields = Lists.newArrayListWithExpectedSize(struct.fields().size());
    List<Types.NestedField> expectedFields = struct.fields();
    for (int i = 0; i < expectedFields.size(); i += 1) {
      Types.NestedField field = expectedFields.get(i);

      // detect reordering
      if (i < fields.size() && !field.name().equals(fields.get(i).name())) {
        hasChange = true;
      }

      String fieldName = AvroSchemaUtil.makeCompatibleName(field.name());
      Schema.Field avroField = updateMap.get(fieldName);

      if (avroField != null) {
        updatedFields.add(avroField);

      } else {
        Preconditions.checkArgument(
            field.isOptional() || MetadataColumns.metadataFieldIds().contains(field.fieldId()),
            "Missing required field: %s",
            field.name());
        // Create a field that will be defaulted to null. We assign a unique suffix to the field
        // to make sure that even if records in the file have the field it is not projected.
        Schema.Field newField =
            new Schema.Field(
                fieldName + "_r" + field.fieldId(),
                AvroSchemaUtil.toOption(AvroSchemaUtil.convert(field.type())),
                null,
                JsonProperties.NULL_VALUE);
        newField.addProp(AvroSchemaUtil.FIELD_ID_PROP, field.fieldId());
        if (!field.name().equals(fieldName)) {
          newField.addProp(AvroSchemaUtil.ICEBERG_FIELD_NAME_PROP, field.name());
        }
        updatedFields.add(newField);
        hasChange = true;
      }
    }

    if (hasChange || renames.containsKey(record.getFullName())) {
      return AvroSchemaUtil.copyRecord(record, updatedFields, renames.get(record.getFullName()));
    }

    return record;
  }

  /**
   * 处理单个字段：按字段 id 在期望 struct 中查找对应字段，更新 {@link #current} 指针后递归投影。
   *
   * <p>逻辑：若期望 struct 中无该字段 id，说明未被选中，返回 null（字段被裁剪）；否则把 {@code current} 切到期望字段类型，递归取得投影
   * schema。若投影结果或期望字段名与原字段不一致， 则拷贝字段并重命名为期望名（Avro 字段不可复用，故始终拷贝）；最后恢复 {@code current}。
   *
   * @param field 文件 Avro 字段
   * @param fieldResult 字段子 schema 投影结果供应器
   * @return 投影后的字段，或 null 表示该字段未投影
   */
  @Override
  public Schema.Field field(Schema.Field field, Supplier<Schema> fieldResult) {
    Types.StructType struct = current.asNestedType().asStructType();
    int fieldId = AvroSchemaUtil.getFieldId(field);
    Types.NestedField expectedField = struct.field(fieldId);

    // if the field isn't present, it was not selected
    if (expectedField == null) {
      return null;
    }

    String expectedName = expectedField.name();

    this.current = expectedField.type();
    try {
      Schema schema = fieldResult.get();

      if (!Objects.equals(schema, field.schema()) || !expectedName.equals(field.name())) {
        // add an alias for the field
        return AvroSchemaUtil.copyField(
            field, schema, AvroSchemaUtil.makeCompatibleName(expectedName));
      } else {
        // always copy because fields can't be reused
        return AvroSchemaUtil.copyField(field, field.schema(), field.name());
      }

    } finally {
      this.current = struct;
    }
  }

  /**
   * 处理 union 节点（仅支持 option schema），用投影后的非空分支重建 option。
   *
   * @param union 文件 Avro union schema
   * @param options 各分支投影结果
   * @return 投影后的 union schema
   */
  @Override
  public Schema union(Schema union, Iterable<Schema> options) {
    Preconditions.checkState(
        AvroSchemaUtil.isOptionSchema(union),
        "Invalid schema: non-option unions are not supported: %s",
        union);
    Schema nonNullOriginal = AvroSchemaUtil.fromOption(union);
    Schema nonNullResult = AvroSchemaUtil.fromOptions(Lists.newArrayList(options));

    if (!Objects.equals(nonNullOriginal, nonNullResult)) {
      return AvroSchemaUtil.toOption(nonNullResult);
    }

    return union;
  }

  /**
   * 处理 array 节点：区分 map-as-array（LogicalMap）与普通 list 两种情况投影。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若是 map-as-array：把 {@code current} 切成 key/value 二字段 struct 对应元素， 取投影后的 value 字段，若与原 value
   *       不一致或需补 LogicalMap 则用 {@link AvroSchemaUtil#createProjectionMap} 重建。
   *   <li>若是普通 list：把 {@code current} 切到元素类型，若投影后元素 schema 变化则用 {@link
   *       AvroSchemaUtil#replaceElement} 重建。
   * </ul>
   *
   * @param array 文件 Avro array schema
   * @param element 元素 schema 投影结果供应器
   * @return 投影后的 array schema
   */
  @Override
  public Schema array(Schema array, Supplier<Schema> element) {
    if (array.getLogicalType() instanceof LogicalMap
        || (current.isMapType() && AvroSchemaUtil.isKeyValueSchema(array.getElementType()))) {
      Preconditions.checkArgument(current.isMapType(), "Incompatible projected type: %s", current);
      Types.MapType asMapType = current.asNestedType().asMapType();
      this.current =
          Types.StructType.of(asMapType.fields()); // create a struct to correspond to element
      try {
        Schema keyValueSchema = array.getElementType();
        Schema.Field keyField = keyValueSchema.getFields().get(0);
        Schema.Field valueField = keyValueSchema.getFields().get(1);
        Schema.Field valueProjection = element.get().getField("value");

        // element was changed, create a new array
        if (!Objects.equals(valueProjection.schema(), valueField.schema())) {
          return AvroSchemaUtil.createProjectionMap(
              keyValueSchema.getFullName(),
              AvroSchemaUtil.getFieldId(keyField),
              keyField.name(),
              keyField.schema(),
              AvroSchemaUtil.getFieldId(valueField),
              valueField.name(),
              valueProjection.schema());
        } else if (!(array.getLogicalType() instanceof LogicalMap)) {
          return AvroSchemaUtil.createProjectionMap(
              keyValueSchema.getFullName(),
              AvroSchemaUtil.getFieldId(keyField),
              keyField.name(),
              keyField.schema(),
              AvroSchemaUtil.getFieldId(valueField),
              valueField.name(),
              valueField.schema());
        }

        return array;

      } finally {
        this.current = asMapType;
      }

    } else {
      Preconditions.checkArgument(current.isListType(), "Incompatible projected type: %s", current);
      Types.ListType list = current.asNestedType().asListType();
      this.current = list.elementType();
      try {
        Schema elementSchema = element.get();

        // element was changed, create a new array
        if (!Objects.equals(elementSchema, array.getElementType())) {
          return AvroSchemaUtil.replaceElement(array, elementSchema);
        }

        return array;

      } finally {
        this.current = list;
      }
    }
  }

  /**
   * 处理原生 map 节点：要求 key 为 string，把 {@code current} 切到 value 类型后投影值类型。
   *
   * <p>逻辑：若投影后 value schema 与原值不一致，则用 {@link AvroSchemaUtil#replaceValue} 重建 map，否则原样返回。
   *
   * @param map 文件 Avro map schema
   * @param value 值 schema 投影结果供应器
   * @return 投影后的 map schema
   */
  @Override
  public Schema map(Schema map, Supplier<Schema> value) {
    Preconditions.checkArgument(
        current.isNestedType() && current.asNestedType().isMapType(),
        "Incompatible projected type: %s",
        current);
    Types.MapType asMapType = current.asNestedType().asMapType();
    Preconditions.checkArgument(
        asMapType.keyType() == Types.StringType.get(),
        "Incompatible projected type: key type %s is not string",
        asMapType.keyType());
    this.current = asMapType.valueType();
    try {
      Schema valueSchema = value.get();

      // element was changed, create a new map
      if (!Objects.equals(valueSchema, map.getValueType())) {
        return AvroSchemaUtil.replaceValue(map, valueSchema);
      }

      return map;

    } finally {
      this.current = asMapType;
    }
  }

  /**
   * 处理原始类型节点：按当前 Iceberg 类型做类型提升。
   *
   * <p>逻辑：int 提升为 long（当期望为 LONG 时）、float 提升为 double（当期望为 DOUBLE 时）， 其余原样返回。
   *
   * @param primitive 文件 Avro 原始类型 schema
   * @return 投影后的原始类型 schema
   */
  @Override
  public Schema primitive(Schema primitive) {
    // check for type promotion
    switch (primitive.getType()) {
      case INT:
        if (current.typeId() == Type.TypeID.LONG) {
          return Schema.create(Schema.Type.LONG);
        }
        return primitive;

      case FLOAT:
        if (current.typeId() == Type.TypeID.DOUBLE) {
          return Schema.create(Schema.Type.DOUBLE);
        }
        return primitive;

      default:
        return primitive;
    }
  }
}
