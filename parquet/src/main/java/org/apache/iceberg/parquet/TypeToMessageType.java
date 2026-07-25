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

import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BOOLEAN;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.DOUBLE;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.FLOAT;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64;

import org.apache.iceberg.Schema;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.types.Type.NestedType;
import org.apache.iceberg.types.Type.PrimitiveType;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types.DecimalType;
import org.apache.iceberg.types.Types.FixedType;
import org.apache.iceberg.types.Types.ListType;
import org.apache.iceberg.types.Types.MapType;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.types.Types.TimestampType;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.LogicalTypeAnnotation.TimeUnit;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;

/**
 * 文件级说明：将 Iceberg {@link Schema} / {@link org.apache.iceberg.types.Type} 转换为 Parquet {@link
 * MessageType} / {@link Type}。
 *
 * <p>所属模块：iceberg-parquet（写入侧 schema 转换，把 Iceberg 类型系统映射为 Parquet schema）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>递归把 Iceberg struct/list/map/primitive 转换为对应 Parquet 节点。
 *   <li>为每个字段保留字段 ID（Parquet schema 的 id），支持 Iceberg 字段追踪。
 *   <li>按 Iceberg 类型选择 Parquet 物理类型与 logical type 注解（如 Date->INT32+DATE）。
 *   <li>通过 {@link AvroSchemaUtil#makeCompatibleName} 对字段名做 Avro 兼容处理。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Decimal 精度分级：precision <= 9 用 INT32，<= 18 用 INT64，否则用 FIXED_LEN_BYTE_ARRAY，平衡存储效率与精度。
 *   <li>时间统一 MICROS：Time 与 Timestamp 均以微秒存储，符合 Iceberg 规范。
 *   <li>UUID 固定 16 字节：以 FIXED_LEN_BYTE_ARRAY(16) + UUID 注解存储。
 * </ul>
 *
 * <p>上下游关系：被 {@link ParquetSchemaUtil} 与写入侧调用；输出 Parquet schema 供 {@link ParquetFileWriter}
 * 与写入器构造使用。
 */
public class TypeToMessageType {
  /** Decimal 用 INT32 存储的最大位数（9 位）。 */
  public static final int DECIMAL_INT32_MAX_DIGITS = 9;
  /** Decimal 用 INT64 存储的最大位数（18 位）。 */
  public static final int DECIMAL_INT64_MAX_DIGITS = 18;

  private static final LogicalTypeAnnotation STRING = LogicalTypeAnnotation.stringType();
  private static final LogicalTypeAnnotation DATE = LogicalTypeAnnotation.dateType();
  private static final LogicalTypeAnnotation TIME_MICROS =
      LogicalTypeAnnotation.timeType(false /* 不调整为 UTC */, TimeUnit.MICROS);
  private static final LogicalTypeAnnotation TIMESTAMP_MICROS =
      LogicalTypeAnnotation.timestampType(false /* 不调整为 UTC */, TimeUnit.MICROS);
  private static final LogicalTypeAnnotation TIMESTAMPTZ_MICROS =
      LogicalTypeAnnotation.timestampType(true /* 调整为 UTC */, TimeUnit.MICROS);

  /**
   * 将 Iceberg {@link Schema} 转换为 Parquet {@link MessageType}。
   *
   * @param schema Iceberg schema
   * @param name schema 名称（会做 Avro 兼容处理）
   * @return Parquet message 类型
   */
  public MessageType convert(Schema schema, String name) {
    Types.MessageTypeBuilder builder = Types.buildMessage();

    for (NestedField field : schema.columns()) {
      builder.addField(field(field));
    }

    return builder.named(AvroSchemaUtil.makeCompatibleName(name));
  }

  /**
   * 将 Iceberg {@link StructType} 转换为 Parquet {@link GroupType}。
   *
   * @param struct Iceberg struct 类型
   * @param repetition Parquet 重复模式（OPTIONAL/REQUIRED）
   * @param id 字段 ID
   * @param name 字段名
   * @return Parquet group 类型
   */
  public GroupType struct(StructType struct, Type.Repetition repetition, int id, String name) {
    Types.GroupBuilder<GroupType> builder = Types.buildGroup(repetition);

    for (NestedField field : struct.fields()) {
      builder.addField(field(field));
    }

    return builder.id(id).named(AvroSchemaUtil.makeCompatibleName(name));
  }

  /**
   * 将 Iceberg {@link NestedField} 转换为 Parquet {@link Type}。
   *
   * <p>逻辑：按字段 optional/required 设置 repetition；若为原始类型走 primitive， 否则按 struct/map/list 分派，未知类型抛异常。
   *
   * @param field Iceberg 字段
   * @return Parquet 类型节点
   * @throws UnsupportedOperationException 未知嵌套类型
   */
  public Type field(NestedField field) {
    Type.Repetition repetition =
        field.isOptional() ? Type.Repetition.OPTIONAL : Type.Repetition.REQUIRED;
    int id = field.fieldId();
    String name = field.name();

    if (field.type().isPrimitiveType()) {
      return primitive(field.type().asPrimitiveType(), repetition, id, name);

    } else {
      NestedType nested = field.type().asNestedType();
      if (nested.isStructType()) {
        return struct(nested.asStructType(), repetition, id, name);
      } else if (nested.isMapType()) {
        return map(nested.asMapType(), repetition, id, name);
      } else if (nested.isListType()) {
        return list(nested.asListType(), repetition, id, name);
      }
      throw new UnsupportedOperationException("Can't convert unknown type: " + nested);
    }
  }

