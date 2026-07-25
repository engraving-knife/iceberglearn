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
 * 文件级说明：将 Iceberg {@link Type} 转换为 Flink {@link LogicalType} 的访问器。
 *
 * <p>所属模块：iceberg-flink（Flink 集成模块），继承 Iceberg core 的 {@link TypeUtil.SchemaVisitor}。
 *
 * <p>职责：递归访问 Iceberg schema 的每个类型节点，映射为对应的 Flink LogicalType。
 *
 * <p>设计意图：通过 Visitor 模式实现 Iceberg → Flink 的类型转换。 类型映射规则见 {@link FlinkSchemaUtil} 的类注释。UUID 映射为
 * BinaryType(16)， String 映射为 VarCharType，Time/Timestamp 映射为微秒精度。
 *
 * <p>上下游关系：被 {@link FlinkSchemaUtil#convert(Schema)} 和 {@link FlinkSchemaUtil#convert(Type)} 调用。
 */
class TypeToFlinkType extends TypeUtil.SchemaVisitor<LogicalType> {
  TypeToFlinkType() {}

  /** schema 节点直接返回 struct 类型结果。 */
  @Override
  public LogicalType schema(Schema schema, LogicalType structType) {
    return structType;
  }

  /**
   * 构建 Flink RowType。
   *
   * <p>逻辑：遍历 Iceberg struct 字段，将每个字段的类型结果转为 Flink RowField， 保留字段名和可空性。
   */
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

  @Override
  public LogicalType list(Types.ListType list, LogicalType elementResult) {
    return new ArrayType(elementResult.copy(list.isElementOptional()));
  }

  @Override
  public LogicalType map(Types.MapType map, LogicalType keyResult, LogicalType valueResult) {
    // keys in map are not allowed to be null.
    return new MapType(keyResult.copy(false), valueResult.copy(map.isValueOptional()));
  }

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
