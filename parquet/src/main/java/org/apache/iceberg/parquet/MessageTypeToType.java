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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.TimestampType;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type.Repetition;

/**
 * 文件级说明：将 Parquet {@link MessageType} 转换为 Iceberg {@link Type} 的访问者。
 *
 * <p>所属模块：iceberg-parquet（读取侧 schema 转换，把 Parquet 文件 schema 映射为 Iceberg 类型系统）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历 Parquet schema（message/struct/list/map/primitive），递归构造等价的 Iceberg 类型。
 *   <li>依据字段 ID（Parquet schema 自带 ID 或调用方提供的 nameToId 函数）建立 字段名路径到 ID 的别名映射。
 *   <li>无法确定 ID 的字段会被裁剪（prune），保证读取 schema 与 Iceberg 表 schema 对齐。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>访问者模式：继承 {@link ParquetTypeVisitor}，把 schema 遍历与类型映射解耦。
 *   <li>逻辑类型优先：primitive 转换时先尝试 LogicalTypeAnnotation（String/Decimal/Date 等）， 未命中再按物理类型 fallback。
 *   <li>ID 双来源：优先使用 Parquet schema 自带 ID，否则回退到 nameToIdFunc 按名称路径解析， 兼容新旧版本 Parquet 文件。
 * </ul>
 *
 * <p>上下游关系：被 {@link ParquetSchemaUtil} 等读取侧工具调用；输出 Iceberg Type 供 后续读取器构造与字段裁剪使用。
 */
class MessageTypeToType extends ParquetTypeVisitor<Type> {
  private static final Joiner DOT = Joiner.on(".");

  private final Map<String, Integer> aliasToId = Maps.newHashMap();
  private final Function<String[], Integer> nameToIdFunc;

  /**
   * 构造转换器。
   *
   * @param nameToIdFunc 当 Parquet schema 不含字段 ID 时，按字段名路径解析 ID 的函数
   */
  MessageTypeToType(Function<String[], Integer> nameToIdFunc) {
    this.nameToIdFunc = nameToIdFunc;
  }

  /**
   * 返回字段名路径到字段 ID 的别名映射。
   *
   * <p>用于在转换过程中记录每个字段的全路径名与其 ID 的对应关系，供上层做字段映射。
   *
   * @return 别名映射表
   */
  public Map<String, Integer> getAliases() {
    return aliasToId;
  }

  /**
   * 处理顶层 message 节点，转换为 Iceberg struct 类型。
   *
   * <p>逻辑：委托 {@link #struct} 转换；若结果为 null（所有字段被裁剪），返回空 struct。
   *
   * @param message Parquet message 类型
   * @param fields 各字段已转换的 Iceberg 类型
   * @return Iceberg struct 类型
   */
  @Override
  public Type message(MessageType message, List<Type> fields) {
    Type struct = struct(message, fields);
    return struct != null ? struct : Types.StructType.of(Lists.newArrayList());
  }

  /**
   * 将 Parquet struct 转换为 Iceberg {@link Types.StructType}。
   *
   * <p>逻辑：遍历字段，校验无 REPEATED 字段；逐字段获取 ID 与类型， 仅当 ID 存在且类型未被裁剪（非 null）时保留，并按 OPTIONAL/REQUIRED 构造
   * {@link Types.NestedField}；全部被裁剪则返回 null。
   *
   * @param struct Parquet struct group 类型
   * @param fieldTypes 各字段已转换的 Iceberg 类型
   * @return Iceberg struct 类型，或 null 表示全部裁剪
   */
  @Override
  public Type struct(GroupType struct, List<Type> fieldTypes) {
    List<org.apache.parquet.schema.Type> parquetFields = struct.getFields();
    List<Types.NestedField> fields = Lists.newArrayListWithExpectedSize(fieldTypes.size());

    for (int i = 0; i < parquetFields.size(); i += 1) {
      org.apache.parquet.schema.Type field = parquetFields.get(i);

      Preconditions.checkArgument(
          !field.isRepetition(Repetition.REPEATED),
          "Fields cannot have repetition REPEATED: %s",
          field);

      Integer fieldId = getId(field);
      Type fieldType = fieldTypes.get(i);

      // 仅当字段有 ID 且未被裁剪（类型非 null）时保留
      if (fieldId != null && fieldType != null) {
        addAlias(field.getName(), fieldId);

        if (parquetFields.get(i).isRepetition(Repetition.OPTIONAL)) {
          fields.add(optional(fieldId, field.getName(), fieldType));
        } else {
          fields.add(required(fieldId, field.getName(), fieldType));
        }
      }
    }

    return fields.isEmpty() ? null : Types.StructType.of(fields);
  }