  /**
   * 将 Iceberg {@link ListType} 转换为 Parquet list {@link GroupType}。
   *
   * @param list Iceberg list 类型
   * @param repetition 重复模式
   * @param id 字段 ID
   * @param name 字段名
   * @return Parquet list group 类型
   */
  public GroupType list(ListType list, Type.Repetition repetition, int id, String name) {
    NestedField elementField = list.fields().get(0);
    return Types.list(repetition)
        .element(field(elementField))
        .id(id)
        .named(AvroSchemaUtil.makeCompatibleName(name));
  }

  /**
   * 将 Iceberg {@link MapType} 转换为 Parquet map {@link GroupType}。
   *
   * @param map Iceberg map 类型
   * @param repetition 重复模式
   * @param id 字段 ID
   * @param name 字段名
   * @return Parquet map group 类型
   */
  public GroupType map(MapType map, Type.Repetition repetition, int id, String name) {
    NestedField keyField = map.fields().get(0);
    NestedField valueField = map.fields().get(1);
    return Types.map(repetition)
        .key(field(keyField))
        .value(field(valueField))
        .id(id)
        .named(AvroSchemaUtil.makeCompatibleName(name));
  }

  /**
   * 将 Iceberg {@link PrimitiveType} 转换为 Parquet 原始 {@link Type}。
   *
   * <p>逻辑：按 Iceberg 类型 ID 分支，选择对应 Parquet 物理类型与 logical type 注解：
   *
   * <ul>
   *   <li>BOOLEAN/INTEGER/LONG/FLOAT/DOUBLE -> 同名物理类型；
   *   <li>DATE -> INT32 + DATE 注解；TIME -> INT64 + TIME_MICROS；
   *   <li>TIMESTAMP -> INT64 + TIMESTAMP_MICROS / TIMESTAMPTZ_MICROS（按 shouldAdjustToUTC）；
   *   <li>STRING -> BINARY + STRING 注解；BINARY -> BINARY；
   *   <li>FIXED -> FIXED_LEN_BYTE_ARRAY(length)；UUID -> FIXED_LEN_BYTE_ARRAY(16) + UUID 注解；
   *   <li>DECIMAL -> 按精度选 INT32/INT64/FIXED_LEN_BYTE_ARRAY + decimal 注解。
   * </ul>
   *
   * @param primitive Iceberg 原始类型
   * @param repetition 重复模式
   * @param id 字段 ID
   * @param originalName 原始字段名
   * @return Parquet 原始类型节点
   * @throws UnsupportedOperationException 不支持的类型
   */
  public Type primitive(
      PrimitiveType primitive, Type.Repetition repetition, int id, String originalName) {
    String name = AvroSchemaUtil.makeCompatibleName(originalName);
    switch (primitive.typeId()) {
      case BOOLEAN:
        return Types.primitive(BOOLEAN, repetition).id(id).named(name);
      case INTEGER:
        return Types.primitive(INT32, repetition).id(id).named(name);
      case LONG:
        return Types.primitive(INT64, repetition).id(id).named(name);
      case FLOAT:
        return Types.primitive(FLOAT, repetition).id(id).named(name);
      case DOUBLE:
        return Types.primitive(DOUBLE, repetition).id(id).named(name);
      case DATE:
        return Types.primitive(INT32, repetition).as(DATE).id(id).named(name);
      case TIME:
        return Types.primitive(INT64, repetition).as(TIME_MICROS).id(id).named(name);
      case TIMESTAMP:
        if (((TimestampType) primitive).shouldAdjustToUTC()) {
          return Types.primitive(INT64, repetition).as(TIMESTAMPTZ_MICROS).id(id).named(name);
        } else {
          return Types.primitive(INT64, repetition).as(TIMESTAMP_MICROS).id(id).named(name);
        }
      case STRING:
        return Types.primitive(BINARY, repetition).as(STRING).id(id).named(name);
      case BINARY:
        return Types.primitive(BINARY, repetition).id(id).named(name);
      case FIXED:
        FixedType fixed = (FixedType) primitive;

        return Types.primitive(FIXED_LEN_BYTE_ARRAY, repetition)
            .length(fixed.length())
            .id(id)
            .named(name);

      case DECIMAL:
        DecimalType decimal = (DecimalType) primitive;

        if (decimal.precision() <= DECIMAL_INT32_MAX_DIGITS) {
          // 精度 <= 9 位，存为 int
          return Types.primitive(INT32, repetition)
              .as(decimalAnnotation(decimal.precision(), decimal.scale()))
              .id(id)
              .named(name);

        } else if (decimal.precision() <= DECIMAL_INT64_MAX_DIGITS) {
          // 精度 <= 18 位，存为 long
          return Types.primitive(INT64, repetition)
              .as(decimalAnnotation(decimal.precision(), decimal.scale()))
              .id(id)
              .named(name);

        } else {
          // 高精度，存为固定长度字节数组
          int minLength = TypeUtil.decimalRequiredBytes(decimal.precision());
          return Types.primitive(FIXED_LEN_BYTE_ARRAY, repetition)
              .length(minLength)
              .as(decimalAnnotation(decimal.precision(), decimal.scale()))
              .id(id)
              .named(name);
        }

      case UUID:
        return Types.primitive(FIXED_LEN_BYTE_ARRAY, repetition)
            .length(16)
            .as(LogicalTypeAnnotation.uuidType())
            .id(id)
            .named(name);

      default:
        throw new UnsupportedOperationException("Unsupported type for Parquet: " + primitive);
    }
  }

  /**
   * 构造 Decimal 的 logical type 注解。
   *
   * @param precision 精度
   * @param scale 标度
   * @return Decimal logical type 注解
   */
  private static LogicalTypeAnnotation decimalAnnotation(int precision, int scale) {
    return LogicalTypeAnnotation.decimalType(scale, precision);
  }
}
