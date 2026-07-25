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
package org.apache.iceberg.flink;

import java.util.List;
import org.apache.flink.table.types.logical.ArrayType;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.BinaryType;
import org.apache.flink.table.types.logical.BooleanType;
import org.apache.flink.table.types.logical.DateType;
import org.apache.flink.table.types.logical.DecimalType;
import org.apache.flink.table.types.logical.DoubleType;
import org.apache.flink.table.types.logical.FloatType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.LocalZonedTimestampType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.MapType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.logical.TimeType;
import org.apache.flink.table.types.logical.TimestampType;
import org.apache.flink.table.types.logical.VarBinaryType;
import org.apache.flink.table.types.logical.VarCharType;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 把 Iceberg 的 {@link Type} 转换为 Flink 的 {@link LogicalType}。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：访问 Iceberg schema 并产出对应的 Flink 逻辑类型， 用于在 Flink 中表示 Iceberg 表
 * schema。
 *
 * <p>设计意图：访问者模式——继承 {@link TypeUtil.SchemaVisitor}，对 Iceberg 每种类型给出 Flink 对应类型。 上下游：由 {@link
 * FlinkSchemaUtil#convert(Schema)} 调用。
 */
class TypeToFlinkType extends TypeUtil.SchemaVisitor<LogicalType> {
  TypeToFlinkType() {}

  /** Schema 入口，直接返回 struct 类型结果。 */
  @Override
  public LogicalType schema(Schema schema, LogicalType structType) {
    return structType;
  }

  /** 转换 Iceberg StructType 为 Flink RowType，按字段可空性设置 copy。 */
  @Override
  public LogicalType struct(Types.StructType struct, List<LogicalType> fieldResults) {
    List<Types.NestedField> fields = struct.fields();

    List<RowType.RowField> flinkFields = Lists.newArrayListWithExpectedSize(fieldResults.size());
    for (int i = 0; i < fields.size(); i += 1) {
      Types.NestedField field = fields.get(i);
      LogicalType type = fieldResults.get(i);
      RowType.RowField flinkField =
          new RowType.RowField(field.name(), type.copy(field.isOptional()), field.doc());
      flinkFields.add(flinkField);
    }

    return new RowType(flinkFields);
  }

  @Override
  public LogicalType field(Types.NestedField field, LogicalType fieldResult) {
    return fieldResult;
  }

  /** 转换 Iceberg ListType 为 Flink ArrayType。 */
  @Override
  public LogicalType list(Types.ListType list, LogicalType elementResult) {
    return new ArrayType(elementResult.copy(list.isElementOptional()));
  }

  /** 转换 Iceberg MapType 为 Flink MapType，键不允许为 null。 */
  @Override
  public LogicalType map(Types.MapType map, LogicalType keyResult, LogicalType valueResult) {
    // keys in map are not allowed to be null.
    return new MapType(keyResult.copy(false), valueResult.copy(map.isValueOptional()));
  }

  /**
   * 转换 Iceberg 基本类型为 Flink 基本类型。
   *
   * <p>逻辑：按 typeId 分发到 BooleanType/IntType/BigIntType/FloatType/DoubleType/DateType/TimeType/
   * TimestampType 等。TIMESTAMP 按 shouldAdjustToUTC 决定是带时区还是不带时区； UUID 与 FIXED 都映射为 BinaryType。
   */
  @Override
  public LogicalType primitive(Type.PrimitiveType primitive) {
    switch (primitive.typeId()) {
      case BOOLEAN:
        return new BooleanType();
      case INTEGER:
        return new IntType();
      case LONG:
        return new BigIntType();
      case FLOAT:
        return new FloatType();
      case DOUBLE:
        return new DoubleType();
      case DATE:
        return new DateType();
      case TIME:
        // For the type: Flink only support TimeType with default precision (second) now. The
        // precision of time is
        // not supported in Flink, so we can think of it as a simple time type directly.
        // For the data: Flink uses int that support mills to represent time data, so it supports
        // mills precision.
        return new TimeType();
      case TIMESTAMP:
        Types.TimestampType timestamp = (Types.TimestampType) primitive;
        if (timestamp.shouldAdjustToUTC()) {
          // MICROS
          return new LocalZonedTimestampType(6);
        } else {
          // MICROS
          return new TimestampType(6);
        }
      case STRING:
        return new VarCharType(VarCharType.MAX_LENGTH);
      case UUID:
        // UUID length is 16
        return new BinaryType(16);
      case FIXED:
        Types.FixedType fixedType = (Types.FixedType) primitive;
        return new BinaryType(fixedType.length());
      case BINARY:
        return new VarBinaryType(VarBinaryType.MAX_LENGTH);
      case DECIMAL:
        Types.DecimalType decimal = (Types.DecimalType) primitive;
        return new DecimalType(decimal.precision(), decimal.scale());
      default:
        throw new UnsupportedOperationException(
            "Cannot convert unknown type to Flink: " + primitive);
    }
  }
}