  /**
   * 将 Parquet list 转换为 Iceberg {@link Types.ListType}。
   *
   * <p>逻辑：通过 {@link ParquetSchemaUtil#determineListElementType} 定位元素类型， 获取元素 ID；仅当 ID
   * 存在且元素类型未被裁剪时保留，按元素 repetition 构造 Optional/Required 列表；否则返回 null。
   *
   * @param array Parquet list group 类型
   * @param elementType 元素已转换的 Iceberg 类型
   * @return Iceberg list 类型，或 null 表示裁剪
   */
  @Override
  public Type list(GroupType array, Type elementType) {
    org.apache.parquet.schema.Type element = ParquetSchemaUtil.determineListElementType(array);

    Integer elementFieldId = getId(element);

    // 仅当元素有 ID 且未被裁剪（类型非 null）时保留
    if (elementFieldId != null && elementType != null) {
      addAlias(element.getName(), elementFieldId);

      if (element.isRepetition(Repetition.OPTIONAL)) {
        return Types.ListType.ofOptional(elementFieldId, elementType);
      } else {
        return Types.ListType.ofRequired(elementFieldId, elementType);
      }
    }

    return null;
  }

  /**
   * 将 Parquet map 转换为 Iceberg {@link Types.MapType}。
   *
   * <p>逻辑：从 key-value group 取出 key 与 value；校验 value 非 REPEATED； 仅当 key、value 均有 ID 且类型未被裁剪时保留，按
   * value 的 repetition 构造 Optional/Required map；否则返回 null。
   *
   * @param map Parquet map group 类型
   * @param keyType key 已转换的 Iceberg 类型
   * @param valueType value 已转换的 Iceberg 类型
   * @return Iceberg map 类型，或 null 表示裁剪
   */
  @Override
  public Type map(GroupType map, Type keyType, Type valueType) {
    GroupType keyValue = map.getType(0).asGroupType();
    org.apache.parquet.schema.Type key = keyValue.getType(0);
    org.apache.parquet.schema.Type value = keyValue.getType(1);

    Preconditions.checkArgument(
        !value.isRepetition(Repetition.REPEATED),
        "Values cannot have repetition REPEATED: %s",
        value);

    Integer keyFieldId = getId(key);
    Integer valueFieldId = getId(value);

    // 仅当 key、value 均有 ID 且类型未被裁剪时保留
    if (keyFieldId != null && valueFieldId != null && keyType != null && valueType != null) {
      addAlias(key.getName(), keyFieldId);
      addAlias(value.getName(), valueFieldId);

      // 按规范 key 必为 required，仅检查 value 的 OPTIONAL
      if (value.isRepetition(Repetition.OPTIONAL)) {
        return Types.MapType.ofOptional(keyFieldId, valueFieldId, keyType, valueType);
      } else {
        return Types.MapType.ofRequired(keyFieldId, valueFieldId, keyType, valueType);
      }
    }

    return null;
  }

  /**
   * 将 Parquet 原始类型转换为 Iceberg 原始类型。
   *
   * <p>逻辑：优先按 LogicalTypeAnnotation 转换（委托 {@link ParquetLogicalTypeVisitor}），命中则返回；否则按物理类型 fallback
   * 到 Boolean/Integer/Long/Float/Double/Fixed/Binary/Timestamp 等。
   *
   * @param primitive Parquet 原始类型
   * @return Iceberg 原始类型
   * @throws UnsupportedOperationException 未知原始类型
   */
  @Override
  public Type primitive(PrimitiveType primitive) {
    // 优先使用 logical type annotation
    LogicalTypeAnnotation logicalType = primitive.getLogicalTypeAnnotation();
    if (logicalType != null) {
      Optional<Type> converted = logicalType.accept(ParquetLogicalTypeVisitor.get());
      if (converted.isPresent()) {
        return converted.get();
      }
    }

    // 否则按物理类型 fallback
    switch (primitive.getPrimitiveTypeName()) {
      case BOOLEAN:
        return Types.BooleanType.get();
      case INT32:
        return Types.IntegerType.get();
      case INT64:
        return Types.LongType.get();
      case FLOAT:
        return Types.FloatType.get();
      case DOUBLE:
        return Types.DoubleType.get();
      case FIXED_LEN_BYTE_ARRAY:
        return Types.FixedType.ofLength(primitive.getTypeLength());
      case INT96:
        return Types.TimestampType.withZone();
      case BINARY:
        return Types.BinaryType.get();
    }

    throw new UnsupportedOperationException("Cannot convert unknown primitive type: " + primitive);
  }

  /**
   * LogicalTypeAnnotation 访问者：把 Parquet logical type 映射为 Iceberg 类型。
   *
   * <p>设计意图：单例复用，集中管理 logical type 到 Iceberg 类型的映射规则。
   */
  private static class ParquetLogicalTypeVisitor
      implements LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<Type> {
    private static final ParquetLogicalTypeVisitor INSTANCE = new ParquetLogicalTypeVisitor();

    private static ParquetLogicalTypeVisitor get() {
      return INSTANCE;
    }

    /** String logical type -> Iceberg StringType。 */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.StringLogicalTypeAnnotation stringType) {
      return Optional.of(Types.StringType.get());
    }

    /** Enum logical type -> Iceberg StringType。 */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.EnumLogicalTypeAnnotation enumType) {
      return Optional.of(Types.StringType.get());
    }

    /** Decimal logical type -> Iceberg DecimalType（保留 precision 与 scale）。 */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.DecimalLogicalTypeAnnotation decimalType) {
      return Optional.of(Types.DecimalType.of(decimalType.getPrecision(), decimalType.getScale()));
    }

    /** Date logical type -> Iceberg DateType。 */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.DateLogicalTypeAnnotation dateType) {
      return Optional.of(Types.DateType.get());
    }

    /** Time logical type -> Iceberg TimeType。 */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.TimeLogicalTypeAnnotation timeType) {
      return Optional.of(Types.TimeType.get());
    }

    /**
     * Timestamp logical type -> Iceberg TimestampType。
     *
     * <p>逻辑：按 isAdjustedToUTC 决定带时区（withZone）或不带时区（withoutZone）。
     */
    @Override
    public Optional<Type> visit(
        LogicalTypeAnnotation.TimestampLogicalTypeAnnotation timestampType) {
      return Optional.of(
          timestampType.isAdjustedToUTC() ? TimestampType.withZone() : TimestampType.withoutZone());
    }

    /**
     * Int logical type -> Iceberg IntegerType 或 LongType。
     *
     * <p>逻辑：禁止 uint64；位宽小于 32 或（位宽等于 32 且有符号）映射为 Integer， 否则映射为 Long。
     *
     * @throws IllegalArgumentException 出现无符号 64 位整数
     */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.IntLogicalTypeAnnotation intType) {
      Preconditions.checkArgument(
          intType.isSigned() || intType.getBitWidth() < 64,
          "Cannot use uint64: not a supported Java type");
      if (intType.getBitWidth() < 32) {
        return Optional.of(Types.IntegerType.get());
      } else if (intType.getBitWidth() == 32 && intType.isSigned()) {
        return Optional.of(Types.IntegerType.get());
      } else {
        return Optional.of(Types.LongType.get());
      }
    }

    /** JSON logical type -> Iceberg StringType。 */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.JsonLogicalTypeAnnotation jsonType) {
      return Optional.of(Types.StringType.get());
    }

    /** BSON logical type -> Iceberg BinaryType。 */
    @Override
    public Optional<Type> visit(LogicalTypeAnnotation.BsonLogicalTypeAnnotation bsonType) {
      return Optional.of(Types.BinaryType.get());
    }
  }

  /**
   * 记录字段全路径名到 ID 的别名映射。
   *
   * @param name 字段名
   * @param fieldId 字段 ID
   */
  private void addAlias(String name, int fieldId) {
    aliasToId.put(DOT.join(path(name)), fieldId);
  }

  /**
   * 获取 Parquet 字段的 ID，优先取 schema 自带 ID，否则回退到 nameToIdFunc。
   *
   * @param type Parquet schema 节点
   * @return 字段 ID，或 null 表示无法确定（该字段将被裁剪）
   */
  private Integer getId(org.apache.parquet.schema.Type type) {
    org.apache.parquet.schema.Type.ID id = type.getId();
    if (id != null) {
      return id.intValue();
    } else {
      return nameToIdFunc.apply(path(type.getName()));
    }
  }
}
